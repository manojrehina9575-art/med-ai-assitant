package com.medai.chat.dto;

import com.medai.chat.enums.SafetyFlag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuardrailEvaluationResult {
    @Builder.Default
    private boolean passed = true;

    @Builder.Default
    private List<SafetyFlag> flags = new ArrayList<>();

    @Builder.Default
    private List<String> notices = new ArrayList<>();

    private String sanitizedInput;
    private String emergencyInterventionMessage;
    private boolean isEmergency;
}
