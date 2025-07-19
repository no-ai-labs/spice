# Spice Framework Wiki

Welcome to the Spice Framework documentation! This wiki provides comprehensive guides for using the framework.

## 📚 Table of Contents

1. [Getting Started](getting-started.md) - Quick start guide
2. [Core Concepts](core-concepts.md) - Understanding the basics
3. [Agent Guide](agent-guide.md) - Creating and managing agents
4. [Tool Development](tool-development.md) - Building custom tools
5. [Flow Orchestration](flow-orchestration.md) - Workflow management
6. [Advanced Features](advanced-features.md) - Swarm, MCP, and more
7. [Spring Boot Integration](spring-boot.md) - Using with Spring
8. [KMP Configuration](kmp-configuration.md) - Kotlin Multiplatform setup (NEW)
9. [API Reference](api-reference.md) - Complete API documentation

## 🚀 Quick Example

```kotlin
import io.github.spice.dsl.*

// Create a simple agent
val agent = buildAgent {
    id = "helper"
    name = "Helper Agent"
    
    handle { comm ->
        comm.reply("Hello! How can I help you?", id)
    }
}

// Use the agent
val response = agent.process(comm("What's the weather?"))
println(response.content)
```

## 📖 Architecture Overview

The Spice Framework follows a simple 3-tier architecture:

```
Agent > Flow > Tool
```

- **Agents**: Autonomous units that process messages
- **Flows**: Orchestration of multiple agents
- **Tools**: Reusable capabilities that agents can use

## 🔗 Links

- [GitHub Repository](https://github.com/devhub/spice-framework)
- [Sample Projects](../spice-dsl-samples)
- [Issue Tracker](https://github.com/devhub/spice-framework/issues)