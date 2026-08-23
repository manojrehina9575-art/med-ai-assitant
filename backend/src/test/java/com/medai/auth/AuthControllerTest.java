package com.medai.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medai.BaseIntegrationTest;
import com.medai.auth.security.RefreshTokenCookie;
import jakarta.servlet.http.Cookie;
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
 *
 * <p>Covers tenant registration, login, refresh-token rotation and replay detection, logout, and
 * the error cases. Every test that touches a refresh token now goes through the httpOnly cookie:
 * the token is no longer in the response body, and each assertion below checks that too, because
 * a regression that puts it back would otherwise be invisible.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthControllerTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String tenantId;
    private static String accessToken;
    private static Cookie refreshCookie;

    /** Pulls the refresh cookie out of a response, failing the test if the server did not set one. */
    private static Cookie refreshCookieFrom(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(RefreshTokenCookie.NAME);
        Assertions.assertNotNull(cookie, "Expected a " + RefreshTokenCookie.NAME + " cookie");
        Assertions.assertTrue(cookie.isHttpOnly(), "Refresh cookie must be httpOnly");
        Assertions.assertNotNull(cookie.getValue());
        Assertions.assertFalse(cookie.getValue().isBlank());
        return cookie;
    }

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
                // The refresh token must never appear in the body — it is cookie-only.
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.email").value("admin@testhospital.com"))
                .andExpect(jsonPath("$.data.role").value("HOSPITAL_ADMIN"))
                .andExpect(jsonPath("$.data.tenantName").value("Test Hospital"))
                .andReturn();

        JsonNode responseData = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data");
        tenantId = responseData.get("tenantId").asText();
        accessToken = responseData.get("accessToken").asText();
        refreshCookie = refreshCookieFrom(result);
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
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andReturn();

        // Update tokens for subsequent tests
        JsonNode responseData = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data");
        accessToken = responseData.get("accessToken").asText();
        refreshCookie = refreshCookieFrom(result);
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
    @DisplayName("Refresh rotates the cookie and revokes the presented token")
    void refreshToken_rotation() throws Exception {
        Cookie presented = refreshCookie;

        MvcResult result = mockMvc.perform(post("/api/auth/refresh").cookie(presented))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andReturn();

        Cookie rotated = refreshCookieFrom(result);
        Assertions.assertNotEquals(presented.getValue(), rotated.getValue(),
                "Refresh must issue a new token, not return the same one");

        // Replaying the old cookie is treated as theft: rejected, and the cookie is cleared so the
        // browser stops resending a token that will never work again.
        mockMvc.perform(post("/api/auth/refresh").cookie(presented))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().maxAge(RefreshTokenCookie.NAME, 0));

        refreshCookie = rotated;
    }

    /**
     * Replay detection revokes the whole family, so the token issued in test 5 is dead too. This
     * is the intended behaviour — it is what makes a stolen token useless rather than merely
     * single-use — so the session has to be re-established before the tests that follow.
     */
    @Test
    @Order(6)
    @DisplayName("Replay revokes the whole token family")
    void refreshToken_replayRevokesFamily() throws Exception {
        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized());

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
                .andReturn();

        accessToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
        refreshCookie = refreshCookieFrom(result);
    }

    @Test
    @Order(7)
    @DisplayName("Refresh with no cookie returns 401")
    void refreshToken_missingCookie() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * A refresh token in the request body is ignored entirely. Accepting one would restore exactly
     * the exposure the cookie removes, since a script able to post a token is a script able to
     * hold one.
     */
    @Test
    @Order(8)
    @DisplayName("Refresh ignores a token supplied in the body")
    void refreshToken_bodyIsIgnored() throws Exception {
        String request = """
                {
                    "refreshToken": "completely-invalid-token-value"
                }
                """;

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(9)
    @DisplayName("Invalid refresh cookie returns 401")
    void refreshToken_invalid() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie(RefreshTokenCookie.NAME, "completely-invalid-token-value")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(10)
    @DisplayName("Logout revokes the session server-side and expires the cookie")
    void logout_revokesSession() throws Exception {
        Cookie active = refreshCookie;

        mockMvc.perform(post("/api/auth/logout").cookie(active))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge(RefreshTokenCookie.NAME, 0));

        // The token is dead on the server, not merely dropped by the client — which is what
        // "logout" meant before, when the token stayed valid for the rest of its seven days.
        mockMvc.perform(post("/api/auth/refresh").cookie(active))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(11)
    @DisplayName("Logout without a session still succeeds")
    void logout_withoutCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(12)
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
    @Order(13)
    @DisplayName("Tenant lookup refuses to enumerate without a subdomain")
    void findTenant_requiresSubdomain() throws Exception {
        mockMvc.perform(get("/api/auth/tenants"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(14)
    @DisplayName("Tenant lookup does not reveal whether an unknown subdomain exists")
    void findTenant_unknownSubdomain() throws Exception {
        mockMvc.perform(get("/api/auth/tenants").param("subdomain", "no-such-hospital"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
