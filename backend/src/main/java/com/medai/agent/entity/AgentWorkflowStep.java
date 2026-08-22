package com.medai.agent.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_workflow_steps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentWorkflowStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "step_index", nullable = false)
    private Integer stepIndex;

    @Column(name = "tool_name", nullable = false, length = 100)
    private String toolName;

    @Column(name = "action_summary", nullable = false, length = 500)
    private String actionSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_payload", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private String inputPayload = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_payload", columnDefinition = "jsonb")
    @Builder.Default
    private String outputPayload = "{}";

    @Column(name = "requires_confirmation", nullable = false)
    @Builder.Default
    private Boolean requiresConfirmation = false;

    @Column(name = "confirmation_status", nullable = false, length = 50)
    @Builder.Default
    private String confirmationStatus = "NOT_REQUIRED";

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
