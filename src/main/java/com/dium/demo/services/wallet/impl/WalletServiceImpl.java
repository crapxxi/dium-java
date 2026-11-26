package com.dium.demo.services.wallet.impl;

import com.dium.demo.config.DeliveryProperties;
import com.dium.demo.dto.responses.WalletResponses;
import com.dium.demo.enums.UserRole;
import com.dium.demo.enums.WalletTransactionType;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.models.User;
import com.dium.demo.models.WalletTransaction;
import com.dium.demo.repositories.UserRepository;
import com.dium.demo.repositories.WalletTransactionRepository;
import com.dium.demo.security.access.RequireRole;
import com.dium.demo.services.auth.CustomUserDetailsService;
import com.dium.demo.services.wallet.PointsPricing;
import com.dium.demo.services.wallet.WalletLedger;
import com.dium.demo.services.wallet.WalletService;
import com.dium.demo.utils.PhoneNormalizer;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {
    /** Самый длинный период сверки за один запрос. */
    private static final int MAX_SETTLEMENT_DAYS = 366;

    /*
     * Сверка по заказам периода:
     *  - доставка оплачена по Kaspi и курьер получил за неё баллы: 100 ₸ получило заведение,
     *    значит, оно должно эту сумму платформе (доставки до появления баллов не считаются);
     *  - заказ оплачен баллами и выдан: заведение денег не получило, платформа должна ему
     *    стоимость еды в тенге (без доставки — её курьер получил баллами).
     */
    private static final String SETTLEMENT_SQL = """
            SELECT venue_id, venue_name, fees_owed, points_count, points_value
            FROM (SELECT v.id   AS venue_id,
                         v.name AS venue_name,
                         coalesce(sum(d.courier_fee)
                                  FILTER (WHERE o.payment_method = 'KASPI' AND d.status = 'DELIVERED'
                                      AND EXISTS (SELECT 1
                                                  FROM wallet_transactions t
                                                  WHERE t.delivery_id = d.id
                                                    AND t.type = 'DELIVERY_EARNING')), 0)  AS fees_owed,
                         count(*) FILTER (WHERE o.payment_method = 'POINTS' AND o.status = 3)              AS points_count,
                         coalesce(sum(o.total_sum - o.delivery_fee)
                                  FILTER (WHERE o.payment_method = 'POINTS' AND o.status = 3), 0)          AS points_value
                  FROM orders o
                           JOIN venues v ON v.id = o.venue_id
                           LEFT JOIN deliveries d ON d.order_id = o.id
                  WHERE o.created_at >= ? AND o.created_at < ?
                  GROUP BY v.id, v.name) s
            WHERE fees_owed > 0 OR points_count > 0
            ORDER BY venue_name
            """;

    private final WalletLedger ledger;
    private final WalletTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final CustomUserDetailsService userDetailsService;
    private final PointsPricing pricing;
    private final DeliveryProperties deliveryProperties;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    @Override
    public WalletResponses.Wallet getMyWallet() {
        Long userId = userDetailsService.getCurrentUser().getId();
        return new WalletResponses.Wallet(
                ledger.balanceOf(userId),
                pricing.markupPercent(),
                pricing.earningInPoints(deliveryProperties.fee()),
                transactions(userId));
    }

    @RequireRole(UserRole.ADMIN)
    @Transactional(readOnly = true)
    @Override
    public WalletResponses.AdminWallet findByPhone(String phone) {
        String normalized = PhoneNormalizer.normalize(phone);
        User user = userRepository.findByPhone(normalized)
                .orElseThrow(() -> new EntityNotFoundException("Пользователь с таким номером не найден"));
        return adminView(user);
    }

    @RequireRole(UserRole.ADMIN)
    @Transactional
    @Override
    public WalletResponses.AdminWallet adjust(Long userId, long amount, String reason) {
        if (amount == 0) {
            throw new BusinessLogicException("Укажите, сколько баллов начислить или списать");
        }
        User user = userRepository.findByIdOrThrow(userId);
        User admin = userDetailsService.getCurrentUser();
        if (admin.getId().equals(user.getId())) {
            throw new BusinessLogicException("Свой баланс администратор менять не может — попросите другого администратора");
        }
        ledger.post(WalletLedger.Posting.builder()
                .userId(user.getId())
                .amount(amount)
                .type(WalletTransactionType.ADMIN_ADJUSTMENT)
                .note(admin.getName() + ": " + reason.trim())
                .createdById(admin.getId())
                .build());
        return adminView(user);
    }

    @RequireRole(UserRole.ADMIN)
    @Transactional(readOnly = true)
    @Override
    public WalletResponses.SettlementReport getSettlements(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BusinessLogicException("Конец периода раньше начала");
        }
        if (from.plusDays(MAX_SETTLEMENT_DAYS).isBefore(to)) {
            throw new BusinessLogicException("Период сверки — не больше года");
        }
        List<WalletResponses.Settlement> venues = jdbcTemplate.query(SETTLEMENT_SQL, (rs, i) -> {
            BigDecimal fees = rs.getBigDecimal("fees_owed");
            BigDecimal points = rs.getBigDecimal("points_value");
            return new WalletResponses.Settlement(
                    rs.getLong("venue_id"),
                    rs.getString("venue_name"),
                    fees,
                    rs.getLong("points_count"),
                    points,
                    points.subtract(fees));
        }, Timestamp.valueOf(from.atStartOfDay()), Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
        return new WalletResponses.SettlementReport(from, to, venues);
    }

    private WalletResponses.AdminWallet adminView(User user) {
        return new WalletResponses.AdminWallet(
                user.getId(),
                user.getName(),
                user.getPhone(),
                user.getRole(),
                ledger.balanceOf(user.getId()),
                transactionRepository.sumByUser(user.getId()),
                transactions(user.getId()));
    }

    private List<WalletResponses.Transaction> transactions(Long userId) {
        return transactionRepository.findTop100ByUserIdOrderByIdDesc(userId).stream()
                .map(WalletServiceImpl::view)
                .toList();
    }

    private static WalletResponses.Transaction view(WalletTransaction t) {
        return new WalletResponses.Transaction(
                t.getId(), t.getAmount(), t.getBalanceAfter(), t.getType(), t.getOrderId(), t.getNote(),
                t.getCreatedAt());
    }
}
