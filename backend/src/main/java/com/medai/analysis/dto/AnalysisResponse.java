package com.medai.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AnalysisResponse {

    private UUID id;
    private UUID patientId;
    private UUID medicalFileId;
    private UUID requestedBy;
    private String analysisType;
    private String clinicalNotes;
    private String status;
    private String urgency;
    private AnalysisResultDto result;
    private String errorMessage;
    private String modelUsed;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private BigDecimal estimatedCost;
    private Instant processingStartedAt;
    private Instant processingCompletedAt;
    private Integer retryCount;
    private Instant createdAt;
    private Instant updatedAt;
}
