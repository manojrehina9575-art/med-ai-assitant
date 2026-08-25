package com.medai.fhir.mapper;

import com.medai.analysis.dto.AnalysisResultDto;
import com.medai.analysis.entity.AnalysisRequest;
import com.medai.analysis.enums.AnalysisStatus;
import com.medai.analysis.enums.AnalysisType;
import com.medai.fhir.FhirConstants;
import com.medai.report.entity.ReportReview;
import com.medai.terminology.dto.CodeValidation;
import com.medai.terminology.service.Icd10Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * Maps a completed analysis to a FHIR DiagnosticReport.
 *
 * <p>Two things here are deliberate and worth stating.
 *
 * <p><strong>Conclusion codes are validated first.</strong> The ICD-10 codes on
 * {@code AnalysisResultDto} come straight from the model. A code that {@link Icd10Validator}
 * cannot confirm is still carried — suppressing it would hide what the report said — but it is
 * carried as {@code text} with no {@code coding}, so a receiving system never treats an
 * unconfirmed code as an asserted one. Emitting a coded conclusion is how a claim gets billed;
 * emitting an invented one as coded is how it gets billed wrongly.
 *
 * <p><strong>The report is never {@code final}.</strong> Everything this product produces is a
 * draft for a clinician to sign, and FHIR has a status that says exactly that. Marking AI output
 * {@code final} would assert a clinical sign-off that has not happened — and would undercut the
 * regulatory position that keeps this out of device territory.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DiagnosticReportResourceMapper {

    private final Icd10Validator icd10Validator;

    public DiagnosticReport toFhir(AnalysisRequest analysis, AnalysisResultDto result, List<Reference> results) {
        return toFhir(analysis, result, results, null);
    }

    /**
     * @param signedReview the practitioner's sign-off, when one exists. Its presence is what
     *                     promotes the report from preliminary to final.
     */
    public DiagnosticReport toFhir(AnalysisRequest analysis, AnalysisResultDto result,
                                   List<Reference> results, ReportReview signedReview) {
        DiagnosticReport report = new DiagnosticReport();
        report.setId(analysis.getId().toString());

        report.addIdentifier()
                .setSystem(FhirConstants.ANALYSIS_SYSTEM)
                .setValue(analysis.getId().toString());

        report.setStatus(status(analysis, signedReview));

        report.addCategory().addCoding(new Coding()
                .setSystem(FhirConstants.DIAGNOSTIC_SERVICE_SYSTEM)
                .setCode(serviceCode(analysis.getAnalysisType()))
                .setDisplay(serviceDisplay(analysis.getAnalysisType())));

        report.setCode(new CodeableConcept().setText(reportTitle(analysis.getAnalysisType())));
        report.setSubject(new Reference("Patient/" + analysis.getPatientId()));

        if (analysis.getCreatedAt() != null) {
            report.setEffective(new DateTimeType(Date.from(analysis.getCreatedAt())));
        }
        if (analysis.getProcessingCompletedAt() != null) {
            report.setIssued(Date.from(analysis.getProcessingCompletedAt()));
        }

        if (results != null && !results.isEmpty()) {
            report.setResult(results);
        }

        if (result != null) {
            if (result.getImpression() != null) {
                report.setConclusion(result.getImpression());
            }
            addConclusionCodes(report, result.getIcd10Codes());
            appendFindings(report, result);
        }

        // Every report says, in the resource itself, that it began as AI output. A consumer
        // reading only the FHIR — which is the whole point of exposing FHIR — would otherwise have
        // no way to know.
        report.addExtension()
                .setUrl(FhirConstants.BASE_NAMESPACE + "/ai-generated")
                .setValue(new BooleanType(true));

        if (signedReview != null && "SIGNED".equals(signedReview.getStatus())) {
            // Names the practitioner who took responsibility. A FINAL report with no interpreter
            // is a claim with nobody behind it.
            report.addResultsInterpreter(new Reference("Practitioner/" + signedReview.getSignedBy()));
            if (signedReview.getSignedAt() != null) {
                report.setIssued(Date.from(signedReview.getSignedAt()));
            }
            // The signed text supersedes the draft: it is what the clinician actually stands behind.
            if (signedReview.getFinalContent() != null && !signedReview.getFinalContent().isBlank()) {
                report.addExtension()
                        .setUrl(FhirConstants.BASE_NAMESPACE + "/clinician-signed")
                        .setValue(new BooleanType(true));
            }
        }

        return report;
    }

    /**
     * The status a consumer can rely on.
     *
     * <p>{@code PARTIAL} means preliminary: content exists but no responsible clinician has
     * verified it. That is the state of every unsigned draft, and it used to be the state of
     * everything, unconditionally.
     *
     * <p>Once a practitioner signs, {@code FINAL} becomes the truthful answer — and an amended
     * report becomes {@code AMENDED}, which is the status an EHR uses to prompt a re-read. This is
     * the payoff of the sign-off workflow at the interop boundary: a receiving system can now tell
     * a machine draft from a report a named human stands behind, which is exactly the distinction
     * the regulatory position rests on.
     */
    private DiagnosticReport.DiagnosticReportStatus status(AnalysisRequest analysis, ReportReview review) {
        if (analysis.getStatus() == AnalysisStatus.FAILED) {
            return DiagnosticReport.DiagnosticReportStatus.CANCELLED;
        }
        if (analysis.getStatus() != AnalysisStatus.COMPLETED) {
            return DiagnosticReport.DiagnosticReportStatus.REGISTERED;
        }
        if (review == null || !"SIGNED".equals(review.getStatus())) {
            return DiagnosticReport.DiagnosticReportStatus.PARTIAL;
        }
        return review.getAmendsReviewId() != null
                ? DiagnosticReport.DiagnosticReportStatus.AMENDED
                : DiagnosticReport.DiagnosticReportStatus.FINAL;
    }

    private void addConclusionCodes(DiagnosticReport report, List<String> icd10Codes) {
        if (icd10Codes == null || icd10Codes.isEmpty()) {
            return;
        }

        for (String rawCode : icd10Codes) {
            CodeValidation validation = icd10Validator.validate(rawCode);
            CodeableConcept concept = new CodeableConcept();

            if (validation.isValid()) {
                concept.addCoding(new Coding()
                        .setSystem(FhirConstants.ICD10_SYSTEM)
                        .setCode(validation.code())
                        .setDisplay(validation.display()));
                concept.setText(validation.display());
            } else {
                // Carried as text only. A consumer sees what the model said and does not receive a
                // coded assertion the system could not stand behind.
                concept.setText(rawCode + " (unverified ICD-10: " + validation.note() + ")");
                log.debug("Unverified ICD-10 code {} on report {}: {}",
                        rawCode, report.getId(), validation.note());
            }

            report.addConclusionCode(concept);
        }
    }

    /**
     * Structured findings become the report's presented form.
     *
     * <p>They have no natural R4 home — {@code DiagnosticReport.conclusion} is a single string and
     * imaging findings are a list with regions, severities and confidences. Rendering them into
     * {@code presentedForm} keeps them readable to any consumer rather than inventing extensions
     * a receiving system would ignore.
     */
    private void appendFindings(DiagnosticReport report, AnalysisResultDto result) {
        if (result.getFindings() == null || result.getFindings().isEmpty()) {
            return;
        }

        StringBuilder text = new StringBuilder("FINDINGS\n");
        for (AnalysisResultDto.Finding finding : result.getFindings()) {
            text.append("- ");
            if (finding.getRegion() != null) {
                text.append('[').append(finding.getRegion()).append("] ");
            }
            text.append(finding.getDescription() == null ? "" : finding.getDescription());
            if (finding.getSeverity() != null) {
                text.append(" (severity: ").append(finding.getSeverity());
                if (finding.getConfidence() != null) {
                    text.append(String.format(", model confidence: %.0f%%", finding.getConfidence() * 100));
                }
                text.append(')');
            }
            text.append('\n');
        }

        if (result.getRecommendations() != null && !result.getRecommendations().isEmpty()) {
            text.append("\nRECOMMENDATIONS\n");
            result.getRecommendations().forEach(r -> text.append("- ").append(r).append('\n'));
        }

        text.append("\nAI-generated draft. Requires review and sign-off by a licensed practitioner.\n");

        report.addPresentedForm(new Attachment()
                .setContentType("text/plain")
                .setData(text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .setTitle("Structured findings"));
    }

    private String serviceCode(AnalysisType type) {
        return switch (type) {
            case IMAGE_ANALYSIS -> "RAD";
            case BLOOD_REPORT -> "LAB";
            case COMBINED -> "OTH";
        };
    }

    private String serviceDisplay(AnalysisType type) {
        return switch (type) {
            case IMAGE_ANALYSIS -> "Radiology";
            case BLOOD_REPORT -> "Laboratory";
            case COMBINED -> "Other";
        };
    }

    private String reportTitle(AnalysisType type) {
        return switch (type) {
            case IMAGE_ANALYSIS -> "AI imaging analysis";
            case BLOOD_REPORT -> "AI blood report analysis";
            case COMBINED -> "AI combined imaging and laboratory analysis";
        };
    }
}
