package com.dium.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Настройки двухфакторной аутентификации ({@code two-factor.*}).
 *
 * @param requiredForStaff  обязательная 2FA для ролей ADMIN и VENUE_OWNER
 * @param encryptionKey     Base64-ключ AES-256 (32 байта) для шифрования TOTP-секретов
 * @param issuer            название сервиса, которое увидит пользователь в приложении-аутентификаторе
 * @param pendingTokenTtl   срок жизни временного токена «2fa_pending» между паролем и кодом
 * @param backupCodesCount  сколько резервных кодов выдаётся
 * @param maxFailedAttempts сколько неверных кодов подряд допускается до временной блокировки
 * @param lockDuration      длительность блокировки после исчерпания попыток
 */
@ConfigurationProperties(prefix = "two-factor")
public record TwoFactorProperties(
        @DefaultValue("true") boolean requiredForStaff,
        String encryptionKey,
        @DefaultValue("DIUM") String issuer,
        @DefaultValue("5m") Duration pendingTokenTtl,
        @DefaultValue("10") int backupCodesCount,
        @DefaultValue("5") int maxFailedAttempts,
        @DefaultValue("5m") Duration lockDuration
) { }
