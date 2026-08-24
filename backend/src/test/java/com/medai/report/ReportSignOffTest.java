package com.medai.report;

import com.medai.BaseIntegrationTest;
import com.medai.analysis.entity.AnalysisRequest;
import com.medai.analysis.enums.AnalysisStatus;
import com.medai.analysis.enums.AnalysisType;
import com.medai.analysis.repository.AnalysisRequestRepository;
import com.medai.auth.security.UserPrincipal;
import com.medai.common.exception.BadRequestException;
import com.medai.report.dto.ReportDtos.*;
import com.medai.report.entity.ReportReview;
import com.medai.report.repository.ReportReviewRepository;
import com.medai.report.service.CriticalResultService;
import com.medai.report.service.ReportSignOffService;
import com.medai.tenant.TenantContext;
import com.medai.user.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The sign-off spine: the state machine that makes "the product drafts, a clinician decides" true
 * in the software rather than asserted in a document.
 */
class ReportSignOffTest extends BaseIntegrationTest {

    @Autowired private ReportSignOffService signOffService;
    @Autowired private CriticalResultService criticalResultService;
    @Autowired private ReportReviewRepository reviewRepository;
    @Autowired private AnalysisRequestRepository analysisRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private UUID tenantId;
    private UUID doctorId;
    private UUID patientId;

    private UserPrincipal doctor() {
        return new UserPrincipal(doctorId, tenantId, "doc@signoff.test", UserRole.DOCTOR.name());
    }

    private void seedWorld() {
        tenantId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO tenants (id, name, subdomain, contact_email)
                VALUES (?, 'SignOff Hospital', ?, 's@example.test')
                """, tenantId, "so-" + tenantId.toString().substring(0, 8));
        TenantContext.setCurrentTenantId(tenantId);

        doctorId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (id, tenant_id, email, password_hash, first_name, last_name, role)
                VALUES (?, ?, ?, 'x', 'Ravi', 'Sharma', 'DOCTOR')
                """, doctorId, tenantId, "doc-" + doctorId + "@signoff.test");

