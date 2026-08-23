package com.medai.analytics.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

public final class AnalyticsDtos {

    private AnalyticsDtos() {}

    /** Top-level dashboard summary card data */
    @Data @Builder
    public static class DashboardSummaryDto {
        private long totalPatients;
        private long totalFiles;
        private long totalAnalyses;
        private long completedAnalyses;
        private long pendingAnalyses;
        private long failedAnalyses;
        private BigDecimal totalEstimatedCost;
        private long unreadNotifications;
    }

    /** One bucket in the analyses-per-day time series */
    @Data @Builder
    public static class DailyAnalysisCountDto {
        private String date;       // ISO date string: "2026-08-21"
        private long count;
        private long completed;
        private long failed;
    }

    /** One entry in the top diagnoses / analysis-type breakdown */
    @Data @Builder
    public static class DiagnosisBreakdownDto {
        private String analysisType;
        private long count;
        private double percentage;
    }

    /** Model usage summary row */
    @Data @Builder
    public static class ModelUsageDto {
        private String modelName;
        private long analysisCount;
        private long totalTokens;
        private BigDecimal totalCost;
    }

    /** Container for all analytics chart data */
    @Data @Builder
    public static class AnalyticsDataDto {
        private DashboardSummaryDto summary;
        private List<DailyAnalysisCountDto> analysesPerDay;
        private List<DiagnosisBreakdownDto> topDiagnoses;
        private List<ModelUsageDto> modelUsage;
    }
}
