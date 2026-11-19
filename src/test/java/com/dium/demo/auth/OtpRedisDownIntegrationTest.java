package com.dium.demo.auth;

import com.dium.demo.repositories.OtpRepository;
import com.dium.demo.repositories.impl.ResilientOtpRepository;
import com.dium.demo.support.AbstractIntegrationTest;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Redis включён, но недоступен: регистрация и подтверждение кода всё равно работают (коды в Caffeine). */
@TestPropertySource(properties = {
        "otp.storage.redis-enabled=true",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=1", // там никто не слушает
})
class OtpRedisDownIntegrationTest extends AbstractIntegrationTest {

    private static final String PHONE = "77075550199";

    @Autowired
    private OtpRepository otpRepository;
    @Autowired
    private CircuitBreaker otpRedisCircuitBreaker;

    @Test
    void registrationAndActivationWorkWithoutRedis() throws Exception {
        assertThat(otpRepository).isInstanceOf(ResilientOtpRepository.class);

        postJson("/api/v1/auth/register",
                "{\"phone\":\"" + PHONE + "\",\"name\":\"Мансур\",\"password\":\"" + PASSWORD + "\"}")
                .andExpect(status().isOk());

        String code = lastCodeSentTo(PHONE);
        mockMvc.perform(post("/api/v1/auth/activate").param("phone", PHONE).param("code", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        assertThat(otpRedisCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }
}
