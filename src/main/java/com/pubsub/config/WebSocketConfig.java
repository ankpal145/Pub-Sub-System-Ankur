package com.pubsub.config;

import com.pubsub.websocket.PubSubWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final PubSubWebSocketHandler webSocketHandler;
    private final ApiKeyHandshakeInterceptor apiKeyHandshakeInterceptor;

    public WebSocketConfig(
            PubSubWebSocketHandler webSocketHandler,
            ApiKeyHandshakeInterceptor apiKeyHandshakeInterceptor
    ) {
        this.webSocketHandler = webSocketHandler;
        this.apiKeyHandshakeInterceptor = apiKeyHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketHandler, "/ws")
                .addInterceptors(apiKeyHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
