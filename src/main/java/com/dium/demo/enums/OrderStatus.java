package com.dium.demo.enums;

import java.util.Optional;

public enum OrderStatus {
    PENDING,
    PREPARING,
    READY,
    COMPLETED,
    CANCELLED;

    /** Следующий шаг обработки заказа кухней; пусто для конечных статусов. */
    public Optional<OrderStatus> next() {
        return switch (this) {
            case PENDING -> Optional.of(PREPARING);
            case PREPARING -> Optional.of(READY);
            case READY -> Optional.of(COMPLETED);
            case COMPLETED, CANCELLED -> Optional.empty();
        };
    }
}
