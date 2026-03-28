package com.dium.demo.dto.venue_product;

import com.dium.demo.enums.ProductCategory;

import java.math.BigDecimal;

public record ProductDTO(
        Long id,
        String name,
        BigDecimal price,
        String imageUrl,
        String description,
        Boolean inStock,
        ProductCategory category,
        Boolean hasModifiers
) { }
