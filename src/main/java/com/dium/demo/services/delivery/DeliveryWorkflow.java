package com.dium.demo.services.delivery;

import com.dium.demo.config.DeliveryProperties;
import com.dium.demo.enums.DeliveryStatus;
import com.dium.demo.enums.OrderStatus;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.models.Delivery;
import com.dium.demo.models.Order;
import com.dium.demo.models.User;
import com.dium.demo.repositories.DeliveryRepository;
import com.dium.demo.services.wallet.WalletOperations;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Переходы доставки по статусам. Здесь только «что меняется при переходе»;
 * кто имеет право на переход и верен ли код, проверяют сервисы до вызова.
 * Методы работают в транзакции вызывающего сервиса.
 */
@Component
@RequiredArgsConstructor
public class DeliveryWorkflow {
    private final DeliveryRepository deliveryRepository;
    private final DeliveryCodes codes;
    private final DeliveryNotifier notifier;
    private final DeliveryProperties properties;
    private final WalletOperations walletOperations;
    private final Clock clock;

    /** Заведение приняло заказ с адресом — заказ появляется в ленте курьеров. Повторный вызов ничего не создаёт. */
    public Delivery open(Order order) {
        return deliveryRepository.findByOrderId(order.getId())
                .orElseGet(() -> deliveryRepository.save(Delivery.builder()
                        .order(order)
                        .status(DeliveryStatus.SEARCHING_COURIER)
                        .courierFee(properties.fee())
                        .deliveryCode(codes.newSealedCode())
                        .createdAt(now())
                        .deadlineAt(now().plus(properties.timeouts().searching()))
                        .build()));
    }

    /** Курьер взял заказ: получает свой код, который покажет заведению. */
    public void assign(Delivery delivery, User courier) {
        moveTo(delivery, DeliveryStatus.ASSIGNED);
        delivery.setCourier(courier);
        delivery.setPickupCode(codes.newSealedCode());
        delivery.setPickupCodeAttempts(0);
        delivery.setAssignedAt(now());
        // Срок «забрать» идёт, только когда заказ готов: пока заведение готовит, курьер не виноват
        delivery.setDeadlineAt(isOrderReady(delivery) ? now().plus(properties.timeouts().pickup()) : null);
        notifier.courierAssigned(delivery);
    }

    /** Заведение отметило заказ готовым: у назначенного курьера начинается срок, чтобы его забрать. */
    public void orderReady(Delivery delivery) {
        if (delivery.getStatus() == DeliveryStatus.ASSIGNED) {
            delivery.setDeadlineAt(now().plus(properties.timeouts().pickup()));
        }
    }

    /** Курьер отказался или его снял администратор: заказ снова в ленте, старый код курьера недействителен. */
    public void release(Delivery delivery) {
        moveTo(delivery, DeliveryStatus.SEARCHING_COURIER);
        delivery.setCourier(null);
        delivery.setPickupCode(null);
        delivery.setPickupCodeAttempts(0);
        delivery.setAssignedAt(null);
        delivery.setDeadlineAt(now().plus(properties.timeouts().searching()));
    }

    /** Заведение отдало заказ курьеру. */
    public void pickUp(Delivery delivery) {
        requireOrderReady(delivery);
        moveTo(delivery, DeliveryStatus.PICKED_UP);
        delivery.setPickedUpAt(now());
        delivery.setDeadlineAt(now().plus(properties.timeouts().delivering()));
        notifier.pickedUp(delivery);
    }

    /** Клиент получил заказ: доставка и заказ завершены, курьеру засчитывается оплата. */
    public void deliver(Delivery delivery) {
        requireOrderReady(delivery);
        moveTo(delivery, DeliveryStatus.DELIVERED);
        delivery.setDeliveredAt(now());
        delivery.setDeadlineAt(null);
        delivery.getOrder().setStatus(OrderStatus.COMPLETED);
        // Баллы курьеру — только сейчас, когда передача подтверждена
        walletOperations.payCourier(delivery);
        notifier.delivered(delivery);
    }

    /**
     * Завершение администратором (например, клиент подтвердил получение по телефону).
     * Если заведение не успело отметить передачу, она записывается отдельным шагом,
     * чтобы журнал статусов и триггер переходов видели честную последовательность.
     */
    public void forceDeliver(Delivery delivery) {
        requireOrderReady(delivery);
        if (delivery.getStatus() == DeliveryStatus.ASSIGNED) {
            moveTo(delivery, DeliveryStatus.PICKED_UP);
            delivery.setPickedUpAt(now());
            deliveryRepository.saveAndFlush(delivery);
        }
        deliver(delivery);
    }

    /** Доставка отменена, заказ остаётся: если он оплачен баллами, баллы за доставку возвращаются клиенту. */
    public void cancel(Delivery delivery, String reason) {
        cancelWithOrder(delivery);
        walletOperations.refundDelivery(delivery);
        notifier.cancelled(delivery, reason);
    }

    /** Отмена вместе с заказом: об отмене заказа клиент уже уведомлён, второе сообщение не нужно. */
    public void cancelWithOrder(Delivery delivery) {
        moveTo(delivery, DeliveryStatus.CANCELLED);
        delivery.setCancelledAt(now());
        delivery.setDeadlineAt(null);
    }

    private void moveTo(Delivery delivery, DeliveryStatus next) {
        if (!delivery.getStatus().canMoveTo(next)) {
            throw new BusinessLogicException(
                    "Доставку в статусе " + delivery.getStatus() + " нельзя перевести в " + next);
        }
        delivery.setStatus(next);
    }

    private static boolean isOrderReady(Delivery delivery) {
        return delivery.getOrder().getStatus() == OrderStatus.READY;
    }

    private static void requireOrderReady(Delivery delivery) {
        if (delivery.getOrder().getStatus() != OrderStatus.READY) {
            throw new BusinessLogicException("Заказ ещё готовится: сначала заведение должно отметить его готовым");
        }
    }

    private Instant now() {
        return clock.instant();
    }
}
