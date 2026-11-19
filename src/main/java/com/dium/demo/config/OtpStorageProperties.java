package com.dium.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Где хранятся OTP-коды ({@code otp.storage.*}). Подключение к Redis настраивается через {@code spring.data.redis.*}.
 *
 * @param redisEnabled   true — Redis основное хранилище, Caffeine запасное; false — только Caffeine
 * @param keyPrefix      префикс ключей в Redis
 * @param circuitBreaker когда переключаться на Caffeine и когда снова пробовать Redis
 */
@ConfigurationProperties(prefix = "otp.storage")
public record OtpStorageProperties(
        @DefaultValue("true") boolean redisEnabled,
        @DefaultValue("dium:otp:") String keyPrefix,
        @DefaultValue CircuitBreakerSettings circuitBreaker
) {

    /**
     * @param failureRateThreshold          процент неудачных вызовов, при котором breaker размыкается
     * @param slidingWindowSize             по скольким последним вызовам считается процент
     * @param minimumNumberOfCalls          минимум вызовов в окне, прежде чем breaker может разомкнуться
     * @param waitDurationInOpenState       сколько не обращаться к Redis после размыкания
     * @param permittedCallsInHalfOpenState сколько пробных вызовов после паузы решают, вернуться ли на Redis
     */
    public record CircuitBreakerSettings(
            @DefaultValue("50") float failureRateThreshold,
            @DefaultValue("20") int slidingWindowSize,
            @DefaultValue("5") int minimumNumberOfCalls,
            @DefaultValue("30s") Duration waitDurationInOpenState,
            @DefaultValue("3") int permittedCallsInHalfOpenState
    ) { }
}
