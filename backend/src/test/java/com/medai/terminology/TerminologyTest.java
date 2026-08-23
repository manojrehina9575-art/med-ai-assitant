package com.medai.terminology;

import com.medai.terminology.dto.CodeValidation;
import com.medai.terminology.service.Icd10Validator;
import com.medai.terminology.service.LoincMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coded output is what gets billed and what gets reported to a registry, so an invented code is
 * not a cosmetic problem. These cover both directions: catching codes that do not exist, and not
 * rejecting ones that do.
 */
class TerminologyTest {

    private final Icd10Validator icd10 = new Icd10Validator();
    private final LoincMapper loinc = new LoincMapper();

    @Nested
    @DisplayName("ICD-10")
    class Icd10 {

        @Test
        @DisplayName("A confirmed code resolves with its official display name")
        void confirmedCode() {
            CodeValidation result = icd10.validate("J18.9");

            assertTrue(result.isValid());
            assertEquals("Pneumonia, unspecified organism", result.display());
        }

        @Test
        @DisplayName("Case and whitespace do not defeat validation")
        void normalisesInput() {
            assertTrue(icd10.validate("  j18.9 ").isValid());
        }

        /** The failure that motivated this: a code the model invented, in a chapter that exists. */
        @Test
        @DisplayName("An unconfirmed but well-formed code is UNKNOWN, not VALID")
        void unconfirmedCodeIsNotValid() {
            CodeValidation result = icd10.validate("J18.7");

            assertEquals(CodeValidation.Status.UNKNOWN, result.status());
            assertTrue(result.note().contains("chapter X"));
            // The clinician gets somewhere to go, not just a rejection.
            assertTrue(result.suggestions().stream().anyMatch(s -> s.startsWith("J18.9")));
        }

        @Test
        @DisplayName("A category outside every WHO chapter is reported as nonexistent")
        void outOfRangeCategory() {
            CodeValidation result = icd10.validate("H99.1");

            assertEquals(CodeValidation.Status.UNKNOWN, result.status());
            assertTrue(result.note().contains("does not exist"));
        }

        @Test
        @DisplayName("Malformed codes are rejected outright")
        void malformedCodes() {
            assertEquals(CodeValidation.Status.MALFORMED, icd10.validate("PNEUMONIA").status());
            assertEquals(CodeValidation.Status.MALFORMED, icd10.validate("J1").status());
            assertEquals(CodeValidation.Status.MALFORMED, icd10.validate("189").status());
            assertEquals(CodeValidation.Status.MALFORMED, icd10.validate("").status());
        }

        @Test
        @DisplayName("A batch validates every code independently")
        void batchValidation() {
            List<CodeValidation> results = icd10.validateAll(List.of("J18.9", "NOTACODE", "I10"));

            assertEquals(3, results.size());
            assertTrue(results.get(0).isValid());
            assertEquals(CodeValidation.Status.MALFORMED, results.get(1).status());
            assertTrue(results.get(2).isValid());
        }
    }

    @Nested
    @DisplayName("LOINC")
    class Loinc {

        @Test
        @DisplayName("Synonyms of one analyte resolve to the same code")
        void synonymsConverge() {
            String expected = loinc.resolve("Hemoglobin").orElseThrow().code();

            assertEquals(expected, loinc.resolve("haemoglobin").orElseThrow().code());
            assertEquals(expected, loinc.resolve("HGB").orElseThrow().code());
            assertEquals(expected, loinc.resolve("Hb").orElseThrow().code());
            assertEquals("718-7", expected);
        }

        @Test
        @DisplayName("Indian-lab abbreviations resolve")
        void indianAbbreviations() {
            assertEquals("6690-2", loinc.resolve("TLC").orElseThrow().code());
            assertEquals("4544-3", loinc.resolve("PCV").orElseThrow().code());
            assertEquals("1558-6", loinc.resolve("FBS").orElseThrow().code());
            assertEquals("1742-6", loinc.resolve("SGPT").orElseThrow().code());
        }

        @Test
        @DisplayName("A specimen or method suffix does not defeat the lookup")
        void stripsSuffixes() {
            assertEquals("2160-0", loinc.resolve("Creatinine, Serum").orElseThrow().code());
            assertEquals("4548-4", loinc.resolve("HbA1c (HPLC)").orElseThrow().code());
        }

        /** A wrong LOINC is worse than none, because the consumer trusts it. */
        @Test
        @DisplayName("An unknown analyte returns nothing rather than a guess")
        void unknownAnalyteIsNotGuessed() {
            assertTrue(loinc.resolve("Zyxotropin level").isEmpty());
            assertTrue(loinc.resolve("").isEmpty());
            assertTrue(loinc.resolve(null).isEmpty());
        }

        @Test
        @DisplayName("Each mapping carries the UCUM unit LOINC expects")
        void carriesUcumUnit() {
            assertEquals("g/dL", loinc.resolve("hemoglobin").orElseThrow().unit());
            assertEquals("mmol/L", loinc.resolve("potassium").orElseThrow().unit());
        }
    }
}
