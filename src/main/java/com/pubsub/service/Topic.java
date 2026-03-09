package com.pubsub.service;

import com.pubsub.model.Message;
import com.pubsub.util.JsonUtil;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class Topic {
    private final String name;
    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final RingBuffer messageHistory;
    private final AtomicLong messageCount = new AtomicLong(0);
    private static final int MAX_QUEUE_SIZE = 1000; // Backpressure limit per subscriber
    private static final int HISTORY_SIZE = 100; // Last N messages for replay

    public Topic(String name) {
        this.name = name;
        this.messageHistory = new RingBuffer(HISTORY_SIZE);
    }

    public String getName() {
        return name;
    }

    public synchronized void subscribe(String clientId, WebSocketSession session, Integer lastN) {
        Subscriber subscriber = new Subscriber(clientId, session, name);
        subscribers.put(clientId, subscriber);

        // Replay last N messages if requested
        if (lastN != null && lastN > 0) {
            List<Message> history = messageHistory.getLastN(Math.min(lastN, HISTORY_SIZE));
            for (Message msg : history) {
                subscriber.sendMessage(msg);
            }
        }
    }

    public synchronized void unsubscribe(String clientId) {
        Subscriber subscriber = subscribers.remove(clientId);
        if (subscriber != null) {
            subscriber.close();
        }
    }

    public void publish(Message message) {
        messageCount.incrementAndGet();
        messageHistory.add(message);

        // Fan-out: deliver to all subscribers
        List<Subscriber> subscriberList = new ArrayList<>(subscribers.values());
        for (Subscriber subscriber : subscriberList) {
            if (!subscriber.sendMessage(message)) {
                // Queue overflow - disconnect subscriber
                unsubscribe(subscriber.getClientId());
            }
        }
    }

    public int getSubscriberCount() {
        return subscribers.size();
    }

    public long getMessageCount() {
        return messageCount.get();
    }

    public List<Message> getHistory(int n) {
        return messageHistory.getLastN(n);
    }

    public void notifySubscribersTopicDeleted() {
        List<Subscriber> subscriberList = new ArrayList<>(subscribers.values());
        for (Subscriber subscriber : subscriberList) {
            subscriber.notifyTopicDeleted();
        }
        subscribers.clear();
    }

    private static class Subscriber {
        private final String clientId;
        private final WebSocketSession session;
        private final BlockingQueue<Message> messageQueue;
        private final String topicName;
        private volatile boolean active = true;

        public Subscriber(String clientId, WebSocketSession session, String topicName) {
            this.clientId = clientId;
            this.session = session;
            this.topicName = topicName;
            this.messageQueue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
            startMessageProcessor();
        }

        public String getClientId() {
            return clientId;
        }

        public boolean sendMessage(Message message) {
            if (!active || !session.isOpen()) {
                return false;
            }
            // Non-blocking offer - returns false if queue is full
            boolean offered = messageQueue.offer(message);
            if (!offered) {
                // Queue overflow - backpressure triggered
                return false;
            }
            return true;
        }

        private void startMessageProcessor() {
            Thread processor = new Thread(() -> {
                while (active && session.isOpen()) {
                    try {
                        Message message = messageQueue.poll(1, TimeUnit.SECONDS);
                        if (message != null) {
                            sendToWebSocket(message);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        // Connection error - stop processing
                        break;
                    }
                }
            });
            processor.setDaemon(true);
            processor.start();
        }

        private void sendToWebSocket(Message message) {
            try {
                if (session.isOpen()) {
                    String json = JsonUtil.eventMessageToJson(topicName, message);
                    session.sendMessage(new org.springframework.web.socket.TextMessage(json));
                }
            } catch (Exception e) {
                active = false;
            }
        }

        public void notifyTopicDeleted() {
            try {
                if (session.isOpen()) {
                    String json = JsonUtil.infoMessageToJson(topicName, "topic_deleted");
                    session.sendMessage(new org.springframework.web.socket.TextMessage(json));
                }
            } catch (Exception e) {
                // Ignore
            }
            close();
        }

        public void close() {
            active = false;
            messageQueue.clear();
        }
    }

    private static class RingBuffer {
        private final Message[] buffer;
        private final int size;
        private int writeIndex = 0;
        private int count = 0;

        public RingBuffer(int size) {
            this.size = size;
            this.buffer = new Message[size];
        }

        public synchronized void add(Message message) {
            buffer[writeIndex] = message;
            writeIndex = (writeIndex + 1) % size;
            if (count < size) {
                count++;
            }
        }

        public synchronized List<Message> getLastN(int n) {
            List<Message> result = new ArrayList<>();
            if (count == 0) {
                return result;
            }

            int startIndex;
            if (count < size) {
                startIndex = 0;
            } else {
                startIndex = writeIndex;
            }

            int toRead = Math.min(n, count);
            for (int i = 0; i < toRead; i++) {
                int index = (startIndex + i) % size;
                if (buffer[index] != null) {
                    result.add(buffer[index]);
                }
            }

            return result;
        }
    }
}
