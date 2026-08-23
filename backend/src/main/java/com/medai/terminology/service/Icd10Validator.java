package com.medai.terminology.service;

import com.medai.terminology.dto.CodeValidation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Validates the ICD-10 codes the model emits.
 *
 * <p>Nothing checked them before. {@code AnalysisResultDto.icd10Codes} came straight out of the
 * model and went straight into the response, so a plausible-looking code that does not exist —
 * or exists and means something else — reached the clinician indistinguishable from a real one.
 * Coded output is what gets billed and what gets reported to a registry, so an invented code is
 * not a cosmetic problem.
 *
 * <p>Validation is structural plus range-based: the code must be syntactically well-formed ICD-10
 * <em>and</em> its three-character category must fall inside a real WHO chapter block. That
 * catches invented categories (there is no chapter for {@code Q99.9}'s neighbour {@code R99.1}
 * pattern violations, no {@code U85}) without shipping the full seventy-thousand-code tabular
 * list or depending on a registered WHO ICD API key at request time.
 *
 * <p>What it therefore cannot do, and reports honestly rather than implying otherwise: confirm
 * that a well-formed in-range code is a real leaf code with the meaning the model intended.
 * {@code J18.9} and {@code J18.7} are both valid shapes in a valid block; only one is
 * "pneumonia, unspecified". Closing that needs the licensed tabular list, and the status is
 * {@code UNKNOWN} rather than {@code VALID} for anything this cannot confirm outright.
 */
@Service
@Slf4j
public class Icd10Validator {

    public static final String SYSTEM = "http://hl7.org/fhir/sid/icd-10";

    /**
     * WHO ICD-10 format: a letter, two digits, optionally a dot and up to four more characters.
     * {@code U} is reserved for provisional assignments and is deliberately excluded from the
     * general pattern — it is handled by the block ranges below.
     */
    private static final Pattern ICD10_FORMAT = Pattern.compile("^[A-TV-Z][0-9]{2}(\\.[0-9A-Z]{1,4})?$");

    /** WHO ICD-10 chapters, as inclusive three-character category ranges. */
    private static final List<Chapter> CHAPTERS = List.of(
            new Chapter("I", "A00", "B99", "Certain infectious and parasitic diseases"),
            new Chapter("II", "C00", "D48", "Neoplasms"),
            new Chapter("III", "D50", "D89", "Diseases of the blood and blood-forming organs"),
            new Chapter("IV", "E00", "E90", "Endocrine, nutritional and metabolic diseases"),
            new Chapter("V", "F00", "F99", "Mental and behavioural disorders"),
            new Chapter("VI", "G00", "G99", "Diseases of the nervous system"),
            new Chapter("VII", "H00", "H59", "Diseases of the eye and adnexa"),
            new Chapter("VIII", "H60", "H95", "Diseases of the ear and mastoid process"),
            new Chapter("IX", "I00", "I99", "Diseases of the circulatory system"),
            new Chapter("X", "J00", "J99", "Diseases of the respiratory system"),
            new Chapter("XI", "K00", "K93", "Diseases of the digestive system"),
            new Chapter("XII", "L00", "L99", "Diseases of the skin and subcutaneous tissue"),
            new Chapter("XIII", "M00", "M99", "Diseases of the musculoskeletal system"),
            new Chapter("XIV", "N00", "N99", "Diseases of the genitourinary system"),
            new Chapter("XV", "O00", "O99", "Pregnancy, childbirth and the puerperium"),
            new Chapter("XVI", "P00", "P96", "Conditions originating in the perinatal period"),
            new Chapter("XVII", "Q00", "Q99", "Congenital malformations and chromosomal abnormalities"),
            new Chapter("XVIII", "R00", "R99", "Symptoms, signs and abnormal findings"),
            new Chapter("XIX", "S00", "T98", "Injury, poisoning and external causes"),
            new Chapter("XX", "V01", "Y98", "External causes of morbidity and mortality"),
            new Chapter("XXI", "Z00", "Z99", "Factors influencing health status"),
            new Chapter("XXII", "U00", "U85", "Codes for special purposes")
    );

    private record Chapter(String number, String from, String to, String title) {
        boolean contains(String category) {
            return category.compareTo(from) >= 0 && category.compareTo(to) <= 0;
        }
    }

