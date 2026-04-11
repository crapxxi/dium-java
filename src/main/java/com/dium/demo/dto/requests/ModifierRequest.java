package com.dium.demo.dto.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ModifierRequest(
        @NotBlank(message = "Modifier name is required")
        String name,
        @NotNull(message = "Price delta is required")
        BigDecimal priceDelta,
        @NotNull(message = "InStock status is required")
        Boolean inStock
) { }
