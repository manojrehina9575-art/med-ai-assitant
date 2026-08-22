package com.medai.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {

    @NotBlank(message = "Message content cannot be blank")
    @Size(max = 10000, message = "Message content cannot exceed 10000 characters")
    private String content;

    @Builder.Default
    private Boolean includeRag = true;
}
