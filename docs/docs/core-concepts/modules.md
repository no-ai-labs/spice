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

### Review highlights

- `RedisStreamsEventBus.close()` / `KafkaEventBus.close()` currently cancel per-subscription coroutines but never close the supervisor scope; wrap the scope in a `Job` and call `scope.cancel()` to free Jedis connections (`spice-core/.../RedisStreamsEventBus.kt:134`, `KafkaEventBus.kt:102`).
- Redis Streams uses `mapOf("topic" to topic, ...)` but does not enforce max stream length or handle pending messages; consider surfacing configuration knobs (trim strategy, dead-letter handling) so consumers can prevent unbounded growth.
- Kafka backend eagerly blocks on `.get()` for every publish which makes producers synchronous; expose an async path or configurable `Future` handling for higher throughput.

## spice-agents

- Adds helper DSL (`buildAgent`), provider-specific factories (`gptAgent`, `claudeAgent`), and mock agents for tests.
- Ships its own Ktor-based HTTP stack to avoid tying agent usage to Spring Boot.

### Review highlights

- `GPTAgent`/`ClaudeAgent` constructors never validate API keys or base URLs; misconfiguration fails at first request with opaque HTTP errors. Add eager validation (e.g., `require(apiKey.isNotBlank())`) so bootstrap issues fail fast.
- HTTP clients are created per-agent without a shared `HttpClient` lifecycle; add a singleton `HttpClientProvider` or close clients when agents are disposed to avoid leaking resources in apps that spin up many agents dynamically.
- Tests only cover mock agents; add integration tests (optionally with WireMock) to ensure request payloads stay compatible with provider APIs.

## spice-springboot

- Aligns Spring Boot autoconfiguration with `spice-core` 1.0.0: registers `DefaultGraphRunner`, cache policy, optional Redis/Jedis, event bus backends, and HITL queue/arbiter beans.
- Exposes new configuration switches for Redis Streams/Kafka EventBus and queue/arbiter toggles through `SpiceFrameworkProperties`.

### Review highlights

- `SpiceAutoConfiguration.arbiterRunner` launches a coroutine with `CoroutineScope(Dispatchers.Default)` but never keeps the resulting `Job` nor cancels it on shutdown (`spice-springboot/.../SpiceAutoConfiguration.kt:247`). Wrap it in a bean that implements `SmartLifecycle`/`DisposableBean` or use Spring’s task executor so restarts don’t spawn duplicate arbiters.
- Arbiter auto-start requires a `GraphProvider` bean, yet no default bean exists. Mention that requirement explicitly in the README/docs or guard startup with a helpful exception when missing.
- Spring Boot starter still depends on `kotlinx-coroutines-core` explicitly even though `spice-core` already exports it; remove the duplicate dependency or mark it as `implementation` to avoid version drift.

## spice-springboot-statemachine

- Provides `GraphToStateMachineAdapter`, Redis checkpoint persister, HITL listeners, tool retry/backoff actions, and actuator endpoints for visualization/metrics/health.
- Integrates with the Spring starter via shared beans (GraphRunner, Redis pool, ApplicationEventPublisher).

### Review highlights

- `StateMachineAutoConfiguration.nodeExecutionLogger` registers a listener unconditionally; large deployments that don’t need events can’t turn it off. Add a `spice.statemachine.events.enabled` guard around listener registration (`spice-springboot-statemachine/.../StateMachineAutoConfiguration.kt:109`).
- `ReactiveRedisStatePersister` blocks on reactive calls via `.block()`; document that it should only be used when the underlying connection factory is configured for blocking operations, or refactor to Reactor-friendly `StateMachinePersist`.
- `GraphToStateMachineAdapter` stores entire `Graph` objects in extended state for Redis persistence, but the `ReactiveRedisStatePersister` serializes everything as strings. Large graphs may exceed Redis value limits or round-trip incorrectly; consider storing lightweight graph IDs plus versions instead.

## spice-springboot-ai

- Bridges Spring AI’s provider ecosystem with Spice agents via factories (`SpringAIAgentFactory`), DSL builders, registries, and tool adapters.
- Ships auto-configuration plus property binding for OpenAI, Anthropic, Ollama, Azure OpenAI, Vertex, and Bedrock.

### Review highlights

- Provider properties currently live under `spice.spring-ai.*` but there’s no configuration metadata (no `spring-configuration-metadata.json`), so IDE auto-completion won’t work. Add the configuration processor (already declared) and ensure `@ConfigurationProperties` classes cover all provider options.
- The factories create Spring AI `ChatClient` instances on demand but do not reuse them; repeated calls can reinitialize the entire client stack. Cache per-provider/per-model instances or document that factories should be scoped.
- Tests for this module are missing; add contract tests that assert the factories build agents with the expected provider metadata and tool adapters behave symmetrically (Spice Tool → Spring AI Tool → Spice Tool).

---

Use this overview as a quick reference when deciding which module to depend on. For detailed configuration and API docs, continue with the rest of the Core Concepts and Spring Boot sections.
