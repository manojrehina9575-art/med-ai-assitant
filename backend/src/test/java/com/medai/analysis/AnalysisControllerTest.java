package com.medai.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medai.BaseIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AnalysisController endpoints.
 * Validates analysis request submission, retrieval, file previewing, and security.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AnalysisControllerTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String tenantToken;
    private static String patientId;
    private static String fileId;
    private static String analysisId;

    @Test
    @Order(1)
    @DisplayName("Setup: Register hospital and create patient")
    void setupHospitalAndPatient() throws Exception {
        // 1. Register hospital
        String registerReq = """
                {
                    "hospitalName": "Radiology Center",
                    "subdomain": "radiology-center",
                    "contactEmail": "contact@radiology.com",
                    "adminEmail": "doctor@radiology.com",
                    "adminPassword": "DocPassword123!",
                    "adminFirstName": "Sarah",
                    "adminLastName": "Connor"
                }
                """;

        MvcResult authResult = mockMvc.perform(post("/api/auth/register-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerReq))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode authData = objectMapper.readTree(authResult.getResponse().getContentAsString()).get("data");
        tenantToken = authData.get("accessToken").asText();

        // 2. Create patient
        String patientReq = """
                {
                    "medicalRecordNumber": "RAD-1001",
                    "firstName": "Robert",
                    "lastName": "Pattinson",
                    "dateOfBirth": "1986-05-13",
                    "gender": "MALE",
                    "bloodGroup": "A+"
                }
                """;

        MvcResult patientResult = mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tenantToken)
                        .content(patientReq))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode patientData = objectMapper.readTree(patientResult.getResponse().getContentAsString()).get("data");
        patientId = patientData.get("id").asText();
    }

    @Test
    @Order(2)
    @DisplayName("Setup: Upload medical X-ray image")
    void uploadMedicalImage() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "chest_xray.png",
                "image/png",
                "fake-png-binary-content-for-xray".getBytes()
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/patients/" + patientId + "/files")
                        .file(file)
                        .param("fileType", "XRAY")
                        .param("description", "PA Chest X-Ray")
                        .header("Authorization", "Bearer " + tenantToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.originalFileName").value("chest_xray.png"))
                .andReturn();

        JsonNode fileData = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("data");
        fileId = fileData.get("id").asText();
    }

    @Test
    @Order(3)
    @DisplayName("View uploaded medical image inline")
    void viewMedicalImageInline() throws Exception {
        mockMvc.perform(get("/api/patients/" + patientId + "/files/" + fileId + "/view")
                        .header("Authorization", "Bearer " + tenantToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "inline; filename=\"chest_xray.png\""))
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }

    @Test
    @Order(4)
    @DisplayName("Submit medical image analysis request")
    void requestImageAnalysis() throws Exception {
        String analysisReq = String.format("""
                {
                    "patientId": "%s",
                    "medicalFileId": "%s",
                    "clinicalNotes": "Cough and mild dyspnea for 3 days."
                }
                """, patientId, fileId);

        MvcResult result = mockMvc.perform(post("/api/analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tenantToken)
                        .content(analysisReq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.patientId").value(patientId))
                .andExpect(jsonPath("$.data.medicalFileId").value(fileId))
                .andExpect(jsonPath("$.data.analysisType").value("IMAGE_ANALYSIS"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();

        JsonNode analysisData = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        analysisId = analysisData.get("id").asText();
    }

    @Test
    @Order(5)
    @DisplayName("Get analysis request by ID")
    void getAnalysisById() throws Exception {
        mockMvc.perform(get("/api/analysis/" + analysisId)
                        .header("Authorization", "Bearer " + tenantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(analysisId))
                .andExpect(jsonPath("$.data.patientId").value(patientId));
    }

    @Test
    @Order(6)
    @DisplayName("List analyses by patient ID")
    void listAnalysesByPatient() throws Exception {
        mockMvc.perform(get("/api/analysis/patient/" + patientId)
                        .header("Authorization", "Bearer " + tenantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(analysisId));
    }

    @Test
    @Order(7)
    @DisplayName("Reject duplicate in-progress analysis for same file")
    void rejectDuplicateInProgressAnalysis() throws Exception {
        String duplicateReq = String.format("""
                {
                    "patientId": "%s",
                    "medicalFileId": "%s",
                    "clinicalNotes": "Duplicate attempt"
                }
                """, patientId, fileId);

        mockMvc.perform(post("/api/analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tenantToken)
                        .content(duplicateReq))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(8)
    @DisplayName("Unauthenticated analysis request rejected with 401")
    void unauthenticatedRequestRejected() throws Exception {
        mockMvc.perform(get("/api/analysis/" + analysisId))
                .andExpect(status().isUnauthorized());
    }
}
