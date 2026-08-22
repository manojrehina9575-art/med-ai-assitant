package com.medai.agent.controller;

import com.medai.agent.dto.*;
import com.medai.agent.service.AgentWorkflowService;
import com.medai.agent.tool.ToolRegistry;
import com.medai.auth.security.UserPrincipal;
import com.medai.clinical.entity.Appointment;
import com.medai.clinical.entity.LabOrder;
import com.medai.clinical.entity.Prescription;
import com.medai.clinical.repository.AppointmentRepository;
import com.medai.clinical.repository.LabOrderRepository;
import com.medai.clinical.repository.PrescriptionRepository;
import com.medai.common.dto.ApiResponse;
import com.medai.common.dto.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Agentic Workflows", description = "LangGraph4j Clinical Agent Workflows & Tool Execution")
public class AgentController {

    private final AgentWorkflowService workflowService;
    private final ToolRegistry toolRegistry;
    private final AppointmentRepository appointmentRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final LabOrderRepository labOrderRepository;

    @GetMapping("/tools")
    @Operation(summary = "List all available clinical tools with schemas")
    public ResponseEntity<ApiResponse<List<ToolDefinitionDto>>> listTools() {
        return ResponseEntity.ok(ApiResponse.ok(toolRegistry.getToolDefinitions()));
    }

    @PostMapping("/workflows")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    @Operation(summary = "Initiate a LangGraph4j clinical agent workflow for a goal")
    public ResponseEntity<ApiResponse<AgentWorkflowDto>> startWorkflow(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateWorkflowRequest request
    ) {
        AgentWorkflowDto workflow = workflowService.startWorkflow(
                principal.tenantId(),
                principal.userId(),
                request
        );
        return ResponseEntity.ok(ApiResponse.ok(workflow));
    }

    @GetMapping("/workflows/{id}")
    @Operation(summary = "Get workflow execution details and step progress")
    public ResponseEntity<ApiResponse<AgentWorkflowDto>> getWorkflow(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        AgentWorkflowDto workflow = workflowService.getWorkflow(principal.tenantId(), id);
        return ResponseEntity.ok(ApiResponse.ok(workflow));
    }

    @GetMapping("/workflows")
    @Operation(summary = "List workflows for the tenant or patient")
    public ResponseEntity<ApiResponse<PagedResponse<AgentWorkflowDto>>> listWorkflows(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID patientId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        var workflows = workflowService.listWorkflows(principal.tenantId(), patientId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(workflows));
    }

    @PostMapping("/workflows/{id}/steps/{stepId}/confirm")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    @Operation(summary = "Approve or reject a pending Human-in-the-Loop action step")
    public ResponseEntity<ApiResponse<AgentWorkflowDto>> confirmStep(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID stepId,
            @Valid @RequestBody ConfirmStepRequest request
    ) {
        AgentWorkflowDto workflow = workflowService.confirmStep(
                principal.tenantId(),
                principal.userId(),
                id,
                stepId,
                request
        );
        return ResponseEntity.ok(ApiResponse.ok(workflow));
    }

    // ── Clinical Records Endpoints ──────────────────────────────

    @GetMapping("/clinical/appointments")
    @Operation(summary = "List appointments for patient")
    public ResponseEntity<ApiResponse<List<Appointment>>> getAppointments(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam UUID patientId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                appointmentRepository.findByTenantIdAndPatientId(principal.tenantId(), patientId)
        ));
    }

    @GetMapping("/clinical/prescriptions")
    @Operation(summary = "List prescriptions for patient")
    public ResponseEntity<ApiResponse<List<Prescription>>> getPrescriptions(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam UUID patientId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                prescriptionRepository.findByTenantIdAndPatientId(principal.tenantId(), patientId)
        ));
    }

    @GetMapping("/clinical/lab-orders")
    @Operation(summary = "List lab orders for patient")
    public ResponseEntity<ApiResponse<List<LabOrder>>> getLabOrders(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam UUID patientId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                labOrderRepository.findByTenantIdAndPatientId(principal.tenantId(), patientId)
        ));
    }
}
