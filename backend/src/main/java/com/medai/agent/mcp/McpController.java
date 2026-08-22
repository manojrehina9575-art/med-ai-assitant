package com.medai.agent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medai.agent.entity.ToolExecution;
import com.medai.agent.repository.ToolExecutionRepository;
import com.medai.agent.tool.ClinicalTool;
import com.medai.agent.tool.ToolRegistry;
import com.medai.auth.security.UserPrincipal;
import com.medai.common.dto.ApiResponse;
import com.medai.common.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Model Context Protocol (MCP)", description = "MCP Server for discovering and invoking Med-AI clinical tools")
public class McpController {

    private final ToolRegistry toolRegistry;
    private final ToolExecutionRepository toolExecutionRepository;
    private final ObjectMapper objectMapper;

    @GetMapping("/tools")
    @Operation(summary = "MCP Tool Discovery — Returns all available tools conforming to MCP JSON schema")
    public ResponseEntity<Map<String, Object>> getMcpTools() {
        List<Map<String, Object>> mcpTools = new ArrayList<>();

        for (ClinicalTool tool : toolRegistry.getAllTools()) {
            Map<String, Object> t = new HashMap<>();
            t.put("name", tool.getName());
            t.put("description", tool.getDescription());
            try {
                t.put("inputSchema", objectMapper.readTree(tool.getInputSchemaJson()));
            } catch (Exception e) {
                t.put("inputSchema", Map.of("type", "object"));
            }
            mcpTools.add(t);
        }

        return ResponseEntity.ok(Map.of("tools", mcpTools));
    }

    @PostMapping("/invoke")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    @Operation(summary = "MCP Tool Invocation — Execute a clinical tool directly via MCP protocol")
    public ResponseEntity<ApiResponse<ClinicalTool.ToolResult>> invokeTool(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody McpInvokeRequest request
    ) {
        ClinicalTool tool = toolRegistry.getTool(request.getToolName())
                .orElseThrow(() -> new ResourceNotFoundException("Tool not found: " + request.getToolName()));

        long start = System.currentTimeMillis();
        ClinicalTool.ToolResult result = tool.execute(
                principal.tenantId(),
                principal.userId(),
                request.getPatientId(),
                request.getParameters() != null ? request.getParameters() : objectMapper.createObjectNode()
        );
        long duration = System.currentTimeMillis() - start;

        // Record audit
        try {
            ToolExecution audit = ToolExecution.builder()
                    .tenantId(principal.tenantId())
                    .userId(principal.userId())
                    .patientId(request.getPatientId())
                    .toolName(request.getToolName())
                    .inputParams(objectMapper.writeValueAsString(request.getParameters()))
                    .outputData(objectMapper.writeValueAsString(result.data()))
                    .executionTimeMs(duration)
                    .success(result.success())
                    .errorMessage(result.errorMessage())
                    .build();
            toolExecutionRepository.save(audit);
        } catch (Exception ignored) {}

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @Data
    public static class McpInvokeRequest {
        private String toolName;
        private UUID patientId;
        private JsonNode parameters;
    }
}
