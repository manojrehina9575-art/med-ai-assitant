package com.medai.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Raises Jackson's maximum string length. Multimodal AI requests embed base64-encoded
 * images that can exceed Jackson's 20 MB default {@code maxStringLength}, which otherwise
 * throws {@code StreamConstraintsException} before the request is sent. Image payloads are
 * also downscaled (see {@code ImagePreprocessor}); this is a defensive ceiling.
 */
@Configuration
public class JacksonConfig {

    private static final int MAX_STRING_LENGTH = 50_000_000;

    @Bean
    Jackson2ObjectMapperBuilderCustomizer largePayloadCustomizer() {
        return builder -> builder.postConfigurer(mapper -> mapper.getFactory()
                .setStreamReadConstraints(StreamReadConstraints.builder()
                        .maxStringLength(MAX_STRING_LENGTH)
                        .build()));
    }
}
