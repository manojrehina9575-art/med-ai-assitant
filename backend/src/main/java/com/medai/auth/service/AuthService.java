package com.medai.auth.service;

import com.medai.auth.dto.*;
import com.medai.auth.entity.RefreshToken;
import com.medai.auth.repository.RefreshTokenRepository;
import com.medai.auth.security.JwtService;
import com.medai.common.exception.BadRequestException;
import com.medai.common.exception.UnauthorizedException;
import com.medai.tenant.TenantContext;
import com.medai.tenant.entity.Tenant;
import com.medai.tenant.repository.TenantRepository;
import com.medai.user.entity.User;
import com.medai.user.enums.UserRole;
import com.medai.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse registerTenant(RegisterTenantRequest request) {
        if (tenantRepository.existsBySubdomain(request.getSubdomain())) {
            throw new BadRequestException("Subdomain already taken: " + request.getSubdomain());
        }

        Tenant tenant = Tenant.builder()
                .name(request.getHospitalName())
                .subdomain(request.getSubdomain().toLowerCase())
                .contactEmail(request.getContactEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .settings(new HashMap<>())
                .build();
        tenant = tenantRepository.save(tenant);

        TenantContext.setCurrentTenantId(tenant.getId());

        if (userRepository.existsByTenantIdAndEmail(tenant.getId(), request.getAdminEmail())) {
            throw new BadRequestException("Email already registered: " + request.getAdminEmail());
        }

        User admin = User.builder()
                .email(request.getAdminEmail())
                .passwordHash(passwordEncoder.encode(request.getAdminPassword()))
                .firstName(request.getAdminFirstName())
                .lastName(request.getAdminLastName())
                .role(UserRole.HOSPITAL_ADMIN)
                .isActive(true)
                .build();
        admin.setTenantId(tenant.getId());
        admin = userRepository.save(admin);

        log.info("Registered new tenant: {} ({}), admin: {}", tenant.getName(), tenant.getId(), admin.getEmail());

        return buildAuthResponse(admin, tenant);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        UUID tenantId = request.getTenantId();
        if (tenantId == null) {
            throw new BadRequestException("Tenant ID is required");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new UnauthorizedException("Invalid tenant"));

        if (!tenant.getIsActive()) {
            throw new UnauthorizedException("Tenant is deactivated");
        }

        User user = userRepository.findByTenantIdAndEmail(tenantId, request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!user.getIsActive()) {
            throw new UnauthorizedException("Account is deactivated");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        TenantContext.setCurrentTenantId(tenantId);

        log.info("User logged in: {} (tenant: {})", user.getEmail(), tenant.getName());

        return buildAuthResponse(user, tenant);
    }

    @Transactional
    public AuthResponse registerUser(RegisterUserRequest request) {
        UUID tenantId = TenantContext.requireTenantId();

        if (userRepository.existsByTenantIdAndEmail(tenantId, request.getEmail())) {
            throw new BadRequestException("Email already registered: " + request.getEmail());
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(request.getRole())
                .specialization(request.getSpecialization())
                .licenseNumber(request.getLicenseNumber())
                .phone(request.getPhone())
                .isActive(true)
                .build();
        user.setTenantId(tenantId);
        user = userRepository.save(user);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BadRequestException("Tenant not found"));

        log.info("Registered new user: {} with role {} (tenant: {})", user.getEmail(), user.getRole(), tenantId);

        return buildAuthResponse(user, tenant);
    }

    /**
     * Refresh token rotation: the old refresh token is revoked and replaced
     * with a new one. If a revoked token is presented (replay attack),
     * all tokens for the user are revoked as a security measure.
     */
    @Transactional
    public AuthResponse refreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthorizedException("Refresh token is required");
        }

        String tokenHash = jwtService.hashToken(rawRefreshToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        // If the token was already revoked, this is a replay attack — revoke all tokens for this user
        if (storedToken.getRevoked()) {
            log.warn("Refresh token replay detected for user {}. Revoking all tokens.", storedToken.getUserId());
            refreshTokenRepository.revokeAllByUserId(storedToken.getUserId());
            throw new UnauthorizedException("Refresh token has been revoked. Please login again.");
        }

        if (storedToken.isExpired()) {
            throw new UnauthorizedException("Refresh token has expired. Please login again.");
        }

        User user = userRepository.findByIdAndTenantId(storedToken.getUserId(), storedToken.getTenantId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        Tenant tenant = tenantRepository.findById(storedToken.getTenantId())
                .orElseThrow(() -> new UnauthorizedException("Tenant not found"));

        TenantContext.setCurrentTenantId(tenant.getId());

        // Revoke the old token
        storedToken.setRevoked(true);

        // Generate new token pair
        String accessToken = jwtService.generateAccessToken(
                user.getId(), tenant.getId(), user.getEmail(), user.getRole().name());

        String newRawRefreshToken = jwtService.generateRefreshTokenValue();
        String newTokenHash = jwtService.hashToken(newRawRefreshToken);

        RefreshToken newRefreshToken = RefreshToken.builder()
                .userId(user.getId())
                .tenantId(tenant.getId())
                .tokenHash(newTokenHash)
                .expiresAt(Instant.now().plusMillis(jwtService.getRefreshExpirationMs()))
                .build();
        newRefreshToken = refreshTokenRepository.save(newRefreshToken);

        // Link old token to new one for audit trail
        storedToken.setReplacedBy(newRefreshToken.getId());
        refreshTokenRepository.save(storedToken);

        log.info("Refresh token rotated for user: {} (tenant: {})", user.getEmail(), tenant.getName());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationMs() / 1000)
                .userId(user.getId())
                .tenantId(tenant.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .tenantName(tenant.getName())
                .build();
    }

    /**
     * Builds the auth response with a new access token (JWT) and
     * a new opaque refresh token stored in the database.
     */
    private AuthResponse buildAuthResponse(User user, Tenant tenant) {
        String accessToken = jwtService.generateAccessToken(
                user.getId(), tenant.getId(), user.getEmail(), user.getRole().name());

        // Generate and store opaque refresh token
        String rawRefreshToken = jwtService.generateRefreshTokenValue();
        String tokenHash = jwtService.hashToken(rawRefreshToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .userId(user.getId())
                .tenantId(tenant.getId())
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusMillis(jwtService.getRefreshExpirationMs()))
                .build();
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationMs() / 1000)
                .userId(user.getId())
                .tenantId(tenant.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .tenantName(tenant.getName())
                .build();
    }
}

