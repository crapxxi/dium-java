package com.dium.demo.services.order;

import com.dium.demo.enums.OrderStatus;
import com.dium.demo.models.Order;

/**
 * Реакция других модулей на смену статуса заказа заведением (принят, готов, выдан, отменён).
 * Модуль подключается бином этого интерфейса, {@code OrderService} о нём не знает.
 * Оба метода вызываются в транзакции смены статуса.
 */
public interface OrderStatusListener {
    /** До смены статуса. Исключение запрещает переход. */
    default void beforeStatusChange(Order order, OrderStatus next) {
    }

    /** После смены статуса: {@code order.getStatus()} уже новый. */
    default void afterStatusChange(Order order, OrderStatus previous) {
    }
}
