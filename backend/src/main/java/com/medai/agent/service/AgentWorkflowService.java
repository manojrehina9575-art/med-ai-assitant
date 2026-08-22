package com.medai.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medai.agent.dto.*;
import com.medai.agent.entity.AgentWorkflow;
import com.medai.agent.entity.AgentWorkflowStep;
import com.medai.agent.entity.ToolExecution;
import com.medai.agent.graph.ClinicalAgentState;
import com.medai.agent.graph.ClinicalWorkflowGraph;
import com.medai.agent.repository.AgentWorkflowRepository;
import com.medai.agent.repository.AgentWorkflowStepRepository;
import com.medai.agent.repository.ToolExecutionRepository;
import com.medai.agent.tool.ClinicalTool;
import com.medai.agent.tool.ToolRegistry;
import com.medai.common.dto.PagedResponse;
import com.medai.common.exception.ResourceNotFoundException;
import com.medai.patient.entity.Patient;
import com.medai.patient.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AgentWorkflowService {

    private final AgentWorkflowRepository workflowRepository;
    private final AgentWorkflowStepRepository stepRepository;
    private final ToolExecutionRepository toolExecutionRepository;
    private final PatientRepository patientRepository;
    private final ClinicalWorkflowGraph workflowGraph;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    @Transactional
    public AgentWorkflowDto startWorkflow(UUID tenantId, UUID userId, CreateWorkflowRequest request) {
        log.info("Starting LangGraph4j clinical workflow for patient: {}, goal: {}",
                request.getPatientId(), request.getGoal());

        // 1. Create Workflow DB record
        AgentWorkflow workflow = AgentWorkflow.builder()
                .tenantId(tenantId)
                .userId(userId)
                .patientId(request.getPatientId())
                .goal(request.getGoal())
                .status("PLANNING")
                .build();
        workflow = workflowRepository.save(workflow);

        // 2. Prepare LangGraph4j State
        Map<String, Object> initialData = new HashMap<>();
        initialData.put(ClinicalAgentState.TENANT_ID, tenantId);
        initialData.put(ClinicalAgentState.DOCTOR_ID, userId);
        initialData.put(ClinicalAgentState.PATIENT_ID, request.getPatientId());
        initialData.put(ClinicalAgentState.GOAL, request.getGoal());

        CompiledGraph<ClinicalAgentState> graph = workflowGraph.getCompiledGraph();

        try {
            // Execute graph
            var finalStateOpt = graph.invoke(initialData);
            if (finalStateOpt.isPresent()) {
                ClinicalAgentState finalState = finalStateOpt.get();
                syncGraphStateToDatabase(workflow, finalState, tenantId, userId);
            }
        } catch (Exception e) {
            log.error("LangGraph4j execution error: {}", e.getMessage(), e);
            workflow.setStatus("FAILED");
            workflow.setFinalOutput("Workflow execution failed: " + e.getMessage());
            workflowRepository.save(workflow);
        }

        return getWorkflow(tenantId, workflow.getId());
    }

    @Transactional
    public AgentWorkflowDto confirmStep(
            UUID tenantId,
            UUID userId,
            UUID workflowId,
            UUID stepId,
            ConfirmStepRequest request
    ) {
        AgentWorkflow workflow = workflowRepository.findByTenantIdAndId(tenantId, workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found: " + workflowId));

        AgentWorkflowStep step = stepRepository.findByWorkflowIdAndId(workflowId, stepId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow step not found: " + stepId));

        if (Boolean.TRUE.equals(request.getApproved())) {
            log.info("Practitioner approved step {} ({}) in workflow {}", step.getStepIndex(), step.getToolName(), workflowId);
            step.setConfirmationStatus("APPROVED");

            // Execute the approved tool action directly
            try {
                var inputJsonNode = request.getModifiedInputPayload() != null
                        ? request.getModifiedInputPayload()
                        : objectMapper.readTree(step.getInputPayload());

                ClinicalTool tool = toolRegistry.getTool(step.getToolName())
                        .orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + step.getToolName()));

                long start = System.currentTimeMillis();
                ClinicalTool.ToolResult result = tool.execute(tenantId, userId, workflow.getPatientId(), inputJsonNode);
                long duration = System.currentTimeMillis() - start;

                step.setOutputPayload(objectMapper.writeValueAsString(result.data()));
                step.setStatus(result.success() ? "COMPLETED" : "FAILED");
                step.setErrorMessage(result.errorMessage());
                stepRepository.save(step);

                // Record tool audit
                ToolExecution audit = ToolExecution.builder()
                        .tenantId(tenantId)
                        .userId(userId)
                        .patientId(workflow.getPatientId())
                        .workflowId(workflowId)
                        .toolName(step.getToolName())
                        .inputParams(objectMapper.writeValueAsString(inputJsonNode))
                        .outputData(objectMapper.writeValueAsString(result.data()))
                        .executionTimeMs(duration)
                        .success(result.success())
                        .errorMessage(result.errorMessage())
                        .build();
                toolExecutionRepository.save(audit);

            } catch (Exception e) {
                log.error("Failed to execute approved tool: {}", e.getMessage(), e);
                step.setStatus("FAILED");
                step.setErrorMessage(e.getMessage());
                stepRepository.save(step);
            }
        } else {
            log.info("Practitioner rejected step {} ({}) in workflow {}", step.getStepIndex(), step.getToolName(), workflowId);
            step.setConfirmationStatus("REJECTED");
            step.setStatus("SKIPPED");
            step.setErrorMessage(request.getRejectionReason() != null ? request.getRejectionReason() : "Rejected by clinician");
            stepRepository.save(step);
        }

        // Check if there are more steps pending approval or execution
        List<AgentWorkflowStep> allSteps = stepRepository.findByWorkflowIdOrderByStepIndexAsc(workflowId);
        boolean anyPendingApproval = allSteps.stream().anyMatch(s -> "PENDING".equals(s.getConfirmationStatus()));
        boolean allDone = allSteps.stream().allMatch(s -> "COMPLETED".equals(s.getStatus()) || "SKIPPED".equals(s.getStatus()) || "FAILED".equals(s.getStatus()));

        if (anyPendingApproval) {
            workflow.setStatus("AWAITING_APPROVAL");
        } else if (allDone) {
            workflow.setStatus("COMPLETED");
            // Generate completed synthesis summary
            StringBuilder sb = new StringBuilder();
            sb.append("### Clinical Actions Summary\n\n");
            for (AgentWorkflowStep s : allSteps) {
                String icon = "COMPLETED".equals(s.getStatus()) ? "✅" : "SKIPPED".equals(s.getStatus()) ? "⏭️" : "❌";
                sb.append(String.format("• **`%s`**: %s (%s) %s\n", s.getToolName(), s.getActionSummary(), s.getStatus(), icon));
            }
            workflow.setFinalOutput(sb.toString());
        }
        workflowRepository.save(workflow);

        return getWorkflow(tenantId, workflowId);
    }

    @Transactional(readOnly = true)
    public AgentWorkflowDto getWorkflow(UUID tenantId, UUID workflowId) {
        AgentWorkflow workflow = workflowRepository.findByTenantIdAndId(tenantId, workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found: " + workflowId));

        Patient patient = workflow.getPatientId() != null
                ? patientRepository.findByTenantIdAndId(tenantId, workflow.getPatientId()).orElse(null)
                : null;

        List<AgentWorkflowStep> steps = stepRepository.findByWorkflowIdOrderByStepIndexAsc(workflowId);
        List<AgentWorkflowStepDto> stepDtos = steps.stream().map(this::toStepDto).collect(Collectors.toList());

        return AgentWorkflowDto.builder()
                .id(workflow.getId())
                .patientId(workflow.getPatientId())
                .patientName(patient != null ? patient.getFullName() : null)
                .patientMrn(patient != null ? patient.getMedicalRecordNumber() : null)
                .goal(workflow.getGoal())
                .status(workflow.getStatus())
                .planSummary(workflow.getPlanSummary())
                .finalOutput(workflow.getFinalOutput())
                .steps(stepDtos)
                .createdAt(workflow.getCreatedAt())
                .updatedAt(workflow.getUpdatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public PagedResponse<AgentWorkflowDto> listWorkflows(UUID tenantId, UUID patientId, Pageable pageable) {
        Page<AgentWorkflow> page = (patientId != null)
                ? workflowRepository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(tenantId, patientId, pageable)
                : workflowRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);

        List<AgentWorkflowDto> dtos = page.getContent().stream()
                .map(w -> getWorkflow(tenantId, w.getId()))
                .collect(Collectors.toList());

        return PagedResponse.of(dtos, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    private void syncGraphStateToDatabase(
            AgentWorkflow workflow,
            ClinicalAgentState state,
            UUID tenantId,
            UUID userId
    ) {
        try {
            List<Map<String, Object>> plannedSteps = state.getPlannedSteps();
            List<Map<String, Object>> executedOutputs = state.getExecutedOutputs();

            // Clear old steps if any
            var existingSteps = stepRepository.findByWorkflowIdOrderByStepIndexAsc(workflow.getId());
            if (existingSteps.isEmpty()) {
                for (int i = 0; i < plannedSteps.size(); i++) {
                    Map<String, Object> stepMap = plannedSteps.get(i);
                    String toolName = (String) stepMap.get("toolName");
                    String actionSummary = (String) stepMap.getOrDefault("actionSummary", toolName);
                    Object inputPayload = stepMap.get("inputPayload");

                    Optional<ClinicalTool> toolOpt = toolRegistry.getTool(toolName);
                    boolean reqConf = toolOpt.map(ClinicalTool::requiresConfirmation)
                            .orElse(Boolean.TRUE.equals(stepMap.get("requiresConfirmation")));

                    // Check if this step was already executed in graph
                    final int stepIndex = i;
                    var executedOpt = executedOutputs.stream()
                            .filter(ex -> Integer.valueOf(stepIndex).equals(ex.get("stepIndex")))
                            .findFirst();

                    String stepStatus = "PENDING";
                    String outPayload = "{}";
                    String errMsg = null;
                    String confStatus = reqConf ? "PENDING" : "NOT_REQUIRED";

                    if (executedOpt.isPresent()) {
                        var ex = executedOpt.get();
                        boolean success = Boolean.TRUE.equals(ex.get("success"));
                        stepStatus = success ? "COMPLETED" : "FAILED";
                        errMsg = (String) ex.get("error");
                        if (ex.get("data") != null) {
                            outPayload = objectMapper.writeValueAsString(ex.get("data"));
                        }
                    }

                    AgentWorkflowStep step = AgentWorkflowStep.builder()
                            .tenantId(tenantId)
                            .workflowId(workflow.getId())
                            .stepIndex(i + 1)
                            .toolName(toolName)
                            .actionSummary(actionSummary)
                            .inputPayload(objectMapper.writeValueAsString(inputPayload != null ? inputPayload : Map.of()))
                            .outputPayload(outPayload)
                            .requiresConfirmation(reqConf)
                            .confirmationStatus(confStatus)
                            .status(stepStatus)
                            .errorMessage(errMsg)
                            .build();

                    stepRepository.save(step);
                }
            }

            workflow.setStatus(state.getStatus());
            workflow.setPlanSummary(String.format("Planned %d clinical steps using LangGraph4j", plannedSteps.size()));
            workflow.setFinalOutput(state.getFinalOutput());
            workflow.setStatePayload(objectMapper.writeValueAsString(state.data()));
            workflowRepository.save(workflow);

        } catch (Exception e) {
            log.error("Failed to sync LangGraph4j state to DB: {}", e.getMessage(), e);
        }
    }

    private AgentWorkflowStepDto toStepDto(AgentWorkflowStep step) {
        try {
            return AgentWorkflowStepDto.builder()
                    .id(step.getId())
                    .stepIndex(step.getStepIndex())
                    .toolName(step.getToolName())
                    .actionSummary(step.getActionSummary())
                    .inputPayload(objectMapper.readTree(step.getInputPayload()))
                    .outputPayload(objectMapper.readTree(step.getOutputPayload()))
                    .requiresConfirmation(step.getRequiresConfirmation())
                    .confirmationStatus(step.getConfirmationStatus())
                    .status(step.getStatus())
                    .errorMessage(step.getErrorMessage())
                    .createdAt(step.getCreatedAt())
                    .updatedAt(step.getUpdatedAt())
                    .build();
        } catch (Exception e) {
            return AgentWorkflowStepDto.builder()
                    .id(step.getId())
                    .stepIndex(step.getStepIndex())
                    .toolName(step.getToolName())
                    .actionSummary(step.getActionSummary())
                    .requiresConfirmation(step.getRequiresConfirmation())
                    .confirmationStatus(step.getConfirmationStatus())
                    .status(step.getStatus())
                    .errorMessage(step.getErrorMessage())
                    .build();
        }
    }
}
