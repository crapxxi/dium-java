package com.dium.demo.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class WhatsAppConfig {
    @Value("${whatsapp.api.url}")
    private String apiUrl;


    @Bean
    public RestClient restClient() {
        // Таймауты обязательны: отправка кода идёт внутри запроса регистрации/входа
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));

        return RestClient.builder()
                .baseUrl(normalizeBaseUrl(apiUrl))
                .requestFactory(requestFactory)
                .build();
    }

    /** Убирает пробелы и завершающие слэши, чтобы не получить "//waInstance..." в итоговом URL. */
    static String normalizeBaseUrl(String url) {
        String trimmed = url == null ? "" : url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
