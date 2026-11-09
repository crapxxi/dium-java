package com.dium.demo.services.auth.impl;

import com.dium.demo.dto.requests.ResetPasswordRequest;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.models.OtpCode;
import com.dium.demo.models.User;
import com.dium.demo.repositories.OtpRepository;
import com.dium.demo.repositories.UserRepository;
import com.dium.demo.services.auth.OtpCodeSenderService;
import com.dium.demo.services.auth.PasswordResetService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserRepository userRepository;
    private final OtpCodeSenderService otpCodeSenderService;
    private final OtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void requestPasswordReset(String phone) {
        userRepository.findByPhone(phone)
                .orElseThrow(() -> new BusinessLogicException("Пользователь с таким номером не найден"));

        otpCodeSenderService.sendCode(phone);
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        OtpCode otpCode = otpRepository.findById(request.phone())
                .orElseThrow(() -> new BusinessLogicException("Код истек или не запрашивался. Попробуйте еще раз."));

        if (!otpCode.getCode().equals(request.code())) {
            throw new BusinessLogicException("Неверный код подтверждения");
        }

        User user = userRepository.findByPhone(request.phone())
                .orElseThrow(() -> new BusinessLogicException("Пользователь не найден"));

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        otpRepository.delete(otpCode);
    }
}
