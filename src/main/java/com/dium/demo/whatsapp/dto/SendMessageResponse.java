package com.dium.demo.whatsapp.dto;

/**
 * Успешный ответ Green API на sendMessage: {@code {"idMessage": "3EB0C767D097B7C7C030"}}.
 */
public record SendMessageResponse(
        String idMessage
) { }
