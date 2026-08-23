package com.medai.terminology.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Maps lab analyte names to LOINC codes.
 *
 * <p>Blood-report parameters arrive as free text from the extraction model — "Haemoglobin", "HGB",
 * "Hb" are all the same analyte and none of them is a code. Without LOINC, an Observation is a
 * string nobody else's system can interpret, results cannot be trended across labs that spell
 * things differently, and the FHIR layer produces resources that are technically conformant and
 * semantically useless.
 *
 * <p>Covers the analytes this product actually sees — CBC, renal, liver, lipids, glycaemic,
 * thyroid, cardiac, inflammatory markers, coagulation — with the synonyms and Indian-lab
 * abbreviations they arrive under. An unmapped analyte yields no code rather than a guessed one:
 * a wrong LOINC is worse than none, because downstream systems trust it.
 */
@Service
@Slf4j
public class LoincMapper {

    public static final String SYSTEM = "http://loinc.org";

    /**
     * @param code    LOINC code
     * @param display official LOINC long common name
     * @param unit    the UCUM unit LOINC expects, for the Observation's value quantity
     */
    public record Loinc(String code, String display, String unit) {
    }

    /** Lower-cased synonym → LOINC. Built once; several synonyms map to one entry. */
    private static final Map<String, Loinc> BY_NAME = build();

