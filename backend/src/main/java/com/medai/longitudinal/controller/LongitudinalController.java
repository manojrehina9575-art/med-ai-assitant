package com.medai.longitudinal.controller;

import com.medai.common.dto.ApiResponse;
import com.medai.longitudinal.model.LongitudinalResult;
import com.medai.longitudinal.service.LongitudinalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Report Longitudinal Comparison", description = "On-demand current-vs-prior report comparison")
public class LongitudinalController {

    private final LongitudinalService longitudinalService;

    @PostMapping("/{currentReviewId}/longitudinal/{priorReviewId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Compare a current report review against an explicit prior report",
               description = "Returns deterministic finding-level interval changes. No results are persisted.")
    public ResponseEntity<ApiResponse<LongitudinalResult>> compare(
            @PathVariable UUID currentReviewId,
            @PathVariable UUID priorReviewId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                longitudinalService.compare(currentReviewId, priorReviewId)));
    }
}
