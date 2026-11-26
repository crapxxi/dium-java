package com.dium.demo.services.wallet;

import com.dium.demo.enums.OrderStatus;
import com.dium.demo.enums.PaymentMethod;
import com.dium.demo.models.Order;
import com.dium.demo.services.order.OrderStatusListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Отменённый заказ, оплаченный баллами, возвращает баллы покупателю. */
@Component
@RequiredArgsConstructor
public class WalletOrderStatusListener implements OrderStatusListener {
    private final WalletOperations operations;

    @Override
    public void afterStatusChange(Order order, OrderStatus previous) {
        if (order.getStatus() == OrderStatus.CANCELLED && order.getPaymentMethod() == PaymentMethod.POINTS) {
            operations.refundOrder(order);
        }
    }
}
