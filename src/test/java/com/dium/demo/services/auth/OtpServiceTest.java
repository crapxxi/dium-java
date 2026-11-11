package com.dium.demo.services.auth;

import com.dium.demo.config.OtpProperties;
import com.dium.demo.enums.OtpPurpose;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.exceptions.MessageDeliveryException;
import com.dium.demo.exceptions.TooManyRequestsException;
import com.dium.demo.repositories.impl.CaffeineOtpRepositoryImpl;
import com.dium.demo.services.SenderService;
import com.dium.demo.services.auth.impl.OtpServiceImpl;
import com.dium.demo.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class OtpServiceTest {

    private static final String PHONE = "77071234567";
    private static final Pattern CODE_IN_MESSAGE = Pattern.compile("\\*(\\d{6})\\*");

    private SenderService senderService;
    private MutableClock clock;
    private OtpService otpService;

    @BeforeEach
    void setUp() {
        senderService = mock(SenderService.class);
        clock = new MutableClock();
        OtpProperties properties = new OtpProperties(6, Duration.ofMinutes(5), 5, Duration.ofSeconds(60));
        otpService = new OtpServiceImpl(
                new CaffeineOtpRepositoryImpl(new ConcurrentMapCacheManager("otpCodes")),
                senderService,
                properties,
                clock);
    }

    /** Достаёт из последнего «отправленного» WhatsApp-сообщения сам код. */
    private String lastSentCode() {
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(senderService, atLeastOnce()).sendMessage(eq(PHONE), message.capture());
        Matcher matcher = CODE_IN_MESSAGE.matcher(message.getValue());
        assertThat(matcher.find()).as("код в тексте сообщения").isTrue();
        return matcher.group(1);
    }

    private static String wrongCodeFor(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    @Test
    void sendsSixDigitCodeAndAcceptsItOnlyOnce() {
        otpService.sendCode(PHONE, OtpPurpose.ACTIVATION);
        String code = lastSentCode();

        assertThatCode(() -> otpService.verifyCode(PHONE, OtpPurpose.ACTIVATION, code))
                .doesNotThrowAnyException();

        // повторное использование того же кода запрещено
        assertThatThrownBy(() -> otpService.verifyCode(PHONE, OtpPurpose.ACTIVATION, code))
                .isInstanceOf(BusinessLogicException.class);
    }

    @Test
    void codeExpiresAfterFiveMinutes() {
        otpService.sendCode(PHONE, OtpPurpose.ACTIVATION);
        String code = lastSentCode();

        clock.advance(Duration.ofMinutes(5).plusSeconds(1));

        assertThatThrownBy(() -> otpService.verifyCode(PHONE, OtpPurpose.ACTIVATION, code))
                .isInstanceOf(BusinessLogicException.class)
                .hasMessageContaining("истёк");
    }

    @Test
    void codeIsStillValidJustBeforeExpiry() {
        otpService.sendCode(PHONE, OtpPurpose.ACTIVATION);
        String code = lastSentCode();

        clock.advance(Duration.ofMinutes(5).minusSeconds(1));

        assertThatCode(() -> otpService.verifyCode(PHONE, OtpPurpose.ACTIVATION, code))
                .doesNotThrowAnyException();
    }

    @Test
    void codeIsInvalidatedAfterFiveWrongAttempts() {
        otpService.sendCode(PHONE, OtpPurpose.ACTIVATION);
        String code = lastSentCode();
        String wrong = wrongCodeFor(code);

        for (int i = 1; i <= 5; i++) {
            assertThatThrownBy(() -> otpService.verifyCode(PHONE, OtpPurpose.ACTIVATION, wrong))
                    .isInstanceOf(BusinessLogicException.class)
                    .hasMessageContaining("Неверный код");
        }

        // даже правильный код после 5 неудачных попыток больше не принимается
        assertThatThrownBy(() -> otpService.verifyCode(PHONE, OtpPurpose.ACTIVATION, code))
                .isInstanceOf(BusinessLogicException.class);
    }

    @Test
    void correctCodeWorksWithinAttemptLimit() {
        otpService.sendCode(PHONE, OtpPurpose.ACTIVATION);
        String code = lastSentCode();
        String wrong = wrongCodeFor(code);

        for (int i = 1; i <= 4; i++) {
            assertThatThrownBy(() -> otpService.verifyCode(PHONE, OtpPurpose.ACTIVATION, wrong))
                    .isInstanceOf(BusinessLogicException.class);
        }

        assertThatCode(() -> otpService.verifyCode(PHONE, OtpPurpose.ACTIVATION, code))
                .doesNotThrowAnyException();
    }

    @Test
    void codesAreSeparatedByPurpose() {
        otpService.sendCode(PHONE, OtpPurpose.ACTIVATION);
        String activationCode = lastSentCode();

        // код активации не подходит для входа и сброса пароля
        assertThatThrownBy(() -> otpService.verifyCode(PHONE, OtpPurpose.LOGIN_2FA, activationCode))
                .isInstanceOf(BusinessLogicException.class);
        assertThatThrownBy(() -> otpService.verifyCode(PHONE, OtpPurpose.PASSWORD_RESET, activationCode))
                .isInstanceOf(BusinessLogicException.class);

        // запрос кода для другой цели не перезаписывает и не блокирует код активации
        otpService.sendCode(PHONE, OtpPurpose.PASSWORD_RESET);
        verify(senderService, times(2)).sendMessage(eq(PHONE), anyString());

        assertThatCode(() -> otpService.verifyCode(PHONE, OtpPurpose.ACTIVATION, activationCode))
                .doesNotThrowAnyException();
    }

    @Test
    void resendIsBlockedForSixtySeconds() {
        otpService.sendCode(PHONE, OtpPurpose.ACTIVATION);

        clock.advance(Duration.ofSeconds(59));
        assertThatThrownBy(() -> otpService.sendCode(PHONE, OtpPurpose.ACTIVATION))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(e -> assertThat(((TooManyRequestsException) e).getRetryAfterSeconds()).isEqualTo(1));
        verify(senderService, times(1)).sendMessage(eq(PHONE), anyString());

        clock.advance(Duration.ofSeconds(1));
        assertThatCode(() -> otpService.sendCode(PHONE, OtpPurpose.ACTIVATION)).doesNotThrowAnyException();
        verify(senderService, times(2)).sendMessage(eq(PHONE), anyString());
    }

    @Test
    void resendReplacesPreviousCode() {
        otpService.sendCode(PHONE, OtpPurpose.ACTIVATION);
        String first = lastSentCode();

        clock.advance(Duration.ofSeconds(61));
        otpService.sendCode(PHONE, OtpPurpose.ACTIVATION);
        String second = lastSentCode();

        if (!first.equals(second)) {
            assertThatThrownBy(() -> otpService.verifyCode(PHONE, OtpPurpose.ACTIVATION, first))
                    .isInstanceOf(BusinessLogicException.class);
        }
        assertThatCode(() -> otpService.verifyCode(PHONE, OtpPurpose.ACTIVATION, second))
                .doesNotThrowAnyException();
    }

    @Test
    void deliveryFailurePropagatesAndDoesNotStartCooldown() {
        doThrow(new MessageDeliveryException("WhatsApp недоступен"))
                .doNothing()
                .when(senderService).sendMessage(eq(PHONE), anyString());

        assertThatThrownBy(() -> otpService.sendCode(PHONE, OtpPurpose.ACTIVATION))
                .isInstanceOf(MessageDeliveryException.class);

        // недоставленный код не должен блокировать немедленную повторную попытку
        assertThatCode(() -> otpService.sendCode(PHONE, OtpPurpose.ACTIVATION)).doesNotThrowAnyException();
    }

    @Test
    void verifyWithoutRequestedCodeFails() {
        doNothing().when(senderService).sendMessage(anyString(), anyString());

        assertThatThrownBy(() -> otpService.verifyCode(PHONE, OtpPurpose.ACTIVATION, "123456"))
                .isInstanceOf(BusinessLogicException.class);
        assertThatThrownBy(() -> otpService.verifyCode(PHONE, OtpPurpose.ACTIVATION, null))
                .isInstanceOf(BusinessLogicException.class);
    }
}
