package com.dium.demo.enums;

/**
 * Назначение одноразового кода. Код, выданный для одной цели, нельзя использовать для другой:
 * ключ кэша = телефон + назначение.
 */
public enum OtpPurpose {
    ACTIVATION,
    PASSWORD_RESET,
    LOGIN_2FA
}
