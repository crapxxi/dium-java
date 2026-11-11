package com.dium.demo.auth;

import com.dium.demo.enums.UserRole;
import com.dium.demo.exceptions.MessageDeliveryException;
import com.dium.demo.models.User;
import com.dium.demo.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Регистрация и коды подтверждения: то, что было сломано изначально. */
class RegistrationOtpIntegrationTest extends AbstractIntegrationTest {

    private static final String PHONE = "77075550101";

    private ResultActions register(String phone) throws Exception {
        return postJson("/api/v1/auth/register",
                "{\"phone\":\"" + phone + "\",\"name\":\"Мансур\",\"password\":\"" + PASSWORD + "\"}");
    }

    private ResultActions activate(String phone, String code) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/activate").param("phone", phone).param("code", code));
    }

    @Test
    void registrationFailsLoudlyWhenCodeCannotBeSent() throws Exception {
        doThrow(new MessageDeliveryException("Не удалось отправить сообщение в WhatsApp. Проверьте номер и попробуйте позже."))
                .when(senderService).sendMessage(anyString(), anyString());

        register(PHONE)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message").value("Не удалось отправить сообщение в WhatsApp. Проверьте номер и попробуйте позже."));

        // транзакция откатилась: «полузарегистрированного» пользователя нет, можно сразу повторить
        assertThat(userRepository.existsByPhone(PHONE)).isFalse();

        doNothing().when(senderService).sendMessage(anyString(), anyString());
        register(PHONE).andExpect(status().isOk());
        assertThat(userRepository.existsByPhone(PHONE)).isTrue();
    }

    @Test
    void phoneIsNormalizedConsistentlyAcrossRegisterActivateAndLogin() throws Exception {
        register("+7 (707) 555-01-01").andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value(PHONE));

        User stored = userRepository.findByPhone(PHONE).orElseThrow();
        assertThat(stored.getIsConfirmed()).isFalse();
        assertThat(stored.getRole()).isEqualTo(UserRole.CLIENT);

        // код ушёл на нормализованный номер; активация — в другом формате записи того же номера
        String code = lastCodeSentTo(PHONE);
        activate("8 707 555 01 01", code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        assertThat(userRepository.findByPhone(PHONE).orElseThrow().getIsConfirmed()).isTrue();

        login("87075550101", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void invalidPhoneIsRejected() throws Exception {
        register("12345").andExpect(status().isUnprocessableEntity());
        register("+1 202 555 0101").andExpect(status().isUnprocessableEntity());
        verify(senderService, never()).sendMessage(anyString(), anyString());
    }

    @Test
    void activationCodeIsInvalidatedAfterFiveWrongAttempts() throws Exception {
        register(PHONE).andExpect(status().isOk());
        String code = lastCodeSentTo(PHONE);

        for (int i = 0; i < 5; i++) {
            activate(PHONE, wrongCodeFor(code)).andExpect(status().isUnprocessableEntity());
        }
        activate(PHONE, code).andExpect(status().isUnprocessableEntity());

        assertThat(userRepository.findByPhone(PHONE).orElseThrow().getIsConfirmed()).isFalse();
    }

    @Test
    void resendIsLimitedToOncePerMinute() throws Exception {
        register(PHONE).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/resend-code").param("phone", PHONE))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        verify(senderService, times(1)).sendMessage(eq(PHONE), anyString());
    }

    @Test
    void passwordResetCodeCannotActivateAccountAndViceVersa() throws Exception {
        register(PHONE).andExpect(status().isOk());
        String activationCode = lastCodeSentTo(PHONE);

        // код активации не подходит для сброса пароля
        postJson("/api/v1/auth/reset-password",
                "{\"phone\":\"" + PHONE + "\",\"code\":\"" + activationCode + "\",\"newPassword\":\"hacked123\"}")
                .andExpect(status().isUnprocessableEntity());

        // запрос кода сброса не блокируется cooldown-ом кода активации (разные назначения)
        mockMvc.perform(post("/api/v1/auth/forgot-password").param("phone", PHONE))
                .andExpect(status().isOk());
        String resetCode = lastCodeSentTo(PHONE);

        if (!resetCode.equals(activationCode)) {
            activate(PHONE, resetCode).andExpect(status().isUnprocessableEntity());
        }

        postJson("/api/v1/auth/reset-password",
                "{\"phone\":\"" + PHONE + "\",\"code\":\"" + resetCode + "\",\"newPassword\":\"newpass123\"}")
                .andExpect(status().isOk());

        // код активации при этом остался действующим
        if (!resetCode.equals(activationCode)) {
            activate(PHONE, activationCode).andExpect(status().isOk());
        }
    }

    @Test
    void unconfirmedLoginSendsActivationCodeOnlyAfterCorrectPassword() throws Exception {
        createUser(PHONE, UserRole.CLIENT, false);

        login(PHONE, "wrong-password").andExpect(status().isUnauthorized());
        verify(senderService, never()).sendMessage(anyString(), anyString());

        login(PHONE, PASSWORD)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("ACCOUNT_LOCKED")));
        verify(senderService, times(1)).sendMessage(eq(PHONE), anyString());
    }

    @Test
    void unconfirmedNumberCanRegisterAgainInsteadOfDeadEnd() throws Exception {
        register(PHONE).andExpect(status().isOk());
        cacheManager.getCache("otpCodes").clear(); // «прошла минута»

        register(PHONE).andExpect(status().isOk());
        assertThat(userRepository.findAll()).hasSize(1);

        activate(PHONE, lastCodeSentTo(PHONE)).andExpect(status().isOk());

        // подтверждённый номер повторно зарегистрировать нельзя
        register(PHONE).andExpect(status().isForbidden());
    }
}
