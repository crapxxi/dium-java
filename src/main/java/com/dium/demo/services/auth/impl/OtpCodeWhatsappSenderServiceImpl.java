package com.dium.demo.services.auth.impl;

import com.dium.demo.models.OtpCode;
import com.dium.demo.repositories.OtpRepository;
import com.dium.demo.services.auth.OtpCodeSenderService;
import com.dium.demo.whatsapp.WhatsAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class OtpCodeWhatsappSenderServiceImpl implements OtpCodeSenderService {

    private final OtpRepository otpRepository;
    private final WhatsAppService whatsAppService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public void sendCode(String phone) {
//        if(userRepository.existsByPhone(phone)) {
//            throw new BusinessLogicException("User with this phone exists in db");
//        }
        String code = String.format("%04d", secureRandom.nextInt(10000));

        OtpCode otpCode = OtpCode.builder()
                .phone(phone)
                .code(code)
                .build();
        otpRepository.save(otpCode);
        whatsAppService.sendMessage(phone, String.format("Код подтверждения DIUM: *%s*\n⚠\uFE0F Если вы не пытались войти в систему, просто проигнорируйте это сообщение. Код истекает через 5 минут.", code));
    }
}
