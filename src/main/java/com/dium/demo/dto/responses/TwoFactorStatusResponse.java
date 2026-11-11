package com.dium.demo.dto.responses;

import com.dium.demo.enums.TwoFactorMethod;
import io.swagger.v3.oas.annotations.media.Schema;

public record TwoFactorStatusResponse(
        @Schema(description = "2FA включена пользователем")
        boolean enabled,
        @Schema(description = "Выбранный способ (null, если не включена)")
        TwoFactorMethod method,
        @Schema(description = "2FA обязательна для роли пользователя: при входе код запрашивается в любом случае")
        boolean required,
        @Schema(description = "Способ, который фактически используется при входе (null, если код не запрашивается)")
        TwoFactorMethod loginMethod,
        @Schema(description = "Сколько резервных кодов осталось")
        int backupCodesLeft
) { }
