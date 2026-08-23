package com.medai.fhir.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * FHIR R4 context and parser.
 *
 * <p>{@link FhirContext} is expensive to construct — it scans and indexes the whole R4 model — and
 * is documented as thread-safe and intended to be created once per application. Building one per
 * request is the standard way to make a FHIR facade slow for no reason.
 *
 * <p>The parser is configured to omit empty elements and to suppress narrative generation, which
 * keeps responses small; nothing here consumes {@code Resource.text}.
 */
@Configuration
public class FhirConfig {

    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();
    }

    @Bean
    public IParser fhirJsonParser(FhirContext fhirContext) {
        return fhirContext.newJsonParser()
                .setPrettyPrint(false)
                .setOmitResourceId(false)
                .setSuppressNarratives(true);
    }
}
