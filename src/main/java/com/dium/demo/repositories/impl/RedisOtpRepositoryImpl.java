package com.dium.demo.repositories.impl;

import com.dium.demo.enums.OtpPurpose;
import com.dium.demo.models.OtpCode;
import com.dium.demo.repositories.OtpRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OTP-коды в Redis: общие для всех экземпляров приложения и переживают перезапуск.
 * <p>
 * Каждый код — hash {@code <prefix><телефон>:<назначение>} с полями code / createdAt / expiresAt / attempts
 * и TTL, равным сроку жизни кода. Запись и счётчик попыток меняются Lua-скриптами, чтобы операции были атомарными.
 * <p>
 * Сам по себе бин не создаётся: его собирает {@link com.dium.demo.config.OtpStorageConfig}
 * и оборачивает в {@link ResilientOtpRepository}.
 */
public class RedisOtpRepositoryImpl implements OtpRepository {

    private static final String F_CODE = "code";
    private static final String F_CREATED_AT = "createdAt";
    private static final String F_EXPIRES_AT = "expiresAt";
    private static final String F_ATTEMPTS = "attempts";

    /** Перезаписывает код целиком (старые поля и счётчик не должны остаться) и ставит TTL. */
    private static final RedisScript<Long> SAVE = RedisScript.of("""
            redis.call('DEL', KEYS[1])
            redis.call('HSET', KEYS[1], 'code', ARGV[1], 'createdAt', ARGV[2], 'expiresAt', ARGV[3], 'attempts', ARGV[4])
            redis.call('PEXPIRE', KEYS[1], ARGV[5])
            return 1
            """, Long.class);

    /** Увеличивает счётчик только у существующего кода: HINCRBY на истёкшем ключе создал бы «вечный» hash без TTL. */
    private static final RedisScript<Long> INCREMENT_ATTEMPTS = RedisScript.of("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return -1
            end
            return redis.call('HINCRBY', KEYS[1], 'attempts', 1)
            """, Long.class);

    private final StringRedisTemplate redis;
    private final String keyPrefix;

    public RedisOtpRepositoryImpl(StringRedisTemplate redis, String keyPrefix) {
        this.redis = redis;
        this.keyPrefix = keyPrefix;
    }

    private String key(String phone, OtpPurpose purpose) {
        return keyPrefix + phone + ":" + purpose.name();
    }

    @Override
    public void save(OtpCode otpCode) {
        // Точный срок жизни проверяет OtpService; TTL в Redis нужен, чтобы ключ не висел вечно
        long ttlMillis = Math.max(1000, Duration.between(otpCode.getCreatedAt(), otpCode.getExpiresAt()).toMillis());
        redis.execute(SAVE,
                List.of(key(otpCode.getPhone(), otpCode.getPurpose())),
                otpCode.getCode(),
                String.valueOf(otpCode.getCreatedAt().toEpochMilli()),
                String.valueOf(otpCode.getExpiresAt().toEpochMilli()),
                String.valueOf(otpCode.getAttempts().get()),
                String.valueOf(ttlMillis));
    }

    @Override
    public Optional<OtpCode> find(String phone, OtpPurpose purpose) {
        Map<Object, Object> fields = redis.opsForHash().entries(key(phone, purpose));
        if (fields.isEmpty() || fields.get(F_CODE) == null) {
            return Optional.empty();
        }
        return Optional.of(OtpCode.builder()
                .phone(phone)
                .purpose(purpose)
                .code((String) fields.get(F_CODE))
                .createdAt(Instant.ofEpochMilli(Long.parseLong((String) fields.get(F_CREATED_AT))))
                .expiresAt(Instant.ofEpochMilli(Long.parseLong((String) fields.get(F_EXPIRES_AT))))
                .attempts(new AtomicInteger(Integer.parseInt((String) fields.getOrDefault(F_ATTEMPTS, "0"))))
                .build());
    }

    @Override
    public void delete(String phone, OtpPurpose purpose) {
        redis.delete(key(phone, purpose));
    }

    @Override
    public OptionalInt incrementAttempts(String phone, OtpPurpose purpose) {
        Long attempts = redis.execute(INCREMENT_ATTEMPTS, List.of(key(phone, purpose)));
        return attempts == null || attempts < 0 ? OptionalInt.empty() : OptionalInt.of(attempts.intValue());
    }
}
