package com.medai.notification.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class NotificationDto {
    private UUID id;
    private String type;
    private String title;
    private String message;
    private String severity;
    private boolean isRead;
    private String relatedEntityType;
    private UUID relatedEntityId;
    private Instant createdAt;
}
