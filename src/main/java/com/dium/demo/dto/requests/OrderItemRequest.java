package com.dium.demo.dto.requests;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record OrderItemRequest(
        @NotNull(message = "Product ID is required")
        Long productId,
        @NotNull(message = "Count is required")
        @Min(value = 1, message = "Count must be at least 1")
        Integer count,
        List<Long> modifierIds
){ }
