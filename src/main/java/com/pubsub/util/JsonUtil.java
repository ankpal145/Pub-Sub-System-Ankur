package com.pubsub.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pubsub.model.Message;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@SuppressWarnings("unchecked")

public class JsonUtil {
    private static final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public static String messageToJson(Message message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (IOException e) {
            // Fallback to manual serialization
            return fallbackMessageToJson(message);
        }
    }

    public static String eventMessageToJson(String topicName, Message message) {
        try {
            Map<String, Object> event = Map.of(
                "type", "event",
                "topic", topicName,
                "message", message,
                "ts", Instant.now().toString()
            );
            return objectMapper.writeValueAsString(event);
        } catch (IOException e) {
            return fallbackEventMessageToJson(topicName, message);
        }
    }

    public static String infoMessageToJson(String topicName, String msg) {
        try {
            Map<String, Object> info = Map.of(
                "type", "info",
                "topic", topicName,
                "msg", msg,
                "ts", Instant.now().toString()
            );
            return objectMapper.writeValueAsString(info);
        } catch (IOException e) {
            return "{\"type\":\"info\",\"topic\":\"" + topicName + 
                   "\",\"msg\":\"" + msg + "\",\"ts\":\"" + Instant.now() + "\"}";
        }
    }

    private static String fallbackMessageToJson(Message message) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"id\":\"").append(escapeJson(message.getId())).append("\"");
        if (message.getPayload() != null) {
            sb.append(",\"payload\":").append(payloadToJson(message.getPayload()));
        }
        sb.append("}");
        return sb.toString();
    }

    private static String fallbackEventMessageToJson(String topicName, Message message) {
        return "{\"type\":\"event\",\"topic\":\"" + escapeJson(topicName) + 
               "\",\"message\":" + fallbackMessageToJson(message) + 
               ",\"ts\":\"" + Instant.now() + "\"}";
    }

    private static String payloadToJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            return fallbackPayloadToJson(payload);
        }
    }

    private static String fallbackPayloadToJson(Map<String, Object> payload) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
            sb.append(valueToJson(entry.getValue()));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private static String valueToJson(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "\"" + escapeJson((String) value) + "\"";
        } else if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof Map) {
            return fallbackPayloadToJson((Map<String, Object>) value);
        } else {
            // For other types, try to serialize with ObjectMapper
            try {
                return objectMapper.writeValueAsString(value);
            } catch (IOException e) {
                return "\"" + escapeJson(value.toString()) + "\"";
            }
        }
    }

    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
