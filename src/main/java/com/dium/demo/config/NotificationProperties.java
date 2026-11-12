package com.dium.demo.config;

import com.dium.demo.enums.OrderStatus;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Тексты уведомлений ({@code notifications.*} в application.yml). Меняются без правки кода.
 * <p>
 * Плейсхолдеры: {@code {orderId}}, {@code {paymentFrom}}, {@code {totalSum}}, {@code {comment}}.
 *
 * @param newOrder    сообщение владельцу заведения о новом заказе
 * @param orderStatus сообщения клиенту при переходе заказа в статус; для статуса без шаблона
 *                    уведомление не отправляется
 */
@ConfigurationProperties(prefix = "notifications")
public record NotificationProperties(
        String newOrder,
        Map<OrderStatus, String> orderStatus
) {
    public NotificationProperties {
        orderStatus = orderStatus == null ? Map.of() : Map.copyOf(orderStatus);
    }
}
