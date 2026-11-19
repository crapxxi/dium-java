package com.dium.demo.repositories;

import com.dium.demo.enums.OtpPurpose;
import com.dium.demo.models.OtpCode;
import com.dium.demo.repositories.impl.RedisOtpRepositoryImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверка на настоящем Redis. В обычной сборке пропускается; запуск:
 * {@code docker run -d -p 6379:6379 redis:7-alpine} и {@code REDIS_TEST_HOST=localhost ./mvnw test}.
 */
@EnabledIfEnvironmentVariable(named = "REDIS_TEST_HOST", matches = ".+")
class RedisOtpRepositoryImplTest {

    private static final String PREFIX = "test:otp:";
    private static final String PHONE = "77071234567";
    private static final OtpPurpose PURPOSE = OtpPurpose.ACTIVATION;

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private RedisOtpRepositoryImpl repository;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(System.getenv("REDIS_TEST_HOST"), 6379);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        repository = new RedisOtpRepositoryImpl(redisTemplate, PREFIX);
        repository.delete(PHONE, PURPOSE);
    }

    private static OtpCode code(String value) {
        Instant now = Instant.now();
        return OtpCode.builder()
                .phone(PHONE)
                .purpose(PURPOSE)
                .code(value)
                .createdAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(5)))
                .build();
    }

    @Test
    void savesFindsCountsAttemptsAndDeletes() {
        OtpCode saved = code("048213");
        repository.save(saved);

        OtpCode found = repository.find(PHONE, PURPOSE).orElseThrow();
        assertThat(found.getCode()).isEqualTo("048213");
        assertThat(found.getCreatedAt().toEpochMilli()).isEqualTo(saved.getCreatedAt().toEpochMilli());
        assertThat(found.getExpiresAt().toEpochMilli()).isEqualTo(saved.getExpiresAt().toEpochMilli());
        assertThat(found.getAttempts().get()).isZero();

        assertThat(repository.incrementAttempts(PHONE, PURPOSE)).hasValue(1);
        assertThat(repository.incrementAttempts(PHONE, PURPOSE)).hasValue(2);
        assertThat(repository.find(PHONE, PURPOSE).orElseThrow().getAttempts().get()).isEqualTo(2);

        Long ttl = redisTemplate.getExpire(PREFIX + PHONE + ":" + PURPOSE.name());
        assertThat(ttl).isBetween(290L, 300L);

        repository.delete(PHONE, PURPOSE);
        assertThat(repository.find(PHONE, PURPOSE)).isEmpty();
    }

    @Test
    void newCodeResetsAttempts() {
        repository.save(code("111111"));
        repository.incrementAttempts(PHONE, PURPOSE);

        repository.save(code("222222"));

        OtpCode found = repository.find(PHONE, PURPOSE).orElseThrow();
        assertThat(found.getCode()).isEqualTo("222222");
        assertThat(found.getAttempts().get()).isZero();
    }

    @Test
    void incrementOnMissingCodeDoesNotCreateKey() {
        assertThat(repository.incrementAttempts(PHONE, PURPOSE)).isEmpty();
        assertThat(redisTemplate.hasKey(PREFIX + PHONE + ":" + PURPOSE.name())).isFalse();
    }
}
