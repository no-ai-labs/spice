---
sidebar_position: 1
---

# Welcome to Spice Framework 🌶️

**Modern Multi-LLM Orchestration Framework for Kotlin**

Spice Framework is a modern, type-safe, coroutine-first framework for building AI-powered applications in Kotlin. It provides a clean DSL for creating agents, managing tools, and orchestrating complex AI workflows with multiple LLM providers.

## Why Spice?

- **🚀 Simple yet Powerful** - Get started in minutes, scale to complex multi-agent systems
- **🔧 Type-Safe** - Leverage Kotlin's type system for compile-time safety
- **🌊 Async-First** - Built on coroutines for efficient concurrent operations
- **🎨 Clean DSL** - Intuitive API that reads like natural language
- **🔌 Extensible** - Easy to add custom agents, tools, and integrations

## Key Features

### Core Features
- **Unified Communication** - Single `Comm` type for all agent interactions
- **Generic Registry System** - Type-safe, thread-safe component management
- **Progressive Disclosure** - Simple things simple, complex things possible
- **Tool System** - Built-in tools and easy custom tool creation
- **JSON Serialization** - Production-grade JSON conversion for all components

### Advanced Features
- **Multi-LLM Support** - OpenAI, Anthropic, Google Vertex AI, and more
- **Swarm Intelligence** - Coordinate multiple agents for complex tasks
- **Vector Store Integration** - Built-in RAG support with multiple providers
- **MCP Protocol** - External tool integration via Model Context Protocol
- **Spring Boot Starter** - Seamless Spring Boot integration
- **Event Sourcing** - Complete event sourcing module with Kafka integration

## Quick Example

```kotlin
import io.github.noailabs.spice.dsl.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Create a simple agent
    val assistant = buildAgent {
        id = "assistant-1"
        name = "AI Assistant"
        description = "A helpful AI assistant"

        // Add an inline tool
        tool("greet") {
            description = "Greet someone"
            parameter("name", "string", "Person's name")
            execute { params ->
                ToolResult.success("Hello, ${params["name"]}!")
            }
        }

        // Define message handling
        handle { comm ->
            SpiceResult.success(
                comm.reply("How can I help you today?", id)
            )
        }
    }

    // Use the agent
    val result = assistant.processComm(
        Comm(content = "Hello!", from = "user")
    )
    result.fold(
        onSuccess = { response -> println(response.content) },
        onFailure = { error -> println("Error: ${error.message}") }
    )
}
```

## Getting Started

Ready to dive in? Check out the [Installation Guide](./getting-started/installation) to get started with Spice Framework.

## Architecture Overview

```
┌─────────────────────────────────────────────────┐
│                Your Application                  │
├─────────────────────────────────────────────────┤
│                  Spice DSL                      │
│         buildAgent { } • buildFlow { }          │
├─────────────────────────────────────────────────┤
│                 Core Layer                      │
│    Agent • Comm • Tool • Registry System        │
├─────────────────────────────────────────────────┤
│              Integration Layer                  │
│    LLMs • Vector Stores • MCP • Spring Boot    │
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
