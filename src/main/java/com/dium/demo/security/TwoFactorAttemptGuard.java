package com.dium.demo.security;

import com.dium.demo.config.TwoFactorProperties;
import com.dium.demo.exceptions.TooManyRequestsException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Защита от перебора второго фактора (TOTP, резервные коды, пароль при отключении 2FA).
 * После {@code maxFailedAttempts} неверных вводов подряд проверки для пользователя блокируются
 * на {@code lockDuration} (отсчёт от первой ошибки).
 */
@Component
public class TwoFactorAttemptGuard {

    private final int maxFailedAttempts;
    private final long lockSeconds;
    private final Cache<String, AtomicInteger> failures;

    public TwoFactorAttemptGuard(TwoFactorProperties properties) {
        this.maxFailedAttempts = properties.maxFailedAttempts();
        this.lockSeconds = Math.max(1, properties.lockDuration().toSeconds());
        this.failures = Caffeine.newBuilder()
                .expireAfterWrite(properties.lockDuration())
                .maximumSize(100_000)
                .build();
    }

    /** @throws TooManyRequestsException если лимит неверных попыток исчерпан */
    public void checkNotLocked(String phone) {
        AtomicInteger count = failures.getIfPresent(phone);
        if (count != null && count.get() >= maxFailedAttempts) {
            long minutes = Math.max(1, (lockSeconds + 59) / 60);
            throw new TooManyRequestsException(
                    "Слишком много неверных попыток. Попробуйте через " + minutes + " мин.", lockSeconds);
        }
    }

    public void recordFailure(String phone) {
        failures.get(phone, key -> new AtomicInteger()).incrementAndGet();
    }

    public void reset(String phone) {
        failures.invalidate(phone);
    }
}
