package com.medai.billing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tenant_subscriptions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TenantSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    /**
     * Day of month the period closes. Anchored per tenant so invoicing is not a month-end
     * thundering herd; capped at 28 so every month actually has the day.
     */
    @Column(name = "billing_day", nullable = false)
    @Builder.Default
    private Short billingDay = 1;

    @Column(name = "started_on", nullable = false)
    private LocalDate startedOn;

    @Column(name = "cancelled_on")
    private LocalDate cancelledOn;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
