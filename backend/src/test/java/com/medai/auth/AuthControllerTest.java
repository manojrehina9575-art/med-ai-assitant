package com.medai.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medai.BaseIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for authentication endpoints.
 * Tests tenant registration, login, token refresh rotation, and error cases.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthControllerTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String tenantId;
    private static String accessToken;
    private static String refreshToken;

    @Test
    @Order(1)
    @DisplayName("Register a new hospital tenant with admin user")
    void registerTenant_success() throws Exception {
        String request = """
                {
                    "hospitalName": "Test Hospital",
                    "subdomain": "test-hospital",
                    "contactEmail": "contact@testhospital.com",
                    "phone": "+1234567890",
                    "address": "123 Test Street",
                    "adminEmail": "admin@testhospital.com",
                    "adminPassword": "SecurePass123!",
                    "adminFirstName": "Admin",
                    "adminLastName": "User"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/auth/register-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.email").value("admin@testhospital.com"))
                .andExpect(jsonPath("$.data.role").value("HOSPITAL_ADMIN"))
                .andExpect(jsonPath("$.data.tenantName").value("Test Hospital"))
                .andReturn();

        JsonNode responseData = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data");
        tenantId = responseData.get("tenantId").asText();
        accessToken = responseData.get("accessToken").asText();
        refreshToken = responseData.get("refreshToken").asText();
    }

    @Test
    @Order(2)
    @DisplayName("Reject duplicate subdomain registration")
    void registerTenant_duplicateSubdomain() throws Exception {
        String request = """
                {
                    "hospitalName": "Another Hospital",
                    "subdomain": "test-hospital",
                    "contactEmail": "other@hospital.com",
                    "adminEmail": "admin@other.com",
                    "adminPassword": "SecurePass123!",
                    "adminFirstName": "Other",
                    "adminLastName": "Admin"
                }
                """;

        mockMvc.perform(post("/api/auth/register-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(3)
    @DisplayName("Login with valid credentials")
    void login_success() throws Exception {
        String request = String.format("""
                {
                    "tenantId": "%s",
                    "email": "admin@testhospital.com",
                    "password": "SecurePass123!"
                }
                """, tenantId);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();

        // Update tokens for subsequent tests
        JsonNode responseData = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data");
        accessToken = responseData.get("accessToken").asText();
        refreshToken = responseData.get("refreshToken").asText();
    }

    @Test
    @Order(4)
    @DisplayName("Login with wrong password returns 401")
    void login_wrongPassword() throws Exception {
        String request = String.format("""
                {
                    "tenantId": "%s",
                    "email": "admin@testhospital.com",
                    "password": "WrongPassword!"
                }
                """, tenantId);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(5)
    @DisplayName("Refresh token rotation — returns new token pair and invalidates old token")
    void refreshToken_rotation() throws Exception {
        String request = String.format("""
                {
                    "refreshToken": "%s"
                }
                """, refreshToken);

        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();

        JsonNode responseData = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data");

        String newRefreshToken = responseData.get("refreshToken").asText();

        // Old refresh token should no longer work (it was revoked)
        String replayRequest = String.format("""
                {
                    "refreshToken": "%s"
                }
                """, refreshToken);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(replayRequest))
                .andExpect(status().isUnauthorized());

        // Update for any subsequent tests
        refreshToken = newRefreshToken;
    }

    @Test
    @Order(6)
    @DisplayName("Invalid refresh token returns 401")
    void refreshToken_invalid() throws Exception {
        String request = """
                {
                    "refreshToken": "completely-invalid-token-value"
                }
                """;

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(7)
    @DisplayName("Tenant lookup resolves one hospital by subdomain")
    void findTenant_bySubdomain() throws Exception {
        mockMvc.perform(get("/api/auth/tenants").param("subdomain", "test-hospital"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Test Hospital"))
                .andExpect(jsonPath("$.data.subdomain").value("test-hospital"));
    }

    /**
     * The endpoint deliberately no longer lists every tenant — that was a public customer list and
     * a credential-stuffing target list. Asking without a subdomain is a client error, not a
     * request for everything.
     */
    @Test
    @Order(8)
    @DisplayName("Tenant lookup refuses to enumerate without a subdomain")
    void findTenant_requiresSubdomain() throws Exception {
        mockMvc.perform(get("/api/auth/tenants"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(9)
    @DisplayName("Tenant lookup does not reveal whether an unknown subdomain exists")
    void findTenant_unknownSubdomain() throws Exception {
        mockMvc.perform(get("/api/auth/tenants").param("subdomain", "no-such-hospital"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
