package com.dium.demo.dto.responses;

import com.dium.demo.enums.UserRole;
import com.dium.demo.enums.WalletTransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Ответы API баллов. */
public final class WalletResponses {
    private WalletResponses() {
    }

    /**
     * Кошелёк текущего пользователя.
     *
     * @param markupPercent     на сколько процентов цена в баллах выше цены в тенге
     * @param pointsPerDelivery сколько баллов курьер получает за одну доставку
     */
    public record Wallet(
            long balance,
            int markupPercent,
            long pointsPerDelivery,
            List<Transaction> transactions
    ) {
    }

    public record Transaction(
            Long id,
            long amount,
            long balanceAfter,
            WalletTransactionType type,
            Long orderId,
            String note,
            Instant createdAt
    ) {
    }

    /**
     * Кошелёк пользователя для администратора.
     *
     * @param ledgerSum сумма всех операций журнала; если не равна балансу — данные испорчены
     */
    public record AdminWallet(
            Long userId,
            String name,
            String phone,
            UserRole role,
            long balance,
            long ledgerSum,
            List<Transaction> transactions
    ) {
    }

    /**
     * Сверка с заведением за период.
     *
     * @param deliveryFeesOwed  плата за доставку, которую клиенты перевели заведению по Kaspi,
     *                          а курьер получил баллами, — заведение должно её платформе
     * @param pointsOrdersCount выданные заказы, оплаченные баллами
     * @param pointsOrdersValue их стоимость в тенге без доставки — её платформа должна заведению
     * @param netToVenue        итог: плюс — платформа платит заведению, минус — заведение платформе
     */
    public record Settlement(
            Long venueId,
            String venueName,
            BigDecimal deliveryFeesOwed,
            long pointsOrdersCount,
            BigDecimal pointsOrdersValue,
            BigDecimal netToVenue
    ) {
    }

    /** Период — с {@code from} по {@code to} включительно. */
    public record SettlementReport(LocalDate from, LocalDate to, List<Settlement> venues) {
    }
}
