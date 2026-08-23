package com.medai.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.medai.user.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;

    /**
     * Never serialised to the client.
     *
     * <p>The refresh token now travels only as an httpOnly cookie
     * ({@link com.medai.auth.security.RefreshTokenCookie}). It used to be in this body, from where
     * the browser put it in localStorage, which meant any XSS — one bad dependency, one
     * mis-escaped patient name — handed an attacker a seven-day credential that survived the
     * session and could not be seen being used. The field stays because the controller needs the
     * value in order to write the cookie; {@code @JsonIgnore} is what keeps it off the wire.
     */
    @JsonIgnore
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
    private UUID userId;
    private UUID tenantId;
    private String email;
    private String fullName;
    private UserRole role;
    private String tenantName;
}
