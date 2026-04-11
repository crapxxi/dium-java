package com.dium.demo.dto.responses;

import java.math.BigDecimal;

public record ModifierResponse(
        Long id,
        String name,
        BigDecimal priceDelta,
        Boolean inStock

) { }
