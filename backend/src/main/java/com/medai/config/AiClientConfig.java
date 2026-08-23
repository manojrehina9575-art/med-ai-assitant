package com.medai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A single shared {@link ChatClient}.
 *
 * <p>Callers used to hold a {@code ChatClient.Builder} and call {@code build()} on every request.
 * A {@code ChatClient} is stateless and thread-safe, so building one per call bought nothing and
 * cost an allocation plus a fresh advisor chain each time. Having one bean also gives a single
 * place to attach observability, default options and retry advisors later.
 */
@Configuration
public class AiClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
