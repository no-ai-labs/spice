# 🌶️ Spice Agents

> Standalone LLM agent implementations for Spice Framework

[![Maven Central](https://img.shields.io/maven-central/v/io.github.noailabs/spice-agents)](https://central.sonatype.com/artifact/io.github.noailabs/spice-agents)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.0-blue.svg?logo=kotlin)](http://kotlinlang.org)

## Overview

`spice-agents` provides standalone LLM agent implementations that work **without Spring dependency**. Built with Ktor HTTP client for maximum portability.

### Supported Providers

- ✅ **OpenAI** (GPT-4, GPT-3.5-turbo, GPT-4-turbo)
- ✅ **Anthropic** (Claude 3.5 Sonnet, Claude 3 Opus/Haiku)
- 🧪 **Mock Agents** (for testing)

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("io.github.noailabs:spice-core:1.0.0-alpha-1")
    implementation("io.github.noailabs:spice-agents:1.0.0-alpha-1")
}
```

### Maven

```xml
<dependencies>
    <dependency>
        <groupId>io.github.noailabs</groupId>
        <artifactId>spice-agents</artifactId>
        <version>1.0.0-alpha-1</version>
    </dependency>
</dependencies>
```

## Quick Start

### 1. Factory Functions (Easiest)

```kotlin
import io.github.noailabs.spice.agents.gptAgent
import io.github.noailabs.spice.SpiceMessage

// Create agent
val agent = gptAgent(
    apiKey = System.getenv("OPENAI_API_KEY"),
    model = "gpt-4",
    systemPrompt = "You are a helpful AI assistant."
)

// Use agent
val response = agent.processMessage(
    SpiceMessage.create("Hello, how are you?", "user")
)

println(response.getOrThrow().content)
```

### 2. Constructor (Direct instantiation)

```kotlin
import io.github.noailabs.spice.agents.GPTAgent

val agent = GPTAgent(
    apiKey = "sk-...",
    model = "gpt-4",
    temperature = 0.7,
    maxTokens = 2000
)
```

### 3. Builder DSL

```kotlin
val agent = buildAgent {
    id = "custom-agent"
    name = "Custom Agent"
    description = "Agent with custom logic"

    handle { message ->
        // Custom processing logic
        SpiceResult.success(
            message.reply("Processed: ${message.content}", id)
        )
    }
}
```

## API Styles

### All 3 Styles Supported

#### 1️⃣ Factory Functions
```kotlin
// OpenAI
val gpt = gptAgent(
    apiKey = "sk-...",
    model = "gpt-4",
    temperature = 0.7
)

// Anthropic
val claude = claudeAgent(
    apiKey = "sk-ant-...",
    model = "claude-3-5-sonnet-20241022"
)

// Mock (for testing)
val mock = mockGPTAgent(
    responses = listOf("Response 1", "Response 2")
)
```

#### 2️⃣ Direct Constructor
```kotlin
val agent = GPTAgent(
    apiKey = System.getenv("OPENAI_API_KEY"),
    model = "gpt-4",
    systemPrompt = "You are helpful.",
    temperature = 0.7,
    maxTokens = 2000,
    tools = listOf(webSearchTool, calculatorTool)
)
```

#### 3️⃣ Builder DSL
```kotlin
val agent = buildAgent {
    id = "my-agent"
    name = "My Custom Agent"
    description = "Custom agent with tools"

    // Add tools
    tools(webSearchTool, calculatorTool)

    // Custom handler
    handle { message ->
        // Custom processing logic
        SpiceResult.success(
            message.reply("Processed with tools: ${message.content}", id)
        )
    }
}
```

## Features

### 🚀 No Spring Dependency
- Pure Kotlin + Ktor
- Works in any Kotlin/JVM application
- No heavy framework required

### 🔌 Pluggable Architecture
- Implement `Agent` interface
- Works with Spice GraphRunner
- Compatible with Spring AI integration

### 🛠️ Tool Support
- Pass `List<Tool>` to agents
- Automatic tool call handling
- Compatible with Spice Tool system

### 🧪 Testing Support
- `mockGPTAgent()` and `mockClaudeAgent()`
- Predefined responses
- No API calls

## Advanced Usage

### With Tools

```kotlin
import io.github.noailabs.spice.Tool

val webSearchTool = /* ... */
val calculatorTool = /* ... */

val agent = gptAgent(
    apiKey = System.getenv("OPENAI_API_KEY"),
    model = "gpt-4",
    tools = listOf(webSearchTool, calculatorTool)
)
```

### Custom Base URL (for proxies or local models)

```kotlin
val agent = gptAgent(
    apiKey = "sk-...",
    model = "gpt-4",
    baseUrl = "https://my-proxy.com/v1"  // Custom endpoint
)
```

### With Graph Integration

```kotlin
import io.github.noailabs.spice.graph.graph
import io.github.noailabs.spice.graph.DefaultGraphRunner

val agent = gptAgent(apiKey = "...")

val graph = graph("assistant") {
    agent("main", agent)
    output("end")
    edge("main", "end")
}

val runner = DefaultGraphRunner()
val result = runner.execute(graph, SpiceMessage.create("Hello", "user"))
```

## Configuration Options

### OpenAI (GPT)

```kotlin
gptAgent(
    apiKey: String,                // Required: OpenAI API key
    model: String = "gpt-4",       // Model name
    systemPrompt: String? = null,  // System prompt
    temperature: Double = 0.7,     // 0.0 - 2.0
    maxTokens: Int? = null,        // Max completion tokens
    tools: List<Tool> = emptyList(),
    id: String = "gpt-$model",
    name: String = "GPT Agent",
    description: String = "...",
    baseUrl: String = "https://api.openai.com/v1",
    organizationId: String? = null
)
```

### Anthropic (Claude)

```kotlin
claudeAgent(
    apiKey: String,                          // Required: Anthropic API key
    model: String = "claude-3-5-sonnet-...", // Model name
    systemPrompt: String? = null,
    temperature: Double = 0.7,
    maxTokens: Int? = 1024,
    tools: List<Tool> = emptyList(),
    id: String = "claude-$model",
    name: String = "Claude Agent",
    description: String = "...",
    baseUrl: String = "https://api.anthropic.com"
)
```

## Testing

### Mock Agents

```kotlin
import io.github.noailabs.spice.agents.mockGPTAgent

val mock = mockGPTAgent(
    responses = listOf(
        "First response",
        "Second response",
        "Third response"
    )
)

// Cycles through responses
val r1 = mock.processMessage(SpiceMessage.create("Q1", "user"))  // "First response"
val r2 = mock.processMessage(SpiceMessage.create("Q2", "user"))  // "Second response"
val r3 = mock.processMessage(SpiceMessage.create("Q3", "user"))  // "Third response"
val r4 = mock.processMessage(SpiceMessage.create("Q4", "user"))  // "First response" (cycles)
```

## Migration from 0.9.0

### Before (0.9.0)
```kotlin
import io.github.noailabs.spice.agents.gptAgent

val agent = gptAgent(
    apiKey = "...",
    model = "gpt-4"
)

// Uses Comm
val result = agent.processComm(comm)
```

### After (1.0.0)
```kotlin
import io.github.noailabs.spice.agents.gptAgent

val agent = gptAgent(
    apiKey = "...",
    model = "gpt-4"
)

// Uses SpiceMessage
val result = agent.processMessage(message)
```

**Changes:**
- ✅ Same `gptAgent()` DSL
- ⚠️ `processComm(Comm)` → `processMessage(SpiceMessage)`
- ✅ Module dependency: Add `spice-agents` to your build

## Architecture

```
┌─────────────────────────────────────┐
│      Your Application               │
└─────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────┐
│      spice-agents                   │
│  ┌─────────────────────────────┐   │
│  │  gptAgent()                 │   │
│  │  claudeAgent()              │   │
│  │  mockGPTAgent()             │   │
│  └─────────────────────────────┘   │
│              │                      │
│              ▼                      │
│  ┌─────────────────────────────┐   │
│  │  GPTAgent                   │   │
│  │  ClaudeAgent                │   │
│  │  MockAgent                  │   │
│  └─────────────────────────────┘   │
│              │                      │
│              ▼                      │
│  ┌─────────────────────────────┐   │
│  │  OpenAIClient (Ktor)        │   │
│  │  AnthropicClient (Ktor)     │   │
│  └─────────────────────────────┘   │
└─────────────────────────────────────┘
              │
              ▼
     ┌─────────────────┐
     │  OpenAI API     │
     │  Anthropic API  │
     └─────────────────┘
```

## Roadmap

### Phase 1: Core Agents ✅ (Completed)
- [x] GPTAgent with Ktor
- [x] ClaudeAgent with AnthropicClient (full implementation)
- [x] Factory functions DSL
- [x] MockAgent for testing

### Phase 2: Builder DSL ✅ (Completed)
- [x] `buildAgent { }` DSL
- [x] Tool integration patterns
- [x] Vector store DSL
- [x] Custom handler support

### Phase 3: Advanced Features
- [ ] Streaming support
- [ ] Function calling
- [ ] Multi-turn conversations
- [ ] Context management

## License

MIT License - see [LICENSE](../LICENSE) file for details.

## Links

- [Spice Framework](https://github.com/no-ai-labs/spice)
- [API Documentation](https://javadoc.io/doc/io.github.noailabs/spice-agents)
- [Examples](../examples/)
