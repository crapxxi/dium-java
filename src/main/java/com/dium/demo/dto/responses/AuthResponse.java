package com.dium.demo.dto.responses;

import com.dium.demo.enums.TwoFactorMethod;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Результат входа. Либо готовый JWT (token), либо требование второго фактора (twoFactorRequired=true + tempToken).")
public record AuthResponse(
        @Schema(description = "JWT для заголовка Authorization. null, пока не пройден второй фактор")
        String token,
        String phone,
        @Schema(description = "true — пароль верный, но нужен код: вызовите POST /api/v1/auth/2fa/verify")
        boolean twoFactorRequired,
        @Schema(description = "Временный токен (5 минут) с пометкой 2fa_pending. Принимается только эндпоинтами /2fa/verify")
        String tempToken,
        @Schema(description = "Основной способ подтверждения для этого пользователя")
        TwoFactorMethod twoFactorMethod,
        @Schema(description = "Способы, между которыми можно переключаться на экране подтверждения")
        List<TwoFactorMethod> availableMethods,
        @Schema(description = "Для WhatsApp: удалось ли отправить код сразу при входе. false — запросите код повторно")
        Boolean codeSent,
        @Schema(description = "Есть ли у пользователя неиспользованные резервные коды")
        Boolean backupCodesAvailable
) {
    /** Обычный успешный вход: выдан настоящий JWT. */
    public static AuthResponse authenticated(String token, String phone) {
        return new AuthResponse(token, phone, false, null, null, null, null, null);
    }

    /** Пароль принят, но JWT не выдаётся до проверки второго фактора. */
    public static AuthResponse twoFactorChallenge(String phone, String tempToken, TwoFactorMethod method,
                                                  List<TwoFactorMethod> availableMethods, Boolean codeSent,
                                                  boolean backupCodesAvailable) {
        return new AuthResponse(null, phone, true, tempToken, method, availableMethods, codeSent, backupCodesAvailable);
    }
}
