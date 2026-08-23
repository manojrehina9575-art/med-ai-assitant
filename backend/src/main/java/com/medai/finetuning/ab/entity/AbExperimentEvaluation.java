package com.medai.finetuning.ab.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ab_experiment_evaluations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbExperimentEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "experiment_id", nullable = false)
    private UUID experimentId;

    @Column(name = "assigned_variant", nullable = false, length = 10)
    private String assignedVariant; // "A" or "B"

    @Column(name = "model_used", nullable = false, length = 100)
    private String modelUsed;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(name = "user_rating")
    private Integer userRating; // 1 to 5 stars

    @Column(name = "is_accurate")
    private Boolean accurate;

    @Column(name = "feedback_notes", columnDefinition = "TEXT")
    private String feedbackNotes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
