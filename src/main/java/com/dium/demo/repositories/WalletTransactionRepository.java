package com.dium.demo.repositories;

import com.dium.demo.enums.WalletTransactionType;
import com.dium.demo.models.WalletTransaction;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WalletTransactionRepository extends BaseRepository<WalletTransaction, Long> {
    Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey);

    List<WalletTransaction> findTop100ByUserIdOrderByIdDesc(Long userId);

    /** Сумма операций по заказу одного вида, например сколько баллов уже вернули. */
    @Query("select coalesce(sum(t.amount), 0) from WalletTransaction t where t.orderId = :orderId and t.type = :type")
    long sumByOrderAndType(@Param("orderId") Long orderId, @Param("type") WalletTransactionType type);

    /** Сумма всех операций пользователя: должна совпадать с балансом кошелька. */
    @Query("select coalesce(sum(t.amount), 0) from WalletTransaction t where t.userId = :userId")
    long sumByUser(@Param("userId") Long userId);
}
