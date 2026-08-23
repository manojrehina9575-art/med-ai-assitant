package com.medai.billing.controller;

import com.medai.billing.dto.BillingDtos.*;
import com.medai.billing.service.BillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Billing and usage.
 *
 * <p>Everything except the plan catalogue is HOSPITAL_ADMIN only. A doctor has no business reading
 * what their hospital is charged, and role-scoping the money is easier to defend than explaining
 * afterwards why a clinician could see the contract terms.
 */
@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
@Tag(name = "Billing", description = "Usage metering, plans and invoices")
public class BillingController {

    private final BillingService billingService;

    @GetMapping("/plans")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    @Operation(summary = "List available billing plans")
    public ResponseEntity<List<PlanView>> plans() {
        return ResponseEntity.ok(billingService.listPlans());
    }

    @PostMapping("/subscription")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    @Operation(summary = "Assign or change this hospital's billing plan")
    public ResponseEntity<Void> assignPlan(@Valid @RequestBody AssignPlanRequest request) {
        billingService.assignPlan(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Usage so far in the open period, with a projected total.
     *
     * <p>The reason this exists rather than only invoices: an overage-priced product whose usage
     * is visible only after the period closes produces a billing dispute every month.
     */
    @GetMapping("/usage/current")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    @Operation(summary = "Usage and projected charges for the current period")
    public ResponseEntity<CurrentUsage> currentUsage() {
        return ResponseEntity.ok(billingService.currentUsage());
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    @Operation(summary = "List this hospital's invoices, newest first")
    public ResponseEntity<List<InvoiceView>> invoices() {
        return ResponseEntity.ok(billingService.listInvoices());
    }

    @GetMapping("/invoices/{id}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    @Operation(summary = "Read one invoice with its line items")
    public ResponseEntity<InvoiceView> invoice(@PathVariable UUID id) {
        return ResponseEntity.ok(billingService.getInvoice(id));
    }

    @PostMapping("/invoices/generate")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    @Operation(summary = "Generate the invoice for the last closed period",
               description = "Regenerates the draft in place. An already-issued invoice is "
                             + "returned unchanged rather than rewritten.")
    public ResponseEntity<InvoiceView> generate() {
        return ResponseEntity.ok(billingService.generateForLastClosedPeriod());
    }

    @PostMapping("/invoices/{id}/issue")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    @Operation(summary = "Issue a draft invoice", description = "After this it is never rewritten.")
    public ResponseEntity<InvoiceView> issue(@PathVariable UUID id) {
        return ResponseEntity.ok(billingService.issueInvoice(id));
    }
}
