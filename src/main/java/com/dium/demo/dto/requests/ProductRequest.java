package com.dium.demo.dto.requests;

import com.dium.demo.enums.ProductCategory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank(message = "Product name is required")
        String name,
        @NotNull(message = "Price is required")
        @Min(value = 0, message = "Price cannot be negative")
        BigDecimal price,
        String imageUrl,
        String description,
        Boolean inStock,
        @NotNull(message = "Category is required")
        ProductCategory category,
        @NotNull(message = "HasModifiers flag is required")
        Boolean hasModifiers
) { }
