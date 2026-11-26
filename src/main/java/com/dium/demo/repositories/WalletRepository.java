package com.dium.demo.repositories;

import com.dium.demo.models.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WalletRepository extends BaseRepository<Wallet, Long> {
    /** Создаёт пустой кошелёк, если его ещё нет. Безопасно при параллельных вызовах. */
    @Modifying
    @Query(value = "INSERT INTO wallets (user_id, balance) VALUES (:userId, 0) ON CONFLICT (user_id) DO NOTHING",
            nativeQuery = true)
    void createIfAbsent(@Param("userId") Long userId);

    /**
     * Блокирует кошелёк до конца транзакции (SELECT ... FOR UPDATE): операции одного пользователя
     * идут по очереди, два одновременных заказа не потратят одни и те же баллы.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.userId = :userId")
    Optional<Wallet> findForUpdate(@Param("userId") Long userId);
}
