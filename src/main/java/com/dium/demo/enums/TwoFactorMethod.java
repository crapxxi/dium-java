package com.dium.demo.enums;

/** Способ получения второго фактора, выбранный пользователем. */
public enum TwoFactorMethod {
    /** Одноразовый код приходит в WhatsApp. */
    WHATSAPP,
    /** Код из приложения-аутентификатора (Google Authenticator и совместимые). */
    TOTP
}
