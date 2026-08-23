package com.medai.chat.guardrail;

import com.medai.chat.dto.ChatCitationDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks what the model said against what it was given.
 *
 * <p>The input guardrail was the only one that existed. Post-processing did nothing but prepend a
 * banner, so a fabricated citation index, a dose recalled from training data, or a claim of
 * certainty no clinician would make all reached the practitioner looking exactly like a grounded
 * answer. For a decision-support product this is the guardrail that carries the risk: a provider
 * error is visible, a confident wrong dose is not.
 *
 * <p>Nothing here blocks a response. These are annotations a clinician reads before acting, and
 * counters an operator can alert on — deciding on the clinician's behalf is neither possible from
 * a regex nor desirable.
 */
@Service
@Slf4j
public class ChatOutputGuardrailService {

    /** A reference to a numbered citation in the model's answer. */
    private static final Pattern CITATION_REFERENCE =
            Pattern.compile("\\[Citation\\s+(\\d+)\\]", Pattern.CASE_INSENSITIVE);

    /**
     * A quantity with a clinical unit. Deliberately narrow: bare numbers ("3 days", "2 doses")
     * are not dosing claims, and treating them as such buries the real findings in noise.
     */
    private static final Pattern CLINICAL_QUANTITY = Pattern.compile(
            "(?<![\\w.])(\\d+(?:\\.\\d+)?)\\s*"
            + "(mcg/kg/min|mg/kg/day|mg/kg|mcg/kg|mmol/l|mmol/L|mg/dl|mg/dL|g/dl|g/dL|meq/l|mEq/L"
            + "|iu/kg|IU/kg|mcg|mg|kg|ml|mL|units?|IU|g)"
            + "(?![\\w/])",
            Pattern.CASE_INSENSITIVE);

    /**
     * Language a clinical decision-support system should not use about a specific patient.
     * Diagnostic certainty is the model's most persuasive failure mode.
     */
    private static final List<Pattern> OVERCONFIDENCE = List.of(
            Pattern.compile("(?i)\\b(definitely|certainly|undoubtedly|without\\s+(a\\s+)?doubt)\\b"),
            Pattern.compile("(?i)\\b(100|hundred)\\s*(%|percent)\\s+(certain|sure|safe|effective|accurate)\\b"),
            Pattern.compile("(?i)\\bthere\\s+is\\s+no\\s+(risk|chance|possibility)\\b"),
            Pattern.compile("(?i)\\b(always|never)\\s+safe\\b"),
            Pattern.compile("(?i)\\bguaranteed\\b"),
            Pattern.compile("(?i)\\bno\\s+need\\s+(for|to)\\s+(further|any)\\s+(testing|investigation|workup)\\b")
    );

    /**
     * One problem found in the model's answer.
     *
     * @param code     stable identifier, also the metric tag
     * @param message  what the clinician is told
     * @param evidence the exact text that triggered it, so the finding can be checked rather than
     *                 taken on trust
     */
    public record OutputFinding(String code, String message, List<String> evidence) {
    }

    public record OutputEvaluation(String annotatedResponse, List<OutputFinding> findings) {
        public boolean isClean() {
            return findings.isEmpty();
        }
    }

    /**
     * @param modelOutput   the raw answer
     * @param citations     the chunks actually retrieved for this turn, in citation order
     * @param groundingText patient context plus retrieved protocol text, from
     *                      {@code ChatContextBuilderService.BuiltContext}
     */
    public OutputEvaluation evaluate(String modelOutput, List<ChatCitationDto> citations, String groundingText) {
        if (modelOutput == null || modelOutput.isBlank()) {
            return new OutputEvaluation(modelOutput == null ? "" : modelOutput, List.of());
        }

        List<OutputFinding> findings = new ArrayList<>();
        int availableCitations = citations == null ? 0 : citations.size();
        String grounding = groundingText == null ? "" : groundingText;

        checkCitations(modelOutput, availableCitations).ifPresent(findings::add);
        checkQuantityGrounding(modelOutput, grounding, availableCitations > 0).ifPresent(findings::add);
        checkOverconfidence(modelOutput).ifPresent(findings::add);

        return new OutputEvaluation(annotate(modelOutput, findings), List.copyOf(findings));
    }

    // ── Citation grounding ───────────────────────────────────────────────────

    /**
     * Every {@code [Citation N]} must resolve to a chunk that was actually retrieved.
     *
     * <p>A model asked to cite will cite whether or not it was given anything to cite from, and
     * "[Citation 3]" beside a claim is read as provenance. When it points at nothing, it is worse
     * than no citation at all.
     */
    private Optional<OutputFinding> checkCitations(String output, int available) {
        Matcher matcher = CITATION_REFERENCE.matcher(output);
        Set<String> fabricated = new LinkedHashSet<>();

        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            if (index < 1 || index > available) {
                fabricated.add(matcher.group());
            }
        }

