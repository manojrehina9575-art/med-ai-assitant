package com.medai.compliance;

import com.medai.compliance.phi.PhiRedactionService;
import com.medai.compliance.phi.PhiRedactionService.RedactionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The redactor was wrong in both directions: it missed most names and destroyed most five-digit
 * numbers. Both directions are tested, because fixing one by worsening the other is not a fix.
 */
class PhiRedactionServiceTest {

    private final PhiRedactionService service = new PhiRedactionService();

    @Nested
    @DisplayName("Over-redaction")
    class OverRedaction {

        /**
         * The old postcode pattern was a bare five-digit match, so a lab value became "[ZIP_1]"
         * and the note lost the number it existed to record.
         */
        @Test
        @DisplayName("A five-digit lab value is not mistaken for a postcode")
        void labValueSurvives() {
            RedactionResult result = service.redact("Platelet count 45000 today, trending down.");

            assertTrue(result.getRedactedText().contains("45000"));
            assertFalse(result.getRedactionsByType().containsKey("ZIP"));
        }

        @Test
        @DisplayName("An accession number is not mistaken for a postcode")
        void accessionSurvives() {
            RedactionResult result = service.redact("Study accession 88421 reported.");
            assertTrue(result.getRedactedText().contains("88421"));
        }

        @Test
        @DisplayName("A postcode in address context is still redacted")
        void realPostcodeIsRedacted() {
            RedactionResult result = service.redact("Discharged to home, Springfield IL 62704.");

            assertFalse(result.getRedactedText().contains("62704"));
            assertEquals(1, result.getRedactionsByType().get("ZIP"));
        }

        @Test
        @DisplayName("A labelled postcode is redacted")
        void labelledPostcodeIsRedacted() {
            RedactionResult result = service.redact("Zip code: 90210");
            assertFalse(result.getRedactedText().contains("90210"));
        }
    }

    @Nested
    @DisplayName("Under-redaction")
    class UnderRedaction {

        /**
         * The largest recall gain available: the patient's name is on the chart, so there is no
         * need to detect it at all.
         */
        @Test
        @DisplayName("Known identifiers are removed without any heuristic")
        void knownIdentifiersRemoved() {
            RedactionResult result = service.redact(
                    "Discussed the plan with Sarah at bedside; her sister agreed.",
                    List.of("Sarah Chen", "Sarah", "MRN-99812"));

            assertFalse(result.getRedactedText().contains("Sarah"));
            assertTrue(result.getRedactedText().contains("at bedside"));
        }

        /** Longest first, or "Jane Doe" leaves "[KNOWN_IDENTIFIER_1] Doe" behind. */
        @Test
        @DisplayName("A full name is removed before its parts")
        void longestIdentifierWins() {
            RedactionResult result = service.redact(
                    "Jane Doe attended clinic.", List.of("Jane", "Jane Doe"));

            assertFalse(result.getRedactedText().contains("Doe"));
        }

        @Test
        @DisplayName("Honorific names are still caught")
        void honorificNamesCaught() {
            RedactionResult result = service.redact("Reviewed by Dr. Alan Grant this morning.");
            assertFalse(result.getRedactedText().contains("Alan Grant"));
        }

        @Test
        @DisplayName("Labelled name fields are caught")
        void labelledNamesCaught() {
            RedactionResult result = service.redact("Attending: Maria Sanchez\nPlan: discharge.");

            assertFalse(result.getRedactedText().contains("Maria Sanchez"));
            // The label survives, so the note still reads as a structured record.
            assertTrue(result.getRedactedText().contains("Attending:"));
        }

        @Test
        @DisplayName("Newly covered identifiers are redacted")
        void newIdentifierTypes() {
            RedactionResult result = service.redact(
                    "Portal https://records.example.com/p/12 — Policy no: AB-9928371, DEA #: BX1234563.");

            assertTrue(result.getRedactionsByType().containsKey("URL"));
            assertTrue(result.getRedactionsByType().containsKey("ACCOUNT"));
            assertTrue(result.getRedactionsByType().containsKey("LICENSE"));
        }

        @Test
        @DisplayName("Structured identifiers are redacted")
        void structuredIdentifiers() {
            RedactionResult result = service.redact(
                    "SSN 123-45-6789, ph 555-123-4567, a@b.com, MRN: A-12345, DOB 03/14/1968");

            String redacted = result.getRedactedText();
            assertFalse(redacted.contains("123-45-6789"));
            assertFalse(redacted.contains("555-123-4567"));
            assertFalse(redacted.contains("a@b.com"));
            assertFalse(redacted.contains("A-12345"));
            assertFalse(redacted.contains("03/14/1968"));
        }
    }

    @Nested
    @DisplayName("Honesty and round-tripping")
    class HonestyAndRestore {

        /**
         * The class used to claim all eighteen Safe Harbor identifiers while implementing about
         * eight. A reviewer who believes text is de-identified handles it as if it were.
         */
        @Test
        @DisplayName("Coverage is reported per identifier, and admits the gaps")
        void coverageIsHonest() {
            var coverage = service.coverage();

            assertEquals(18, coverage.size());
            assertTrue(coverage.stream().anyMatch(c -> c.status().equals("PARTIAL")));
            assertTrue(coverage.stream().anyMatch(c -> c.status().equals("NOT_DETECTED")));

            var names = coverage.stream().filter(c -> c.identifier().startsWith("1.")).findFirst().orElseThrow();
            assertEquals("PARTIAL", names.status());
            assertTrue(names.note().contains("NOT detected"));
        }

        @Test
        @DisplayName("Every redaction carries the coverage statement with it")
        void coverageTravelsWithTheResult() {
            assertNotNull(service.redact("Nothing sensitive here.").getCoverage());
        }

        @Test
        @DisplayName("Restore round-trips exactly")
        void restoreRoundTrips() {
            String original = "Contact a@b.com or 555-123-4567.";
            RedactionResult result = service.redact(original);

            assertEquals(original, service.restore(result.getRedactedText(), result.getTokenMap()));
        }

        /** [NAME_1] must not be substituted inside [NAME_11]. */
        @Test
        @DisplayName("Restore is not confused by token numbering")
        void restoreHandlesDoubleDigitTokens() {
            StringBuilder text = new StringBuilder();
            for (int i = 1; i <= 12; i++) {
                text.append("Dr. Name").append(i).append(" reviewed. ");
            }
            RedactionResult result = service.redact(text.toString());

            assertEquals(text.toString(), service.restore(result.getRedactedText(), result.getTokenMap()));
        }

        @Test
        @DisplayName("Empty input is handled")
        void emptyInput() {
            assertEquals(0, service.redact("").getTotalRedactionsCount());
            assertEquals(0, service.redact(null).getTotalRedactionsCount());
        }
    }
}
