package com.medai.auth.service;

import com.medai.auth.dto.*;
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

    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtService.validateToken(refreshToken)) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        UUID userId = jwtService.extractUserId(refreshToken);
        UUID tenantId = jwtService.extractTenantId(refreshToken);

        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new UnauthorizedException("Tenant not found"));

        TenantContext.setCurrentTenantId(tenantId);

        return buildAuthResponse(user, tenant);
    }

    private AuthResponse buildAuthResponse(User user, Tenant tenant) {
        String accessToken = jwtService.generateAccessToken(
                user.getId(), tenant.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(user.getId(), tenant.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
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
