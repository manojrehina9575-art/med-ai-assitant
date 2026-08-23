package com.medai.notification.entity;

import com.medai.common.entity.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification extends TenantAwareEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Notification category. Values: ANALYSIS_COMPLETE, CRITICAL_FINDING, WORKFLOW_UPDATE,
     * SYSTEM, INFO
     */
    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    /** INFO | WARNING | CRITICAL */
    @Column(name = "severity", nullable = false, length = 20)
    @Builder.Default
    private String severity = "INFO";

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean isRead = false;

    /** Entity type this notification relates to (e.g. "ANALYSIS", "PATIENT", "WORKFLOW") */
    @Column(name = "related_entity_type", length = 50)
    private String relatedEntityType;

    /** Primary key of the related entity */
    @Column(name = "related_entity_id")
    private UUID relatedEntityId;
}
