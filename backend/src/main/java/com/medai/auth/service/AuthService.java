package com.medai.auth.service;

import com.medai.auth.dto.*;
import com.medai.auth.entity.RefreshToken;
import com.medai.auth.repository.RefreshTokenRepository;
import com.medai.auth.security.JwtService;
import com.medai.common.exception.BadRequestException;
import com.medai.common.exception.UnauthorizedException;
import com.medai.tenant.TenantSession;
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
    private final TenantSession tenantSession;
    private final RefreshTokenRevoker refreshTokenRevoker;

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

        // Row-level security is enforced (V9), so the connection must be told which tenant it is
        // acting as before the admin user is inserted — the WITH CHECK clause would reject it.
        tenantSession.bind(tenant.getId());

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

        // Bind before any query against a tenant-scoped table: `users` is behind RLS, so an
        // unbound connection would find no account and every login would fail as bad credentials.
        tenantSession.bind(tenantId);

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

        log.info("User logged in: {} (tenant: {})", user.getEmail(), tenant.getName());

        return buildAuthResponse(user, tenant);
    }

    /**
     * Creates an account for another person and returns their profile.
     *
     * <p>Deliberately issues no tokens. It used to return a full {@code AuthResponse}, which handed
     * the administrator a working session for the account they had just created.
     */
    @Transactional
    public UserResponse registerUser(RegisterUserRequest request) {
        UUID tenantId = com.medai.tenant.TenantContext.requireTenantId();

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

        log.info("Registered new user: {} with role {} (tenant: {})", user.getEmail(), user.getRole(), tenantId);

        return UserResponse.builder()
                .id(user.getId())
                .tenantId(tenantId)
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .role(user.getRole())
                .specialization(user.getSpecialization())
                .licenseNumber(user.getLicenseNumber())
                .phone(user.getPhone())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .build();
    }

    /**
     * Revokes a refresh token, ending the session server-side.
     *
     * <p>Logout previously existed only on the client, which dropped its copy and left the token
     * valid for the remainder of its seven days. Revoking the whole family rather than the single
     * token is deliberate: a user logging out on a shared workstation means all of it.
     *
     * <p>An unknown or already-revoked token is not an error — logout must always succeed, or a
     * user with a stale token can never clear it.
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        // Looking a token up by hash precedes knowing its tenant, the same cross-tenant read the
        // refresh path performs. Scoped to this transaction.
        tenantSession.beginMaintenance();

        refreshTokenRepository.findByTokenHash(jwtService.hashToken(rawRefreshToken))
                .ifPresent(token -> refreshTokenRevoker.revokeAllForUser(token.getUserId(), "logout"));
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

        // A refresh token is looked up by hash before its tenant is known, which is the one read
        // that is legitimately cross-tenant. Scoped to this transaction only.
        tenantSession.beginMaintenance();

        String tokenHash = jwtService.hashToken(rawRefreshToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        // A token presented after it was rotated away is a replay: either the legitimate client is
        // retrying with a stale copy, or someone else has the token. Both mean the family is no
        // longer trustworthy, so all of it goes.
        //
        // The revocation runs in its own committed transaction. Calling the repository directly
        // here did nothing at all — the exception thrown on the next line rolled it back, so the
        // rotated-into token that the attacker held stayed valid for its full seven days.
        if (storedToken.getRevoked()) {
            refreshTokenRevoker.revokeAllForUser(storedToken.getUserId(), "refresh token replay detected");
            throw new UnauthorizedException("Refresh token has been revoked. Please login again.");
        }

        if (storedToken.isExpired()) {
            throw new UnauthorizedException("Refresh token has expired. Please login again.");
        }

        User user = userRepository.findByIdAndTenantId(storedToken.getUserId(), storedToken.getTenantId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        // Access tokens are stateless, so deactivation only takes effect at the next refresh.
        // With a 15-minute access token that bounds the window to 15 minutes.
        if (!user.getIsActive()) {
            // Same rollback trap as the replay branch above: this has to commit independently.
            refreshTokenRevoker.revokeAllForUser(user.getId(), "account is deactivated");
            throw new UnauthorizedException("Account is deactivated");
        }

        Tenant tenant = tenantRepository.findById(storedToken.getTenantId())
                .orElseThrow(() -> new UnauthorizedException("Tenant not found"));

        tenantSession.bind(tenant.getId());

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

