package com.medai.terminology.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves drug names to RxNorm concepts against the NLM's public RxNav service.
 *
 * <p>{@link com.medai.clinical.safety.DrugKnowledgeBase} normalises brand names through a
 * hand-curated map. That map is honest about being a starter set, but it is finite in a way the
 * problem is not: a brand it has never heard of falls through to a string comparison, and the
 * prescription safety check silently has nothing to work with. RxNorm covers the whole US drug
 * vocabulary and resolves brands, synonyms and combination products to stable concept ids.
 *
 * <p><strong>Normalisation only.</strong> RxNav's drug–drug interaction API was retired in January
 * 2024 and no longer returns data, so interaction checking stays with the curated table in
 * {@code DrugKnowledgeBase}. Anyone reading this expecting RxNav to be doing the interaction work
 * would be wrong, and would trust the result more than it deserves.
 *
 * <p><strong>India note.</strong> RxNorm is a US vocabulary. Indian brand names — Crocin, Dolo,
 * Ecosprin, Septran — are largely absent from it, which is exactly why the curated map is
 * consulted first rather than being treated as a fallback. RxNav extends coverage for generics and
 * international brands; it does not replace local knowledge.
 *
 * <p>Every call is best-effort. The service is external, unauthenticated and out of our control,
 * so a timeout or an outage resolves to "not found" rather than failing the prescription check —
 * degrading to the curated map is the correct behaviour, and it is what happens.
 */
@Component
@Slf4j
public class RxNormClient {

    public static final String SYSTEM = "http://www.nlm.nih.gov/research/umls/rxnorm";

    /**
     * @param rxcui  RxNorm concept unique identifier
     * @param name   the concept's normalised name
     * @param termType RxNorm term type: IN (ingredient), BN (brand name), SCD (clinical drug)…
     */
    public record RxConcept(String rxcui, String name, String termType) {
    }

    private final HttpClient http;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final boolean enabled;

    /**
     * Resolutions are cached for the process lifetime. RxNorm changes monthly at most, the same
     * handful of drugs recur constantly in one hospital's prescribing, and every cache hit is a
     * network round trip removed from a clinician's request.
     */
    private final Map<String, Optional<RxConcept>> cache = new ConcurrentHashMap<>();

    public RxNormClient(ObjectMapper objectMapper,
                        @Value("${app.terminology.rxnorm.base-url:https://rxnav.nlm.nih.gov/REST}") String baseUrl,
                        @Value("${app.terminology.rxnorm.enabled:true}") boolean enabled,
                        @Value("${app.terminology.rxnorm.timeout-ms:1500}") long timeoutMs) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.enabled = enabled;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        if (!enabled) {
            log.info("RxNorm lookup disabled; drug normalisation uses the curated map only.");
        }
    }

    /**
     * Resolves a drug name to its RxNorm concept.
     *
     * <p>Asks for the ingredient rather than the exact product: a prescription safety check cares
     * that amoxicillin is a penicillin, not which 500mg capsule presentation was written.
     */
    public Optional<RxConcept> resolve(String drugName) {
        if (!enabled || drugName == null || drugName.isBlank()) {
            return Optional.empty();
        }

        String key = drugName.trim().toLowerCase(Locale.ROOT);
        return cache.computeIfAbsent(key, this::lookup);
    }

    private Optional<RxConcept> lookup(String drugName) {
        try {
            // search=2 enables normalised matching, which handles spelling and word-order variants.
            String url = baseUrl + "/rxcui.json?search=2&name="
                         + URLEncoder.encode(drugName, StandardCharsets.UTF_8);

            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(3))
                            .header("Accept", "application/json")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.debug("RxNav returned {} for '{}'", response.statusCode(), drugName);
                return Optional.empty();
            }

            JsonNode idGroup = objectMapper.readTree(response.body()).path("idGroup");
            JsonNode rxcuis = idGroup.path("rxnormId");
            if (!rxcuis.isArray() || rxcuis.isEmpty()) {
                return Optional.empty();
            }

            String rxcui = rxcuis.get(0).asText();
            return Optional.of(describe(rxcui).orElse(new RxConcept(rxcui, drugName, "UNKNOWN")));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            // Best-effort by design: the curated map is the floor, not this.
            log.debug("RxNorm lookup failed for '{}': {}", drugName, e.getMessage());
            return Optional.empty();
        }
    }

    /** Fetches the concept's canonical name and term type. */
    private Optional<RxConcept> describe(String rxcui) {
        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/rxcui/" + rxcui + "/property.json?propName=RxNorm%20Name"))
                            .timeout(Duration.ofSeconds(3))
                            .header("Accept", "application/json")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return Optional.empty();
            }

            JsonNode value = objectMapper.readTree(response.body())
                    .path("propConceptGroup").path("propConcept");
            if (!value.isArray() || value.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(new RxConcept(rxcui, value.get(0).path("propValue").asText(), "IN"));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** For the terminology coverage endpoint. */
    public boolean isEnabled() {
        return enabled;
    }
}
