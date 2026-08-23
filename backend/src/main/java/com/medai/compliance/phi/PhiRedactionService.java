package com.medai.compliance.phi;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pattern-based PHI redaction for the identifiers that have a recognisable shape.
 *
 * <p><strong>This is not a complete HIPAA Safe Harbor de-identifier, and the class used to say it
 * was.</strong> It claimed all eighteen identifiers and implemented roughly eight, heuristically,
 * while being wrong in both directions at once:
 *
 * <ul>
 *   <li><b>Under-redacting.</b> Names were only caught behind an honorific — {@code Dr.}, {@code
 *       Mr.}, {@code Patient} — so the majority of names in free-text clinical notes passed
 *       straight through. That is the failure that matters: a note reading "discussed with Sarah
 *       at bedside" was reported as fully redacted.</li>
 *   <li><b>Over-redacting.</b> The postcode pattern was a bare {@code \b\d{5}\b}, which matched
 *       any five-digit number — lab values, accession numbers, device serials, doses in
 *       micrograms. It silently corrupted the clinical content it was supposed to be preserving.</li>
 * </ul>
 *
 * <p>What is fixed here: postcodes now require address context; the identifier set is wider and
 * each pattern is tighter; and, most usefully, {@link #redact(String, Collection)} takes the
 * identifiers already known from the patient record and removes those exactly. A name read off the
 * chart needs no guessing — the highest-recall path is to be told the answer.
 *
 * <p>What is still missing, and is stated rather than implied by silence: names not present in the
 * supplied identifier list and not preceded by an honorific or a label are not detected. Closing
 * that needs clinical NER (scispaCy, Philter, Comprehend Medical) measured against a labelled
 * corpus. {@link #coverage()} reports this per identifier so a compliance reviewer reads the real
 * state instead of the claim.
 */
@Service
@Slf4j
public class PhiRedactionService {

    // ── Structured identifiers: distinctive shapes, low false-positive rate ──

    private static final Pattern SSN_PATTERN = Pattern.compile(
            "\\b(?!000|666|9\\d{2})\\d{3}[- ]?(?!00)\\d{2}[- ]?(?!0000)\\d{4}\\b");

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "\\b(?:\\+?1[-. ]?)?\\(?([0-9]{3})\\)?[-. ]?([0-9]{3})[-. ]?([0-9]{4})\\b");

    /** Requires a digit in the identifier, so "RECORD REVIEWED" is not read as a record number. */
    private static final Pattern MRN_PATTERN = Pattern.compile(
            "\\b(?:MRN|MR#|UHID|HOSP(?:ITAL)?\\s*(?:NO|NUM|ID)|PATIENT\\s*ID|RECORD\\s*(?:NO|NUM|ID))"
            + "[:#]?\\s*([A-Z]{0,4}[-]?\\d[A-Z0-9-]{3,14})\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DATE_PATTERN = Pattern.compile(
            "\\b(?:0?[1-9]|1[0-2])[\\/\\-.](?:0?[1-9]|[12][0-9]|3[01])[\\/\\-.](?:19|20)\\d{2}\\b"
            + "|\\b(?:19|20)\\d{2}[\\/\\-.](?:0?[1-9]|1[0-2])[\\/\\-.](?:0?[1-9]|[12][0-9]|3[01])\\b");

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b");

    private static final Pattern URL_PATTERN = Pattern.compile(
            "\\bhttps?://[^\\s<>\"]+|\\bwww\\.[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");

    /**
     * Postcodes, but only in address context.
     *
     * <p>A bare five-digit number is far more often a lab value than a postcode in a clinical
     * note, so this requires either a preceding label or a preceding state/region code. That loses
     * unlabelled postcodes on their own line — an acceptable trade against corrupting the clinical
     * data the redacted note exists to preserve, and a trade that {@link #coverage()} records.
     */
    private static final Pattern ZIP_PATTERN = Pattern.compile(
            "(?i)(?:\\b(?:zip|postal|pin)\\s*(?:code)?\\s*[:#]?\\s*|\\b[A-Z]{2},?\\s+)(\\d{5}(?:-\\d{4})?)\\b");

    private static final Pattern ACCOUNT_PATTERN = Pattern.compile(
            "\\b(?:acct|account|policy|member|beneficiary|insurance)\\s*(?:no|num|number|#|id)?"
            + "\\s*[:#]?\\s*([A-Z0-9][A-Z0-9-]{5,19})\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern LICENSE_PATTERN = Pattern.compile(
            "\\b(?:licen[cs]e|dea|npi|registration)\\s*(?:no|num|number|#)?\\s*[:#]?\\s*"
            + "([A-Z0-9][A-Z0-9-]{5,14})\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DEVICE_SERIAL_PATTERN = Pattern.compile(
            "\\b(?:serial|device|implant|udi)\\s*(?:no|num|number|#|id)?\\s*[:#]?\\s*"
            + "([A-Z0-9][A-Z0-9-]{5,24})\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern VEHICLE_PATTERN = Pattern.compile(
            "\\b(?:vin|licen[cs]e\\s*plate|vehicle)\\s*(?:no|num|number|#)?\\s*[:#]?\\s*"
            + "([A-Z0-9][A-Z0-9-]{5,16})\\b",
            Pattern.CASE_INSENSITIVE);

    // ── Names: honorific or explicit label only ─────────────────────────────

    private static final Pattern NAME_HONORIFIC_PATTERN = Pattern.compile(
            "\\b(?:Dr|Mr|Mrs|Ms|Miss|Prof|Sr|Sister|Nurse|Pt)\\.?\\s+"
            + "([A-Z][a-z]+(?:\\s+[A-Z][a-z]+){0,2})\\b");

    /** "Patient Name: Jane Doe", "Attending: A Rivera", "Signed by: ..." */
    private static final Pattern NAME_LABELLED_PATTERN = Pattern.compile(
            "(?i)\\b(?:patient|pt|name|attending|consultant|physician|surgeon|referred\\s+by|"
            + "signed\\s+by|dictated\\s+by|next\\s+of\\s+kin|guardian|emergency\\s+contact)"
            + "\\s*(?:name)?\\s*[:\\-]\\s*([A-Z][A-Za-z'\\-]+(?:\\s+[A-Z][A-Za-z'\\-]+){0,2})\\b");

    /**
     * Applied in order. Earlier entries win the overlapping text, which is why the most specific
     * patterns come first: an email must be taken before the phone pattern can find ten digits
     * inside it, and a labelled MRN before the account pattern claims the same token.
     */
    private static final List<Rule> RULES = List.of(
            new Rule("EMAIL", EMAIL_PATTERN, 0),
            new Rule("URL", URL_PATTERN, 0),
            new Rule("IP_ADDR", IPV4_PATTERN, 0),
            new Rule("SSN", SSN_PATTERN, 0),
            new Rule("MRN", MRN_PATTERN, 1),
            new Rule("ACCOUNT", ACCOUNT_PATTERN, 1),
            new Rule("LICENSE", LICENSE_PATTERN, 1),
            new Rule("DEVICE_ID", DEVICE_SERIAL_PATTERN, 1),
            new Rule("VEHICLE_ID", VEHICLE_PATTERN, 1),
            new Rule("PHONE", PHONE_PATTERN, 0),
            new Rule("DATE", DATE_PATTERN, 0),
            new Rule("ZIP", ZIP_PATTERN, 1),
            new Rule("NAME", NAME_LABELLED_PATTERN, 1),
            new Rule("NAME", NAME_HONORIFIC_PATTERN, 1)
    );

    /**
     * @param type  redaction token prefix
     * @param group capture group to replace — 0 for the whole match, 1 to keep the label and
     *              redact only the value, so "MRN: [MRN_1]" stays readable as a record structure
     */
    private record Rule(String type, Pattern pattern, int group) {
    }

    @Data
    @Builder
    public static class RedactionResult {
        private String originalText;
        private String redactedText;
        private int totalRedactionsCount;
        private Map<String, Integer> redactionsByType;
        private Map<String, String> tokenMap; // token -> original value (for authorized de-pseudonymization)
        /** What this pass could and could not detect. Never omitted, so it cannot be overlooked. */
        private List<CoverageNote> coverage;
    }

    @Data
    @Builder
    public static class PhiEntity {
        private String type;
        private String originalValue;
        private String token;
        private int startOffset;
        private int endOffset;
    }

    /**
     * @param identifier one of the HIPAA Safe Harbor categories
     * @param status     DETECTED, PARTIAL or NOT_DETECTED
     * @param note       what a compliance reviewer needs to know about this line
     */
    public record CoverageNote(String identifier, String status, String note) {
    }

    public RedactionResult redact(String text) {
        return redact(text, List.of());
    }

    /**
     * Redacts PHI, removing the supplied known identifiers exactly as well as matching patterns.
     *
     * @param knownIdentifiers literal values already known from the patient record — name, MRN,
     *                         phone, email, address. These need no detection heuristic at all, and
     *                         passing them is by far the largest recall improvement available.
     */
    public RedactionResult redact(String text, Collection<String> knownIdentifiers) {
        if (text == null || text.isBlank()) {
            return RedactionResult.builder()
                    .originalText(text)
                    .redactedText(text)
                    .totalRedactionsCount(0)
                    .redactionsByType(Map.of())
                    .tokenMap(Map.of())
                    .coverage(coverage())
                    .build();
        }

        String result = text;
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> tokenMap = new LinkedHashMap<>();

        // Known values first: they are certainties, and taking them out early stops a pattern from
        // claiming part of one and leaving the rest behind.
        result = redactKnown(result, knownIdentifiers, counts, tokenMap);

        for (Rule rule : RULES) {
            result = applyRule(result, rule, counts, tokenMap);
        }

        int total = counts.values().stream().mapToInt(Integer::intValue).sum();

        return RedactionResult.builder()
                .originalText(text)
                .redactedText(result)
                .totalRedactionsCount(total)
                .redactionsByType(counts)
                .tokenMap(tokenMap)
                .coverage(coverage())
                .build();
    }

    /**
     * Removes values taken verbatim from the patient record.
     *
     * <p>Longest first: redacting "Jane Doe" before "Jane" leaves "[NAME_1]" rather than
     * "[NAME_1] Doe" with the surname still in the text.
     */
    private String redactKnown(String text, Collection<String> knownIdentifiers,
                               Map<String, Integer> counts, Map<String, String> tokenMap) {
        if (knownIdentifiers == null || knownIdentifiers.isEmpty()) {
            return text;
        }

        List<String> ordered = knownIdentifiers.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                // Two characters or fewer matches far too much to be worth redacting.
                .filter(value -> value.length() > 2)
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();

        String result = text;
        for (String value : ordered) {
            Pattern literal = Pattern.compile("(?i)\\b" + Pattern.quote(value) + "\\b");
            result = applyRule(result, new Rule("KNOWN_IDENTIFIER", literal, 0), counts, tokenMap);
        }
        return result;
    }

    private String applyRule(String input, Rule rule, Map<String, Integer> counts, Map<String, String> tokenMap) {
        Matcher matcher = rule.pattern().matcher(input);
        StringBuilder sb = new StringBuilder();
        int count = counts.getOrDefault(rule.type(), 0);
        boolean matched = false;

        while (matcher.find()) {
            String value = matcher.group(rule.group());
            if (value == null || value.isBlank()) {
                continue;
            }
            // A stretch of text already replaced by an earlier rule must not be redacted again.
            if (value.startsWith("[") && value.endsWith("]")) {
                continue;
            }

            matched = true;
            count++;
            String token = "[" + rule.type() + "_" + count + "]";
            tokenMap.put(token, value);

            // Replace only the captured group, so the surrounding label survives.
            String replacement = rule.group() == 0
                    ? token
                    : matcher.group().replace(value, token);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        if (matched) {
            counts.put(rule.type(), count);
        }
        return sb.toString();
    }

    /**
     * The honest coverage statement for the eighteen Safe Harbor identifiers.
     *
     * <p>Returned with every redaction and exposed on the API, because the previous claim of full
     * coverage was the part of this class most likely to cause harm: a reviewer who believes text
     * is de-identified handles it as if it were.
     */
    public List<CoverageNote> coverage() {
        return List.of(
                new CoverageNote("1. Names", "PARTIAL",
                        "Detected when supplied as a known identifier, preceded by an honorific "
                        + "(Dr., Mr., Nurse), or on a labelled field (Patient:, Attending:). "
                        + "Bare names in narrative text are NOT detected — this needs clinical NER."),
                new CoverageNote("2. Geographic subdivisions", "PARTIAL",
                        "Postcodes are detected only in address context (after a label or a "
                        + "state/region code). Street names and cities are NOT detected."),
                new CoverageNote("3. Dates", "DETECTED",
                        "Numeric dates in common formats. Written months ('3 March 1985') are NOT "
                        + "detected, and ages over 89 are not separately suppressed."),
                new CoverageNote("4. Telephone numbers", "DETECTED", "North American and +1 formats."),
                new CoverageNote("5. Fax numbers", "DETECTED", "Indistinguishable from telephone; same pattern."),
                new CoverageNote("6. Email addresses", "DETECTED", "Standard addresses."),
                new CoverageNote("7. Social security numbers", "DETECTED",
                        "Valid SSN shapes, excluding reserved ranges."),
                new CoverageNote("8. Medical record numbers", "DETECTED",
                        "Detected on a label (MRN, UHID, Hospital No, Patient ID). An unlabelled "
                        + "identifier is not distinguishable from an accession or order number."),
                new CoverageNote("9. Health plan beneficiary numbers", "DETECTED", "Detected on a label."),
                new CoverageNote("10. Account numbers", "DETECTED", "Detected on a label."),
                new CoverageNote("11. Certificate / licence numbers", "DETECTED",
                        "Licence, DEA and NPI on a label."),
                new CoverageNote("12. Vehicle identifiers", "DETECTED", "VIN and plate on a label."),
                new CoverageNote("13. Device identifiers and serial numbers", "DETECTED", "Detected on a label."),
                new CoverageNote("14. Web URLs", "DETECTED", "http/https and bare www."),
                new CoverageNote("15. IP addresses", "DETECTED", "IPv4 only; IPv6 is NOT detected."),
                new CoverageNote("16. Biometric identifiers", "NOT_DETECTED",
                        "Out of scope for text redaction."),
                new CoverageNote("17. Full-face photographs", "NOT_DETECTED",
                        "Out of scope for text redaction. Note that DICOM pixel data can carry "
                        + "burned-in identifiers, which nothing here inspects."),
                new CoverageNote("18. Any other unique identifier", "NOT_DETECTED",
                        "Open-ended by definition; no pattern can cover it."));
    }

    /**
     * Safely restore pseudonymized tokens back to original values for authorized clinician view.
     *
     * <p>Longest token first, so {@code [NAME_1]} is not matched inside {@code [NAME_11]}.
     */
    public String restore(String redactedText, Map<String, String> tokenMap) {
        if (redactedText == null || tokenMap == null || tokenMap.isEmpty()) {
            return redactedText;
        }

        String restored = redactedText;
        List<String> tokens = tokenMap.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();

        for (String token : tokens) {
            restored = restored.replace(token, tokenMap.get(token));
        }
        return restored;
    }
}
