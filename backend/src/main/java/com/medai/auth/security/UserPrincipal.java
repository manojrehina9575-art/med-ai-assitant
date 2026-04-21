package com.medai.auth.security;

import java.util.UUID;

public record UserPrincipal(
        UUID userId,
        UUID tenantId,
        String email,
        String role
) {}