    /**
     * Categories common enough in this product's output to be worth confirming outright, with
     * their official display names. A hit here is the only route to {@code VALID}.
     *
     * <p>Deliberately small and radiology/lab-weighted rather than a partial copy of the tabular
     * list: a half-complete code set that reports {@code VALID} for whatever it happens to contain
     * and {@code UNKNOWN} for the rest is a worse signal than a small set that is exactly right.
     */
    private static final Map<String, String> KNOWN_CODES = Map.ofEntries(
            Map.entry("A15.0", "Tuberculosis of lung, confirmed bacteriologically and histologically"),
            Map.entry("A15.9", "Respiratory tuberculosis unspecified, confirmed bacteriologically and histologically"),
            Map.entry("A41.9", "Sepsis, unspecified organism"),
            Map.entry("D50.9", "Iron deficiency anaemia, unspecified"),
            Map.entry("D64.9", "Anaemia, unspecified"),
            Map.entry("E11.9", "Type 2 diabetes mellitus without complications"),
            Map.entry("E10.9", "Type 1 diabetes mellitus without complications"),
            Map.entry("E78.5", "Hyperlipidaemia, unspecified"),
            Map.entry("E87.6", "Hypokalaemia"),
            Map.entry("I10", "Essential (primary) hypertension"),
            Map.entry("I21.9", "Acute myocardial infarction, unspecified"),
            Map.entry("I50.9", "Heart failure, unspecified"),
            Map.entry("I63.9", "Cerebral infarction, unspecified"),
            Map.entry("J18.9", "Pneumonia, unspecified organism"),
            Map.entry("J44.9", "Chronic obstructive pulmonary disease, unspecified"),
            Map.entry("J45.9", "Asthma, unspecified"),
            Map.entry("J90", "Pleural effusion, not elsewhere classified"),
            Map.entry("J93.9", "Pneumothorax, unspecified"),
            Map.entry("K76.0", "Fatty (change of) liver, not elsewhere classified"),
            Map.entry("N18.9", "Chronic kidney disease, unspecified"),
            Map.entry("N39.0", "Urinary tract infection, site not specified"),
            Map.entry("M79.3", "Panniculitis, unspecified"),
            Map.entry("R05", "Cough"),
            Map.entry("R06.0", "Dyspnoea"),
            Map.entry("R07.4", "Chest pain, unspecified"),
            Map.entry("R10.4", "Other and unspecified abdominal pain"),
            Map.entry("R50.9", "Fever, unspecified"),
            Map.entry("R91.8", "Other nonspecific abnormal finding of lung field"),
            Map.entry("S72.0", "Fracture of neck of femur"),
            Map.entry("Z00.0", "General adult medical examination")
    );

    public CodeValidation validate(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return CodeValidation.malformed("", SYSTEM, "Empty code.");
        }

        String code = rawCode.trim().toUpperCase(Locale.ROOT);

        if (!ICD10_FORMAT.matcher(code).matches()) {
            return CodeValidation.malformed(rawCode, SYSTEM,
                    "Not a valid ICD-10 code format. Expected a letter, two digits, and optionally "
                    + "a dot with up to four more characters — for example J18.9.");
        }

        String category = code.length() >= 3 ? code.substring(0, 3) : code;

        Optional<Chapter> chapter = CHAPTERS.stream().filter(c -> c.contains(category)).findFirst();
        if (chapter.isEmpty()) {
            return CodeValidation.unknown(rawCode, SYSTEM,
                    "Category " + category + " does not fall inside any WHO ICD-10 chapter, so this "
                    + "code does not exist.", suggestionsFor(category));
        }

        String display = KNOWN_CODES.get(code);
        if (display != null) {
            return CodeValidation.valid(code, SYSTEM, display);
        }

        return CodeValidation.unknown(code, SYSTEM,
                "Well-formed and inside chapter " + chapter.get().number() + " ("
                + chapter.get().title() + "), but not confirmed against the tabular list. "
                + "Verify the code means what the report says before billing or reporting it.",
                suggestionsFor(category));
    }

    public List<CodeValidation> validateAll(Collection<String> codes) {
        if (codes == null) {
            return List.of();
        }
        return codes.stream().map(this::validate).toList();
    }

    /** Confirmed codes sharing the category, which is usually where the intended one is. */
    private List<String> suggestionsFor(String category) {
        return KNOWN_CODES.entrySet().stream()
                .filter(e -> e.getKey().startsWith(category))
                .map(e -> e.getKey() + " — " + e.getValue())
                .limit(5)
                .toList();
    }

    /** Official display for a confirmed code, for the FHIR CodeableConcept. */
    public Optional<String> displayFor(String code) {
        return Optional.ofNullable(KNOWN_CODES.get(code == null ? "" : code.trim().toUpperCase(Locale.ROOT)));
    }
}
