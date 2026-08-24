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

    /**
     * The model declined to interpret the study.
     *
     * <p>Every input used to get an answer, which is the failure mode most likely to end up in a
     * case report: an unreadable film or a study outside the validated scope produced confident
     * findings anyway. A system that can say "I cannot read this" is trusted more, not less.
     */
    private Boolean abstained;

    /** Required when abstained is true — "image quality insufficient", "no prior for comparison". */
    private String abstentionReason;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Finding {
        private String region;
        private String description;
        private String severity;
        private Double confidence;
    }
}
