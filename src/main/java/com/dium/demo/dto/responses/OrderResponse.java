package com.dium.demo.dto.responses;

import com.dium.demo.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String venueName,
        BigDecimal totalSum,
        OrderStatus status,
        Integer pickupCode,
        LocalDateTime createdAt,
        List<OrderItemResponse> items,
        String address,
        String comment,
        BigDecimal deliveryFee,
        String paymentFrom
){ }
