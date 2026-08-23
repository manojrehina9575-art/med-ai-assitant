package com.medai.chat.service;

import com.medai.analysis.entity.AnalysisRequest;
import com.medai.analysis.repository.AnalysisRequestRepository;
import com.medai.chat.dto.ChatCitationDto;
import com.medai.knowledge.repository.ChunkSimilarityProjection;
import com.medai.knowledge.repository.DocumentChunkRepository;
import com.medai.knowledge.service.EmbeddingService;
import com.medai.patient.entity.Patient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatContextBuilderService {

    private final DocumentChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final AnalysisRequestRepository analysisRepository;

    /**
     * @param systemPrompt  the assembled prompt sent to the model
     * @param citations     the retrieved protocol chunks, in citation order
     * @param groundingText every fact the model was actually given — patient context plus retrieved
     *                      protocol text. {@code ChatOutputGuardrailService} checks the model's
     *                      numeric claims against this: a dose that appears in the answer but
     *                      nowhere in here was not read off a hospital protocol, it was recalled.
     */
    public record BuiltContext(String systemPrompt, List<ChatCitationDto> citations, String groundingText) {

        /** True when at least one hospital protocol was retrieved for this turn. */
        public boolean hasProtocolGrounding() {
            return !citations.isEmpty();
        }
    }

    public BuiltContext buildContext(
            UUID tenantId,
            Patient patient,
            String latestUserQuery,
            boolean includeRag
    ) {
        StringBuilder sb = new StringBuilder();
        // Accumulates only the factual material — never the instructions — so the output guardrail
        // compares the model's claims against what it was told, not against its own prompt.
        StringBuilder grounding = new StringBuilder();
        sb.append("""
                You are Med-AI, a highly qualified Clinical Diagnostic Intelligence Assistant.
                Your role is to assist healthcare practitioners (physicians, radiologists, lab technicians) with medical reasoning, patient diagnostic synthesis, and guideline lookup.

                CORE CLINICAL PRINCIPLES:
                1. Provide evidence-based, accurate, structured medical information.
                2. Explicitly highlight urgent findings, contraindications, and potential drug-allergy interactions.
                3. Ground clinical answers in verified hospital guidelines when provided, citing sources as [Citation 1], [Citation 2].
                4. Maintain clear distinction between patient-specific observed data and general clinical recommendations.
                5. If critical data (e.g., vital signs, creatinine clearance, lab panels) is missing for a clinical decision, state what is missing.
                """);

        // 1. Inject Patient Context if available
        int patientContextStart = sb.length();
        if (patient != null) {
            sb.append("\n========================================\n");
            sb.append("PATIENT CLINICAL CONTEXT:\n");
            sb.append("----------------------------------------\n");
            sb.append("Patient Name: ").append(patient.getFullName()).append("\n");
            sb.append("MRN: ").append(patient.getMedicalRecordNumber()).append("\n");
            if (patient.getDateOfBirth() != null) {
                int age = Period.between(patient.getDateOfBirth(), LocalDate.now()).getYears();
                sb.append("Age: ").append(age).append(" (DOB: ").append(patient.getDateOfBirth()).append(")\n");
            }
            sb.append("Gender: ").append(patient.getGender()).append("\n");
            if (patient.getBloodGroup() != null) {
                sb.append("Blood Group: ").append(patient.getBloodGroup()).append("\n");
            }
            if (patient.getAllergies() != null && !patient.getAllergies().isEmpty()) {
                sb.append("⚠️ KNOWN ALLERGIES: ").append(String.join(", ", patient.getAllergies())).append("\n");
            } else {
                sb.append("Allergies: No known drug allergies recorded.\n");
            }
            if (patient.getMedicalHistory() != null && !patient.getMedicalHistory().isEmpty()) {
                sb.append("Past Medical History: ").append(String.join("; ", patient.getMedicalHistory())).append("\n");
            }

            // Fetch recent analyses for this patient
            try {
                var recentAnalyses = analysisRepository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(
                        tenantId, patient.getId(), PageRequest.of(0, 5)
                ).getContent();

                if (!recentAnalyses.isEmpty()) {
                    sb.append("\nRECENT DIAGNOSTIC & LAB STUDIES:\n");
                    for (AnalysisRequest ar : recentAnalyses) {
                        sb.append(String.format("• [%s] Type: %s | Status: %s | Urgency: %s",
                                ar.getCreatedAt() != null ? ar.getCreatedAt().toString() : "Recent",
                                ar.getAnalysisType(),
                                ar.getStatus(),
                                ar.getUrgency() != null ? ar.getUrgency() : "N/A"
                        ));
                        if (ar.getClinicalNotes() != null && !ar.getClinicalNotes().isBlank()) {
                            sb.append(" | Clinical Notes: ").append(ar.getClinicalNotes());
                        }
                        if (ar.getResult() != null && !ar.getResult().isBlank()) {
                            // Add concise snippet of result
                            String resSnippet = ar.getResult().length() > 300
                                    ? ar.getResult().substring(0, 300) + "..."
                                    : ar.getResult();
                            sb.append("\n  Findings/Result: ").append(resSnippet);
                        }
                        sb.append("\n");
                    }
                }
            } catch (Exception e) {
                log.warn("Could not load recent analyses for patient context: {}", e.getMessage());
            }
            sb.append("========================================\n\n");
            // Everything appended since the patient header is patient fact, and is grounding.
            grounding.append(sb.substring(patientContextStart));
        }

        // 2. Inject RAG Knowledge Base Chunks
        List<ChatCitationDto> citations = new ArrayList<>();
        if (includeRag && latestUserQuery != null && !latestUserQuery.isBlank()) {
            try {
                float[] queryEmbedding = embeddingService.embedText(latestUserQuery);
                String queryVectorStr = embeddingService.toVectorString(queryEmbedding);

                List<ChunkSimilarityProjection> chunks = chunkRepository.findSimilarChunks(
                        tenantId, queryVectorStr, 3
                );

                if (!chunks.isEmpty()) {
                    sb.append("\n========================================\n");
                    sb.append("HOSPITAL PROTOCOLS & CLINICAL GUIDELINES:\n");
                    sb.append("----------------------------------------\n");

                    for (int i = 0; i < chunks.size(); i++) {
                        ChunkSimilarityProjection chunk = chunks.get(i);
                        int citationIdx = i + 1;
                        sb.append(String.format("[Citation %d] Protocol: %s (Type: %s, Chunk: %d)\n%s\n\n",
                                citationIdx,
                                chunk.getDocTitle(),
                                chunk.getDocType(),
                                chunk.getChunkIndex() + 1,
                                chunk.getContent()
                        ));
                        grounding.append(chunk.getContent()).append('\n');

                        String excerpt = chunk.getContent().length() > 200
                                ? chunk.getContent().substring(0, 200) + "..."
                                : chunk.getContent();

                        citations.add(ChatCitationDto.builder()
                                .documentId(chunk.getDocumentId())
                                .title(chunk.getDocTitle())
                                .documentType(chunk.getDocType())
                                .chunkIndex(chunk.getChunkIndex() + 1)
                                .excerpt(excerpt)
                                .similarityScore(chunk.getSimilarityScore())
                                .build());
                    }
                    sb.append("========================================\n\n");
                }
            } catch (Exception e) {
                log.warn("RAG retrieval failed in chat context builder: {}", e.getMessage());
            }
        }

        sb.append("""
                RESPONSE GUIDELINES:
                - Use clear Markdown formatting with headers and bullet points for complex medical reasoning.
                - When hospital protocols are provided, cite them using [Citation 1], [Citation 2] where applicable.
                - Keep tone professional, analytical, and supportive of clinical decision-making.
                """);

        // The user's own words are grounding too: a dose the practitioner stated and the model
        // echoed back is not the model inventing one.
        if (latestUserQuery != null) {
            grounding.append('\n').append(latestUserQuery);
        }

        return new BuiltContext(sb.toString(), citations, grounding.toString());
    }
}
