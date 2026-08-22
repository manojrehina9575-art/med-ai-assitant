package com.medai.chat.repository;

import com.medai.chat.entity.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    Page<ChatSession> findByTenantIdAndIsArchivedFalseOrderByUpdatedAtDesc(UUID tenantId, Pageable pageable);

    Page<ChatSession> findByTenantIdAndPatientIdAndIsArchivedFalseOrderByUpdatedAtDesc(
            UUID tenantId, UUID patientId, Pageable pageable);

    Optional<ChatSession> findByIdAndTenantId(UUID id, UUID tenantId);
}
