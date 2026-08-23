package com.medai.notification.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class NotificationListDto {
    private List<NotificationDto> notifications;
    private long unreadCount;
    private long totalElements;
    private int totalPages;
    private int currentPage;
}
