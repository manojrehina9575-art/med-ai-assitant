package com.medai.chat.service;

import com.medai.chat.entity.ChatMessage;
import com.medai.chat.entity.ChatSession;
import com.medai.chat.repository.ChatMessageRepository;
import com.medai.chat.repository.ChatSessionRepository;
import com.medai.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * The short transactional steps of a chat turn, separated from the long non-transactional one.
 *
 * <p>{@code ChatService.sendMessage} used to be a single {@code @Transactional} method wrapped
 * around the model call, so a pooled database connection was held for the whole round trip —
 * seconds, sometimes tens of seconds. With HikariCP's default pool of ten, roughly ten concurrent
 * chats exhausted it and every endpoint in the application stalled behind them, not just chat.
 *
 * <p>Splitting the work into its own component rather than adding {@code REQUIRES_NEW} methods to
 * {@code ChatService} is what makes the boundaries real: a self-invocation inside one bean does not
 * pass through the transactional proxy, so those annotations would have done nothing at all.
 */
@Component
@RequiredArgsConstructor
public class ChatMessagePersistence {

    private final ChatMessageRepository messageRepository;
    private final ChatSessionRepository sessionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChatSession saveSession(ChatSession session) {
        return sessionRepository.save(session);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChatSession loadSession(UUID sessionId, UUID tenantId) {
        return sessionRepository.findByIdAndTenantId(sessionId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChatMessage save(ChatMessage message) {
        return messageRepository.save(message);
    }

    /**
     * Saves the assistant's reply and moves the session to the top of the list.
     *
     * <p>One transaction for both: a reply that is visible while its session still shows the
     * previous timestamp is a reply the user cannot find.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChatMessage saveReplyAndTouchSession(ChatMessage assistantMessage, UUID sessionId, UUID tenantId) {
        ChatMessage saved = messageRepository.save(assistantMessage);
        sessionRepository.findByIdAndTenantId(sessionId, tenantId).ifPresent(session -> {
            session.setUpdatedAt(Instant.now());
            sessionRepository.save(session);
        });
        return saved;
    }
}
