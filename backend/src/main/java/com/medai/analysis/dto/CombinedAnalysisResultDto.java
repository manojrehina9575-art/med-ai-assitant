package com.medai.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CombinedAnalysisResultDto {

    private String overallAssessment;
    private String clinicalCorrelation;
    private List<DiagnosisRecommendation> diagnoses;
    private List<String> criticalFindings;
    private List<String> recommendations;
    private String urgency;
    private Double confidenceScore;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DiagnosisRecommendation {
        private String diagnosis;
        private String icd10Code;
        private Double confidence;
        private List<String> supportingEvidence;
    }
}
