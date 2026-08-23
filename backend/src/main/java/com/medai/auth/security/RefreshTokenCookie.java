package com.medai.auth.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

/**
 * Reads and writes the refresh token as an httpOnly cookie.
 *
 * <p>The token was previously returned in the login response body and kept in localStorage by the
 * client, where any script running on the origin could read it. A refresh token is a seven-day
 * credential that mints access tokens, so that single exposure outweighed the rotation and
 * replay-detection the backend does correctly — an attacker did not need to defeat rotation, they
 * could simply take the token.
 *
 * <p>Three properties do the work here:
 * <ul>
 *   <li><b>HttpOnly</b> — invisible to {@code document.cookie}, so XSS cannot exfiltrate it.</li>
 *   <li><b>SameSite=Strict</b> — not attached to cross-site requests, so CSRF cannot spend it.
 *       This is what allows the API to stay stateless with CSRF protection disabled.</li>
 *   <li><b>Path=/api/auth</b> — sent only to the endpoints that need it, so it is absent from
 *       every other request rather than riding along on all of them.</li>
 * </ul>
 */
@Component
@Slf4j
public class RefreshTokenCookie {

    public static final String NAME = "medai_refresh";

    private static final String PATH = "/api/auth";

    private final boolean secure;
    private final long maxAgeSeconds;

    public RefreshTokenCookie(
            @Value("${app.jwt.refresh-cookie-secure:true}") boolean secure,
            @Value("${app.jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs) {
        this.secure = secure;
        this.maxAgeSeconds = refreshExpirationMs / 1000;

        if (!secure) {
            log.warn("Refresh cookie is being issued WITHOUT the Secure flag "
                     + "(app.jwt.refresh-cookie-secure=false). This is for plain-HTTP local "
                     + "development only — over the network the token travels in clear text.");
        }
    }

    /** Issues or replaces the cookie. Called on login, registration, and every rotation. */
    public void set(HttpServletResponse response, String rawRefreshToken) {
        response.addHeader("Set-Cookie", ResponseCookie.from(NAME, rawRefreshToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAgeSeconds)
                .build()
                .toString());
    }

    /** Expires the cookie. The server-side revocation is separate and is what actually matters. */
    public void clear(HttpServletResponse response) {
        response.addHeader("Set-Cookie", ResponseCookie.from(NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(0)
                .build()
                .toString());
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(c -> NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst();
    }
}
