package com.dium.demo.services.auth;

import com.dium.demo.dto.responses.AuthResponse;

public interface ActivationService {
    AuthResponse activateAccount(String phone, String code);
    void resendActivationCode(String phone);
}
