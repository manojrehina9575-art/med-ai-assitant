package com.medai.chat.repository;

import com.medai.chat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findByTenantIdAndSessionIdOrderByCreatedAtAsc(UUID tenantId, UUID sessionId);

    @Query("SELECT m FROM ChatMessage m WHERE m.tenantId = :tenantId AND m.sessionId = :sessionId ORDER BY m.createdAt DESC")
    List<ChatMessage> findRecentMessages(
            @Param("tenantId") UUID tenantId,
            @Param("sessionId") UUID sessionId,
            Pageable pageable
    );

    long countByTenantIdAndSessionId(UUID tenantId, UUID sessionId);
}
