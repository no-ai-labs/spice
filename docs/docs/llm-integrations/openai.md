# OpenAI Integration

Complete guide for integrating OpenAI's GPT models with Spice Framework 1.0.0 via Spring AI 1.1.0.

## Prerequisites

- Spice Framework 1.0.0-alpha-2 or later
- OpenAI API key ([Get one here](https://platform.openai.com/api-keys))
- Spring Boot 3.5.7+

## Installation

Add the dependency to your project:

```kotlin
dependencies {
    implementation("io.github.noailabs:spice-springboot-ai:1.0.0-alpha-2")
}
```

## Configuration

### Basic Setup

Configure OpenAI in `application.yml`:

```yaml
spice:
  spring-ai:
    enabled: true
    default-provider: openai

    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY}  # Set via environment variable
      model: gpt-4
      temperature: 0.7
      max-tokens: 2048
```

### Advanced Configuration

```yaml
spice:
  spring-ai:
    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY}
      base-url: https://api.openai.com  # Optional: custom endpoint
      model: gpt-4

      # Chat options
      temperature: 0.7      # 0.0 - 2.0 (creativity)
      max-tokens: 4096      # Maximum response length
      top-p: 1.0           # Nucleus sampling
      frequency-penalty: 0.0  # -2.0 to 2.0
      presence-penalty: 0.0   # -2.0 to 2.0
```

## Usage

### Method 1: SpringAIAgentFactory (Recommended)

```kotlin
import io.github.noailabs.spice.springboot.ai.factory.SpringAIAgentFactory
import io.github.noailabs.spice.springboot.ai.factory.OpenAIConfig
import io.github.noailabs.spice.SpiceMessage
import org.springframework.stereotype.Service

@Service
class OpenAIService(
    private val factory: SpringAIAgentFactory
) {
    suspend fun chat(userMessage: String): String {
        // Create agent with default config
        val agent = factory.openai("gpt-4")

        // Process message
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
                topP = 0.95,
                systemPrompt = "You are a helpful assistant specialized in Kotlin."
            )
        )

        val response = agent.processMessage(
            SpiceMessage.create(userMessage, "user")
        )

        return response.getOrThrow().content
    }
}
```

### Method 2: Auto-configured Default Agent

```kotlin
import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.SpiceMessage
import org.springframework.stereotype.Service

@Service
class SimpleService(
    private val agent: Agent  // Auto-wired from default-provider config
) {
    suspend fun ask(question: String): String {
        val response = agent.processMessage(
            SpiceMessage.create(question, "user")
        )
        return response.getOrThrow().content
    }
}
```

### Method 3: Agent Registry

```kotlin
import io.github.noailabs.spice.springboot.ai.registry.AgentRegistry
import org.springframework.stereotype.Service

@Service
class RegistryService(
    private val registry: AgentRegistry
) {
    suspend fun askOpenAI(question: String): String {
        val agent = registry.get("openai")  // Get registered OpenAI agent
        val response = agent.processMessage(
            SpiceMessage.create(question, "user")
        )
        return response.getOrThrow().content
    }
}
```

## Available Models

### GPT-4 Family (Recommended)

- **`gpt-4`** - Latest GPT-4 model (most capable)
- **`gpt-4-turbo`** - Faster, cheaper GPT-4 variant
- **`gpt-4-32k`** - Extended 32K context window

### GPT-3.5 Family

- **`gpt-3.5-turbo`** - Fast and cost-effective
- **`gpt-3.5-turbo-16k`** - Extended context window

## OpenAIConfig Options

```kotlin
import io.github.noailabs.spice.springboot.ai.factory.OpenAIConfig

val config = OpenAIConfig(
    agentId = "my-gpt-agent",           // Custom agent ID
    agentName = "GPT-4 Assistant",       // Human-readable name
    agentDescription = "AI assistant",   // Description

    apiKey = "sk-...",                   // Override API key
    baseUrl = "https://api.openai.com",  // Custom endpoint
    organizationId = "org-...",          // OpenAI organization

    temperature = 0.7,                   // Creativity (0.0-2.0)
    maxTokens = 2048,                    // Max response length
    topP = 1.0,                          // Nucleus sampling
    frequencyPenalty = 0.0,              // Reduce repetition (-2.0 to 2.0)
    presencePenalty = 0.0,               // Encourage new topics (-2.0 to 2.0)

    systemPrompt = "You are a helpful AI assistant."
)

val agent = factory.openai("gpt-4", config)
```

## Complete Examples

### Example 1: Simple Chatbot

```kotlin
import io.github.noailabs.spice.springboot.ai.factory.SpringAIAgentFactory
import io.github.noailabs.spice.SpiceMessage
import org.springframework.stereotype.Service

@Service
class ChatbotService(
    private val factory: SpringAIAgentFactory
) {
    private val agent = factory.openai("gpt-4")

    suspend fun chat(message: String): String {
        val response = agent.processMessage(
            SpiceMessage.create(message, "user")
        )
        return response.getOrThrow().content
    }
}
```

### Example 2: Code Review Assistant

```kotlin
import io.github.noailabs.spice.springboot.ai.factory.SpringAIAgentFactory
import io.github.noailabs.spice.springboot.ai.factory.OpenAIConfig
import io.github.noailabs.spice.SpiceMessage
import org.springframework.stereotype.Service

@Service
class CodeReviewService(
    private val factory: SpringAIAgentFactory
) {
    private val reviewer = factory.openai(
        model = "gpt-4",
        config = OpenAIConfig(
            agentId = "code-reviewer",
            systemPrompt = """
                You are an expert code reviewer specializing in Kotlin.
                Analyze code for:
                - Correctness and bugs
                - Performance issues
                - Best practices
                - Security vulnerabilities
                Provide specific, actionable feedback.
            """.trimIndent(),
            temperature = 0.3  // Lower temperature for more focused responses
        )
    )

    suspend fun reviewCode(code: String): String {
        val response = reviewer.processMessage(
            SpiceMessage.create("Review this code:\n\n$code", "developer")
        )
        return response.getOrThrow().content
    }
}
```

### Example 3: Multi-Temperature Chat

```kotlin
@Service
class CreativeService(
    private val factory: SpringAIAgentFactory
) {
    suspend fun generateCreativeResponse(prompt: String): String {
        val agent = factory.openai(
            model = "gpt-4",
            config = OpenAIConfig(temperature = 1.5)  // High creativity
        )
        val response = agent.processMessage(
            SpiceMessage.create(prompt, "user")
        )
        return response.getOrThrow().content
    }

    suspend fun generateFactualResponse(prompt: String): String {
        val agent = factory.openai(
            model = "gpt-4",
            config = OpenAIConfig(temperature = 0.2)  // Low creativity
        )
        val response = agent.processMessage(
            SpiceMessage.create(prompt, "user")
        )
        return response.getOrThrow().content
    }
}
```

### Example 4: With Metadata

```kotlin
@Service
class MetadataService(
    private val factory: SpringAIAgentFactory
) {
    private val agent = factory.openai("gpt-4")

    suspend fun chatWithContext(
        message: String,
        userId: String,
        sessionId: String
    ): String {
        val response = agent.processMessage(
            SpiceMessage.create(message, "user")
                .withMetadata(mapOf(
                    "userId" to userId,
                    "sessionId" to sessionId,
                    "timestamp" to System.currentTimeMillis()
                ))
        )
        return response.getOrThrow().content
    }
}
```

## Error Handling

```kotlin
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult

@Service
class RobustService(
    private val factory: SpringAIAgentFactory
) {
    private val agent = factory.openai("gpt-4")

    suspend fun safeChat(message: String): String {
        return try {
            val response = agent.processMessage(
                SpiceMessage.create(message, "user")
            )

            when (response) {
                is SpiceResult.Success -> response.value.content
                is SpiceResult.Failure -> {
                    logger.error("Chat failed: ${response.error.message}")
                    "Sorry, I encountered an error. Please try again."
                }
            }
        } catch (e: Exception) {
            logger.error("Unexpected error", e)
            "An unexpected error occurred."
        }
    }
}
```

## Best Practices

### 1. Reuse Agents

```kotlin
@Service
class EfficientService(
    private val factory: SpringAIAgentFactory
) {
    // ✅ Create agent once and reuse
    private val agent = factory.openai("gpt-4")

    suspend fun chat(message: String) =
        agent.processMessage(SpiceMessage.create(message, "user"))
}
```

### 2. Use Appropriate Temperature

- **0.0 - 0.3**: Factual, deterministic (code, math, analysis)
- **0.4 - 0.7**: Balanced (general chat, Q&A)
- **0.8 - 1.5**: Creative (writing, brainstorming)
- **1.6 - 2.0**: Highly creative (experimental)

### 3. Set System Prompts

```kotlin
val agent = factory.openai(
    model = "gpt-4",
    config = OpenAIConfig(
        systemPrompt = """
            You are a Kotlin expert.
            Always provide working, production-ready code.
            Include error handling and tests.
        """.trimIndent()
    )
)
```

### 4. Limit Token Usage

```kotlin
val agent = factory.openai(
    model = "gpt-4",
    config = OpenAIConfig(
        maxTokens = 500  // Limit response length
    )
)
```

## Testing

### With TestContainers (Mock)

```kotlin
@SpringBootTest
class OpenAIServiceTest {
    @Autowired
    lateinit var factory: SpringAIAgentFactory

    @Test
    fun `should process message`() = runBlocking {
        val agent = factory.openai("gpt-4")

        val response = agent.processMessage(
            SpiceMessage.create("Hello", "user")
        )

        assertTrue(response.isSuccess)
        assertNotNull(response.getOrThrow().content)
    }
}
```

## Migration from 0.x

### Before (0.x)
```kotlin
val agent = gptAgent(
    apiKey = System.getenv("OPENAI_API_KEY"),
    model = "gpt-4"
)

val response = agent.processComm(
    Comm(content = "Hello", from = "user")
)
```

### After (1.0.0)
```kotlin
@Service
class MyService(
    private val factory: SpringAIAgentFactory
) {
    suspend fun chat(message: String): String {
        val agent = factory.openai("gpt-4")
        val response = agent.processMessage(
            SpiceMessage.create(message, "user")
        )
        return response.getOrThrow().content
    }
}
```

## Troubleshooting

### API Key Not Found

```yaml
# Set environment variable
export OPENAI_API_KEY=sk-...

# Or set in application.yml (not recommended for production)
spice:
  spring-ai:
    openai:
      api-key: sk-...
```

### Rate Limits

OpenAI enforces rate limits. Implement retry logic:

```kotlin
import kotlinx.coroutines.delay

suspend fun chatWithRetry(message: String, maxRetries: Int = 3): String {
    repeat(maxRetries) { attempt ->
        try {
            return agent.processMessage(
                SpiceMessage.create(message, "user")
            ).getOrThrow().content
        } catch (e: Exception) {
            if (attempt == maxRetries - 1) throw e
            delay(1000L * (attempt + 1))  // Exponential backoff
        }
    }
    error("Unreachable")
}
```

## Next Steps

- [Anthropic Integration](./anthropic) - Claude 3.5 Sonnet
- [Multi-Agent Workflows](../orchestration/multi-agent) - Orchestrate multiple agents
- [Tool Calling](../tools-extensions/creating-tools) - Function calling with GPT
