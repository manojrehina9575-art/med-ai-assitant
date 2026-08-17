package com.medai.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

@Service
@Slf4j
public class EmbeddingService {

    public static final int EMBEDDING_DIMENSION = 1536;

    /**
     * Generates a 1536-dimensional vector for text.
     * Uses feature hashing and character n-gram projection to generate dense, normalized semantic vectors.
     */
    public float[] embedText(String text) {
        if (text == null || text.isBlank()) {
            float[] zeros = new float[EMBEDDING_DIMENSION];
            return zeros;
        }

        float[] vector = new float[EMBEDDING_DIMENSION];
        String cleaned = text.toLowerCase().trim();
        String[] words = cleaned.split("\\s+");

        // Unigrams & Bigrams projection
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            hashAndAccumulate(word, vector, 1.0f);

            if (i < words.length - 1) {
                String bigram = word + "_" + words[i + 1];
                hashAndAccumulate(bigram, vector, 1.5f);
            }
        }

        // Substring 3-grams
        for (int i = 0; i <= cleaned.length() - 3; i += 2) {
            String trigram = cleaned.substring(i, i + 3);
            hashAndAccumulate(trigram, vector, 0.5f);
        }

        // Normalize vector to unit length (L2 norm)
        float norm = 0.0f;
        for (float v : vector) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);

        if (norm > 0.0f) {
            for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
                vector[i] = vector[i] / norm;
            }
        }

        return vector;
    }

    /**
     * Formats float array into PostgreSQL vector format string e.g. "[0.123, -0.456, ...]"
     */
    public String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            sb.append(String.format(java.util.Locale.US, "%.6f", embedding[i]));
            if (i < embedding.length - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private void hashAndAccumulate(String token, float[] vector, float weight) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(token.getBytes(StandardCharsets.UTF_8));

            for (int j = 0; j < 4; j++) {
                int index = ((hash[j * 4] & 0xFF) << 24
                        | (hash[j * 4 + 1] & 0xFF) << 16
                        | (hash[j * 4 + 2] & 0xFF) << 8
                        | (hash[j * 4 + 3] & 0xFF)) & 0x7FFFFFFF;
                int slot = index % EMBEDDING_DIMENSION;
                float sign = (hash[j] & 0x01) == 0 ? 1.0f : -1.0f;
                vector[slot] += sign * weight;
            }
        } catch (NoSuchAlgorithmException e) {
            int hash = token.hashCode();
            int slot = Math.abs(hash) % EMBEDDING_DIMENSION;
            vector[slot] += weight;
        }
    }
}
