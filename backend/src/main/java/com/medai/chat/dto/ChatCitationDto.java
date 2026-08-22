package com.medai.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatCitationDto {
    private UUID documentId;
    private String title;
    private String documentType;
    private int chunkIndex;
    private String excerpt;
    private Double similarityScore;
}
