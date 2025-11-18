---
id: configuration
title: Event Bus Configuration
sidebar_label: Configuration
sidebar_position: 2
description: Drop-in configuration for EventBus and ToolCallEventBus backends (InMemory, Redis, Kafka)
---

# Event Bus Configuration

Spice Framework provides two layers of event bus infrastructure that are fully drop-in configurable:

1. **EventBus** - Core event bus for graph execution events
2. **ToolCallEventBus** - Specialized event bus for tool call lifecycle events

Both support three backends: **InMemory** (dev/testing), **Redis Streams** (distributed), and **Kafka** (high-throughput).

## Quick Start

### Spring Boot Applications

Configure via `application.yml`:

```yaml
spice:
  # Core EventBus Configuration
  events:
    enabled: true
    backend: KAFKA  # Options: IN_MEMORY, REDIS_STREAMS, KAFKA
    kafka:
      bootstrapServers: localhost:9092
      topic: spice.events
      acks: all

  # ToolCallEventBus Configuration
  tool-call-event-bus:
    enabled: true
    backend: REDIS_STREAMS  # Options: IN_MEMORY, REDIS_STREAMS, KAFKA
    redis-streams:
      stream-key: spice:toolcall:events
    history:
      enabled: true
      size: 1000

  # Redis Configuration (shared by both)
  redis:
    enabled: true
    host: localhost
    port: 6379
```

### Non-Spring (Kotlin) Applications

Use factory pattern:

```kotlin
import io.github.noailabs.spice.events.EventBusFactory
import io.github.noailabs.spice.event.ToolCallEventBusFactory

// EventBus - Kafka
val eventBus = EventBusFactory.kafka {
    bootstrapServers = "localhost:9092"
    topic = "spice.events"
    acks = "all"
}

// ToolCallEventBus - Redis
val toolCallEventBus = ToolCallEventBusFactory.redisStreams {
    host = "localhost"
    port = 6379
    streamKey = "spice:toolcall:events"
}
```

---

## EventBus Configuration

### InMemory Backend (Dev/Testing)

**Spring Boot:**

```yaml
spice:
  events:
    enabled: true
    backend: IN_MEMORY
```

**Non-Spring:**

```kotlin
val eventBus = EventBusFactory.inMemory()
```

**Characteristics:**
- ✅ Fast (no network I/O)
- ✅ Simple (no external dependencies)
- ❌ Ephemeral (events lost on restart)
- ❌ Single-instance only (no distribution)

**Use Cases:**
- Development environment
- Unit tests
- Integration tests
- Single-instance deployments

---

### Redis Streams Backend (Distributed)

**Spring Boot:**

```yaml
spice:
  events:
    enabled: true
    backend: REDIS_STREAMS
    redis-streams:
      stream-key: spice:events
      consumer-prefix: spice-events
      poll-timeout: 1s
      batch-size: 100

  redis:
    enabled: true
    host: redis.example.com
    port: 6379
    password: ${REDIS_PASSWORD}
    ssl: true
    database: 0
```

**Non-Spring:**

```kotlin
val eventBus = EventBusFactory.redisStreams {
    host = "redis.example.com"
    port = 6379
    password = System.getenv("REDIS_PASSWORD")
    ssl = true
    database = 0
    streamKey = "spice:events"
    consumerPrefix = "spice-events"
    batchSize = 100
    pollTimeout = 1.seconds
}
```

**Characteristics:**
- ✅ Distributed (multi-instance support)
- ✅ Persistent (events survive restarts)
- ✅ Consumer groups (load balancing)
- ⚠️ Requires Redis 5.0+
- ⚠️ Manual stream trimming needed for long-running streams

**Use Cases:**
- Multi-instance production deployments
- Event replay requirements
- Cross-service orchestration

---

### Kafka Backend (High-Throughput)

**Spring Boot:**

```yaml
spice:
  events:
    enabled: true
    backend: KAFKA
    kafka:
      bootstrap-servers: kafka-1:9092,kafka-2:9092,kafka-3:9092
      topic: spice.events
      client-id: spice-eventbus
      poll-timeout: 1s
      acks: all
      # Optional: SASL/SSL security
      security-protocol: SASL_SSL
      sasl-mechanism: SCRAM-SHA-512
      sasl-jaas-config: |
        org.apache.kafka.common.security.scram.ScramLoginModule required
        username="spice-user"
        password="${KAFKA_PASSWORD}";
```

**Non-Spring:**

