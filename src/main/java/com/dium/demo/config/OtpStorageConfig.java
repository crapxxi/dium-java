package com.dium.demo.config;

import com.dium.demo.repositories.impl.CaffeineOtpRepositoryImpl;
import com.dium.demo.repositories.impl.RedisOtpRepositoryImpl;
import com.dium.demo.repositories.impl.ResilientOtpRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis + Caffeine для OTP-кодов. При {@code otp.storage.redis-enabled=false} конфигурация не подключается,
 * и единственным {@link com.dium.demo.repositories.OtpRepository} остаётся {@link CaffeineOtpRepositoryImpl}.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "otp.storage", name = "redis-enabled", havingValue = "true", matchIfMissing = true)
public class OtpStorageConfig {

    @Bean
    public CircuitBreaker otpRedisCircuitBreaker(OtpStorageProperties properties) {
        OtpStorageProperties.CircuitBreakerSettings settings = properties.circuitBreaker();
        CircuitBreaker circuitBreaker = CircuitBreaker.of("otpRedis", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(settings.slidingWindowSize())
                .minimumNumberOfCalls(settings.minimumNumberOfCalls())
                .failureRateThreshold(settings.failureRateThreshold())
                .waitDurationInOpenState(settings.waitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(settings.permittedCallsInHalfOpenState())
                .build());

        circuitBreaker.getEventPublisher().onStateTransition(event -> {
            switch (event.getStateTransition().getToState()) {
                case OPEN -> log.warn("Redis недоступен: OTP-коды временно хранятся в памяти (Caffeine). Повторная попытка через {}",
                        settings.waitDurationInOpenState());
                case HALF_OPEN -> log.info("Redis: пробные запросы после сбоя");
                case CLOSED -> log.info("Redis снова доступен: OTP-коды хранятся в Redis");
                default -> log.info("Redis circuit breaker: {}", event.getStateTransition());
            }
        });
        return circuitBreaker;
    }

    @Bean
    public RedisOtpRepositoryImpl redisOtpRepository(StringRedisTemplate redisTemplate, OtpStorageProperties properties) {
        return new RedisOtpRepositoryImpl(redisTemplate, properties.keyPrefix());
    }

    @Bean
    @Primary
    public ResilientOtpRepository resilientOtpRepository(RedisOtpRepositoryImpl redisOtpRepository,
                                                         CaffeineOtpRepositoryImpl caffeineOtpRepository,
                                                         CircuitBreaker otpRedisCircuitBreaker,
                                                         OtpProperties otpProperties) {
        return new ResilientOtpRepository(
                redisOtpRepository,
                caffeineOtpRepository,
                otpRedisCircuitBreaker,
                // TTL кода в Redis равен otp.ttl; запас на расхождение часов
                otpProperties.ttl().plusMinutes(1));
    }
}
