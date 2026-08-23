package com.medai.auth.service;

import com.medai.auth.repository.RefreshTokenRepository;
import com.medai.tenant.TenantSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Revokes a user's refresh tokens in a transaction that commits even when the caller fails.
 *
 * <p>{@code AuthService.refreshToken} detects a replayed token, revokes the whole family, and then
 * throws — and throwing rolled the revocation straight back. Replay detection was logging a
 * warning and changing nothing: the token the attacker had rotated into stayed valid for its full
 * seven days. The same rollback silently undid the revocation on the deactivated-account path.
 *
 * <p>{@code REQUIRES_NEW} in its own bean is what fixes it. It has to be a separate bean: a
 * self-invocation does not pass through the transactional proxy, so the annotation would have been
 * inert on a private method of {@code AuthService}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenRevoker {

    private final RefreshTokenRepository refreshTokenRepository;
    private final TenantSession tenantSession;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllForUser(UUID userId, String reason) {
        // Runs before the caller is authenticated, so no tenant is bound to this connection and
        // row-level security would match nothing. Scoped to this transaction only.
        tenantSession.beginMaintenance();

        int revoked = refreshTokenRepository.revokeAllByUserId(userId);
        log.warn("Revoked {} refresh token(s) for user {}: {}", revoked, userId, reason);
    }
}
