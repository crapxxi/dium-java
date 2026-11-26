package com.dium.demo.dto.responses;

import com.dium.demo.enums.DeliveryIssueStatus;
import com.dium.demo.enums.DeliveryIssueType;
import com.dium.demo.enums.DeliveryParticipant;
import com.dium.demo.enums.DeliveryStatus;
import com.dium.demo.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Ответы API доставки. У каждой стороны свой ответ и в нём только то, что ей нужно:
 * код клиента видит только клиент, код курьера — только курьер, заведение и администратор
 * кодов не видят вовсе. Телефоны показываются, только пока доставка в работе.
 */
public final class DeliveryResponses {
    private DeliveryResponses() {
    }

    /** Свободный заказ в ленте курьера. Данных клиента здесь нет — только куда и откуда. */
    public record Available(
            Long id,
            Long orderId,
            String venueName,
            String venueAddress,
            String deliveryAddress,
            int itemsCount,
            BigDecimal courierFee,
            OrderStatus orderStatus,
            Instant createdAt
    ) {
    }

    /** Доставка глазами курьера. */
    public record Courier(
            Long id,
            Long orderId,
            DeliveryStatus status,
            OrderStatus orderStatus,
            String venueName,
            String venueAddress,
            String deliveryAddress,
            String customerName,
            String customerPhone,
            String comment,
            List<OrderItemResponse> items,
            BigDecimal courierFee,
            /* Код, который курьер показывает заведению; есть только до получения заказа. */
            String pickupCode,
            int deliveryAttemptsLeft,
            boolean deliveryCodeLocked,
            Instant assignedAt,
            Instant pickedUpAt,
            Instant deliveredAt,
            /* Срок текущего этапа: найти курьера, забрать заказ или отдать клиенту; null — срока нет */
            Instant deadlineAt
    ) {
    }

    /** Заработок и загрузка курьера. */
    public record CourierSummary(
            long deliveredCount,
            BigDecimal totalEarned,
            BigDecimal feePerDelivery,
            long activeCount,
            int maxActive
    ) {
    }

    /** Доставка глазами клиента. */
    public record Customer(
            Long id,
            Long orderId,
            DeliveryStatus status,
            String venueName,
            String courierName,
            String courierPhone,
            /* Код, который клиент называет курьеру при получении; есть, пока доставка не завершена. */
            String deliveryCode,
            Instant createdAt,
            Instant assignedAt,
            Instant pickedUpAt,
            Instant deliveredAt,
            /* Срок текущего этапа: найти курьера, забрать заказ или отдать клиенту; null — срока нет */
            Instant deadlineAt
    ) {
    }

    /** Доставка глазами заведения. */
    public record Venue(
            Long id,
            Long orderId,
            DeliveryStatus status,
            OrderStatus orderStatus,
            String deliveryAddress,
            String courierName,
            String courierPhone,
            int pickupAttemptsLeft,
            boolean pickupCodeLocked,
            Instant createdAt,
            Instant assignedAt,
            Instant pickedUpAt,
            Instant deliveredAt,
            Instant cancelledAt
    ) {
    }

    /** Доставка в админке: все участники, попытки ввода кодов, открытые проблемы. */
    public record Admin(
            Long id,
            Long orderId,
            DeliveryStatus status,
            OrderStatus orderStatus,
            String venueName,
            String deliveryAddress,
            BigDecimal orderTotal,
            BigDecimal courierFee,
            String customerName,
            String customerPhone,
            String courierName,
            String courierPhone,
            int pickupCodeAttempts,
            int deliveryCodeAttempts,
            boolean codeLocked,
            boolean hasOpenIssue,
            String adminNote,
            Instant createdAt,
            Instant assignedAt,
            Instant pickedUpAt,
            Instant deliveredAt,
            Instant cancelledAt,
            /* Срок текущего этапа: найти курьера, забрать заказ или отдать клиенту; null — срока нет */
            Instant deadlineAt
    ) {
    }

    public record Issue(
            Long id,
            Long deliveryId,
            Long orderId,
            DeliveryStatus deliveryStatus,
            DeliveryIssueType type,
            DeliveryIssueStatus status,
            DeliveryParticipant reportedBy,
            String reporterName,
            String reporterPhone,
            String description,
            String resolution,
            String resolvedByName,
            Instant createdAt,
            Instant resolvedAt
    ) {
    }
}
