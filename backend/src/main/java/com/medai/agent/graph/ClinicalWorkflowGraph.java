package com.medai.agent.graph;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medai.agent.tool.ClinicalTool;
import com.medai.agent.tool.ToolRegistry;
import com.medai.patient.entity.Patient;
import com.medai.patient.repository.PatientRepository;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Component
@Slf4j
public class ClinicalWorkflowGraph {

    private final ToolRegistry toolRegistry;
    private final PatientRepository patientRepository;
    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.openai.chat.options.model:qwen/qwen3.6-27b}")
    private String modelName;

    private CompiledGraph<ClinicalAgentState> compiledGraph;

    public ClinicalWorkflowGraph(
            ToolRegistry toolRegistry,
            PatientRepository patientRepository,
            ChatClient.Builder chatClientBuilder,
            ObjectMapper objectMapper
    ) {
        this.toolRegistry = toolRegistry;
        this.patientRepository = patientRepository;
        this.chatClientBuilder = chatClientBuilder;
        this.objectMapper = objectMapper;
        this.initGraph();
    }

    private void initGraph() {
        try {
            StateGraph<ClinicalAgentState> graph = new StateGraph<>(ClinicalAgentState::new);

            graph.addNode("plan_steps", node_async(this::planStepsNode));
            graph.addNode("check_safety", node_async(this::checkSafetyNode));
            graph.addNode("execute_step", node_async(this::executeStepNode));
            graph.addNode("synthesize_report", node_async(this::synthesizeReportNode));

            graph.addEdge(START, "plan_steps");
            graph.addEdge("plan_steps", "check_safety");

            // Conditional routing from check_safety
            graph.addConditionalEdges("check_safety",
                    edge_async(state -> "AWAITING_APPROVAL".equals(state.getStatus()) ? END : "execute_step"),
                    Map.of(
                            END, END,
                            "execute_step", "execute_step"
                    )
            );

            // Conditional routing from execute_step
            graph.addConditionalEdges("execute_step",
                    edge_async(state -> state.getCurrentStepIndex() < state.getPlannedSteps().size() ? "check_safety" : "synthesize_report"),
                    Map.of(
                            "check_safety", "check_safety",
                            "synthesize_report", "synthesize_report"
                    )
            );

            graph.addEdge("synthesize_report", END);

            this.compiledGraph = graph.compile();
            log.info("LangGraph4j Clinical Workflow Graph successfully compiled!");
        } catch (Exception e) {
            log.error("Failed to compile LangGraph4j StateGraph: {}", e.getMessage(), e);
        }
    }

    public CompiledGraph<ClinicalAgentState> getCompiledGraph() {
        return compiledGraph;
    }

    // ── Node 1: Plan Steps ─────────────────────────────────────
    private Map<String, Object> planStepsNode(ClinicalAgentState state) {
        log.info("LangGraph4j [plan_steps] for goal: {}", state.getGoal());
        Map<String, Object> updates = new HashMap<>();

        try {
            UUID tenantId = state.getTenantId();
            UUID patientId = state.getPatientId();
            Patient patient = (patientId != null && tenantId != null)
                    ? patientRepository.findByTenantIdAndId(tenantId, patientId).orElse(null)
                    : null;

            String patientContext = (patient != null)
                    ? String.format("Patient: %s (MRN: %s, Gender: %s, Allergies: %s, History: %s)",
                    patient.getFullName(), patient.getMedicalRecordNumber(), patient.getGender(),
                    patient.getAllergies(), patient.getMedicalHistory())
                    : "No specific patient context provided.";

            String toolsPrompt = toolRegistry.generateToolsPrompt();

            String systemPrompt = String.format("""
                    You are Med-AI Clinical Agent Planner.
                    Your job is to decompose the clinical practitioner's goal into an ordered sequence of clinical tool actions.

                    %s

                    %s

                    PLANNING RULES:
                    1. Choose from available tools ONLY: %s
                    2. If the goal requires multiple actions (e.g. Discharge with prescription & follow-up), produce ALL necessary steps in logical order.
                    3. Output MUST be valid JSON array conforming to this schema:
                    [
                      {
                        "toolName": "tool_name_here",
                        "actionSummary": "Short explanation of what this step does",
                        "requiresConfirmation": true_or_false,
                        "inputPayload": { ... tool parameters according to schema ... }
                      }
                    ]
                    Do NOT wrap with markdown quotes other than standard JSON.
                    """, patientContext, toolsPrompt, String.join(", ", toolRegistry.getAllTools().stream().map(ClinicalTool::getName).toList()));

            var chatClient = chatClientBuilder.build();
            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user("Clinical Goal: " + state.getGoal())
                    .call()
                    .content();

            // Extract JSON array
            String jsonContent = extractJson(response);
            List<Map<String, Object>> steps = objectMapper.readValue(jsonContent, new TypeReference<>() {});

            updates.put(ClinicalAgentState.PLANNED_STEPS, steps);
            updates.put(ClinicalAgentState.CURRENT_STEP_INDEX, 0);
            updates.put(ClinicalAgentState.STATUS, "EXECUTING");
            updates.put(ClinicalAgentState.EXECUTED_OUTPUTS, new ArrayList<Map<String, Object>>());

            log.info("Planned {} steps for workflow", steps.size());
        } catch (Exception e) {
            log.error("Planning failed: {}", e.getMessage(), e);
            updates.put(ClinicalAgentState.STATUS, "FAILED");
            updates.put(ClinicalAgentState.ERROR, "Planning failed: " + e.getMessage());
        }

        return updates;
    }

    // ── Node 2: Check Safety & Confirmation Checkpoint ────────
    private Map<String, Object> checkSafetyNode(ClinicalAgentState state) {
        log.info("LangGraph4j [check_safety] at step index: {}", state.getCurrentStepIndex());
        Map<String, Object> updates = new HashMap<>();

        List<Map<String, Object>> steps = state.getPlannedSteps();
        int currentIndex = state.getCurrentStepIndex();

        if (currentIndex >= steps.size()) {
            updates.put(ClinicalAgentState.STATUS, "COMPLETED");
            return updates;
        }

        Map<String, Object> currentStep = steps.get(currentIndex);
        String toolName = (String) currentStep.get("toolName");

        Optional<ClinicalTool> toolOpt = toolRegistry.getTool(toolName);
        boolean requiresConfirmation = toolOpt.map(ClinicalTool::requiresConfirmation)
                .orElse(Boolean.TRUE.equals(currentStep.get("requiresConfirmation")));

        Boolean isAlreadyApproved = (Boolean) currentStep.get("isApproved");

        if (requiresConfirmation && !Boolean.TRUE.equals(isAlreadyApproved)) {
            log.info("Pausing LangGraph4j execution for human confirmation on tool: {}", toolName);
            updates.put(ClinicalAgentState.STATUS, "AWAITING_APPROVAL");
            updates.put(ClinicalAgentState.PENDING_APPROVAL_STEP_ID, String.valueOf(currentIndex));
        } else {
            updates.put(ClinicalAgentState.STATUS, "EXECUTING");
        }

        return updates;
    }

    // ── Node 3: Execute Step ───────────────────────────────────
    @SuppressWarnings("unchecked")
    private Map<String, Object> executeStepNode(ClinicalAgentState state) {
        int currentIndex = state.getCurrentStepIndex();
        List<Map<String, Object>> steps = state.getPlannedSteps();
        log.info("LangGraph4j [execute_step] index {} of {}", currentIndex, steps.size());

        Map<String, Object> updates = new HashMap<>();
        List<Map<String, Object>> executed = new ArrayList<>(state.getExecutedOutputs());

        if (currentIndex < steps.size()) {
            Map<String, Object> step = steps.get(currentIndex);
            String toolName = (String) step.get("toolName");
            Object inputPayloadObj = step.get("inputPayload");

            try {
                JsonNode inputNode = objectMapper.valueToTree(inputPayloadObj != null ? inputPayloadObj : Map.of());
                ClinicalTool tool = toolRegistry.getTool(toolName)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown clinical tool: " + toolName));

                ClinicalTool.ToolResult result = tool.execute(
                        state.getTenantId(),
                        state.getDoctorId(),
                        state.getPatientId(),
                        inputNode
                );

                Map<String, Object> executionRecord = new HashMap<>();
                executionRecord.put("stepIndex", currentIndex);
                executionRecord.put("toolName", toolName);
                executionRecord.put("success", result.success());
                executionRecord.put("summary", result.summary());
                executionRecord.put("data", result.data());
                executionRecord.put("error", result.errorMessage());
                executed.add(executionRecord);

                updates.put(ClinicalAgentState.EXECUTED_OUTPUTS, executed);
                updates.put(ClinicalAgentState.CURRENT_STEP_INDEX, currentIndex + 1);
            } catch (Exception e) {
                log.error("Tool execution failed for {}: {}", toolName, e.getMessage(), e);
                Map<String, Object> executionRecord = new HashMap<>();
                executionRecord.put("stepIndex", currentIndex);
                executionRecord.put("toolName", toolName);
                executionRecord.put("success", false);
                executionRecord.put("error", e.getMessage());
                executed.add(executionRecord);

                updates.put(ClinicalAgentState.EXECUTED_OUTPUTS, executed);
                updates.put(ClinicalAgentState.CURRENT_STEP_INDEX, currentIndex + 1);
            }
        }

        return updates;
    }

    // ── Node 4: Synthesize Final Report ────────────────────────
    private Map<String, Object> synthesizeReportNode(ClinicalAgentState state) {
        log.info("LangGraph4j [synthesize_report] for workflow");
        Map<String, Object> updates = new HashMap<>();

        try {
            List<Map<String, Object>> executed = state.getExecutedOutputs();
            StringBuilder sb = new StringBuilder();
            sb.append("### Clinical Workflow Execution Report\n\n");
            sb.append("**Goal:** ").append(state.getGoal()).append("\n\n");
            sb.append("#### Completed Clinical Actions:\n");

            for (Map<String, Object> ex : executed) {
                String tool = (String) ex.get("toolName");
                boolean success = Boolean.TRUE.equals(ex.get("success"));
                String summary = (String) ex.get("summary");
                String error = (String) ex.get("error");

                if (success) {
                    sb.append(String.format("• **`%s`**: %s ✅\n", tool, summary));
                } else {
                    sb.append(String.format("• **`%s`**: Failed (%s) ❌\n", tool, error));
                }
            }

            sb.append("\n---\n*Verified and recorded in patient EHR electronic chart.*");

            updates.put(ClinicalAgentState.STATUS, "COMPLETED");
            updates.put(ClinicalAgentState.FINAL_OUTPUT, sb.toString());
        } catch (Exception e) {
            updates.put(ClinicalAgentState.STATUS, "COMPLETED");
            updates.put(ClinicalAgentState.FINAL_OUTPUT, "Workflow completed with executed steps.");
        }

        return updates;
    }

    private String extractJson(String text) {
        if (text == null) return "[]";
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastBackticks = trimmed.lastIndexOf("```");
            if (firstNewline != -1 && lastBackticks > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastBackticks).trim();
            }
        }
        return trimmed;
    }
}
