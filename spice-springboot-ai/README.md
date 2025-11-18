# 🌶️ Spice Spring Boot AI

> Spring AI integration for Spice Framework with multi-provider support and flexible API design

[![Maven Central](https://img.shields.io/maven-central/v/io.github.noailabs/spice-springboot-ai)](https://central.sonatype.com/artifact/io.github.noailabs/spice-springboot-ai)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.0-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![Spring Boot](https://img.shields.io/badge/spring--boot-3.5.7-green.svg)](https://spring.io/projects/spring-boot)

## Features

- 🚀 **Multi-Provider Support**: OpenAI, Anthropic (Claude), Ollama, Azure OpenAI, Vertex AI, Bedrock
- 🏭 **4-Tier API Flexibility**: Auto-config, Factory, Builder/DSL, Registry
- 🔌 **Zero-Config Setup**: Auto-wiring with Spring Boot
- 🎯 **Type-Safe Configuration**: Kotlin-idiomatic DSL and configuration properties
- 🛠️ **Tool Integration**: Bidirectional Spring AI ↔ Spice Tool conversion
- 🌊 **Streaming Support**: Built-in streaming chat model support
- 📚 **Agent Registry**: Multi-agent management for runtime selection
- ⚡ **Reactive**: Full Kotlin coroutines + Project Reactor support

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("io.github.noailabs:spice-springboot-ai:1.0.0-alpha-1")
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}
```

### Maven

```xml
<dependencies>
    <dependency>
        <groupId>io.github.noailabs</groupId>
        <artifactId>spice-springboot-ai</artifactId>
        <version>1.0.0-alpha-1</version>
    </dependency>
</dependencies>

<repositories>
    <repository>
        <id>spring-milestones</id>
        <url>https://repo.spring.io/milestone</url>
    </repository>
</repositories>
```

## Quick Start

### 1. Zero-Config (Auto-Wiring)

**application.yml:**
```yaml
spice:
  spring-ai:
    default-provider: openai
    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY}
      model: gpt-4
      temperature: 0.7
```

**Service:**
```kotlin
@Service
class ChatService(private val agent: Agent) {

    suspend fun chat(message: String): String {
        val response = agent.processMessage(
            SpiceMessage.create(message, "user")
        )
        return response.getOrThrow().content
    }
}
```

### 2. Factory API (Programmatic Creation)

```kotlin
@Service
class MultiModelService(private val factory: SpringAIAgentFactory) {

    suspend fun smartChat(message: String): String {
        val agent = factory.openai("gpt-4")
        return agent.processMessage(SpiceMessage.create(message, "user"))
            .getOrThrow().content
    }

    suspend fun fastChat(message: String): String {
        val agent = factory.openai("gpt-3.5-turbo")
        return agent.processMessage(SpiceMessage.create(message, "user"))
            .getOrThrow().content
    }

    suspend fun claudeChat(message: String): String {
        val agent = factory.anthropic("claude-3-5-sonnet-20241022")
        return agent.processMessage(SpiceMessage.create(message, "user"))
            .getOrThrow().content
    }
}
```

### 3. Builder/DSL Pattern (Kotlin Idiomatic)

```kotlin
@Configuration
class AgentConfig(private val factory: SpringAIAgentFactory) {

    @Bean
    fun codeReviewAgent() = springAIAgent(factory) {
        provider = "openai"
        model = "gpt-4"
        temperature = 0.3
        maxTokens = 2000
        systemPrompt = "You are a senior code reviewer."
    }

    @Bean
    fun documentationAgent() = anthropicAgent(factory, "claude-3-5-sonnet-20241022") {
        temperature = 0.7
        maxTokens = 4000
        systemPrompt = "You are a technical documentation expert."
    }

    @Bean
    fun localAgent() = ollamaAgent(factory, "llama3") {
        temperature = 0.5
        systemPrompt = "You are a helpful AI assistant."
    }
}
```

### 4. Registry Pattern (Multi-Agent Management)

```kotlin
@Configuration
class AgentRegistryConfig(private val factory: SpringAIAgentFactory) {

    @Bean
    fun agentRegistry(): AgentRegistry {
        return DefaultAgentRegistry().apply {
            register("fast", factory.openai("gpt-3.5-turbo"))
            register("smart", factory.openai("gpt-4"))
            register("reasoning", factory.openai("o1"))
            register("claude", factory.anthropic("claude-3-5-sonnet-20241022"))
            register("local", factory.ollama("llama3"))
        }
    }
}

@Service
class DynamicChatService(private val registry: AgentRegistry) {

    suspend fun chat(agentName: String, message: String): String {
        val agent = registry.get(agentName)
        return agent.processMessage(SpiceMessage.create(message, "user"))
            .getOrThrow().content
    }

