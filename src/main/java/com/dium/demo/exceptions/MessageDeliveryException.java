package com.dium.demo.exceptions;

/**
 * Сообщение (например, код подтверждения) не удалось доставить через внешний сервис.
 * Клиент получает HTTP 502 с понятным текстом; технические детали остаются только в логах.
 */
public class MessageDeliveryException extends RuntimeException {
    public MessageDeliveryException(String message) {
        super(message);
    }

    public MessageDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
