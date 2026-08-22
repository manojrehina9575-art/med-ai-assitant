package com.medai.agent.tool;

import com.medai.agent.dto.ToolDefinitionDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ToolRegistry {

    private final Map<String, ClinicalTool> toolsByName = new HashMap<>();

    public ToolRegistry(List<ClinicalTool> tools) {
        for (ClinicalTool tool : tools) {
            toolsByName.put(tool.getName(), tool);
            log.info("Registered clinical tool: {} (requires confirmation: {})",
                    tool.getName(), tool.requiresConfirmation());
        }
    }

    public Optional<ClinicalTool> getTool(String name) {
        return Optional.ofNullable(toolsByName.get(name));
    }

    public List<ClinicalTool> getAllTools() {
        return new ArrayList<>(toolsByName.values());
    }

    public List<ToolDefinitionDto> getToolDefinitions() {
        return toolsByName.values().stream()
                .map(t -> ToolDefinitionDto.builder()
                        .name(t.getName())
                        .description(t.getDescription())
                        .requiresConfirmation(t.requiresConfirmation())
                        .inputSchemaJson(t.getInputSchemaJson())
                        .build())
                .collect(Collectors.toList());
    }

    public String generateToolsPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("AVAILABLE CLINICAL TOOLS & ACTIONS:\n\n");
        for (ClinicalTool tool : toolsByName.values()) {
            sb.append(String.format("• Tool: `%s`\n", tool.getName()));
            sb.append(String.format("  Description: %s\n", tool.getDescription()));
            sb.append(String.format("  Requires Practitioner Approval: %s\n", tool.requiresConfirmation()));
            sb.append(String.format("  Input JSON Schema:\n```json\n%s\n```\n\n", tool.getInputSchemaJson().trim()));
        }
        return sb.toString();
    }
}
