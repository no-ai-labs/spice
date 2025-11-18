# Anthropic Integration

Complete guide for integrating Anthropic's Claude models with Spice Framework 1.0.0 via Spring AI 1.1.0.

## Prerequisites

- Spice Framework 1.0.0-alpha-2 or later
- Anthropic API key ([Get one here](https://console.anthropic.com/))
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

Configure Anthropic in `application.yml`:

```yaml
spice:
  spring-ai:
    enabled: true
    default-provider: anthropic

    anthropic:
      enabled: true
      api-key: ${ANTHROPIC_API_KEY}  # Set via environment variable
      model: claude-3-5-sonnet-20241022
      temperature: 0.7
      max-tokens: 4096
```

### Advanced Configuration

```yaml
spice:
  spring-ai:
    anthropic:
      enabled: true
      api-key: ${ANTHROPIC_API_KEY}
      base-url: https://api.anthropic.com  # Optional: custom endpoint
      model: claude-3-5-sonnet-20241022

      # Chat options
      temperature: 0.7      # 0.0 - 1.0 (creativity)
      max-tokens: 4096      # Maximum response length (up to 200K for Claude 3)
      top-p: 1.0           # Nucleus sampling
      top-k: 40            # Top-K sampling
```

## Usage

### Method 1: SpringAIAgentFactory (Recommended)

```kotlin
import io.github.noailabs.spice.springboot.ai.factory.SpringAIAgentFactory
import io.github.noailabs.spice.springboot.ai.factory.AnthropicConfig
import io.github.noailabs.spice.SpiceMessage
import org.springframework.stereotype.Service

@Service
class AnthropicService(
    private val factory: SpringAIAgentFactory
) {
    suspend fun chat(userMessage: String): String {
        // Create agent with default config
        val agent = factory.anthropic("claude-3-5-sonnet-20241022")

        // Process message
        val response = agent.processMessage(
            SpiceMessage.create(userMessage, "user")
        )

        return response.getOrThrow().content
    }

    suspend fun chatWithCustomConfig(userMessage: String): String {
        // Create agent with custom configuration
        val agent = factory.anthropic(
            model = "claude-3-5-sonnet-20241022",
            config = AnthropicConfig(
                temperature = 0.9,
                maxTokens = 8192,
                topP = 0.95,
                topK = 50,
                systemPrompt = "You are Claude, a helpful AI assistant specialized in Kotlin development."
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
    suspend fun askClaude(question: String): String {
        val agent = registry.get("anthropic")  // Get registered Anthropic agent
        val response = agent.processMessage(
            SpiceMessage.create(question, "user")
        )
        return response.getOrThrow().content
    }
}
```

## Available Models

### Claude 3.5 Family (Recommended)

- **`claude-3-5-sonnet-20241022`** - Latest, most capable Claude 3.5 Sonnet
  - 200K context window
  - Best for complex tasks, coding, analysis
  - Most balanced cost/performance

### Claude 3 Family

- **`claude-3-opus-20240229`** - Most capable Claude 3 model
  - 200K context window
  - Best for highly complex tasks
  - Highest cost

- **`claude-3-sonnet-20240229`** - Balanced Claude 3 model
  - 200K context window
  - Good balance of capability and speed

- **`claude-3-haiku-20240307`** - Fastest Claude 3 model
  - 200K context window
  - Best for simple tasks, low latency
  - Lowest cost

## AnthropicConfig Options

```kotlin
import io.github.noailabs.spice.springboot.ai.factory.AnthropicConfig

val config = AnthropicConfig(
    agentId = "my-claude-agent",         // Custom agent ID
    agentName = "Claude Assistant",       // Human-readable name
    agentDescription = "AI assistant",    // Description

    apiKey = "sk-ant-...",               // Override API key
    baseUrl = "https://api.anthropic.com", // Custom endpoint

    temperature = 0.7,                   // Creativity (0.0-1.0)
    maxTokens = 4096,                    // Max response length
    topP = 1.0,                          // Nucleus sampling
    topK = 40,                           // Top-K sampling

    systemPrompt = "You are Claude, a helpful AI assistant."
)

val agent = factory.anthropic("claude-3-5-sonnet-20241022", config)
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
    private val agent = factory.anthropic("claude-3-5-sonnet-20241022")

    suspend fun chat(message: String): String {
        val response = agent.processMessage(
            SpiceMessage.create(message, "user")
        )
        return response.getOrThrow().content
    }
}
```

### Example 2: Long-Context Document Analysis

Claude 3.5's 200K token context window enables analyzing large documents:

```kotlin
import io.github.noailabs.spice.springboot.ai.factory.SpringAIAgentFactory
import io.github.noailabs.spice.springboot.ai.factory.AnthropicConfig
import io.github.noailabs.spice.SpiceMessage
import org.springframework.stereotype.Service

@Service
class DocumentAnalysisService(
    private val factory: SpringAIAgentFactory
) {
    private val analyzer = factory.anthropic(
        model = "claude-3-5-sonnet-20241022",
        config = AnthropicConfig(
            agentId = "doc-analyzer",
            systemPrompt = """
                You are an expert document analyzer.
                Analyze documents for:
                - Key themes and insights
                - Important facts and figures
                - Actionable recommendations
                - Potential issues or concerns
            """.trimIndent(),
            maxTokens = 8192,  // Allow longer responses
            temperature = 0.3   // More focused analysis
        )
    )

    suspend fun analyzeDocument(document: String, question: String): String {
        val prompt = """
            Document:
            $document

            Question: $question
        """.trimIndent()

        val response = analyzer.processMessage(
            SpiceMessage.create(prompt, "analyst")
        )
        return response.getOrThrow().content
    }
}
```

### Example 3: Code Generation with Claude

```kotlin
@Service
class CodeGeneratorService(
    private val factory: SpringAIAgentFactory
) {
    private val coder = factory.anthropic(
        model = "claude-3-5-sonnet-20241022",
        config = AnthropicConfig(
            agentId = "kotlin-coder",
            systemPrompt = """
                You are an expert Kotlin developer.
                Generate production-ready Kotlin code that:
                - Follows Kotlin best practices and idioms
                - Includes comprehensive error handling
                - Uses coroutines appropriately
                - Has clear documentation
                - Is type-safe and null-safe
            """.trimIndent(),
            temperature = 0.2  // Lower for more consistent code
        )
    )

    suspend fun generateCode(specification: String): String {
        val response = coder.processMessage(
            SpiceMessage.create(
                "Generate Kotlin code for: $specification",
                "developer"
            )
        )
        return response.getOrThrow().content
    }
}
```

### Example 4: Multi-Model Comparison

Compare responses from different Claude models:

```kotlin
@Service
class ModelComparisonService(
    private val factory: SpringAIAgentFactory
) {
    suspend fun compareModels(question: String): Map<String, String> {
        val models = listOf(
            "claude-3-5-sonnet-20241022",
            "claude-3-opus-20240229",
            "claude-3-haiku-20240307"
        )

        return models.associate { model ->
            val agent = factory.anthropic(model)
            val response = agent.processMessage(
                SpiceMessage.create(question, "user")
            )
            model to response.getOrThrow().content
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
    private val agent = factory.anthropic("claude-3-5-sonnet-20241022")

    suspend fun chat(message: String) =
        agent.processMessage(SpiceMessage.create(message, "user"))
}
```

### 2. Choose the Right Model

- **Claude 3.5 Sonnet**: Default choice for most tasks (best balance)
- **Claude 3 Opus**: Complex reasoning, research, analysis
- **Claude 3 Haiku**: Simple tasks, high throughput, low latency

### 3. Leverage Long Context

Claude 3 models support 200K tokens (~500 pages):

```kotlin
val agent = factory.anthropic(
    model = "claude-3-5-sonnet-20241022",
    config = AnthropicConfig(
        maxTokens = 8192  // Allow longer responses for complex tasks
    )
)
```

### 4. Use System Prompts Effectively

```kotlin
val agent = factory.anthropic(
    model = "claude-3-5-sonnet-20241022",
    config = AnthropicConfig(
        systemPrompt = """
            You are Claude, an AI assistant created by Anthropic.
            You are knowledgeable, helpful, and honest.

            Guidelines:
            - Be concise but thorough
            - Cite sources when possible
            - Admit uncertainty rather than guess
            - Prioritize safety and ethical considerations
        """.trimIndent()
    )
)
```

### 5. Temperature Guidelines for Claude

- **0.0 - 0.3**: Factual, deterministic (analysis, code, math)
- **0.4 - 0.7**: Balanced (general chat, Q&A, explanations)
- **0.8 - 1.0**: Creative (writing, brainstorming, exploration)

## Migration from 0.x

### Before (0.x)
```kotlin
val agent = claudeAgent(
    apiKey = System.getenv("ANTHROPIC_API_KEY"),
    model = "claude-3-sonnet-20240229"
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
        val agent = factory.anthropic("claude-3-5-sonnet-20241022")
        val response = agent.processMessage(
            SpiceMessage.create(message, "user")
        )
        return response.getOrThrow().content
    }
}
```

## Performance Comparison

| Model | Context | Speed | Cost | Best For |
|-------|---------|-------|------|----------|
| **Claude 3.5 Sonnet** | 200K | Fast | Medium | Most tasks (recommended) |
| **Claude 3 Opus** | 200K | Slower | High | Complex reasoning |
| **Claude 3 Haiku** | 200K | Fastest | Low | Simple tasks, high volume |

## Next Steps

- [OpenAI Integration](./openai) - GPT-4 setup
- [Multi-Agent Workflows](../orchestration/multi-agent) - Orchestrate multiple agents
- [Tool Calling](../tools-extensions/creating-tools) - Function calling with Claude
