package com.medai.billing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "invoices")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 40)
    private String invoiceNumber;

    @Column(name = "plan_code", nullable = false, length = 50)
    private String planCode;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_rate_percent", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal taxRatePercent = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    /**
     * What the AI providers charged for this period. Cost of goods, not price — kept so margin is
     * visible per customer per month, and deliberately never rendered on the customer's invoice.
     */
    @Column(name = "provider_cost_usd", nullable = false, precision = 14, scale = 6)
    @Builder.Default
    private BigDecimal providerCostUsd = BigDecimal.ZERO;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "issued_at")
    private Instant issuedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
