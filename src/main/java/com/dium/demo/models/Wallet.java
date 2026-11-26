package com.dium.demo.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Баланс баллов пользователя. Меняется только через {@code WalletLedger} вместе с записью в журнале. */
@Entity
@Table(name = "wallets")
@Getter
@Setter
@NoArgsConstructor
public class Wallet {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private long balance;
}
