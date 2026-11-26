package com.dium.demo.services.wallet;

import com.dium.demo.dto.responses.WalletResponses;

import java.time.LocalDate;

/**
 * Кошелёк баллов. Пользователь видит только свой кошелёк; переводов между пользователями
 * и вывода баллов в деньги нет намеренно — баллы можно только потратить на заказ.
 */
public interface WalletService {
    WalletResponses.Wallet getMyWallet();

    /** Администратор: кошелёк по номеру телефона. */
    WalletResponses.AdminWallet findByPhone(String phone);

    /** Администратор: ручная правка с причиной (компенсация, исправление ошибки). */
    WalletResponses.AdminWallet adjust(Long userId, long amount, String reason);

    /** Администратор: сколько платформа и заведения должны друг другу за период (даты включительно). */
    WalletResponses.SettlementReport getSettlements(LocalDate from, LocalDate to);
}
