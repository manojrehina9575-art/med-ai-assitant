package com.medai.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AnalysisResultDto {

    private List<Finding> findings;
    private String impression;
    private List<String> icd10Codes;
    private List<String> recommendations;
    private String urgency;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Finding {
        private String region;
        private String description;
        private String severity;
        private Double confidence;
    }
}
