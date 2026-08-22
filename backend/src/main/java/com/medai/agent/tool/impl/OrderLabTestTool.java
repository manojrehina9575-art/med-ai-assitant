package com.medai.agent.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medai.agent.tool.ClinicalTool;
import com.medai.clinical.entity.LabOrder;
import com.medai.clinical.repository.LabOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderLabTestTool implements ClinicalTool {

    private final LabOrderRepository labOrderRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "orderLabTest";
    }

    @Override
    public String getDescription() {
        return "Order clinical laboratory tests (e.g., Complete Blood Count, Comprehensive Metabolic Panel, CRP, Blood Cultures).";
    }

    @Override
    public boolean requiresConfirmation() {
        return true; // Critical action requiring practitioner confirmation
    }

    @Override
    public String getInputSchemaJson() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "tests": {
                      "type": "array",
                      "items": { "type": "string" },
                      "description": "List of diagnostic tests to order (e.g., ['CBC with Diff', 'CMP', 'CRP', 'Blood Culture x2'])"
                    },
                    "urgency": {
                      "type": "string",
                      "enum": ["ROUTINE", "URGENT", "STAT"],
                      "description": "Order priority level"
                    },
                    "clinicalIndication": {
                      "type": "string",
                      "description": "Reason for study or diagnostic suspicion"
                    }
                  },
                  "required": ["tests"]
                }
                """;
    }

    @Override
    public ToolResult execute(UUID tenantId, UUID doctorId, UUID patientId, JsonNode inputParams) {
        try {
            String urgency = inputParams.has("urgency") ? inputParams.get("urgency").asText().toUpperCase() : "ROUTINE";
            if (!urgency.equals("ROUTINE") && !urgency.equals("URGENT") && !urgency.equals("STAT")) {
                urgency = "ROUTINE";
            }

            String indication = inputParams.has("clinicalIndication") ? inputParams.get("clinicalIndication").asText() : null;
            String testsJson = "[]";
            if (inputParams.has("tests")) {
                testsJson = objectMapper.writeValueAsString(inputParams.get("tests"));
            }

            LabOrder order = LabOrder.builder()
                    .tenantId(tenantId)
                    .patientId(patientId)
                    .doctorId(doctorId)
                    .testNames(testsJson)
                    .urgency(urgency)
                    .status("PENDING")
                    .clinicalIndication(indication)
                    .build();

            LabOrder saved = labOrderRepository.save(order);
            log.info("Lab order created: {} for patient: {}", saved.getId(), patientId);

            Map<String, Object> data = new HashMap<>();
            data.put("labOrderId", saved.getId().toString());
            data.put("tests", inputParams.get("tests"));
            data.put("urgency", saved.getUrgency());
            data.put("status", saved.getStatus());
            data.put("clinicalIndication", saved.getClinicalIndication());

            int testCount = inputParams.has("tests") ? inputParams.get("tests").size() : 1;
            return ToolResult.success(
                    String.format("Ordered %d lab test(s) with urgency %s", testCount, urgency),
                    data
            );
        } catch (Exception e) {
            log.error("Failed to order lab tests: {}", e.getMessage(), e);
            return ToolResult.failure("Lab order failed: " + e.getMessage());
        }
    }
}
