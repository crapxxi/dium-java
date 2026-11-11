package com.dium.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Настройки одноразовых кодов ({@code otp.*} в application.yml).
 *
 * @param length         длина кода в цифрах
 * @param ttl            срок жизни кода
 * @param maxAttempts    сколько раз можно ввести код, после чего он аннулируется
 * @param resendCooldown минимальная пауза между отправками кода на один номер (для одного назначения)
 */
@ConfigurationProperties(prefix = "otp")
public record OtpProperties(
        @DefaultValue("6") int length,
        @DefaultValue("5m") Duration ttl,
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("60s") Duration resendCooldown
) { }
