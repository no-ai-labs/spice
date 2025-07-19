# Spice Framework Configuration System

The Spice Framework provides a centralized configuration system that works across different environments including standalone Kotlin, KMP, and Spring Boot.

## Quick Start

### Basic Usage

```kotlin
// Initialize global configuration
SpiceConfig.initialize {
    providers {
        openai {
            apiKey = "your-openai-api-key"
            model = "gpt-4"
            temperature = 0.7
        }
        
        anthropic {
            apiKey = "your-claude-api-key"
            model = "claude-3-sonnet-20240229"
        }
    }
    
    engine {
        maxAgents = 50
        enableHealthCheck = true
    }
    
    debug {
        enabled = true
        prefix = "[MY-APP]"
    }
}

// Access configuration
val config = SpiceConfig.current()
val openAIAgent = config.createOpenAIAgent()
```

### Spring Boot Usage

In Spring Boot, configuration is automatically loaded from `application.yml`:

```yaml
spice:
  enabled: true
  openai:
    api-key: ${OPENAI_API_KEY}
    model: gpt-4
    temperature: 0.7
  anthropic:
    api-key: ${ANTHROPIC_API_KEY}
    model: claude-3-sonnet-20240229
  engine:
    max-agents: 50
    enable-health-check: true
  debug:
    enabled: true
    prefix: "[SPICE]"
```

The `SpiceAutoConfiguration` class automatically creates agents based on your configuration.

### Creating Agents from Configuration

```kotlin
// Using extension functions
val openAIAgent = SpiceConfig.current().createOpenAIAgent()
val claudeAgent = SpiceConfig.current().createClaudeAgent()

// With configuration overrides
val customAgent = SpiceConfig.current().createOpenAIAgent("custom-agent") {
    model = "gpt-3.5-turbo"
    temperature = 0.9
}

// Create all configured agents
val allAgents = SpiceConfig.current().createAllAgents()
```

### Custom Providers

```kotlin
SpiceConfig.initialize {
    providers {
        custom("cohere") {
            property("apiKey", "your-cohere-key")
            property("model", "command-xlarge")
            property("baseUrl", "https://api.cohere.ai")
        }
    }
}

// Access custom provider
val cohereConfig = SpiceConfig.current()
    .providers.getTyped<CustomProviderConfig>("cohere")
val apiKey = cohereConfig?.getProperty<String>("apiKey")
```

### Vector Store Configuration

```kotlin
SpiceConfig.initialize {
    vectorStore {
        defaultProvider = "qdrant"
        
        qdrant = VectorStoreSettings.QdrantConfig(
            host = "localhost",
            port = 6333,
            apiKey = "optional-api-key"
        )
        
        pinecone = VectorStoreSettings.PineconeConfig(
            apiKey = "your-pinecone-key",
            environment = "us-east-1",
            indexName = "my-vectors"
        )
    }
}
```

## Configuration Classes

- **SpiceConfig**: Main configuration container
- **ProviderRegistry**: Manages all provider configurations
- **OpenAIConfig**: OpenAI-specific settings
- **AnthropicConfig**: Anthropic Claude settings
- **VertexConfig**: Google Vertex AI settings
- **VLLMConfig**: vLLM server settings
- **CustomProviderConfig**: Extensible configuration for any provider
- **EngineConfig**: Agent engine settings
- **DebugConfig**: Debug and logging settings
- **VectorStoreSettings**: Vector database configurations

## Best Practices

1. **Environment Variables**: Use environment variables for sensitive data like API keys
2. **Validation**: Provider configurations include validation methods
3. **Type Safety**: Use typed accessors like `getTyped<T>()` for compile-time safety
4. **Spring Boot**: Let auto-configuration handle setup in Spring Boot apps
5. **Testing**: Use mock agents for testing without API calls