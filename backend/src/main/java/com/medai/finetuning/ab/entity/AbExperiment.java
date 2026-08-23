package com.medai.finetuning.ab.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ab_experiments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbExperiment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "model_a_id", nullable = false, length = 100)
    private String modelAId; // Base model (e.g. qwen/qwen3.6-27b)

    @Column(name = "model_b_id", nullable = false, length = 100)
    private String modelBId; // Variant / Fine-tuned model (e.g. lora-radiology-xray-v1)

    @Column(name = "traffic_split_percent", nullable = false)
    @Builder.Default
    private int trafficSplitPercent = 50; // % routed to Model B (0-100)

    @Column(name = "modality", nullable = false, length = 50)
    @Builder.Default
    private String modality = "ALL"; // ALL, RADIOLOGY, BLOOD_LAB, CHAT

    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, PAUSED, COMPLETED

    @Column(name = "start_date", nullable = false)
    @Builder.Default
    private Instant startDate = Instant.now();

    @Column(name = "end_date")
    private Instant endDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
