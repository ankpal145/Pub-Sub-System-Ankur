package com.pubsub.controller;

import com.pubsub.service.TopicService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthController {
    private final TopicService topicService;

    public HealthController(TopicService topicService) {
        this.topicService = topicService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("uptime_sec", topicService.getUptimeSeconds());
        response.put("topics", topicService.getTotalTopics());
        response.put("subscribers", topicService.getTotalSubscribers());
        return ResponseEntity.ok(response);
    }
}