    private static Map<String, Loinc> build() {
        Map<String, Loinc> m = new HashMap<>();

        // ── Complete blood count ────────────────────────────────────────────
        put(m, new Loinc("718-7", "Hemoglobin [Mass/volume] in Blood", "g/dL"),
                "hemoglobin", "haemoglobin", "hgb", "hb");
        put(m, new Loinc("4544-3", "Hematocrit [Volume Fraction] of Blood by Automated count", "%"),
                "hematocrit", "haematocrit", "hct", "pcv", "packed cell volume");
        put(m, new Loinc("6690-2", "Leukocytes [#/volume] in Blood by Automated count", "10*3/uL"),
                "wbc", "white blood cell count", "leukocytes", "total leucocyte count", "tlc", "wbc count");
        put(m, new Loinc("789-8", "Erythrocytes [#/volume] in Blood by Automated count", "10*6/uL"),
                "rbc", "red blood cell count", "erythrocytes", "rbc count");
        put(m, new Loinc("777-3", "Platelets [#/volume] in Blood by Automated count", "10*3/uL"),
                "platelets", "platelet count", "plt");
        put(m, new Loinc("787-2", "MCV [Entitic volume] by Automated count", "fL"),
                "mcv", "mean corpuscular volume");
        put(m, new Loinc("785-6", "MCH [Entitic mass] by Automated count", "pg"),
                "mch", "mean corpuscular hemoglobin");
        put(m, new Loinc("786-4", "MCHC [Mass/volume] by Automated count", "g/dL"),
                "mchc", "mean corpuscular hemoglobin concentration");
        put(m, new Loinc("770-8", "Neutrophils/100 leukocytes in Blood by Automated count", "%"),
                "neutrophils", "neutrophil %", "neutrophils %", "polymorphs");
        put(m, new Loinc("736-9", "Lymphocytes/100 leukocytes in Blood by Automated count", "%"),
                "lymphocytes", "lymphocyte %", "lymphocytes %");
        put(m, new Loinc("5905-5", "Monocytes/100 leukocytes in Blood by Automated count", "%"),
                "monocytes", "monocyte %");
        put(m, new Loinc("713-8", "Eosinophils/100 leukocytes in Blood by Automated count", "%"),
                "eosinophils", "eosinophil %");
        put(m, new Loinc("788-0", "Erythrocyte distribution width [Ratio] by Automated count", "%"),
                "rdw", "red cell distribution width");
        put(m, new Loinc("30341-2", "Erythrocyte sedimentation rate", "mm/h"),
                "esr", "erythrocyte sedimentation rate");

        // ── Renal ───────────────────────────────────────────────────────────
        put(m, new Loinc("2160-0", "Creatinine [Mass/volume] in Serum or Plasma", "mg/dL"),
                "creatinine", "serum creatinine", "s. creatinine");
        put(m, new Loinc("3094-0", "Urea nitrogen [Mass/volume] in Serum or Plasma", "mg/dL"),
                "bun", "blood urea nitrogen", "urea nitrogen");
        put(m, new Loinc("22664-7", "Urea [Mass/volume] in Serum or Plasma", "mg/dL"),
                "urea", "blood urea", "serum urea");
        put(m, new Loinc("33914-3", "Glomerular filtration rate/1.73 sq M.predicted", "mL/min/{1.73_m2}"),
                "egfr", "gfr", "estimated gfr");
        put(m, new Loinc("2951-2", "Sodium [Moles/volume] in Serum or Plasma", "mmol/L"),
                "sodium", "na", "na+", "serum sodium");
        put(m, new Loinc("2823-3", "Potassium [Moles/volume] in Serum or Plasma", "mmol/L"),
                "potassium", "k", "k+", "serum potassium");
        put(m, new Loinc("2075-0", "Chloride [Moles/volume] in Serum or Plasma", "mmol/L"),
                "chloride", "cl", "cl-");
        put(m, new Loinc("3084-1", "Urate [Mass/volume] in Serum or Plasma", "mg/dL"),
                "uric acid", "urate", "serum uric acid");

        // ── Liver ───────────────────────────────────────────────────────────
        put(m, new Loinc("1742-6", "Alanine aminotransferase [Enzymatic activity/volume] in Serum or Plasma", "U/L"),
                "alt", "sgpt", "alanine aminotransferase", "alt (sgpt)");
        put(m, new Loinc("1920-8", "Aspartate aminotransferase [Enzymatic activity/volume] in Serum or Plasma", "U/L"),
                "ast", "sgot", "aspartate aminotransferase", "ast (sgot)");
        put(m, new Loinc("6768-6", "Alkaline phosphatase [Enzymatic activity/volume] in Serum or Plasma", "U/L"),
                "alp", "alkaline phosphatase", "alk phos");
        put(m, new Loinc("1975-2", "Bilirubin.total [Mass/volume] in Serum or Plasma", "mg/dL"),
                "total bilirubin", "bilirubin total", "bilirubin", "t. bilirubin");
        put(m, new Loinc("1968-7", "Bilirubin.direct [Mass/volume] in Serum or Plasma", "mg/dL"),
                "direct bilirubin", "bilirubin direct", "conjugated bilirubin");
        put(m, new Loinc("2885-2", "Protein [Mass/volume] in Serum or Plasma", "g/dL"),
                "total protein", "protein total", "serum protein");
        put(m, new Loinc("1751-7", "Albumin [Mass/volume] in Serum or Plasma", "g/dL"),
                "albumin", "serum albumin");
        put(m, new Loinc("2324-2", "Gamma glutamyl transferase [Enzymatic activity/volume] in Serum or Plasma", "U/L"),
                "ggt", "gamma glutamyl transferase", "gamma gt");

        // ── Glycaemic ───────────────────────────────────────────────────────
        put(m, new Loinc("4548-4", "Hemoglobin A1c/Hemoglobin.total in Blood", "%"),
                "hba1c", "hemoglobin a1c", "haemoglobin a1c", "glycated hemoglobin", "a1c");
        put(m, new Loinc("1558-6", "Fasting glucose [Mass/volume] in Serum or Plasma", "mg/dL"),
                "fasting glucose", "fbs", "fasting blood sugar", "fasting blood glucose");
        put(m, new Loinc("2345-7", "Glucose [Mass/volume] in Serum or Plasma", "mg/dL"),
                "glucose", "random blood sugar", "rbs", "blood glucose", "random glucose");
        put(m, new Loinc("1521-4", "Glucose [Mass/volume] in Serum or Plasma --2 hours post meal", "mg/dL"),
                "ppbs", "post prandial glucose", "postprandial blood sugar", "2 hour glucose");

        // ── Lipids ──────────────────────────────────────────────────────────
        put(m, new Loinc("2093-3", "Cholesterol [Mass/volume] in Serum or Plasma", "mg/dL"),
                "total cholesterol", "cholesterol", "cholesterol total");
        put(m, new Loinc("2085-9", "Cholesterol in HDL [Mass/volume] in Serum or Plasma", "mg/dL"),
                "hdl", "hdl cholesterol", "hdl-c");
        put(m, new Loinc("2089-1", "Cholesterol in LDL [Mass/volume] in Serum or Plasma", "mg/dL"),
                "ldl", "ldl cholesterol", "ldl-c");
        put(m, new Loinc("2571-8", "Triglyceride [Mass/volume] in Serum or Plasma", "mg/dL"),
                "triglycerides", "triglyceride", "tg");

        // ── Thyroid ─────────────────────────────────────────────────────────
        put(m, new Loinc("3016-3", "Thyrotropin [Units/volume] in Serum or Plasma", "m[IU]/L"),
                "tsh", "thyrotropin", "thyroid stimulating hormone");
        put(m, new Loinc("3024-7", "Thyroxine (T4) free [Mass/volume] in Serum or Plasma", "ng/dL"),
                "free t4", "ft4", "t4 free");
        put(m, new Loinc("3051-0", "Triiodothyronine (T3) free [Mass/volume] in Serum or Plasma", "pg/mL"),
                "free t3", "ft3", "t3 free");

        // ── Cardiac and inflammatory ────────────────────────────────────────
        put(m, new Loinc("10839-9", "Troponin I.cardiac [Mass/volume] in Serum or Plasma", "ng/mL"),
                "troponin i", "troponin", "trop i", "cardiac troponin");
        put(m, new Loinc("33762-6", "Natriuretic peptide.B prohormone N-Terminal [Mass/volume] in Serum or Plasma", "pg/mL"),
                "nt-probnp", "nt probnp", "probnp");
        put(m, new Loinc("1988-5", "C reactive protein [Mass/volume] in Serum or Plasma", "mg/L"),
                "crp", "c reactive protein", "c-reactive protein");
        put(m, new Loinc("30522-7", "C reactive protein [Mass/volume] in Serum or Plasma by High sensitivity method", "mg/L"),
                "hs-crp", "hs crp", "high sensitivity crp");
        put(m, new Loinc("2532-0", "Lactate dehydrogenase [Enzymatic activity/volume] in Serum or Plasma", "U/L"),
                "ldh", "lactate dehydrogenase");
        put(m, new Loinc("2276-4", "Ferritin [Mass/volume] in Serum or Plasma", "ng/mL"),
                "ferritin", "serum ferritin");

        // ── Coagulation ─────────────────────────────────────────────────────
        put(m, new Loinc("5902-2", "Prothrombin time (PT)", "s"),
                "pt", "prothrombin time");
        put(m, new Loinc("6301-6", "INR in Platelet poor plasma by Coagulation assay", "{INR}"),
                "inr", "international normalised ratio", "international normalized ratio");
        put(m, new Loinc("14979-9", "aPTT in Platelet poor plasma by Coagulation assay", "s"),
                "aptt", "activated partial thromboplastin time", "ptt");
        put(m, new Loinc("48065-7", "Fibrin D-dimer FEU [Mass/volume] in Platelet poor plasma", "ug/mL{FEU}"),
                "d-dimer", "d dimer", "ddimer");

        return Map.copyOf(m);
    }

