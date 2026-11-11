package com.dium.demo.support;

import com.dium.demo.enums.UserRole;
import com.dium.demo.models.User;
import com.dium.demo.repositories.UserRepository;
import com.dium.demo.services.SenderService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Полный контекст приложения на H2. WhatsApp заменён моком: коды «достаются» из текста сообщения,
 * которое приложение пыталось отправить.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final String PASSWORD = "secret123";
    private static final Pattern CODE_IN_MESSAGE = Pattern.compile("\\*(\\d{6})\\*");

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected CacheManager cacheManager;

    @MockitoBean
    protected SenderService senderService;

    @BeforeEach
    void cleanState() {
        userRepository.deleteAll();
        cacheManager.getCache("otpCodes").clear();
    }

    protected User createUser(String phone, UserRole role) {
        return createUser(phone, role, true);
    }

    protected User createUser(String phone, UserRole role, boolean confirmed) {
        User user = new User();
        user.setPhone(phone);
        user.setName("Test " + role);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setIsConfirmed(confirmed);
        return userRepository.save(user);
    }

    /** Последний код, «отправленный» на номер через WhatsApp. */
    protected String lastCodeSentTo(String phone) {
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(senderService, atLeastOnce()).sendMessage(eq(phone), message.capture());
        List<String> all = message.getAllValues();
        Matcher matcher = CODE_IN_MESSAGE.matcher(all.get(all.size() - 1));
        assertThat(matcher.find()).as("код в тексте сообщения").isTrue();
        return matcher.group(1);
    }

    protected static String wrongCodeFor(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    protected ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions postJson(String url, String json, String bearerToken) throws Exception {
        return mockMvc.perform(withBearer(post(url), bearerToken)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions getWithToken(String url, String bearerToken) throws Exception {
        return mockMvc.perform(withBearer(get(url), bearerToken));
    }

    private static MockHttpServletRequestBuilder withBearer(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    protected ResultActions login(String phone, String password) throws Exception {
        return postJson("/api/v1/auth/login",
                "{\"phone\":\"" + phone + "\",\"password\":\"" + password + "\"}");
    }

    protected ResultActions verifyTwoFactor(String tempToken, String code, String method) throws Exception {
        String methodPart = method == null ? "" : ",\"method\":\"" + method + "\"";
        return postJson("/api/v1/auth/2fa/verify",
                "{\"tempToken\":\"" + tempToken + "\",\"code\":\"" + code + "\"" + methodPart + "}");
    }

    protected static <T> T json(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), path);
    }
}
