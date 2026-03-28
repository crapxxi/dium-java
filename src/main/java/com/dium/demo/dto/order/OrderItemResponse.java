package com.dium.demo.dto.order;

import com.dium.demo.dto.product_modifier.ModifierDTO;
import com.dium.demo.models.Modifier;

import java.math.BigDecimal;
import java.util.List;

public record OrderItemResponse(
        String productName,
        BigDecimal priceAtPurchase,
        Integer count,
        List<ModifierDTO> modifiers
){ }
