package com.dium.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Настройки доставки ({@code delivery.*} в application.yml).
 *
 * @param fee                 цена доставки за заказ целиком, ₸: столько платит клиент и столько же получает
 *                            курьер. Не зависит ни от количества товаров, ни от заведения
 * @param codeLength          длина кодов передачи (код курьера и код клиента)
 * @param maxCodeAttempts     сколько неверных вводов кода допускается, потом код блокируется
 *                            и создаётся проблема для администратора
 * @param maxActivePerCourier сколько незавершённых доставок может вести один курьер
 * @param timeouts            сколько ждать на каждом этапе, прежде чем вмешается система
 * @param messages            тексты WhatsApp-уведомлений клиенту; пустой шаблон — без уведомления
 */
@ConfigurationProperties(prefix = "delivery")
public record DeliveryProperties(
        @DefaultValue("100") BigDecimal fee,
        @DefaultValue("4") int codeLength,
        @DefaultValue("5") int maxCodeAttempts,
        @DefaultValue("2") int maxActivePerCourier,
        @DefaultValue Timeouts timeouts,
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
        if (timeouts == null) {
            timeouts = new Timeouts(null, null, null);
        }
        if (messages == null) {
            messages = new Messages(null, null, null, null, null);
        }
    }

    /**
     * Сроки этапов доставки. Проверяются раз в минуту.
     *
     * @param searching  заказ никто не взял — доставка отменяется, клиент забирает сам
     *                   (баллы за доставку возвращаются)
     * @param pickup     курьер взял заказ, а он готов, но курьер не пришёл — курьера снимают,
     *                   заказ снова в ленте, администратор видит проблему. Отсчёт с момента, когда
     *                   заказ взят и готов: пока заведение готовит, курьер не виноват
     * @param delivering курьер забрал заказ, но так и не отдал клиенту — администратор видит проблему
     *                   (снять курьера нельзя: еда у него в руках)
     */
    public record Timeouts(Duration searching, Duration pickup, Duration delivering) {
        public Timeouts {
            searching = positiveOr(searching, Duration.ofMinutes(30), "searching");
            pickup = positiveOr(pickup, Duration.ofMinutes(20), "pickup");
            delivering = positiveOr(delivering, Duration.ofMinutes(60), "delivering");
        }

        private static Duration positiveOr(Duration value, Duration fallback, String name) {
            if (value == null) {
                return fallback;
            }
            if (value.isNegative() || value.isZero()) {
                throw new IllegalArgumentException("delivery.timeouts." + name + " must be positive");
            }
            return value;
        }
    }

    /**
     * Плейсхолдеры: {@code {orderId}}, {@code {courierName}}, {@code {code}} (код клиента), {@code {reason}}.
     *
     * @param courierAssigned курьер взял заказ
     * @param pickedUp        курьер забрал заказ в заведении (сообщение содержит код клиента)
     * @param delivered       заказ доставлен
     * @param cancelled       доставка отменена
     * @param courierReleased курьер не пришёл за заказом и снят — ищем другого
     */
    public record Messages(String courierAssigned, String pickedUp, String delivered, String cancelled,
                           String courierReleased) {
    }
}
