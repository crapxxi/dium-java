package com.dium.demo.dto.requests;

import com.dium.demo.enums.TwoFactorCodeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record TwoFactorDisableRequest(
        @NotBlank(message = "Password is required")
        String password,

        @Schema(description = "Действующий код второго фактора или резервный код", example = "123456")
        @NotBlank(message = "Code is required")
        String code,

        @Schema(description = "Тип кода. Если не указан — основной способ пользователя")
        TwoFactorCodeType method
) { }
