package com.dium.demo.dto.requests;

import com.dium.demo.enums.FulfillmentType;
import com.dium.demo.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record OrderRequest(
        @NotNull(message = "Venue ID is required")
        Long venueId,
        @NotEmpty(message = "Order must contain at least one item")
        @Valid
        List<OrderItemRequest> items,
        String address,
        String comment,
        @Min(value = 0, message = "Delivery fee cannot be negative")
        BigDecimal deliveryFee,
        /* Кто переводит по Kaspi. Обязателен для оплаты Kaspi, при оплате баллами не нужен. */
        String paymentFrom,
        /*
         * Самовывоз или доставка — выбирает покупатель. Если не передано (старые клиенты),
         * заказ считается доставкой, когда указан адрес и заведение доставляет.
         */
        FulfillmentType fulfillment,
        /* Kaspi (по умолчанию) или баллы. */
        PaymentMethod paymentMethod
) {
    /** Заказ с оплатой Kaspi. */
    public OrderRequest(Long venueId, List<OrderItemRequest> items, String address, String comment,
                        BigDecimal deliveryFee, String paymentFrom, FulfillmentType fulfillment) {
        this(venueId, items, address, comment, deliveryFee, paymentFrom, fulfillment, null);
    }
}
