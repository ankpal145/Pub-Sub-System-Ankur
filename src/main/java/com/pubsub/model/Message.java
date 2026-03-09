package com.pubsub.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Message {
    private String id;
    private Map<String, Object> payload;

    // The assignment protocol includes delivery timestamp as top-level `ts`.
    // Keep internal timestamp but don't emit it on the wire.
    @JsonIgnore
    private Instant timestamp;

    public Message() {
        this.timestamp = Instant.now();
    }

    public Message(String id, Map<String, Object> payload) {
        this.id = id;
        this.payload = payload;
        this.timestamp = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
