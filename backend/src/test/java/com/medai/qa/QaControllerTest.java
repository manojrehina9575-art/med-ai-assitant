package com.medai.qa;

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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QaControllerTest extends BaseIntegrationTest {

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
    @DisplayName("report QA endpoint returns laterality conflict evidence")
    void evaluatesReportForLateralityConflict() throws Exception {
        SeededReview seeded = seedReview(
                "Comminuted fracture of the proximal right humerus.",
                "Comminuted fracture of the proximal left humerus.");

        TenantContext.clear();

        mockMvc.perform(post("/api/reports/" + seeded.reviewId() + "/qa")
                        .header("Authorization", "Bearer " + seeded.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reportId").value(seeded.reviewId().toString()))
                .andExpect(jsonPath("$.data.status").value("REVIEW_RECOMMENDED"))
                .andExpect(jsonPath("$.data.issueCount").value(1))
                .andExpect(jsonPath("$.data.issues[0].type").value("LATERALITY_CONFLICT"))
                .andExpect(jsonPath("$.data.issues[0].severity").value("HIGH"))
                .andExpect(jsonPath("$.data.issues[0].sectionA").value("FINDINGS"))
                .andExpect(jsonPath("$.data.issues[0].sectionB").value("IMPRESSION"))
                .andExpect(jsonPath("$.data.issues[0].sideA").value("RIGHT"))
                .andExpect(jsonPath("$.data.issues[0].sideB").value("LEFT"))
                .andExpect(jsonPath("$.data.issues[0].findingText", containsString("right humerus")))
                .andExpect(jsonPath("$.data.issues[0].impressionText", containsString("left humerus")))
                .andExpect(jsonPath("$.data.issues[0].anatomyCode").value("HUMERUS"))
                .andExpect(jsonPath("$.data.issues[0].region").value("PROXIMAL"))
                .andExpect(jsonPath("$.data.issues[0].detector").value("LateralityRule"))
                .andExpect(jsonPath("$.data.issues[0].detectorVersion").value("1.0.0"))
                .andExpect(jsonPath("$.data.issues[0].evidence", hasSize(2)))
                .andExpect(jsonPath("$.data.issues[0].evidence[0].sourceSection").value("FINDINGS"))
                .andExpect(jsonPath("$.data.issues[0].evidence[0].findingType").value("FRACTURE"))
                .andExpect(jsonPath("$.data.issues[0].evidence[0].anatomy").value("HUMERUS"))
                .andExpect(jsonPath("$.data.issues[0].evidence[0].anatomyText").value("humerus"))
                .andExpect(jsonPath("$.data.issues[0].evidence[0].side").value("RIGHT"))
                .andExpect(jsonPath("$.data.issues[0].evidence[0].region").value("PROXIMAL"))
                .andExpect(jsonPath("$.data.issues[0].evidence[0].status").value("PRESENT"))
                .andExpect(jsonPath("$.data.issues[0].evidence[0].certainty").value("ASSERTED"))
                .andExpect(jsonPath("$.data.issues[0].evidence[0].sourceText", containsString("right humerus")))
                .andExpect(jsonPath("$.data.issues[0].evidence[0].anatomyTarget.system").value("SKELETAL"))
                .andExpect(jsonPath("$.data.issues[0].evidence[0].anatomyTarget.structureCode").value("HUMERUS"))
                .andExpect(jsonPath("$.data.issues[0].evidence[0].anatomyTarget.side").value("RIGHT"))
                .andExpect(jsonPath("$.data.issues[0].evidence[0].anatomyTarget.region").value("PROXIMAL"))
                .andExpect(jsonPath("$.data.issues[0].evidence[0].anatomyTarget.displayName")
                        .value("Right proximal humerus"))
                .andExpect(jsonPath("$.data.issues[0].evidence[0].anatomyTarget.viewerKey")
                        .value("skeleton.humerus.right"))
                .andExpect(jsonPath("$.data.issues[0].evidence[0].anatomyTarget.sourceAnatomy").value("HUMERUS"))
                .andExpect(jsonPath("$.data.issues[0].evidence[1].sourceSection").value("IMPRESSION"))
                .andExpect(jsonPath("$.data.issues[0].evidence[1].findingType").value("FRACTURE"))
                .andExpect(jsonPath("$.data.issues[0].evidence[1].anatomy").value("HUMERUS"))
                .andExpect(jsonPath("$.data.issues[0].evidence[1].anatomyText").value("humerus"))
                .andExpect(jsonPath("$.data.issues[0].evidence[1].side").value("LEFT"))
                .andExpect(jsonPath("$.data.issues[0].evidence[1].region").value("PROXIMAL"))
                .andExpect(jsonPath("$.data.issues[0].evidence[1].status").value("PRESENT"))
                .andExpect(jsonPath("$.data.issues[0].evidence[1].certainty").value("ASSERTED"))
                .andExpect(jsonPath("$.data.issues[0].evidence[1].sourceText", containsString("left humerus")))
                .andExpect(jsonPath("$.data.issues[0].evidence[1].anatomyTarget.system").value("SKELETAL"))
                .andExpect(jsonPath("$.data.issues[0].evidence[1].anatomyTarget.structureCode").value("HUMERUS"))
                .andExpect(jsonPath("$.data.issues[0].evidence[1].anatomyTarget.side").value("LEFT"))
                .andExpect(jsonPath("$.data.issues[0].evidence[1].anatomyTarget.region").value("PROXIMAL"))
                .andExpect(jsonPath("$.data.issues[0].evidence[1].anatomyTarget.displayName")
                        .value("Left proximal humerus"))
                .andExpect(jsonPath("$.data.issues[0].evidence[1].anatomyTarget.viewerKey")
                        .value("skeleton.humerus.left"));
    }

    @Test
    @DisplayName("report QA endpoint returns no issues for consistent laterality")
    void evaluatesReportWithNoIssues() throws Exception {
        SeededReview seeded = seedReview(
                "Moderate right knee effusion.",
                "Moderate right knee effusion.");

        TenantContext.clear();

        mockMvc.perform(post("/api/reports/" + seeded.reviewId() + "/qa")
                        .header("Authorization", "Bearer " + seeded.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("NO_ISSUES"))
                .andExpect(jsonPath("$.data.issueCount").value(0))
                .andExpect(jsonPath("$.data.issues").isEmpty());
    }

    @Test
    @DisplayName("report QA evidence carries the brain target for a brain conflict")
    void enrichesBrainConflictWithAnatomyTarget() throws Exception {
        SeededReview seeded = seedReview(
                "Right brain aneurysm.",
                "Left brain aneurysm.");

        TenantContext.clear();

        mockMvc.perform(post("/api/reports/" + seeded.reviewId() + "/qa")
                        .header("Authorization", "Bearer " + seeded.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REVIEW_RECOMMENDED"))
                .andExpect(jsonPath("$.data.issueCount").value(1))
                .andExpect(jsonPath("$.data.issues[0].type").value("LATERALITY_CONFLICT"))
                .andExpect(jsonPath("$.data.issues[0].severity").value("HIGH"))
                .andExpect(jsonPath("$.data.issues[0].findingText", containsString("Right brain aneurysm")))
                .andExpect(jsonPath("$.data.issues[0].impressionText", containsString("Left brain aneurysm")))
                .andExpect(jsonPath("$.data.issues[0].sideA").value("RIGHT"))
                .andExpect(jsonPath("$.data.issues[0].sideB").value("LEFT"))
                .andExpect(jsonPath("$.data.issues[0].anatomyCode").value("BRAIN"))
                .andExpect(jsonPath("$.data.issues[0].evidence", hasSize(2)))
                .andExpect(jsonPath("$.data.issues[0].evidence[0].findingType").value("ANEURYSM"))
                .andExpect(jsonPath("$.data.issues[0].evidence[0].anatomy").value("BRAIN"))
                .andExpect(jsonPath("$.data.issues[0].evidence[0].anatomyTarget.system").value("NERVOUS"))
                .andExpect(jsonPath("$.data.issues[0].evidence[0].anatomyTarget.structureCode").value("BRAIN"))
                .andExpect(jsonPath("$.data.issues[0].evidence[0].anatomyTarget.viewerKey")
                        .value("nervous.brain"))
                .andExpect(jsonPath("$.data.issues[0].evidence[1].findingType").value("ANEURYSM"))
                .andExpect(jsonPath("$.data.issues[0].evidence[1].anatomyTarget.viewerKey")
                        .value("nervous.brain"));
    }

    @Test
    @DisplayName("report QA evidence carries side-specific anatomy targets for a knee conflict")
    void enrichesKneeConflictWithAnatomyTargets() throws Exception {
        SeededReview seeded = seedReview(
                "Moderate right knee effusion.",
                "Moderate left knee effusion.");

        TenantContext.clear();

        mockMvc.perform(post("/api/reports/" + seeded.reviewId() + "/qa")
                        .header("Authorization", "Bearer " + seeded.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVIEW_RECOMMENDED"))
                .andExpect(jsonPath("$.data.issues[0].type").value("LATERALITY_CONFLICT"))
                .andExpect(jsonPath("$.data.issues[0].severity").value("HIGH"))
                .andExpect(jsonPath("$.data.issues[0].evidence", hasSize(2)))
                .andExpect(jsonPath("$.data.issues[0].evidence[0].side").value("RIGHT"))
                .andExpect(jsonPath("$.data.issues[0].evidence[0].anatomyTarget.structureCode").value("KNEE"))
                .andExpect(jsonPath("$.data.issues[0].evidence[0].anatomyTarget.system").value("SKELETAL"))
                .andExpect(jsonPath("$.data.issues[0].evidence[0].anatomyTarget.viewerKey")
                        .value("skeleton.knee.right"))
                .andExpect(jsonPath("$.data.issues[0].evidence[0].anatomyTarget.displayName").value("Right knee"))
                .andExpect(jsonPath("$.data.issues[0].evidence[1].side").value("LEFT"))
                .andExpect(jsonPath("$.data.issues[0].evidence[1].anatomyTarget.structureCode").value("KNEE"))
                .andExpect(jsonPath("$.data.issues[0].evidence[1].anatomyTarget.viewerKey")
                        .value("skeleton.knee.left"))
                .andExpect(jsonPath("$.data.issues[0].evidence[1].anatomyTarget.displayName").value("Left knee"));
    }

    @Test
    @DisplayName("report QA endpoint is tenant-scoped")
    void qaEndpointIsTenantScoped() throws Exception {
        SeededReview tenantA = seedReview(
                "Right proximal humerus fracture.",
                "Left proximal humerus fracture.");
        SeededReview tenantB = seedReview(
                "Right knee effusion.",
                "Right knee effusion.");

        TenantContext.clear();

        mockMvc.perform(post("/api/reports/" + tenantA.reviewId() + "/qa")
                        .header("Authorization", "Bearer " + tenantB.token()))
                .andExpect(status().isNotFound());
    }

    private SeededReview seedReview(String findings, String impression) throws JsonProcessingException {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO tenants (id, name, subdomain, contact_email)
                VALUES (?, 'QA Hospital', ?, 'qa@example.test')
                """, tenantId, "qa-" + tenantId.toString().substring(0, 8));
        TenantContext.setCurrentTenantId(tenantId);

        UUID doctorId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (id, tenant_id, email, password_hash, first_name, last_name, role)
                VALUES (?, ?, ?, 'x', 'Mira', 'Patel', 'DOCTOR')
                """, doctorId, tenantId, "doc-" + doctorId + "@qa.test");

        UUID patientId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO patients (id, tenant_id, medical_record_number, first_name, last_name, date_of_birth, gender)
                VALUES (?, ?, ?, 'John', 'Doe', DATE '1974-01-16', 'MALE')
                """, patientId, tenantId, "QA-" + patientId.toString().substring(0, 8));

        UUID fileId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO medical_files
                    (id, tenant_id, patient_id, uploaded_by, file_name, original_file_name,
                     file_type, mime_type, file_size_bytes, storage_path)
                VALUES (?, ?, ?, ?, 'qa-study', 'qa-study.dcm', 'CT_SCAN', 'application/dicom', 1, 'qa-study.dcm')
                """, fileId, tenantId, patientId, doctorId);

        String reportContent = objectMapper.writeValueAsString(Map.of(
                "findings", List.of(Map.of("description", findings)),
                "impression", impression));

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

        ReportReview review = ReportReview.builder()
                .tenantId(tenantId)
                .analysisId(analysis.getId())
                .patientId(patientId)
                .status("DRAFT")
                .draftContent(reportContent)
                .build();
        review = reviewRepository.saveAndFlush(review);

        String token = jwtService.generateAccessToken(
                doctorId,
                tenantId,
                "doc-" + doctorId + "@qa.test",
                UserRole.DOCTOR.name());

        return new SeededReview(review.getId(), token);
    }

    private record SeededReview(UUID reviewId, String token) {
    }
}
