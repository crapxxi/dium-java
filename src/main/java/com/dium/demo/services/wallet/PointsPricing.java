package com.dium.demo.services.wallet;

import com.dium.demo.config.WalletProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Курс баллов. Курьер получает 1 балл за 1 ₸ доставки, а в заведениях цена в баллах
 * выше на наценку: 1000 ₸ при наценке 20% — 1200 баллов. Баллы целые, округление
 * всегда в пользу платформы: цена — вверх, начисление — вниз.
 */
@Component
@RequiredArgsConstructor
public class PointsPricing {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final WalletProperties properties;

    /** Цена в баллах за сумму в тенге. */
    public long priceInPoints(BigDecimal tenge) {
        if (tenge == null || tenge.signum() <= 0) {
            return 0;
        }
        BigDecimal factor = HUNDRED.add(BigDecimal.valueOf(properties.pointsMarkupPercent()));
        return tenge.multiply(factor).divide(HUNDRED, 0, RoundingMode.CEILING).longValueExact();
    }

    /** Сколько баллов курьер получает за доставку: столько же, сколько клиент платит за неё в тенге. */
    public long earningInPoints(BigDecimal courierFee) {
        if (courierFee == null || courierFee.signum() <= 0) {
            return 0;
        }
        return courierFee.setScale(0, RoundingMode.DOWN).longValueExact();
    }

    public int markupPercent() {
        return properties.pointsMarkupPercent();
    }
}
