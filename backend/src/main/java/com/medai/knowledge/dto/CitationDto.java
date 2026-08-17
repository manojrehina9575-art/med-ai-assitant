package com.medai.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitationDto {
    private UUID documentId;
    private String title;
    private String documentType;
    private Integer chunkIndex;
    private String excerpt;
    private Double similarityScore;
}
