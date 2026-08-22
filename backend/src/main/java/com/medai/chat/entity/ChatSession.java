package com.medai.chat.entity;

import com.medai.common.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "chat_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSession extends TenantAwareEntity {

    @Column(name = "patient_id")
    private UUID patientId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String title;

    @Column(name = "is_archived", nullable = false)
    @Builder.Default
    private Boolean isArchived = false;
}
