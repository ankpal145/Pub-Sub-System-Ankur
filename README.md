# Pub/Sub System

A simplified in-memory Pub/Sub system implemented in Java using Spring Boot. This system provides WebSocket-based publish/subscribe functionality and REST APIs for topic management.

## Features

- **WebSocket Pub/Sub**: Real-time messaging over WebSocket (`/ws`)
- **REST API**: Topic management and observability endpoints
- **In-Memory Storage**: No external dependencies (Redis, Kafka, RabbitMQ)
- **Concurrency Safe**: Thread-safe implementation using concurrent data structures
- **Fan-Out**: Every subscriber receives each message once
- **Topic Isolation**: Complete isolation between topics
- **Backpressure Handling**: Bounded queues with overflow protection
- **Message Replay**: Support for replaying last N messages on subscription
- **Graceful Shutdown**: Clean shutdown with message flushing

## Architecture

### Components

1. **TopicService**: Manages topics and provides statistics
2. **Topic**: Represents a topic with subscribers and message history
3. **PubSubWebSocketHandler**: Handles WebSocket connections and messages
4. **REST Controllers**: Topic management, health, and stats endpoints

### Design Decisions

#### Concurrency Safety
- Uses `ConcurrentHashMap` for topic and subscriber storage
- Synchronized methods for critical sections (subscribe/unsubscribe)
- Atomic counters for message counting
- Thread-safe ring buffer for message history

#### Backpressure Policy
- **Queue Size**: Each subscriber has a bounded queue of 1000 messages
- **Overflow Handling**: When queue is full, the subscriber is **disconnected** after receiving an `error` with code `SLOW_CONSUMER`
- **Rationale**: Prevents memory exhaustion and ensures fast publishers don't overwhelm slow consumers

#### Message Replay
- Ring buffer maintains last 100 messages per topic
- Subscribers can request replay of the **most recent** N messages (`last_n`, capped at 100)
- Replayed messages are delivered in **chronological order** (oldest → newest)

#### Timestamps
- Server timestamps (`ts`) are emitted as **ISO-8601 strings** (e.g., `"2025-08-25T10:01:00Z"`)
- For `event`, `ts` represents the **delivery timestamp**

#### Fan-Out Implementation
- Each message published to a topic is delivered to all active subscribers
- Uses non-blocking queue operations to prevent blocking publishers
- Each subscriber has its own message processing thread

#### Graceful Shutdown
- Best-effort shutdown: stops background heartbeat and closes sockets as the server stops
- No persistence across restarts (in-memory only)

#### Authentication (optional)
- Supports a simple API key via `X-API-Key` header for both REST and WebSocket.
- When `app.api-key` is set (see configuration), all incoming requests must include a valid key.

## API Documentation

### WebSocket Endpoint: `/ws`

#### Client → Server Messages

**Subscribe**
```json
{
  "type": "subscribe",
  "topic": "orders",
  "client_id": "s1",
  "last_n": 5,
  "request_id": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Unsubscribe**
```json
{
  "type": "unsubscribe",
  "topic": "orders",
  "client_id": "s1",
  "request_id": "340e8400-e29b-41d4-a716-4466554480098"
}
```

**Publish**
```json
{
  "type": "publish",
  "topic": "orders",
  "message": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "payload": {
      "order_id": "ORD-123",
      "amount": 99.5,
      "currency": "USD"
    }
  },
  "request_id": "340e8400-e29b-41d4-a716-4466554480098"
}
```

**Ping**
```json
{
  "type": "ping",
  "request_id": "570t8400-e29b-41d4-a716-4466554412345"
}
```

#### Server → Client Messages

**Ack**
```json
{
  "type": "ack",
  "request_id": "550e8400-e29b-41d4-a716-446655440000",
  "topic": "orders",
  "status": "ok",
  "ts": "2025-08-25T10:00:00Z"
}
```

**Event**
```json
{
  "type": "event",
  "topic": "orders",
  "message": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "payload": {
      "order_id": "ORD-123",
      "amount": 99.5,
      "currency": "USD"
    }
  },
  "ts": "2025-08-25T10:01:00Z"
}
```

**Error**
```json
{
  "type": "error",
  "request_id": "req-67890",
  "topic": "orders",
  "error": {
    "code": "BAD_REQUEST",
    "message": "message.id must be a valid UUID"
  },
  "ts": "2025-08-25T10:02:00Z"
}
```

**Pong**
```json
{
  "type": "pong",
  "request_id": "ping-abc",
  "ts": "2025-08-25T10:03:00Z"
}
```

**Info (Heartbeat)**
```json
{
  "type": "info",
  "msg": "ping",
  "ts": "2025-08-25T10:04:00Z"
}
```

**Info (Topic Deleted)**
```json
{
  "type": "info",
  "topic": "orders",
  "msg": "topic_deleted",
  "ts": "2025-08-25T10:05:00Z"
}
```

### REST Endpoints

#### Create Topic
```http
POST /topics
Content-Type: application/json

