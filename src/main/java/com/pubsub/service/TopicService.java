package com.pubsub.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class TopicService {
    private final Map<String, Topic> topics = new ConcurrentHashMap<>();
    private final AtomicLong startTime = new AtomicLong(System.currentTimeMillis());

    public void createTopic(String name) {
        topics.computeIfAbsent(name, k -> new Topic(name));
    }

    public boolean deleteTopic(String name) {
        Topic topic = topics.remove(name);
        if (topic != null) {
            topic.notifySubscribersTopicDeleted();
            return true;
        }
        return false;
    }

    public Topic getTopic(String name) {
        return topics.get(name);
    }

    public boolean topicExists(String name) {
        return topics.containsKey(name);
    }

    public List<TopicInfo> listTopics() {
        return topics.values().stream()
                .map(topic -> new TopicInfo(topic.getName(), topic.getSubscriberCount()))
                .collect(Collectors.toList());
    }

    public Map<String, TopicStats> getStats() {
        Map<String, TopicStats> stats = new HashMap<>();
        topics.forEach((name, topic) -> {
            stats.put(name, new TopicStats(topic.getMessageCount(), topic.getSubscriberCount()));
        });
        return stats;
    }

    public int getTotalTopics() {
        return topics.size();
    }

    public int getTotalSubscribers() {
        return topics.values().stream()
                .mapToInt(Topic::getSubscriberCount)
                .sum();
    }

    public long getUptimeSeconds() {
        return (System.currentTimeMillis() - startTime.get()) / 1000;
    }

    public static class TopicInfo {
        private String name;
        private int subscribers;

        public TopicInfo(String name, int subscribers) {
            this.name = name;
            this.subscribers = subscribers;
        }

        public String getName() {
            return name;
        }

        public int getSubscribers() {
            return subscribers;
        }
    }

    public static class TopicStats {
        private long messages;
        private int subscribers;

        public TopicStats(long messages, int subscribers) {
            this.messages = messages;
            this.subscribers = subscribers;
        }

        public long getMessages() {
            return messages;
        }

        public int getSubscribers() {
            return subscribers;
        }
    }
}
