# Anthropic Claude Integration

Claude 3.5 Sonnet and Claude 3 Opus integration with Spice Framework.

## Creating a Claude Agent

### Basic Setup

```kotlin
import io.github.noailabs.spice.agents.claudeAgent

val agent = claudeAgent(
    id = "claude",
    apiKey = System.getenv("ANTHROPIC_API_KEY"),
    model = "claude-3-sonnet-20240229",
    systemPrompt = "You are Claude, a helpful AI assistant.",
    debugEnabled = false
)
```

### Available Models

- `claude-3-sonnet-20240229` - Claude 3 Sonnet (default)
- `claude-3-5-sonnet-20241022` - Claude 3.5 Sonnet (latest)
- `claude-3-opus-20240229` - Claude 3 Opus (most capable)
- `claude-3-haiku-20240307` - Claude 3 Haiku (fastest)

## Configuration Options

```kotlin
val agent = claudeAgent(
    id = "my-claude-agent",                        // Agent identifier
    apiKey = System.getenv("ANTHROPIC_API_KEY"),  // Anthropic API key (required)
    model = "claude-3-sonnet-20240229",           // Model to use
    systemPrompt = "Custom system prompt",         // System instructions
    debugEnabled = true                            // Enable debug logging
)
```

## Using the Agent

```kotlin
import io.github.noailabs.spice.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val claude = claudeAgent(
        apiKey = System.getenv("ANTHROPIC_API_KEY"),
        model = "claude-3-5-sonnet-20241022"
    )

    val response = claude.processComm(
        Comm(
            content = "Write a Kotlin function to calculate fibonacci numbers",
            from = "user"
        )
    )

    println(response.content)
}
```

## Mock Agent for Testing

Use a mock Claude agent during development without making API calls:

```kotlin
import io.github.noailabs.spice.agents.mockClaudeAgent

val mockClaude = mockClaudeAgent(
    id = "mock-claude",
    personality = "helpful",  // Options: helpful, concise, verbose
    debugEnabled = true
)

// Use exactly like a real agent
val response = mockClaude.processComm(
    Comm(content = "Hello Claude!", from = "user")
)
```

### Mock Personality Types

- **`helpful`** (default) - Balanced, helpful responses
- **`concise`** - Brief, to-the-point responses
- **`verbose`** - Detailed, comprehensive responses

## Advanced: DSL Builder

For more complex configurations:

```kotlin
import io.github.noailabs.spice.agents.claude

val agent = claude {
    id = "custom-claude"
    apiKey = System.getenv("ANTHROPIC_API_KEY")
    model = "claude-3-5-sonnet-20241022"
    systemPrompt = "You are a technical writer specializing in API documentation."
    debugEnabled = true
    maxTokens = 4096
    temperature = 0.7
}
```

## From SpiceConfig

Create agents from global configuration:

```kotlin
import io.github.noailabs.spice.agents.claudeAgentFromConfig
import io.github.noailabs.spice.SpiceConfig

SpiceConfig.initialize {
    providers {
        anthropic {
            apiKey = System.getenv("ANTHROPIC_API_KEY")
            model = "claude-3-sonnet-20240229"
            systemPrompt = "Default system prompt"
        }
    }
}

// Create agent from config
val agent = claudeAgentFromConfig(
    id = "claude-from-config"
)

// Or with overrides
val customAgent = claudeAgentFromConfig(id = "custom") {
    model = "claude-3-5-sonnet-20241022"  // Override model
}
```

## Comparing with GPT

```kotlin
// Create both agents
val gpt = gptAgent(
    apiKey = System.getenv("OPENAI_API_KEY"),
    model = "gpt-4"
)

val claude = claudeAgent(
    apiKey = System.getenv("ANTHROPIC_API_KEY"),
    model = "claude-3-5-sonnet-20241022"
)

// Ask the same question to both
val question = Comm(content = "What is functional programming?", from = "user")

val gptResponse = gpt.processComm(question)
val claudeResponse = claude.processComm(question)

println("GPT: ${gptResponse.content}")
println("Claude: ${claudeResponse.content}")
```

## Error Handling

```kotlin
val response = try {
    claude.processComm(comm)
} catch (e: Exception) {
    println("Error: ${e.message}")
    Comm(
        content = "Sorry, I encountered an error",
        from = claude.id,
        type = CommType.ERROR
    )
}
```

## API Headers

Claude requires specific headers which are handled automatically:

```kotlin
// Automatically included:
// - x-api-key: Your API key
// - anthropic-version: 2023-06-01
// - content-type: application/json
```

## Next Steps

- [OpenAI GPT Integration](./openai)
- [Multi-Agent Systems](../orchestration/multi-agent)
- [Building Agents](../dsl-guide/build-agent)
