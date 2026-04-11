package com.dium.demo.dto.responses;

import com.dium.demo.enums.ProductCategory;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String name,
        BigDecimal price,
        String imageUrl,
        String description,
        Boolean inStock,
        ProductCategory category,
        Boolean hasModifiers
) { }
