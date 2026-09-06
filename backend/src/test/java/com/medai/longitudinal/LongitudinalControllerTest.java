package com.medai.longitudinal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medai.BaseIntegrationTest;
import com.medai.analysis.entity.AnalysisRequest;
import com.medai.analysis.enums.AnalysisStatus;
import com.medai.analysis.enums.AnalysisType;
import com.medai.analysis.repository.AnalysisRequestRepository;
import com.medai.auth.security.JwtService;
import com.medai.report.entity.ReportReview;
import com.medai.report.repository.ReportReviewRepository;
import com.medai.tenant.TenantContext;
import com.medai.user.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LongitudinalControllerTest extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AnalysisRequestRepository analysisRepository;
    @Autowired private ReportReviewRepository reviewRepository;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("longitudinal endpoint compares explicit same-tenant current and prior reports")
    void comparesExplicitCurrentAndPriorReports() throws Exception {
        SeededTenant tenant = seedTenant();
        UUID patientId = seedPatient(tenant.tenantId(), tenant.doctorId());
        SeededReview prior = seedReview(tenant.tenantId(), tenant.doctorId(), patientId,
                "5 mm left upper lobe pulmonary nodule.",
                "SIGNED",
                Instant.now().minusSeconds(86_400));
        SeededReview current = seedReview(tenant.tenantId(), tenant.doctorId(), patientId,
                "8 mm left upper lobe pulmonary nodule.",
                "DRAFT",
                null);

        TenantContext.clear();

        mockMvc.perform(post("/api/reports/" + current.reviewId() + "/longitudinal/" + prior.reviewId())
                        .header("Authorization", "Bearer " + tenant.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.currentReportId").value(current.reviewId().toString()))
                .andExpect(jsonPath("$.data.priorReportId").value(prior.reviewId().toString()))
                .andExpect(jsonPath("$.data.comparisons[0].changeType").value("INCREASED"))
                .andExpect(jsonPath("$.data.comparisons[0].priorMeasurementMm").value(5))
                .andExpect(jsonPath("$.data.comparisons[0].currentMeasurementMm").value(8))
                .andExpect(jsonPath("$.data.comparisons[0].measurementDeltaMm").value(3))
                .andExpect(jsonPath("$.data.comparisons[0].priorFinding.findingType").value("NODULE"))
                .andExpect(jsonPath("$.data.comparisons[0].priorFinding.anatomy").value("LUNG"))
                .andExpect(jsonPath("$.data.comparisons[0].priorFinding.side").value("LEFT"))
                .andExpect(jsonPath("$.data.comparisons[0].priorFinding.region").value("UPPER"))
                .andExpect(jsonPath("$.data.comparisons[0].currentFinding.findingType").value("NODULE"))
                .andExpect(jsonPath("$.data.comparisons[0].currentFinding.anatomy").value("LUNG"))
                .andExpect(jsonPath("$.data.comparisons[0].currentFinding.side").value("LEFT"))
                .andExpect(jsonPath("$.data.comparisons[0].currentFinding.region").value("UPPER"))
                .andExpect(jsonPath("$.data.comparisons[0].explanation", containsString("Potential interval increase")))
                .andExpect(jsonPath("$.data.comparisons[0].priorAnatomyTarget.system").value("RESPIRATORY"))
                .andExpect(jsonPath("$.data.comparisons[0].priorAnatomyTarget.structureCode").value("LUNG"))
                .andExpect(jsonPath("$.data.comparisons[0].priorAnatomyTarget.side").value("LEFT"))
                .andExpect(jsonPath("$.data.comparisons[0].priorAnatomyTarget.region").value("UPPER"))
                .andExpect(jsonPath("$.data.comparisons[0].priorAnatomyTarget.displayName").value("Left upper lung"))
                .andExpect(jsonPath("$.data.comparisons[0].priorAnatomyTarget.viewerKey")
                        .value("respiratory.lung.left"))
                .andExpect(jsonPath("$.data.comparisons[0].currentAnatomyTarget.structureCode").value("LUNG"))
                .andExpect(jsonPath("$.data.comparisons[0].currentAnatomyTarget.viewerKey")
                        .value("respiratory.lung.left"))
                .andExpect(jsonPath("$.data.summary.increasedFindings").value(1));
    }

    @Test
    @DisplayName("longitudinal endpoint exposes a skeletal viewer key and omits unmapped targets")
    void exposesSkeletalAnatomyTargetsAndOmitsUnmappedOnes() throws Exception {
        SeededTenant tenant = seedTenant();
        UUID patientId = seedPatient(tenant.tenantId(), tenant.doctorId());
        SeededReview prior = seedReview(tenant.tenantId(), tenant.doctorId(), patientId,
                "Nondisplaced fracture of the proximal right humerus. 5 mm right frontal lobe lesion.",
                "SIGNED",
                Instant.now().minusSeconds(86_400));
        SeededReview current = seedReview(tenant.tenantId(), tenant.doctorId(), patientId,
                "Healing fracture of the proximal right humerus. 5 mm right frontal lobe lesion.",
                "DRAFT",
                null);

        TenantContext.clear();

        mockMvc.perform(post("/api/reports/" + current.reviewId() + "/longitudinal/" + prior.reviewId())
                        .header("Authorization", "Bearer " + tenant.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                // Matched comparisons come first, so the humerus pair is index 0.
                .andExpect(jsonPath("$.data.comparisons[0].changeType").value("UNCHANGED"))
                .andExpect(jsonPath("$.data.comparisons[0].currentAnatomyTarget.system").value("SKELETAL"))
                .andExpect(jsonPath("$.data.comparisons[0].currentAnatomyTarget.structureCode").value("HUMERUS"))
                .andExpect(jsonPath("$.data.comparisons[0].currentAnatomyTarget.side").value("RIGHT"))
                .andExpect(jsonPath("$.data.comparisons[0].currentAnatomyTarget.region").value("PROXIMAL"))
                .andExpect(jsonPath("$.data.comparisons[0].currentAnatomyTarget.viewerKey")
                        .value("skeleton.humerus.right"))
                .andExpect(jsonPath("$.data.comparisons[0].priorAnatomyTarget.viewerKey")
                        .value("skeleton.humerus.right"))
                // The uncatalogued lesion still produces comparisons; they carry no anatomy target.
                .andExpect(jsonPath("$.data.comparisons[1].changeType").value("NEW"))
                .andExpect(jsonPath("$.data.comparisons[1].currentFinding.findingType").value("LESION"))
                .andExpect(jsonPath("$.data.comparisons[1].currentAnatomyTarget").doesNotExist())
                .andExpect(jsonPath("$.data.comparisons[1].priorAnatomyTarget").doesNotExist())
                .andExpect(jsonPath("$.data.comparisons[2].changeType").value("INDETERMINATE"))
                .andExpect(jsonPath("$.data.comparisons[2].priorAnatomyTarget").doesNotExist());
    }

    @Test
    @DisplayName("longitudinal endpoint rejects cross-tenant reports")
    void rejectsCrossTenantReports() throws Exception {
        SeededTenant tenantA = seedTenant();
        UUID patientId = seedPatient(tenantA.tenantId(), tenantA.doctorId());
        SeededReview prior = seedReview(tenantA.tenantId(), tenantA.doctorId(), patientId,
                "5 mm left pulmonary nodule.",
                "SIGNED",
                Instant.now().minusSeconds(86_400));
        SeededReview current = seedReview(tenantA.tenantId(), tenantA.doctorId(), patientId,
                "8 mm left pulmonary nodule.",
                "DRAFT",
                null);
        SeededTenant tenantB = seedTenant();

        TenantContext.clear();

        mockMvc.perform(post("/api/reports/" + current.reviewId() + "/longitudinal/" + prior.reviewId())
                        .header("Authorization", "Bearer " + tenantB.token()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("longitudinal endpoint returns not found for unknown report")
    void returnsNotFoundForUnknownReport() throws Exception {
        SeededTenant tenant = seedTenant();
        UUID patientId = seedPatient(tenant.tenantId(), tenant.doctorId());
        SeededReview prior = seedReview(tenant.tenantId(), tenant.doctorId(), patientId,
                "5 mm left pulmonary nodule.",
                "SIGNED",
                Instant.now().minusSeconds(86_400));

        TenantContext.clear();

        mockMvc.perform(post("/api/reports/" + UUID.randomUUID() + "/longitudinal/" + prior.reviewId())
                        .header("Authorization", "Bearer " + tenant.token()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("longitudinal endpoint rejects prior report that is not earlier")
    void rejectsPriorThatIsNotEarlierThanCurrent() throws Exception {
        SeededTenant tenant = seedTenant();
        UUID patientId = seedPatient(tenant.tenantId(), tenant.doctorId());
        SeededReview prior = seedReview(tenant.tenantId(), tenant.doctorId(), patientId,
                "5 mm left pulmonary nodule.",
                "SIGNED",
                Instant.now().plusSeconds(86_400));
        SeededReview current = seedReview(tenant.tenantId(), tenant.doctorId(), patientId,
                "8 mm left pulmonary nodule.",
                "DRAFT",
                null);

        TenantContext.clear();

        mockMvc.perform(post("/api/reports/" + current.reviewId() + "/longitudinal/" + prior.reviewId())
                        .header("Authorization", "Bearer " + tenant.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("earlier")));
    }

    @Test
    @DisplayName("longitudinal endpoint rejects reports from different patients")
    void rejectsDifferentPatients() throws Exception {
        SeededTenant tenant = seedTenant();
        UUID priorPatientId = seedPatient(tenant.tenantId(), tenant.doctorId());
        UUID currentPatientId = seedPatient(tenant.tenantId(), tenant.doctorId());
        SeededReview prior = seedReview(tenant.tenantId(), tenant.doctorId(), priorPatientId,
                "5 mm left pulmonary nodule.",
                "SIGNED",
                Instant.now().minusSeconds(86_400));
        SeededReview current = seedReview(tenant.tenantId(), tenant.doctorId(), currentPatientId,
                "8 mm left pulmonary nodule.",
                "DRAFT",
                null);

        TenantContext.clear();

        mockMvc.perform(post("/api/reports/" + current.reviewId() + "/longitudinal/" + prior.reviewId())
                        .header("Authorization", "Bearer " + tenant.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("same patient")));
    }

    private SeededTenant seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO tenants (id, name, subdomain, contact_email)
                VALUES (?, 'Longitudinal Hospital', ?, 'longitudinal@example.test')
                """, tenantId, "long-" + tenantId.toString().substring(0, 8));
        TenantContext.setCurrentTenantId(tenantId);

        UUID doctorId = UUID.randomUUID();
        String email = "doc-" + doctorId + "@longitudinal.test";
        jdbcTemplate.update("""
                INSERT INTO users (id, tenant_id, email, password_hash, first_name, last_name, role)
                VALUES (?, ?, ?, 'x', 'Asha', 'Rao', 'DOCTOR')
                """, doctorId, tenantId, email);

        String token = jwtService.generateAccessToken(doctorId, tenantId, email, UserRole.DOCTOR.name());
        return new SeededTenant(tenantId, doctorId, token);
    }

    private UUID seedPatient(UUID tenantId, UUID doctorId) {
        UUID patientId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO patients (id, tenant_id, medical_record_number, first_name, last_name, date_of_birth, gender)
                VALUES (?, ?, ?, 'Priya', 'Nair', DATE '1978-04-20', 'FEMALE')
                """, patientId, tenantId, "LONG-" + patientId.toString().substring(0, 8));
        return patientId;
    }

    private SeededReview seedReview(
            UUID tenantId,
            UUID doctorId,
            UUID patientId,
            String reportText,
            String status,
            Instant signedAt
    ) throws JsonProcessingException {
        TenantContext.setCurrentTenantId(tenantId);
        UUID fileId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO medical_files
                    (id, tenant_id, patient_id, uploaded_by, file_name, original_file_name,
                     file_type, mime_type, file_size_bytes, storage_path)
                VALUES (?, ?, ?, ?, 'long-study', 'long-study.dcm', 'CT_SCAN', 'application/dicom', 1, 'long-study.dcm')
                """, fileId, tenantId, patientId, doctorId);

        String reportContent = objectMapper.writeValueAsString(Map.of(
                "findings", List.of(Map.of("description", reportText))));

        AnalysisRequest analysis = AnalysisRequest.builder()
                .patientId(patientId)
                .medicalFileId(fileId)
                .requestedBy(doctorId)
                .analysisType(AnalysisType.IMAGE_ANALYSIS)
                .status(AnalysisStatus.COMPLETED)
                .result(reportContent)
                .retryCount(0)
                .maxRetries(3)
                .build();
        analysis.setTenantId(tenantId);
        analysis = analysisRepository.saveAndFlush(analysis);

        ReportReview.ReportReviewBuilder reviewBuilder = ReportReview.builder()
                .tenantId(tenantId)
                .analysisId(analysis.getId())
                .patientId(patientId)
                .status(status)
                .draftContent(reportContent);
        if ("SIGNED".equals(status)) {
            reviewBuilder
                    .finalContent(reportContent)
                    .reviewAction("ACCEPTED")
                    .signedBy(doctorId)
                    .signedAt(signedAt);
        }

        ReportReview review = reviewRepository.saveAndFlush(reviewBuilder.build());
        return new SeededReview(review.getId());
    }

    private record SeededTenant(UUID tenantId, UUID doctorId, String token) {
    }

    private record SeededReview(UUID reviewId) {
    }
}