```kotlin
val eventBus = EventBusFactory.kafka {
    bootstrapServers = "kafka-1:9092,kafka-2:9092,kafka-3:9092"
    topic = "spice.events"
    clientId = "spice-eventbus"
    pollTimeout = 1.seconds
    acks = "all"

    // Optional: SASL/SSL security
    securityProtocol = "SASL_SSL"
    saslMechanism = "SCRAM-SHA-512"
    saslJaasConfig = """
        org.apache.kafka.common.security.scram.ScramLoginModule required
        username="spice-user"
        password="${System.getenv("KAFKA_PASSWORD")}";
    """.trimIndent()
}
```

**Characteristics:**
- ✅ High-throughput (millions of events/second)
- ✅ Strongly durable (replicated across brokers)
- ✅ Consumer groups (load balancing)
- ✅ Long-term retention (configurable)
- ✅ Idempotent producer (prevents duplicates)
- ⚠️ Requires Kafka 2.0+ cluster
- ⚠️ More complex infrastructure

**Use Cases:**
- High-scale production deployments
- Multi-region orchestration
- Event sourcing with long-term retention
- High-throughput workloads (>10k events/sec)

---

## ToolCallEventBus Configuration

### InMemory Backend (Dev/Testing)

**Spring Boot:**

```yaml
spice:
  tool-call-event-bus:
    enabled: true
    backend: IN_MEMORY
    history:
      enabled: true
      size: 1000
      enable-metrics: true
```

**Non-Spring:**

```kotlin
val eventBus = ToolCallEventBusFactory.inMemory(
    config = EventBusConfig(
        enableHistory = true,
        historySize = 1000,
        enableMetrics = true
    )
)
```

**Same characteristics as EventBus InMemory backend.**

---

### Redis Streams Backend (Distributed)

**Spring Boot:**

```yaml
spice:
  tool-call-event-bus:
    enabled: true
    backend: REDIS_STREAMS
    redis-streams:
      stream-key: spice:toolcall:events
      poll-interval: 1s
    history:
      enabled: true
      size: 1000
      enable-metrics: true

  redis:
    enabled: true
    host: redis.example.com
    port: 6379
    password: ${REDIS_PASSWORD}
    ssl: true
```

**Non-Spring:**

```kotlin
val eventBus = ToolCallEventBusFactory.redisStreams {
    host = "redis.example.com"
    port = 6379
    password = System.getenv("REDIS_PASSWORD")
    ssl = true
    streamKey = "spice:toolcall:events"
    pollInterval = 1.seconds
    enableHistory = true
    historySize = 1000
    enableMetrics = true
}
```

**Same characteristics as EventBus Redis backend, specialized for tool call events.**

---

### Kafka Backend (High-Throughput)

**Spring Boot:**

```yaml
spice:
  tool-call-event-bus:
    enabled: true
    backend: KAFKA
    kafka:
      bootstrap-servers: kafka-1:9092,kafka-2:9092
      topic: spice.toolcall.events
      client-id: spice-toolcall-eventbus
      poll-timeout: 1s
      acks: all
      # Optional: SASL/SSL security
      security-protocol: SASL_SSL
      sasl-mechanism: SCRAM-SHA-512
      sasl-jaas-config: |
        org.apache.kafka.common.security.scram.ScramLoginModule required
        username="spice-user"
        password="${KAFKA_PASSWORD}";
    history:
      enabled: true
      size: 1000
      enable-metrics: true
```

**Non-Spring:**

```kotlin
val eventBus = ToolCallEventBusFactory.kafka {
    bootstrapServers = "kafka-1:9092,kafka-2:9092"
    topic = "spice.toolcall.events"
    clientId = "spice-toolcall-eventbus"
    pollTimeout = 1.seconds
    acks = "all"

    // Optional: SASL/SSL security
    securityProtocol = "SASL_SSL"
    saslMechanism = "SCRAM-SHA-512"
    saslJaasConfig = """
        org.apache.kafka.common.security.scram.ScramLoginModule required
        username="spice-user"
        password="${System.getenv("KAFKA_PASSWORD")}";
    """.trimIndent()

    enableHistory = true
    historySize = 1000
    enableMetrics = true
}
```

**Same characteristics as EventBus Kafka backend, specialized for tool call events.**

---

## Configuration from Map (Programmatic)

Both factories support configuration from a `Map<String, Any>`:

### EventBus from Config Map

```kotlin
val config = mapOf(
    "type" to "kafka",
    "kafka.bootstrapServers" to "localhost:9092",
    "kafka.topic" to "spice.events",
    "kafka.acks" to "all"
)

val eventBus = EventBusFactory.fromConfig(config)
```

### ToolCallEventBus from Config Map

```kotlin
val config = mapOf(
    "type" to "redis",
    "redis.host" to "localhost",
    "redis.port" to 6379,
    "redis.streamKey" to "spice:toolcall:events",
    "history.enabled" to true,
    "history.size" to 1000
)

val eventBus = ToolCallEventBusFactory.fromConfig(config)
```

