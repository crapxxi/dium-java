package com.dium.demo.repositories.impl;

import com.dium.demo.enums.OtpPurpose;
import com.dium.demo.models.OtpCode;
import com.dium.demo.repositories.OtpRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Redis как основное хранилище OTP-кодов и Caffeine как запасное.
 * <p>
 * Каждое обращение к Redis идёт через {@link CircuitBreaker}. Пока Redis отвечает, коды пишутся только в него.
 * Если вызов падает или breaker разомкнут (Redis недавно падал), операция выполняется в Caffeine, и пользователь
 * сбоя не замечает. В разомкнутом состоянии к Redis не обращаемся вовсе, поэтому запросы не ждут таймаутов.
 * <p>
 * Чтобы при переключениях код не «терялся» и не использовался повторно:
 * <ul>
 *     <li>если в Redis кода нет, ищем в Caffeine — там остаются коды, выданные во время сбоя;</li>
 *     <li>после успешной записи в Redis локальная копия с тем же ключом удаляется;</li>
 *     <li>если удалить код из Redis не удалось (код уже использован), ключ запоминается, и Redis-копия
 *         игнорируется, пока не будет удалена или не истечёт. Иначе после восстановления Redis
 *         использованный код снова бы сработал.</li>
 * </ul>
 * Это гарантии в рамках одного экземпляра приложения: во время сбоя Redis коды, выданные одним экземпляром,
 * другому не видны.
 */
@Slf4j
public class ResilientOtpRepository implements OtpRepository {

    private final OtpRepository primary;
    private final OtpRepository fallback;
    private final CircuitBreaker circuitBreaker;
    /** Ключи кодов, которые уже использованы или аннулированы, но остались в Redis из-за сбоя. */
    private final Cache<String, Boolean> pendingRedisDeletes;

    /**
     * @param staleEntryTtl сколько помнить неудалённые из Redis ключи; не меньше TTL кода в Redis
     */
    public ResilientOtpRepository(OtpRepository primary, OtpRepository fallback,
                                  CircuitBreaker circuitBreaker, Duration staleEntryTtl) {
        this.primary = primary;
        this.fallback = fallback;
        this.circuitBreaker = circuitBreaker;
        this.pendingRedisDeletes = Caffeine.newBuilder()
                .expireAfterWrite(staleEntryTtl)
                .maximumSize(100_000)
                .build();
    }

    private static String key(String phone, OtpPurpose purpose) {
        return phone + ":" + purpose.name();
    }

    @Override
    public void save(OtpCode otpCode) {
        String phone = otpCode.getPhone();
        OtpPurpose purpose = otpCode.getPurpose();

        if (runOnPrimary("save", () -> primary.save(otpCode))) {
            // Новый код в Redis перезаписал старый; локальная копия только мешала бы
            pendingRedisDeletes.invalidate(key(phone, purpose));
            fallback.delete(phone, purpose);
        } else {
            fallback.save(otpCode);
        }
    }

    @Override
    public Optional<OtpCode> find(String phone, OtpPurpose purpose) {
        if (!redisCopyIsTrusted(phone, purpose)) {
            return fallback.find(phone, purpose);
        }
        return callPrimary("find", () -> primary.find(phone, purpose))
                .flatMap(Function.identity())
                .or(() -> fallback.find(phone, purpose));
    }

    @Override
    public OptionalInt incrementAttempts(String phone, OtpPurpose purpose) {
        if (!redisCopyIsTrusted(phone, purpose)) {
            return fallback.incrementAttempts(phone, purpose);
        }
        return callPrimary("incrementAttempts", () -> primary.incrementAttempts(phone, purpose))
                .filter(OptionalInt::isPresent)
                .orElseGet(() -> fallback.incrementAttempts(phone, purpose));
    }

    @Override
    public void delete(String phone, OtpPurpose purpose) {
        fallback.delete(phone, purpose);
        if (runOnPrimary("delete", () -> primary.delete(phone, purpose))) {
            pendingRedisDeletes.invalidate(key(phone, purpose));
        } else {
            pendingRedisDeletes.put(key(phone, purpose), Boolean.TRUE);
        }
    }

    /**
     * {@code false}, если Redis-копия кода должна была быть удалена, но удалить её не удалось.
     * Заодно пробует удалить её ещё раз.
     */
    private boolean redisCopyIsTrusted(String phone, OtpPurpose purpose) {
        String key = key(phone, purpose);
        if (pendingRedisDeletes.getIfPresent(key) == null) {
            return true;
        }
        if (runOnPrimary("delete", () -> primary.delete(phone, purpose))) {
            pendingRedisDeletes.invalidate(key);
        }
        // Даже после удаления в Redis этого кода уже нет: актуальным может быть только локальный
        return false;
    }

    private boolean runOnPrimary(String operation, Runnable call) {
        return callPrimary(operation, () -> {
            call.run();
            return Boolean.TRUE;
        }).isPresent();
    }

    /** Результат вызова Redis или пусто, если Redis недоступен (ошибка или разомкнутый breaker). */
    private <T> Optional<T> callPrimary(String operation, Supplier<T> call) {
        try {
            return Optional.of(circuitBreaker.executeSupplier(call));
        } catch (CallNotPermittedException e) {
            // Breaker разомкнут: Redis недавно падал, о переходе уже залогировано
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("Redis: операция OTP '{}' не выполнена, используется локальное хранилище: {}", operation, e.toString());
            return Optional.empty();
        }
    }
}