{
  "name": "orders"
}
```

**Response (201 Created)**
```json
{
  "status": "created",
  "topic": "orders"
}
```

**Response (409 Conflict)**
```json
{
  "error": "Topic already exists"
}
```

#### Delete Topic
```http
DELETE /topics/{name}
```

**Response (200 OK)**
```json
{
  "status": "deleted",
  "topic": "orders"
}
```

**Response (404 Not Found)**
```json
{
  "error": "Topic not found"
}
```

#### List Topics
```http
GET /topics
```

**Response (200 OK)**
```json
{
  "topics": [
    {
      "name": "orders",
      "subscribers": 3
    }
  ]
}
```

#### Health Check
```http
GET /health
```

**Response (200 OK)**
```json
{
  "uptime_sec": 123,
  "topics": 2,
  "subscribers": 4
}
```

#### Statistics
```http
GET /stats
```

**Response (200 OK)**
```json
{
  "topics": {
    "orders": {
      "messages": 42,
      "subscribers": 3
    }
  }
}
```

## Error Codes

- `BAD_REQUEST`: Invalid request format or missing required fields
- `TOPIC_NOT_FOUND`: Topic does not exist
- `SLOW_CONSUMER`: Subscriber queue overflow (subscriber disconnected)
- `INTERNAL`: Unexpected server error

## Setup & Run (Local)

### Prerequisites

- **Java**: 17+ (`java -version`)
- **Maven**: 3.6+ (`mvn -version`)
- **Git**: optional (to clone the repo)

### Setup steps

1. **Clone**

```bash
git clone <your-repo-url>
cd Pub-Sub-System-Ankur
```

2. **Build**

```bash
mvn clean compile
```

3. **Run**

```bash
mvn spring-boot:run
```

The service starts on:
- **HTTP**: `http://localhost:8080`
- **WebSocket**: `ws://localhost:8080/ws`

### Troubleshooting

- **Port 8080 already in use**
  - Stop the process using port 8080, or change the port in `src/main/resources/application.properties`:

```properties
server.port=8081
```

- **Windows PowerShell note**
  - In Windows PowerShell, `curl` may be an alias for `Invoke-WebRequest`. Postman is recommended for the WebSocket flow.

### Docker

1. Build the Docker image:
```bash
docker build -t pub-sub-system .
```

2. Run the container:
```bash
docker run -p 8080:8080 pub-sub-system
```

## Testing (Postman - REST + WebSocket)

### Start fresh (erase in-memory data)

This service is **in-memory only**. To clear all topics/subscribers/message history, **restart the server process**.

### REST API testing (Postman)

1. **GET** `http://localhost:8080/health`
2. **GET** `http://localhost:8080/topics`
3. **POST** `http://localhost:8080/topics`
   - Header: `Content-Type: application/json`
   - Body:
```json
{"name":"orders"}
```
4. **GET** `http://localhost:8080/topics` (verify topic exists)
5. **GET** `http://localhost:8080/stats`

### WebSocket testing (Postman)

