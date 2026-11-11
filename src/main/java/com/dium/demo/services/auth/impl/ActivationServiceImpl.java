package com.dium.demo.services.auth.impl;

import com.dium.demo.dto.responses.AuthResponse;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.models.OtpCode;
import com.dium.demo.models.User;
import com.dium.demo.repositories.OtpRepository;
import com.dium.demo.repositories.UserRepository;
import com.dium.demo.services.auth.ActivationService;
import com.dium.demo.services.auth.JwtService;
import com.dium.demo.services.auth.OtpCodeSenderService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActivationServiceImpl implements ActivationService {
    private final UserRepository userRepository;
    private final OtpRepository otpRepository;
    private final JwtService jwtService;
    private final OtpCodeSenderService otpCodeSenderService;

    @Transactional
    @Override
    public AuthResponse activateAccount(String phone, String code) {
        OtpCode cachedOtp = otpRepository.findById(phone)
                .orElseThrow(() -> new BusinessLogicException("Код истек или не найден"));

        if (!cachedOtp.getCode().equals(code)) {
            throw new BusinessLogicException("Неверный код активации");
        }

        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new EntityNotFoundException("Пользователь не найден"));

        user.setIsConfirmed(true);
        userRepository.save(user);

        otpRepository.delete(cachedOtp);

        return new AuthResponse(jwtService.generateToken(user), phone);
    }

    @Transactional
    @Override
    public void resendActivationCode(String phone) {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (user.getIsConfirmed()) {
            throw new BusinessLogicException("Account is already confirmed");
        }

        otpCodeSenderService.sendCode(phone);
    }
}
