package com.medai.compliance;

import com.medai.BaseIntegrationTest;
import com.medai.audit.service.AuditLogWriter;
import com.medai.audit.service.AuditService;
import com.medai.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The audit trail is written on every request and kept for six years, so it is the table most
 * likely to become the largest object in the database. These cover both halves of that: the
 * partitioning that bounds it, and the batched writer that keeps writing it off the request path.
 */
class AuditPartitioningTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditLogWriter auditLogWriter;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO tenants (id, name, subdomain, contact_email)
                VALUES (?, 'Partition Hospital', ?, 'p@example.test')
                """, tenantId, "part-" + tenantId.toString().substring(0, 8));
        TenantContext.setCurrentTenantId(tenantId);
        return tenantId;
    }

    @Test
    @DisplayName("audit_logs is partitioned by month with a default catch-all")
    void tableIsPartitioned() {
        String strategy = jdbcTemplate.queryForObject("""
                SELECT CASE partstrat WHEN 'r' THEN 'RANGE' ELSE partstrat::text END
                FROM pg_partitioned_table WHERE partrelid = 'audit_logs'::regclass
                """, String.class);
        assertThat(strategy).isEqualTo("RANGE");

        Integer partitions = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_inherits WHERE inhparent = 'audit_logs'::regclass", Integer.class);
        assertThat(partitions).isGreaterThan(1);

        Boolean hasDefault = jdbcTemplate.queryForObject(
                "SELECT count(*) > 0 FROM pg_class WHERE relname = 'audit_logs_default'", Boolean.class);
        assertThat(hasDefault)
                .as("a missing month must never make an audit write fail")
                .isTrue();
    }

    /**
     * A policy on the parent governs access through the parent. A partition is a table in its own
     * right and can be named directly, so it needs its own.
     */
    @Test
    @DisplayName("Every partition carries forced RLS and a WITH CHECK policy of its own")
    void everyPartitionIsProtected() {
        Integer unprotected = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM pg_inherits i
                JOIN pg_class c ON c.oid = i.inhrelid
                WHERE i.inhparent = 'audit_logs'::regclass
                  AND (NOT c.relforcerowsecurity
                       OR NOT EXISTS (SELECT 1 FROM pg_policies p
                                      WHERE p.tablename = c.relname AND p.with_check IS NOT NULL))
                """, Integer.class);

        assertThat(unprotected)
                .as("a partition without its own policy is a way around tenant isolation")
                .isZero();
    }

    @Test
    @DisplayName("A partition cannot be emptied by naming it directly")
    void partitionsAreAppendOnlyToo() {
        seedTenant();
        String partition = jdbcTemplate.queryForObject(
                "SELECT ensure_audit_partition(?)", String.class, Date.valueOf(LocalDate.now()));

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM " + partition))
                .rootCause()
                .hasMessageContaining("permission denied");
    }

    @Test
    @DisplayName("Creating a partition twice is a no-op, not a failure")
    void partitionCreationIsIdempotent() {
        LocalDate month = LocalDate.now().plusMonths(2).withDayOfMonth(1);

        String first = jdbcTemplate.queryForObject(
                "SELECT ensure_audit_partition(?)", String.class, Date.valueOf(month));
        String second = jdbcTemplate.queryForObject(
                "SELECT ensure_audit_partition(?)", String.class, Date.valueOf(month));

        assertThat(first).isEqualTo(second);
    }

    /**
     * The awkward path. A row for an uncreated month lands in the default partition, and
     * PostgreSQL then refuses to attach a partition covering it. The function has to detach the
     * default, carve out the month, move the rows, and reattach — without losing any.
     */
    @Test
    @DisplayName("A month already sitting in the default partition is carved out without loss")
    void defaultPartitionRowsAreMigrated() {
        UUID tenantId = seedTenant();
        LocalDate farFuture = LocalDate.now().plusMonths(30).withDayOfMonth(1);

        jdbcTemplate.update("""
                INSERT INTO audit_logs (tenant_id, action, entity_type, created_at)
                VALUES (?, 'FUTURE', 'Test', ?)
                """, tenantId, java.sql.Timestamp.valueOf(farFuture.atStartOfDay().plusDays(3)));

        Integer before = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE tenant_id = ?", Integer.class, tenantId);

        jdbcTemplate.queryForObject("SELECT ensure_audit_partition(?)", String.class, Date.valueOf(farFuture));

        Integer after = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE tenant_id = ?", Integer.class, tenantId);

        assertThat(after).as("the row must survive the detach/attach shuffle").isEqualTo(before);
    }

    /**
     * Pruning is the whole return on partitioning. Asserting on a partition that must not be
     * touched is more durable than counting removed subplans, which depends on how many months
     * happen to exist in the database under test.
     */
    @Test
    @DisplayName("A date-ranged query does not touch partitions outside the range")
    void queriesPrune() {
        LocalDate oldMonth = LocalDate.now().minusMonths(9).withDayOfMonth(1);
        String oldPartition = jdbcTemplate.queryForObject(
                "SELECT ensure_audit_partition(?)", String.class, Date.valueOf(oldMonth));

        String plan = String.join("\n", jdbcTemplate.queryForList("""
                EXPLAIN (COSTS OFF) SELECT * FROM audit_logs
                WHERE tenant_id = ? AND created_at >= now() - interval '10 days'
                """, String.class, UUID.randomUUID()));

        assertThat(plan)
                .as("a nine-month-old partition cannot hold rows from the last ten days, so the "
                    + "planner should never open it. Plan was:%n%s", plan)
                .doesNotContain(oldPartition);
    }

    @Test
    @DisplayName("The batched writer persists what it is given, under the right tenant")
    void writerPersistsEntries() {
        UUID tenantId = seedTenant();
        UUID entityId = UUID.randomUUID();

        auditService.record(tenantId, null, "GET_test", "Partitioning", entityId,
                Map.of("outcome", "SUCCESS"), "127.0.0.1", "junit");

        // Buffered, not written yet — that is the whole point of the change.
        auditLogWriter.flush();

        Integer written = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE tenant_id = ? AND entity_id = ?",
                Integer.class, tenantId, entityId);
        assertThat(written).isEqualTo(1);

        assertThat(auditLogWriter.pendingCount()).isZero();
    }

    @Test
    @DisplayName("One flush writes entries for several tenants, each under its own binding")
    void writerHandlesMixedTenantsInOneBatch() {
        UUID tenantA = seedTenant();
        UUID tenantB = seedTenant();
        UUID markerA = UUID.randomUUID();
        UUID markerB = UUID.randomUUID();

        auditService.record(tenantA, null, "GET_a", "Mixed", markerA, Map.of(), null, null);
        auditService.record(tenantB, null, "GET_b", "Mixed", markerB, Map.of(), null, null);

        auditLogWriter.flush();

        TenantContext.setCurrentTenantId(tenantA);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE entity_id = ?", Integer.class, markerA)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE entity_id = ?", Integer.class, markerB))
                .as("tenant A must not be able to see tenant B's audit row")
                .isZero();

        TenantContext.setCurrentTenantId(tenantB);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE entity_id = ?", Integer.class, markerB)).isEqualTo(1);
    }

    /** Serialisation of the detail map must never cost the entry itself. */
    @Test
    @DisplayName("An unserialisable detail map still produces an audit row")
    void unserialisableDetailsStillWriteTheEntry() {
        UUID tenantId = seedTenant();
        UUID entityId = UUID.randomUUID();

        auditService.record(tenantId, null, "GET_bad", "Partitioning", entityId,
                Map.of("self", new Object() {
                    @SuppressWarnings("unused")
                    public Object getSelf() {
                        throw new IllegalStateException("not serialisable");
                    }
                }), null, null);

        auditLogWriter.flush();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE tenant_id = ? AND entity_id = ?",
                Integer.class, tenantId, entityId)).isEqualTo(1);
    }
}
