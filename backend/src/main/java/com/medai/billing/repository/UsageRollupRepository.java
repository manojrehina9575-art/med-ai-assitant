package com.medai.billing.repository;

import com.medai.billing.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The billable facts for one period, read straight from the operational tables.
 *
 * <p>Counts completed analyses rather than AI requests: a chat turn and a chest X-ray report are
 * both "an AI request" and are worth very different amounts. Provider cost is read from
 * {@code tenant_ai_usage_daily} and carried for margin, never for pricing.
 */
@Repository
public interface UsageRollupRepository extends JpaRepository<Invoice, UUID> {

    /** A failed analysis is never billed — the first question a customer asks about an invoice. */
    @Query(value = """
            SELECT COUNT(*) FROM analysis_requests
             WHERE tenant_id = :tenantId
               AND status = 'COMPLETED'
               AND created_at >= :from AND created_at < :to
            """, nativeQuery = true)
    long countBillableAnalyses(@Param("tenantId") UUID tenantId,
                               @Param("from") LocalDate from,
                               @Param("to") LocalDate to);

    /**
     * Users who signed in during the period. Charging for provisioned rather than active accounts
     * invites a dispute the first time a hospital notices it is paying for leavers.
     */
    @Query(value = """
            SELECT COUNT(*) FROM users
             WHERE tenant_id = :tenantId
               AND is_active = TRUE
               AND last_login_at >= :from AND last_login_at < :to
            """, nativeQuery = true)
    long countActiveSeats(@Param("tenantId") UUID tenantId,
                          @Param("from") LocalDate from,
                          @Param("to") LocalDate to);

    @Query(value = """
            SELECT COALESCE(SUM(cost_usd), 0) FROM tenant_ai_usage_daily
             WHERE tenant_id = :tenantId
               AND usage_date >= :from AND usage_date < :to
            """, nativeQuery = true)
    BigDecimal sumProviderCost(@Param("tenantId") UUID tenantId,
                               @Param("from") LocalDate from,
                               @Param("to") LocalDate to);

    @Query(value = """
            SELECT COALESCE(SUM(request_count), 0) FROM tenant_ai_usage_daily
             WHERE tenant_id = :tenantId
               AND usage_date >= :from AND usage_date < :to
            """, nativeQuery = true)
    long sumAiRequests(@Param("tenantId") UUID tenantId,
                       @Param("from") LocalDate from,
                       @Param("to") LocalDate to);
}
