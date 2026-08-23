package com.medai.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Durable per-tenant AI usage. Entity-less on purpose: both operations are single statements, and
 * an upsert expresses "add to today's total" far more safely than read-modify-write, which would
 * lose increments whenever two analyses finish at once.
 */
@Repository
public interface TenantAiUsageRepository extends JpaRepository<TenantAiUsage, TenantAiUsageId> {

    @Modifying
    @Query(value = """
            INSERT INTO tenant_ai_usage_daily
                (tenant_id, usage_date, request_count, prompt_tokens, completion_tokens, cost_usd, updated_at)
            VALUES (:tenantId, :usageDate, 1, :promptTokens, :completionTokens, :cost, NOW())
            ON CONFLICT (tenant_id, usage_date) DO UPDATE SET
                request_count     = tenant_ai_usage_daily.request_count + 1,
                prompt_tokens     = tenant_ai_usage_daily.prompt_tokens + EXCLUDED.prompt_tokens,
                completion_tokens = tenant_ai_usage_daily.completion_tokens + EXCLUDED.completion_tokens,
                cost_usd          = tenant_ai_usage_daily.cost_usd + EXCLUDED.cost_usd,
                updated_at        = NOW()
            """, nativeQuery = true)
    void addUsage(@Param("tenantId") UUID tenantId,
                  @Param("usageDate") LocalDate usageDate,
                  @Param("promptTokens") long promptTokens,
                  @Param("completionTokens") long completionTokens,
                  @Param("cost") BigDecimal cost);

    @Query(value = """
            SELECT COALESCE(cost_usd, 0) FROM tenant_ai_usage_daily
             WHERE tenant_id = :tenantId AND usage_date = :usageDate
            """, nativeQuery = true)
    BigDecimal findCostForDay(@Param("tenantId") UUID tenantId, @Param("usageDate") LocalDate usageDate);

    java.util.Optional<TenantAiUsage> findByTenantIdAndUsageDate(UUID tenantId, LocalDate usageDate);
}
