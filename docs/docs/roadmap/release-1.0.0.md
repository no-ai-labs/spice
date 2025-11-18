---
id: release-1-0-0
title: Spice Framework v1.0.0 Guide
sidebar_label: v1.0.0 Release Guide
sidebar_position: 1
description: Architecture notes, migration guidance, and feature overview for the 1.0.0 runtime.
---

# Spice Framework v1.0.0

Spice 1.0.0 is the first release built entirely around the new `SpiceMessage` execution model, a production-grade graph runner, and a refreshed Spring Boot experience (including a dedicated state machine starter). Use this guide to understand what changed, how to configure the new starters, and where future work is headed.

## Release Highlights

- **Unified execution contract** – `SpiceMessage` + `ExecutionState` now flow through every graph, checkpoint, and HITL boundary. Legacy Comm/NodeResult types are removed.
- **DefaultGraphRunner** – built-in validation, state transitions, vector cache hydration, and idempotency guards before every node step.
- **First-class Spring Boot autoconfiguration** – `SpiceAutoConfiguration` exposes cache policy, vector cache, idempotency, and event bus wiring behind `SpiceFrameworkProperties`.
- **State machine extension** – the new `spice-springboot-statemachine` module automates HITL pause/resume, tool retry/backoff, event emission, metrics, and visualization endpoints.
- **Reactive Redis persistence** – optional Redis-backed idempotency, vector cache, and state machine checkpoints share the same Jedis pool so you can connect once and reuse.

## Runtime Architecture Updates

### Unified Message + State Model

- The core module consolidates runtime data into `SpiceMessage` (`spice-core/src/main/kotlin/io/github/noailabs/spice/SpiceMessage.kt`) with explicit `ExecutionState` transitions and history tracking. All nodes, middleware, and checkpoint helpers now mutate/inspect the same shape.
- Tool APIs, registries, and DSL helpers take `Map<String, Any>` metadata instead of stringifying values. This keeps retry/error metadata structured when it reaches checkpoint stores or event sinks.

### DefaultGraphRunner Enhancements

`DefaultGraphRunner` (`spice-core/.../graph/runner/GraphRunner.kt`) is the only implementation most apps need:

- Validates graphs via `SchemaValidationPipeline` and enforces `ExecutionStateMachine` invariants up front.
- Emits graph lifecycle events when `Graph.eventBus` is present. The baseline is the in-memory bus, with Redis Streams/Kafka backends planned once those core implementations land (see **Known Gaps** below).
- Integrates idempotency checks by delegating to an `IdempotencyStore` before each node executes. TTLs and cache windows come from `CachePolicy`.
- Records vector embeddings for intents/steps when `VectorCache` is supplied (in-memory or Redis).
- Understands `WAITING` → `RUNNING` resume loops through `resume(...)` so HITL responses can be replayed safely.

### Checkpoint + HITL Flow

`GraphRunnerCheckpointExtensions` wraps any runner with persistence-aware helpers:

1. `executeWithCheckpoint` persists `Checkpoint` entries whenever a message transitions into `ExecutionState.WAITING` or when `saveOnError=true`.
2. `resumeFromCheckpoint` loads the serialized `SpiceMessage`, merges optional `HumanResponse`, and moves it back to `RUNNING` before re-entering the graph.
3. TTLs and cleanup behavior come from `CheckpointConfig`, so you can tune how long HITL sessions remain resumable.

### Caching & Idempotency

- `VectorCache` implementations (in-memory and Redis) now store typed metadata and calculate cosine similarity inside the module. The Redis implementation shares the Jedis pool with other infrastructure.
- `IdempotencyStore` offers the same backends and collects hit/miss statistics for observability.
- `CachePolicy` centralizes TTL decisions for tool calls, steps, and intents and can be reconfigured via Spring Boot properties.

## Spring Boot Starter (spice-springboot)

`SpiceAutoConfiguration` replaces the old `@EnableSpice` annotation and legacy property classes. It exposes modern beans and can be pulled in with:

```kotlin
dependencies {
    implementation("io.github.noailabs:spice-springboot")
}
```

### Auto-configured Beans

- `SchemaValidationPipeline`, `ExecutionStateMachine`, and `CachePolicy`
- `DefaultGraphRunner` with optional `VectorCache`
- Conditional `IdempotencyStore` (in-memory or Redis)
- `EventBus` with three backends: in-memory (stable), Redis Streams (beta), and Kafka (beta)
- Shared `JedisPool` used by idempotency/vector caches and Redis-backed EventBus

