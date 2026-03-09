package com.pubsub.config;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;

@Component
public class ApiKeyHandshakeInterceptor implements HandshakeInterceptor {

    private static final String HEADER_NAME = "X-API-Key";

    private final ApiKeyValidator apiKeyValidator;

    public ApiKeyHandshakeInterceptor(ApiKeyValidator apiKeyValidator) {
        this.apiKeyValidator = apiKeyValidator;
    }

    @Override
    public boolean beforeHandshake(
            @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            @NonNull Map<String, Object> attributes
    ) {
        if (!apiKeyValidator.isEnabled()) {
            return true; // auth disabled
        }

        List<String> headers = request.getHeaders().get(HEADER_NAME);
        String apiKey = (headers != null && !headers.isEmpty()) ? headers.get(0) : null;
        if (apiKeyValidator.isValid(apiKey)) {
            return true;
        }

        response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
        return false;
    }

    @Override
    public void afterHandshake(
            @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            @Nullable Exception exception
    ) {
        // no-op
    }
}

