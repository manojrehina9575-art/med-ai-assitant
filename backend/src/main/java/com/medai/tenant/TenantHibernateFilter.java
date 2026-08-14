package com.medai.tenant;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;

/**
 * Interceptor that enables Hibernate tenant filtering and sets the PostgreSQL
 * RLS session variable for every authenticated request.
 *
 * <p>Two layers of tenant isolation:
 * <ul>
 *   <li><b>Hibernate Filter</b>: Adds {@code WHERE tenant_id = :tenantId} to all JPA queries
 *       on entities extending {@code TenantAwareEntity}.</li>
 *   <li><b>PostgreSQL RLS</b>: Sets {@code app.current_tenant} session variable so that
 *       Row-Level Security policies enforce isolation even for raw SQL or missed filters.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantHibernateFilter implements HandlerInterceptor {

    private final EntityManager entityManager;
    private final DataSource dataSource;

    @Override
    public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                             jakarta.servlet.http.HttpServletResponse response,
                             Object handler) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        if (tenantId != null) {
            enableHibernateFilter(tenantId);
            setRlsSessionVariable(tenantId);
        }
        return true;
    }

    /**
     * Enables the Hibernate filter so all JPA queries on TenantAwareEntity
     * subclasses automatically include tenant_id filtering.
     */
    private void enableHibernateFilter(UUID tenantId) {
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("tenantFilter")
               .setParameter("tenantId", tenantId);
    }

    /**
     * Sets the PostgreSQL session variable used by Row-Level Security policies.
     * Uses SET LOCAL so the variable is scoped to the current transaction.
     */
    private void setRlsSessionVariable(UUID tenantId) {
        try {
            Connection connection = dataSource.getConnection();
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SET LOCAL app.current_tenant = ?")) {
                stmt.setString(1, tenantId.toString());
                stmt.execute();
            } finally {
                connection.close();
            }
        } catch (Exception e) {
            log.warn("Failed to set RLS session variable for tenant {}: {}", tenantId, e.getMessage());
        }
    }
}
