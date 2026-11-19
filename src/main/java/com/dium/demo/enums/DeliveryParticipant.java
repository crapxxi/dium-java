package com.dium.demo.enums;

/** Кем пользователь приходится конкретной доставке. */
public enum DeliveryParticipant {
    /** Заказчик. */
    CUSTOMER,
    /** Назначенный курьер. */
    COURIER,
    /** Владелец заведения, из которого везут заказ. */
    VENUE,
    /** Создано автоматически. */
    SYSTEM
}
