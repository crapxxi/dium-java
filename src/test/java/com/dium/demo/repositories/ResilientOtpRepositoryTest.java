package com.dium.demo.repositories;

import com.dium.demo.config.OtpProperties;
import com.dium.demo.enums.OtpPurpose;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.models.OtpCode;
import com.dium.demo.repositories.impl.CaffeineOtpRepositoryImpl;
import com.dium.demo.repositories.impl.ResilientOtpRepository;
import com.dium.demo.services.SenderService;
import com.dium.demo.services.auth.OtpService;
import com.dium.demo.services.auth.impl.OtpServiceImpl;
import com.dium.demo.support.MutableClock;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ResilientOtpRepositoryTest {

    private static final String PHONE = "77071234567";
    private static final OtpPurpose PURPOSE = OtpPurpose.LOGIN_2FA;
    private static final Pattern CODE_IN_MESSAGE = Pattern.compile("\\*(\\d{6})\\*");

    /** «Redis»: настоящая логика хранения, но его можно «уронить». */
    private FlakyRepository redis;
    private CaffeineOtpRepositoryImpl caffeine;
    private CircuitBreaker circuitBreaker;
    private ResilientOtpRepository repository;

    @BeforeEach
    void setUp() {
        redis = new FlakyRepository(new CaffeineOtpRepositoryImpl(new ConcurrentMapCacheManager("otpCodes")));
        caffeine = new CaffeineOtpRepositoryImpl(new ConcurrentMapCacheManager("otpCodes"));
        circuitBreaker = CircuitBreaker.of("test", CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofHours(1)) // в HALF_OPEN переводим вручную
                .permittedNumberOfCallsInHalfOpenState(1)
                .build());
        repository = new ResilientOtpRepository(redis, caffeine, circuitBreaker, Duration.ofMinutes(10));
    }

    private static OtpCode code(String value) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return OtpCode.builder()
                .phone(PHONE)
                .purpose(PURPOSE)
                .code(value)
                .createdAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(5)))
                .build();
    }

    @Test
    void storesOnlyInRedisWhileItWorks() {
        repository.save(code("111111"));

        assertThat(redis.delegate.find(PHONE, PURPOSE)).isPresent();
        assertThat(caffeine.find(PHONE, PURPOSE)).isEmpty();
        assertThat(repository.find(PHONE, PURPOSE)).map(OtpCode::getCode).contains("111111");
        assertThat(repository.incrementAttempts(PHONE, PURPOSE)).hasValue(1);
        assertThat(redis.delegate.find(PHONE, PURPOSE).orElseThrow().getAttempts().get()).isEqualTo(1);

        repository.delete(PHONE, PURPOSE);
        assertThat(repository.find(PHONE, PURPOSE)).isEmpty();
    }

    @Test
    void fallsBackToCaffeineWhenRedisFails() {
        redis.down = true;

        assertThatCode(() -> repository.save(code("222222"))).doesNotThrowAnyException();

        assertThat(caffeine.find(PHONE, PURPOSE)).map(OtpCode::getCode).contains("222222");
        assertThat(repository.find(PHONE, PURPOSE)).map(OtpCode::getCode).contains("222222");
        assertThat(repository.incrementAttempts(PHONE, PURPOSE)).hasValue(1);
    }

    @Test
    void openBreakerStopsCallingRedis() {
        redis.down = true;
        repository.save(code("333333"));
        repository.find(PHONE, PURPOSE);

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int callsWhenOpened = redis.calls;

        repository.find(PHONE, PURPOSE);
        repository.incrementAttempts(PHONE, PURPOSE);
        repository.save(code("444444"));

        assertThat(redis.calls).as("в разомкнутом состоянии Redis не вызывается").isEqualTo(callsWhenOpened);
        assertThat(repository.find(PHONE, PURPOSE)).map(OtpCode::getCode).contains("444444");
    }

    @Test
    void returnsToRedisAfterRecoveryAndKeepsCodesIssuedDuringOutage() {
        redis.down = true;
        repository.save(code("555555"));
        repository.find(PHONE, PURPOSE);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        redis.down = false;
        circuitBreaker.transitionToHalfOpenState(); // прошло waitDurationInOpenState

        // В Redis этого кода нет, но выданный во время сбоя код продолжает работать
        assertThat(repository.find(PHONE, PURPOSE)).map(OtpCode::getCode).contains("555555");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // Новый код уже уходит в Redis, а устаревшая локальная копия удаляется
        repository.save(code("666666"));
        assertThat(redis.delegate.find(PHONE, PURPOSE)).map(OtpCode::getCode).contains("666666");
        assertThat(caffeine.find(PHONE, PURPOSE)).isEmpty();
    }

    @Test
    void usedCodeLeftInRedisAfterFailedDeleteIsNotAcceptedAgain() {
        repository.save(code("777777"));

        redis.down = true;
        repository.delete(PHONE, PURPOSE); // код использован, но из Redis не удалился
        assertThat(redis.delegate.find(PHONE, PURPOSE)).as("копия осталась в Redis").isPresent();

        // Breaker ещё разомкнут: в Redis не смотрим, а локально кода нет
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        redis.down = false;
        assertThat(repository.find(PHONE, PURPOSE)).isEmpty();

        // Redis вернулся: старая копия всё ещё лежит там, но принять её нельзя
        circuitBreaker.transitionToHalfOpenState();
        assertThat(repository.find(PHONE, PURPOSE)).isEmpty();
        assertThat(repository.incrementAttempts(PHONE, PURPOSE)).isEmpty();
        assertThat(redis.delegate.find(PHONE, PURPOSE)).as("при обращении копия дочищена в Redis").isEmpty();
    }

    @Test
    void otpFlowKeepsWorkingWhenRedisGoesDown() {
        SenderService sender = mock(SenderService.class);
        MutableClock clock = new MutableClock();
        OtpService otpService = new OtpServiceImpl(repository, sender,
                new OtpProperties(6, Duration.ofMinutes(5), 5, Duration.ofSeconds(60)), clock);

        redis.down = true;
        otpService.sendCode(PHONE, PURPOSE);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeastOnce()).sendMessage(eq(PHONE), message.capture());
        Matcher matcher = CODE_IN_MESSAGE.matcher(message.getValue());
        assertThat(matcher.find()).isTrue();
        String sent = matcher.group(1);

        String wrong = sent.equals("000000") ? "111111" : "000000";
        assertThatThrownBy(() -> otpService.verifyCode(PHONE, PURPOSE, wrong))
                .isInstanceOf(BusinessLogicException.class)
                .hasMessageContaining("Осталось попыток: 4");

        assertThatCode(() -> otpService.verifyCode(PHONE, PURPOSE, sent)).doesNotThrowAnyException();
        assertThatThrownBy(() -> otpService.verifyCode(PHONE, PURPOSE, sent))
                .as("код одноразовый и при сбое Redis")
                .isInstanceOf(BusinessLogicException.class);
    }

    /** Обёртка, которая при {@code down = true} ведёт себя как недоступный Redis. */
    private static final class FlakyRepository implements OtpRepository {
        final OtpRepository delegate;
        volatile boolean down;
        int calls;

        FlakyRepository(OtpRepository delegate) {
            this.delegate = delegate;
        }

        private void touch() {
            calls++;
            if (down) {
                throw new RedisConnectionFailureException("Redis is down");
            }
        }

        @Override
        public void save(OtpCode otpCode) {
            touch();
            delegate.save(otpCode);
        }

        @Override
        public Optional<OtpCode> find(String phone, OtpPurpose purpose) {
            touch();
            return delegate.find(phone, purpose);
        }

        @Override
        public void delete(String phone, OtpPurpose purpose) {
            touch();
            delegate.delete(phone, purpose);
        }

        @Override
        public OptionalInt incrementAttempts(String phone, OtpPurpose purpose) {
            touch();
            return delegate.incrementAttempts(phone, purpose);
        }
    }
}
