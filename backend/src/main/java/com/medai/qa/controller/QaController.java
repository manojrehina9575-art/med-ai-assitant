package com.medai.qa.controller;

import com.medai.common.dto.ApiResponse;
import com.medai.qa.model.QaResult;
import com.medai.qa.service.QaService;
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
@Tag(name = "Report QA", description = "On-demand report quality checks before clinician sign-off")
public class QaController {

    private final QaService qaService;

    @PostMapping("/{reviewId}/qa")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Run deterministic QA checks for a report review",
               description = "Returns potential issues and supporting evidence. The report is not modified.")
    public ResponseEntity<ApiResponse<QaResult>> evaluateReport(@PathVariable UUID reviewId) {
        return ResponseEntity.ok(ApiResponse.success(qaService.evaluateReport(reviewId)));
    }
}
