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
import com.medai.analytics.AiMetricsService;
import com.medai.chat.guardrail.ChatGuardrailService;
import com.medai.chat.guardrail.ChatOutputGuardrailService;
import com.medai.chat.repository.ChatMessageRepository;
import com.medai.chat.repository.ChatSessionRepository;
import com.medai.common.dto.PagedResponse;
import com.medai.common.exception.ResourceNotFoundException;
import com.medai.config.ModelPricing;
import com.medai.config.RateLimitService;
import com.medai.patient.entity.Patient;
import com.medai.patient.repository.PatientRepository;
import com.medai.tenant.TenantContext;
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
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
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
    private final ChatOutputGuardrailService outputGuardrailService;
    private final AiMetricsService metricsService;
    private final ChatContextBuilderService contextBuilderService;
    private final ChatClient chatClient;
    private final ChatMessagePersistence persistence;
    private final RateLimitService rateLimitService;
    private final ModelPricing modelPricing;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.openai.chat.options.model:qwen/qwen3.6-27b}")
    private String modelName;

    /** How many prior messages are replayed to the model as conversational memory. */
    private static final int HISTORY_WINDOW = 10;

    /**
     * Two minutes. Long enough for a slow model to finish, short enough that a wedged upstream
     * releases the connection rather than holding it until the client gives up.
     */
    private static final long STREAM_TIMEOUT_MS = 120_000L;

    /**
     * Creates a session, and optionally runs its first turn.
     *
     * <p>Not {@code @Transactional}, for the same reason {@link #sendMessage} is not: when an
     * initial message is supplied this method calls straight into a model round trip, and a
     * transaction opened here would be held across it. It would also break outright — the steps
     * inside {@code sendMessage} run in their own transactions and could not see a session this
     * one had created but not yet committed.
     */
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

        ChatSession savedSession = persistence.saveSession(session);

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

        // Two batch queries for the whole page, rather than two per row. A twenty-row page used to
        // issue forty-one queries — a patient lookup and a count for each session — every one of
        // them re-evaluating the row-level-security policy.
        List<ChatSession> sessions = sessionPage.getContent();

        Set<UUID> patientIds = sessions.stream()
                .map(ChatSession::getPatientId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, Patient> patientsById = patientIds.isEmpty()
                ? Map.of()
                : patientRepository.findByTenantIdAndIdIn(principal.tenantId(), patientIds).stream()
                        .collect(Collectors.toMap(Patient::getId, Function.identity()));

        Set<UUID> sessionIds = sessions.stream().map(ChatSession::getId).collect(Collectors.toSet());

        Map<UUID, Long> countsBySession = sessionIds.isEmpty()
                ? Map.of()
                : messageRepository.countBySessionIds(principal.tenantId(), sessionIds).stream()
                        .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));

        Page<ChatSessionDto> dtoPage = sessionPage.map(chatSession -> {
            Patient patient = chatSession.getPatientId() != null
                    ? patientsById.get(chatSession.getPatientId())
                    : null;
            ChatSessionDto dto = toSessionDto(chatSession, patient);
            // A session with no messages yet is absent from the grouped count, not zero-valued.
            dto.setMessageCount(countsBySession.getOrDefault(chatSession.getId(), 0L));
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
    /**
     * Runs one chat turn and returns the complete answer.
     *
     * <p>Deliberately NOT {@code @Transactional}. The model call in the middle takes seconds, and
     * holding a pooled database connection across it exhausted the pool under a handful of
     * concurrent chats — stalling every endpoint, not just this one. The database steps go through
     * {@link ChatMessagePersistence}, each in its own short transaction, with the model call
     * between them holding nothing.
     *
     * <p>The consequence is that a turn is no longer atomic: if the model call fails, the user's
     * message is already committed. That is the right trade — the user did say it, the transcript
     * should show it, and {@link #finishTurn} records the failure against it rather than
     * pretending the turn never happened.
     */
    public ChatMessageDto sendMessage(UUID sessionId, SendMessageRequest request, UserPrincipal principal) {
        PreparedTurn turn = prepareTurn(sessionId, request, principal);
        ModelOutcome outcome = invokeModel(turn, principal, sessionId);
        return finishTurn(turn, outcome, sessionId, principal);
    }

    /**
     * Runs one chat turn, forwarding the model's output as it arrives.
     *
     * <p>This used to call {@link #sendMessage}, wait for the entire answer, then replay it word
     * by word with a 15ms sleep between words. Time to first token was unchanged — the clinician
     * waited the full latency and then watched a fake reveal, which is slower than simply showing
     * the answer — and the animation misrepresented what was happening. The deltas below are the
     * provider's real ones.
     *
     * <p>Two things still cannot be streamed, and are not. The acute-emergency banner is derived
     * from the input, so it is sent first, before the model has produced anything: that is the
     * most time-critical text in the response and it now arrives immediately rather than last.
     * The output guardrail needs the whole answer to check citation indices and dose grounding, so
     * its annotations arrive with the completion event.
     */
    public SseEmitter streamMessage(UUID sessionId, SendMessageRequest request, UserPrincipal principal) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);

        // A virtual thread rather than CompletableFuture.runAsync: this body blocks on the model
        // stream, and doing that on the common ForkJoinPool starves every other parallel stream in
        // the JVM. Virtual threads are enabled application-wide.
        Thread.startVirtualThread(() -> {
            // TenantContext is a ThreadLocal and does not cross the thread boundary, so without
            // this the connection is stamped with an empty tenant and PostgreSQL row-level
            // security — FORCE'd on chat_sessions and chat_messages since V12 — matches no rows.
            TenantContext.setCurrentTenantId(principal.tenantId());
            try {
                PreparedTurn turn = prepareTurn(sessionId, request, principal);

                // The red flag is known before the model is called. Sending it first is the whole
                // reason to prefer real streaming here: a clinician sees it in milliseconds
                // instead of after the answer completes.
                if (turn.guardrailResult().isEmergency()) {
                    send(emitter, "chunk", ChatStreamChunkDto.builder()
                            .delta(turn.guardrailResult().getEmergencyInterventionMessage() + "\n\n---\n\n")
                            .isComplete(false)
                            .safetyNotices(List.of(SafetyFlag.RED_FLAG_EMERGENCY.name()))
                            .build());
                }

                ModelOutcome outcome = streamModel(turn, principal, sessionId, emitter);
                ChatMessageDto assistantDto = finishTurn(turn, outcome, sessionId, principal);

                send(emitter, "complete", ChatStreamChunkDto.builder()
                        .delta("")
                        .isComplete(true)
                        .message(assistantDto)
                        .safetyNotices(assistantDto.getSafetyFlags())
                        .build());
                emitter.complete();
            } catch (Exception e) {
                log.error("Streaming failed for session {} in tenant {}",
                        sessionId, principal.tenantId(), e);
                try {
                    // The exception text is logged and not sent: it is the provider's, and it
                    // carries endpoint and request detail the client has no use for.
                    emitter.send(SseEmitter.event().name("error")
                            .data("The response could not be generated. Please try again."));
                } catch (IOException ignored) {
                    // The client is gone; there is nobody left to tell.
                }
                emitter.completeWithError(e);
            } finally {
                TenantContext.clear();
            }
        });

        return emitter;
    }

    // ── Turn pipeline ────────────────────────────────────────────────────────

    /**
     * Everything a turn needs before the model is called, shared by the buffered and streaming
     * paths so the two cannot drift apart on guardrails, context or history.
     */
    private record PreparedTurn(
            ChatSession session,
            Patient patient,
            GuardrailEvaluationResult guardrailResult,
            ChatMessage userMessage,
            ChatContextBuilderService.BuiltContext context,
            List<Message> prompt,
            long startedAtMillis
    ) {
    }

    /** What the model produced, or the fact that it did not. */
    private record ModelOutcome(
            String rawResponse,
            boolean failed,
            int promptTokens,
            int completionTokens,
            int totalTokens,
            BigDecimal cost
    ) {
        static ModelOutcome failure(String message) {
            return new ModelOutcome(message, true, 0, 0, 0, BigDecimal.ZERO);
        }
    }

    private PreparedTurn prepareTurn(UUID sessionId, SendMessageRequest request, UserPrincipal principal) {
        long startedAt = System.currentTimeMillis();
        rateLimitService.checkRateLimit(principal.tenantId());

        ChatSession session = persistence.loadSession(sessionId, principal.tenantId());

        Patient patient = null;
        if (session.getPatientId() != null) {
            patient = patientRepository.findByIdAndTenantId(session.getPatientId(), principal.tenantId()).orElse(null);
        }

        // 1. Guardrail input check
        GuardrailEvaluationResult guardrailResult = guardrailService.evaluateInput(request.getContent(), patient);
        guardrailResult.getFlags().forEach(flag ->
                metricsService.recordGuardrailFinding(principal.tenantId().toString(), "INPUT", flag.name()));

        // 2. Persist User Message
        ChatMessage userMsg = ChatMessage.builder()
                .sessionId(sessionId)
                .role(ChatRole.USER)
                .content(guardrailResult.getSanitizedInput())
                .safetyFlags(guardrailResult.getFlags().stream().map(Enum::name).collect(Collectors.toList()))
                .build();
        userMsg.setTenantId(principal.tenantId());
        userMsg = persistence.save(userMsg);

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
                principal.tenantId(), sessionId, PageRequest.of(0, HISTORY_WINDOW));
        Collections.reverse(recentMessages);

        // 5. Construct Spring AI message list
        List<Message> prompt = new ArrayList<>();
        prompt.add(new SystemMessage(builtContext.systemPrompt()));

        for (ChatMessage msg : recentMessages) {
            if (msg.getId().equals(userMsg.getId())) {
                continue; // Current user message handled last
            }
            if (msg.getRole() == ChatRole.USER) {
                prompt.add(new UserMessage(msg.getContent()));
            } else if (msg.getRole() == ChatRole.ASSISTANT) {
                prompt.add(new AssistantMessage(msg.getContent()));
            }
        }
        prompt.add(new UserMessage(guardrailResult.getSanitizedInput()));

        return new PreparedTurn(session, patient, guardrailResult, userMsg, builtContext, prompt, startedAt);
    }

    /** Buffered invocation: one request, one complete answer. */
    private ModelOutcome invokeModel(PreparedTurn turn, UserPrincipal principal, UUID sessionId) {
        try {
            ChatResponse response = chatClient.prompt()
                    .messages(turn.prompt())
                    .call()
                    .chatResponse();

            return usageFrom(response, response.getResult().getOutput().getContent(), principal);
        } catch (Exception e) {
            return modelFailure(e, sessionId, principal);
        }
    }

    /**
     * Streaming invocation: forwards each delta as it arrives and accumulates the full text.
     *
     * <p>{@code toStream()} blocks the calling thread on the reactive publisher, which is exactly
     * what is wanted on a virtual thread — the flow reads top to bottom and costs no platform
     * thread while it waits.
     */
    private ModelOutcome streamModel(PreparedTurn turn, UserPrincipal principal,
                                     UUID sessionId, SseEmitter emitter) {
        StringBuilder full = new StringBuilder();
        ChatResponse lastWithUsage = null;

        try {
            var responses = chatClient.prompt()
                    .messages(turn.prompt())
                    .stream()
                    .chatResponse()
                    .toStream()
                    .iterator();

            while (responses.hasNext()) {
                ChatResponse response = responses.next();

                if (response.getMetadata() != null && response.getMetadata().getUsage() != null
                    && response.getMetadata().getUsage().getTotalTokens() != null) {
                    // Providers report usage on a single chunk, usually the last. Keep the most
                    // recent one seen rather than assuming which.
                    lastWithUsage = response;
                }

                if (response.getResult() == null || response.getResult().getOutput() == null) {
                    continue;
                }

                String delta = response.getResult().getOutput().getContent();
                if (delta == null || delta.isEmpty()) {
                    continue;
                }

                full.append(delta);
                send(emitter, "chunk", ChatStreamChunkDto.builder()
                        .delta(delta)
                        .isComplete(false)
                        .build());
            }
        } catch (Exception e) {
            if (full.isEmpty()) {
                return modelFailure(e, sessionId, principal);
            }
            // The stream died partway. What arrived is real and the clinician has already read it,
            // so it is kept and marked rather than replaced with an error — silently discarding
            // text somebody is mid-way through reading is worse than an incomplete answer.
            log.error("Chat stream broke after {} characters for session {} in tenant {}",
                    full.length(), sessionId, principal.tenantId(), e);
            full.append("\n\n_[The response was cut short by a connection error and is incomplete.]_");
        }

        return usageFrom(lastWithUsage, full.toString(), principal);
    }

    /** Reads token usage off a response, meters it, and pairs it with the text. */
    private ModelOutcome usageFrom(ChatResponse response, String text, UserPrincipal principal) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return new ModelOutcome(text, false, 0, 0, 0, BigDecimal.ZERO);
        }

        var usage = response.getMetadata().getUsage();
        int promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens().intValue() : 0;
        int completionTokens = usage.getGenerationTokens() != null ? usage.getGenerationTokens().intValue() : 0;
        int totalTokens = usage.getTotalTokens() != null ? usage.getTotalTokens().intValue() : 0;

        BigDecimal cost = modelPricing.estimate(modelName, promptTokens, completionTokens);
        rateLimitService.recordUsage(principal.tenantId(), modelName, promptTokens, completionTokens);

        return new ModelOutcome(text, false, promptTokens, completionTokens, totalTokens, cost);
    }

    /**
     * The provider's message is logged and never shown.
     *
     * <p>It used to be concatenated into the assistant's reply and saved as a normal turn, which
     * put endpoint URLs and request fragments into a clinical transcript the UI exports — and,
     * because the row looked like a success, made the failure invisible to every metric.
     */
    private ModelOutcome modelFailure(Exception e, UUID sessionId, UserPrincipal principal) {
        log.error("Chat model call failed for session {} in tenant {}", sessionId, principal.tenantId(), e);
        return ModelOutcome.failure(
                "This request could not be completed. Nothing was generated — please try again, "
                + "and if it persists, contact your administrator.");
    }

    /** Guards the output, persists the assistant message, and returns it. */
    private ChatMessageDto finishTurn(PreparedTurn turn, ModelOutcome outcome,
                                      UUID sessionId, UserPrincipal principal) {
        metricsService.recordChatTurn(principal.tenantId().toString(), modelName,
                outcome.failed() ? "FAILED" : "SUCCESS",
                System.currentTimeMillis() - turn.startedAtMillis());

        // Guard the output, then apply the input-derived banner.
        //
        // The output check is the one that was missing entirely: postProcessOutput only ever
        // prepended an emergency banner, so a fabricated citation index or a dose recalled from
        // training data rather than read off a hospital protocol reached the clinician looking
        // exactly like a grounded answer. Skipped on failure — there is nothing to guard, and
        // prepending a clinical emergency banner to an error message would be worse than useless.
        String finalResponse;
        List<String> outputFindingCodes = List.of();

        if (outcome.failed()) {
            finalResponse = outcome.rawResponse();
        } else {
            ChatOutputGuardrailService.OutputEvaluation outputEvaluation =
                    outputGuardrailService.evaluate(outcome.rawResponse(), turn.context().citations(),
                            turn.context().groundingText());

            outputFindingCodes = outputEvaluation.findings().stream()
                    .map(ChatOutputGuardrailService.OutputFinding::code)
                    .toList();

            outputFindingCodes.forEach(code ->
                    metricsService.recordGuardrailFinding(principal.tenantId().toString(), "OUTPUT", code));

            if (!outputEvaluation.isClean()) {
                log.warn("Output guardrail findings for session {} in tenant {}: {}",
                        sessionId, principal.tenantId(), outputFindingCodes);
            }

            finalResponse = guardrailService.postProcessOutput(
                    outputEvaluation.annotatedResponse(), turn.guardrailResult());
        }

        List<String> assistantSafetyFlags = new ArrayList<>();
        if (outcome.failed()) {
            // Flagged rather than silently stored, so a failed turn is countable in the transcript
            // and distinguishable from an answer the model actually produced.
            assistantSafetyFlags.add(SafetyFlag.GENERATION_FAILED.name());
        } else {
            assistantSafetyFlags.addAll(turn.guardrailResult().getFlags().stream().map(Enum::name).toList());
            if (turn.guardrailResult().isEmergency()) {
                assistantSafetyFlags.add(SafetyFlag.RED_FLAG_EMERGENCY.name());
            }
            // Output findings are carried on the message, not only rendered into its text, so the
            // UI can badge an ungrounded answer and a query can count them after the fact.
            assistantSafetyFlags.addAll(outputFindingCodes);
        }

        String citationsJson = null;
        if (!turn.context().citations().isEmpty()) {
            try {
                citationsJson = objectMapper.writeValueAsString(turn.context().citations());
            } catch (JsonProcessingException e) {
                log.warn("Could not serialize chat citations: {}", e.getMessage());
            }
        }

        ChatMessage assistantMsg = ChatMessage.builder()
                .sessionId(sessionId)
                .role(ChatRole.ASSISTANT)
                .content(finalResponse)
                .citations(citationsJson)
                .safetyFlags(assistantSafetyFlags)
                .modelUsed(modelName)
                .promptTokens(outcome.promptTokens())
                .completionTokens(outcome.completionTokens())
                .totalTokens(outcome.totalTokens())
                .estimatedCost(outcome.cost())
                .build();
        assistantMsg.setTenantId(principal.tenantId());

        return toMessageDto(persistence.saveReplyAndTouchSession(
                assistantMsg, sessionId, principal.tenantId()));
    }

    /** Wraps the checked IOException so the streaming body stays readable. */
    private void send(SseEmitter emitter, String event, ChatStreamChunkDto payload) {
        try {
            emitter.send(SseEmitter.event().name(event).data(payload));
        } catch (IOException e) {
            throw new UncheckedIOException("Client disconnected from chat stream", e);
        }
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
