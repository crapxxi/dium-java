package com.dium.demo.services.auth.impl;

import com.dium.demo.dto.responses.AuthResponse;
import com.dium.demo.dto.requests.LoginRequest;
import com.dium.demo.dto.requests.RegisterRequest;
import com.dium.demo.dto.requests.VenueOwnerRequest;
import com.dium.demo.dto.responses.UserResponse;
import com.dium.demo.enums.OtpPurpose;
import com.dium.demo.enums.UserRole;
import com.dium.demo.exceptions.AccessDeniedException;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.exceptions.TooManyRequestsException;
import com.dium.demo.mappers.UserMapper;
import com.dium.demo.models.User;
import com.dium.demo.repositories.UserRepository;
import com.dium.demo.services.auth.AuthService;
import com.dium.demo.services.auth.CustomUserDetailsService;
import com.dium.demo.services.auth.JwtService;
import com.dium.demo.services.auth.OtpService;
import com.dium.demo.services.auth.TwoFactorService;
import com.dium.demo.utils.PhoneNormalizer;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final TwoFactorService twoFactorService;

    @Override
    public AuthResponse login(LoginRequest request) {
        User user = authenticate(request.phone(), request.password());
        String phone = user.getPhone();

        if (!user.getIsConfirmed()) {
            // Код отправляем только после проверки пароля. Если он уже был отправлен
            // меньше минуты назад — просто ведём пользователя на экран активации.
            try {
                otpService.sendCode(phone, OtpPurpose.ACTIVATION);
            } catch (TooManyRequestsException ignored) {
            }
            throw new BusinessLogicException("ACCOUNT_LOCKED:Please confirm your phone number. Code sent.");
        }

        if (twoFactorService.isLoginChallengeRequired(user)) {
            // JWT не выдаём: только временный токен «2fa_pending» до проверки кода в /2fa/verify
            return twoFactorService.startLoginChallenge(user);
        }

        return AuthResponse.authenticated(jwtService.generateToken(user), phone);
    }

    /**
     * Проверяет телефон и пароль. Сообщение об ошибке одинаковое для «нет пользователя» и «неверный пароль».
     */
    private User authenticate(String rawPhone, String rawPassword) {
        String phone;
        try {
            phone = PhoneNormalizer.normalize(rawPhone);
        } catch (BusinessLogicException e) {
            throw new BadCredentialsException("Phone or password is incorrect!");
        }

        User user = userRepository.findByPhone(phone).orElse(null);
        if (user == null || user.getPassword() == null
                || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new BadCredentialsException("Phone or password is incorrect!");
        }
        return user;
    }

    @Transactional
    @Override
    public UserResponse register(RegisterRequest request) {
        String phone = PhoneNormalizer.normalize(request.phone());

        User user = userRepository.findByPhone(phone).orElse(null);
        if (user != null && user.getIsConfirmed()) {
            throw new AccessDeniedException("User has already registered!");
        }

        if (user == null) {
            user = userMapper.toEntity(request);
            user.setPhone(phone);
            user.setRole(UserRole.CLIENT);
            user.setIsConfirmed(false);
        } else if (user.getRole() != UserRole.CLIENT) {
            // Неподтверждённый аккаунт владельца/админа нельзя «перерегистрировать» через публичную форму
            throw new AccessDeniedException("User has already registered!");
        } else {
            // Номер зарегистрирован, но не подтверждён (например, код не дошёл):
            // даём пройти регистрацию заново вместо тупика «уже зарегистрирован».
            user.setName(request.name());
        }
        user.setPassword(passwordEncoder.encode(request.password()));

        User saved = userRepository.save(user);

        // Если код не отправился, исключение откатит транзакцию и клиент получит ошибку, а не 200
        otpService.sendCode(saved.getPhone(), OtpPurpose.ACTIVATION);

        return userMapper.toResponse(saved);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    @Override
    public UserResponse assignVenueOwner(VenueOwnerRequest request) {
        String phone = PhoneNormalizer.normalize(request.phone());

        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new EntityNotFoundException("User with this phone is not registered"));

        if (user.getRole() != UserRole.CLIENT) {
            throw new BusinessLogicException("User is already " + user.getRole());
        }

        user.setRole(UserRole.VENUE_OWNER);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    @Override
    public UserResponse getMe() {
        User user = userDetailsService.getCurrentUser();

        if(!user.isAccountNonLocked())
            throw new AccessDeniedException("Please, logout and confirm phone!");

        return userMapper.toResponse(user);
    }
}
