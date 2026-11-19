package com.dium.demo.enums;

/** Что случилось с доставкой. */
public enum DeliveryIssueType {
    /** Клиент: заказ так и не привезли. */
    NOT_DELIVERED,
    /** Заведение или клиент: курьер взял заказ, но не пришёл. */
    COURIER_NOT_ARRIVED,
    /** Курьер: не может найти клиента или тот не отвечает. */
    CUSTOMER_UNREACHABLE,
    /** Курьер: клиент получил заказ, но не называет код. */
    CUSTOMER_REFUSES_CODE,
    /** Заказ повреждён или не тот. */
    DAMAGED_OR_WRONG,
    /** Создаётся системой: код передачи заблокирован после неверных попыток. */
    CODE_LOCKED,
    OTHER
}