This is useful for:
- Loading config from external sources (env vars, config files)
- Dynamic backend selection at runtime
- Testing different backends

---

## Mixed Backend Strategy

You can use different backends for EventBus and ToolCallEventBus:

```yaml
spice:
  # Core events → Kafka (high throughput)
  events:
    enabled: true
    backend: KAFKA
    kafka:
      bootstrap-servers: kafka:9092
      topic: spice.events

  # Tool call events → Redis (lower volume, simpler ops)
  tool-call-event-bus:
    enabled: true
    backend: REDIS_STREAMS
    redis-streams:
      stream-key: spice:toolcall:events

  redis:
    enabled: true
    host: redis
    port: 6379
```

**Why mix backends?**
- Core events (graph execution) may have higher throughput → Kafka
- Tool call events (HITL interactions) may be lower volume → Redis
- Operational simplicity: Redis for everything except high-throughput use cases

---

## Environment-Specific Configuration

### Development

```yaml
spice:
  events:
    backend: IN_MEMORY
  tool-call-event-bus:
    enabled: false  # Disable if not needed
```

### Staging

```yaml
spice:
  events:
    backend: REDIS_STREAMS
  tool-call-event-bus:
    enabled: true
    backend: REDIS_STREAMS
  redis:
    host: staging-redis
    port: 6379
```

### Production

```yaml
spice:
  events:
    backend: KAFKA
    kafka:
      bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
      security-protocol: SASL_SSL
      sasl-mechanism: SCRAM-SHA-512
      sasl-jaas-config: ${KAFKA_JAAS_CONFIG}

  tool-call-event-bus:
    enabled: true
    backend: KAFKA
    kafka:
      bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
      security-protocol: SASL_SSL
      sasl-mechanism: SCRAM-SHA-512
      sasl-jaas-config: ${KAFKA_JAAS_CONFIG}
```

---

## Best Practices

### Security

1. **Never hardcode credentials** - Use environment variables:
   ```yaml
   redis:
     password: ${REDIS_PASSWORD}
   ```

2. **Enable SSL/TLS in production**:
   ```yaml
   redis:
     ssl: true
   ```

3. **Use SASL authentication for Kafka**:
   ```yaml
   kafka:
     security-protocol: SASL_SSL
     sasl-mechanism: SCRAM-SHA-512
   ```

### Performance

1. **Tune batch sizes** for Redis:
   ```yaml
   redis-streams:
     batch-size: 500  # Increase for higher throughput
   ```

2. **Adjust poll timeout** based on latency requirements:
   ```yaml
   kafka:
     poll-timeout: 100ms  # Lower for real-time, higher for batch
   ```

3. **Set acks appropriately**:
   - `acks: "1"` - Fast (leader ack only)
   - `acks: "all"` - Durable (all replicas ack)

### Observability

1. **Enable metrics** for monitoring:
   ```yaml
   tool-call-event-bus:
     history:
       enable-metrics: true
   ```

2. **Monitor event lag** (pending messages)

3. **Alert on error rates**

### Operational

1. **Redis stream trimming** - Set up cron job to trim streams:
   ```bash
   redis-cli XTRIM spice:events MAXLEN ~ 100000
   ```

2. **Kafka retention** - Configure retention policy:
   ```properties
   retention.ms=604800000  # 7 days
   ```

3. **Test failover** - Verify behavior when Redis/Kafka is unavailable

---

## Troubleshooting

### EventBus not receiving events

1. Check backend is enabled:
   ```yaml
   spice:
     events:
       enabled: true
   ```

2. Verify Redis/Kafka connectivity:
   ```bash
   # Redis
   redis-cli -h localhost -p 6379 PING

   # Kafka
   kafka-topics.sh --list --bootstrap-server localhost:9092
   ```

3. Check subscription registration:
   ```kotlin
   val subscriptionId = eventBus.subscribe("topic") { message ->
       println("Received: $message")
   }
   ```

### High event lag

1. Increase batch size (Redis):
   ```yaml
   redis-streams:
     batch-size: 1000
   ```

2. Scale consumers (Kafka) - Use consumer groups

3. Check for blocking handlers (use async processing)

### Events lost on restart

1. Verify backend is **not** InMemory
2. Check persistence is enabled (Redis/Kafka)
3. Verify Redis persistence config (RDB/AOF)
4. Verify Kafka replication factor ≥ 2

---

## Next Steps

- [EventBus API Reference](./eventbus-api)
- [ToolCallEventBus Guide](./toolcall-eventbus)
- [Event-Driven Orchestration](../orchestration/event-driven)
- [HITL Integration](../hitl/overview)
