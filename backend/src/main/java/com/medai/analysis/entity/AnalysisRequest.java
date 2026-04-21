package com.medai.analysis.entity;

import com.medai.analysis.enums.AnalysisStatus;
import com.medai.analysis.enums.AnalysisType;
import com.medai.common.entity.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analysis_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AnalysisRequest extends TenantAwareEntity {

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "medical_file_id", nullable = false)
    private UUID medicalFileId;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_type", nullable = false)
    private AnalysisType analysisType;

    @Column(name = "clinical_notes", columnDefinition = "TEXT")
    private String clinicalNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AnalysisStatus status;

    @Column(name = "urgency")
    private String urgency;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", columnDefinition = "jsonb")
    private String result;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "model_used")
    private String modelUsed;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "estimated_cost", precision = 10, scale = 6)
    private BigDecimal estimatedCost;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "processing_completed_at")
    private Instant processingCompletedAt;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    @Builder.Default
    private Integer maxRetries = 3;
}
