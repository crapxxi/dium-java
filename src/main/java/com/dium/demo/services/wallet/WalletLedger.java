package com.dium.demo.services.wallet;

import com.dium.demo.enums.WalletTransactionType;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.models.Wallet;
import com.dium.demo.models.WalletTransaction;
import com.dium.demo.repositories.WalletRepository;
import com.dium.demo.repositories.WalletTransactionRepository;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Единственное место, где меняется баланс. Каждое изменение — запись в журнале с балансом после неё.
 * <ul>
 *     <li>кошелёк блокируется на время операции: операции одного пользователя идут по очереди;</li>
 *     <li>операция с ключом проводится один раз: повтор возвращает уже сделанную запись;</li>
 *     <li>баланс не уходит в минус — ни здесь, ни в БД (CHECK).</li>
 * </ul>
 * Работает только внутри транзакции вызывающего: баллы меняются вместе с заказом или доставкой.
 */
@Component
@RequiredArgsConstructor
public class WalletLedger {
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final Clock clock;

    /**
     * Операция с баллами.
     *
     * @param amount         плюс — начисление, минус — списание
     * @param idempotencyKey ключ автоматической операции; null — для ручных правок
     * @param createdById    администратор, если правка ручная
     */
    @Builder
    public record Posting(Long userId, long amount, WalletTransactionType type, String idempotencyKey,
                          Long orderId, Long deliveryId, String note, Long createdById) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public WalletTransaction post(Posting posting) {
        Long userId = posting.userId();
        long amount = posting.amount();
        String idempotencyKey = posting.idempotencyKey();
        if (amount == 0) {
            throw new IllegalArgumentException("Операция на 0 баллов");
        }
        walletRepository.createIfAbsent(userId);
        Wallet wallet = walletRepository.findForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("Кошелёк " + userId + " не создан"));

        // Проверка ключа после блокировки: второй такой же запрос дождётся первого и увидит его запись
        if (idempotencyKey != null) {
            var existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        long balanceAfter = Math.addExact(wallet.getBalance(), amount);
        if (balanceAfter < 0) {
            throw new BusinessLogicException(
                    "Недостаточно баллов: на счёте " + wallet.getBalance() + ", нужно " + Math.negateExact(amount));
        }
        wallet.setBalance(balanceAfter);

        return transactionRepository.save(WalletTransaction.builder()
                .userId(userId)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .type(posting.type())
                .idempotencyKey(idempotencyKey)
                .orderId(posting.orderId())
                .deliveryId(posting.deliveryId())
                .note(posting.note())
                .createdById(posting.createdById())
                .createdAt(clock.instant())
                .build());
    }

    public long balanceOf(Long userId) {
        return walletRepository.findById(userId).map(Wallet::getBalance).orElse(0L);
    }
}