        patientId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO patients (id, tenant_id, medical_record_number, first_name, last_name, date_of_birth, gender)
                VALUES (?, ?, ?, 'Asha', 'Menon', DATE '1979-04-12', 'FEMALE')
                """, patientId, tenantId, "MRN-" + patientId.toString().substring(0, 8));
    }

    private AnalysisRequest seedAnalysis(String urgency, boolean abstained) {
        UUID fileId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO medical_files
                    (id, tenant_id, patient_id, uploaded_by, file_name, original_file_name,
                     file_type, mime_type, file_size_bytes, storage_path)
                VALUES (?, ?, ?, ?, 'f', 'f', 'XRAY', 'image/png', 1, 'p')
                """, fileId, tenantId, patientId, doctorId);

        AnalysisRequest analysis = AnalysisRequest.builder()
                .patientId(patientId).medicalFileId(fileId).requestedBy(doctorId)
                .analysisType(AnalysisType.IMAGE_ANALYSIS)
                .status(AnalysisStatus.COMPLETED)
                .urgency(urgency)
                .abstained(abstained)
                .abstentionReason(abstained ? "Image quality insufficient" : null)
                .result("{\"impression\":\"Right lower lobe consolidation.\"}")
                .retryCount(0).maxRetries(3)
                .build();
        analysis.setTenantId(tenantId);
        return analysisRepository.save(analysis);
    }

    @Nested
    @DisplayName("Draft, review, sign")
    class Lifecycle {

        @Test
        @DisplayName("A completed analysis opens a review, and reopening is idempotent")
        void openingIsIdempotent() {
            seedWorld();
            AnalysisRequest analysis = seedAnalysis("ROUTINE", false);

            ReportReview first = signOffService.openReview(tenantId, analysis.getId()).orElseThrow();
            ReportReview second = signOffService.openReview(tenantId, analysis.getId()).orElseThrow();

            assertThat(second.getId()).isEqualTo(first.getId());
            assertThat(first.getStatus()).isEqualTo("DRAFT");
            // Frozen at open time: the analysis can be retried, what the clinician saw must not move.
            assertThat(first.getDraftContent()).contains("consolidation");
        }

        /** Nothing to sign, and it would waste a radiologist's attention on the worklist. */
        @Test
        @DisplayName("An abstention opens no review")
        void abstentionOpensNoReview() {
            seedWorld();
            AnalysisRequest analysis = seedAnalysis("ROUTINE", true);

            assertThat(signOffService.openReview(tenantId, analysis.getId())).isEmpty();
        }

        @Test
        @DisplayName("Accepting signs the draft unchanged and attributes it")
        void acceptSigns() {
            seedWorld();
            AnalysisRequest analysis = seedAnalysis("ROUTINE", false);
            ReportReview review = signOffService.openReview(tenantId, analysis.getId()).orElseThrow();

            ReviewView signed = signOffService.sign(review.getId(),
                    new SignRequest("ACCEPTED", null, null), doctor());

            assertThat(signed.status()).isEqualTo("SIGNED");
            assertThat(signed.reviewAction()).isEqualTo("ACCEPTED");
            assertThat(signed.signedBy()).isEqualTo(doctorId);
            assertThat(signed.signedAt()).isNotNull();
            assertThat(signed.finalContent()).isEqualTo(signed.draftContent());
        }

        /** The most valuable record in the system: a real error beside its real correction. */
        @Test
        @DisplayName("Editing keeps both the draft and the correction")
        void editKeepsBoth() {
            seedWorld();
            AnalysisRequest analysis = seedAnalysis("ROUTINE", false);
            ReportReview review = signOffService.openReview(tenantId, analysis.getId()).orElseThrow();

            ReviewView signed = signOffService.sign(review.getId(),
                    new SignRequest("EDITED", "Left lower lobe consolidation, not right.", null), doctor());

            assertThat(signed.reviewAction()).isEqualTo("EDITED");
            assertThat(signed.draftContent()).contains("Right lower lobe");
            assertThat(signed.finalContent()).contains("Left lower lobe");
        }

        @Test
        @DisplayName("An edit without corrected content is refused")
        void editNeedsContent() {
            seedWorld();
            AnalysisRequest analysis = seedAnalysis("ROUTINE", false);
            ReportReview review = signOffService.openReview(tenantId, analysis.getId()).orElseThrow();

            assertThatThrownBy(() -> signOffService.sign(review.getId(),
                    new SignRequest("EDITED", "  ", null), doctor()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("corrected content");
        }

        @Test
        @DisplayName("A rejection without a reason is refused")
        void rejectNeedsReason() {
            seedWorld();
            AnalysisRequest analysis = seedAnalysis("ROUTINE", false);
            ReportReview review = signOffService.openReview(tenantId, analysis.getId()).orElseThrow();

            assertThatThrownBy(() -> signOffService.sign(review.getId(),
                    new SignRequest("REJECTED", null, null), doctor()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("reason");
        }

        @Test
        @DisplayName("A signed report cannot be signed again")
        void signedIsTerminal() {
            seedWorld();
            AnalysisRequest analysis = seedAnalysis("ROUTINE", false);
            ReportReview review = signOffService.openReview(tenantId, analysis.getId()).orElseThrow();
            signOffService.sign(review.getId(), new SignRequest("ACCEPTED", null, null), doctor());

            assertThatThrownBy(() -> signOffService.sign(review.getId(),
                    new SignRequest("ACCEPTED", null, null), doctor()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Amend it instead");
        }

        /**
         * A records request or a case review needs not just the final text but what was signed
         * first and when it changed.
         */
        @Test
        @DisplayName("An amendment supersedes without erasing")
        void amendmentSupersedes() {
            seedWorld();
            AnalysisRequest analysis = seedAnalysis("ROUTINE", false);
            ReportReview review = signOffService.openReview(tenantId, analysis.getId()).orElseThrow();
            ReviewView original = signOffService.sign(review.getId(),
                    new SignRequest("ACCEPTED", null, null), doctor());

            ReviewView amendment = signOffService.amend(original.id(),
                    "Corrected: left lower lobe.", doctor());

            assertThat(amendment.id()).isNotEqualTo(original.id());
            assertThat(amendment.amendsReviewId()).isEqualTo(original.id());
            assertThat(amendment.status()).isEqualTo("SIGNED");

            ReviewView superseded = signOffService.get(original.id());
            assertThat(superseded.status()).isEqualTo("AMENDED");
            assertThat(superseded.finalContent()).as("the original text is still readable").isNotNull();
        }

        @Test
        @DisplayName("The worklist shows oldest first and drops signed reports")
        void worklistOrdering() {
            seedWorld();
            AnalysisRequest older = seedAnalysis("ROUTINE", false);
            AnalysisRequest newer = seedAnalysis("ROUTINE", false);
            signOffService.openReview(tenantId, older.getId());
            ReportReview newerReview = signOffService.openReview(tenantId, newer.getId()).orElseThrow();

            assertThat(signOffService.worklist(0, 20).getContent())
                    .as("oldest first — a newest-first list starves the bottom of the queue")
                    .first()
                    .extracting(ReviewView::analysisId).isEqualTo(older.getId());

            signOffService.sign(newerReview.getId(), new SignRequest("ACCEPTED", null, null), doctor());

            assertThat(signOffService.worklist(0, 20).getContent())
                    .extracting(ReviewView::analysisId)
                    .containsExactly(older.getId());
        }
    }

    @Nested
    @DisplayName("Critical results")
    class CriticalResults {

        @Test
        @DisplayName("A critical finding raises an escalation; a routine one does not")
        void raisesOnlyForCritical() {
            seedWorld();
            AnalysisRequest critical = seedAnalysis("CRITICAL", false);
            AnalysisRequest routine = seedAnalysis("ROUTINE", false);

            assertThat(criticalResultService.raiseIfCritical(
                    tenantId, critical.getId(), "CRITICAL", "Tension pneumothorax")).isPresent();
            assertThat(criticalResultService.raiseIfCritical(
                    tenantId, routine.getId(), "ROUTINE", "Normal study")).isEmpty();
        }

        /** Re-running an analysis must not re-page the ward. */
        @Test
        @DisplayName("Raising twice for one analysis yields one escalation")
        void raisingIsIdempotent() {
            seedWorld();
            AnalysisRequest analysis = seedAnalysis("CRITICAL", false);

            var first = criticalResultService.raiseIfCritical(tenantId, analysis.getId(), "CRITICAL", "x");
            var second = criticalResultService.raiseIfCritical(tenantId, analysis.getId(), "CRITICAL", "x");

            assertThat(second.orElseThrow().getId()).isEqualTo(first.orElseThrow().getId());
            assertThat(criticalResultService.open()).hasSize(1);
        }

        @Test
        @DisplayName("Acknowledgement records who, when and what was done")
        void acknowledgementIsAttributed() {
            seedWorld();
            AnalysisRequest analysis = seedAnalysis("CRITICAL", false);
            var escalation = criticalResultService.raiseIfCritical(
                    tenantId, analysis.getId(), "CRITICAL", "Tension pneumothorax").orElseThrow();

            EscalationView acknowledged = criticalResultService.acknowledge(escalation.getId(),
                    new AcknowledgeRequest("Chest drain inserted, patient stable."), doctor());

            assertThat(acknowledged.status()).isEqualTo("ACKNOWLEDGED");
            assertThat(acknowledged.acknowledgedBy()).isEqualTo(doctorId);
            assertThat(acknowledged.actionTaken()).contains("Chest drain");
            assertThat(criticalResultService.open()).isEmpty();
        }

        /** The duty asks what happened to the patient, not that a button was pressed. */
        @Test
        @DisplayName("Acknowledgement without an action is refused")
        void acknowledgementNeedsAnAction() {
            seedWorld();
            AnalysisRequest analysis = seedAnalysis("CRITICAL", false);
            var escalation = criticalResultService.raiseIfCritical(
                    tenantId, analysis.getId(), "CRITICAL", "x").orElseThrow();

            assertThatThrownBy(() -> criticalResultService.acknowledge(escalation.getId(),
                    new AcknowledgeRequest("   "), doctor()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("what was done");
        }

        @Test
        @DisplayName("An unacknowledged escalation widens rather than repeating")
        void unacknowledgedWidens() {
            seedWorld();
            AnalysisRequest analysis = seedAnalysis("CRITICAL", false);
            var escalation = criticalResultService.raiseIfCritical(
                    tenantId, analysis.getId(), "CRITICAL", "x").orElseThrow();

            assertThat(escalation.getEscalationLevel()).isZero();

            criticalResultService.widen(escalation);
            assertThat(criticalResultService.open())
                    .first()
                    .extracting(EscalationView::escalationLevel)
                    .isEqualTo((short) 1);
        }

        @Test
        @DisplayName("The overdue sweep finds only escalations past their deadline")
        void sweepFindsOverdue() {
            seedWorld();
            AnalysisRequest analysis = seedAnalysis("CRITICAL", false);
            var escalation = criticalResultService.raiseIfCritical(
                    tenantId, analysis.getId(), "CRITICAL", "x").orElseThrow();

            assertThat(criticalResultService.findOverdue(Instant.now().minus(1, ChronoUnit.HOURS)))
                    .as("just raised, so not yet overdue")
                    .noneMatch(e -> e.getId().equals(escalation.getId()));

            assertThat(criticalResultService.findOverdue(Instant.now().plusSeconds(1)))
                    .anyMatch(e -> e.getId().equals(escalation.getId()));
        }
    }

    @Nested
    @DisplayName("Tenant isolation")
    class Isolation {

        @Test
        @DisplayName("Reviews and escalations are scoped to their tenant")
        void scopedToTenant() {
            seedWorld();
            AnalysisRequest analysis = seedAnalysis("CRITICAL", false);
            signOffService.openReview(tenantId, analysis.getId());
            criticalResultService.raiseIfCritical(tenantId, analysis.getId(), "CRITICAL", "x");

            UUID otherTenant = UUID.randomUUID();
            jdbcTemplate.update("""
                    INSERT INTO tenants (id, name, subdomain, contact_email)
                    VALUES (?, 'Other', ?, 'o@example.test')
                    """, otherTenant, "other-" + otherTenant.toString().substring(0, 8));
            TenantContext.setCurrentTenantId(otherTenant);

            assertThat(signOffService.worklist(0, 20).getContent()).isEmpty();
            assertThat(criticalResultService.open()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Worklist summary")
    class Summary {

        @Test
        @DisplayName("Counts reflect the review outcomes a pilot is measured on")
        void summaryCounts() {
            seedWorld();
            AnalysisRequest a = seedAnalysis("ROUTINE", false);
            AnalysisRequest b = seedAnalysis("ROUTINE", false);
            AnalysisRequest c = seedAnalysis("ROUTINE", false);

            signOffService.sign(signOffService.openReview(tenantId, a.getId()).orElseThrow().getId(),
                    new SignRequest("ACCEPTED", null, null), doctor());
            signOffService.sign(signOffService.openReview(tenantId, b.getId()).orElseThrow().getId(),
                    new SignRequest("EDITED", "corrected", null), doctor());
            signOffService.openReview(tenantId, c.getId());

            WorklistSummary summary = signOffService.summary(0);

            assertThat(summary.acceptedAllTime()).isEqualTo(1);
            assertThat(summary.editedAllTime()).isEqualTo(1);
            assertThat(summary.awaitingReview()).isEqualTo(1);
            assertThat(summary.signedToday()).isEqualTo(2);
        }
    }
}
