# 🌶️ Spice Framework

<p align="center">
  <strong>Modern Multi-LLM Orchestration Framework for Kotlin</strong>
</p>

<p align="center">
  <a href="#features">Features</a> •
  <a href="#quick-start">Quick Start</a> •
  <a href="#architecture">Architecture</a> •
  <a href="#documentation">Documentation</a> •
  <a href="#contributing">Contributing</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-1.9.0-blue.svg" alt="Kotlin">
  <img src="https://img.shields.io/badge/Coroutines-1.7.3-green.svg" alt="Coroutines">
  <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License">
</p>

---

## 🎯 What is Spice?

Spice Framework is a modern, type-safe, coroutine-first framework for building AI-powered applications in Kotlin. It provides a clean DSL for creating agents, managing tools, and orchestrating complex AI workflows with multiple LLM providers.

### Why Spice?

- **🚀 Simple yet Powerful** - Get started in minutes, scale to complex multi-agent systems
- **🔧 Type-Safe** - Leverage Kotlin's type system for compile-time safety
- **🌊 Async-First** - Built on coroutines for efficient concurrent operations
- **🎨 Clean DSL** - Intuitive API that reads like natural language
- **🔌 Extensible** - Easy to add custom agents, tools, and integrations

## ✨ Features

### Core Features
- **Unified Communication** - Single `Comm` type for all agent interactions
- **Generic Registry System** - Type-safe, thread-safe component management
- **Progressive Disclosure** - Simple things simple, complex things possible
- **Tool System** - Built-in tools and easy custom tool creation

### Advanced Features
- **Multi-LLM Support** - OpenAI, Anthropic, Google Vertex AI, and more
- **Swarm Intelligence** - Coordinate multiple agents for complex tasks
- **Vector Store Integration** - Built-in RAG support with multiple providers
- **MCP Protocol** - External tool integration via Model Context Protocol
- **Spring Boot Starter** - Seamless Spring Boot integration

## 🚀 Quick Start

### Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.spice:spice-core:0.1.0")
    
    // Optional modules
    implementation("io.github.spice:spice-springboot:0.1.0")
}
```

### Your First Agent

```kotlin
import io.github.spice.*
import io.github.spice.dsl.*
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
                "Hello, ${params["name"]}! How can I help you today?"
            }
        }
        
        // Define message handling
        handle { comm ->
            when {
                comm.content.startsWith("greet ") -> {
                    val name = comm.content.removePrefix("greet ").trim()
                    val result = run("greet", mapOf("name" to name))
                    comm.reply(result.result.toString(), id)
                }
                else -> comm.reply("Say 'greet NAME' to get a greeting!", id)
            }
        }
    }
    
    // Use the agent
    val response = assistant.processComm(
        Comm(content = "greet Alice", from = "user")
    )
    println(response.content) // "Hello, Alice! How can I help you today?"
}
```

### Using LLM Providers

```kotlin
// OpenAI Integration
val gptAgent = buildOpenAIAgent {
    id = "gpt-4"
    name = "GPT-4 Assistant"
    apiKey = System.getenv("OPENAI_API_KEY")
    model = "gpt-4"
    systemPrompt = "You are a helpful coding assistant."
}

// Anthropic Integration
val claudeAgent = buildClaudeAgent {
    id = "claude-3"
    name = "Claude Assistant"
    apiKey = System.getenv("ANTHROPIC_API_KEY")
    model = "claude-3-opus-20240229"
}

