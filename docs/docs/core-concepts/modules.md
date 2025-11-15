---
id: modules
title: Module Overview
sidebar_label: Modules & Responsibilities
sidebar_position: 6
description: High-level breakdown of the primary Spice Framework modules and their responsibilities in 1.0.0.
---

# Spice Modules (1.0.0)

Spice Framework 1.0.0 is organized into five primary modules. Each targets a different runtime surface so you can mix and match what you ship:

| Module | Purpose | When to depend on it |
| --- | --- | --- |
| `spice-core` | Core execution engine (graphs, state machine, idempotency, cache, event bus, arbiter helpers) | Always – every other module re-uses it |
| `spice-agents` | Standalone HTTP agents (OpenAI, Anthropic, Mock) that work without Spring | Lightweight CLI/back-end apps that only need agents |
| `spice-springboot` | Spring Boot starter that wires `DefaultGraphRunner`, cache/idempotency stores, EventBus backends, and HITL queues | Spring services that host Spice graphs or HITL flows |
| `spice-springboot-statemachine` | State machine starter: HITL pause/resume, auto checkpoint persistence, retry/backoff, actuator endpoints | Spring apps that want opinionated HITL automation and observability |
| `spice-springboot-ai` | Spring AI integration layer with provider factories/DSLs/registries | Teams already on Spring AI that want unified agent creation |

## spice-core

- Implements `DefaultGraphRunner`, checkpoint helpers, caching, idempotency stores, the unified `SpiceMessage`, and `GraphRunner` resume semantics.
- Provides distributed EventBus backends (`RedisStreamsEventBus`, `KafkaEventBus`) plus an in-memory option.
- Supplies the Arbiter + message queue primitives used by the Spring starter.

### Enhancement ideas

- Redis Streams backend still retains every entry forever; expose stream trimming and pending-claim helpers so operators can cap memory usage.
- EventBus implementations emit high-level stats but not per-topic lag/consumer metrics—wire Micrometer counters to surface them in Spring Boot.
- Consider pluggable serialization hooks so large `SpiceMessage` payloads can live in CBOR/Smile instead of JSON when hitting Redis/Kafka.

## spice-agents

- Adds helper DSL (`buildAgent`), provider-specific factories (`gptAgent`, `claudeAgent`), and mock agents for tests.
- Ships its own Ktor-based HTTP stack to avoid tying agent usage to Spring Boot.

### Enhancement ideas

- Add eager validation hooks (`require(apiKey…​)`) so runtime errors surface at bootstrap.
- Share/reuse HTTP clients across agents or expose `close()` on the agent so CLI apps can free sockets quickly.
- Expand the test suite with WireMock/OpenAI-compatible goldens to guard request/response formats.

## spice-springboot

- Aligns Spring Boot autoconfiguration with `spice-core` 1.0.0: registers `DefaultGraphRunner`, cache policy, optional Redis/Jedis, event bus backends, and HITL queue/arbiter beans.
- Exposes new configuration switches for Redis Streams/Kafka EventBus and queue/arbiter toggles through `SpiceFrameworkProperties`.

### Enhancement ideas

- Provide a sample `GraphProvider` implementation (or sensible default) so HITL apps can opt-in without extra wiring.
- Document best practices for running multiple arbiters (topics, consumer groups, deployment topology).
- Consider splitting EventBus auto-config into dedicated starters so teams only pull Kafka or Redis dependencies they actually use.

## spice-springboot-statemachine

- Provides `GraphToStateMachineAdapter`, Redis checkpoint persister, HITL listeners, tool retry/backoff actions, and actuator endpoints for visualization/metrics/health.
- Integrates with the Spring starter via shared beans (GraphRunner, Redis pool, ApplicationEventPublisher).

### Enhancement ideas

- `ReactiveRedisStatePersister` still blocks via `.block()`; document the expectation clearly or add a non-blocking alternative.
- `GraphToStateMachineAdapter` stores entire `Graph` instances inside extended state; persisting graph IDs + versions instead would keep Redis payloads small.
- Add built-in OpenTelemetry spans or Micrometer timers so every state transition can be traced without custom listeners.

## spice-springboot-ai

- Bridges Spring AI’s provider ecosystem with Spice agents via factories (`SpringAIAgentFactory`), DSL builders, registries, and tool adapters.
- Ships auto-configuration plus property binding for OpenAI, Anthropic, Ollama, Azure OpenAI, Vertex, and Bedrock.

### Enhancement ideas

- Generate `spring-configuration-metadata.json` so IDEs can auto-complete `spice.spring-ai.*` properties.
- Cache `ChatModel`/`ChatClient` instances per provider/model combo to avoid reinitializing expensive HTTP clients on every call.
- Add contract tests to ensure tool adapters (function callbacks) faithfully map between Spring AI and Spice Tool semantics.

---

Use this overview as a quick reference when deciding which module to depend on. For detailed configuration and API docs, continue with the rest of the Core Concepts and Spring Boot sections.