### Property Reference

| Property | Purpose | Notes |
| --- | --- | --- |
| `spice.enabled` | Global toggle for the starter | defaults to `true` |
| `spice.graph-runner.enable-idempotency` | Controls idempotency checks | requires an `IdempotencyStore` bean |
| `spice.graph-runner.enable-events` | Enables EventBus dispatch | Works with all three EventBus backends |
| `spice.cache.*` | TTLs for tool calls, steps, intents | mapped into `CachePolicy` |
| `spice.idempotency.*` | Backend + namespace configuration | `IN_MEMORY` or `REDIS` |
| `spice.vector-cache.*` | Vector cache backend + namespace | shares Redis pool when `REDIS` |
| `spice.redis.*` | Jedis connection info | reused by all Redis-backed components |
| `spice.events.enabled` | Switches the EventBus bean on/off | stays `true` for most apps |
| `spice.events.backend` | Selects the EventBus backend (`in-memory`, `redis-streams`, `kafka`) | Redis Streams & Kafka are now first-class options |
| `spice.events.redis-streams.*` | Stream key, consumer prefix, poll timeout, batch size | defaults: `spice:events`, `spice-events`, `1s`, `100` |
| `spice.events.kafka.*` | Topic, bootstrap servers, poll timeout, client id, ACK settings | defaults: `spice.events`, `localhost:9092`, `1s`, `spice-eventbus`, `all` |
| `spice.hitl.queue.enabled` | Exposes a `MessageQueue` bean (`InMemoryMessageQueue` or `RedisMessageQueue`) | uses shared Jedis pool when `backend=redis` |
| `spice.hitl.queue.backend` | Queue backend selection (`in-memory`, `redis`) | set to `redis` once you enable `spice.redis.*` |
| `spice.hitl.arbiter.enabled` | Publishes an `Arbiter` bean wired to the configured queue | lets apps bootstrap the full HITL worker stack |

Example `application.yml` snippet:

```yaml
spice:
  graph-runner:
    enable-idempotency: true
    enable-events: true
  redis:
    enabled: true
    host: redis.prod.local
    port: 6379
    database: 3
  idempotency:
    enabled: true
    backend: redis
    namespace: spice:idempotency:prod
  vector-cache:
    enabled: true
    backend: redis
    namespace: spice:vector:prod
```

### EventBus Backends

`spice-core` now ships `RedisStreamsEventBus` and `KafkaEventBus`, both exposed through the starter:

```yaml
spice:
  events:
    enabled: true
    backend: redis-streams
    redis-streams:
      stream-key: spice:events
      consumer-prefix: spice-events
      poll-timeout: 1s
      batch-size: 200
```

```yaml
spice:
  events:
    enabled: true
    backend: kafka
    kafka:
      bootstrap-servers: kafka-1:9092,kafka-2:9092
      topic: spice.events
      client-id: spice-eventbus
      poll-timeout: 1s
      acks: all
```

The Redis Streams backend stores every logical topic inside a single stream key and filters client-side (maintaining `*`/`**` topic patterns). The Kafka backend follows the same envelope format (Spice topic in the record key, serialized `SpiceMessage` as payload) so existing subscribers keep working regardless of backend choice.

:::note Redis Streams fan-out
`XREADGROUP` delivers each entry to only one consumer per group. If you need broadcast-style delivery, either use `subscribe(...)` (no group) or ensure every consumer registers with its own `groupId`. Otherwise messages will be load-balanced, not fanned out.
:::

:::note Kafka publish semantics
The Kafka backend waits for the broker ACK before returning. If the send fails, the coroutine throws and the caller receives a `SpiceResult.Failure`, making at-least-once delivery guarantees explicit.
:::

### HITL Queue + Arbiter Wiring

Two new property blocks make it trivial to stand up the shared queue/arbiter stack:

```yaml
spice:
  redis:
    enabled: true
    host: redis.internal
  hitl:
    queue:
      enabled: true
      backend: redis
      namespace: spice:mq:prod
      topic: spice.hitl.queue
    arbiter:
      enabled: true
      topic: spice.hitl.queue
```

- `spice.hitl.queue.enabled=true` yields a `MessageQueue` bean (in-memory by default, Redis when requested) using the Jedis pool you already configured.
- `spice.hitl.arbiter.enabled=true` creates an `Arbiter` bean wired to the default `GraphRunner`, `ExecutionStateMachine`, and optional `DeadLetterHandler`. You decide where/how to invoke `arbiter.start(topic, graphProvider)` (ApplicationRunner, coroutine, etc.).
- If you bring your own `MessageQueue` implementation, keep `spice.hitl.queue.enabled=false` and register a bean manually—`spice.hitl.arbiter.enabled` will still reuse it.