    private static void put(Map<String, Loinc> m, Loinc loinc, String... synonyms) {
        for (String synonym : synonyms) {
            m.put(synonym, loinc);
        }
    }

    /**
     * Resolves an analyte name to LOINC, or empty when it is not recognised.
     *
     * <p>Punctuation and spacing vary between labs and between extractions of the same report, so
     * the lookup normalises both before matching. It does not guess: an unrecognised analyte
     * returns empty and the Observation carries the original text with no code, which downstream
     * systems treat as uncoded rather than mis-coded.
     */
    public Optional<Loinc> resolve(String analyteName) {
        if (analyteName == null || analyteName.isBlank()) {
            return Optional.empty();
        }

        String normalised = normalise(analyteName);
        Loinc direct = BY_NAME.get(normalised);
        if (direct != null) {
            return Optional.of(direct);
        }

        // Labs append the specimen or method: "Creatinine, Serum" / "HbA1c (HPLC)".
        String stripped = normalised
                .replaceAll("\\s*[,(\\[].*$", "")
                .replaceAll("\\s+(serum|plasma|blood|urine)$", "")
                .trim();

        return Optional.ofNullable(BY_NAME.get(stripped));
    }

    private String normalise(String name) {
        return name.toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** How many analytes the mapper knows, for the coverage endpoint. */
    public int knownSynonymCount() {
        return BY_NAME.size();
    }
}
