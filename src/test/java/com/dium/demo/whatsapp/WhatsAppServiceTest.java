package com.dium.demo.whatsapp;

import com.dium.demo.exceptions.MessageDeliveryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WhatsAppServiceTest {

    private static final String URL = "https://green.test/waInstance1101/sendMessage/secret-token";

    private MockRestServiceServer server;
    private WhatsAppService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://green.test");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new WhatsAppService(builder.build());
        ReflectionTestUtils.setField(service, "instanceId", "1101");
        ReflectionTestUtils.setField(service, "apiToken", "secret-token");
    }

    @Test
    void succeedsWhenGreenApiReturnsIdMessage() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.chatId").value("77071234567@c.us"))
                .andExpect(jsonPath("$.message").value("hello"))
                .andRespond(withSuccess("{\"idMessage\":\"3EB0C767D097B7C7C030\"}", MediaType.APPLICATION_JSON));

        // номер в «человеческом» формате нормализуется перед отправкой
        assertThatCode(() -> service.sendMessage("8 (707) 123-45-67", "hello")).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void failsWhenResponseIs200ButHasNoIdMessage() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"somethingElse\":true}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.sendMessage("77071234567", "hello"))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void failsOnUnauthorizedInstance() {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> service.sendMessage("77071234567", "hello"))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void failsOnQuotaExceeded() {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.valueOf(500))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"quota exceeded\"}"));

        assertThatThrownBy(() -> service.sendMessage("77071234567", "hello"))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void errorMessageNeverContainsApiToken() {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> service.sendMessage("77071234567", "hello"))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageNotContaining("secret-token");
    }

    @Test
    void failsFastWhenNotConfigured() {
        ReflectionTestUtils.setField(service, "apiToken", "");

        assertThatThrownBy(() -> service.sendMessage("77071234567", "hello"))
                .isInstanceOf(MessageDeliveryException.class);
        // к Green API не было ни одного запроса
        server.verify();
    }
}