1. Postman → **New** → **WebSocket Request**
2. Connect to: `ws://localhost:8080/ws`

#### Subscribe
```json
{"type":"subscribe","topic":"orders","client_id":"s1","last_n":0,"request_id":"sub-1"}
```
Expected: `ack`

#### Publish (message.id must be a UUID)
```json
{"type":"publish","topic":"orders","message":{"id":"550e8400-e29b-41d4-a716-446655440000","payload":{"order_id":"ORD-123","amount":99.5,"currency":"USD"}},"request_id":"pub-1"}
```
Expected:
- Publisher receives `ack`
- Subscribers receive `event`

#### Replay (`last_n`)

1. Publish a few messages to `orders`
2. Subscribe with `last_n`:

```json
{"type":"subscribe","topic":"orders","client_id":"s2","last_n":2,"request_id":"sub-replay"}
```

Expected: receive the **last 2** events immediately (oldest → newest), then continue with live events.

#### Ping / Pong
```json
{"type":"ping","request_id":"ping-1"}
```
Expected: `pong`

#### Unsubscribe
```json
{"type":"unsubscribe","topic":"orders","client_id":"s1","request_id":"unsub-1"}
```
Expected: `ack`

### Topic delete notification (REST + WS)

1. Ensure a WS client is subscribed to a topic (e.g., `orders`)
2. **DELETE** `http://localhost:8080/topics/orders`
3. Expected WS message:
```json
{"type":"info","topic":"orders","msg":"topic_deleted","ts":"..."}
```

### Notes

- WebSocket client fields accept the assignment’s snake_case keys (`client_id`, `last_n`, `request_id`).
- `request_id` is echoed back on `ack`/`error` when provided.

## Configuration

Configuration can be modified in `src/main/resources/application.properties`:

```properties
server.port=8080
spring.application.name=pub-sub-system
spring.websocket.allowed-origins=*
app.api-key=          # optional, if set all REST + WS calls must send X-API-Key
```

## Limitations

- **No Persistence**: All data is lost on restart
- **Single Instance**: Not designed for horizontal scaling
- **Memory Bound**: Limited by available heap memory
- **No Authentication**: WebSocket and REST endpoints are unauthenticated (can be added as stretch goal)

## Performance Considerations

- **Message History**: Limited to last 100 messages per topic
- **Subscriber Queue**: Maximum 1000 messages per subscriber (can be tuned in code)
- **Concurrent Subscribers**: Designed to handle hundreds of concurrent subscribers per topic
- **Message Throughput**: Optimized for high-throughput scenarios with bounded memory usage

## Notable Edge Cases & Behaviors

- **Unknown message type**: returns `error` with `BAD_REQUEST` and keeps the WS open.
- **Malformed JSON**: returns `error` with `BAD_REQUEST` (`Invalid message format ...`).
- **Missing fields**:
  - Subscribe/Unsubscribe/Publish all validate required `topic`, `client_id`, and `message.id` (UUID).
- **Non-existent topic**:
  - Subscribe/Publish return `TOPIC_NOT_FOUND`; for subscribe, the WS is closed after the error.
- **Duplicate `client_id` on same topic**:
  - Second subscribe fails with `BAD_REQUEST` and that WS is closed (`client_id is already subscribed to this topic`).
- **Unsubscribe when not subscribed**:
  - Treated as idempotent; `ack` is returned even if the client was not subscribed.
- **Topic deletion**:
  - All subscribers receive `info topic_deleted` and their WS sessions are closed.
  - Subsequent operations on that topic return `TOPIC_NOT_FOUND`.
- **Backpressure**:
  - Per-subscriber queue is bounded; overflow triggers `SLOW_CONSUMER` error and closes that subscriber’s WS.

## Future Enhancements

Potential improvements (stretch goals):
- Basic authentication (X-API-Key header)
- Configurable queue sizes and history limits
- Metrics and monitoring endpoints
- Message filtering/subscription patterns
- Multi-instance support with message replication

## License

MIT License - see LICENSE file for details.
