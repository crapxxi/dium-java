package com.dium.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Лимит запросов на чувствительные эндпоинты аутентификации ({@code rate-limit.*}).
 *
 * @param enabled               включён ли лимит
 * @param capacity              сколько запросов разрешено за {@code period} с одного IP на один эндпоинт
 * @param period                окно пополнения лимита
 * @param trustForwardedHeader  брать IP клиента из X-Forwarded-For (нужно за прокси вроде Render/Nginx)
 */
@ConfigurationProperties(prefix = "rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("10") int capacity,
        @DefaultValue("1m") Duration period,
        @DefaultValue("true") boolean trustForwardedHeader
) { }
