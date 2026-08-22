package com.medai.chat.guardrail;

import com.medai.chat.dto.GuardrailEvaluationResult;
import com.medai.chat.enums.SafetyFlag;
import com.medai.patient.entity.Patient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ChatGuardrailService {

    // Prompt injection & jailbreak patterns
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)ignore\\s+(all\\s+)?(previous|prior|above)\\s+instructions"),
            Pattern.compile("(?i)disregard\\s+(safety|clinical|all)\\s+guidelines"),
            Pattern.compile("(?i)system\\s+prompt\\s*:\\s*"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+in\\s+DAN\\s+mode"),
            Pattern.compile("(?i)bypass\\s+filter"),
            Pattern.compile("(?i)reveal\\s+(your|the)\\s+(hidden|internal|system)\\s+instructions")
    );

    // Acute medical emergency red flags
    private static final List<Pattern> EMERGENCY_PATTERNS = List.of(
            Pattern.compile("(?i)\\b(crushing|radiating)\\s+chest\\s+pain\\b"),
            Pattern.compile("(?i)\\b(acute\\s+myocardial\\s+infarction|stemi|nstemi)\\b"),
            Pattern.compile("(?i)\\b(facial\\s+droop|arm\\s+weakness|slurred\\s+speech|acute\\s+stroke)\\b"),
            Pattern.compile("(?i)\\b(anaphylaxis|stridor|airway\\s+obstruction|severe\\s+angioedema)\\b"),
            Pattern.compile("(?i)\\b(massive\\s+hemoptysis|uncontrolled\\s+arterial\\s+bleed)\\b"),
            Pattern.compile("(?i)\\b(cardiac\\s+arrest|ventricular\\s+fibrillation|unresponsive\\s+patient)\\b")
    );

    /**
     * Evaluates user input before sending to the LLM.
     */
    public GuardrailEvaluationResult evaluateInput(String input, Patient patient) {
        GuardrailEvaluationResult result = GuardrailEvaluationResult.builder()
                .passed(true)
                .flags(new ArrayList<>())
                .notices(new ArrayList<>())
                .sanitizedInput(input != null ? input.trim() : "")
                .build();

        if (input == null || input.isBlank()) {
            return result;
        }

        // 1. Check for prompt injection
        for (Pattern p : INJECTION_PATTERNS) {
            if (p.matcher(input).find()) {
                log.warn("Prompt injection pattern detected in input: '{}'", input);
                result.getFlags().add(SafetyFlag.POTENTIAL_INJECTION);
                result.getNotices().add("Notice: System directives and clinical safety boundaries are strictly enforced.");
                // Neutralize hostile directives
                result.setSanitizedInput(p.matcher(result.getSanitizedInput()).replaceAll("[sanitized safety instruction]"));
            }
        }

        // 2. Check for Acute Red-Flag Emergencies
        for (Pattern p : EMERGENCY_PATTERNS) {
            if (p.matcher(input).find()) {
                log.info("Critical acute red-flag pattern matched in input.");
                result.setEmergency(true);
                result.getFlags().add(SafetyFlag.RED_FLAG_EMERGENCY);
                result.getNotices().add("🚨 CRITICAL CLINICAL ALERT: Symptoms match acute emergency criteria. Immediate bedside evaluation and rapid response protocol required.");
                result.setEmergencyInterventionMessage(
                        "> ⚠️ **CRITICAL RED-FLAG ALERT**: Patient presentation matches acute emergency criteria. " +
                        "Immediate bedside clinical evaluation, vital signs stabilization, and emergency physician notification are strongly indicated."
                );
                break;
            }
        }

        // 3. Check for Patient Allergy Conflicts in input
        if (patient != null && patient.getAllergies() != null && !patient.getAllergies().isEmpty()) {
            String lowerInput = input.toLowerCase();
            for (String allergy : patient.getAllergies()) {
                String cleanAllergy = allergy.toLowerCase().trim();
                if (!cleanAllergy.isEmpty() && lowerInput.contains(cleanAllergy)) {
                    result.getFlags().add(SafetyFlag.ALLERGY_CONFLICT_DETECTED);
                    result.getNotices().add(String.format("⚠️ ALLERGY WARNING: Patient has documented allergy to '%s'. Ensure contraindication checks.", allergy));
                    break;
                }
            }
        }

        return result;
    }

    /**
     * Post-processes and checks model output safety.
     */
    public String postProcessOutput(String rawOutput, GuardrailEvaluationResult guardrailResult) {
        if (rawOutput == null) {
            return "";
        }

        StringBuilder formatted = new StringBuilder();

        // If an acute emergency was detected, prepend high-visibility alert banner
        if (guardrailResult != null && guardrailResult.isEmergency() && guardrailResult.getEmergencyInterventionMessage() != null) {
            formatted.append(guardrailResult.getEmergencyInterventionMessage()).append("\n\n---\n\n");
        }

        formatted.append(rawOutput.trim());

        return formatted.toString();
    }
}
