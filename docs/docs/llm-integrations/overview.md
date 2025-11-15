# LLM Integrations

Spice Framework 1.0.0 provides seamless integration with multiple LLM providers through **Spring AI 1.1.0**.

## Supported Providers

- **OpenAI** - GPT-4, GPT-4-turbo, GPT-3.5-turbo
- **Anthropic** - Claude 3.5 Sonnet, Claude 3 Opus, Claude 3 Haiku
- **Ollama** - Local LLMs (Llama, Mistral, etc.)
- **Azure OpenAI** - Coming soon
- **Vertex AI** - Coming soon
- **AWS Bedrock** - Coming soon

## Installation

Add the Spring AI integration module to your project:

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("io.github.noailabs:spice-springboot-ai:1.0.0-alpha-2")
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.noailabs</groupId>
    <artifactId>spice-springboot-ai</artifactId>
    <version>1.0.0-alpha-2</version>
</dependency>
```

## Quick Start

### 1. Configure via `application.yml`

```yaml
spice:
  spring-ai:
    enabled: true
    default-provider: openai

    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY}
      model: gpt-4
      temperature: 0.7
      max-tokens: 2048

    anthropic:
      enabled: true
      api-key: ${ANTHROPIC_API_KEY}
      model: claude-3-5-sonnet-20241022
      temperature: 0.7
      max-tokens: 4096

    ollama:
      enabled: true
      base-url: http://localhost:11434
      model: llama3
```

### 2. Use SpringAIAgentFactory

```kotlin
import io.github.noailabs.spice.springboot.ai.factory.SpringAIAgentFactory
import io.github.noailabs.spice.SpiceMessage
import org.springframework.stereotype.Service

@Service
class ChatService(
    private val factory: SpringAIAgentFactory
) {
    suspend fun chat(message: String): String {
        // Create OpenAI agent
        val agent = factory.openai("gpt-4")

        // Process message
        val response = agent.processMessage(
            SpiceMessage.create(message, "user")
        )

        return response.getOrThrow().content
    }
}
```

### 3. Or Use Auto-configured Default Agent

```kotlin
import io.github.noailabs.spice.Agent
import org.springframework.stereotype.Service

@Service
class SimpleService(
    private val agent: Agent  // Auto-wired default agent
) {
    suspend fun chat(message: String): String {
        val response = agent.processMessage(
            SpiceMessage.create(message, "user")
        )
        return response.getOrThrow().content
    }
}
```

## Multi-Provider Support

Switch between providers easily:

```kotlin
@Service
class MultiProviderService(
    private val factory: SpringAIAgentFactory
) {
    suspend fun chatWithOpenAI(message: String): String {
        val agent = factory.openai("gpt-4")
        return processMessage(agent, message)
    }

    suspend fun chatWithClaude(message: String): String {
        val agent = factory.anthropic("claude-3-5-sonnet-20241022")
        return processMessage(agent, message)
    }

    suspend fun chatWithOllama(message: String): String {
        val agent = factory.ollama("llama3")
        return processMessage(agent, message)
    }

    private suspend fun processMessage(agent: Agent, message: String): String {
        val response = agent.processMessage(
            SpiceMessage.create(message, "user")
        )
        return response.getOrThrow().content
    }
}
```

## Advanced Configuration

### Custom Agent with Options

```kotlin
import io.github.noailabs.spice.springboot.ai.factory.OpenAIConfig

val customAgent = factory.openai(
    model = "gpt-4-turbo",
    config = OpenAIConfig(
        temperature = 0.9,
        maxTokens = 4096,
        topP = 0.95,
        frequencyPenalty = 0.5,
        presencePenalty = 0.5,
        systemPrompt = "You are an expert code reviewer."
    )
)
```

### Agent Registry

Auto-register multiple agents:

```yaml
spice:
  spring-ai:
    registry:
      enabled: true
      auto-register: true  # Auto-registers all enabled providers
```

```kotlin
import io.github.noailabs.spice.springboot.ai.registry.AgentRegistry

@Service
class RegistryService(
    private val registry: AgentRegistry
) {
    suspend fun chatWithProvider(provider: String, message: String): String {
        val agent = registry.get(provider)  // "openai", "anthropic", "ollama"
        val response = agent.processMessage(
            SpiceMessage.create(message, "user")
        )
        return response.getOrThrow().content
    }
}
```

## Architecture

Spice's Spring AI integration uses the **Adapter Pattern**:

```
┌─────────────────────┐
│  Spring AI 1.1.0    │
│  (OpenAI, Claude)   │
└──────────┬──────────┘
           │
┌──────────▼──────────┐
│ ChatModelToAgent    │
│      Adapter        │
└──────────┬──────────┘
           │
┌──────────▼──────────┐
│   Spice Agent       │
│   Interface         │
└─────────────────────┘
```

- **Provider-agnostic**: Same interface for all LLM providers
- **Spring Boot native**: Leverages Spring's dependency injection and configuration
- **Type-safe**: Kotlin-first design with compile-time safety

## Features

✅ **Multiple Providers** - OpenAI, Anthropic, Ollama, and more
✅ **Auto-configuration** - Zero-config setup with Spring Boot
✅ **Type-safe** - Kotlin DSL with compile-time checks
✅ **Async-first** - Coroutine-based for high performance
✅ **Tool Calling** - Function calling support (coming soon)
✅ **Streaming** - Streaming responses (coming soon)

## Next Steps

- [OpenAI Integration](./openai) - GPT-4 and GPT-3.5 setup
- [Anthropic Integration](./anthropic) - Claude 3.5 Sonnet setup
- [Ollama Integration](./ollama) - Local LLMs setup (coming soon)
- [Tool Calling](../tools-extensions/creating-tools) - Function calling with LLMs
