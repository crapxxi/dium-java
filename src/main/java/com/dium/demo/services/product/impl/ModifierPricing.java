package com.dium.demo.services.product.impl;

import com.dium.demo.utils.MoneyValidator;

import java.math.BigDecimal;

/** Правила цен модификаторов, общие для сервисов модификаторов и их групп. */
final class ModifierPricing {

    private ModifierPricing() {
    }

    static void validatePriceDelta(BigDecimal priceDelta) {
        MoneyValidator.requireNonNegativeIfPresent(priceDelta,
                "Price delta is out of allowed range (too big discount)");
    }
}
