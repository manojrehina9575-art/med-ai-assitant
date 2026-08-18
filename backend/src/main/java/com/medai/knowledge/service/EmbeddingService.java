package com.medai.knowledge.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Produces semantic embeddings for knowledge-base text.
 *
 * <p>This replaces a hash-based implementation that projected SHA-256 hashes of words, bigrams,
 * and character trigrams into a 1536-dimensional vector. That produced a well-formed vector, but
 * cosine distance between two such vectors measures literal token overlap rather than meaning — a
 * query about "metformin in CKD stage 4" could not retrieve a chunk about "dosing in renal
 * impairment", because none of the hashed tokens coincide. Every citation the UI showed, with its
 * confident similarity score, was close to arbitrary.
 *
 * <p>Embeddings now come from a sentence-transformer model running in-process via ONNX Runtime.
 * Nothing is sent to a third party, and there is no per-token cost.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

    /**
     * Dimension of the configured model's output. Must match the {@code vector(n)} column in
     * {@code document_chunks} — see V8. all-MiniLM-L6-v2 emits 384 dimensions.
     */
    public static final int EMBEDDING_DIMENSION = 384;

    private final TransformersEmbeddingModel embeddingModel;

    /**
     * Recorded on every chunk so a future model change can be detected rather than silently
     * mixing incompatible vector spaces in one index.
     */
    @Value("${app.knowledge.embedding-model-id:all-MiniLM-L6-v2}")
    private String modelId;

    /**
     * Fails startup rather than letting a model whose output does not match the database column
     * write vectors that would be rejected — or worse, silently truncated — at insert time.
     */
    @PostConstruct
    void verifyDimension() {
        int actual = embeddingModel.embed("dimension probe").length;
        if (actual != EMBEDDING_DIMENSION) {
            throw new IllegalStateException(
                    "Embedding model '" + modelId + "' produces " + actual + " dimensions, but document_chunks.embedding "
                    + "is vector(" + EMBEDDING_DIMENSION + "). Update EMBEDDING_DIMENSION and add a migration that "
                    + "alters the column and re-embeds every chunk.");
        }
        log.info("Embedding model '{}' ready — {} dimensions, running locally", modelId, actual);
    }

    public String modelId() {
        return modelId;
    }

    /** Embeds a single passage or query. */
    public float[] embedText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Cannot embed empty text");
        }
        return embeddingModel.embed(text);
    }

    /** Formats a vector as a pgvector literal, e.g. {@code [0.123,-0.456,...]}. */
    public String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder(embedding.length * 10 + 2);
        sb.append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format(Locale.US, "%.6f", embedding[i]));
        }
        sb.append(']');
        return sb.toString();
    }
}
