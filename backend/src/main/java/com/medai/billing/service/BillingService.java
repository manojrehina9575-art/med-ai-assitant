package com.medai.billing.service;

import com.medai.billing.dto.BillingDtos.*;
import com.medai.billing.entity.*;
import com.medai.billing.repository.*;
import com.medai.common.exception.BadRequestException;
import com.medai.common.exception.ResourceNotFoundException;
import com.medai.tenant.TenantContext;
import com.medai.tenant.TenantSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Turns metered usage into invoices.
 *
 * <p>{@code tenant_ai_usage_daily} has held accurate per-tenant token counts and provider cost
 * since V10 and nothing ever read it for money. This is the piece that closes that.
 *
 * <p>Two decisions worth stating, because both are easy to get subtly wrong and expensive to
 * correct once a customer has been invoiced:
 *
 * <ul>
 *   <li><strong>The billable unit is a completed analysis, not an AI request.</strong> A chat turn
 *       and a chest X-ray report are both one AI request and are worth very different amounts.
 *       Diagnostic chains already think in studies, so that is what they are billed for — and a
 *       failed analysis is never billed, which is a question a customer will otherwise ask on the
 *       first invoice.</li>
 *   <li><strong>Provider cost is not price.</strong> {@code cost_usd} is what the AI provider
 *       charges us. It is recorded on the invoice for margin visibility and never appears on
 *       anything the customer sees.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {

    private final BillingPlanRepository planRepository;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineItemRepository lineItemRepository;
    private final UsageRollupRepository usageRepository;
    private final TenantSession tenantSession;

    // ── Plans and subscription ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PlanView> listPlans() {
        return planRepository.findByIsActiveTrueOrderByPlatformFeeAsc().stream()
                .map(this::toPlanView)
                .toList();
    }

    @Transactional
    public TenantSubscription assignPlan(AssignPlanRequest request) {
        UUID tenantId = TenantContext.requireTenantId();

        BillingPlan plan = planRepository.findByCode(request.planCode())
                .orElseThrow(() -> new ResourceNotFoundException("BillingPlan", "code", request.planCode()));

        short billingDay = request.billingDay() == null ? 1 : request.billingDay();
        if (billingDay < 1 || billingDay > 28) {
            // 29-31 do not exist in every month, and a billing anchor that silently slides is a
            // support ticket every February.
            throw new BadRequestException("billingDay must be between 1 and 28.");
        }

        TenantSubscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElseGet(() -> TenantSubscription.builder()
                        .tenantId(tenantId)
                        .startedOn(LocalDate.now())
                        .build());

        subscription.setPlanId(plan.getId());
        subscription.setBillingDay(billingDay);
        subscription.setStatus("ACTIVE");
        subscription.setCancelledOn(null);

        return subscriptionRepository.save(subscription);
    }

    // ── Current period ───────────────────────────────────────────────────────

    /**
     * What the open period has consumed and what it would cost if it closed now.
     *
     * <p>Exists so nobody is surprised by an invoice. An overage-priced product where usage is
     * only visible after the fact is one that generates a billing dispute per month.
     */
    @Transactional(readOnly = true)
    public CurrentUsage currentUsage() {
        UUID tenantId = TenantContext.requireTenantId();
        Subscription context = resolveSubscription(tenantId);
        Period period = currentPeriod(context.subscription().getBillingDay());

        Rollup rollup = rollup(tenantId, period);
        Charges charges = price(context.plan(), rollup);

        return new CurrentUsage(
                context.plan().getCode(),
                context.plan().getDisplayName(),
                context.plan().getCurrency(),
                period.start(),
                period.endExclusive().minusDays(1),
                rollup.analyses(),
                context.plan().getIncludedAnalyses(),
                charges.chargeableAnalyses(),
                rollup.activeSeats(),
                rollup.aiRequests(),
                charges.subtotal(),
                charges.tax(),
                charges.total());
    }

    // ── Invoicing ────────────────────────────────────────────────────────────

    /**
     * Builds, or rebuilds, the invoice for a closed period.
     *
     * <p>Regenerating replaces the draft in place rather than adding a second invoice for the same
     * period, which the unique constraint on (tenant, period) also enforces. An invoice that has
     * been issued is never rewritten — reissuing a document an accounts department already has is
     * how reconciliation breaks.
     */
    @Transactional
    public InvoiceView generateInvoice(UUID tenantId, Period period) {
        Subscription context = resolveSubscription(tenantId);
        Rollup rollup = rollup(tenantId, period);
        Charges charges = price(context.plan(), rollup);

        Invoice invoice = invoiceRepository
                .findByTenantIdAndPeriodStartAndPeriodEnd(tenantId, period.start(), period.endInclusive())
                .orElseGet(() -> Invoice.builder()
                        .tenantId(tenantId)
                        .invoiceNumber(nextInvoiceNumber())
                        .periodStart(period.start())
                        .periodEnd(period.endInclusive())
                        .build());

        if ("ISSUED".equals(invoice.getStatus()) || "PAID".equals(invoice.getStatus())) {
            log.info("Invoice {} is already {}; not regenerating.", invoice.getInvoiceNumber(), invoice.getStatus());
            return toInvoiceView(invoice);
        }

        invoice.setPlanCode(context.plan().getCode());
        invoice.setCurrency(context.plan().getCurrency());
        invoice.setSubtotal(charges.subtotal());
        invoice.setTaxRatePercent(context.plan().getTaxRatePercent());
        invoice.setTaxAmount(charges.tax());
        invoice.setTotal(charges.total());
        invoice.setProviderCostUsd(rollup.providerCostUsd());
        invoice.setStatus("DRAFT");

        Invoice saved = invoiceRepository.save(invoice);

        lineItemRepository.deleteByInvoiceId(saved.getId());
        saved.setInvoiceNumber(saved.getInvoiceNumber());
        persistLineItems(saved, context.plan(), rollup, charges);

        log.info("Invoice {} for tenant {} covering {}..{}: {} {} (provider cost ${})",
                saved.getInvoiceNumber(), tenantId, period.start(), period.endInclusive(),
                charges.total(), context.plan().getCurrency(), rollup.providerCostUsd());

        return toInvoiceView(saved);
    }

    @Transactional(readOnly = true)
    public List<InvoiceView> listInvoices() {
        UUID tenantId = TenantContext.requireTenantId();
        return invoiceRepository.findByTenantIdOrderByPeriodStartDesc(tenantId).stream()
                .map(this::toInvoiceView)
                .toList();
    }

    @Transactional(readOnly = true)
    public InvoiceView getInvoice(UUID invoiceId) {
        UUID tenantId = TenantContext.requireTenantId();
        return invoiceRepository.findByIdAndTenantId(invoiceId, tenantId)
                .map(this::toInvoiceView)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", invoiceId.toString()));
    }

    /** Generates the invoice for the period that just closed, for the current tenant. */
    @Transactional
    public InvoiceView generateForLastClosedPeriod() {
        UUID tenantId = TenantContext.requireTenantId();
        Subscription context = resolveSubscription(tenantId);
        return generateInvoice(tenantId, previousPeriod(context.subscription().getBillingDay()));
    }

    // ── Scheduled run ────────────────────────────────────────────────────────

    /**
     * Invoices every tenant whose period closes today.
     *
     * <p>Anchored per tenant rather than run for everyone on the first of the month, so a large
     * customer base spreads across the month instead of arriving as one nightly spike.
     */
    @Scheduled(cron = "${app.billing.cron:0 0 3 * * *}")
    public void runScheduledInvoicing() {
        short today = (short) LocalDate.now().getDayOfMonth();
        if (today > 28) {
            // Billing days are capped at 28, so there is never anything anchored here.
            return;
        }

        List<TenantSubscription> due = findSubscriptionsDue(today);
        log.info("Billing run for day {}: {} subscription(s) due", today, due.size());

        for (TenantSubscription subscription : due) {
            try {
                TenantContext.setCurrentTenantId(subscription.getTenantId());
                generateInvoice(subscription.getTenantId(), previousPeriod(subscription.getBillingDay()));
            } catch (Exception e) {
                // One tenant's bad data must not stop everyone else being invoiced.
                log.error("Invoicing failed for tenant {}: {}", subscription.getTenantId(), e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }

    /**
     * Its own transaction with maintenance access: the scan spans every tenant, which row-level
     * security correctly forbids by default.
     */
    @Transactional(readOnly = true)
    public List<TenantSubscription> findSubscriptionsDue(short billingDay) {
        tenantSession.beginMaintenance();
        return subscriptionRepository.findByStatusAndBillingDay("ACTIVE", billingDay);
    }

    // ── Pricing ──────────────────────────────────────────────────────────────

    /** A billing period, half-open internally and inclusive when shown to a human. */
    public record Period(LocalDate start, LocalDate endExclusive) {
        public LocalDate endInclusive() {
            return endExclusive.minusDays(1);
        }
    }

    private record Rollup(long analyses, long activeSeats, long aiRequests, BigDecimal providerCostUsd) {
    }

    private record Charges(long chargeableAnalyses, BigDecimal analysesAmount, BigDecimal seatsAmount,
                           BigDecimal subtotal, BigDecimal tax, BigDecimal total) {
    }

    private record Subscription(TenantSubscription subscription, BillingPlan plan) {
    }

    private Rollup rollup(UUID tenantId, Period period) {
        return new Rollup(
                usageRepository.countBillableAnalyses(tenantId, period.start(), period.endExclusive()),
                usageRepository.countActiveSeats(tenantId, period.start(), period.endExclusive()),
                usageRepository.sumAiRequests(tenantId, period.start(), period.endExclusive()),
                orZero(usageRepository.sumProviderCost(tenantId, period.start(), period.endExclusive())));
    }

    private Charges price(BillingPlan plan, Rollup rollup) {
        long chargeable = Math.max(0, rollup.analyses() - plan.getIncludedAnalyses());

        BigDecimal analysesAmount = plan.getPricePerAnalysis()
                .multiply(BigDecimal.valueOf(chargeable))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal seatsAmount = plan.getPricePerActiveSeat()
                .multiply(BigDecimal.valueOf(rollup.activeSeats()))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal subtotal = plan.getPlatformFee()
                .add(analysesAmount)
                .add(seatsAmount)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal tax = subtotal
                .multiply(plan.getTaxRatePercent())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        return new Charges(chargeable, analysesAmount, seatsAmount, subtotal, tax, subtotal.add(tax));
    }

    private void persistLineItems(Invoice invoice, BillingPlan plan, Rollup rollup, Charges charges) {
        List<InvoiceLineItem> items = new ArrayList<>();
        short order = 0;

        if (plan.getPlatformFee().signum() > 0) {
            items.add(line(invoice, "Platform fee — " + plan.getDisplayName(),
                    BigDecimal.ONE, plan.getPlatformFee(), plan.getPlatformFee(), order++));
        }

        if (plan.getIncludedAnalyses() > 0) {
            // A zero-amount line, deliberately: the customer should see what their fee covered and
            // how much of it they used, not just the overage that resulted.
            items.add(line(invoice,
                    String.format("Included analyses (%d of %d used)",
                            Math.min(rollup.analyses(), plan.getIncludedAnalyses()), plan.getIncludedAnalyses()),
                    BigDecimal.valueOf(Math.min(rollup.analyses(), plan.getIncludedAnalyses())),
                    BigDecimal.ZERO, BigDecimal.ZERO, order++));
        }

        if (charges.chargeableAnalyses() > 0) {
            items.add(line(invoice,
                    plan.getIncludedAnalyses() > 0 ? "Additional analyses" : "Analyses",
                    BigDecimal.valueOf(charges.chargeableAnalyses()),
                    plan.getPricePerAnalysis(), charges.analysesAmount(), order++));
        }

        if (plan.getPricePerActiveSeat().signum() > 0 && rollup.activeSeats() > 0) {
            items.add(line(invoice, "Active clinician seats",
                    BigDecimal.valueOf(rollup.activeSeats()),
                    plan.getPricePerActiveSeat(), charges.seatsAmount(), order++));
        }

        lineItemRepository.saveAll(items);
    }

    private InvoiceLineItem line(Invoice invoice, String description, BigDecimal quantity,
                                 BigDecimal unitPrice, BigDecimal amount, short order) {
        return InvoiceLineItem.builder()
                .tenantId(invoice.getTenantId())
                .invoiceId(invoice.getId())
                .description(description)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .amount(amount)
                .sortOrder(order)
                .build();
    }

    // ── Periods ──────────────────────────────────────────────────────────────

    /** The open period: from the most recent anchor date up to the next one. */
    public Period currentPeriod(short billingDay) {
        LocalDate today = LocalDate.now();
        LocalDate anchorThisMonth = today.withDayOfMonth(billingDay);
        LocalDate start = today.isBefore(anchorThisMonth) ? anchorThisMonth.minusMonths(1) : anchorThisMonth;
        return new Period(start, start.plusMonths(1));
    }

    /** The period that closed most recently — what a billing run invoices for. */
    public Period previousPeriod(short billingDay) {
        Period current = currentPeriod(billingDay);
        return new Period(current.start().minusMonths(1), current.start());
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private Subscription resolveSubscription(UUID tenantId) {
        TenantSubscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new BadRequestException(
                        "This hospital has no billing plan assigned. Assign one via "
                        + "POST /api/billing/subscription before invoicing."));

        BillingPlan plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingPlan", "id", subscription.getPlanId().toString()));

        return new Subscription(subscription, plan);
    }

    private String nextInvoiceNumber() {
        return "INV-" + LocalDate.now().getYear() + "-" + invoiceRepository.nextInvoiceNumber();
    }

    private InvoiceView toInvoiceView(Invoice invoice) {
        List<LineItem> items = lineItemRepository.findByInvoiceIdOrderBySortOrderAsc(invoice.getId()).stream()
                .map(i -> new LineItem(i.getDescription(), i.getQuantity(), i.getUnitPrice(), i.getAmount()))
                .toList();

        return new InvoiceView(
                invoice.getId(), invoice.getInvoiceNumber(), invoice.getPlanCode(), invoice.getCurrency(),
                invoice.getPeriodStart(), invoice.getPeriodEnd(), items,
                invoice.getSubtotal(), invoice.getTaxRatePercent(), invoice.getTaxAmount(),
                invoice.getTotal(), invoice.getStatus(), invoice.getIssuedAt());
    }

    private PlanView toPlanView(BillingPlan plan) {
        return new PlanView(plan.getCode(), plan.getDisplayName(), plan.getCurrency(),
                plan.getPlatformFee(), plan.getIncludedAnalyses(), plan.getPricePerAnalysis(),
                plan.getPricePerActiveSeat(), plan.getTaxRatePercent());
    }

    private BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Marks a draft as issued. Once issued it is never rewritten. */
    @Transactional
    public InvoiceView issueInvoice(UUID invoiceId) {
        UUID tenantId = TenantContext.requireTenantId();
        Invoice invoice = invoiceRepository.findByIdAndTenantId(invoiceId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", invoiceId.toString()));

        if (!"DRAFT".equals(invoice.getStatus())) {
            throw new BadRequestException("Only a draft invoice can be issued; this one is "
                                          + invoice.getStatus() + ".");
        }

        invoice.setStatus("ISSUED");
        invoice.setIssuedAt(Instant.now());
        return toInvoiceView(invoiceRepository.save(invoice));
    }
}
