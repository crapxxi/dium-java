package com.dium.demo.services.auth;

public interface OtpCodeSenderService {
    void sendCode(String phone);
}
