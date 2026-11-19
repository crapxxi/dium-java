package com.dium.demo.services.delivery;

import com.dium.demo.config.DeliveryProperties;
import com.dium.demo.dto.responses.DeliveryResponses;
import com.dium.demo.dto.responses.OrderItemResponse;
import com.dium.demo.enums.DeliveryStatus;
import com.dium.demo.mappers.OrderItemMapper;
import com.dium.demo.models.Delivery;
import com.dium.demo.models.DeliveryIssue;
import com.dium.demo.models.Order;
import com.dium.demo.models.OrderItem;
import com.dium.demo.models.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Собирает ответы API для каждой стороны доставки. Что кому показывать, решается только здесь:
 * <ul>
 *     <li>код клиента — только клиенту и только пока доставка не завершена;</li>
 *     <li>код курьера — только назначенному курьеру и только до получения заказа;</li>
 *     <li>телефоны клиента и курьера — только пока доставка в работе.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class DeliveryViews {
    private final DeliveryCodes codes;
    private final DeliveryProperties properties;
    private final OrderItemMapper orderItemMapper;

    public DeliveryResponses.Available available(Delivery delivery) {
        Order order = delivery.getOrder();
        return new DeliveryResponses.Available(
                delivery.getId(),
                order.getId(),
                order.getVenue().getName(),
                order.getVenue().getAddress(),
                order.getAddress(),
                itemsCount(order),
                delivery.getCourierFee(),
                order.getStatus(),
                delivery.getCreatedAt());
    }

    public DeliveryResponses.Courier forCourier(Delivery delivery) {
        Order order = delivery.getOrder();
        boolean inProgress = DeliveryStatus.IN_PROGRESS.contains(delivery.getStatus());
        boolean beforePickup = delivery.getStatus() == DeliveryStatus.ASSIGNED;
        return new DeliveryResponses.Courier(
                delivery.getId(),
                order.getId(),
                delivery.getStatus(),
                order.getStatus(),
                order.getVenue().getName(),
                order.getVenue().getAddress(),
                order.getAddress(),
                order.getUser().getName(),
                inProgress ? order.getUser().getPhone() : null,
                order.getComment(),
                items(order),
                delivery.getCourierFee(),
                beforePickup ? codes.reveal(delivery.getPickupCode()) : null,
                HandoverCode.DELIVERY.attemptsLeft(delivery, maxAttempts()),
                HandoverCode.DELIVERY.isLocked(delivery, maxAttempts()),
                delivery.getAssignedAt(),
                delivery.getPickedUpAt(),
                delivery.getDeliveredAt());
    }

    public DeliveryResponses.Customer forCustomer(Delivery delivery) {
        User courier = delivery.getCourier();
        boolean inProgress = DeliveryStatus.IN_PROGRESS.contains(delivery.getStatus());
        return new DeliveryResponses.Customer(
                delivery.getId(),
                delivery.getOrder().getId(),
                delivery.getStatus(),
                delivery.getOrder().getVenue().getName(),
                courier == null ? null : courier.getName(),
                inProgress ? courier.getPhone() : null,
                delivery.getStatus().isActive() ? codes.reveal(delivery.getDeliveryCode()) : null,
                delivery.getCreatedAt(),
                delivery.getAssignedAt(),
                delivery.getPickedUpAt(),
                delivery.getDeliveredAt());
    }

    public DeliveryResponses.Venue forVenue(Delivery delivery) {
        User courier = delivery.getCourier();
        boolean inProgress = DeliveryStatus.IN_PROGRESS.contains(delivery.getStatus());
        return new DeliveryResponses.Venue(
                delivery.getId(),
                delivery.getOrder().getId(),
                delivery.getStatus(),
                delivery.getOrder().getStatus(),
                delivery.getOrder().getAddress(),
                courier == null ? null : courier.getName(),
                inProgress ? courier.getPhone() : null,
                HandoverCode.PICKUP.attemptsLeft(delivery, maxAttempts()),
                HandoverCode.PICKUP.isLocked(delivery, maxAttempts()),
                delivery.getCreatedAt(),
                delivery.getAssignedAt(),
                delivery.getPickedUpAt(),
                delivery.getDeliveredAt(),
                delivery.getCancelledAt());
    }

    public DeliveryResponses.Admin forAdmin(Delivery delivery, boolean hasOpenIssue) {
        Order order = delivery.getOrder();
        User courier = delivery.getCourier();
        return new DeliveryResponses.Admin(
                delivery.getId(),
                order.getId(),
                delivery.getStatus(),
                order.getStatus(),
                order.getVenue().getName(),
                order.getAddress(),
                order.getTotalSum(),
                delivery.getCourierFee(),
                order.getUser().getName(),
                order.getUser().getPhone(),
                courier == null ? null : courier.getName(),
                courier == null ? null : courier.getPhone(),
                HandoverCode.PICKUP.attempts(delivery),
                HandoverCode.DELIVERY.attempts(delivery),
                HandoverCode.PICKUP.isLocked(delivery, maxAttempts()) || HandoverCode.DELIVERY.isLocked(delivery, maxAttempts()),
                hasOpenIssue,
                delivery.getAdminNote(),
                delivery.getCreatedAt(),
                delivery.getAssignedAt(),
                delivery.getPickedUpAt(),
                delivery.getDeliveredAt(),
                delivery.getCancelledAt());
    }

    public DeliveryResponses.Issue issue(DeliveryIssue issue) {
        User reporter = issue.getReporter();
        User resolvedBy = issue.getResolvedBy();
        return new DeliveryResponses.Issue(
                issue.getId(),
                issue.getDelivery().getId(),
                issue.getDelivery().getOrder().getId(),
                issue.getDelivery().getStatus(),
                issue.getType(),
                issue.getStatus(),
                issue.getReportedBy(),
                reporter == null ? null : reporter.getName(),
                reporter == null ? null : reporter.getPhone(),
                issue.getDescription(),
                issue.getResolution(),
                resolvedBy == null ? null : resolvedBy.getName(),
                issue.getCreatedAt(),
                issue.getResolvedAt());
    }

    private int maxAttempts() {
        return properties.maxCodeAttempts();
    }

    private List<OrderItemResponse> items(Order order) {
        return order.getItems() == null ? List.of() : order.getItems().stream().map(orderItemMapper::toResponse).toList();
    }

    private static int itemsCount(Order order) {
        return order.getItems() == null ? 0 : order.getItems().stream().mapToInt(OrderItem::getCount).sum();
    }
}
