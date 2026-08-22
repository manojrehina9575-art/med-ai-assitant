package com.medai.chat.dto;

import com.medai.chat.enums.ChatRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {
    private UUID id;
    private UUID sessionId;
    private ChatRole role;
    private String content;
    private List<ChatCitationDto> citations;
    private List<String> safetyFlags;
    private String modelUsed;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private BigDecimal estimatedCost;
    private Instant createdAt;
}
