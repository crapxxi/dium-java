package com.dium.demo.whatsapp;

import com.dium.demo.exceptions.MessageDeliveryException;
import com.dium.demo.services.SenderService;
import com.dium.demo.utils.PhoneNormalizer;
import com.dium.demo.whatsapp.dto.SendMessageResponse;
import com.dium.demo.whatsapp.dto.SendSmsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@RequiredArgsConstructor
@Slf4j
@Service
public class WhatsAppService implements SenderService {

    static final String USER_MESSAGE =
            "Не удалось отправить сообщение в WhatsApp. Проверьте номер и попробуйте позже.";

    @Value("${whatsapp.api.instance.id}")
    private String instanceId;

    @Value("${whatsapp.api.instance.token}")
    private String apiToken;

    private final RestClient restClient;

    /**
     * Отправляет сообщение через Green API.
     * Успех — только HTTP 2xx <b>и</b> непустой {@code idMessage} в ответе.
     *
     * @throws MessageDeliveryException если сообщение не было принято Green API
     */
    @Override
    public void sendMessage(String phone, String message) {
        if (isBlank(instanceId) || isBlank(apiToken)) {
            log.error("WhatsApp не настроен: задайте WHATSAPP_API_URL, WHATSAPP_INSTANCE_ID и WHATSAPP_API_TOKEN");
            throw new MessageDeliveryException(USER_MESSAGE);
        }

        final String chatId = PhoneNormalizer.normalize(phone) + "@c.us";
        final SendSmsRequest payload = new SendSmsRequest(chatId, message);

        final ResponseEntity<SendMessageResponse> response;
        try {
            response = restClient.post()
                    .uri("/waInstance{instanceId}/sendMessage/{apiToken}", instanceId, apiToken)
                    .body(payload)
                    .retrieve()
                    .toEntity(SendMessageResponse.class);
        } catch (RestClientResponseException e) {
            // 4xx/5xx от Green API: 401 — неверный токен/инстанс, 466 — исчерпан лимит тарифа и т.д.
            log.error("Green API отклонил сообщение для {}: HTTP {} {}",
                    chatId, e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new MessageDeliveryException(USER_MESSAGE, e);
        } catch (RestClientException e) {
            // Сеть/таймаут/нечитаемый ответ. Сообщение исключения содержит URL с токеном — не логируем его.
            Throwable root = e.getMostSpecificCause();
            log.error("Green API недоступен при отправке для {}: {} ({})",
                    chatId, e.getClass().getSimpleName(), root.getClass().getSimpleName());
            throw new MessageDeliveryException(USER_MESSAGE, e);
        }

        SendMessageResponse body = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || body == null || isBlank(body.idMessage())) {
            log.error("Green API не подтвердил отправку для {}: HTTP {}, idMessage отсутствует",
                    chatId, response.getStatusCode().value());
            throw new MessageDeliveryException(USER_MESSAGE);
        }

        log.info("Сообщение отправлено на {} (idMessage={})", chatId, body.idMessage());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
