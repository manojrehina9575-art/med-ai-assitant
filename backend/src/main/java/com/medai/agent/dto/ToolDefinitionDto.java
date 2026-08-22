package com.medai.agent.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ToolDefinitionDto {
    private String name;
    private String description;
    private boolean requiresConfirmation;
    private String inputSchemaJson;
}
