package com.dium.demo.services;

import org.slf4j.LoggerFactory;

public interface SenderService {
    /**
     * Отправляет сообщение.
     *
     * @throws com.dium.demo.exceptions.MessageDeliveryException если сообщение не доставлено
     */
    void sendMessage(String user, String message);

    /**
     * «Best effort» отправка для уведомлений, которые не должны ломать основную операцию
     * (например, смену статуса заказа). Ошибка логируется, но не пробрасывается.
     *
     * @return true, если сообщение отправлено
     */
    default boolean trySendMessage(String user, String message) {
        try {
            sendMessage(user, message);
            return true;
        } catch (RuntimeException e) {
            LoggerFactory.getLogger(SenderService.class)
                    .warn("Уведомление не отправлено: {}", e.getMessage());
            return false;
        }
    }
}
