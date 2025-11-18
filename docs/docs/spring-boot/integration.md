# Spring Boot Integration

Use Spice agents as Spring beans with Spring AI 1.1.0.

## Prerequisites

- Spice Framework 1.0.0-alpha-2 or later
- Spring Boot 3.5.7+
- LLM provider API keys (OpenAI, Anthropic, etc.)

## Configuration

Configure Spice in `application.yml`:

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

    anthropic:
      enabled: true
      api-key: ${ANTHROPIC_API_KEY}
      model: claude-3-5-sonnet-20241022
      temperature: 0.7
```

## Method 1: Inject Default Agent

Inject the auto-configured default agent:

```kotlin
import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.SpiceMessage
import org.springframework.stereotype.Service

@Service
class MyService(
    private val agent: Agent  // Auto-wired from default-provider
) {
    suspend fun process(message: String): String {
        val response = agent.processMessage(
            SpiceMessage.create(message, "user")
        )
        return response.getOrThrow().content
    }
}
```

## Method 2: Use SpringAIAgentFactory

Create agents programmatically with the factory:

```kotlin
import io.github.noailabs.spice.springboot.ai.factory.SpringAIAgentFactory
import io.github.noailabs.spice.springboot.ai.factory.OpenAIConfig
import io.github.noailabs.spice.SpiceMessage
import org.springframework.stereotype.Service

@Service
class ChatService(
    private val factory: SpringAIAgentFactory
) {
    suspend fun chat(userMessage: String): String {
        // Create OpenAI agent
        val agent = factory.openai("gpt-4")

        val response = agent.processMessage(
            SpiceMessage.create(userMessage, "user")
        )

        return response.getOrThrow().content
    }

    suspend fun chatWithClaude(userMessage: String): String {
        // Create Anthropic agent
        val agent = factory.anthropic("claude-3-5-sonnet-20241022")

        val response = agent.processMessage(
            SpiceMessage.create(userMessage, "user")
        )

        return response.getOrThrow().content
    }

    suspend fun chatWithCustomConfig(userMessage: String): String {
        // Create agent with custom configuration
        val agent = factory.openai(
            model = "gpt-4-turbo",
            config = OpenAIConfig(
                temperature = 0.9,
                maxTokens = 4096,
                systemPrompt = "You are a helpful coding assistant."
            )
        )

        val response = agent.processMessage(
            SpiceMessage.create(userMessage, "user")
        )

        return response.getOrThrow().content
    }
}
```

## Method 3: Use Agent Registry

Use the agent registry for multi-agent management:

```kotlin
import io.github.noailabs.spice.springboot.ai.registry.AgentRegistry
import io.github.noailabs.spice.SpiceMessage
import org.springframework.stereotype.Service

@Service
class MultiAgentService(
    private val registry: AgentRegistry
) {
    suspend fun chatWithProvider(provider: String, message: String): String {
        // Get registered agent by provider name
        val agent = registry.get(provider)  // "openai", "anthropic", "ollama"

        val response = agent.processMessage(
            SpiceMessage.create(message, "user")
        )

        return response.getOrThrow().content
    }
}
```

## Error Handling

Handle errors with SpiceResult:

```kotlin
import io.github.noailabs.spice.error.SpiceResult
import org.springframework.stereotype.Service

@Service
class RobustService(
    private val factory: SpringAIAgentFactory
) {
    private val agent = factory.openai("gpt-4")

    suspend fun safeChat(message: String): String {
        val response = agent.processMessage(
            SpiceMessage.create(message, "user")
        )

        return when (response) {
            is SpiceResult.Success -> response.value.content
            is SpiceResult.Failure -> {
                logger.error("Chat failed: ${response.error.message}")
                "Sorry, I encountered an error. Please try again."
            }
        }
    }
}
```

## Next Steps

- [LLM Integrations Overview](../llm-integrations/overview) - Supported providers
- [OpenAI Integration](../llm-integrations/openai) - GPT-4 setup
- [Anthropic Integration](../llm-integrations/anthropic) - Claude setup
