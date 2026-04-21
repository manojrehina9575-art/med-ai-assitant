package com.medai.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BloodReportResultDto {

    private String testName;
    private List<Parameter> parameters;
    private String interpretation;
    private List<String> flags;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Parameter {
        private String name;
        private Double value;
        private String unit;
        private String referenceRange;
        private String flag; // NORMAL, HIGH, LOW, CRITICAL_HIGH, CRITICAL_LOW
    }
}
