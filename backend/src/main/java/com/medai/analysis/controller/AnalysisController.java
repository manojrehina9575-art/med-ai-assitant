package com.medai.analysis.controller;

import com.medai.analysis.dto.AnalysisResponse;
import com.medai.analysis.dto.CreateAnalysisRequest;
import com.medai.analysis.service.AnalysisService;
import com.medai.auth.security.UserPrincipal;
import com.medai.common.dto.ApiResponse;
import com.medai.common.dto.PagedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    @PostMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR')")
    public ResponseEntity<ApiResponse<AnalysisResponse>> requestAnalysis(
            @Valid @RequestBody CreateAnalysisRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        AnalysisResponse response = analysisService.requestAnalysis(request, principal);
        return ResponseEntity.ok(ApiResponse.success("Analysis request submitted", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'LAB_TECH')")
    public ResponseEntity<ApiResponse<AnalysisResponse>> getAnalysis(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        AnalysisResponse response = analysisService.getAnalysis(id, principal);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'LAB_TECH')")
    public ResponseEntity<ApiResponse<PagedResponse<AnalysisResponse>>> getPatientAnalyses(
            @PathVariable UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        PagedResponse<AnalysisResponse> response = analysisService.getPatientAnalyses(patientId, page, size, principal);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR')")
    public ResponseEntity<ApiResponse<PagedResponse<AnalysisResponse>>> getAllAnalyses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        PagedResponse<AnalysisResponse> response = analysisService.getAllAnalyses(page, size, principal);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR')")
    public ResponseEntity<ApiResponse<AnalysisResponse>> retryAnalysis(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        AnalysisResponse response = analysisService.retryAnalysis(id, principal);
        return ResponseEntity.ok(ApiResponse.success("Analysis retry submitted", response));
    }
}
