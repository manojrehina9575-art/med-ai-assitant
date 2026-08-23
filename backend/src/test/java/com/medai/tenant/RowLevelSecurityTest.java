package com.medai.tenant;

import com.medai.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves tenant isolation at the database level, bypassing the application entirely.
 *
 * <p>The existing isolation tests go through the REST API, so they pass as long as the Hibernate
 * filter works — they cannot tell whether row-level security is doing anything. It was not: the
 * policies existed but the application connected as a superuser, which bypasses RLS
 * unconditionally. These tests issue raw SQL on the application's own connection, which is the
 * only way to catch that.
 */
class RowLevelSecurityTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID createTenant(String subdomain) {
        UUID tenantId = UUID.randomUUID();
        // tenants is not tenant-scoped (it is the registry of tenants), so this insert needs no
        // session variable.
        jdbcTemplate.update("""
                INSERT INTO tenants (id, name, subdomain, contact_email)
                VALUES (?, ?, ?, ?)
                """, tenantId, subdomain + " Hospital", subdomain, subdomain + "@example.test");
        return tenantId;
    }

    /**
     * Binds the tenant the way the application does, through {@link TenantContext}.
     *
     * <p>Issuing {@code set_config} directly would prove nothing: {@link TenantAwareDataSource}
     * re-stamps {@code app.current_tenant} on every connection as it leaves the pool, so a value
     * set by one statement is gone by the next — each {@code JdbcTemplate} call borrows and
     * returns its own connection. Going through the context exercises that stamping, which is the
     * mechanism RLS actually depends on.
     */
    private void actAs(UUID tenantId) {
        if (tenantId == null) {
            TenantContext.clear();
        } else {
            TenantContext.setCurrentTenantId(tenantId);
        }
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private void insertPatient(UUID tenantId, String mrn) {
        jdbcTemplate.update("""
                INSERT INTO patients (tenant_id, medical_record_number, first_name, last_name, date_of_birth, gender)
                VALUES (?, ?, 'Test', 'Patient', DATE '1980-01-01', 'MALE')
                """, tenantId, mrn);
    }

    @Test
    @DisplayName("The application's database role cannot bypass row-level security")
    void appRoleIsSubjectToRls() {
        String role = jdbcTemplate.queryForObject("SELECT current_user", String.class);

        Boolean canBypass = jdbcTemplate.queryForObject(
                "SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname = current_user", Boolean.class);

        assertThat(canBypass)
                .as("connecting as a superuser or BYPASSRLS role (%s) makes every RLS policy "
                    + "decorative — check spring.datasource.username", role)
                .isFalse();
    }

    @Test
    @DisplayName("With no tenant bound, tenant-scoped tables return nothing")
    void unboundConnectionSeesNoRows() {
        UUID tenantA = createTenant("rls-unbound-a");
        actAs(tenantA);
        insertPatient(tenantA, "RLS-UNBOUND-1");

        actAs(null);

        Integer visible = jdbcTemplate.queryForObject("SELECT count(*) FROM patients", Integer.class);
        assertThat(visible)
                .as("a connection with no tenant in scope must see no patient rows at all")
                .isZero();
    }

    @Test
    @DisplayName("A tenant sees only its own rows, even through raw SQL")
    void tenantSeesOnlyItsOwnRows() {
        UUID tenantA = createTenant("rls-read-a");
        UUID tenantB = createTenant("rls-read-b");

        actAs(tenantA);
        insertPatient(tenantA, "RLS-READ-A1");

        actAs(tenantB);
        insertPatient(tenantB, "RLS-READ-B1");

        // Still acting as B: A's row must be invisible.
        var visibleMrns = jdbcTemplate.queryForList(
                "SELECT medical_record_number FROM patients", String.class);

        assertThat(visibleMrns).contains("RLS-READ-B1");
        assertThat(visibleMrns).doesNotContain("RLS-READ-A1");
    }

    @Test
    @DisplayName("WITH CHECK refuses a write carrying another tenant's id")
    void cannotWriteIntoAnotherTenant() {
        UUID tenantA = createTenant("rls-write-a");
        UUID tenantB = createTenant("rls-write-b");

        actAs(tenantB);

        // Assert on the root cause, as appRoleCannotAlterSchema does: Spring wraps the driver
        // exception and its own message carries only the failing SQL, not PostgreSQL's reason.
        assertThatThrownBy(() -> insertPatient(tenantA, "RLS-SMUGGLED-1"))
                .as("USING alone filters reads; without WITH CHECK an insert can plant a row in "
                    + "another tenant")
                .rootCause()
                .hasMessageContaining("row-level security");
    }

    @Test
    @DisplayName("The application role cannot perform DDL")
    void appRoleCannotAlterSchema() {
        assertThatThrownBy(() -> jdbcTemplate.execute("ALTER TABLE patients DROP COLUMN allergies"))
                .rootCause()
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("must be owner");
    }

    /**
     * Every tenant-scoped table, checked structurally.
     *
     * <p>The per-table tests above only cover {@code patients}. Thirteen tables added by V13-V15
     * had policies that tested {@code current_setting('app.current_tenant_id')} — a name nothing
     * sets — so they matched no rows in either direction and the application could neither read
     * nor write prescriptions, notifications, workflows or consent records. Nothing failed loudly;
     * the tables were simply always empty.
     *
     * <p>Asserting on the catalogue rather than on behaviour is deliberate: it covers a table the
     * moment it exists, without anyone remembering to write a case for it, which is the property
     * the original omission needed.
     */
    @Test
    @DisplayName("Every tenant-scoped table is protected by a forced, checked policy")
    void everyTenantTableIsProtected() {
        List<String> tenantScopedTables = jdbcTemplate.queryForList("""
                SELECT c.relname
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace AND n.nspname = 'public'
                JOIN pg_attribute a ON a.attrelid = c.oid AND a.attname = 'tenant_id' AND a.attnum > 0
                WHERE c.relkind = 'r'
                  AND c.relname <> 'audit_log_archive'
                ORDER BY c.relname
                """, String.class);

        assertThat(tenantScopedTables)
                .as("expected the tenant-scoped tables to be discoverable")
                .isNotEmpty();

        for (String table : tenantScopedTables) {
            var flags = jdbcTemplate.queryForMap("""
                    SELECT c.relrowsecurity AS enabled, c.relforcerowsecurity AS forced
                    FROM pg_class c
                    JOIN pg_namespace n ON n.oid = c.relnamespace AND n.nspname = 'public'
                    WHERE c.relname = ?
                    """, table);

            assertThat(flags.get("enabled"))
                    .as("row-level security is not enabled on %s", table).isEqualTo(true);
            assertThat(flags.get("forced"))
                    .as("FORCE is not set on %s, so the owner bypasses its policies", table)
                    .isEqualTo(true);

            Integer usable = jdbcTemplate.queryForObject("""
                    SELECT count(*) FROM pg_policies
                    WHERE schemaname = 'public' AND tablename = ?
                      AND qual IS NOT NULL AND with_check IS NOT NULL
                    """, Integer.class, table);

            assertThat(usable)
                    .as("%s has no policy with both USING and WITH CHECK; USING alone filters "
                        + "reads and lets an insert plant a row in another tenant", table)
                    .isGreaterThan(0);
        }
    }

    /**
     * The specific defect: a policy comparing against a setting the application never assigns
     * evaluates to NULL, which is falsy, so the table is invisible rather than protected.
     */
    @Test
    @DisplayName("No policy references a session setting the application does not set")
    void noPolicyReferencesAnUnsetSetting() {
        List<String> broken = jdbcTemplate.queryForList("""
                SELECT tablename || '.' || policyname
                FROM pg_policies
                WHERE schemaname = 'public'
                  AND (coalesce(qual, '') LIKE '%current_tenant_id%'
                    OR coalesce(with_check, '') LIKE '%current_tenant_id%')
                """, String.class);

        assertThat(broken)
                .as("these policies test app.current_tenant_id; TenantAwareDataSource sets "
                    + "app.current_tenant, so they can never match")
                .isEmpty();
    }

    /**
     * The write path for a table that was unreachable before V16, exercised end to end. The
     * structural test above would pass on a policy that was correctly shaped and still wrong.
     */
    @Test
    @DisplayName("A repaired table isolates reads and refuses cross-tenant writes")
    void repairedTableIsolatesProperly() {
        UUID tenantA = createTenant("rls-presc-a");
        UUID tenantB = createTenant("rls-presc-b");

        actAs(tenantA);
        UUID patientA = insertPatientReturningId(tenantA, "RLS-PRESC-A1");
        UUID doctorA = insertUser(tenantA, "doc-a@rls.test");
        insertPrescription(tenantA, patientA, doctorA, "Amoxicillin");

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM prescriptions", Integer.class))
                .as("the owning tenant must see its own prescription — before V16 it saw nothing")
                .isEqualTo(1);

        actAs(tenantB);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM prescriptions", Integer.class))
                .as("another tenant must see none of it")
                .isZero();

        assertThatThrownBy(() -> insertPrescription(tenantA, patientA, doctorA, "Smuggled"))
                .rootCause()
                .hasMessageContaining("row-level security");
    }

    private UUID insertPatientReturningId(UUID tenantId, String mrn) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO patients (id, tenant_id, medical_record_number, first_name, last_name, date_of_birth, gender)
                VALUES (?, ?, ?, 'Test', 'Patient', DATE '1980-01-01', 'MALE')
                """, id, tenantId, mrn);
        return id;
    }

    private UUID insertUser(UUID tenantId, String email) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (id, tenant_id, email, password_hash, first_name, last_name, role)
                VALUES (?, ?, ?, 'x', 'Test', 'Doctor', 'DOCTOR')
                """, id, tenantId, email);
        return id;
    }

    private void insertPrescription(UUID tenantId, UUID patientId, UUID doctorId, String drug) {
        jdbcTemplate.update("""
                INSERT INTO prescriptions (tenant_id, patient_id, doctor_id, medications, diagnosis)
                VALUES (?, ?, ?, CAST(? AS jsonb), 'Test')
                """, tenantId, patientId, doctorId, "[{\"name\":\"" + drug + "\"}]");
    }
}
