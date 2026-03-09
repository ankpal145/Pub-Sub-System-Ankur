package com.pubsub.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClientMessage {
    private String type; // subscribe, unsubscribe, publish, ping
    private String topic;
    private Message message;

    @JsonProperty("client_id")
    @JsonAlias({ "clientId" })
    private String clientId;

    @JsonProperty("last_n")
    @JsonAlias({ "lastN" })
    private Integer lastN;

    @JsonProperty("request_id")
    @JsonAlias({ "requestId" })
    private String requestId;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public Integer getLastN() {
        return lastN;
    }

    public void setLastN(Integer lastN) {
        this.lastN = lastN;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
