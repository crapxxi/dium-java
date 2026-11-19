package com.dium.demo.models;

import com.dium.demo.enums.DeliveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Доставка заказа студентом-курьером. Создаётся, когда заведение принимает заказ с адресом.
 * <p>
 * Передача подтверждается двумя кодами (оба хранятся зашифрованными):
 * {@link #pickupCode} курьер показывает заведению, {@link #deliveryCode} клиент называет курьеру.
 */
@Entity
@Table(name = "deliveries")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Delivery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    /** Курьер — студент (роль CLIENT). Пусто, пока заказ никто не взял. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "courier_id")
    private User courier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @ToString.Include
    private DeliveryStatus status;

    /** Оплата курьеру за эту доставку, фиксируется при создании. */
    @Column(nullable = false)
    private BigDecimal courierFee;

    /** Код клиента (зашифрован). Курьер вводит его, когда отдаёт заказ. */
    @Column(nullable = false)
    private String deliveryCode;

    /** Код курьера (зашифрован). Заведение вводит его, когда отдаёт заказ курьеру. */
    private String pickupCode;

    @Builder.Default
    @Column(nullable = false)
    private Integer deliveryCodeAttempts = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer pickupCodeAttempts = 0;

    /** Последнее действие администратора и его причина. */
    @Column(length = 600)
    private String adminNote;

    @Column(nullable = false)
    private Instant createdAt;
    private Instant assignedAt;
    private Instant pickedUpAt;
    private Instant deliveredAt;
    private Instant cancelledAt;

    /** Оптимистическая блокировка: из двух одновременных изменений одно получит 409. */
    @Version
    private Long version;
}
