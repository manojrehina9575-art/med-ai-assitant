-- ===========================================
-- V8: Replace hash-based vectors with semantic embeddings
-- ===========================================
-- The previous EmbeddingService projected SHA-256 hashes of words, bigrams, and character
-- trigrams into a vector(1536). Cosine distance over those vectors measures literal token
-- overlap, not meaning, so retrieval returned near-arbitrary chunks with confident-looking
-- similarity scores.
--
-- Embeddings now come from a local sentence-transformer model (all-MiniLM-L6-v2, 384 dims).
-- Old vectors are not comparable to new ones and cannot be converted — the affected documents
-- must be re-uploaded so their text is re-embedded.

-- Existing chunks carry incomparable vectors; keeping them would poison every search.
DELETE FROM document_chunks;

ALTER TABLE document_chunks DROP COLUMN embedding;
ALTER TABLE document_chunks ADD COLUMN embedding vector(384);

-- Records which model produced each vector, so a future model change is detectable rather than
-- silently mixing incompatible vector spaces in a single index.
ALTER TABLE document_chunks ADD COLUMN embedding_model VARCHAR(100);

-- The embedding column had no index at all: every RAG query was a sequential scan with a full
-- distance computation per row. HNSW with cosine ops matches the `<=>` operator used by
-- DocumentChunkRepository.findSimilarChunks.
CREATE INDEX idx_doc_chunks_embedding_hnsw
    ON document_chunks USING hnsw (embedding vector_cosine_ops);

-- Surface the re-upload requirement in the UI instead of leaving documents that claim to be
-- indexed but have no chunks behind them.
UPDATE knowledge_documents
   SET status = 'FAILED',
       total_chunks = 0,
       error_message = 'Re-upload required: the knowledge base was upgraded to semantic embeddings '
                       || 'and this document''s index could not be converted.'
 WHERE status = 'READY';
