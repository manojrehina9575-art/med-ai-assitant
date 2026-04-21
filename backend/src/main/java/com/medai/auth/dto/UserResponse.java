package com.medai.auth.dto;

import com.medai.user.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private UUID id;
    private UUID tenantId;
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
    private UserRole role;
    private String specialization;
    private String licenseNumber;
    private String phone;
    private Boolean isActive;
    private Instant lastLoginAt;
    private Instant createdAt;
}
