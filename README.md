# 🌶️ Spice Framework 1.0

Modern Kotlin runtime for building AI-centric workflows with **SpiceMessage**, graph-based orchestration, Spring Boot integration, and production-grade observability.

> **1.0.0 status**: Graph runner, ToolCall event bus, Redis/Kafka backends, and Spring state machine adapter are production-ready. Pending recovery, DLQ, and metadata propagation are built in.

---

## 📚 Documentation

| Topic | Description |
| --- | --- |
| [README_1.0.md](docs/README_1.0.md) | Documentation hub & navigation |
| [Quick Start](docs/QUICK_START_1.0.md) | Build and run your first graph in minutes |
| [Installation](docs/INSTALLATION_1.0.md) | Dependency matrix for core/agents/spring modules |
| [Architecture](docs/ARCHITECTURE_1.0.md) | SpiceMessage flow, graphs, state machine adapter |
| [Migration 0.x → 1.0](docs/MIGRATION_0.x_TO_1.0.md) | Breaking changes, side-by-side code samples |
| [Concept Comparison](docs/CONCEPT_COMPARISON_1.0.md) | Quick reference tables for APIs & patterns |

---

## ✨ 1.0 Highlights

- **SpiceMessage Everywhere** – unified message type replaces legacy `Comm`/`Message`.
- **Graph Runner + StateMachine Adapter** – deterministic execution with HITL, checkpoints, and retry hooks.
- **ToolCall Event Bus** – Kafka/Redis implementations with DLQ, pending recovery, and telemetry callbacks.
- **Spring Boot Starters** – auto-configure graph runner, state machine, Redis/Kafka resources, metrics, and endpoints.
- **Production Docs** – Quick start, installation, architecture, and migration guides rewritten for 1.0.

---

## 🧱 Modules

| Module | Description |
| --- | --- |
| `spice-core` | Graph runner, SpiceMessage, Tool/Agent APIs, ToolCall event bus |
| `spice-agents` | Opinionated agent patterns and helpers |
| `spice-springboot` | Spring Boot auto-configuration for graphs, Redis/Kafka, metrics |
| `spice-springboot-statemachine` | Graph ↔ Spring StateMachine bridge, HITL tooling |
| `spice-springboot-ai` | Spring AI provider integration (OpenAI, Anthropic, etc.) |
| `spice-springboot-statemachine/transformer` | Message transformer hooks for auth/tracing/subgraphs |

Each module has a `build.gradle.kts` with exact coordinates. See [Installation](docs/INSTALLATION_1.0.md) for Gradle/Maven snippets.

---

## 🚀 Quick Start

```kotlin
implementation("io.github.noailabs:spice-core:1.0.0")
implementation("io.github.noailabs:spice-springboot:1.0.0")
```

1. Register your graph and tools using the Kotlin DSL (examples in [Quick Start](docs/QUICK_START_1.0.md)).
2. Wire `GraphToStateMachineAdapter` via Spring Boot starter for HITL/retry support.
3. Enable Redis/Kafka ToolCall event bus if you need distributed execution or DLQ.

---

## 🛡️ Production Readiness

- Redis Streams event bus: pending recovery (XPENDING/XCLAIM) and DLQ routing documented in [REDIS_EVENTBUS_PRODUCTION_READINESS.md](docs/REDIS_EVENTBUS_PRODUCTION_READINESS.md).
- ToolCall buses: DLQ support for Kafka/Redis, metrics hooks, and message transformers to enrich context.
- Documentation includes troubleshooting checklists, verification scripts, and release notes from v0.4 → v1.0.

---

## 📜 License

Spice Framework is MIT licensed. See [LICENSE](LICENSE) for details.
