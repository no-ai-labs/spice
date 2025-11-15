# Spice 1.0.0 Roadmap

Spice 1.0.0 is the first “all-in” release built around the unified **SpiceMessage + GraphRunner** execution model. All of the legacy Flow/Swarm/DAG hybrids have been replaced with a single, testable runtime that ships with Redis/Kafka eventing, HITL automation, and Spring Boot wiring out of the box.

This page captures the high‑level direction for 1.0.x and how the five primary modules fit together.

## What’s New in 1.0.0

- **SpiceMessage everywhere** – agents, tools, graph nodes, checkpoints, and event buses exchange a single typed payload with explicit `ExecutionState`.
- **DefaultGraphRunner** – validates graphs, manages retries/idempotency, records vector embeddings, and emits lifecycle events.
- **Distributed EventBus** – Redis Streams and Kafka implementations sit beside the in-memory bus so production deployments can choose their transport.
- **Spring Boot Starters** – modern auto-config configures GraphRunner, Redis-backed caches, EventBus backends, and HITL queue/arbiter helpers with simple `application.yml` switches.
- **State Machine Extension** – HITL pause/resume, tool retry/backoff, checkpoint persistence, metrics, and actuator visualizations ship as a dedicated starter.
- **Spring Boot AI Integration** – bridges Spring AI ChatModels to Spice agents with factories, DSL builders, and registries.

👉 Dive into the full breakdown in [Spice Framework v1.0.0 Guide](./release-1-0-0).

## Module Focus

| Module | Core Responsibility | Notes |
| --- | --- | --- |
| `spice-core` | Graph runtime, state machine, caching, idempotency, event bus, arbiter/queue primitives | Pure Kotlin/JVM – no Spring dependency |
| `spice-agents` | Standalone OpenAI/Anthropic agents + mock tooling | Ships its own Ktor client stack |
| `spice-springboot` | Auto-config for GraphRunner, Redis pools, EventBus backends, HITL queue/arbiter wiring | Enables property-driven deployment |
| `spice-springboot-statemachine` | HITL pause/resume automation, retries, event emission, metrics, visualization | Designed to plug directly into Kai-core |
| `spice-springboot-ai` | Spring AI → Spice adapter (factories, DSL, registry) | Lets existing Spring AI apps opt into Spice orchestration |

Need a refresher on each module? See [Modules & Responsibilities](../core-concepts/modules).

## 1.0.x Priorities

1. **Operational Hardening**
   - Richer metrics on EventBus backends (lag, consumer health)
   - Redis queue/arbiter adapters that reuse the shared Jedis pool
2. **Observability & Tooling**
   - OpenTelemetry exporters for GraphRunner and state machine listeners
   - Developer tooling for visualizing checkpoints and node transitions
3. **Integration Kits**
   - Spring Boot auto-config for Redis Streams/Kafka EventBus selection
   - Optional Spring Messaging bridge for existing queues
4. **Docs & Examples**
   - Expand the [Getting Started](../getting-started/quick-start.md) and [Examples](../examples/context-production.md) sections with multi-module samples
   - Migration notes focused solely on 1.0.0 (legacy migration guides have been retired)

## How to Adopt 1.0

1. Start with the [Quick Start](../getting-started/quick-start) to wire a graph + state machine.
2. Configure the new properties outlined in the [1.0.0 Guide](./release-1-0-0) to enable Redis/Kafka/HITL when needed.
3. For Spring AI users, jump to [`spice-springboot-ai`](../spring-boot/overview) to re-use your existing ChatModels.

1.0.x is where Spice becomes opinionated and production ready—no more juggling parallel runtimes or bespoke queue scripts. Let us know what’s missing and we’ll fold it into the roadmap! 🚀
