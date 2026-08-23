package com.medai.billing.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "invoice_line_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InvoiceLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Short sortOrder = 0;
}
