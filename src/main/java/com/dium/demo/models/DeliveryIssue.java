package com.dium.demo.models;

import com.dium.demo.enums.DeliveryIssueStatus;
import com.dium.demo.enums.DeliveryIssueType;
import com.dium.demo.enums.DeliveryParticipant;
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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/** Проблема по доставке. Открывают участники доставки или система, закрывает администратор. */
@Entity
@Table(name = "delivery_issues")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DeliveryIssue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id", nullable = false)
    private Delivery delivery;

    /** Кто сообщил; пусто, если проблему создала система. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id")
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryParticipant reportedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @ToString.Include
    private DeliveryIssueType type;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @ToString.Include
    @Builder.Default
    private DeliveryIssueStatus status = DeliveryIssueStatus.OPEN;

    @Column(length = 1000)
    private String resolution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_id")
    private User resolvedBy;

    @Column(nullable = false)
    private Instant createdAt;
    private Instant resolvedAt;
}
