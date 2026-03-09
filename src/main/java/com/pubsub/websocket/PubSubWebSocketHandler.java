package com.pubsub.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pubsub.model.ClientMessage;
import com.pubsub.model.Message;
import com.pubsub.model.ServerMessage;
import com.pubsub.service.TopicService;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class PubSubWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(PubSubWebSocketHandler.class);
    private final TopicService topicService;
    private final ObjectMapper objectMapper;
    private final Map<WebSocketSession, SessionInfo> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(1);

    public PubSubWebSocketHandler(TopicService topicService, ObjectMapper objectMapper) {
        this.topicService = topicService;
        this.objectMapper = objectMapper;
        startHeartbeat();
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        sessions.put(session, new SessionInfo());
        log.info("WS connected: sessionId={}, remote={}", session.getId(), session.getRemoteAddress());
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        log.info("WS closed: sessionId={}, status={}", session.getId(), status);
        SessionInfo info = sessions.remove(session);
        if (info != null) {
            // Unsubscribe from all topics
            info.getSubscribedTopics().forEach(topicName -> {
                var topic = topicService.getTopic(topicName);
                if (topic != null) {
                    topic.unsubscribe(info.getClientId());
                }
            });
        }
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage textMessage) {
        try {
            log.info("WS recv: sessionId={}, payload={}", session.getId(), textMessage.getPayload());
            ClientMessage clientMsg = objectMapper.readValue(textMessage.getPayload(), ClientMessage.class);
            handleClientMessage(session, clientMsg);
        } catch (Exception e) {
            log.warn("WS parse/handle failed: sessionId={}, err={}", session.getId(), e.toString());
            sendError(session, null, "BAD_REQUEST", "Invalid message format: " + e.getMessage());
        }
    }

    private void handleClientMessage(WebSocketSession session, ClientMessage clientMsg) {
        SessionInfo info = sessions.get(session);
        if (info == null) {
            info = new SessionInfo();
            sessions.put(session, info);
        }

        String type = clientMsg.getType();
        if (type == null) {
            sendError(session, clientMsg.getRequestId(), "BAD_REQUEST", "Missing type field");
            return;
        }

        switch (type.toLowerCase()) {
            case "subscribe":
                handleSubscribe(session, clientMsg, info);
                break;
            case "unsubscribe":
                handleUnsubscribe(session, clientMsg, info);
                break;
            case "publish":
                handlePublish(session, clientMsg);
                break;
            case "ping":
                handlePing(session, clientMsg);
                break;
            default:
                sendError(session, clientMsg.getRequestId(), "BAD_REQUEST", "Unknown message type: " + type);
        }
    }

    private void handleSubscribe(WebSocketSession session, ClientMessage clientMsg, SessionInfo info) {
        String topic = clientMsg.getTopic();
        String clientId = clientMsg.getClientId();

        if (topic == null || topic.isEmpty()) {
            sendError(session, clientMsg.getRequestId(), "BAD_REQUEST", "Topic is required for subscribe");
            return;
        }

        if (clientId == null || clientId.isEmpty()) {
            sendError(session, clientMsg.getRequestId(), "BAD_REQUEST", "client_id is required for subscribe");
            return;
        }

        if (!topicService.topicExists(topic)) {
            sendError(session, clientMsg.getRequestId(), "TOPIC_NOT_FOUND", "Topic does not exist: " + topic);
            return;
        }

        info.setClientId(clientId);
        var topicObj = topicService.getTopic(topic);
        topicObj.subscribe(clientId, session, clientMsg.getLastN());
        info.addSubscribedTopic(topic);

        ServerMessage ack = new ServerMessage();
        ack.setType("ack");
        ack.setRequestId(clientMsg.getRequestId());
        ack.setTopic(topic);
        ack.setStatus("ok");
        sendMessage(session, ack);
    }

    private void handleUnsubscribe(WebSocketSession session, ClientMessage clientMsg, SessionInfo info) {
        String topic = clientMsg.getTopic();
        String clientId = clientMsg.getClientId();

        if (topic == null || topic.isEmpty()) {
            sendError(session, clientMsg.getRequestId(), "BAD_REQUEST", "Topic is required for unsubscribe");
            return;
        }

        if (clientId == null || clientId.isEmpty()) {
            sendError(session, clientMsg.getRequestId(), "BAD_REQUEST", "client_id is required for unsubscribe");
            return;
        }

        var topicObj = topicService.getTopic(topic);
        if (topicObj != null) {
            topicObj.unsubscribe(clientId);
            info.removeSubscribedTopic(topic);
        }

        ServerMessage ack = new ServerMessage();
        ack.setType("ack");
        ack.setRequestId(clientMsg.getRequestId());
        ack.setTopic(topic);
        ack.setStatus("ok");
        sendMessage(session, ack);
    }

    private void handlePublish(WebSocketSession session, ClientMessage clientMsg) {
        String topic = clientMsg.getTopic();
        Message message = clientMsg.getMessage();

        if (topic == null || topic.isEmpty()) {
            sendError(session, clientMsg.getRequestId(), "BAD_REQUEST", "Topic is required for publish");
            return;
        }

        if (message == null) {
            sendError(session, clientMsg.getRequestId(), "BAD_REQUEST", "Message is required for publish");
            return;
        }

        if (message.getId() == null || message.getId().isEmpty()) {
            sendError(session, clientMsg.getRequestId(), "BAD_REQUEST", "message.id must be a valid UUID");
            return;
        }

        // Validate UUID format
        try {
            UUID.fromString(message.getId());
        } catch (IllegalArgumentException e) {
            sendError(session, clientMsg.getRequestId(), "BAD_REQUEST", "message.id must be a valid UUID");
            return;
        }

        if (!topicService.topicExists(topic)) {
            sendError(session, clientMsg.getRequestId(), "TOPIC_NOT_FOUND", "Topic does not exist: " + topic);
            return;
        }

        var topicObj = topicService.getTopic(topic);
        topicObj.publish(message);

        ServerMessage ack = new ServerMessage();
        ack.setType("ack");
        ack.setRequestId(clientMsg.getRequestId());
        ack.setTopic(topic);
        ack.setStatus("ok");
        sendMessage(session, ack);
    }

    private void handlePing(WebSocketSession session, ClientMessage clientMsg) {
        ServerMessage pong = new ServerMessage();
        pong.setType("pong");
        pong.setRequestId(clientMsg.getRequestId());
        sendMessage(session, pong);
    }

    private void sendError(WebSocketSession session, String requestId, String code, String message) {
        ServerMessage error = new ServerMessage();
        error.setType("error");
        error.setRequestId(requestId);
        error.setError(new ServerMessage.ErrorInfo(code, message));
        sendMessage(session, error);
    }

    private void sendMessage(WebSocketSession session, ServerMessage message) {
        try {
            if (session.isOpen()) {
                String json = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(json));
                log.info("WS send: sessionId={}, payload={}", session.getId(), json);
            }
        } catch (IOException e) {
            // Connection closed or error - ignore
            log.warn("WS send failed: sessionId={}, err={}", session.getId(), e.toString());
        }
    }

    private void startHeartbeat() {
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            ServerMessage heartbeat = new ServerMessage();
            heartbeat.setType("info");
            heartbeat.setMsg("ping");

            sessions.keySet().forEach(session -> {
                try {
                    if (session.isOpen()) {
                        sendMessage(session, heartbeat);
                    }
                } catch (Exception e) {
                    // Ignore
                }
            });
        }, 30, 30, TimeUnit.SECONDS);
    }

    public void shutdown() {
        heartbeatExecutor.shutdown();
        try {
            if (!heartbeatExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                heartbeatExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            heartbeatExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static class SessionInfo {
        private String clientId;
        private final java.util.Set<String> subscribedTopics = ConcurrentHashMap.newKeySet();

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public java.util.Set<String> getSubscribedTopics() {
            return subscribedTopics;
        }

        public void addSubscribedTopic(String topic) {
            subscribedTopics.add(topic);
        }

        public void removeSubscribedTopic(String topic) {
            subscribedTopics.remove(topic);
        }
    }
}
