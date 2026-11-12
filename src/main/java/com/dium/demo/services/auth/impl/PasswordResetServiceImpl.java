package com.dium.demo.services.auth.impl;

import com.dium.demo.dto.requests.ResetPasswordRequest;
import com.dium.demo.enums.OtpPurpose;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.models.User;
import com.dium.demo.repositories.UserRepository;
import com.dium.demo.services.auth.OtpService;
import com.dium.demo.services.auth.PasswordResetService;
import com.dium.demo.utils.PhoneNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void requestPasswordReset(String rawPhone) {
        String phone = PhoneNormalizer.normalize(rawPhone);

        userRepository.findByPhone(phone)
                .orElseThrow(() -> new BusinessLogicException("Пользователь с таким номером не найден"));

        otpService.sendCode(phone, OtpPurpose.PASSWORD_RESET);
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String phone = PhoneNormalizer.normalize(request.phone());

        otpService.verifyCode(phone, OtpPurpose.PASSWORD_RESET, request.code());

        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new BusinessLogicException("Пользователь не найден"));

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }
}
