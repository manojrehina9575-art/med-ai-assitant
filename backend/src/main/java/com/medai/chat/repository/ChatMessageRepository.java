package com.medai.chat.repository;

import com.medai.chat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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

    /**
     * Message counts for a whole page of sessions in one query.
     *
     * <p>Listing used to call {@link #countByTenantIdAndSessionId} once per row, so a page of
     * twenty sessions issued twenty counts — each re-evaluating the row-level-security policy —
     * on top of twenty separate patient lookups.
     *
     * <p>Returns {@code [sessionId, count]} pairs; {@code SessionMessageCount} would be tidier but
     * a projection interface cannot express a grouped tuple without a wrapper class per query.
     */
    @Query("SELECT m.sessionId, COUNT(m) FROM ChatMessage m "
           + "WHERE m.tenantId = :tenantId AND m.sessionId IN :sessionIds "
           + "GROUP BY m.sessionId")
    List<Object[]> countBySessionIds(
            @Param("tenantId") UUID tenantId,
            @Param("sessionIds") Collection<UUID> sessionIds
    );
}
