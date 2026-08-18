package com.medai.knowledge.service;

import com.medai.auth.security.UserPrincipal;
import com.medai.knowledge.entity.DocumentChunk;
import com.medai.knowledge.entity.DocumentStatus;
import com.medai.knowledge.entity.DocumentType;
import com.medai.knowledge.entity.KnowledgeDocument;
import com.medai.knowledge.repository.DocumentChunkRepository;
import com.medai.knowledge.repository.KnowledgeDocumentRepository;
import com.medai.upload.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final KnowledgeDocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final StorageService storageService;
    private final EmbeddingService embeddingService;

    private static final int CHUNK_SIZE = 800;
    private static final int CHUNK_OVERLAP = 150;

    @Transactional
    public KnowledgeDocument ingestDocument(
            MultipartFile file,
            String title,
            DocumentType documentType,
            String source,
            UserPrincipal principal
    ) {
        log.info("Ingesting knowledge document '{}' (type: {}) for tenant {}",
                title, documentType, principal.tenantId());

        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setTenantId(principal.tenantId());
        doc.setTitle(title != null && !title.isBlank() ? title : file.getOriginalFilename());
        doc.setDocumentType(documentType != null ? documentType : DocumentType.GUIDELINE);
        doc.setSource(source);
        doc.setFileName(file.getOriginalFilename());
        doc.setFileSizeBytes(file.getSize());
        doc.setMimeType(file.getContentType());
        doc.setCreatedBy(principal.userId());
        doc.setStatus(DocumentStatus.PROCESSING);

        // Store file
        String storagePath = storageService.store(
                principal.tenantId(),
                UUID.randomUUID(),
                file.getOriginalFilename(),
                file
        );
        doc.setStoragePath(storagePath);

        doc = documentRepository.save(doc);

        try {
            // Extract text
            String text = extractText(file);
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("Document contains no readable text content.");
            }

            // Chunk text
            List<String> textChunks = chunkText(text, CHUNK_SIZE, CHUNK_OVERLAP);
            log.info("Document '{}' split into {} chunks", doc.getTitle(), textChunks.size());

            // Generate embeddings & save chunks
            for (int i = 0; i < textChunks.size(); i++) {
                String chunkContent = textChunks.get(i);
                float[] embedding = embeddingService.embedText(chunkContent);
                String vectorStr = embeddingService.toVectorString(embedding);

                DocumentChunk chunk = new DocumentChunk();
                chunk.setTenantId(principal.tenantId());
                chunk.setKnowledgeDocument(doc);
                chunk.setChunkIndex(i);
                chunk.setContent(chunkContent);
                chunk.setEmbedding(vectorStr);
                chunk.setEmbeddingModel(embeddingService.modelId());
                chunk.setMetadata(String.format("{\"chunk\":%d,\"doc_title\":\"%s\"}", i, doc.getTitle()));

                chunkRepository.save(chunk);
            }

            doc.setTotalChunks(textChunks.size());
            doc.setStatus(DocumentStatus.READY);
            doc = documentRepository.save(doc);

            log.info("Document '{}' successfully ingested and indexed with {} vector chunks",
                    doc.getTitle(), textChunks.size());

            return doc;
        } catch (Exception e) {
            log.error("Failed to ingest document '{}': {}", doc.getTitle(), e.getMessage(), e);
            doc.setStatus(DocumentStatus.FAILED);
            doc.setErrorMessage(e.getMessage());
            return documentRepository.save(doc);
        }
    }

    private String extractText(MultipartFile file) throws Exception {
        String filename = (file.getOriginalFilename() != null) ? file.getOriginalFilename().toLowerCase() : "";
        String contentType = (file.getContentType() != null) ? file.getContentType().toLowerCase() : "";

        if (filename.endsWith(".pdf") || contentType.contains("pdf")) {
            try (InputStream is = file.getInputStream();
                 PDDocument document = Loader.loadPDF(is.readAllBytes())) {
                PDFTextStripper stripper = new PDFTextStripper();
                return stripper.getText(document);
            }
        }

        // Plain text, Markdown, CSV
        return new String(file.getBytes(), StandardCharsets.UTF_8);
    }

    public List<String> chunkText(String text, int targetChunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) return chunks;

        String[] paragraphs = text.split("\n\n+");
        StringBuilder currentChunk = new StringBuilder();

        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) continue;

            if (currentChunk.length() + trimmed.length() > targetChunkSize && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());

                // Create overlap from end of current chunk
                String currentStr = currentChunk.toString();
                int overlapStart = Math.max(0, currentStr.length() - overlap);
                currentChunk = new StringBuilder(currentStr.substring(overlapStart));
                currentChunk.append("\n");
            }

            currentChunk.append(trimmed).append("\n\n");
        }

        if (currentChunk.length() > 0 && !currentChunk.toString().trim().isEmpty()) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }
}
