package com.dium.demo.dto.product_modifier;

import java.math.BigDecimal;

public record ModifierDTO(
        Long id,
        String name,
        BigDecimal priceDelta,
        Boolean inStock
) { }
