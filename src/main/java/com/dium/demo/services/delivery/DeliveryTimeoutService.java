package com.dium.demo.services.delivery;

import com.dium.demo.config.DeliveryProperties;
import com.dium.demo.enums.DeliveryIssueType;
import com.dium.demo.enums.DeliveryStatus;
import com.dium.demo.enums.OrderStatus;
import com.dium.demo.models.Delivery;
import com.dium.demo.repositories.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Следит, чтобы доставка не зависала. Раз в минуту берёт доставки с истёкшим сроком этапа:
 * <ul>
 *     <li>никто не взял заказ — доставка отменяется, клиент получает сообщение и забирает заказ
 *     сам; если платил баллами, баллы за доставку возвращаются;</li>
 *     <li>заказ готов, а курьер не пришёл — курьер снимается, заказ снова в ленте, клиенту
 *     сообщение, администратору — проблема «курьер не пришёл»;</li>
 *     <li>курьер забрал заказ и не отдал клиенту — администратору проблема «не доставлен»
 *     (снимать курьера нельзя: заказ у него).</li>
 * </ul>
 * Каждая доставка обрабатывается в своей транзакции и заново проверяется под ней: если курьер
 * успел что-то сделать, его действие важнее, а одновременное изменение отсекает {@code @Version}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryTimeoutService {
    private final DeliveryRepository deliveryRepository;
    private final DeliveryWorkflow workflow;
    private final DeliveryIssueService issueService;
    private final DeliveryNotifier notifier;
    private final DeliveryProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void scheduledCheck() {
        int handled = processOverdue();
        if (handled > 0) {
            log.info("Delivery timeouts: handled {} overdue deliveries", handled);
        }
    }

    /** @return сколько просроченных доставок обработано */
    public int processOverdue() {
        List<Long> overdue = deliveryRepository.findOverdueIds(DeliveryStatus.ACTIVE, clock.instant());
        int handled = 0;
        for (Long deliveryId : overdue) {
            try {
                Boolean done = transactionTemplate.execute(tx -> handle(deliveryId));
                if (Boolean.TRUE.equals(done)) {
                    handled++;
                }
            } catch (ObjectOptimisticLockingFailureException e) {
                // Курьер или администратор изменили доставку в эту же секунду — проверим в следующий раз
                log.debug("Delivery {} changed concurrently, skipped", deliveryId);
            } catch (RuntimeException e) {
                log.error("Delivery timeout for {} failed", deliveryId, e);
            }
        }
        return handled;
    }

    private boolean handle(Long deliveryId) {
        Delivery delivery = deliveryRepository.findByIdOrThrow(deliveryId);
        Instant now = clock.instant();
        if (!delivery.getStatus().isActive() || delivery.getDeadlineAt() == null || delivery.getDeadlineAt().isAfter(now)) {
            return false;
        }
        DeliveryProperties.Timeouts timeouts = properties.timeouts();

        switch (delivery.getStatus()) {
            case SEARCHING_COURIER -> {
                String reason = "никто из курьеров не взял заказ за " + minutes(timeouts.searching());
                workflow.cancel(delivery, reason);
                delivery.setAdminNote("Автоотмена: " + reason);
            }
            case ASSIGNED -> {
                if (delivery.getOrder().getStatus() != OrderStatus.READY) {
                    // Заказ ещё готовится: срок начнётся, когда заведение отметит его готовым
                    delivery.setDeadlineAt(null);
                    return false;
                }
                String courierName = delivery.getCourier().getName();
                workflow.release(delivery);
                String text = "Курьер " + courierName + " не забрал готовый заказ за "
                        + minutes(timeouts.pickup()) + " и снят автоматически, заказ снова в ленте";
                delivery.setAdminNote(text);
                issueService.openSystemIssue(delivery, DeliveryIssueType.COURIER_NOT_ARRIVED, text);
                notifier.courierReleased(delivery);
            }
            case PICKED_UP -> {
                issueService.openSystemIssue(delivery, DeliveryIssueType.NOT_DELIVERED,
                        "Курьер " + delivery.getCourier().getName() + " забрал заказ больше "
                                + minutes(timeouts.delivering()) + " назад и не передал клиенту");
                // Сообщаем один раз: дальше решает администратор
                delivery.setDeadlineAt(null);
            }
            default -> {
                return false;
            }
        }
        deliveryRepository.saveAndFlush(delivery);
        return true;
    }

    private static String minutes(Duration duration) {
        return duration.toMinutes() + " мин";
    }
}
