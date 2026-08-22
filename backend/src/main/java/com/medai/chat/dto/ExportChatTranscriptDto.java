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
public class ExportChatTranscriptDto {
    private UUID sessionId;
    private String title;
    private String tenantName;
    private String patientName;
    private String patientMrn;
    private Instant exportedAt;
    private List<ChatMessageDto> messages;
    private String formattedMarkdown;
}
