package com.dium.demo.enums;

/** Чем именно пользователь подтверждает действие: кодом из WhatsApp, из приложения или резервным кодом. */
public enum TwoFactorCodeType {
    WHATSAPP,
    TOTP,
    BACKUP_CODE
}
