package com.dium.demo.dto.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record TwoFactorTempTokenRequest(
        @Schema(description = "Временный токен из ответа POST /login")
        @NotBlank(message = "Temp token is required")
        String tempToken
) { }