// Use them just like any other agent
val response = gptAgent.processComm(
    Comm(content = "Explain coroutines in Kotlin", from = "user")
)
```

## 🏗️ Architecture

### Core Components

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

### Key Design Patterns

1. **Registry Pattern** - Centralized management of agents, tools, and flows
2. **Builder Pattern** - Intuitive DSL for creating components
3. **Strategy Pattern** - Pluggable LLM providers and tool implementations
4. **Observer Pattern** - Event-driven agent communication

### Component Overview

- **`Agent`** - Base interface for all intelligent agents
- **`Comm`** - Universal communication unit (replaces legacy Message system)
- **`Tool`** - Reusable functions agents can execute
- **`Registry<T>`** - Generic, thread-safe component registry
- **`SmartCore`** - Next-generation agent system
- **`CommHub`** - Central message routing system

## 📚 Documentation

Comprehensive documentation is available in our [GitHub Wiki](https://github.com/no-ai-labs/spice/wiki):

- 📖 **[Getting Started Guide](https://github.com/no-ai-labs/spice/wiki/Getting-Started)** - Installation and first steps
- 🎯 **[Core Concepts](https://github.com/no-ai-labs/spice/wiki/Core-Concepts)** - Understanding the fundamentals
- 🏗️ **[Architecture Overview](https://github.com/no-ai-labs/spice/wiki/Architecture)** - System design and patterns
- 📚 **[Examples](https://github.com/no-ai-labs/spice/wiki/Examples)** - Learn by example
- 🔧 **[API Reference](https://github.com/no-ai-labs/spice/wiki/API-Reference)** - Detailed API documentation

## 🛠️ Advanced Usage

### Multi-Agent Collaboration

```kotlin
// Create specialized agents
val researcher = buildAgent {
    id = "researcher"
    name = "Research Agent"
    // ... configuration
}

val analyzer = buildAgent {
    id = "analyzer"
    name = "Analysis Agent"
    // ... configuration
}

// Register them
AgentRegistry.register(researcher)
AgentRegistry.register(analyzer)

// Create a workflow
val researchFlow = buildFlow {
    id = "research-flow"
    name = "Research and Analyze"
    
    step("research", "researcher")
    step("analyze", "analyzer") { comm ->
        // Only analyze if research found something
        comm.content.isNotEmpty()
    }
}
```

### Vector Store Integration

```kotlin
val ragAgent = buildAgent {
    id = "rag-agent"
    name = "RAG Assistant"
    
    // Configure vector store
    vectorStore("knowledge") {
        provider("qdrant")
        connection("localhost", 6333)
        collection("documents")
    }
    
    handle { comm ->
        // Automatic vector search tool available
        val results = run("search-knowledge", mapOf(
            "query" to comm.content,
            "topK" to 5
        ))
        // Process results...
    }
}
```

## 🌱 Spring Boot Integration

```kotlin
@SpringBootApplication
@EnableSpice
class MyApplication

@Component
class MyService(
    @Autowired private val agentRegistry: AgentRegistry
) {
    fun processRequest(message: String): String {
        val agent = agentRegistry.get("my-agent")
        val response = runBlocking {
            agent?.processComm(Comm(content = message, from = "user"))
        }
        return response?.content ?: "No response"
    }
}
```

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details.

### Development Setup

```bash
# Clone the repository
git clone https://github.com/spice-framework/spice.git
cd spice-framework

# Build the project
./gradlew build

# Run tests
./gradlew test
```

## 📊 Project Status

- ✅ Core Agent System
- ✅ Generic Registry System
- ✅ Unified Communication (Comm)
- ✅ Tool Management System
- ✅ LLM Integrations (OpenAI, Anthropic)
- ✅ Spring Boot Starter
- 🚧 Swarm Intelligence (Beta)
- 🚧 Vector Store Integrations (Beta)
- 📋 MCP Protocol Support (Planned)

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Built with ❤️ using Kotlin and Coroutines
- Inspired by modern AI agent architectures
- Special thanks to all contributors

## 📬 Contact

- **GitHub Issues**: [Report bugs or request features](https://github.com/no-ai-labs/spice/issues)
- **Discussions**: [Ask questions and share ideas](https://github.com/no-ai-labs/spice/discussions)
- **Wiki**: [Comprehensive documentation](https://github.com/no-ai-labs/spice/wiki)

---

<p align="center">
  <strong>Ready to spice up your AI applications? 🌶️</strong>
</p>

<p align="center">
  <a href="https://github.com/no-ai-labs/spice/wiki/Getting-Started">Get Started</a> •
  <a href="https://github.com/no-ai-labs/spice/wiki/Examples">View Examples</a> •
  <a href="https://github.com/no-ai-labs/spice/wiki">Read Docs</a>
</p>
