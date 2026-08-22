package com.medai.chat.controller;

import com.medai.auth.security.UserPrincipal;
import com.medai.chat.dto.*;
import com.medai.chat.service.ChatService;
import com.medai.common.dto.ApiResponse;
import com.medai.common.dto.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Clinical AI Chat", description = "Multi-turn conversational AI with patient context and hospital protocols")
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/sessions")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'LAB_TECH')")
    @Operation(summary = "Create a new clinical chat session")
    public ResponseEntity<ApiResponse<ChatSessionDto>> createSession(
            @Valid @RequestBody CreateChatSessionRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ChatSessionDto session = chatService.createSession(request, principal);
        return ResponseEntity.ok(ApiResponse.success("Chat session created", session));
    }

    @GetMapping("/sessions")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'LAB_TECH')")
    @Operation(summary = "List chat sessions for the current tenant")
    public ResponseEntity<ApiResponse<PagedResponse<ChatSessionDto>>> listSessions(
            @RequestParam(required = false) UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PagedResponse<ChatSessionDto> sessions = chatService.listSessions(patientId, page, size, principal);
        return ResponseEntity.ok(ApiResponse.success(sessions));
    }

    @GetMapping("/sessions/{sessionId}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'LAB_TECH')")
    @Operation(summary = "Get a chat session with all message history")
    public ResponseEntity<ApiResponse<ChatSessionDto>> getSession(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ChatSessionDto session = chatService.getSession(sessionId, principal);
        return ResponseEntity.ok(ApiResponse.success(session));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR')")
    @Operation(summary = "Delete or archive a chat session")
    public ResponseEntity<ApiResponse<Void>> deleteSession(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        chatService.deleteSession(sessionId, principal);
        return ResponseEntity.ok(ApiResponse.success("Session deleted successfully", null));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'LAB_TECH')")
    @Operation(summary = "Send a message within a chat session")
    public ResponseEntity<ApiResponse<ChatMessageDto>> sendMessage(
            @PathVariable UUID sessionId,
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ChatMessageDto message = chatService.sendMessage(sessionId, request, principal);
        return ResponseEntity.ok(ApiResponse.success(message));
    }

    @GetMapping(value = "/sessions/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'LAB_TECH')")
    @Operation(summary = "Stream assistant response chunks via Server-Sent Events (SSE)")
    public SseEmitter streamMessage(
            @PathVariable UUID sessionId,
            @RequestParam String content,
            @RequestParam(defaultValue = "true") Boolean includeRag,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        SendMessageRequest request = SendMessageRequest.builder()
                .content(content)
                .includeRag(includeRag)
                .build();
        return chatService.streamMessage(sessionId, request, principal);
    }

    @GetMapping("/sessions/{sessionId}/export")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'LAB_TECH')")
    @Operation(summary = "Export consultation transcript formatted as clinical markdown")
    public ResponseEntity<ApiResponse<ExportChatTranscriptDto>> exportTranscript(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ExportChatTranscriptDto transcript = chatService.exportTranscript(sessionId, principal);
        return ResponseEntity.ok(ApiResponse.success(transcript));
    }
}
