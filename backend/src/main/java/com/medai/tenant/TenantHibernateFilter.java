package com.medai.tenant;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * Enables the Hibernate tenant filter for the current request, adding
 * {@code WHERE tenant_id = :tenantId} to JPA queries on {@code TenantAwareEntity} subclasses.
 *
 * <p>This is the first of two isolation layers and the one that shapes queries. The second — and
 * the one that actually enforces — is PostgreSQL row-level security, whose session variable is set
 * per connection by {@link TenantAwareDataSource}. Setting that variable used to happen here too,
 * which was unreliable: an interceptor runs once per request, while connections are checked out per
 * transaction, so any work on a second connection ran unprotected.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantHibernateFilter implements HandlerInterceptor {

    private final EntityManager entityManager;

    @Override
    public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                             jakarta.servlet.http.HttpServletResponse response,
                             Object handler) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        if (tenantId != null) {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
        }
        return true;
    }
}
