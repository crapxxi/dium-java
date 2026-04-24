package com.dium.demo.whatsapp.dto;

public record SendSmsRequest(
        String chatId,
        String message
) { }
