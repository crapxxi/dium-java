package com.dium.demo.services.auth;

import com.dium.demo.dto.requests.ResetPasswordRequest;

public interface PasswordResetService {
    void requestPasswordReset(String phone);
    void resetPassword(ResetPasswordRequest request);
}
