package com.dium.demo.services.order;

import com.dium.demo.config.NotificationProperties;
import com.dium.demo.dto.responses.OrderResponse;
import com.dium.demo.models.Order;
import com.dium.demo.services.SenderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/** Уведомления о заказах. Тексты берутся из {@link NotificationProperties}. */
@Service
@RequiredArgsConstructor
public class OrderNotificationService {
    private final SenderService senderService;
    private final NotificationProperties properties;

    /** Сообщает владельцу заведения о новом заказе. */
    public void notifyVenueAboutNewOrder(OrderResponse order) {
        send(order.venueOwnerPhone(), properties.newOrder(), Map.of(
                "orderId", String.valueOf(order.id()),
                "paymentFrom", text(order.paymentFrom()),
                "totalSum", money(order.totalSum()),
                "comment", text(order.comment())
        ));
    }

    /** Сообщает клиенту о текущем статусе заказа, если для статуса задан шаблон. */
    public void notifyCustomerAboutStatus(Order order) {
        send(order.getUser().getPhone(), properties.orderStatus().get(order.getStatus()), Map.of(
                "orderId", String.valueOf(order.getId())
        ));
    }

    private void send(String phone, String template, Map<String, String> values) {
        if (template == null || template.isBlank()) {
            return;
        }
        String message = template;
        for (Map.Entry<String, String> value : values.entrySet()) {
            message = message.replace("{" + value.getKey() + "}", value.getValue());
        }
        senderService.trySendMessage(phone, message);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String money(BigDecimal value) {
        return value == null ? "" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
