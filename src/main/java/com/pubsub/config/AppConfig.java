package com.pubsub.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {
    @Bean
    public ObjectMapper objectMapper() {
        // Ensure Java time types (Instant) and other modules are supported.
        return new ObjectMapper()
            .findAndRegisterModules()
            // Match assignment examples: ISO-8601 timestamps, not numeric epoch.
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
