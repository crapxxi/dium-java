package com.dium.demo.dto.requests;

import com.dium.demo.enums.TwoFactorMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TwoFactorEnableRequest(
        @NotNull(message = "Method is required")
        TwoFactorMethod method,

        @Schema(description = "Код, подтверждающий, что способ работает: из приложения (TOTP) или из WhatsApp", example = "123456")
        @NotBlank(message = "Code is required")
        String code
) { }
