package com.medai.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medai.auth.security.UserPrincipal;
import com.medai.chat.dto.*;
import com.medai.chat.entity.ChatMessage;
import com.medai.chat.entity.ChatSession;
import com.medai.chat.enums.ChatRole;
import com.medai.chat.enums.SafetyFlag;
import com.medai.chat.guardrail.ChatGuardrailService;
import com.medai.chat.repository.ChatMessageRepository;
import com.medai.chat.repository.ChatSessionRepository;
import com.medai.common.dto.PagedResponse;
import com.medai.common.exception.ResourceNotFoundException;
import com.medai.config.ModelPricing;
import com.medai.config.RateLimitService;
import com.medai.patient.entity.Patient;
import com.medai.patient.repository.PatientRepository;
import com.medai.tenant.entity.Tenant;
import com.medai.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final PatientRepository patientRepository;
    private final TenantRepository tenantRepository;
    private final ChatGuardrailService guardrailService;
    private final ChatContextBuilderService contextBuilderService;
    private final ChatClient.Builder chatClientBuilder;
    private final RateLimitService rateLimitService;
    private final ModelPricing modelPricing;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.openai.chat.options.model:qwen/qwen3.6-27b}")
    private String modelName;

    @Transactional
    public ChatSessionDto createSession(CreateChatSessionRequest request, UserPrincipal principal) {
        log.info("Creating chat session for user {} in tenant {}", principal.userId(), principal.tenantId());

        String title = request.getTitle();
        Patient patient = null;

        if (request.getPatientId() != null) {
            patient = patientRepository.findByIdAndTenantId(request.getPatientId(), principal.tenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
            if (title == null || title.isBlank()) {
                title = "Clinical Consultation: " + patient.getFullName();
            }
        }

        if (title == null || title.isBlank()) {
            title = "New Consultation (" + java.time.LocalDate.now() + ")";
        }

        ChatSession session = ChatSession.builder()
                .patientId(request.getPatientId())
                .userId(principal.userId())
                .title(title)
                .isArchived(false)
                .build();
        session.setTenantId(principal.tenantId());

        ChatSession savedSession = sessionRepository.save(session);

        // If an initial message is provided, process it immediately
        if (request.getInitialMessage() != null && !request.getInitialMessage().isBlank()) {
            sendMessage(savedSession.getId(), SendMessageRequest.builder()
                    .content(request.getInitialMessage())
                    .includeRag(true)
                    .build(), principal);
        }

        return toSessionDto(savedSession, patient);
    }

    public PagedResponse<ChatSessionDto> listSessions(UUID patientId, int page, int size, UserPrincipal principal) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ChatSession> sessionPage;

        if (patientId != null) {
            sessionPage = sessionRepository.findByTenantIdAndPatientIdAndIsArchivedFalseOrderByUpdatedAtDesc(
                    principal.tenantId(), patientId, pageable);
        } else {
            sessionPage = sessionRepository.findByTenantIdAndIsArchivedFalseOrderByUpdatedAtDesc(
                    principal.tenantId(), pageable);
        }

        Page<ChatSessionDto> dtoPage = sessionPage.map(s -> {
            Patient p = null;
            if (s.getPatientId() != null) {
                p = patientRepository.findByIdAndTenantId(s.getPatientId(), principal.tenantId()).orElse(null);
            }
            ChatSessionDto dto = toSessionDto(s, p);
            dto.setMessageCount(messageRepository.countByTenantIdAndSessionId(principal.tenantId(), s.getId()));
            return dto;
        });

        return PagedResponse.of(dtoPage);
    }

    public ChatSessionDto getSession(UUID sessionId, UserPrincipal principal) {
        ChatSession session = sessionRepository.findByIdAndTenantId(sessionId, principal.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));

        Patient patient = null;
        if (session.getPatientId() != null) {
            patient = patientRepository.findByIdAndTenantId(session.getPatientId(), principal.tenantId()).orElse(null);
        }

        List<ChatMessage> messages = messageRepository.findByTenantIdAndSessionIdOrderByCreatedAtAsc(
                principal.tenantId(), sessionId);

        ChatSessionDto dto = toSessionDto(session, patient);
        dto.setMessages(messages.stream().map(this::toMessageDto).collect(Collectors.toList()));
        dto.setMessageCount((long) messages.size());
        return dto;
    }

    @Transactional
    public void deleteSession(UUID sessionId, UserPrincipal principal) {
        ChatSession session = sessionRepository.findByIdAndTenantId(sessionId, principal.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));
        sessionRepository.delete(session);
    }

    @Transactional
    public ChatMessageDto sendMessage(UUID sessionId, SendMessageRequest request, UserPrincipal principal) {
        rateLimitService.checkRateLimit(principal.tenantId());

        ChatSession session = sessionRepository.findByIdAndTenantId(sessionId, principal.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));

        Patient patient = null;
        if (session.getPatientId() != null) {
            patient = patientRepository.findByIdAndTenantId(session.getPatientId(), principal.tenantId()).orElse(null);
        }

        // 1. Guardrail input check
        GuardrailEvaluationResult guardrailResult = guardrailService.evaluateInput(request.getContent(), patient);

        // 2. Persist User Message
        ChatMessage userMsg = ChatMessage.builder()
                .sessionId(sessionId)
                .role(ChatRole.USER)
                .content(guardrailResult.getSanitizedInput())
                .safetyFlags(guardrailResult.getFlags().stream().map(Enum::name).collect(Collectors.toList()))
                .build();
        userMsg.setTenantId(principal.tenantId());
        messageRepository.save(userMsg);

        // 3. Build Intelligent Clinical Context (Patient + RAG)
        boolean includeRag = request.getIncludeRag() != null ? request.getIncludeRag() : true;
        ChatContextBuilderService.BuiltContext builtContext = contextBuilderService.buildContext(
                principal.tenantId(),
                patient,
                guardrailResult.getSanitizedInput(),
                includeRag
        );

        // 4. Retrieve recent history for memory (last 10 messages)
        List<ChatMessage> recentMessages = messageRepository.findRecentMessages(
                principal.tenantId(), sessionId, PageRequest.of(0, 10));
        Collections.reverse(recentMessages);

        // 5. Construct Spring AI message list
        List<Message> springAiMessages = new ArrayList<>();
        springAiMessages.add(new SystemMessage(builtContext.systemPrompt()));

        for (ChatMessage msg : recentMessages) {
            if (msg.getId().equals(userMsg.getId())) {
                continue; // Current user message handled last
            }
            if (msg.getRole() == ChatRole.USER) {
                springAiMessages.add(new UserMessage(msg.getContent()));
            } else if (msg.getRole() == ChatRole.ASSISTANT) {
                springAiMessages.add(new AssistantMessage(msg.getContent()));
            }
        }
        springAiMessages.add(new UserMessage(guardrailResult.getSanitizedInput()));

        // 6. Invoke Spring AI Chat LLM
        ChatClient chatClient = chatClientBuilder.build();
        String rawResponse;
        Integer promptTokens = 0;
        Integer completionTokens = 0;
        Integer totalTokens = 0;
        BigDecimal cost = BigDecimal.ZERO;

        try {
            ChatResponse response = chatClient.prompt()
                    .messages(springAiMessages)
                    .call()
                    .chatResponse();

            rawResponse = response.getResult().getOutput().getContent();

            if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                var usage = response.getMetadata().getUsage();
                promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens().intValue() : 0;
                completionTokens = usage.getGenerationTokens() != null ? usage.getGenerationTokens().intValue() : 0;
                totalTokens = usage.getTotalTokens() != null ? usage.getTotalTokens().intValue() : 0;
                cost = modelPricing.estimate(modelName, promptTokens, completionTokens);
                rateLimitService.recordUsage(principal.tenantId(), modelName, promptTokens, completionTokens);
            }
        } catch (Exception e) {
            log.error("Chat LLM invocation failed: {}", e.getMessage(), e);
            rawResponse = "I apologize, but I encountered a processing error while synthesizing your medical query: " + e.getMessage();
        }

        // 7. Post-process with Guardrails
        String finalResponse = guardrailService.postProcessOutput(rawResponse, guardrailResult);

        // 8. Prepare safety flags for assistant message
        List<String> assistantSafetyFlags = new ArrayList<>(
                guardrailResult.getFlags().stream().map(Enum::name).toList()
        );
        if (guardrailResult.isEmergency()) {
            assistantSafetyFlags.add(SafetyFlag.RED_FLAG_EMERGENCY.name());
        }

        // 9. Serialize citations
        String citationsJson = null;
        if (!builtContext.citations().isEmpty()) {
            try {
                citationsJson = objectMapper.writeValueAsString(builtContext.citations());
            } catch (JsonProcessingException e) {
                log.warn("Could not serialize chat citations: {}", e.getMessage());
            }
        }

        // 10. Persist Assistant Message
        ChatMessage assistantMsg = ChatMessage.builder()
                .sessionId(sessionId)
                .role(ChatRole.ASSISTANT)
                .content(finalResponse)
                .citations(citationsJson)
                .safetyFlags(assistantSafetyFlags)
                .modelUsed(modelName)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .estimatedCost(cost)
                .build();
        assistantMsg.setTenantId(principal.tenantId());
        ChatMessage savedAssistantMsg = messageRepository.save(assistantMsg);

        // Update session's updated_at timestamp
        session.setUpdatedAt(Instant.now());
        sessionRepository.save(session);

        return toMessageDto(savedAssistantMsg);
    }

    public SseEmitter streamMessage(UUID sessionId, SendMessageRequest request, UserPrincipal principal) {
        SseEmitter emitter = new SseEmitter(120_000L); // 2 minute timeout

        CompletableFuture.runAsync(() -> {
            try {
                // Execute standard generation
                ChatMessageDto assistantDto = sendMessage(sessionId, request, principal);

                // Stream response in readable token chunks
                String content = assistantDto.getContent();
                String[] words = content.split(" ");
                for (int i = 0; i < words.length; i++) {
                    String piece = words[i] + (i < words.length - 1 ? " " : "");
                    ChatStreamChunkDto chunk = ChatStreamChunkDto.builder()
                            .delta(piece)
                            .isComplete(false)
                            .safetyNotices(assistantDto.getSafetyFlags())
                            .build();
                    emitter.send(SseEmitter.event().name("chunk").data(chunk));
                    Thread.sleep(15); // Natural streaming pace
                }

                // Send final completion payload
                ChatStreamChunkDto finalChunk = ChatStreamChunkDto.builder()
                        .delta("")
                        .isComplete(true)
                        .message(assistantDto)
                        .safetyNotices(assistantDto.getSafetyFlags())
                        .build();
                emitter.send(SseEmitter.event().name("complete").data(finalChunk));
                emitter.complete();
            } catch (Exception e) {
                log.error("Streaming error for session {}: {}", sessionId, e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event().name("error").data("Streaming error: " + e.getMessage()));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    public ExportChatTranscriptDto exportTranscript(UUID sessionId, UserPrincipal principal) {
        ChatSession session = sessionRepository.findByIdAndTenantId(sessionId, principal.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));

        Patient patient = null;
        if (session.getPatientId() != null) {
            patient = patientRepository.findByIdAndTenantId(session.getPatientId(), principal.tenantId()).orElse(null);
        }

        List<ChatMessage> messages = messageRepository.findByTenantIdAndSessionIdOrderByCreatedAtAsc(
                principal.tenantId(), sessionId);

        StringBuilder md = new StringBuilder();
        md.append("# Med-AI Clinical Consultation Transcript\n\n");
        md.append("**Session:** ").append(session.getTitle()).append("\n");
        md.append("**Date:** ").append(Instant.now().toString()).append("\n");

        if (patient != null) {
            md.append("**Patient:** ").append(patient.getFullName())
                    .append(" (MRN: ").append(patient.getMedicalRecordNumber()).append(")\n");
        }
        md.append("\n---\n\n");

        for (ChatMessage msg : messages) {
            String roleName = msg.getRole() == ChatRole.USER ? "Practitioner" : "Med-AI Assistant";
            md.append("### ").append(roleName).append(" (").append(msg.getCreatedAt()).append(")\n\n");
            md.append(msg.getContent()).append("\n\n");
        }

        md.append("\n---\n*Disclaimer: AI decision-support output. Clinical verification by licensed practitioner required.*\n");

        String tenantName = tenantRepository.findById(principal.tenantId())
                .map(Tenant::getName)
                .orElse("Hospital");

        return ExportChatTranscriptDto.builder()
                .sessionId(sessionId)
                .title(session.getTitle())
                .tenantName(tenantName)
                .patientName(patient != null ? patient.getFullName() : null)
                .patientMrn(patient != null ? patient.getMedicalRecordNumber() : null)
                .exportedAt(Instant.now())
                .messages(messages.stream().map(this::toMessageDto).collect(Collectors.toList()))
                .formattedMarkdown(md.toString())
                .build();
    }

    private ChatSessionDto toSessionDto(ChatSession session, Patient patient) {
        return ChatSessionDto.builder()
                .id(session.getId())
                .patientId(session.getPatientId())
                .patientName(patient != null ? patient.getFullName() : null)
                .patientMrn(patient != null ? patient.getMedicalRecordNumber() : null)
                .userId(session.getUserId())
                .title(session.getTitle())
                .isArchived(session.getIsArchived())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }

    private ChatMessageDto toMessageDto(ChatMessage msg) {
        List<ChatCitationDto> citations = null;
        if (msg.getCitations() != null && !msg.getCitations().isBlank()) {
            try {
                citations = objectMapper.readValue(msg.getCitations(), new TypeReference<List<ChatCitationDto>>() {});
            } catch (Exception e) {
                log.warn("Could not deserialize message citations: {}", e.getMessage());
            }
        }

        return ChatMessageDto.builder()
                .id(msg.getId())
                .sessionId(msg.getSessionId())
                .role(msg.getRole())
                .content(msg.getContent())
                .citations(citations)
                .safetyFlags(msg.getSafetyFlags())
                .modelUsed(msg.getModelUsed())
                .promptTokens(msg.getPromptTokens())
                .completionTokens(msg.getCompletionTokens())
                .totalTokens(msg.getTotalTokens())
                .estimatedCost(msg.getEstimatedCost())
                .createdAt(msg.getCreatedAt())
                .build();
    }
}
