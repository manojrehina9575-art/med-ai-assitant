package com.medai.knowledge.service;

import com.medai.auth.security.UserPrincipal;
import com.medai.knowledge.dto.CitationDto;
import com.medai.knowledge.dto.RagQueryRequest;
import com.medai.knowledge.dto.RagResponse;
import com.medai.knowledge.repository.ChunkSimilarityProjection;
import com.medai.knowledge.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class RagService {

    private final DocumentChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final ChatClient.Builder chatClientBuilder;

    private static final String RAG_SYSTEM_PROMPT = """
            You are an expert Hospital Clinical Intelligence Assistant for Med-AI.
            Your role is to answer the healthcare practitioner's query accurately and objectively based on the hospital-approved medical protocols and clinical guidelines provided below.
            
            GUIDELINES & CONTEXT FROM HOSPITAL KNOWLEDGE BASE:
            ------------------------------------------------
            %s
            ------------------------------------------------
            
            INSTRUCTIONS:
            1. Rely primarily on the provided hospital guidelines to formulate your response.
            2. When referencing specific facts, dosing, protocols, or contraindications, cite the source using [Citation 1], [Citation 2], etc. matching the context sources above.
            3. If the hospital guidelines do not contain enough information to fully answer the query, clearly state what the guidelines cover, provide general evidence-based clinical guidance, and advise clinical discretion.
            4. Conclude your answer with a "Suggested Follow-ups" section containing 2-3 relevant clinical questions the practitioner might want to explore.
            """;

    public RagResponse queryKnowledgeBase(RagQueryRequest request, UserPrincipal principal) {
        log.info("Processing RAG query '{}' for tenant {}", request.getQuery(), principal.tenantId());

        int topK = (request.getTopK() != null && request.getTopK() > 0) ? Math.min(request.getTopK(), 10) : 4;

        // Generate query embedding
        float[] queryEmbedding = embeddingService.embedText(request.getQuery());
        String queryVectorStr = embeddingService.toVectorString(queryEmbedding);

        // Retrieve similar chunks
        List<ChunkSimilarityProjection> chunks = chunkRepository.findSimilarChunks(
                principal.tenantId(), queryVectorStr, topK);

        List<CitationDto> citations = new ArrayList<>();
        StringBuilder contextBuilder = new StringBuilder();

        if (chunks.isEmpty()) {
            contextBuilder.append("No hospital-specific documents are currently indexed in your knowledge base.\n");
        } else {
            for (int i = 0; i < chunks.size(); i++) {
                ChunkSimilarityProjection chunk = chunks.get(i);
                int citationIndex = i + 1;

                contextBuilder.append(String.format("[Citation %d] Document: %s (Type: %s, Chunk: %d)\n%s\n\n",
                        citationIndex,
                        chunk.getDocTitle(),
                        chunk.getDocType(),
                        chunk.getChunkIndex() + 1,
                        chunk.getContent()
                ));

                // Create excerpt (first 200 chars)
                String excerpt = chunk.getContent().length() > 200
                        ? chunk.getContent().substring(0, 200) + "..."
                        : chunk.getContent();

                citations.add(CitationDto.builder()
                        .documentId(chunk.getDocumentId())
                        .title(chunk.getDocTitle())
                        .documentType(chunk.getDocType())
                        .chunkIndex(chunk.getChunkIndex() + 1)
                        .excerpt(excerpt)
                        .similarityScore(chunk.getSimilarityScore())
                        .build());
            }
        }

        // Call Chat LLM with grounded prompt
        String prompt = String.format(RAG_SYSTEM_PROMPT, contextBuilder.toString());
        ChatClient chatClient = chatClientBuilder.build();

        String answer;
        try {
            answer = chatClient.prompt()
                    .system(prompt)
                    .user(request.getQuery())
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("Failed to generate RAG response: {}", e.getMessage(), e);
            answer = "Unable to process query at this time. Error: " + e.getMessage();
        }

        // Extract suggested follow-ups
        List<String> suggestedFollowUps = extractFollowUps(answer);

        return RagResponse.builder()
                .query(request.getQuery())
                .answer(answer)
                .citations(citations)
                .suggestedFollowUps(suggestedFollowUps)
                .totalSourcesRetrieved(citations.size())
                .build();
    }

    private List<String> extractFollowUps(String answer) {
        List<String> followUps = new ArrayList<>();
        if (answer == null) return followUps;

        int idx = answer.toLowerCase().indexOf("suggested follow-up");
        if (idx != -1) {
            String section = answer.substring(idx);
            String[] lines = section.split("\n");
            for (String line : lines) {
                String trimmed = line.replaceAll("^[-*•\\d.]+\\s*", "").trim();
                if (trimmed.endsWith("?") && trimmed.length() > 10) {
                    followUps.add(trimmed);
                }
            }
        }

        if (followUps.isEmpty()) {
            followUps.add("What are the recommended dosing adjustments for renal impairment?");
            followUps.add("What are the contraindications and monitoring requirements?");
        }

        return followUps;
    }
}
