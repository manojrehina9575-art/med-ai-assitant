package com.medai.patient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medai.BaseIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for patient CRUD and tenant isolation.
 * Verifies that tenants cannot access each other's patients.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PatientControllerTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Tenant A
    private static String tenantAId;
    private static String tenantAToken;
    private static String patientAId;

    // Tenant B
    private static String tenantBId;
    private static String tenantBToken;

    @Test
    @Order(1)
    @DisplayName("Setup: Register Tenant A")
    void setupTenantA() throws Exception {
        String request = """
                {
                    "hospitalName": "Hospital Alpha",
                    "subdomain": "hospital-alpha",
                    "contactEmail": "alpha@hospital.com",
                    "adminEmail": "admin@alpha.com",
                    "adminPassword": "AlphaPass123!",
                    "adminFirstName": "Alpha",
                    "adminLastName": "Admin"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/auth/register-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        tenantAId = data.get("tenantId").asText();
        tenantAToken = data.get("accessToken").asText();
    }

    @Test
    @Order(2)
    @DisplayName("Setup: Register Tenant B")
    void setupTenantB() throws Exception {
        String request = """
                {
                    "hospitalName": "Hospital Beta",
                    "subdomain": "hospital-beta",
                    "contactEmail": "beta@hospital.com",
                    "adminEmail": "admin@beta.com",
                    "adminPassword": "BetaPass123!",
                    "adminFirstName": "Beta",
                    "adminLastName": "Admin"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/auth/register-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        tenantBId = data.get("tenantId").asText();
        tenantBToken = data.get("accessToken").asText();
    }

    @Test
    @Order(3)
    @DisplayName("Tenant A creates a patient")
    void createPatient_tenantA() throws Exception {
        String request = """
                {
                    "medicalRecordNumber": "MRN-001",
                    "firstName": "John",
                    "lastName": "Doe",
                    "dateOfBirth": "1990-05-15",
                    "gender": "MALE",
                    "bloodGroup": "O+",
                    "phone": "+1234567890"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tenantAToken)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.firstName").value("John"))
                .andExpect(jsonPath("$.data.lastName").value("Doe"))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        patientAId = data.get("id").asText();
    }

    @Test
    @Order(4)
    @DisplayName("Tenant A can list its own patients")
    void listPatients_tenantA_seesOwnPatients() throws Exception {
        mockMvc.perform(get("/api/patients")
                        .header("Authorization", "Bearer " + tenantAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].firstName").value("John"));
    }

    @Test
    @Order(5)
    @DisplayName("ISOLATION: Tenant B cannot see Tenant A's patients")
    void listPatients_tenantB_seesNoPatients() throws Exception {
        mockMvc.perform(get("/api/patients")
                        .header("Authorization", "Bearer " + tenantBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    @Order(6)
    @DisplayName("ISOLATION: Tenant B cannot access Tenant A's patient by ID")
    void getPatient_crossTenant_notFound() throws Exception {
        mockMvc.perform(get("/api/patients/" + patientAId)
                        .header("Authorization", "Bearer " + tenantBToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(7)
    @DisplayName("Update patient information")
    void updatePatient_success() throws Exception {
        String request = """
                {
                    "firstName": "Jonathan",
                    "lastName": "Doe",
                    "phone": "+0987654321"
                }
                """;

        mockMvc.perform(put("/api/patients/" + patientAId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tenantAToken)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.firstName").value("Jonathan"));
    }

    @Test
    @Order(8)
    @DisplayName("Reject duplicate MRN within same tenant")
    void createPatient_duplicateMrn() throws Exception {
        String request = """
                {
                    "medicalRecordNumber": "MRN-001",
                    "firstName": "Jane",
                    "lastName": "Smith",
                    "dateOfBirth": "1985-03-20",
                    "gender": "FEMALE"
                }
                """;

        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tenantAToken)
                        .content(request))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(9)
    @DisplayName("Same MRN allowed in different tenant")
    void createPatient_sameMrnDifferentTenant() throws Exception {
        String request = """
                {
                    "medicalRecordNumber": "MRN-001",
                    "firstName": "Different",
                    "lastName": "Patient",
                    "dateOfBirth": "2000-01-01",
                    "gender": "MALE"
                }
                """;

        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tenantBToken)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @Order(10)
    @DisplayName("Deactivate patient (soft delete)")
    void deletePatient_deactivate() throws Exception {
        mockMvc.perform(delete("/api/patients/" + patientAId)
                        .header("Authorization", "Bearer " + tenantAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Patient deactivated"));

        // List active only should return 0
        mockMvc.perform(get("/api/patients?active=true")
                        .header("Authorization", "Bearer " + tenantAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));

        // List inactive only should return 1
        mockMvc.perform(get("/api/patients?active=false")
                        .header("Authorization", "Bearer " + tenantAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].isActive").value(false));
    }

    @Test
    @Order(11)
    @DisplayName("Reactivate patient via update")
    void updatePatient_reactivate() throws Exception {
        String request = """
                {
                    "isActive": true
                }
                """;

        mockMvc.perform(put("/api/patients/" + patientAId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tenantAToken)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isActive").value(true));
    }

    @Test
    @Order(12)
    @DisplayName("Permanently delete patient")
    void deletePatient_permanent() throws Exception {
        mockMvc.perform(delete("/api/patients/" + patientAId + "?permanent=true")
                        .header("Authorization", "Bearer " + tenantAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Patient deleted permanently"));

        mockMvc.perform(get("/api/patients/" + patientAId)
                        .header("Authorization", "Bearer " + tenantAToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(13)
    @DisplayName("Unauthenticated request returns 401")
    void listPatients_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/patients"))
                .andExpect(status().isUnauthorized());
    }
}

