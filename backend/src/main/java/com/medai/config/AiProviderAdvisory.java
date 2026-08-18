package com.medai.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Logs a prominent warning at startup when patient data would be sent to an AI provider that has
 * not been declared as covered by a data agreement.
 *
 * <p>Medical images and clinical notes are posted to whatever {@code spring.ai.openai.base-url}
 * points at. A public inference endpoint's standard terms are not a HIPAA Business Associate
 * Agreement and not a DPDP-grade processing agreement, so running real patient data through one is
 * a disclosure. That is a commercial and legal decision, not something code can settle — but it
 * should never happen unnoticed, which is what this makes impossible.
 *
 * <p>Set {@code app.ai.data-agreement-in-place=true} once an agreement is signed with the
 * configured provider, and the warning stops.
 */
@Configuration
@Slf4j
public class AiProviderAdvisory {

    /** Hosts reachable only from the deployment itself, where no third-party disclosure occurs. */
    private static final List<String> LOCAL_HOSTS = List.of("localhost", "127.0.0.1", "0.0.0.0", "host.docker.internal");

    @Value("${spring.ai.openai.base-url:}")
    private String baseUrl;

    @Value("${app.ai.data-agreement-in-place:false}")
    private boolean dataAgreementInPlace;

    @PostConstruct
    void warnIfPhiWouldLeaveWithoutAnAgreement() {
        if (baseUrl == null || baseUrl.isBlank() || dataAgreementInPlace || isLocal(baseUrl)) {
            return;
        }

        log.warn("""

                ============================================================================
                 AI provider: {}

                 Medical images and clinical notes sent for analysis will leave this server
                 and reach that provider. No data agreement has been declared for it
                 (app.ai.data-agreement-in-place=false).

                 Use synthetic or de-identified data until either:
                   - a BAA / data processing agreement is signed with this provider, or
                   - the base URL points at a self-hosted model.

                 Then set app.ai.data-agreement-in-place=true to silence this warning.
                ============================================================================
                """, baseUrl);
    }

    private static boolean isLocal(String url) {
        String lower = url.toLowerCase();
        return LOCAL_HOSTS.stream().anyMatch(host -> lower.contains("://" + host));
    }
}
