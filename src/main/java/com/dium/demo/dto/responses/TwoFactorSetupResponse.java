package com.dium.demo.dto.responses;

import com.dium.demo.enums.TwoFactorMethod;
import io.swagger.v3.oas.annotations.media.Schema;

public record TwoFactorSetupResponse(
        TwoFactorMethod method,
        @Schema(description = "TOTP: секрет в Base32 для ручного ввода в приложение. Показывается один раз")
        String secret,
        @Schema(description = "TOTP: ссылка otpauth://totp/... для QR-кода")
        String otpauthUri,
        @Schema(description = "TOTP: готовый QR-код (data:image/png;base64,...)")
        String qrCodeDataUri
) { }
