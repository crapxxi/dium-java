package com.dium.demo.services.wallet;

import com.dium.demo.enums.PaymentMethod;
import com.dium.demo.enums.WalletTransactionType;
import com.dium.demo.models.Delivery;
import com.dium.demo.models.Order;
import com.dium.demo.repositories.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Баллы в жизни заказа и доставки. У каждой операции свой ключ, поэтому повторный вызов
 * (повтор запроса, второй обработчик того же события) ничего не начислит и не спишет дважды.
 */
@Component
@RequiredArgsConstructor
public class WalletOperations {
    private final WalletLedger ledger;
    private final WalletTransactionRepository transactionRepository;
    private final PointsPricing pricing;

    /** Заказ оформлен с оплатой баллами: списываем сразу, до того как заведение его увидит. */
    public void payOrder(Order order) {
        ledger.post(WalletLedger.Posting.builder()
                .userId(order.getUser().getId())
                .amount(-order.getPointsSpent())
                .type(WalletTransactionType.ORDER_PAYMENT)
                .idempotencyKey("order-payment:" + order.getId())
                .orderId(order.getId())
                .note("Заказ #" + order.getId() + " в «" + order.getVenue().getName() + "»")
                .build());
    }

    /** Заказ отменён (заведение отказалось или не приняло вовремя): возвращаем всё, что ещё не вернули. */
    public void refundOrder(Order order) {
        long remaining = notRefundedYet(order);
        if (remaining <= 0) {
            return;
        }
        ledger.post(WalletLedger.Posting.builder()
                .userId(order.getUser().getId())
                .amount(remaining)
                .type(WalletTransactionType.ORDER_REFUND)
                .idempotencyKey("order-refund:" + order.getId())
                .orderId(order.getId())
                .note("Возврат за отменённый заказ #" + order.getId())
                .build());
    }

    /** Доставку отменили, а заказ остался (заберут сами): возвращаем баллы, списанные за доставку. */
    public void refundDelivery(Delivery delivery) {
        Order order = delivery.getOrder();
        if (order.getPaymentMethod() != PaymentMethod.POINTS || order.getPointsDelivery() == null) {
            return;
        }
        long amount = Math.min(order.getPointsDelivery(), notRefundedYet(order));
        if (amount <= 0) {
            return;
        }
        ledger.post(WalletLedger.Posting.builder()
                .userId(order.getUser().getId())
                .amount(amount)
                .type(WalletTransactionType.ORDER_REFUND)
                .idempotencyKey("delivery-refund:" + delivery.getId())
                .orderId(order.getId())
                .deliveryId(delivery.getId())
                .note("Возврат за отменённую доставку заказа #" + order.getId())
                .build());
    }

    /**
     * Курьер доставил заказ — подтверждено кодом клиента или администратором.
     * Начисляется только здесь: до этого момента баллы курьеру не положены.
     */
    public void payCourier(Delivery delivery) {
        long amount = pricing.earningInPoints(delivery.getCourierFee());
        if (amount <= 0 || delivery.getCourier() == null) {
            return;
        }
        ledger.post(WalletLedger.Posting.builder()
                .userId(delivery.getCourier().getId())
                .amount(amount)
                .type(WalletTransactionType.DELIVERY_EARNING)
                .idempotencyKey("delivery-earning:" + delivery.getId())
                .orderId(delivery.getOrder().getId())
                .deliveryId(delivery.getId())
                .note("Доставка заказа #" + delivery.getOrder().getId())
                .build());
    }

    private long notRefundedYet(Order order) {
        if (order.getPaymentMethod() != PaymentMethod.POINTS || order.getPointsSpent() == null) {
            return 0;
        }
        long refunded = transactionRepository.sumByOrderAndType(order.getId(), WalletTransactionType.ORDER_REFUND);
        return order.getPointsSpent() - refunded;
    }
}
