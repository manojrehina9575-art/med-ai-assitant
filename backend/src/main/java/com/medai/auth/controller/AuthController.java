package com.medai.auth.controller;

import com.medai.auth.dto.*;
import com.medai.auth.security.RefreshTokenCookie;
import com.medai.auth.service.AuthService;
import com.medai.common.dto.ApiResponse;
import com.medai.common.exception.ResourceNotFoundException;
import com.medai.common.exception.UnauthorizedException;
import com.medai.tenant.entity.Tenant;
import com.medai.tenant.repository.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TenantRepository tenantRepository;
    private final RefreshTokenCookie refreshTokenCookie;

    @PostMapping("/register-tenant")
    public ResponseEntity<ApiResponse<AuthResponse>> registerTenant(
            @Valid @RequestBody RegisterTenantRequest request,
            HttpServletResponse response) {
        AuthResponse auth = authService.registerTenant(request);
        refreshTokenCookie.set(response, auth.getRefreshToken());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Hospital registered successfully", auth));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        AuthResponse auth = authService.login(request);
        refreshTokenCookie.set(response, auth.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("Login successful", auth));
    }

    /**
     * Creates an account for someone else.
     *
     * <p>Returns the new user's profile and nothing else. It used to return a full
     * {@code AuthResponse} — an access token and a refresh token for the created account, handed
     * to the administrator who created it. Those are that user's credentials, not the admin's, and
     * an admin should never hold them.
     */
    @PostMapping("/register-user")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> registerUser(
            @Valid @RequestBody RegisterUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User registered successfully", authService.registerUser(request)));
    }

    /**
     * Rotates the refresh token and issues a new access token.
     *
     * <p>The token is read from the httpOnly cookie, never from the request body — the client
     * never receives it and so cannot send it. The body is not consulted at all: accepting one
     * would recreate exactly the exposure the cookie exists to remove, since a script that could
     * post a token is a script that could hold one.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            HttpServletRequest request, HttpServletResponse response) {
        String rawRefreshToken = refreshTokenCookie.read(request)
                .orElseThrow(() -> new UnauthorizedException("No active session"));

        AuthResponse auth;
        try {
            auth = authService.refreshToken(rawRefreshToken);
        } catch (UnauthorizedException e) {
            // A rejected token is a dead session: drop the cookie so the browser stops resending
            // it on every reload, which would otherwise look like repeated replay attempts.
            refreshTokenCookie.clear(response);
            throw e;
        }

        refreshTokenCookie.set(response, auth.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", auth));
    }

    /**
     * Ends the session: revokes the refresh token server-side and expires the cookie.
     *
     * <p>Without this there was no logout at all — the client dropped its copy of the token and
     * the token itself stayed valid for its full seven days.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request, HttpServletResponse response) {
        refreshTokenCookie.read(request).ifPresent(authService::logout);
        refreshTokenCookie.clear(response);
        return ResponseEntity.ok(ApiResponse.success("Logged out", null));
    }

    /**
     * Resolves a single hospital by its subdomain, for the login form.
     *
     * <p>This used to return every tenant on the platform to anyone who asked — a public list of
     * your customers, and a ready-made target list for credential stuffing. A caller must now know
     * the subdomain, which they do: it is what their hospital gave them.
     */
    @GetMapping("/tenants")
    public ResponseEntity<ApiResponse<Map<String, Object>>> findTenant(
            @RequestParam String subdomain) {
        Tenant tenant = tenantRepository.findBySubdomain(subdomain.trim().toLowerCase())
                .filter(Tenant::getIsActive)
                // Deliberately the same response for "no such hospital" and "deactivated", so the
                // endpoint cannot be used to enumerate which subdomains exist.
                .orElseThrow(() -> new ResourceNotFoundException("Hospital", "subdomain", subdomain));

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "id", tenant.getId(),
                "name", tenant.getName(),
                "subdomain", tenant.getSubdomain())));
    }
}
