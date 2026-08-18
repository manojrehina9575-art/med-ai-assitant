package com.medai.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A tenant's AI usage for one day.
 *
 * <p>Writes go through the upsert in {@link TenantAiUsageRepository}; this mapping exists so the
 * table is a first-class entity for reads (a usage dashboard, an invoice) rather than only ever
 * reachable through native SQL.
 */
@Entity
@Table(name = "tenant_ai_usage_daily")
@IdClass(TenantAiUsageId.class)
@Getter
@Setter
@NoArgsConstructor
public class TenantAiUsage {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Id
    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "request_count", nullable = false)
    private Integer requestCount;

    @Column(name = "prompt_tokens", nullable = false)
    private Long promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private Long completionTokens;

    @Column(name = "cost_usd", nullable = false, precision = 12, scale = 6)
    private BigDecimal costUsd;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
