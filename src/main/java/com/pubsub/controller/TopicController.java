package com.pubsub.controller;

import com.pubsub.service.TopicService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/topics")
public class TopicController {
    private final TopicService topicService;

    public TopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createTopic(@RequestBody Map<String, String> request) {
        String name = request.get("name");
        if (name == null || name.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Topic name is required");
            return ResponseEntity.badRequest().body(error);
        }

        if (topicService.topicExists(name)) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Topic already exists");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }

        topicService.createTopic(name);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "created");
        response.put("topic", name);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Map<String, Object>> deleteTopic(@PathVariable String name) {
        boolean deleted = topicService.deleteTopic(name);
        if (!deleted) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Topic not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "deleted");
        response.put("topic", name);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listTopics() {
        List<TopicService.TopicInfo> topics = topicService.listTopics();
        Map<String, Object> response = new HashMap<>();
        response.put("topics", topics);
        return ResponseEntity.ok(response);
    }
}
