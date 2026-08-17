package com.medai.knowledge.repository;

import java.util.UUID;

public interface ChunkSimilarityProjection {
    UUID getId();
    UUID getDocumentId();
    UUID getTenantId();
    Integer getChunkIndex();
    String getContent();
    String getMetadata();
    String getDocTitle();
    String getDocType();
    Double getSimilarityScore();
}
