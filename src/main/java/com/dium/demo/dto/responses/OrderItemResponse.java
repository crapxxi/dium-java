package com.dium.demo.dto.responses;

import java.math.BigDecimal;
import java.util.List;

public record OrderItemResponse(
        String productName,
        BigDecimal priceAtPurchase,
        Integer count,
        List<ModifierResponse> modifiers
){ }
