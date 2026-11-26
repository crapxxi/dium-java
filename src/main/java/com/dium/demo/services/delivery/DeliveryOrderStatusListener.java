package com.dium.demo.services.delivery;

import com.dium.demo.enums.DeliveryStatus;
import com.dium.demo.enums.OrderStatus;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.models.Order;
import com.dium.demo.repositories.DeliveryRepository;
import com.dium.demo.services.order.OrderStatusListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Связь кухни и доставки:
 * <ul>
 *     <li>заведение приняло заказ с адресом — заказ появляется в ленте курьеров;</li>
 *     <li>пока доставка не завершена, заведение не может само отметить заказ выданным —
 *     заказ завершается, когда курьер введёт код клиента;</li>
 *     <li>заказ отменён — отменяется и доставка.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class DeliveryOrderStatusListener implements OrderStatusListener {
    private final DeliveryRepository deliveryRepository;
    private final DeliveryWorkflow workflow;

    @Override
    public void beforeStatusChange(Order order, OrderStatus next) {
        if (next == OrderStatus.COMPLETED
                && deliveryRepository.existsByOrderIdAndStatusIn(order.getId(), DeliveryStatus.ACTIVE)) {
            throw new BusinessLogicException(
                    "Заказ с доставкой завершится сам, когда курьер передаст его клиенту по коду");
        }
    }

    @Override
    public void afterStatusChange(Order order, OrderStatus previous) {
        if (order.getStatus() == OrderStatus.PREPARING && hasText(order.getAddress())) {
            workflow.open(order);
        } else if (order.getStatus() == OrderStatus.READY) {
            deliveryRepository.findByOrderId(order.getId()).ifPresent(workflow::orderReady);
        } else if (order.getStatus() == OrderStatus.CANCELLED) {
            deliveryRepository.findByOrderId(order.getId())
                    .filter(delivery -> delivery.getStatus().isActive())
                    .ifPresent(workflow::cancelWithOrder);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
