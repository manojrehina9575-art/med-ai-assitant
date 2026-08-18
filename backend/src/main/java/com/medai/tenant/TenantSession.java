package com.medai.tenant;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.util.UUID;

/**
 * Binds a tenant to the current thread <em>and</em> the connection already in use.
 *
 * <p>{@link TenantAwareDataSource} covers the normal case, where the tenant is known before the
 * first query. Three flows discover the tenant only part-way through a transaction — registering a
 * hospital, logging in, and rotating a refresh token — by which point a connection may already be
 * checked out and stamped empty. Those flows call {@link #bind(UUID)} to re-stamp it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantSession {

    private final EntityManager entityManager;

    /** Sets the tenant for this thread and re-stamps the connection currently in use. */
    public void bind(UUID tenantId) {
        TenantContext.setCurrentTenantId(tenantId);
        setConfig("app.current_tenant", tenantId.toString(), false);
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
    }

    /**
     * Lifts tenant scoping for the remainder of the current transaction.
     *
     * <p>Needed by the two operations that are legitimately cross-tenant: looking up a refresh
     * token by its hash before the tenant is known, and the reaper claiming stalled analyses
     * across all tenants. The setting is transaction-local, so it cannot leak to another request
     * through the connection pool.
     */
    public void beginMaintenance() {
        setConfig("app.maintenance", "on", true);
    }

    private void setConfig(String key, String value, boolean transactionLocal) {
        Session session = entityManager.unwrap(Session.class);
        session.doWork(connection -> {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT set_config(?, ?, ?)")) {
                stmt.setString(1, key);
                stmt.setString(2, value);
                stmt.setBoolean(3, transactionLocal);
                stmt.execute();
            }
        });
    }
}
