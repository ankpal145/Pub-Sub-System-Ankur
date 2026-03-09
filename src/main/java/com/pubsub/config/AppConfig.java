package com.pubsub.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {
    @Bean
    public ObjectMapper objectMapper() {
        // Ensure Java time types (Instant) and other modules are supported.
        return new ObjectMapper().findAndRegisterModules();
    }
}
