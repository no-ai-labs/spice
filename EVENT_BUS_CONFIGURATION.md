# Event Bus Configuration - Implementation Summary

## Overview

Implemented drop-in configurable event bus infrastructure for both **EventBus** and **ToolCallEventBus**, making the MQ story fully configuration-driven with zero code changes required to switch backends.

## What Was Implemented

### 1. New Implementations

#### `RedisToolCallEventBus` (spice-core)
- Distributed tool call event bus using Redis Streams
- Background polling from Redis Stream to local Flow
- Event history and metrics support
- Consumer group support
- Location: `spice-core/src/main/kotlin/io/github/noailabs/spice/event/RedisToolCallEventBus.kt`

#### `KafkaToolCallEventBus` (spice-core)
- High-throughput tool call event bus using Kafka
- Background Kafka consumer with local Flow emission
- Idempotent producer configuration
- SASL/SSL security support
- Location: `spice-core/src/main/kotlin/io/github/noailabs/spice/event/KafkaToolCallEventBus.kt`

#### `EventBusFactory` (spice-core)
- Factory for creating EventBus instances in non-Spring applications
- Fluent configuration API
- Support for InMemory, Redis, Kafka backends
- Configuration from Map for dynamic backend selection
- Location: `spice-core/src/main/kotlin/io/github/noailabs/spice/events/EventBusFactory.kt`

#### `ToolCallEventBusFactory` (spice-core)
- Factory for creating ToolCallEventBus instances in non-Spring applications
- Same fluent API pattern as EventBusFactory
- History configuration support
- Location: `spice-core/src/main/kotlin/io/github/noailabs/spice/event/ToolCallEventBusFactory.kt`

### 2. Configuration Support

#### `SpiceFrameworkProperties` Updates (spice-springboot)
Added `ToolCallEventBusProperties` with:
- `backend`: IN_MEMORY | REDIS_STREAMS | KAFKA
- `redisStreams`: Configuration for Redis backend
- `kafka`: Configuration for Kafka backend
- `history`: Event history configuration

Location: `spice-springboot/src/main/kotlin/io/github/noailabs/spice/springboot/SpiceFrameworkProperties.kt`

#### `SpiceAutoConfiguration` Updates (spice-springboot)
Added `toolCallEventBus()` bean that:
- Reads configuration from `application.yml`
- Creates appropriate backend based on `spice.tool-call-event-bus.backend`
- Validates dependencies (Redis enabled for REDIS_STREAMS, etc.)
- Auto-wired into any service that needs it

Location: `spice-springboot/src/main/kotlin/io/github/noailabs/spice/springboot/SpiceAutoConfiguration.kt`

### 3. Documentation

#### Event Bus Configuration Guide
Comprehensive documentation covering:
- Quick start for Spring Boot and non-Spring apps
- Configuration examples for all backends
- Mixed backend strategies
- Environment-specific configurations
- Security best practices
- Performance tuning
- Troubleshooting guide

Location: `docs/docs/event-bus/configuration.md`

## Usage Examples

### Spring Boot - Drop-in Configuration

**Development (InMemory):**
```yaml
spice:
  events:
    backend: IN_MEMORY
  tool-call-event-bus:
    enabled: false
```

**Staging (Redis):**
```yaml
spice:
  events:
    backend: REDIS_STREAMS
  tool-call-event-bus:
    enabled: true
    backend: REDIS_STREAMS
  redis:
    enabled: true
    host: staging-redis
    port: 6379
```

**Production (Kafka):**
```yaml
spice:
  events:
    backend: KAFKA
    kafka:
      bootstrap-servers: kafka-1:9092,kafka-2:9092
  tool-call-event-bus:
    enabled: true
    backend: KAFKA
    kafka:
      bootstrap-servers: kafka-1:9092,kafka-2:9092
```

### Non-Spring - Factory Pattern

