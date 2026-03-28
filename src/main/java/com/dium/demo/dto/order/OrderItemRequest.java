package com.dium.demo.dto.order;

import java.util.List;

public record OrderItemRequest(
        Long productId,
        Integer count,
        List<Long> modifierIds
){ }
