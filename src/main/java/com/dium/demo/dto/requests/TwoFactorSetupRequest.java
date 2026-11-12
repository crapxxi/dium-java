package com.dium.demo.dto.requests;

import com.dium.demo.enums.TwoFactorMethod;
import jakarta.validation.constraints.NotNull;

public record TwoFactorSetupRequest(
        @NotNull(message = "Method is required")
        TwoFactorMethod method
) { }