## Spring Boot State Machine Extension (spice-springboot-statemachine)

Add the module when you need opinionated HITL automation or retry logic:

```kotlin
dependencies {
    implementation("io.github.noailabs:spice-springboot-statemachine")
}
```

### What It Provides

- `GraphToStateMachineAdapter` that wraps any `GraphRunner` and drives Spring StateMachine transitions.
- `HitlStateMachineListener` + `CheckpointSaveAction` to persist checkpoints automatically when a graph pauses in `ExecutionState.WAITING`, and to resume when your application publishes `HumanResponseEvent`.
- `ToolRetryAction` with exponential/fixed/linear backoff strategies configured via `spice.statemachine.retry`.
- `NodeExecutionLogger` that publishes `NodeExecutionEvent` and `WorkflowCompletedEvent` through Spring’s `ApplicationEventPublisher` so you can forward them to Kafka, Redis Streams, or Elasticsearch sinks.
- `ReactiveRedisStatePersister` that serializes extended state as JSON when `spice.statemachine.persistence.type=redis`.
- Actuator extras: `StateMachineHealthIndicator`, Micrometer-backed `StateMachineMetrics`, and `/actuator/statemachine/{visualize|mermaid}` endpoints generated by `StateMachineVisualizationEndpoint`.

### Configuration Example

```yaml
spice:
  statemachine:
    enabled: true
    persistence:
      type: redis
      redis:
        host: redis.prod.local
        port: 6379
        key-prefix: spice:sm:
        ttl-seconds: 604800
    retry:
      enabled: true
      max-attempts: 3
      backoff-strategy: exponential
      initial-backoff-ms: 1000
      max-backoff-ms: 10000
    events:
      enabled: true
      publish-to-kafka: false   # wire custom ApplicationEventListener for Kafka/Redis Streams
    visualization:
      enabled: true
      format: mermaid
```

When a node transitions into `WAITING`, checkpoints are persisted, HITL notifications can be emitted, and `/actuator/statemachine/mermaid` immediately reflects the current topology. When operators respond, publishing a `HumanResponseEvent` drives `SpiceEvent.RESUME` without any manual wiring.

### Extending Event Delivery

Because events flow through Spring’s `ApplicationEventPublisher`, you can layer your own sinks:

```kotlin
@Component
class RedisStreamSink(private val redisTemplate: ReactiveRedisTemplate<String, String>) {
    @EventListener
    fun onNodeEvent(event: NodeExecutionEvent) {
        redisTemplate.opsForStream<String, String>()
            .add("spice.node.execution", mapOf("payload" to json.encodeToString(event)))
            .subscribe()
    }
}
```

When `spice-core` lands Redis Streams or Kafka `EventBus` implementations, matching property switches in this starter will stand up dedicated beans using the already-configured Jedis pool.

## Migration Checklist

- Remove legacy `@EnableSpice` annotations; Spring Boot autoconfiguration now registers everything when `spice.enabled=true`.
- Replace Comm/NodeResult payloads with `SpiceMessage`. Tool inputs/outputs now use native Kotlin types in `Map<String, Any>`.
- Adopt `GraphRunner.execute(...)`/`resume(...)` everywhere; node factories and middleware expect `SpiceMessage`.
- Use the new Spring properties for idempotency/vector caches instead of manually instantiating Redis/Jedis clients.
- When HITL support is required, depend on `spice-springboot-statemachine` and inject `GraphToStateMachineAdapter` rather than bolting custom logic to `GraphRunner` responses.

## Known Gaps & Follow-ups

1. **EventBus operations tooling** – Distributed backends are now available; next steps are richer metrics/health endpoints (lag, consumer status) layered on top of the new EventBus implementations.
2. **Redis Streams / Kafka queue options** – HITL queue wiring currently supports in-memory + Redis List semantics. When we settle on Redis Streams or Kafka queue adapters, wire them under the same `spice.hitl.queue.backend` enum.
3. **Documentation versioning** – This page lives in the “next” docs set. Run `pnpm docusaurus docs:version 1.0.0` after locking the release so the 1.0.0 selector appears beside the existing 0.x versions.

Until those pieces land, the new starters are production-ready with in-memory eventing and manual ApplicationEvent sinks, and the state machine module covers the full HITL automation story with Redis-backed persistence.
