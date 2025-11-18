package io.github.noailabs.spice.springboot.ai.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * ⚙️ Spring AI Configuration Properties for Spice Framework
 *
 * Type-safe configuration for Spring AI integration.
 * Supports multiple providers (OpenAI, Anthropic, Ollama, Azure, Vertex, Bedrock).
 *
 * **Usage:**
 * ```yaml
 * spice:
 *   spring-ai:
 *     enabled: true
 *     default-provider: openai
 *     openai:
 *       enabled: true
 *       api-key: ${OPENAI_API_KEY}
 *       model: gpt-4
 *       temperature: 0.7
 *     anthropic:
 *       enabled: true
 *       api-key: ${ANTHROPIC_API_KEY}
 *       model: claude-3-5-sonnet-20241022
 * ```
 *
 * @since 1.0.0
 * @author Spice Framework
 */
@ConfigurationProperties("spice.spring-ai")
data class SpringAIProperties(
    /**
     * Enable/disable Spring AI integration
     * Default: true
     */
    val enabled: Boolean = true,

    /**
     * Default provider to use when multiple are configured
     * Options: "openai", "anthropic", "ollama", "azure-openai", "vertex-ai", "bedrock"
     * Default: null (first enabled provider)
     */
    val defaultProvider: String? = null,

    /**
     * OpenAI provider configuration
     */
    val openai: OpenAIConfig = OpenAIConfig(),

    /**
     * Anthropic (Claude) provider configuration
     */
    val anthropic: AnthropicConfig = AnthropicConfig(),

    /**
     * Ollama (local LLM) provider configuration
     */
    val ollama: OllamaConfig = OllamaConfig(),

    /**
     * Azure OpenAI provider configuration
     */
    val azureOpenai: AzureOpenAIConfig = AzureOpenAIConfig(),

    /**
     * Google Vertex AI provider configuration
     */
    val vertexAi: VertexAIConfig = VertexAIConfig(),

    /**
     * AWS Bedrock provider configuration
     */
    val bedrock: BedrockConfig = BedrockConfig(),

    /**
     * Chat-specific configuration (applies to all providers)
     */
    val chat: ChatConfig = ChatConfig(),

    /**
     * Streaming configuration
     */
    val streaming: StreamingConfig = StreamingConfig(),

    /**
     * Agent registry configuration
     */
    val registry: RegistryConfig = RegistryConfig()
) {

    /**
     * OpenAI Configuration
     */
    data class OpenAIConfig(
        /**
         * Enable OpenAI provider
         */
        val enabled: Boolean = false,

        /**
         * OpenAI API key
         * Can be set via environment variable: ${OPENAI_API_KEY}
         */
        val apiKey: String? = null,

        /**
         * Model name
         * Examples: gpt-4, gpt-3.5-turbo, gpt-4-turbo-preview
         */
        val model: String = "gpt-4",

        /**
         * Base URL (for custom OpenAI-compatible endpoints)
         * Default: https://api.openai.com
         */
        val baseUrl: String? = null,

        /**
         * Organization ID (optional)
         */
        val organizationId: String? = null,

        /**
         * Temperature (0.0 - 2.0)
         * Lower = more deterministic, Higher = more creative
         * Default: null (uses provider default)
         */
        val temperature: Double? = null,

        /**
         * Maximum tokens to generate
         * Default: null (uses provider default)
         */
        val maxTokens: Int? = null,

        /**
         * Top P sampling parameter (0.0 - 1.0)
         * Default: null (uses provider default)
         */
        val topP: Double? = null,

        /**
         * Frequency penalty (reduce repetition)
         * Range: -2.0 to 2.0
         * Default: null (uses provider default)
         */
        val frequencyPenalty: Double? = null,

        /**
         * Presence penalty (encourage new topics)
         * Range: -2.0 to 2.0
         * Default: null (uses provider default)
         */
        val presencePenalty: Double? = null
    )

    /**
     * Anthropic (Claude) Configuration
     */
    data class AnthropicConfig(
        val enabled: Boolean = false,
        val apiKey: String? = null,
        val model: String = "claude-3-5-sonnet-20241022",
        val baseUrl: String? = null,
        val temperature: Double? = null,
        val maxTokens: Int? = null,
        val topP: Double? = null,
        val topK: Int? = null
    )

    /**
     * Ollama (Local LLM) Configuration
     */
    data class OllamaConfig(
        val enabled: Boolean = false,
        val baseUrl: String = "http://localhost:11434",
        val model: String = "llama3",
        val temperature: Double? = null,
        val maxTokens: Int? = null,
        val topP: Double? = null,
        val topK: Int? = null,

        /**
         * GPU layers (for model optimization)
         * Default: null (auto-detect)
         */
        val numGpu: Int? = null,

        /**
         * Thread count
         * Default: null (auto-detect)
         */
        val numThread: Int? = null
    )

    /**
     * Azure OpenAI Configuration
     */
    data class AzureOpenAIConfig(
        val enabled: Boolean = false,
        val apiKey: String? = null,
        val endpoint: String? = null,
        val deploymentName: String? = null,
        val temperature: Double? = null,
        val maxTokens: Int? = null
    )

    /**
     * Google Vertex AI Configuration
     */
    data class VertexAIConfig(
        val enabled: Boolean = false,
        val projectId: String? = null,
        val location: String = "us-central1",
        val model: String = "gemini-pro",
        val temperature: Double? = null,
        val maxTokens: Int? = null
    )

    /**
     * AWS Bedrock Configuration
     */
    data class BedrockConfig(
        val enabled: Boolean = false,
        val region: String = "us-east-1",
        val model: String = "anthropic.claude-3-sonnet-20240229-v1:0",
        val accessKeyId: String? = null,
        val secretAccessKey: String? = null,
        val temperature: Double? = null,
        val maxTokens: Int? = null
    )

    /**
     * Chat Configuration (applies to all providers)
     */
    data class ChatConfig(
        /**
         * Default temperature if not specified in provider config
         */
        val defaultTemperature: Double = 0.7,

        /**
         * Default max tokens if not specified in provider config
         */
        val defaultMaxTokens: Int? = null,

        /**
         * Default system prompt for all agents
         */
        val defaultSystemPrompt: String? = null,

        /**
         * Request timeout in milliseconds
         * Default: 60000 (60 seconds)
         */
        val timeoutMs: Long = 60000,

        /**
         * Retry configuration
         */
        val retry: RetryConfig = RetryConfig()
    )

    /**
     * Retry Configuration
     */
    data class RetryConfig(
        /**
         * Enable retries
         */
        val enabled: Boolean = true,

        /**
         * Maximum retry attempts
         */
        val maxAttempts: Int = 3,

        /**
         * Initial backoff delay in milliseconds
         */
        val initialBackoffMs: Long = 1000,

        /**
         * Maximum backoff delay in milliseconds
         */
        val maxBackoffMs: Long = 10000,

        /**
         * Backoff multiplier
         */
        val backoffMultiplier: Double = 2.0
    )

    /**
     * Streaming Configuration
     */
    data class StreamingConfig(
        /**
         * Enable streaming support
         */
        val enabled: Boolean = true,

        /**
         * Buffer size for streaming
         */
        val bufferSize: Int = 10,

        /**
         * Timeout for stream completion in milliseconds
         */
        val streamTimeoutMs: Long = 120000
    )

    /**
     * Agent Registry Configuration
     */
    data class RegistryConfig(
        /**
         * Enable agent registry auto-configuration
         */
        val enabled: Boolean = true,

        /**
         * Auto-register agents from enabled providers
         */
        val autoRegister: Boolean = true,

        /**
         * Thread-safe registry implementation
         */
        val threadSafe: Boolean = true
    )
}
