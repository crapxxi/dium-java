package com.dium.demo.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Статус доставки. Хранится строкой; допустимые переходы продублированы триггером в V6__delivery.sql.
 * <pre>
 * SEARCHING_COURIER ──► ASSIGNED ──► PICKED_UP ──► DELIVERED
 *        ▲                 │
 *        └─────────────────┘ курьер отказался / снят администратором
 * любой незавершённый ──────────────────────────► CANCELLED
 * </pre>
 */
public enum DeliveryStatus {
    /** Заведение приняло заказ, ждём, пока его возьмёт курьер. */
    SEARCHING_COURIER,
    /** Курьер взял заказ и идёт в заведение. */
    ASSIGNED,
    /** Заведение передало заказ курьеру (подтверждено кодом курьера). */
    PICKED_UP,
    /** Клиент получил заказ (подтверждено кодом клиента). */
    DELIVERED,
    CANCELLED;

    /** Доставка ещё не завершена. */
    public static final Set<DeliveryStatus> ACTIVE = EnumSet.of(SEARCHING_COURIER, ASSIGNED, PICKED_UP);
    /** Доставка закреплена за курьером и ещё не завершена. */
    public static final Set<DeliveryStatus> IN_PROGRESS = EnumSet.of(ASSIGNED, PICKED_UP);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }

    public boolean canMoveTo(DeliveryStatus next) {
        return switch (this) {
            case SEARCHING_COURIER -> next == ASSIGNED || next == CANCELLED;
            case ASSIGNED -> next == SEARCHING_COURIER || next == PICKED_UP || next == CANCELLED;
            case PICKED_UP -> next == DELIVERED || next == CANCELLED;
            case DELIVERED, CANCELLED -> false;
        };
    }
}
