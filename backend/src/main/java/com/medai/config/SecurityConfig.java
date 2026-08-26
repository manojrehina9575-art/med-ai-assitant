package com.medai.config;

import com.medai.auth.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SecurityErrorResponder securityErrorResponder;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${app.cors.allowed-origin-patterns:}")
    private String allowedOriginPatterns;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // The FHIR CapabilityStatement is fetched before authentication: it is how
                        // a client discovers what the server supports and where to authenticate.
                        // Behind auth it returns 401 and conformant tooling gives up rather than
                        // guessing. It exposes no patient data — only which resources exist.
                        .requestMatchers(HttpMethod.GET, "/fhir/metadata").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityErrorResponder)
                        .accessDeniedHandler(securityErrorResponder)
                )
                // The refresh token lives in an httpOnly cookie, so the remaining exposure is a
                // script that runs on the origin and drives the API with the user's session. A
                // Content-Security-Policy is what closes that: no third-party script can load, and
                // no inline script executes.
                //
                // 'unsafe-inline' remains on style-src because Tailwind and the Radix primitives
                // set element styles directly. Inline *styles* cannot execute code; inline
                // *scripts* can, and script-src does not allow them.
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(String.join("; ",
                                "default-src 'self'",
                                "script-src 'self'",
                                "style-src 'self' 'unsafe-inline'",
                                "img-src 'self' data: blob:",
                                "font-src 'self' data:",
                                "connect-src 'self'",
                                "frame-ancestors 'none'",
                                "form-action 'self'",
                                "base-uri 'self'",
                                "object-src 'none'")))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                        .ReferrerPolicy.NO_REFERRER))
                        // 2 years, preloadable. PHI must never travel over plain HTTP, and a
                        // downgrade is not something the user should be able to click through.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(63072000))
                        .permissionsPolicy(permissions -> permissions.policy(
                                "camera=(), microphone=(), geolocation=(), payment=(), usb=()"))
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .toList();

        config.setAllowedOrigins(origins);

        // Every hospital is served at its own <workspace>.<base-domain> host, so the set of valid
        // origins is open-ended and cannot be enumerated the way allowed-origins is — a new tenant
        // would otherwise need a redeploy to be reachable. Deployments pass their own zone here
        // (https://*.medaiclinical.com); the localhost entries keep multi-tenant testing working
        // against the dev server.
        //
        // This is defence in depth rather than the mechanism tenants rely on: in production the
        // SPA calls a relative /api on the host it was loaded from, so tenant traffic is
        // same-origin and never preflighted at all.
        List<String> patterns = new ArrayList<>(List.of(
                "http://*.localhost:[*]",
                "http://localhost:[*]",
                "https://*.localhost:[*]",
                "http://*.127.0.0.1:[*]"
        ));
        Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(p -> !p.isEmpty())
                .forEach(patterns::add);
        config.setAllowedOriginPatterns(patterns);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
