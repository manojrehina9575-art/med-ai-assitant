package com.medai.billing;

import com.medai.BaseIntegrationTest;
import com.medai.billing.dto.BillingDtos.*;
import com.medai.billing.service.BillingService;
import com.medai.common.exception.BadRequestException;
import com.medai.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Invoice arithmetic, against a real database.
 *
 * <p>Billing bugs are the expensive kind: they are discovered by a customer, they are discovered
 * after money has moved, and they cost trust disproportionate to their size. Every assertion here
 * is a number a hospital's accounts department would check.
 */
class BillingServiceTest extends BaseIntegrationTest {

    @Autowired private BillingService billingService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO tenants (id, name, subdomain, contact_email)
                VALUES (?, 'Billing Hospital', ?, 'b@example.test')
                """, tenantId, "bill-" + tenantId.toString().substring(0, 8));
        TenantContext.setCurrentTenantId(tenantId);
        return tenantId;
    }

    private UUID seedUser(UUID tenantId, LocalDate lastLogin) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (id, tenant_id, email, password_hash, first_name, last_name, role, last_login_at)
                VALUES (?, ?, ?, 'x', 'A', 'B', 'DOCTOR', ?)
                """, id, tenantId, "u-" + id + "@bill.test",
                lastLogin == null ? null : Timestamp.valueOf(lastLogin.atStartOfDay()));
        return id;
    }

    private UUID seedPatient(UUID tenantId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO patients (id, tenant_id, medical_record_number, first_name, last_name, date_of_birth, gender)
                VALUES (?, ?, ?, 'P', 'Q', DATE '1980-01-01', 'MALE')
                """, id, tenantId, "MRN-" + id.toString().substring(0, 8));
        return id;
    }

    /** Seeds one analysis with an explicit status and date, so period boundaries can be tested. */
    private void seedAnalysis(UUID tenantId, UUID patientId, UUID userId, String status, LocalDate on) {
        UUID fileId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO medical_files
                    (id, tenant_id, patient_id, uploaded_by, file_name, original_file_name,
                     file_type, mime_type, file_size_bytes, storage_path)
                VALUES (?, ?, ?, ?, 'f', 'f', 'XRAY', 'image/png', 1, 'p')
                """, fileId, tenantId, patientId, userId);

        jdbcTemplate.update("""
                INSERT INTO analysis_requests
                    (tenant_id, patient_id, medical_file_id, requested_by, analysis_type, status, created_at)
                VALUES (?, ?, ?, ?, 'IMAGE_ANALYSIS', ?, ?)
                """, tenantId, patientId, fileId, userId, status,
                Timestamp.valueOf(on.atStartOfDay()));
    }

    private void assignPlan(String code) {
        billingService.assignPlan(new AssignPlanRequest(code, (short) 1));
    }

    @Test
    @DisplayName("The seeded plan catalogue is readable")
    void plansAreSeeded() {
        seedTenant();
        List<PlanView> plans = billingService.listPlans();

        assertThat(plans).extracting(PlanView::code)
                .containsExactlyInAnyOrder("PILOT", "VOLUME", "HOSPITAL");
        assertThat(plans).allSatisfy(p -> assertThat(p.currency()).isEqualTo("INR"));
    }

    @Test
    @DisplayName("Usage before a plan is assigned fails with a usable message")
    void requiresAPlan() {
        seedTenant();

        assertThatThrownBy(() -> billingService.currentUsage())
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no billing plan assigned");
    }

    /** 29-31 do not exist in every month; a silently sliding anchor is a February support ticket. */
    @Test
    @DisplayName("A billing day past 28 is rejected")
    void billingDayIsCapped() {
        seedTenant();

        assertThatThrownBy(() -> billingService.assignPlan(new AssignPlanRequest("VOLUME", (short) 31)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("between 1 and 28");
    }

    @Test
    @DisplayName("Per-study pricing charges every completed analysis")
    void perStudyPricing() {
        UUID tenantId = seedTenant();
        UUID user = seedUser(tenantId, LocalDate.now());
        UUID patient = seedPatient(tenantId);
        assignPlan("VOLUME");

        for (int i = 0; i < 4; i++) {
            seedAnalysis(tenantId, patient, user, "COMPLETED", LocalDate.now());
        }

        CurrentUsage usage = billingService.currentUsage();

        assertThat(usage.billableAnalyses()).isEqualTo(4);
        assertThat(usage.chargeableAnalyses()).isEqualTo(4);
        // 4 x Rs 35.00 = 140.00, plus 18% GST = 165.20
        assertThat(usage.projectedSubtotal()).isEqualByComparingTo("140.00");
        assertThat(usage.projectedTax()).isEqualByComparingTo("25.20");
        assertThat(usage.projectedTotal()).isEqualByComparingTo("165.20");
    }

    /** The first question a customer asks about an invoice. */
    @Test
    @DisplayName("A failed analysis is never billed")
    void failedAnalysesAreNotBilled() {
        UUID tenantId = seedTenant();
        UUID user = seedUser(tenantId, LocalDate.now());
        UUID patient = seedPatient(tenantId);
        assignPlan("VOLUME");

        seedAnalysis(tenantId, patient, user, "COMPLETED", LocalDate.now());
        seedAnalysis(tenantId, patient, user, "FAILED", LocalDate.now());
        seedAnalysis(tenantId, patient, user, "PENDING", LocalDate.now());

        assertThat(billingService.currentUsage().billableAnalyses()).isEqualTo(1);
    }

    @Test
    @DisplayName("Analyses outside the period are not counted")
    void periodBoundariesHold() {
        UUID tenantId = seedTenant();
        UUID user = seedUser(tenantId, LocalDate.now());
        UUID patient = seedPatient(tenantId);
        assignPlan("VOLUME");

        seedAnalysis(tenantId, patient, user, "COMPLETED", LocalDate.now());
        seedAnalysis(tenantId, patient, user, "COMPLETED", LocalDate.now().minusMonths(3));

        assertThat(billingService.currentUsage().billableAnalyses()).isEqualTo(1);
    }

    @Test
    @DisplayName("Included volume is consumed before overage is charged")
    void includedAllowanceIsConsumedFirst() {
        UUID tenantId = seedTenant();
        UUID user = seedUser(tenantId, LocalDate.now());
        UUID patient = seedPatient(tenantId);
        assignPlan("PILOT"); // 500 included, Rs 0 per analysis, no platform fee

        for (int i = 0; i < 3; i++) {
            seedAnalysis(tenantId, patient, user, "COMPLETED", LocalDate.now());
        }

        CurrentUsage usage = billingService.currentUsage();

        assertThat(usage.billableAnalyses()).isEqualTo(3);
        assertThat(usage.chargeableAnalyses()).as("well inside the included allowance").isZero();
        assertThat(usage.projectedTotal()).isEqualByComparingTo("0.00");
    }

    /** Charging for provisioned rather than active accounts invites a dispute over leavers. */
    @Test
    @DisplayName("Only clinicians who signed in during the period are counted as seats")
    void onlyActiveSeatsAreCharged() {
        UUID tenantId = seedTenant();
        UUID patient = seedPatient(tenantId);
        assignPlan("HOSPITAL"); // Rs 75,000 platform + Rs 1,500 per active seat

        UUID active = seedUser(tenantId, LocalDate.now());
        seedUser(tenantId, LocalDate.now().minusYears(1));  // a leaver
        seedUser(tenantId, null);                            // never signed in
        seedAnalysis(tenantId, patient, active, "COMPLETED", LocalDate.now());

        CurrentUsage usage = billingService.currentUsage();

        assertThat(usage.activeSeats()).isEqualTo(1);
        // 75,000 platform + 1,500 seat = 76,500; analyses are inside the 2,000 allowance.
        assertThat(usage.projectedSubtotal()).isEqualByComparingTo("76500.00");
        assertThat(usage.projectedTax()).isEqualByComparingTo("13770.00");
    }

    @Test
    @DisplayName("An invoice carries itemised lines and a human-readable number")
    void invoiceIsItemised() {
        UUID tenantId = seedTenant();
        UUID user = seedUser(tenantId, LocalDate.now().minusMonths(1));
        UUID patient = seedPatient(tenantId);
        assignPlan("VOLUME");

        BillingService.Period previous = billingService.previousPeriod((short) 1);
        for (int i = 0; i < 2; i++) {
            seedAnalysis(tenantId, patient, user, "COMPLETED", previous.start().plusDays(2));
        }

        InvoiceView invoice = billingService.generateForLastClosedPeriod();

        assertThat(invoice.invoiceNumber()).startsWith("INV-");
        assertThat(invoice.status()).isEqualTo("DRAFT");
        assertThat(invoice.currency()).isEqualTo("INR");
        assertThat(invoice.lineItems()).anySatisfy(line -> {
            assertThat(line.description()).isEqualTo("Analyses");
            assertThat(line.quantity()).isEqualByComparingTo("2");
            assertThat(line.amount()).isEqualByComparingTo("70.00");
        });
        assertThat(invoice.total()).isEqualByComparingTo("82.60");
    }

    @Test
    @DisplayName("Regenerating a period updates the draft rather than creating a second invoice")
    void regenerationIsIdempotent() {
        UUID tenantId = seedTenant();
        seedUser(tenantId, LocalDate.now());
        assignPlan("VOLUME");

        InvoiceView first = billingService.generateForLastClosedPeriod();
        InvoiceView second = billingService.generateForLastClosedPeriod();

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(billingService.listInvoices()).hasSize(1);
    }

    /** Reissuing a document an accounts department already has is how reconciliation breaks. */
    @Test
    @DisplayName("An issued invoice is never rewritten")
    void issuedInvoicesAreImmutable() {
        UUID tenantId = seedTenant();
        UUID user = seedUser(tenantId, LocalDate.now());
        UUID patient = seedPatient(tenantId);
        assignPlan("VOLUME");

        BillingService.Period previous = billingService.previousPeriod((short) 1);
        seedAnalysis(tenantId, patient, user, "COMPLETED", previous.start().plusDays(1));

        InvoiceView draft = billingService.generateForLastClosedPeriod();
        InvoiceView issued = billingService.issueInvoice(draft.id());
        assertThat(issued.status()).isEqualTo("ISSUED");

        // More usage lands after issuing; the issued invoice must not silently change.
        seedAnalysis(tenantId, patient, user, "COMPLETED", previous.start().plusDays(2));
        InvoiceView regenerated = billingService.generateForLastClosedPeriod();

        assertThat(regenerated.status()).isEqualTo("ISSUED");
        assertThat(regenerated.total()).isEqualByComparingTo(issued.total());
    }

    /**
     * cost_usd is what the AI provider charges us. It is recorded for margin and must never reach
     * the customer's invoice.
     */
    @Test
    @DisplayName("Provider cost is recorded for margin and kept off the invoice")
    void providerCostIsNotShownToTheCustomer() {
        UUID tenantId = seedTenant();
        seedUser(tenantId, LocalDate.now());
        assignPlan("VOLUME");

        BillingService.Period previous = billingService.previousPeriod((short) 1);
        jdbcTemplate.update("""
                INSERT INTO tenant_ai_usage_daily
                    (tenant_id, usage_date, request_count, prompt_tokens, completion_tokens, cost_usd, updated_at)
                VALUES (?, ?, 40, 1000, 2000, 12.345678, NOW())
                """, tenantId, previous.start().plusDays(1));

        InvoiceView invoice = billingService.generateForLastClosedPeriod();

        assertThat(invoice.lineItems()).noneSatisfy(line ->
                assertThat(line.description().toLowerCase()).contains("provider"));

        BigDecimal recorded = jdbcTemplate.queryForObject(
                "SELECT provider_cost_usd FROM invoices WHERE id = ?", BigDecimal.class, invoice.id());
        assertThat(recorded).isEqualByComparingTo("12.345678");
    }

    @Test
    @DisplayName("Invoices are tenant-scoped")
    void invoicesAreTenantScoped() {
        UUID tenantA = seedTenant();
        seedUser(tenantA, LocalDate.now());
        assignPlan("VOLUME");
        billingService.generateForLastClosedPeriod();

        seedTenant();
        assertThat(billingService.listInvoices()).isEmpty();
    }
}
