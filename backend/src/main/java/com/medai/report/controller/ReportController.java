package com.medai.report.controller;

import com.medai.auth.security.UserPrincipal;
import com.medai.common.dto.ApiResponse;
import com.medai.common.dto.PagedResponse;
import com.medai.report.dto.ReportDtos.*;
import com.medai.report.service.CriticalResultService;
import com.medai.report.service.PriorStudyComparisonService;
import com.medai.report.service.ReportSignOffService;
import com.medai.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The reading workflow: worklist, sign-off, amendment, critical results and prior comparison.
 *
 * <p>Signing is restricted to DOCTOR and HOSPITAL_ADMIN. A lab technician can upload a study and
 * see its draft; taking clinical responsibility for a report is not theirs to do, and the whole
 * regulatory position rests on that being enforced rather than assumed.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Report Review", description = "Draft → review → sign, critical results, prior comparison")
public class ReportController {

    private final ReportSignOffService signOffService;
    private final CriticalResultService criticalResultService;
    private final PriorStudyComparisonService comparisonService;

    // ── Worklist and sign-off ────────────────────────────────────────────────

    @GetMapping("/worklist")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Reports awaiting review, oldest first")
    public ResponseEntity<ApiResponse<PagedResponse<ReviewView>>> worklist(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(signOffService.worklist(page, size)));
    }

    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Worklist counts and review outcomes",
               description = "Also the turnaround and accept/edit/reject numbers a pilot is measured on.")
    public ResponseEntity<ApiResponse<WorklistSummary>> summary() {
        return ResponseEntity.ok(ApiResponse.success(signOffService.summary(criticalResultService.countOpen())));
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Every report for one patient, newest first")
    public ResponseEntity<ApiResponse<PagedResponse<ReviewView>>> forPatient(
            @PathVariable UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(signOffService.forPatient(patientId, page, size)));
    }

    @GetMapping("/{reviewId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Read one report review")
    public ResponseEntity<ApiResponse<ReviewView>> get(@PathVariable UUID reviewId) {
        return ResponseEntity.ok(ApiResponse.success(signOffService.get(reviewId)));
    }

    @PostMapping("/{reviewId}/claim")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    @Operation(summary = "Take ownership of a draft",
               description = "Advisory, not a lock. Two clinicians opening one study is surfaced, "
                             + "not refused — refusing would strand a study whenever someone "
                             + "claimed it and walked away.")
    public ResponseEntity<ApiResponse<ReviewView>> claim(@PathVariable UUID reviewId,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(signOffService.claim(reviewId, principal)));
    }

    @PostMapping("/{reviewId}/sign")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    @Operation(summary = "Accept, correct or reject the draft",
               description = "The moment a named practitioner takes responsibility for the content. "
                             + "The action is also the training label: ACCEPTED is a positive "
                             + "example, EDITED pairs a real error with its correction, REJECTED "
                             + "records why the draft was unusable.")
    public ResponseEntity<ApiResponse<ReviewView>> sign(@PathVariable UUID reviewId,
                                           @RequestBody SignRequest request,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(signOffService.sign(reviewId, request, principal)));
    }

    @PostMapping("/{reviewId}/amend")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    @Operation(summary = "Supersede a signed report",
               description = "A signed report is never edited in place. The amendment links to what "
                             + "it replaces and both remain readable.")
    public ResponseEntity<ApiResponse<ReviewView>> amend(@PathVariable UUID reviewId,
                                            @RequestBody Map<String, String> body,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                signOffService.amend(reviewId, body.get("correctedContent"), principal)));
    }

    // ── Critical results ─────────────────────────────────────────────────────

    @GetMapping("/critical")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Critical findings still awaiting acknowledgement")
    public ResponseEntity<ApiResponse<List<EscalationView>>> criticalResults() {
        return ResponseEntity.ok(ApiResponse.success(criticalResultService.open()));
    }

    @PostMapping("/critical/{escalationId}/acknowledge")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    @Operation(summary = "Acknowledge a critical finding and record the action taken",
               description = "actionTaken is required. An acknowledgement with no action is a "
                             + "click, and the notification duty asks what happened to the patient.")
    public ResponseEntity<ApiResponse<EscalationView>> acknowledge(@PathVariable UUID escalationId,
                                                      @RequestBody AcknowledgeRequest request,
                                                      @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(criticalResultService.acknowledge(escalationId, request, principal)));
    }

    // ── Prior comparison ─────────────────────────────────────────────────────

    @GetMapping("/compare/{patientId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Trend a patient's studies against their priors",
               description = "Analytes aligned by LOINC, so a lab writing 'Hb' one month and "
                             + "'Haemoglobin' the next produces one trend rather than two rows.")
    public ResponseEntity<ApiResponse<ComparisonView>> compare(@PathVariable UUID patientId,
                                                  @RequestParam(defaultValue = "5") int studies) {
        return ResponseEntity.ok(ApiResponse.success(
                comparisonService.compare(TenantContext.requireTenantId(), patientId, studies)));
    }
}
