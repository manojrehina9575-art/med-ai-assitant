package com.medai.analytics.service;

import com.medai.analytics.dto.AnalyticsDtos;
import com.medai.analytics.dto.AnalyticsDtos.*;
import com.medai.notification.repository.NotificationRepository;
import com.medai.patient.repository.PatientRepository;
import com.medai.tenant.TenantContext;
import com.medai.upload.repository.MedicalFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final PatientRepository patientRepository;
    private final MedicalFileRepository medicalFileRepository;
    private final NotificationRepository notificationRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public DashboardSummaryDto getSummary() {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId   = currentUserId();

        long patients  = patientRepository.countByTenantId(tenantId);
        long files     = medicalFileRepository.countByTenantId(tenantId);

        // Analyses breakdown
        Map<String, Long> statusCounts = queryAnalysisStatusCounts(tenantId);
        long completed = statusCounts.getOrDefault("COMPLETED", 0L);
        long pending   = statusCounts.getOrDefault("PENDING", 0L)
                       + statusCounts.getOrDefault("PROCESSING", 0L);
        long failed    = statusCounts.getOrDefault("FAILED", 0L);
        long total     = statusCounts.values().stream().mapToLong(Long::longValue).sum();

        BigDecimal cost = queryTotalCost(tenantId);
        long unread    = notificationRepository.countByTenantIdAndUserIdAndIsReadFalse(tenantId, userId);

        return DashboardSummaryDto.builder()
                .totalPatients(patients)
                .totalFiles(files)
                .totalAnalyses(total)
                .completedAnalyses(completed)
                .pendingAnalyses(pending)
                .failedAnalyses(failed)
                .totalEstimatedCost(cost)
                .unreadNotifications(unread)
                .build();
    }

    @Transactional(readOnly = true)
    public List<DailyAnalysisCountDto> getAnalysesPerDay(int days) {
        UUID tenantId = TenantContext.requireTenantId();
        String sql = """
                SELECT
                    DATE(created_at AT TIME ZONE 'UTC') AS day,
                    COUNT(*)                             AS total,
                    SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed,
                    SUM(CASE WHEN status = 'FAILED'    THEN 1 ELSE 0 END) AS failed
                FROM analysis_requests
                WHERE tenant_id = ?
                  AND created_at >= NOW() - INTERVAL '%d days'
                GROUP BY day
                ORDER BY day ASC
                """.formatted(days);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, tenantId);

        // Fill in missing days with zero counts
        Map<String, DailyAnalysisCountDto> byDate = new LinkedHashMap<>();
        for (int i = days - 1; i >= 0; i--) {
            String d = LocalDate.now().minusDays(i).format(DateTimeFormatter.ISO_LOCAL_DATE);
            byDate.put(d, DailyAnalysisCountDto.builder().date(d).count(0).completed(0).failed(0).build());
        }

        for (Map<String, Object> row : rows) {
            String d = row.get("day").toString().substring(0, 10);
            byDate.put(d, DailyAnalysisCountDto.builder()
                    .date(d)
                    .count(((Number) row.get("total")).longValue())
                    .completed(((Number) row.get("completed")).longValue())
                    .failed(((Number) row.get("failed")).longValue())
                    .build());
        }

        return new ArrayList<>(byDate.values());
    }

    @Transactional(readOnly = true)
    public List<DiagnosisBreakdownDto> getTopDiagnoses(int limit) {
        UUID tenantId = TenantContext.requireTenantId();
        String sql = """
                SELECT analysis_type, COUNT(*) AS cnt
                FROM analysis_requests
                WHERE tenant_id = ? AND status = 'COMPLETED'
                GROUP BY analysis_type
                ORDER BY cnt DESC
                LIMIT ?
                """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, tenantId, limit);
        long grandTotal = rows.stream().mapToLong(r -> ((Number) r.get("cnt")).longValue()).sum();
        if (grandTotal == 0) return List.of();

        return rows.stream().map(row -> {
            long cnt = ((Number) row.get("cnt")).longValue();
            double pct = BigDecimal.valueOf(cnt * 100.0 / grandTotal)
                    .setScale(1, RoundingMode.HALF_UP).doubleValue();
            return DiagnosisBreakdownDto.builder()
                    .analysisType(row.get("analysis_type").toString())
                    .count(cnt)
                    .percentage(pct)
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ModelUsageDto> getModelUsage() {
        UUID tenantId = TenantContext.requireTenantId();
        String sql = """
                SELECT
                    COALESCE(model_used, 'unknown') AS model,
                    COUNT(*)                        AS cnt,
                    COALESCE(SUM(total_tokens), 0)  AS tokens,
                    COALESCE(SUM(estimated_cost), 0) AS cost
                FROM analysis_requests
                WHERE tenant_id = ?
                GROUP BY model_used
                ORDER BY cnt DESC
                """;

        return jdbcTemplate.queryForList(sql, tenantId).stream().map(row ->
            ModelUsageDto.builder()
                    .modelName(row.get("model").toString())
                    .analysisCount(((Number) row.get("cnt")).longValue())
                    .totalTokens(((Number) row.get("tokens")).longValue())
                    .totalCost(new BigDecimal(row.get("cost").toString()))
                    .build()
        ).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.AnalyticsDataDto getFullAnalytics(int days, int topN) {
        return AnalyticsDtos.AnalyticsDataDto.builder()
                .summary(getSummary())
                .analysesPerDay(getAnalysesPerDay(days))
                .topDiagnoses(getTopDiagnoses(topN))
                .modelUsage(getModelUsage())
                .build();
    }

    // ── helpers ─────────────────────────────────────────────

    private Map<String, Long> queryAnalysisStatusCounts(UUID tenantId) {
        String sql = "SELECT status, COUNT(*) AS cnt FROM analysis_requests WHERE tenant_id = ? GROUP BY status";
        Map<String, Long> result = new HashMap<>();
        jdbcTemplate.queryForList(sql, tenantId)
                .forEach(r -> result.put(r.get("status").toString(), ((Number) r.get("cnt")).longValue()));
        return result;
    }

    private BigDecimal queryTotalCost(UUID tenantId) {
        String sql = "SELECT COALESCE(SUM(estimated_cost), 0) FROM analysis_requests WHERE tenant_id = ?";
        BigDecimal cost = jdbcTemplate.queryForObject(sql, BigDecimal.class, tenantId);
        return cost != null ? cost : BigDecimal.ZERO;
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.medai.auth.security.UserPrincipal up) {
            return up.userId();
        }
        return null;
    }
}