    suspend fun routeByComplexity(message: String, isComplex: Boolean): String {
        val agentName = if (isComplex) "smart" else "fast"
        return chat(agentName, message)
    }
}
```

## Configuration Reference

### Full Configuration Example

```yaml
spice:
  spring-ai:
    enabled: true
    default-provider: openai  # or anthropic, ollama, etc.

    # OpenAI Configuration
    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY}
      model: gpt-4
      base-url: https://api.openai.com  # Optional: custom endpoint
      organization-id: org-xxx  # Optional
      temperature: 0.7
      max-tokens: 2000
      top-p: 1.0
      frequency-penalty: 0.0
      presence-penalty: 0.0

    # Anthropic Configuration
    anthropic:
      enabled: true
      api-key: ${ANTHROPIC_API_KEY}
      model: claude-3-5-sonnet-20241022
      temperature: 0.7
      max-tokens: 4000
      top-p: 1.0
      top-k: 40

    # Ollama Configuration (Local LLMs)
    ollama:
      enabled: true
      base-url: http://localhost:11434
      model: llama3
      temperature: 0.7
      num-gpu: 1
      num-thread: 8

    # Chat Defaults (applies to all providers)
    chat:
      default-temperature: 0.7
      default-max-tokens: 2000
      default-system-prompt: "You are a helpful AI assistant."
      timeout-ms: 60000
      retry:
        enabled: true
        max-attempts: 3
        initial-backoff-ms: 1000
        max-backoff-ms: 10000
        backoff-multiplier: 2.0

    # Streaming Configuration
    streaming:
      enabled: true
      buffer-size: 10
      stream-timeout-ms: 120000

    # Agent Registry Configuration
    registry:
      enabled: true
      auto-register: true  # Auto-register enabled providers
      thread-safe: true
```

## Advanced Usage

### Dynamic Provider Selection

```kotlin
@Service
class SmartRoutingService(private val factory: SpringAIAgentFactory) {

    suspend fun process(request: UserRequest): SpiceMessage {
        // Select provider based on requirements
        val agent = when {
            request.requiresFastResponse ->
                factory.openai("gpt-3.5-turbo")

            request.requiresComplexReasoning ->
                factory.openai("gpt-4")

            request.requiresLongContext ->
                factory.anthropic("claude-3-5-sonnet-20241022")

            request.isLocal ->
                factory.ollama("llama3")

            else -> factory.default()
        }

        return agent.processMessage(request.message).getOrThrow()
    }
}
```

### Override Auto-Configuration

```kotlin
@Configuration
class CustomSpringAIConfig {

    // Override factory
    @Bean
    fun springAIAgentFactory(properties: SpringAIProperties): SpringAIAgentFactory {
        return MyCustomAgentFactory(properties)
    }

    // Override default agent
    @Bean("spice.spring-ai.defaultAgent")
    fun customDefaultAgent(factory: SpringAIAgentFactory): Agent {
        return factory.openai("gpt-4-turbo", OpenAIConfig(
            temperature = 0.5,
            systemPrompt = "Custom system prompt"
        ))
    }

    // Override registry
    @Bean
    fun agentRegistry(factory: SpringAIAgentFactory): AgentRegistry {
        return DefaultAgentRegistry().apply {
            register("production", factory.openai("gpt-4"))
            register("development", factory.ollama("llama3"))
        }
    }
}
```

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Spice Application                        │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│              Spice Spring Boot AI Module                    │
│  ┌─────────────────────────────────────────────────────┐   │
│  │    SpringAIAutoConfiguration                        │   │
│  │  - Auto-wiring                                      │   │
│  │  - Conditional beans                                │   │
│  └─────────────────────────────────────────────────────┘   │
│                          │                                  │
│         ┌────────────────┼────────────────┐                │
│         ▼                ▼                ▼                │
│  ┌───────────┐    ┌───────────┐    ┌───────────┐         │
│  │  Factory  │    │  Builder  │    │ Registry  │         │
│  │    API    │    │    DSL    │    │  Pattern  │         │
│  └───────────┘    └───────────┘    └───────────┘         │
│         │                │                │                │
│         └────────────────┼────────────────┘                │
│                          ▼                                  │
│         ┌──────────────────────────────────┐               │
│         │  ChatModelToAgentAdapter         │               │
│         │  - SpiceMessage → Prompt         │               │
│         │  - ChatResponse → SpiceMessage   │               │
│         └──────────────────────────────────┘               │
│                          │                                  │
└──────────────────────────┼──────────────────────────────────┘
                          ▼
         ┌─────────────────────────────────────┐
         │         Spring AI                   │
         │  - OpenAI API                       │
         │  - Anthropic API                    │
         │  - Ollama API                       │
         │  - Azure OpenAI, Vertex, Bedrock    │
         └─────────────────────────────────────┘
```

## Comparison with spice-springboot-statemachine

| Feature | State Machine | Spring AI |
|---------|--------------|-----------|
| Auto-configuration | ✅ | ✅ |
| Factory API | ⚠️ Spring-coupled | ✅ Standalone |
| Builder/DSL | ❌ | ✅ Kotlin idiomatic |
| Registry Pattern | ❌ | ✅ Multi-agent mgmt |
| @ConditionalOnMissingBean | 1 bean | All beans |
| Runtime Flexibility | ❌ | ✅ Full |
| Multi-instance | ❌ | ✅ |
| Programmatic Config | ❌ | ✅ |

## Examples

See the `examples/` directory for complete sample applications:

- **simple-chat**: Basic zero-config chat application
- **multi-provider**: Dynamic provider selection
- **agent-registry**: Multi-agent management
- **tool-integration**: Function calling with Spring AI tools

## License

MIT License - see [LICENSE](../LICENSE) file for details.

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](../CONTRIBUTING.md) for guidelines.

## Links

- [Spice Framework Documentation](https://github.com/no-ai-labs/spice)
- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
- [API Reference](https://javadoc.io/doc/io.github.noailabs/spice-springboot-ai)
- [Issue Tracker](https://github.com/no-ai-labs/spice/issues)
