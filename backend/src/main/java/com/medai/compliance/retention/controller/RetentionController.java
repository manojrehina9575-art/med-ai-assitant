package com.medai.compliance.retention.controller;

import com.medai.compliance.retention.entity.DataRetentionPolicy;
import com.medai.compliance.retention.entity.RetentionPurgeLog;
import com.medai.compliance.retention.service.DataRetentionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/compliance/retention")
@RequiredArgsConstructor
@Tag(name = "Data Retention & Compliance Purge", description = "Tenant Data Retention Policies and Purge Audit")
public class RetentionController {

    private final DataRetentionService retentionService;

    @GetMapping("/policy")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN')")
    @Operation(summary = "Get current tenant data retention policy")
    public ResponseEntity<DataRetentionPolicy> getPolicy() {
        return ResponseEntity.ok(retentionService.getOrCreatePolicyForTenant());
    }

    @PutMapping("/policy")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN')")
    @Operation(summary = "Update data retention policy")
    public ResponseEntity<DataRetentionPolicy> updatePolicy(@RequestBody DataRetentionPolicy policy) {
        return ResponseEntity.ok(retentionService.updatePolicy(policy));
    }

    @PostMapping("/purge")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    @Operation(summary = "Execute manual data retention purge")
    public ResponseEntity<DataRetentionService.PurgeSummary> executePurge() {
        return ResponseEntity.ok(retentionService.executeManualPurge());
    }

    @GetMapping("/logs")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    @Operation(summary = "Get historical retention purge logs")
    public ResponseEntity<List<RetentionPurgeLog>> getPurgeLogs() {
        return ResponseEntity.ok(retentionService.getPurgeLogs());
    }
}
