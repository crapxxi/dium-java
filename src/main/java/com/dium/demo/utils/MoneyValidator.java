package com.dium.demo.utils;

import com.dium.demo.exceptions.BusinessLogicException;

import java.math.BigDecimal;

/** Единые проверки денежных значений (цены, доставка, наценки модификаторов). */
public final class MoneyValidator {

    private MoneyValidator() {
    }

    /** Значение обязательно и не может быть отрицательным. */
    public static void requireNonNegative(BigDecimal value, String message) {
        if (value == null) {
            throw new BusinessLogicException(message);
        }
        requireNonNegativeIfPresent(value, message);
    }

    /** Значение необязательно, но если задано — не может быть отрицательным. */
    public static void requireNonNegativeIfPresent(BigDecimal value, String message) {
        if (value != null && value.signum() < 0) {
            throw new BusinessLogicException(message);
        }
    }
}
