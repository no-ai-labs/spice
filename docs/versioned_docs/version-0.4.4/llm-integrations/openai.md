# OpenAI Integration

GPT-4 and GPT-3.5-turbo integration with Spice Framework.

## Creating a GPT Agent

### Basic Setup

```kotlin
import io.github.noailabs.spice.agents.gptAgent

val agent = gptAgent(
    id = "gpt-4",
    apiKey = System.getenv("OPENAI_API_KEY"),
    model = "gpt-4",
    systemPrompt = "You are a helpful AI assistant.",
    debugEnabled = false
)
```

### Available Models

- `gpt-4` - Latest GPT-4 model (default)
- `gpt-4-turbo`
- `gpt-3.5-turbo`

## Configuration Options

```kotlin
val agent = gptAgent(
    id = "my-gpt-agent",                          // Agent identifier
    apiKey = System.getenv("OPENAI_API_KEY"),   // OpenAI API key (required)
    model = "gpt-4",                             // Model to use
    systemPrompt = "Custom system prompt",       // System instructions
    debugEnabled = true                          // Enable debug logging
)
```

## Using the Agent

```kotlin
import io.github.noailabs.spice.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val gpt = gptAgent(
        apiKey = System.getenv("OPENAI_API_KEY"),
        model = "gpt-4"
    )

    val response = gpt.processComm(
        Comm(
            content = "Explain quantum computing in simple terms",
            from = "user"
        )
    )

    println(response.content)
}
```

## Mock Agent for Testing

Use a mock GPT agent during development without making API calls:

```kotlin
import io.github.noailabs.spice.agents.mockGPTAgent

val mockGpt = mockGPTAgent(
    id = "mock-gpt",
    personality = "professional",  // Options: professional, technical, casual
    debugEnabled = true
)

// Use exactly like a real agent
val response = mockGpt.processComm(
    Comm(content = "Hello!", from = "user")
)
```

### Mock Personality Types

- **`professional`** (default) - Standard helpful responses
- **`technical`** - Technical analysis and implementation details
- **`casual`** - Friendly, conversational responses

## Advanced: DSL Builder

For more complex configurations:

```kotlin
import io.github.noailabs.spice.agents.gpt

val agent = gpt {
    id = "custom-gpt"
    apiKey = System.getenv("OPENAI_API_KEY")
    model = "gpt-4"
    systemPrompt = "You are a code review expert."
    debugEnabled = true
    maxTokens = 4096
    temperature = 0.7
}
```

## From SpiceConfig

Create agents from global configuration:

```kotlin
import io.github.noailabs.spice.agents.gptAgentFromConfig
import io.github.noailabs.spice.SpiceConfig

SpiceConfig.initialize {
    providers {
        openai {
            apiKey = System.getenv("OPENAI_API_KEY")
            model = "gpt-4"
            systemPrompt = "Default system prompt"
        }
    }
}

// Create agent from config
val agent = gptAgentFromConfig(
    id = "gpt-from-config"
)

// Or with overrides
val customAgent = gptAgentFromConfig(id = "custom") {
    model = "gpt-3.5-turbo"  // Override model
}
```

## Error Handling

```kotlin
val response = try {
    gpt.processComm(comm)
} catch (e: Exception) {
    println("Error: ${e.message}")
    Comm(
        content = "Sorry, I encountered an error",
        from = gpt.id,
        type = CommType.ERROR
    )
}
```

## Next Steps

- [Anthropic Claude Integration](./anthropic)
- [Multi-Agent Systems](../orchestration/multi-agent)
- [Building Agents](../dsl-guide/build-agent)