**EventBus:**
```kotlin
// In-Memory
val eventBus = EventBusFactory.inMemory()

// Redis
val eventBus = EventBusFactory.redisStreams {
    host = "localhost"
    port = 6379
    streamKey = "spice:events"
}

// Kafka
val eventBus = EventBusFactory.kafka {
    bootstrapServers = "localhost:9092"
    topic = "spice.events"
}
```

**ToolCallEventBus:**
```kotlin
// In-Memory
val eventBus = ToolCallEventBusFactory.inMemory()

// Redis
val eventBus = ToolCallEventBusFactory.redisStreams {
    host = "localhost"
    port = 6379
    streamKey = "spice:toolcall:events"
}

// Kafka
val eventBus = ToolCallEventBusFactory.kafka {
    bootstrapServers = "localhost:9092"
    topic = "spice.toolcall.events"
}
```

## Key Features

### 1. Zero Code Changes
Switch backends by changing configuration only - no code changes required:
- Spring Boot: Edit `application.yml`
- Non-Spring: Change factory method call

### 2. Mixed Backend Strategy
Use different backends for EventBus and ToolCallEventBus:
```yaml
spice:
  events:
    backend: KAFKA  # High-throughput core events
  tool-call-event-bus:
    backend: REDIS_STREAMS  # Lower-volume tool calls
```

### 3. Environment-Specific
Different backends per environment:
- Dev: InMemory (fast, simple)
- Staging: Redis (distributed, easy ops)
- Production: Kafka (high-throughput, durable)

### 4. Security Support
- Redis: SSL/TLS, password authentication
- Kafka: SASL/SSL, SCRAM authentication

### 5. History & Metrics
Configurable event history and metrics for observability:
```yaml
tool-call-event-bus:
  history:
    enabled: true
    size: 1000
    enable-metrics: true
```

## Architecture

### Before (0.x)
```
Code → new InMemoryToolCallEventBus()
```
- Hardcoded InMemory only
- No distributed support
- Code changes to switch backends

### After (1.0.0)
```
Spring Boot:
  application.yml → SpiceAutoConfiguration → ToolCallEventBus bean

Non-Spring:
  Config map → ToolCallEventBusFactory → ToolCallEventBus instance
```
- Drop-in configurable
- Three backends (InMemory, Redis, Kafka)
- Zero code changes to switch

## Testing

All modules compiled and published to local Maven successfully:
```
./gradlew clean build publishToMavenLocal -x test
```

Published:
- spice-core (with new implementations)
- spice-springboot (with auto-configuration)
- spice-springboot-ai
- spice-springboot-statemachine
- spice-agents

## Files Added/Modified

### Added (6 files):
1. `spice-core/src/main/kotlin/io/github/noailabs/spice/event/RedisToolCallEventBus.kt`
2. `spice-core/src/main/kotlin/io/github/noailabs/spice/event/KafkaToolCallEventBus.kt`
3. `spice-core/src/main/kotlin/io/github/noailabs/spice/events/EventBusFactory.kt`
4. `spice-core/src/main/kotlin/io/github/noailabs/spice/event/ToolCallEventBusFactory.kt`
5. `docs/docs/event-bus/configuration.md`
6. `EVENT_BUS_CONFIGURATION.md` (this file)

### Modified (2 files):
1. `spice-springboot/src/main/kotlin/io/github/noailabs/spice/springboot/SpiceFrameworkProperties.kt`
   - Added `ToolCallEventBusProperties`
2. `spice-springboot/src/main/kotlin/io/github/noailabs/spice/springboot/SpiceAutoConfiguration.kt`
   - Added `toolCallEventBus()` bean

## Benefits

1. **Operational Flexibility**: Switch backends without code deployment
2. **Environment Parity**: Use InMemory in dev, Redis/Kafka in prod
3. **Cost Optimization**: Start with Redis, scale to Kafka only when needed
4. **Developer Experience**: Same API, different backend
5. **Testing**: Easy to test with InMemory, deploy with distributed backend

## Next Steps

1. Add integration tests for Redis and Kafka backends
2. Add Micrometer metrics integration
3. Consider adding NATS backend for lightweight deployments
4. Document operational runbooks for Redis/Kafka troubleshooting
