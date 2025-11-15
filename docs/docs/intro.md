---
sidebar_position: 1
---

# Welcome to Spice Framework 🌶️

**Modern Multi-LLM Orchestration Framework for Kotlin (v1.0.0)**

Spice 1.0.0 unifies agents, tools, graphs, and HITL flows behind a single `SpiceMessage` execution model. Build production-grade AI systems with built-in retries, checkpoints, Redis/Kafka eventing, and Spring Boot starters – all while staying in idiomatic Kotlin.

## Why Spice 1.0.0?

- **🪄 Unified runtime** – `SpiceMessage` + `ExecutionState` flow through agents, nodes, and checkpoints (no separate Comm/NodeResult types).
- **🧠 Graph-first orchestration** – `DefaultGraphRunner` validates, executes, retries, and emits events for every graph.
- **🌐 Pluggable EventBus** – choose in-memory, Redis Streams, or Kafka transports without touching graph code.
- **⏸️ HITL automation** – Spring state machine starter handles pause/resume, checkpoint persistence, and metrics automatically.
- **🔌 Spring AI bridge** – reuse your Spring AI ChatModels through the `spice-springboot-ai` factory/DSL/registry stack.
- **⚙️ Dev-first DX** – consistent DSLs, typed registries, and coroutine-friendly APIs keep everything testable.

## Core Building Blocks

- **`SpiceMessage`** – carries content, data, metadata, and `ExecutionState` for every hop.
- **Agents & Tools** – build standalone Ktor agents (`spice-agents`) or wrap Spring AI chat models.
- **Graph DSL** – describe workflows with nodes, middleware, and merge strategies.
- **Event Bus** – publish node/graph lifecycle events to in-memory, Redis Streams, or Kafka.
- **HITL Toolkit** – auto checkpointing, Redis state persistence, actuator endpoints, and Micrometer metrics.

## 1.0.0 Highlights

- [Release Guide](roadmap/release-1-0-0)
- [Module Overview](core-concepts/modules)
- [Spring Boot Starter](spring-boot/overview)
- [State Machine Extension](roadmap/release-1-0-0#spring-boot-state-machine-extension-spice-springboot-statemachine)

## Quick Example

```kotlin
import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.graph.graph
import io.github.noailabs.spice.graph.nodes.agentNode
import io.github.noailabs.spice.graph.nodes.toolNode
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import io.github.noailabs.spice.SimpleTool
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val lookupTool = SimpleTool(
        name = "lookup_weather",
        description = "Return mock weather info",
    ) { params ->
        val city = params["city"] as? String ?: "Seoul"
        ToolResult.success(mapOf("city" to city, "temp" to 25))
    }

    val greetingAgent = agentNode("greet-agent") {
        handle { message ->
            val weather = message.data["weather"] ?: "no data"
            SpiceResult.success(
                message.reply("Weather summary: $weather", id = "greet-agent")
                    .transitionTo(ExecutionState.COMPLETED, "done")
            )
        }
    }

    val workflow = graph(id = "weather-report") {
        startWith(
            toolNode("fetch-weather", lookupTool) { params ->
                params["city"] = message.metadata["city"] ?: "Seoul"
            }
        ).then(greetingAgent) { previous ->
            message.withData(mapOf("weather" to previous.data))
        }
    }

    val runner = DefaultGraphRunner()
    val input = SpiceMessage.create(
        content = "Generate a weather summary",
        from = "user"
    ).withMetadata(mapOf("city" to "Busan"))

    val result = runner.execute(workflow, input)
    println(result.getOrThrow().content) // Weather summary: {city=Busan, temp=25}
}
```

The runner handles validation, state transitions, and produces a `SpiceResult<SpiceMessage>` you can persist or feed into HITL flows.

## Getting Started

Ready to dive in? Check out the [Installation Guide](./getting-started/installation) to get started with Spice Framework.

## Architecture Overview

```
┌─────────────────────────────────────────────────┐
│                Your Application                  │
├─────────────────────────────────────────────────┤
│                  Spice DSL                      │
│     buildAgent { } • graph { } • merge { }      │
├─────────────────────────────────────────────────┤
│                 Core Layer                      │
│    Agent • Comm • Tool • Registry System        │
├─────────────────────────────────────────────────┤
│              Integration Layer                  │
│    LLMs • Vector Stores • Event Bus • Spring   │
└─────────────────────────────────────────────────┘
```

## Community & Support

- **GitHub**: [no-ai-labs/spice](https://github.com/no-ai-labs/spice)
- **Issues**: [Report bugs](https://github.com/no-ai-labs/spice/issues)
- **Discussions**: [Ask questions](https://github.com/no-ai-labs/spice/discussions)
- **JitPack**: [Latest releases](https://jitpack.io/#no-ai-labs/spice-framework)

## License

Spice Framework is licensed under the MIT License. See [LICENSE](https://github.com/no-ai-labs/spice/blob/main/LICENSE) for details.

---

**Ready to spice up your AI applications?** 🌶️

[Get Started →](./getting-started/installation)
