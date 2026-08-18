package com.medai.tenant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Stamps {@code app.current_tenant} on every connection as it is handed out, from
 * {@link TenantContext}.
 *
 * <p>This is what makes PostgreSQL row-level security dependable. Previously the setting was
 * applied once per request by a Spring MVC interceptor, which only worked because
 * {@code spring.jpa.open-in-view} keeps a single Hibernate session — and therefore a single
 * connection — open for the whole request. Any code path that acquired a different connection
 * (a new transaction, a background thread, a second datasource call) ran with the setting unset
 * or, worse, with a value left behind by a previous request on that pooled connection.
 *
 * <p>Setting it at checkout removes both problems: every connection is stamped with the current
 * tenant, and a connection with no tenant in scope is explicitly stamped empty rather than
 * inheriting the last one.
 */
@Slf4j
public class TenantAwareDataSource extends DelegatingDataSource {

    public TenantAwareDataSource(DataSource targetDataSource) {
        super(targetDataSource);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return stamp(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return stamp(super.getConnection(username, password));
    }

    private Connection stamp(Connection connection) throws SQLException {
        UUID tenantId = TenantContext.getCurrentTenantId();
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT set_config('app.current_tenant', ?, false)")) {
            // Empty string rather than NULL: the RLS policies compare against
            // NULLIF(current_setting(...), '') so an empty value matches no rows at all.
            stmt.setString(1, tenantId != null ? tenantId.toString() : "");
            stmt.execute();
        } catch (SQLException e) {
            // Fail closed: a connection we could not stamp must not be used, or it would run
            // with whatever tenant the previous borrower left behind.
            connection.close();
            throw e;
        }
        return connection;
    }
}
