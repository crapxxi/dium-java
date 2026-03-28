package com.dium.demo.dto.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CreateOrderRequest(
        Long venueId,
        List<OrderItemRequest> items,
        String address,
        String comment,
        BigDecimal deliveryFee
) { }
