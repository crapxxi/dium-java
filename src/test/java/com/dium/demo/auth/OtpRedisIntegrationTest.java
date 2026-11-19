package com.dium.demo.auth;

import com.dium.demo.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Полный контекст с настоящим Redis: код активации хранится в Redis, а не в памяти.
 * Запуск: {@code REDIS_TEST_HOST=localhost ./mvnw test} (см. {@code RedisOtpRepositoryImplTest}).
 */
@EnabledIfEnvironmentVariable(named = "REDIS_TEST_HOST", matches = ".+")
class OtpRedisIntegrationTest extends AbstractIntegrationTest {

    private static final String PHONE = "77075550177";
    private static final String KEY = "dium:otp:" + PHONE + ":ACTIVATION";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("otp.storage.redis-enabled", () -> "true");
        registry.add("spring.data.redis.host", () -> System.getenv("REDIS_TEST_HOST"));
    }

    @Test
    void activationCodeIsStoredInRedisAndConsumedOnce() throws Exception {
        redisTemplate.delete(KEY);

        postJson("/api/v1/auth/register",
                "{\"phone\":\"" + PHONE + "\",\"name\":\"Мансур\",\"password\":\"" + PASSWORD + "\"}")
                .andExpect(status().isOk());

        String code = lastCodeSentTo(PHONE);
        assertThat(redisTemplate.<String, String>opsForHash().get(KEY, "code")).isEqualTo(code);
        assertThat(cacheManager.getCache("otpCodes").get(PHONE + ":ACTIVATION")).as("в Caffeine копии нет").isNull();

        mockMvc.perform(post("/api/v1/auth/activate").param("phone", PHONE).param("code", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        assertThat(redisTemplate.hasKey(KEY)).as("использованный код удалён из Redis").isFalse();
    }
}
