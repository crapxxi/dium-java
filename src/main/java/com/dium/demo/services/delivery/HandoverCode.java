package com.dium.demo.services.delivery;

import com.dium.demo.models.Delivery;

import java.util.function.BiConsumer;
import java.util.function.Function;

/** Два кода передачи заказа и счётчики их неверных вводов. */
public enum HandoverCode {
    /** Код курьера: курьер показывает, заведение вводит. Подтверждает, что заказ отдан назначенному курьеру. */
    PICKUP(Delivery::getPickupCode, Delivery::getPickupCodeAttempts, Delivery::setPickupCodeAttempts,
            "код курьера"),
    /** Код клиента: клиент называет, курьер вводит. Подтверждает, что клиент получил заказ. */
    DELIVERY(Delivery::getDeliveryCode, Delivery::getDeliveryCodeAttempts, Delivery::setDeliveryCodeAttempts,
            "код клиента");

    private final Function<Delivery, String> sealedCode;
    private final Function<Delivery, Integer> attempts;
    private final BiConsumer<Delivery, Integer> setAttempts;
    private final String label;

    HandoverCode(Function<Delivery, String> sealedCode, Function<Delivery, Integer> attempts,
                 BiConsumer<Delivery, Integer> setAttempts, String label) {
        this.sealedCode = sealedCode;
        this.attempts = attempts;
        this.setAttempts = setAttempts;
        this.label = label;
    }

    /** Код в зашифрованном виде. */
    public String sealedCode(Delivery delivery) {
        return sealedCode.apply(delivery);
    }

    public int attempts(Delivery delivery) {
        Integer value = attempts.apply(delivery);
        return value == null ? 0 : value;
    }

    public void setAttempts(Delivery delivery, int value) {
        setAttempts.accept(delivery, value);
    }

    public boolean isLocked(Delivery delivery, int maxAttempts) {
        return attempts(delivery) >= maxAttempts;
    }

    public int attemptsLeft(Delivery delivery, int maxAttempts) {
        return Math.max(0, maxAttempts - attempts(delivery));
    }

    public String label() {
        return label;
    }
}
