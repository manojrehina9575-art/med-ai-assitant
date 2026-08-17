package com.medai.knowledge.dto;

import com.medai.knowledge.entity.DocumentStatus;
import com.medai.knowledge.entity.DocumentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocumentResponse {
    private UUID id;
    private String title;
    private DocumentType documentType;
    private String source;
    private String fileName;
    private Long fileSizeBytes;
    private Integer totalChunks;
    private DocumentStatus status;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
}
