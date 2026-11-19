package com.dium.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;

/**
 * Настройки доставки ({@code delivery.*} в application.yml).
 *
 * @param fee                 цена доставки за заказ целиком, ₸: столько платит клиент и столько же получает
 *                            курьер. Не зависит ни от количества товаров, ни от заведения
 * @param codeLength          длина кодов передачи (код курьера и код клиента)
 * @param maxCodeAttempts     сколько неверных вводов кода допускается, потом код блокируется
 *                            и создаётся проблема для администратора
 * @param maxActivePerCourier сколько незавершённых доставок может вести один курьер
 * @param messages            тексты WhatsApp-уведомлений клиенту; пустой шаблон — без уведомления
 */
@ConfigurationProperties(prefix = "delivery")
public record DeliveryProperties(
        @DefaultValue("100") BigDecimal fee,
        @DefaultValue("4") int codeLength,
        @DefaultValue("5") int maxCodeAttempts,
        @DefaultValue("2") int maxActivePerCourier,
        @DefaultValue Messages messages
) {
    public DeliveryProperties {
        if (fee == null || fee.signum() < 0) {
            throw new IllegalArgumentException("delivery.fee must be >= 0");
        }
        if (codeLength < 4 || codeLength > 8) {
            throw new IllegalArgumentException("delivery.code-length must be between 4 and 8");
        }
        if (maxCodeAttempts < 1 || maxActivePerCourier < 1) {
            throw new IllegalArgumentException("delivery.max-code-attempts and max-active-per-courier must be >= 1");
        }
        if (messages == null) {
            messages = new Messages(null, null, null, null);
        }
    }

    /**
     * Плейсхолдеры: {@code {orderId}}, {@code {courierName}}, {@code {code}} (код клиента), {@code {reason}}.
     *
     * @param courierAssigned курьер взял заказ
     * @param pickedUp        курьер забрал заказ в заведении (сообщение содержит код клиента)
     * @param delivered       заказ доставлен
     * @param cancelled       доставка отменена
     */
    public record Messages(String courierAssigned, String pickedUp, String delivered, String cancelled) {
    }
}
