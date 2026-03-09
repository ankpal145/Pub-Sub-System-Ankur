package com.pubsub.config;

import com.pubsub.websocket.PubSubWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class GracefulShutdown implements ApplicationListener<ContextClosedEvent> {
    private final PubSubWebSocketHandler webSocketHandler;

    @Autowired
    public GracefulShutdown(PubSubWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @Override
    public void onApplicationEvent(@NonNull ContextClosedEvent event) {
        // Graceful shutdown: stop accepting new operations
        System.out.println("Initiating graceful shutdown...");
        
        // Shutdown WebSocket handler
        webSocketHandler.shutdown();
        
        // Give some time for in-flight messages to be processed
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("Shutdown complete.");
    }
}
