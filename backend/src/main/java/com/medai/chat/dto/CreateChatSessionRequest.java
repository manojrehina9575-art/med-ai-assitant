package com.medai.chat.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateChatSessionRequest {
    private UUID patientId;

    @Size(max = 255, message = "Session title cannot exceed 255 characters")
    private String title;

    private String initialMessage;
}
