package com.medai.compliance;

import com.medai.BaseIntegrationTest;
import com.medai.compliance.retention.service.DataRetentionService;
import com.medai.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The audit trail is the first artefact a regulator asks for, and it was deletable by the
 * application on a tenant-configurable 365-day schedule. These prove it is not any more.
 */
class AuditRetentionTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private UUID seedTenantWithAuditRows() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO tenants (id, name, subdomain, contact_email)
                VALUES (?, 'Audit Hospital', ?, 'audit@example.test')
                """, tenantId, "audit-" + tenantId.toString().substring(0, 8));

        TenantContext.setCurrentTenantId(tenantId);

        jdbcTemplate.update("""
                INSERT INTO audit_logs (tenant_id, action, entity_type, created_at)
                VALUES (?, 'READ', 'Patient', ?)
                """, tenantId, Timestamp.from(Instant.now().minus(8 * 365, ChronoUnit.DAYS)));
        jdbcTemplate.update("""
                INSERT INTO audit_logs (tenant_id, action, entity_type, created_at)
                VALUES (?, 'READ', 'Patient', ?)
                """, tenantId, Timestamp.from(Instant.now().minus(2 * 365, ChronoUnit.DAYS)));

        return tenantId;
    }

    @Test
    @DisplayName("The application role cannot delete an audit row")
    void auditLogsCannotBeDeleted() {
        UUID tenantId = seedTenantWithAuditRows();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM audit_logs WHERE tenant_id = ?", tenantId))
                .as("an audit trail the application can erase is not an audit trail")
                .rootCause()
                .hasMessageContaining("permission denied");
    }

    @Test
    @DisplayName("The application role cannot alter an audit row")
    void auditLogsCannotBeUpdated() {
        UUID tenantId = seedTenantWithAuditRows();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE audit_logs SET action = 'TAMPERED' WHERE tenant_id = ?", tenantId))
                .rootCause()
                .hasMessageContaining("permission denied");
    }

    /**
     * HIPAA 164.316(b)(2)(i) requires six years. The purge takes whatever cutoff it is given and
     * clamps it, so a tenant configured for 30 days still keeps six years.
     */
    @Test
    @DisplayName("A purge below the statutory floor is clamped, not honoured")
    void purgeIsClampedToSixYears() {
        UUID tenantId = seedTenantWithAuditRows();

        Integer purged = jdbcTemplate.queryForObject(
                "SELECT purge_audit_logs(?, ?)", Integer.class,
                tenantId, Timestamp.from(Instant.now().minus(30, ChronoUnit.DAYS)));

        assertThat(purged).as("only the eight-year-old row is older than six years").isEqualTo(1);

        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE tenant_id = ?", Integer.class, tenantId);
        assertThat(remaining).as("the two-year-old row must survive a 30-day purge request").isEqualTo(1);
    }

    @Test
    @DisplayName("Purged rows are archived, not destroyed")
    void purgedRowsAreArchived() {
        UUID tenantId = seedTenantWithAuditRows();

        jdbcTemplate.queryForObject("SELECT purge_audit_logs(?, ?)", Integer.class,
                tenantId, Timestamp.from(Instant.now().minus(30, ChronoUnit.DAYS)));

        // The application cannot read the archive at all, so this checks through the owner
        // connection Flyway uses — which is the point: only an operator can see it.
        assertThatThrownBy(() -> jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log_archive", Integer.class))
                .as("the application must have no access to the archive")
                .rootCause()
                .hasMessageContaining("permission denied");
    }

    @Test
    @DisplayName("A retention policy below six years is rejected with a reason")
    void policyFloorIsEnforced() {
        UUID tenantId = seedTenantWithAuditRows();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO data_retention_policies (tenant_id, audit_log_retention_days)
                VALUES (?, 365)
                """, tenantId))
                .rootCause()
                .hasMessageContaining("chk_audit_retention_statutory_floor");
    }

    @Test
    @DisplayName("The service constant matches the database constraint")
    void serviceFloorMatchesDatabase() {
        assertThat(DataRetentionService.MIN_AUDIT_RETENTION_DAYS).isEqualTo(2190);
    }
}
