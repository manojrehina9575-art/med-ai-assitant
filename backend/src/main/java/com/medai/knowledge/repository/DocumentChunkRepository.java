package com.medai.knowledge.repository;

import com.medai.knowledge.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    List<DocumentChunk> findByKnowledgeDocument_IdAndTenantIdOrderByChunkIndexAsc(UUID documentId, UUID tenantId);

    void deleteByKnowledgeDocument_IdAndTenantId(UUID documentId, UUID tenantId);

    @Query(value = """
            SELECT c.id as id,
                   c.document_id as documentId,
                   c.tenant_id as tenantId,
                   c.chunk_index as chunkIndex,
                   c.content as content,
                   c.metadata as metadata,
                   d.title as docTitle,
                   d.document_type as docType,
                   1 - (c.embedding <=> cast(:queryVector as vector)) as similarityScore
            FROM document_chunks c
            JOIN knowledge_documents d ON c.document_id = d.id
            WHERE c.tenant_id = :tenantId AND c.embedding IS NOT NULL
            ORDER BY c.embedding <=> cast(:queryVector as vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<ChunkSimilarityProjection> findSimilarChunks(
            @Param("tenantId") UUID tenantId,
            @Param("queryVector") String queryVector,
            @Param("topK") int topK
    );
}
