package com.dium.demo.dto.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
        @NotBlank(message = "Payment from is required")
        String paymentFrom
) { }
