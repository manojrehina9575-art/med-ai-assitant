package com.medai.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medai.BaseIntegrationTest;
import com.medai.auth.security.JwtService;
import com.medai.tenant.TenantContext;
import com.medai.user.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportControllerTextDraftTest extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("POST /api/reports/text-draft creates a draft review rather than matching /{reviewId}")
    void textDraftRouteIsRegisteredBeforeReviewIdRoute() throws Exception {
        SeededWorld seeded = seedWorld();
        String reportText = "FINDINGS:\nRight lower lobe opacity.\n\nIMPRESSION:\nPneumonia.";
        String request = objectMapper.writeValueAsString(new TextDraftPayload(
                seeded.patientId(), reportText, "XRAY", "Portable chest radiograph"));

        TenantContext.clear();

        MvcResult result = mockMvc.perform(post("/api/reports/text-draft")
                        .header("Authorization", "Bearer " + seeded.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.patientId").value(seeded.patientId().toString()))
                .andExpect(jsonPath("$.data.patientName").value("Asha Menon"))
                .andExpect(jsonPath("$.data.analysisType").value("IMAGE_ANALYSIS"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.draftContent").value(reportText))
                .andExpect(jsonPath("$.data.sections[0].section").value("FINDINGS"))
                .andExpect(jsonPath("$.data.sections[0].text").value("Right lower lobe opacity."))
                .andExpect(jsonPath("$.data.sections[1].section").value("IMPRESSION"))
                .andExpect(jsonPath("$.data.sections[1].text").value("Pneumonia."))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        String reviewId = data.get("id").asText();

        mockMvc.perform(get("/api/reports/worklist")
                        .header("Authorization", "Bearer " + seeded.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(reviewId))
                .andExpect(jsonPath("$.data.content[0].status").value("DRAFT"));
    }

    private SeededWorld seedWorld() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO tenants (id, name, subdomain, contact_email)
                VALUES (?, 'Text Draft Hospital', ?, 'text@example.test')
                """, tenantId, "text-" + tenantId.toString().substring(0, 8));
        TenantContext.setCurrentTenantId(tenantId);

        UUID doctorId = UUID.randomUUID();
        String email = "doc-" + doctorId + "@textdraft.test";
        jdbcTemplate.update("""
                INSERT INTO users (id, tenant_id, email, password_hash, first_name, last_name, role)
                VALUES (?, ?, ?, 'x', 'Mira', 'Patel', 'DOCTOR')
                """, doctorId, tenantId, email);

        UUID patientId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO patients (id, tenant_id, medical_record_number, first_name, last_name, date_of_birth, gender)
                VALUES (?, ?, ?, 'Asha', 'Menon', DATE '1979-04-12', 'FEMALE')
                """, patientId, tenantId, "MRN-" + patientId.toString().substring(0, 8));

        String token = jwtService.generateAccessToken(doctorId, tenantId, email, UserRole.DOCTOR.name());
        return new SeededWorld(tenantId, patientId, token);
    }

    private record SeededWorld(UUID tenantId, UUID patientId, String token) {
    }

    private record TextDraftPayload(UUID patientId, String reportText, String modality,
                                    String studyDescription) {
    }
}
