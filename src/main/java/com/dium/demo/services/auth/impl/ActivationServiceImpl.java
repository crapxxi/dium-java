package com.dium.demo.services.auth.impl;

import com.dium.demo.dto.responses.AuthResponse;
import com.dium.demo.enums.OtpPurpose;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.models.User;
import com.dium.demo.repositories.UserRepository;
import com.dium.demo.services.auth.ActivationService;
import com.dium.demo.services.auth.JwtService;
import com.dium.demo.services.auth.OtpService;
import com.dium.demo.utils.PhoneNormalizer;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActivationServiceImpl implements ActivationService {
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final OtpService otpService;

    @Transactional
    @Override
    public AuthResponse activateAccount(String rawPhone, String code) {
        String phone = PhoneNormalizer.normalize(rawPhone);

        otpService.verifyCode(phone, OtpPurpose.ACTIVATION, code);

        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new EntityNotFoundException("Пользователь не найден"));

        user.setIsConfirmed(true);
        userRepository.save(user);

        return AuthResponse.authenticated(jwtService.generateToken(user), phone);
    }

    @Override
    public void resendActivationCode(String rawPhone) {
        String phone = PhoneNormalizer.normalize(rawPhone);

        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new EntityNotFoundException("Пользователь не найден"));

        if (user.getIsConfirmed()) {
            throw new BusinessLogicException("Аккаунт уже подтверждён");
        }

        otpService.sendCode(phone, OtpPurpose.ACTIVATION);
    }
}
