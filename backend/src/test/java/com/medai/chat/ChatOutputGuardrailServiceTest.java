package com.medai.chat;

import com.medai.chat.dto.ChatCitationDto;
import com.medai.chat.guardrail.ChatOutputGuardrailService;
import com.medai.chat.guardrail.ChatOutputGuardrailService.OutputEvaluation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The output guardrail is the one that did not exist. These are the failures it has to catch —
 * answers that look completely normal and are not grounded in anything.
 */
class ChatOutputGuardrailServiceTest {

    private final ChatOutputGuardrailService service = new ChatOutputGuardrailService();

    private static ChatCitationDto citation(String title) {
        return ChatCitationDto.builder()
                .documentId(UUID.randomUUID())
                .title(title)
                .documentType("PROTOCOL")
                .chunkIndex(1)
                .excerpt("…")
                .build();
    }

    private static boolean has(OutputEvaluation e, String code) {
        return e.findings().stream().anyMatch(f -> f.code().equals(code));
    }

    @Nested
    @DisplayName("Citation grounding")
    class Citations {

        @Test
        @DisplayName("A citation index beyond what was retrieved is flagged")
        void citationBeyondRetrieved() {
            var evaluation = service.evaluate(
                    "Start empirical therapy [Citation 1]. Escalate per policy [Citation 4].",
                    List.of(citation("Sepsis pathway")),
                    "Start empirical therapy within one hour.");

            assertTrue(has(evaluation, "FABRICATED_CITATION"));
        }

        /**
         * The worst case: the model was given nothing and cited anyway. "[Citation 2]" beside a
         * claim reads as provenance, and here it points at nothing at all.
         */
        @Test
        @DisplayName("Citing when nothing was retrieved is flagged")
        void citationWithNoProtocols() {
            var evaluation = service.evaluate(
                    "Per hospital policy [Citation 2], admit to HDU.", List.of(), "");

            assertTrue(has(evaluation, "FABRICATED_CITATION"));
            assertTrue(evaluation.annotatedResponse().contains("Citation does not resolve"));
        }

        @Test
        @DisplayName("Citations that resolve are not flagged")
        void validCitationsPass() {
            var evaluation = service.evaluate(
                    "Give 500mg amoxicillin [Citation 1] then review [Citation 2].",
                    List.of(citation("CAP protocol"), citation("Review policy")),
                    "Give 500mg amoxicillin orally. Review at 48 hours.");

            assertFalse(has(evaluation, "FABRICATED_CITATION"));
        }
    }

    @Nested
    @DisplayName("Numeric grounding")
    class Numerics {

        @Test
        @DisplayName("A dose absent from the retrieved protocols is flagged")
        void ungroundedDoseIsFlagged() {
            var evaluation = service.evaluate(
                    "Give 875mg amoxicillin twice daily.",
                    List.of(citation("CAP protocol")),
                    "First line for community-acquired pneumonia is amoxicillin 500mg three times daily.");

            assertTrue(has(evaluation, "UNGROUNDED_QUANTITY"));
            assertTrue(evaluation.annotatedResponse().contains("875mg"));
        }

        @Test
        @DisplayName("A dose read off the protocol is not flagged")
        void groundedDosePasses() {
            var evaluation = service.evaluate(
                    "Give amoxicillin 500mg three times daily.",
                    List.of(citation("CAP protocol")),
                    "First line is amoxicillin 500 mg TID for five days.");

            assertFalse(has(evaluation, "UNGROUNDED_QUANTITY"));
        }

        /** "500 mg", "500mg" and "500 MG" are the same dose and must not read as different ones. */
        @Test
        @DisplayName("Spacing and case do not make a dose look ungrounded")
        void quantityNormalisation() {
            var evaluation = service.evaluate(
                    "Administer 1 G of paracetamol.",
                    List.of(citation("Analgesia protocol")),
                    "Paracetamol 1g orally, maximum four doses in 24 hours.");

            assertFalse(has(evaluation, "UNGROUNDED_QUANTITY"));
        }

        /**
         * Flagging every figure when there is nothing to compare against would mark whole answers
         * and train clinicians to ignore the warning.
         */
        @Test
        @DisplayName("With no protocol retrieved, one note covers the turn")
        void noProtocolYieldsSingleNote() {
            var evaluation = service.evaluate(
                    "Give 500mg amoxicillin and 1g paracetamol.", List.of(), "");

            assertTrue(has(evaluation, "NO_PROTOCOL_GROUNDING"));
            assertFalse(has(evaluation, "UNGROUNDED_QUANTITY"));
        }

        @Test
        @DisplayName("Bare numbers are not treated as dosing claims")
        void bareNumbersIgnored() {
            var evaluation = service.evaluate(
                    "Review in 3 days after 2 doses.", List.of(citation("Protocol")), "Review the patient.");

            assertFalse(has(evaluation, "UNGROUNDED_QUANTITY"));
            assertFalse(has(evaluation, "NO_PROTOCOL_GROUNDING"));
        }

        @Test
        @DisplayName("Figures the practitioner supplied count as grounded")
        void userSuppliedFiguresAreGrounded() {
            var evaluation = service.evaluate(
                    "A creatinine of 180 mmol/L supports dose reduction.",
                    List.of(citation("Renal dosing")),
                    "Reduce in renal impairment.\nPatient creatinine is 180 mmol/L today.");

            assertFalse(has(evaluation, "UNGROUNDED_QUANTITY"));
        }
    }

    @Nested
    @DisplayName("Certainty and annotation")
    class CertaintyAndAnnotation {

        @Test
        @DisplayName("Diagnostic certainty is flagged")
        void overconfidenceIsFlagged() {
            var evaluation = service.evaluate(
                    "This is definitely bacterial pneumonia and there is no risk of complication.",
                    List.of(citation("CAP protocol")), "Consider bacterial pneumonia.");

            assertTrue(has(evaluation, "UNSUPPORTED_CERTAINTY"));
        }

        @Test
        @DisplayName("A clean answer is returned unchanged")
        void cleanAnswerUntouched() {
            String answer = "Consider a chest radiograph and review the patient at 48 hours.";
            var evaluation = service.evaluate(answer, List.of(citation("Protocol")), "Chest radiograph advised.");

            assertTrue(evaluation.isClean());
            assertEquals(answer, evaluation.annotatedResponse());
        }

        /**
         * Findings go below the answer. Above the fold belongs to the acute-emergency banner, and
         * pushing that down for a citation-numbering problem inverts the urgency the reader feels.
         */
        @Test
        @DisplayName("Findings are appended, not prepended")
        void findingsAppearAfterTheAnswer() {
            var evaluation = service.evaluate(
                    "Give 875mg amoxicillin.", List.of(citation("CAP")), "Give 500mg amoxicillin.");

            String annotated = evaluation.annotatedResponse();
            assertTrue(annotated.indexOf("Give 875mg") < annotated.indexOf("Verification required"));
        }

        @Test
        @DisplayName("Empty output produces no findings")
        void emptyOutputIsSafe() {
            assertTrue(service.evaluate("", List.of(), "").isClean());
            assertTrue(service.evaluate(null, List.of(), "").isClean());
        }
    }
}
