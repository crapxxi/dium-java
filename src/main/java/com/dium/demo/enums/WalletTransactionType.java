package com.dium.demo.enums;

/** Вид операции в журнале баллов. */
public enum WalletTransactionType {
    /** Курьер доставил заказ (клиент назвал код или доставку завершил администратор). */
    DELIVERY_EARNING,
    /** Заказ оплачен баллами. */
    ORDER_PAYMENT,
    /** Возврат за отменённый заказ или отменённую доставку. */
    ORDER_REFUND,
    /** Ручная правка администратора с причиной. */
    ADMIN_ADJUSTMENT
}
