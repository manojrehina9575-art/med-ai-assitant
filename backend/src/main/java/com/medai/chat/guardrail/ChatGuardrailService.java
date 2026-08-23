package com.medai.chat.guardrail;

import com.medai.chat.dto.GuardrailEvaluationResult;
import com.medai.chat.enums.SafetyFlag;
import com.medai.patient.entity.Patient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ChatGuardrailService {

    /**
     * Prompt injection and jailbreak attempts.
     *
     * <p>Six literal phrasings used to be the whole list, so "ignore previous instructions" was
     * caught and "forget what you were told above" was not. These are built from the parts that
     * are hard to paraphrase away — an imperative to discard, override, or disclose the
     * instructions — rather than from whole sentences.
     *
     * <p>This will never be complete. It exists to make casual attempts visible and to flag them
     * for review, not to be a boundary the system's safety depends on; the boundary is that the
     * model has no tools and no authority here.
     */
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            // Discard / override the instructions, however phrased.
            Pattern.compile("(?i)\\b(ignore|disregard|forget|discard|override|bypass|skip)\\b[^.!?\\n]{0,40}?"
                            + "\\b(previous|prior|above|earlier|initial|original|all|any|your|the)\\b"
                            + "[^.!?\\n]{0,20}?\\b(instruction|instructions|prompt|prompts|rule|rules|"
                            + "guideline|guidelines|direction|directions|constraint|constraints|"
                            + "restriction|restrictions|filter|filters|policy|policies|training)\\b"),
            // Disclose the instructions.
            Pattern.compile("(?i)\\b(reveal|show|print|repeat|output|display|tell\\s+me|what\\s+(is|are))\\b"
                            + "[^.!?\\n]{0,30}?\\b(your|the)\\b[^.!?\\n]{0,20}?"
                            + "\\b(system\\s+prompt|initial\\s+prompt|hidden|internal|underlying|"
                            + "original)\\b[^.!?\\n]{0,20}?"
                            + "\\b(prompt|instruction|instructions|message|rules)\\b"),
            Pattern.compile("(?i)\\bsystem\\s*prompt\\s*[:=]"),
            // Role reassignment and persona jailbreaks.
            Pattern.compile("(?i)\\byou\\s+are\\s+(now|no\\s+longer)\\b"),
            Pattern.compile("(?i)\\b(DAN|developer|god|admin|root|jailbreak|unrestricted|unfiltered)\\s+mode\\b"),
            Pattern.compile("(?i)\\bpretend\\s+(you|to\\s+be)\\b[^.!?\\n]{0,30}?\\b(no|not|without)\\b"),
            Pattern.compile("(?i)\\bact\\s+as\\s+(if\\s+you\\s+(have|had)\\s+no|an?\\s+unrestricted)\\b"),
            // Attempts to fabricate a higher authority inside the message.
            Pattern.compile("(?i)^\\s*(system|assistant|developer)\\s*[:>]", Pattern.MULTILINE),
            Pattern.compile("(?i)<\\s*/?\\s*(system|instructions?|prompt)\\s*>")
    );

    /**
     * Presentations that are an emergency on their own, whatever else the message says.
     *
     * <p>The previous list was six exact phrases, which meant "crushing chest pain" fired and
     * "pt clutching chest, diaphoretic, pain radiating to jaw" did not. For a red-flag detector
     * the false-negative direction is the one that hurts, so this is built from individual terms
     * and from co-occurring symptoms rather than from whole sentences.
     */
    private static final List<Pattern> CRITICAL_TERMS = List.of(
            // Cardiac
            Pattern.compile("(?i)\\b(cardiac\\s+arrest|asystole|pulseless|v[- ]?fib|ventricular\\s+fibrillation|"
                            + "ventricular\\s+tachycardia|vt\\s+arrest|pea\\s+arrest|code\\s+blue)\\b"),
            Pattern.compile("(?i)\\b(stemi|nstemi|acute\\s+m\\.?i\\.?|myocardial\\s+infarction|"
                            + "acute\\s+coronary\\s+syndrome|acs)\\b"),
            Pattern.compile("(?i)\\b(aortic\\s+dissection|ruptured\\s+(aaa|aneurysm)|cardiac\\s+tamponade)\\b"),
            // Neurological
            Pattern.compile("(?i)\\b(acute\\s+stroke|cva|cerebrovascular\\s+accident|intracranial\\s+h(a)?emorrhage|"
                            + "subarachnoid\\s+h(a)?emorrhage|status\\s+epilepticus|gcs\\s*[<≤]?\\s*8)\\b"),
            Pattern.compile("(?i)\\b(facial\\s+droop|slurred\\s+speech|sudden\\s+(onset\\s+)?(hemiparesis|"
                            + "hemiplegia|aphasia)|worst\\s+headache\\s+of\\s+(their|his|her|my)\\s+life|"
                            + "thunderclap\\s+headache)\\b"),
            // Airway and breathing
            Pattern.compile("(?i)\\b(anaphylaxis|anaphylactic|stridor|airway\\s+obstruction|"
                            + "severe\\s+angioedema|respiratory\\s+arrest|apn(o|oe)ic|"
                            + "tension\\s+pneumothorax|silent\\s+chest)\\b"),
            Pattern.compile("(?i)\\b(sp?o2|o2\\s*sat(uration)?)\\s*(is\\s*)?[<≤]?\\s*(8[0-9]|[0-7][0-9])\\s*%?\\b"),
            // Circulation and bleeding
            Pattern.compile("(?i)\\b(massive\\s+h(a)?em(o|orr)?(ptysis|rrhage)|uncontrolled\\s+(arterial\\s+)?"
                            + "bleed(ing)?|exsanguinat|h(a)?emorrhagic\\s+shock|"
                            + "septic\\s+shock|cardiogenic\\s+shock|hypovol(a)?emic\\s+shock)\\b"),
            Pattern.compile("(?i)\\b(sepsis|septic)\\b[^.!?\\n]{0,30}\\b(suspect|likely|criteria|shock|source)\\b"),
            // Obstetric and metabolic
            Pattern.compile("(?i)\\b(eclampsia|eclamptic|placental\\s+abruption|cord\\s+prolapse|"
                            + "post[- ]?partum\\s+h(a)?emorrhage|pph)\\b"),
            Pattern.compile("(?i)\\b(diabetic\\s+ketoacidosis|dka|hyperosmolar\\s+hyperglyc(a)?emic|"
                            + "myx(o)?edema\\s+coma|thyroid\\s+storm|malignant\\s+hyperthermia)\\b"),
            // State
            Pattern.compile("(?i)\\b(unresponsive|unconscious|not\\s+breathing|no\\s+pulse|"
                            + "peri[- ]?arrest|rapidly\\s+deteriorating|crashing)\\b")
    );

    /**
     * Symptoms that are ordinary alone and an emergency together.
     *
     * <p>"Chest pain" is one of the most common things a clinician types and cannot be a red flag
     * by itself. Chest pain with diaphoresis, or with radiation to the jaw, is a different message
     * — and it is the one the old six-phrase list read as routine.
     */
    private static final List<SymptomCluster> SYMPTOM_CLUSTERS = List.of(
            new SymptomCluster(
                    "acute coronary syndrome",
                    // Three ways a real note says it: named symptom, observed behaviour, or the
                    // site given after the symptom. Only the first was matched, so "clutching
                    // chest, diaphoretic" read as routine.
                    Pattern.compile("(?i)(\\bchest\\s+(pain|pressure|tightness|discomfort|heaviness|"
                                    + "pain\\s+radiating)\\b"
                                    + "|\\b(clutch|grab|grip|hold)(ing|ed|s)?\\s+(his|her|their|the)?\\s*chest\\b"
                                    + "|\\b(pain|pressure|tightness|discomfort)\\s+(in|to|over|across)\\s+"
                                    + "(the\\s+)?chest\\b)"),
                    Pattern.compile("(?i)\\b(diaphore(sis|tic)|sweat(y|ing)|clammy|radiat(es|ing|ion)|"
                                    + "left\\s+arm|jaw|crushing|nausea|vomit|short(ness)?\\s+of\\s+breath|"
                                    + "dyspn(o)?ea|syncope|collapse|pallor)\\b")),
            new SymptomCluster(
                    "stroke",
                    Pattern.compile("(?i)\\b(weak(ness)?|numb(ness)?|droop|vision\\s+loss|confusion)\\b"),
                    Pattern.compile("(?i)\\b(sudden(ly)?|acute(ly)?|abrupt|one[- ]sided|unilateral|"
                                    + "left\\s+side|right\\s+side|face)\\b")),
            new SymptomCluster(
                    "meningitis",
                    Pattern.compile("(?i)\\b(fever|febrile|pyrexia)\\b"),
                    Pattern.compile("(?i)\\b(neck\\s+stiff(ness)?|photophobia|non[- ]?blanching\\s+rash|"
                                    + "petechial\\s+rash|purpuric\\s+rash)\\b")),
            new SymptomCluster(
                    "acute abdomen",
                    Pattern.compile("(?i)\\b(abdominal\\s+pain|abdo\\s+pain)\\b"),
                    Pattern.compile("(?i)\\b(rigid|guarding|rebound|peritonitic|peritonism|"
                                    + "board[- ]?like|distend)\\b"))
    );

    /** A pair of patterns that must both appear in the same message to raise a red flag. */
    private record SymptomCluster(String presentation, Pattern primary, Pattern qualifier) {
        boolean matches(String input) {
            return primary.matcher(input).find() && qualifier.matcher(input).find();
        }
    }

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
        detectEmergency(input).ifPresent(presentation -> {
            log.info("Acute red-flag presentation matched in input: {}", presentation);
            result.setEmergency(true);
            result.getFlags().add(SafetyFlag.RED_FLAG_EMERGENCY);
            result.getNotices().add("🚨 CRITICAL CLINICAL ALERT: Presentation matches acute emergency "
                                    + "criteria (" + presentation + "). Immediate bedside evaluation and "
                                    + "rapid response protocol required.");
            result.setEmergencyInterventionMessage(
                    "> ⚠️ **CRITICAL RED-FLAG ALERT** — possible **" + presentation + "**. "
                    + "Immediate bedside clinical evaluation, vital signs and stabilisation, and "
                    + "emergency physician notification are strongly indicated. Do not wait on this "
                    + "response before acting."
            );
        });

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
     * Returns the presentation matched, if the message describes an acute emergency.
     *
     * <p>Single critical terms are checked first because they are unambiguous; the symptom
     * clusters follow, and name what they matched so the banner tells the clinician which
     * emergency was suspected rather than just that one was.
     */
    private Optional<String> detectEmergency(String input) {
        for (Pattern pattern : CRITICAL_TERMS) {
            java.util.regex.Matcher matcher = pattern.matcher(input);
            if (matcher.find()) {
                return Optional.of(matcher.group().trim().toLowerCase(java.util.Locale.ROOT));
            }
        }
        for (SymptomCluster cluster : SYMPTOM_CLUSTERS) {
            if (cluster.matches(input)) {
                return Optional.of(cluster.presentation());
            }
        }
        return Optional.empty();
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
