package com.dium.demo.services.auth.impl;

import com.dium.demo.config.TwoFactorProperties;
import com.dium.demo.dto.requests.TwoFactorDisableRequest;
import com.dium.demo.dto.requests.TwoFactorEnableRequest;
import com.dium.demo.dto.requests.TwoFactorVerifyRequest;
import com.dium.demo.dto.responses.AuthResponse;
import com.dium.demo.dto.responses.BackupCodesResponse;
import com.dium.demo.dto.responses.TwoFactorSetupResponse;
import com.dium.demo.dto.responses.TwoFactorStatusResponse;
import com.dium.demo.enums.OtpPurpose;
import com.dium.demo.enums.TwoFactorCodeType;
import com.dium.demo.enums.TwoFactorMethod;
import com.dium.demo.enums.UserRole;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.exceptions.MessageDeliveryException;
import com.dium.demo.exceptions.TooManyRequestsException;
import com.dium.demo.models.User;
import com.dium.demo.repositories.UserRepository;
import com.dium.demo.security.SecretEncryptor;
import com.dium.demo.security.TwoFactorAttemptGuard;
import com.dium.demo.services.auth.CustomUserDetailsService;
import com.dium.demo.services.auth.JwtService;
import com.dium.demo.services.auth.OtpService;
import com.dium.demo.services.auth.TotpService;
import com.dium.demo.services.auth.TwoFactorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TwoFactorServiceImpl implements TwoFactorService {

    /** Алфавит резервных кодов без похожих символов (0/O, 1/I/L). */
    private static final char[] BACKUP_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int BACKUP_CODE_LENGTH = 10;
    private static final String HASH_SEPARATOR = "\n";

    private final UserRepository userRepository;
    private final CustomUserDetailsService userDetailsService;
    private final OtpService otpService;
    private final TotpService totpService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final SecretEncryptor secretEncryptor;
    private final TwoFactorAttemptGuard attemptGuard;
    private final TwoFactorProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    // =====================================================================
    // Вход
    // =====================================================================

    @Override
    public boolean isLoginChallengeRequired(User user) {
        return user.hasTwoFactor() || isRequiredFor(user);
    }

    @Override
    public AuthResponse startLoginChallenge(User user) {
        TwoFactorMethod method = loginMethod(user);

        Boolean codeSent = null;
        if (method == TwoFactorMethod.WHATSAPP) {
            codeSent = trySendLoginCode(user.getPhone());
        }

        return AuthResponse.twoFactorChallenge(
                user.getPhone(),
                jwtService.generateTwoFactorPendingToken(user),
                method,
                availableMethods(user),
                codeSent,
                !backupHashes(user).isEmpty());
    }

    /**
     * Если WhatsApp недоступен, вход не обрываем: пользователь попадает на экран подтверждения,
     * где может запросить код ещё раз или ввести резервный.
     */
    private boolean trySendLoginCode(String phone) {
        try {
            otpService.sendCode(phone, OtpPurpose.LOGIN_2FA);
            return true;
        } catch (TooManyRequestsException e) {
            return true; // код отправлен меньше минуты назад и ещё действует
        } catch (MessageDeliveryException e) {
            log.warn("Не удалось отправить код входа для {}", phone);
            return false;
        }
    }

    @Override
    public void sendLoginCode(String tempToken) {
        User user = userFromTempToken(tempToken);
        otpService.sendCode(user.getPhone(), OtpPurpose.LOGIN_2FA);
    }

    @Transactional
    @Override
    public AuthResponse verifyLogin(TwoFactorVerifyRequest request) {
        User user = userFromTempToken(request.tempToken());

        if (!isLoginChallengeRequired(user)) {
            throw new BadCredentialsException("Сессия входа недействительна. Войдите заново.");
        }

        verifyCode(user, request.method(), request.code());

        return AuthResponse.authenticated(jwtService.generateToken(user), user.getPhone());
    }

    private User userFromTempToken(String tempToken) {
        String phone = jwtService.extractTwoFactorPendingSubject(tempToken);
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new BadCredentialsException("Сессия входа недействительна. Войдите заново."));
        if (!Boolean.TRUE.equals(user.getIsConfirmed())) {
            throw new BadCredentialsException("Сессия входа недействительна. Войдите заново.");
        }
        return user;
    }

    // =====================================================================
    // Управление
    // =====================================================================

    @Transactional(readOnly = true)
    @Override
    public TwoFactorStatusResponse getStatus() {
        User user = currentUser();
        return new TwoFactorStatusResponse(
                user.hasTwoFactor(),
                user.hasTwoFactor() ? user.getTwoFactorMethod() : null,
                isRequiredFor(user),
                isLoginChallengeRequired(user) ? loginMethod(user) : null,
                backupHashes(user).size());
    }

    @Transactional
    @Override
    public TwoFactorSetupResponse setup(TwoFactorMethod method) {
        User user = currentUser();

        if (user.hasTwoFactor() && user.getTwoFactorMethod() == method) {
            throw new BusinessLogicException("Этот способ двухфакторной аутентификации уже включён");
        }

        if (method == TwoFactorMethod.WHATSAPP) {
            otpService.sendCode(user.getPhone(), OtpPurpose.LOGIN_2FA);
            return new TwoFactorSetupResponse(TwoFactorMethod.WHATSAPP, null, null, null);
        }

        // TOTP: секрет сохраняется зашифрованным и начинает действовать только после /2fa/enable
        String secret = totpService.generateSecret();
        user.setTotpSecret(secretEncryptor.encrypt(secret));
        userRepository.save(user);

        String label = "+" + user.getPhone();
        return new TwoFactorSetupResponse(
                TwoFactorMethod.TOTP,
                secret,
                totpService.buildOtpAuthUri(label, secret),
                totpService.buildQrCodeDataUri(label, secret));
    }

    @Transactional
    @Override
    public BackupCodesResponse enable(TwoFactorEnableRequest request) {
        User user = currentUser();
        String phone = user.getPhone();

        if (user.hasTwoFactor() && user.getTwoFactorMethod() == request.method()) {
            throw new BusinessLogicException("Этот способ двухфакторной аутентификации уже включён");
        }

        attemptGuard.checkNotLocked(phone);
        try {
            if (request.method() == TwoFactorMethod.TOTP) {
                if (user.getTotpSecret() == null) {
                    throw new BusinessLogicException("Сначала начните настройку приложения-аутентификатора");
                }
                if (!totpService.isValidCode(secretEncryptor.decrypt(user.getTotpSecret()), request.code())) {
                    throw new BusinessLogicException("Неверный код из приложения. Проверьте время на телефоне и попробуйте ещё раз.");
                }
            } else {
                otpService.verifyCode(phone, OtpPurpose.LOGIN_2FA, request.code());
                user.setTotpSecret(null);
            }
        } catch (BusinessLogicException e) {
            attemptGuard.recordFailure(phone);
            throw e;
        }
        attemptGuard.reset(phone);

        user.setTwoFactorEnabled(true);
        user.setTwoFactorMethod(request.method());
        List<String> backupCodes = issueBackupCodes(user);
        userRepository.save(user);

        return new BackupCodesResponse(backupCodes);
    }

    @Override
    public void sendCodeToCurrentUser() {
        otpService.sendCode(currentUser().getPhone(), OtpPurpose.LOGIN_2FA);
    }

    @Transactional
    @Override
    public void disable(TwoFactorDisableRequest request) {
        User user = currentUser();

        if (!user.hasTwoFactor()) {
            throw new BusinessLogicException("Двухфакторная аутентификация не включена");
        }

        checkPassword(user, request.password());
        verifyCode(user, request.method(), request.code());

        user.setTwoFactorEnabled(false);
        user.setTwoFactorMethod(null);
        user.setTotpSecret(null);
        user.setBackupCodeHashes(null);
        userRepository.save(user);
    }

    @Transactional
    @Override
    public BackupCodesResponse regenerateBackupCodes(String password) {
        User user = currentUser();

        if (!user.hasTwoFactor()) {
            throw new BusinessLogicException("Сначала включите двухфакторную аутентификацию");
        }

        checkPassword(user, password);

        List<String> backupCodes = issueBackupCodes(user);
        userRepository.save(user);
        return new BackupCodesResponse(backupCodes);
    }

    // =====================================================================
    // Внутреннее
    // =====================================================================

    /** Свежая (управляемая JPA) копия пользователя из SecurityContext. */
    private User currentUser() {
        return userDetailsService.getCurrentUser();
    }

    private boolean isRequiredFor(User user) {
        return properties.requiredForStaff()
                && (user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.VENUE_OWNER);
    }

    /** Чем подтверждается вход: выбранным способом, а если 2FA обязательна, но не настроена — кодом в WhatsApp. */
    private TwoFactorMethod loginMethod(User user) {
        return user.hasTwoFactor() ? user.getTwoFactorMethod() : TwoFactorMethod.WHATSAPP;
    }

    private boolean isTotpActive(User user) {
        return user.hasTwoFactor() && user.getTwoFactorMethod() == TwoFactorMethod.TOTP && user.getTotpSecret() != null;
    }

    private List<TwoFactorMethod> availableMethods(User user) {
        // Телефон подтверждён при регистрации, поэтому код в WhatsApp доступен всегда как запасной способ
        return isTotpActive(user)
                ? List.of(TwoFactorMethod.TOTP, TwoFactorMethod.WHATSAPP)
                : List.of(TwoFactorMethod.WHATSAPP);
    }

    private void checkPassword(User user, String rawPassword) {
        String phone = user.getPhone();
        attemptGuard.checkNotLocked(phone);
        if (rawPassword == null || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            attemptGuard.recordFailure(phone);
            // 422, а не 401: фронтенд на 401 разлогинивает пользователя
            throw new BusinessLogicException("Неверный пароль");
        }
    }

    /**
     * Проверяет код второго фактора. Неверные попытки считаются; после лимита — временная блокировка.
     */
    private void verifyCode(User user, TwoFactorCodeType requestedType, String code) {
        String phone = user.getPhone();
        TwoFactorCodeType type = requestedType != null
                ? requestedType
                : TwoFactorCodeType.valueOf(loginMethod(user).name());

        attemptGuard.checkNotLocked(phone);
        try {
            switch (type) {
                case WHATSAPP -> otpService.verifyCode(phone, OtpPurpose.LOGIN_2FA, code);
                case TOTP -> {
                    if (!isTotpActive(user)) {
                        throw new BusinessLogicException("Приложение-аутентификатор не подключено");
                    }
                    if (!totpService.isValidCode(secretEncryptor.decrypt(user.getTotpSecret()), code)) {
                        throw new BusinessLogicException("Неверный код из приложения");
                    }
                }
                case BACKUP_CODE -> consumeBackupCode(user, code);
            }
        } catch (BusinessLogicException e) {
            attemptGuard.recordFailure(phone);
            throw e;
        }
        attemptGuard.reset(phone);
    }

    // ---------- резервные коды ----------

    private List<String> backupHashes(User user) {
        String stored = user.getBackupCodeHashes();
        if (stored == null || stored.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(stored.split(HASH_SEPARATOR))
                .filter(hash -> !hash.isBlank())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** Генерирует новые коды, сохраняет в пользователе только их хэши и возвращает коды для показа. */
    private List<String> issueBackupCodes(User user) {
        List<String> codes = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        for (int i = 0; i < properties.backupCodesCount(); i++) {
            String raw = randomBackupCode();
            hashes.add(passwordEncoder.encode(raw));
            // показываем в виде XXXXX-XXXXX, чтобы было удобно переписывать
            codes.add(raw.substring(0, 5) + "-" + raw.substring(5));
        }
        user.setBackupCodeHashes(String.join(HASH_SEPARATOR, hashes));
        return codes;
    }

    private String randomBackupCode() {
        StringBuilder sb = new StringBuilder(BACKUP_CODE_LENGTH);
        for (int i = 0; i < BACKUP_CODE_LENGTH; i++) {
            sb.append(BACKUP_ALPHABET[secureRandom.nextInt(BACKUP_ALPHABET.length)]);
        }
        return sb.toString();
    }

    private static String normalizeBackupCode(String code) {
        return code == null ? "" : code.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    /** Резервный код одноразовый: при совпадении его хэш удаляется. */
    private void consumeBackupCode(User user, String code) {
        String normalized = normalizeBackupCode(code);
        List<String> hashes = backupHashes(user);

        if (normalized.length() == BACKUP_CODE_LENGTH) {
            for (String hash : hashes) {
                if (passwordEncoder.matches(normalized, hash)) {
                    hashes.remove(hash);
                    user.setBackupCodeHashes(hashes.isEmpty() ? null : String.join(HASH_SEPARATOR, hashes));
                    userRepository.save(user);
                    return;
                }
            }
        }
        throw new BusinessLogicException("Неверный или уже использованный резервный код");
    }
}
