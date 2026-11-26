package com.dium.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Настройки баллов ({@code wallet.*} в application.yml).
 *
 * @param pointsMarkupPercent на сколько процентов цена в баллах выше цены в тенге.
 *                            Курьер получает 1 балл за 1 ₸ доставки, а тратит их с наценкой: разница
 *                            остаётся платформе и не даёт заработать на доставках «по кругу»
 */
@ConfigurationProperties(prefix = "wallet")
public record WalletProperties(@DefaultValue("20") int pointsMarkupPercent) {
    public WalletProperties {
        if (pointsMarkupPercent < 0 || pointsMarkupPercent > 100) {
            throw new IllegalArgumentException("wallet.points-markup-percent must be between 0 and 100");
        }
    }
}
