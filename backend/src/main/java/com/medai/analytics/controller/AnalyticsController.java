package com.medai.analytics.controller;

import com.medai.analytics.dto.AnalyticsDtos.*;
import com.medai.analytics.service.AnalyticsService;
import com.medai.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /** Full analytics payload for the dashboard page (single call) */
    @GetMapping
    public ResponseEntity<ApiResponse<AnalyticsDataDto>> getFullAnalytics(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "10") int topN) {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getFullAnalytics(days, topN)));
    }

    /** Dashboard KPI summary cards */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DashboardSummaryDto>> getSummary() {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getSummary()));
    }

    /** Time-series: analyses per day for charting */
    @GetMapping("/analyses-per-day")
    public ResponseEntity<ApiResponse<List<DailyAnalysisCountDto>>> getAnalysesPerDay(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getAnalysesPerDay(days)));
    }

    /** Top diagnosis categories */
    @GetMapping("/top-diagnoses")
    public ResponseEntity<ApiResponse<List<DiagnosisBreakdownDto>>> getTopDiagnoses(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getTopDiagnoses(limit)));
    }

    /** AI model usage stats */
    @GetMapping("/model-usage")
    public ResponseEntity<ApiResponse<List<ModelUsageDto>>> getModelUsage() {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getModelUsage()));
    }
}