        if (fabricated.isEmpty()) {
            return Optional.empty();
        }

        String message = available == 0
                ? "The answer cites hospital protocols, but no protocol was retrieved for this "
                  + "question. These references point at nothing."
                : "The answer cites protocol numbers that were not retrieved for this question "
                  + "(only " + available + " available).";

        return Optional.of(new OutputFinding("FABRICATED_CITATION", message, List.copyOf(fabricated)));
    }

    // ── Numeric grounding ────────────────────────────────────────────────────

    /**
     * Flags dosing and threshold figures that appear in the answer but nowhere in the material the
     * model was given.
     *
     * <p>An ungrounded figure is not necessarily wrong — it is usually recalled correctly from
     * general medical knowledge. It is simply not something this hospital's protocols said, and a
     * practitioner reading a protocol-grounded answer is entitled to know which parts are which.
     *
     * <p>When no protocol was retrieved at all, flagging every figure individually would mark the
     * whole answer, so one note covers the turn instead.
     */
    private Optional<OutputFinding> checkQuantityGrounding(String output, String grounding, boolean hasProtocols) {
        Set<String> quantities = extractQuantities(output);
        if (quantities.isEmpty()) {
            return Optional.empty();
        }

        if (!hasProtocols) {
            return Optional.of(new OutputFinding(
                    "NO_PROTOCOL_GROUNDING",
                    "This answer contains dosing or threshold figures, and no hospital protocol was "
                    + "retrieved for this question. Nothing here was checked against your "
                    + "guidelines — verify before acting.",
                    List.copyOf(quantities)));
        }

        Set<String> groundedQuantities = extractQuantities(grounding);
        List<String> ungrounded = quantities.stream()
                .filter(q -> !groundedQuantities.contains(q))
                .toList();

        if (ungrounded.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new OutputFinding(
                "UNGROUNDED_QUANTITY",
                "These figures do not appear in the retrieved protocols or the patient record. "
                + "They come from the model's general knowledge, not from your hospital's "
                + "guidelines — confirm each one.",
                ungrounded));
    }

    /**
     * Reduces every quantity to a canonical {@code value+unit} token, so "500 mg", "500mg" and
     * "500 MG" compare equal and a difference in spacing is not read as a difference in dose.
     */
    private Set<String> extractQuantities(String text) {
        Set<String> quantities = new LinkedHashSet<>();
        Matcher matcher = CLINICAL_QUANTITY.matcher(text);
        while (matcher.find()) {
            String value = matcher.group(1);
            // Trailing zeros differ between "0.5" and ".50" but the dose does not.
            if (value.contains(".")) {
                value = value.replaceAll("0+$", "").replaceAll("\\.$", "");
            }
            quantities.add(value + normaliseUnit(matcher.group(2)));
        }
        return quantities;
    }

    private String normaliseUnit(String unit) {
        String u = unit.toLowerCase(Locale.ROOT);
        return switch (u) {
            case "unit", "units", "iu" -> "unit";
            case "ml" -> "ml";
            default -> u;
        };
    }

    // ── Overconfidence ───────────────────────────────────────────────────────

    private Optional<OutputFinding> checkOverconfidence(String output) {
        List<String> hits = new ArrayList<>();
        for (Pattern pattern : OVERCONFIDENCE) {
            Matcher matcher = pattern.matcher(output);
            if (matcher.find()) {
                hits.add(matcher.group().trim());
            }
        }

        if (hits.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new OutputFinding(
                "UNSUPPORTED_CERTAINTY",
                "The answer states diagnostic or safety certainty that decision-support output "
                + "cannot support.",
                hits));
    }

    // ── Annotation ───────────────────────────────────────────────────────────

    /**
     * Appends the findings below the answer rather than above it.
     *
     * <p>Above the fold is where the acute-emergency banner goes, and pushing that down for a
     * citation-numbering problem would invert the urgency the reader should feel.
     */
    private String annotate(String output, List<OutputFinding> findings) {
        if (findings.isEmpty()) {
            return output;
        }

        StringBuilder annotated = new StringBuilder(output.trim());
        annotated.append("\n\n---\n\n**⚠️ Verification required before acting on this answer**\n");

        for (OutputFinding finding : findings) {
            annotated.append("\n- **").append(readable(finding.code())).append("** — ")
                     .append(finding.message());
            if (!finding.evidence().isEmpty()) {
                annotated.append(" _(").append(String.join(", ", finding.evidence())).append(")_");
            }
        }

        return annotated.toString();
    }

    private String readable(String code) {
        return switch (code) {
            case "FABRICATED_CITATION" -> "Citation does not resolve";
            case "UNGROUNDED_QUANTITY" -> "Figures not found in your protocols";
            case "NO_PROTOCOL_GROUNDING" -> "No protocol was retrieved";
            case "UNSUPPORTED_CERTAINTY" -> "Overstated certainty";
            default -> code;
        };
    }
}
