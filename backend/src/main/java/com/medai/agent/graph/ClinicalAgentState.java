package com.medai.agent.graph;

import org.bsc.langgraph4j.state.AgentState;

import java.util.*;

public class ClinicalAgentState extends AgentState {

    public static final String TENANT_ID = "tenantId";
    public static final String DOCTOR_ID = "doctorId";
    public static final String PATIENT_ID = "patientId";
    public static final String GOAL = "goal";
    public static final String STATUS = "status";
    public static final String PLANNED_STEPS = "plannedSteps";
    public static final String CURRENT_STEP_INDEX = "currentStepIndex";
    public static final String EXECUTED_OUTPUTS = "executedOutputs";
    public static final String PENDING_APPROVAL_STEP_ID = "pendingApprovalStepId";
    public static final String FINAL_OUTPUT = "finalOutput";
    public static final String ERROR = "error";

    public ClinicalAgentState(Map<String, Object> initData) {
        super(initData);
    }

    public UUID getTenantId() {
        return value(TENANT_ID).map(v -> v instanceof UUID ? (UUID) v : UUID.fromString(v.toString())).orElse(null);
    }

    public UUID getDoctorId() {
        return value(DOCTOR_ID).map(v -> v instanceof UUID ? (UUID) v : UUID.fromString(v.toString())).orElse(null);
    }

    public UUID getPatientId() {
        return value(PATIENT_ID).map(v -> v instanceof UUID ? (UUID) v : UUID.fromString(v.toString())).orElse(null);
    }

    public String getGoal() {
        return value(GOAL).map(Object::toString).orElse("");
    }

    public String getStatus() {
        return value(STATUS).map(Object::toString).orElse("PLANNING");
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getPlannedSteps() {
        return (List<Map<String, Object>>) value(PLANNED_STEPS).orElse(Collections.emptyList());
    }

    public int getCurrentStepIndex() {
        return value(CURRENT_STEP_INDEX).map(v -> ((Number) v).intValue()).orElse(0);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getExecutedOutputs() {
        return (List<Map<String, Object>>) value(EXECUTED_OUTPUTS).orElse(Collections.emptyList());
    }

    public String getPendingApprovalStepId() {
        return value(PENDING_APPROVAL_STEP_ID).map(Object::toString).orElse(null);
    }

    public String getFinalOutput() {
        return value(FINAL_OUTPUT).map(Object::toString).orElse(null);
    }

    public String getError() {
        return value(ERROR).map(Object::toString).orElse(null);
    }
}
