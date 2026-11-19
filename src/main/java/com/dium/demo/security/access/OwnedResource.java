package com.dium.demo.security.access;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Ресурсы, принадлежащие заведению (а через него — его владельцу). */
@Getter
@RequiredArgsConstructor
public enum OwnedResource {
    PRODUCT("Product"),
    MODIFIER_GROUP("Modifier group"),
    MODIFIER("Modifier"),
    ORDER("Order"),
    DELIVERY("Delivery");

    /** Название для сообщений об ошибках. */
    private final String label;
}
