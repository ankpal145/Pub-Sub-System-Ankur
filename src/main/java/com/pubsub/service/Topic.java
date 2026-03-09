package com.pubsub.service;

import com.pubsub.model.Message;
import com.pubsub.util.JsonUtil;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class Topic {
    private final String name;
    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final RingBuffer messageHistory;
    private final AtomicLong messageCount = new AtomicLong(0);
    // Backpressure limit per subscriber (kept deliberately small so overflow is easy to observe in tests)
    private static final int MAX_QUEUE_SIZE = 10;
    private static final int HISTORY_SIZE = 100; // Last N messages for replay

    public Topic(String name) {
        this.name = name;
        this.messageHistory = new RingBuffer(HISTORY_SIZE);
    }

    public String getName() {
        return name;
    }

    /**
     * Subscribe a client to this topic.
     *
     * @return true if subscription succeeded, false if the clientId is already subscribed.
     */
    public synchronized boolean subscribe(String clientId, WebSocketSession session, Integer lastN) {
        if (subscribers.containsKey(clientId)) {
            // Reject duplicate clientId on the same topic
            return false;
        }

        Subscriber subscriber = new Subscriber(clientId, session, name);
        subscribers.put(clientId, subscriber);

        // Replay last N messages if requested
        if (lastN != null && lastN > 0) {
            List<Message> history = messageHistory.getLastN(Math.min(lastN, HISTORY_SIZE));
            for (Message msg : history) {
                subscriber.sendMessage(msg);
            }
        }
        return true;
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
                // Queue overflow - backpressure triggered (disconnect slow consumer)
                disconnectSlowConsumer();
                return false;
            }
            return true;
        }

        private void disconnectSlowConsumer() {
            if (!active) {
                return;
            }
            active = false;
            messageQueue.clear();

            try {
                if (session.isOpen()) {
                    String err = JsonUtil.errorMessageToJson(
                        topicName,
                        null,
                        "SLOW_CONSUMER",
                        "subscriber queue overflow"
                    );
                    session.sendMessage(new org.springframework.web.socket.TextMessage(err));
                    session.close(CloseStatus.POLICY_VIOLATION);
                }
            } catch (Exception ignored) {
                // Best-effort: ignore failures while closing.
            }
        }

        private void startMessageProcessor() {
            Thread processor = new Thread(() -> {
                while (active && session.isOpen()) {
                    try {
                        Message message = messageQueue.poll(1, TimeUnit.SECONDS);
                        if (message != null) {
                            sendToWebSocket(message);
                            // Artificial delay to simulate a slow consumer so backpressure
                            // (queue overflow) is easy to trigger in tests.
                            Thread.sleep(50);
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
                    // Close the WebSocket channel for this topic as part of deletion
                    session.close(CloseStatus.NORMAL);
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

            int toRead = Math.min(n, count);

            // We want the most recent N messages, returned in chronological order (oldest -> newest).
            // writeIndex always points to the next write slot.
            int startIndex;
            if (count < size) {
                // Buffer not wrapped yet: valid range is [0, count)
                startIndex = Math.max(0, count - toRead);
            } else {
                // Buffer wrapped: oldest is at writeIndex, newest is at (writeIndex - 1 + size) % size.
                startIndex = (writeIndex - toRead + size) % size;
            }

            for (int i = 0; i < toRead; i++) {
                int index = (startIndex + i) % size;
                Message msg = buffer[index];
                if (msg != null) {
                    result.add(msg);
                }
            }

            return result;
        }
    }
}
