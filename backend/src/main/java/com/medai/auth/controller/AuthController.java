package com.medai.auth.controller;

import com.medai.auth.dto.*;
import com.medai.auth.service.AuthService;
import com.medai.common.dto.ApiResponse;
import com.medai.common.exception.ResourceNotFoundException;
import com.medai.tenant.entity.Tenant;
import com.medai.tenant.repository.TenantRepository;
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

    @PostMapping("/register-tenant")
    public ResponseEntity<ApiResponse<AuthResponse>> registerTenant(
            @Valid @RequestBody RegisterTenantRequest request) {
        AuthResponse response = authService.registerTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Hospital registered successfully", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/register-user")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<ApiResponse<AuthResponse>> registerUser(
            @Valid @RequestBody RegisterUserRequest request) {
        AuthResponse response = authService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User registered successfully", response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");
        AuthResponse response = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", response));
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
