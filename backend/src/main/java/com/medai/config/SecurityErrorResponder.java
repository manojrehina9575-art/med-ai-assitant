package com.medai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medai.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Writes filter-chain security failures in the same {@link ApiResponse} shape the controllers use.
 *
 * <p>{@code GlobalExceptionHandler} cannot cover these: they are raised inside the security filter
 * chain, before the request reaches the dispatcher servlet, so {@code @RestControllerAdvice} never
 * sees them. Without an entry point of our own, Spring Security falls back to
 * {@code Http403ForbiddenEntryPoint} — no login mechanism is configured — and answered anonymous
 * requests with 403, which tells a caller "you are known and not allowed" when the truth is
 * "you never authenticated". Missing or invalid credentials are 401; a valid identity without the
 * required role stays 403.
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(jakarta.servlet.http.HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    @Override
    public void handle(jakarta.servlet.http.HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "Access denied");
    }

    private void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(message));
    }
}
