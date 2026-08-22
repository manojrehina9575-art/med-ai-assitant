package com.medai.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionDto {
    private UUID id;
    private UUID patientId;
    private String patientName;
    private String patientMrn;
    private UUID userId;
    private String title;
    private Boolean isArchived;
    private List<ChatMessageDto> messages;
    private Long messageCount;
    private Instant createdAt;
    private Instant updatedAt;
}
