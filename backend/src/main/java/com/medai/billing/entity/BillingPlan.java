package com.medai.billing.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A rate card. Global reference data; the application reads it and never writes it. */
@Entity
@Table(name = "billing_plans")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BillingPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "platform_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal platformFee;

    @Column(name = "included_analyses", nullable = false)
    private Integer includedAnalyses;

    @Column(name = "price_per_analysis", nullable = false, precision = 12, scale = 4)
    private BigDecimal pricePerAnalysis;

    @Column(name = "price_per_active_seat", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerActiveSeat;

    @Column(name = "tax_rate_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRatePercent;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private Instant updatedAt;
}
