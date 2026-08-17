package com.medai.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagResponse {
    private String query;
    private String answer;
    private List<CitationDto> citations;
    private List<String> suggestedFollowUps;
    private int totalSourcesRetrieved;
}
