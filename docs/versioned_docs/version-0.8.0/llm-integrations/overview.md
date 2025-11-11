# LLM Integrations

Spice Framework supports multiple LLM providers out of the box.

## Supported Providers

- **OpenAI** - GPT-4, GPT-3.5-turbo
- **Anthropic** - Claude 3.5 Sonnet, Claude 3 Opus

## Quick Start

```kotlin
// OpenAI
val gptAgent = gptAgent(
    id = "gpt-4",
    apiKey = System.getenv("OPENAI_API_KEY"),
    model = "gpt-4"
)

// Anthropic
val claudeAgent = claudeAgent(
    id = "claude",
    apiKey = System.getenv("ANTHROPIC_API_KEY"),
    model = "claude-3-sonnet-20240229"
)
```

## Configuration

### Via SpiceConfig

```kotlin
SpiceConfig.initialize {
    providers {
        openai {
            apiKey = System.getenv("OPENAI_API_KEY")
            model = "gpt-4"
            temperature = 0.7
        }

        anthropic {
            apiKey = System.getenv("ANTHROPIC_API_KEY")
            model = "claude-3-sonnet-20240229"
        }
    }
}
```

## Mock Agents for Testing

Both providers offer mock agents for development without API calls:

```kotlin
// Mock GPT agent
val mockGpt = mockGPTAgent(
    id = "mock-gpt",
    personality = "professional",
    debugEnabled = true
)

// Mock Claude agent
val mockClaude = mockClaudeAgent(
    id = "mock-claude",
    personality = "helpful",
    debugEnabled = true
)
```

## Next Steps

- [OpenAI Integration](./openai)
- [Anthropic Integration](./anthropic)
