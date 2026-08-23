package com.medai.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Billing API payloads. */
public final class BillingDtos {

    private BillingDtos() {
    }

    /**
     * What a tenant has consumed in the current open period, and what it would cost if the period
     * closed now. The point is that a customer is never surprised by an invoice.
     */
    public record CurrentUsage(
            String planCode,
            String planName,
            String currency,
            LocalDate periodStart,
            LocalDate periodEnd,
            long billableAnalyses,
            int includedAnalyses,
            long chargeableAnalyses,
            long activeSeats,
            long aiRequests,
            BigDecimal projectedSubtotal,
            BigDecimal projectedTax,
            BigDecimal projectedTotal
    ) {
    }

    public record LineItem(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {
    }

    public record InvoiceView(
            UUID id,
            String invoiceNumber,
            String planCode,
            String currency,
            LocalDate periodStart,
            LocalDate periodEnd,
            List<LineItem> lineItems,
            BigDecimal subtotal,
            BigDecimal taxRatePercent,
            BigDecimal taxAmount,
            BigDecimal total,
            String status,
            Instant issuedAt
    ) {
    }

    public record PlanView(
            String code,
            String displayName,
            String currency,
            BigDecimal platformFee,
            int includedAnalyses,
            BigDecimal pricePerAnalysis,
            BigDecimal pricePerActiveSeat,
            BigDecimal taxRatePercent
    ) {
    }

    public record AssignPlanRequest(String planCode, Short billingDay) {
    }
}
