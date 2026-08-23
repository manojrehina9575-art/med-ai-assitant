package com.medai.notification.repository;

import com.medai.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByTenantIdAndUserIdOrderByCreatedAtDesc(
            UUID tenantId, UUID userId, Pageable pageable);

    long countByTenantIdAndUserIdAndIsReadFalse(UUID tenantId, UUID userId);

    Optional<Notification> findByIdAndTenantIdAndUserId(UUID id, UUID tenantId, UUID userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true " +
           "WHERE n.tenantId = :tenantId AND n.userId = :userId AND n.isRead = false")
    int markAllReadByTenantAndUser(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);
}
