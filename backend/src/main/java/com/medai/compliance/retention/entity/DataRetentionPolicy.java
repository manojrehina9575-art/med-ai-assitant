package com.medai.compliance.retention.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "data_retention_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataRetentionPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    /** Six years. See DataRetentionService.MIN_AUDIT_RETENTION_DAYS — this is a floor, not a default. */
    @Column(name = "audit_log_retention_days", nullable = false)
    @Builder.Default
    private int auditLogRetentionDays = 2190;

    @Column(name = "analysis_retention_days", nullable = false)
    @Builder.Default
    private int analysisRetentionDays = 730;

    @Column(name = "chat_session_retention_days", nullable = false)
    @Builder.Default
    private int chatSessionRetentionDays = 180;

    @Column(name = "soft_delete_purge_days", nullable = false)
    @Builder.Default
    private int softDeletePurgeDays = 30;

    @Column(name = "is_auto_purge_enabled", nullable = false)
    @Builder.Default
    private boolean autoPurgeEnabled = false;

    @Column(name = "last_purge_at")
    private Instant lastPurgeAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
