package com.dium.demo.services.auth.impl;

import com.dium.demo.config.OtpProperties;
import com.dium.demo.enums.OtpPurpose;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.exceptions.TooManyRequestsException;
import com.dium.demo.models.OtpCode;
import com.dium.demo.repositories.OtpRepository;
import com.dium.demo.services.SenderService;
import com.dium.demo.services.auth.OtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private static final String EXPIRED_MESSAGE = "Код истёк или не запрашивался. Запросите новый код.";

    private final OtpRepository otpRepository;
    private final SenderService senderService;
    private final OtpProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public void sendCode(String phone, OtpPurpose purpose) {
        Instant now = clock.instant();

        otpRepository.find(phone, purpose).ifPresent(previous -> {
            Instant allowedAt = previous.getCreatedAt().plus(properties.resendCooldown());
            if (now.isBefore(allowedAt)) {
                long seconds = Math.max(1, Duration.between(now, allowedAt).toSeconds());
                throw new TooManyRequestsException(
                        "Код уже отправлен. Повторная отправка возможна через " + seconds + " сек.", seconds);
            }
        });

        String code = generateCode();
        otpRepository.save(OtpCode.builder()
                .phone(phone)
                .purpose(purpose)
                .code(code)
                .createdAt(now)
                .expiresAt(now.plus(properties.ttl()))
                .build());

        try {
            senderService.sendMessage(phone, buildMessage(purpose, code));
        } catch (RuntimeException e) {
            // Код не доставлен — удаляем его, чтобы cooldown не мешал повторить попытку
            otpRepository.delete(phone, purpose);
            throw e;
        }
    }

    @Override
    public void verifyCode(String phone, OtpPurpose purpose, String code) {
        OtpCode stored = otpRepository.find(phone, purpose)
                .orElseThrow(() -> new BusinessLogicException(EXPIRED_MESSAGE));

        if (!clock.instant().isBefore(stored.getExpiresAt())) {
            otpRepository.delete(phone, purpose);
            throw new BusinessLogicException(EXPIRED_MESSAGE);
        }

        int attempt = stored.getAttempts().incrementAndGet();
        if (attempt > properties.maxAttempts()) {
            otpRepository.delete(phone, purpose);
            throw new BusinessLogicException("Превышено число попыток. Запросите новый код.");
        }

        if (!constantTimeEquals(stored.getCode(), code)) {
            int left = properties.maxAttempts() - attempt;
            if (left <= 0) {
                otpRepository.delete(phone, purpose);
                throw new BusinessLogicException("Неверный код. Превышено число попыток — запросите новый код.");
            }
            throw new BusinessLogicException("Неверный код. Осталось попыток: " + left);
        }

        otpRepository.delete(phone, purpose);
    }

    private String generateCode() {
        int bound = (int) Math.pow(10, properties.length());
        return String.format("%0" + properties.length() + "d", secureRandom.nextInt(bound));
    }

    /** Сравнение за постоянное время, чтобы по времени ответа нельзя было подобрать код. */
    private static boolean constantTimeEquals(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.trim().getBytes(StandardCharsets.UTF_8));
    }

    private String buildMessage(OtpPurpose purpose, String code) {
        long minutes = Math.max(1, properties.ttl().toMinutes());
        String title = switch (purpose) {
            case ACTIVATION -> "Код подтверждения регистрации DIUM";
            case PASSWORD_RESET -> "Код для сброса пароля DIUM";
            case LOGIN_2FA -> "Код для входа в DIUM";
        };
        return String.format(
                "%s: *%s*\n⚠️ Никому не сообщайте этот код. Если вы его не запрашивали, просто проигнорируйте сообщение. Код действует %d мин.",
                title, code, minutes);
    }
}
