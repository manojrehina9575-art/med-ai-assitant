package com.medai.tenant;

import com.medai.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
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

    private void actAs(UUID tenantId) {
        jdbcTemplate.execute("SELECT set_config('app.current_tenant', '"
                             + (tenantId == null ? "" : tenantId) + "', false)");
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

        assertThatThrownBy(() -> insertPatient(tenantA, "RLS-SMUGGLED-1"))
                .as("USING alone filters reads; without WITH CHECK an insert can plant a row in "
                    + "another tenant")
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
}
