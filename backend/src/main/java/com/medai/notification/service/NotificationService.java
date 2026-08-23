package com.medai.notification.service;

import com.medai.auth.security.UserPrincipal;
import com.medai.notification.dto.NotificationDto;
import com.medai.notification.dto.NotificationListDto;
import com.medai.notification.entity.Notification;
import com.medai.notification.repository.NotificationRepository;
import com.medai.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * Creates a notification for a specific user in the current tenant.
     * Call this from analysis completion hooks, workflow updates, etc.
     */
    @Transactional
    public Notification createNotification(UUID userId,
                                           String type,
                                           String title,
                                           String message,
                                           String severity,
                                           String relatedEntityType,
                                           UUID relatedEntityId) {
        Notification n = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .severity(severity)
                .relatedEntityType(relatedEntityType)
                .relatedEntityId(relatedEntityId)
                .build();
        return notificationRepository.save(n);
    }

    /** Convenience overload — no related entity */
    @Transactional
    public Notification createNotification(UUID userId, String type, String title, String message, String severity) {
        return createNotification(userId, type, title, message, severity, null, null);
    }

    @Transactional(readOnly = true)
    public NotificationListDto list(int page, int size) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId   = currentUserId();

        Page<Notification> p = notificationRepository
                .findByTenantIdAndUserIdOrderByCreatedAtDesc(tenantId, userId, PageRequest.of(page, size));

        long unread = notificationRepository.countByTenantIdAndUserIdAndIsReadFalse(tenantId, userId);

        return NotificationListDto.builder()
                .notifications(p.getContent().stream().map(this::toDto).toList())
                .unreadCount(unread)
                .totalElements(p.getTotalElements())
                .totalPages(p.getTotalPages())
                .currentPage(page)
                .build();
    }

    @Transactional
    public void markAsRead(UUID notificationId) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId   = currentUserId();

        notificationRepository.findByIdAndTenantIdAndUserId(notificationId, tenantId, userId)
                .ifPresent(n -> {
                    n.setRead(true);
                    notificationRepository.save(n);
                });
    }

    @Transactional
    public int markAllRead() {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId   = currentUserId();
        return notificationRepository.markAllReadByTenantAndUser(tenantId, userId);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount() {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId   = currentUserId();
        return notificationRepository.countByTenantIdAndUserIdAndIsReadFalse(tenantId, userId);
    }

    // ── helpers ─────────────────────────────────────────────

    private NotificationDto toDto(Notification n) {
        return NotificationDto.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .message(n.getMessage())
                .severity(n.getSeverity())
                .isRead(n.isRead())
                .relatedEntityType(n.getRelatedEntityType())
                .relatedEntityId(n.getRelatedEntityId())
                .createdAt(n.getCreatedAt())
                .build();
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal up) {
            return up.userId();
        }
        throw new IllegalStateException("No authenticated user in context");
    }
}
