package com.medai.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.UUID;

public interface ClinicalTool {

    String getName();

    String getDescription();

    boolean requiresConfirmation();

    String getInputSchemaJson();

    /**
     * Executes the tool with the given tenant, doctor, and input parameters.
     */
    ToolResult execute(UUID tenantId, UUID doctorId, UUID patientId, JsonNode inputParams);

    record ToolResult(
            boolean success,
            String summary,
            Map<String, Object> data,
            String errorMessage
    ) {
        public static ToolResult success(String summary, Map<String, Object> data) {
            return new ToolResult(true, summary, data, null);
        }

        public static ToolResult failure(String errorMessage) {
            return new ToolResult(false, null, Map.of(), errorMessage);
        }
    }
}
