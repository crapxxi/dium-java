package com.dium.demo.services.delivery;

import com.dium.demo.config.DeliveryProperties;
import com.dium.demo.models.Delivery;
import com.dium.demo.services.SenderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

/**
 * WhatsApp-уведомления клиенту о ходе доставки. Отправляются только после коммита транзакции,
 * чтобы клиент не получил «доставлено», если сохранение не удалось. Ошибка отправки не ломает
 * доставку: все данные есть в приложении.
 */
@Component
@RequiredArgsConstructor
public class DeliveryNotifier {
    private final SenderService senderService;
    private final DeliveryProperties properties;
    private final DeliveryCodes codes;

    public void courierAssigned(Delivery delivery) {
        notifyCustomer(delivery, properties.messages().courierAssigned(), Map.of());
    }

    /** Курьер забрал заказ: напоминаем клиенту его код (называть только при получении). */
    public void pickedUp(Delivery delivery) {
        notifyCustomer(delivery, properties.messages().pickedUp(),
                Map.of("code", codes.reveal(delivery.getDeliveryCode())));
    }

    public void delivered(Delivery delivery) {
        notifyCustomer(delivery, properties.messages().delivered(), Map.of());
    }

    public void cancelled(Delivery delivery, String reason) {
        notifyCustomer(delivery, properties.messages().cancelled(), Map.of("reason", reason == null ? "" : reason));
    }

    /** Курьер не пришёл за заказом и снят: заказ снова ищет курьера. */
    public void courierReleased(Delivery delivery) {
        notifyCustomer(delivery, properties.messages().courierReleased(), Map.of());
    }

    private void notifyCustomer(Delivery delivery, String template, Map<String, String> extra) {
        if (template == null || template.isBlank()) {
            return;
        }
        String message = template
                .replace("{orderId}", String.valueOf(delivery.getOrder().getId()))
                .replace("{courierName}", delivery.getCourier() == null ? "" : delivery.getCourier().getName());
        for (Map.Entry<String, String> value : extra.entrySet()) {
            message = message.replace("{" + value.getKey() + "}", value.getValue());
        }
        String phone = delivery.getOrder().getUser().getPhone();
        String text = message;
        afterCommit(() -> senderService.trySendMessage(phone, text));
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
