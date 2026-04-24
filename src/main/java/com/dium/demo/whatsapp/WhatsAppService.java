package com.dium.demo.whatsapp;

import com.dium.demo.whatsapp.dto.SendSmsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
@Slf4j
@Service
public class WhatsAppService {
    @Value("${whatsapp.api.instance.id}")
    private String instanceId;

    @Value("${whatsapp.api.instance.token}")
    private String apiToken;

    private final RestClient restClient;

    public void sendMessage(String phone, String message) {
        String processedPhone = phone.replaceAll("\\D", "");
        if (processedPhone.startsWith("8") && processedPhone.length() == 11) {
            processedPhone = "7" + processedPhone.substring(1);
        }
        final String finalPhone = processedPhone + "@c.us";

        SendSmsRequest payload = new SendSmsRequest(
                finalPhone,
                message
        );

        try {
           String response = restClient.post()
                   .uri( String.format("/waInstance%s/sendMessage/%s", instanceId, apiToken) )
                   .body(payload)
                   .retrieve()
                   .body(String.class);


            log.info("Ответ от WhatsApp: {}", response);

            if (response != null && response.contains("error_code")) {
                log.error("Ошибка WhatsApp для номера {}: {}", finalPhone, response);
            } else {
                log.info("Сообщения успешно отправлено на номер {}", finalPhone);
            }

        } catch (Exception e) {
            log.error("Критическая ошибка при отправке сообщения: {}", e.getMessage());
        }
    }
}
