package com.medai.finetuning.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_models_registry")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiModelRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId; // NULL for global models

    @Column(name = "model_id", nullable = false, length = 100)
    private String modelId;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "base_model", nullable = false, length = 100)
    private String baseModel;

    @Column(name = "adapter_type", nullable = false, length = 50)
    @Builder.Default
    private String adapterType = "LORA"; // LORA, QLORA, FULL_FINETUNE, SYSTEM_PROMPT

    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = "READY"; // REGISTERED, TRAINING, READY, DEPLOYED, ARCHIVED

    @Column(name = "lora_rank")
    private Integer loraRank;

    @Column(name = "lora_alpha")
    private Integer loraAlpha;

    @Column(name = "training_loss")
    private Double trainingLoss;

    @Column(name = "training_samples_count")
    @Builder.Default
    private Integer trainingSamplesCount = 0;

    @Column(name = "endpoint_url")
    private String endpointUrl;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
