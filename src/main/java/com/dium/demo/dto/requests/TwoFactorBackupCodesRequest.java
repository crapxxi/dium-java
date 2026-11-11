package com.dium.demo.dto.requests;

import jakarta.validation.constraints.NotBlank;

public record TwoFactorBackupCodesRequest(
        @NotBlank(message = "Password is required")
        String password
) { }
