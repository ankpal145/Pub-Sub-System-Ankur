package com.pubsub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ApiKeyValidator {

    private final String requiredApiKey;

    public ApiKeyValidator(@Value("${app.api-key:}") String requiredApiKey) {
        this.requiredApiKey = requiredApiKey == null ? "" : requiredApiKey.trim();
    }

    /**
     * If no key is configured (empty), auth is effectively disabled.
     */
    public boolean isEnabled() {
        return !requiredApiKey.isEmpty();
    }

    public boolean isValid(String provided) {
        if (!isEnabled()) {
            return true; // auth disabled
        }
        if (provided == null) {
            return false;
        }
        return provided.trim().equals(requiredApiKey);
    }
}

