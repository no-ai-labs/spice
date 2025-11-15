package io.github.noailabs.spice.springboot.ai.factory

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.error.SpiceResult

/**
 * 🏭 Spring AI Agent Factory
 *
 * Creates Spice agents from Spring AI ChatModels with multiple API styles:
 * 1. Type-safe provider-specific methods (openai, anthropic, ollama)
 * 2. Generic dynamic creation (create)
 * 3. Default agent from configuration (default)
 *
 * **Usage Examples:**
 *
 * **1. Type-safe provider methods:**
 * ```kotlin
 * @Service
 * class MyService(private val factory: SpringAIAgentFactory) {
 *     suspend fun chat(message: String): String {
 *         val agent = factory.openai("gpt-4", OpenAIConfig(temperature = 0.7))
 *         val response = agent.processMessage(SpiceMessage.create(message, "user"))
 *         return response.getOrThrow().content
 *     }
 * }
 * ```
 *
 * **2. Dynamic provider selection:**
 * ```kotlin
 * val agent = factory.create(
 *     provider = "openai",
 *     model = "gpt-4",
 *     config = mapOf("temperature" to 0.7, "maxTokens" to 2000)
 * )
 * ```
 *
 * **3. Auto-wired default:**
 * ```kotlin
 * @Service
 * class SimpleService(private val factory: SpringAIAgentFactory) {
 *     suspend fun chat(message: String) {
 *         val agent = factory.default()  // Uses application.yml default-provider
 *         agent.processMessage(SpiceMessage.create(message, "user"))
 *     }
 * }
 * ```
 *
 * @since 1.0.0
 * @author Spice Framework
 */
interface SpringAIAgentFactory {

    // ===== Type-Safe Provider Methods =====

    /**
     * Create OpenAI agent
     *
     * @param model Model name (e.g., "gpt-4", "gpt-3.5-turbo")
     * @param config OpenAI-specific configuration
     * @return Agent wrapping OpenAI ChatModel
     */
    fun openai(model: String, config: OpenAIConfig = OpenAIConfig()): Agent

    /**
     * Create Anthropic (Claude) agent
     *
     * @param model Model name (e.g., "claude-3-5-sonnet-20241022")
     * @param config Anthropic-specific configuration
     * @return Agent wrapping Anthropic ChatModel
     */
    fun anthropic(model: String, config: AnthropicConfig = AnthropicConfig()): Agent

    /**
     * Create Ollama (local LLM) agent
     *
     * @param model Model name (e.g., "llama3", "mistral")
     * @param config Ollama-specific configuration
     * @return Agent wrapping Ollama ChatModel
     */
    fun ollama(model: String, config: OllamaConfig = OllamaConfig()): Agent

    /**
     * Create Azure OpenAI agent
     *
     * @param deploymentName Azure deployment name
     * @param config Azure OpenAI configuration
     * @return Agent wrapping Azure OpenAI ChatModel
     */
    fun azureOpenai(deploymentName: String, config: AzureOpenAIConfig = AzureOpenAIConfig()): Agent

    /**
     * Create Google Vertex AI agent
     *
     * @param model Model name (e.g., "gemini-pro")
     * @param config Vertex AI configuration
     * @return Agent wrapping Vertex AI ChatModel
     */
    fun vertexAi(model: String, config: VertexAIConfig = VertexAIConfig()): Agent

    /**
     * Create AWS Bedrock agent
     *
     * @param model Model name (e.g., "anthropic.claude-3-sonnet-20240229-v1:0")
     * @param config Bedrock configuration
     * @return Agent wrapping Bedrock ChatModel
     */
    fun bedrock(model: String, config: BedrockConfig = BedrockConfig()): Agent

    // ===== Generic Dynamic Creation =====

    /**
     * Create agent dynamically by provider name
     *
     * @param provider Provider name ("openai", "anthropic", "ollama", etc.)
     * @param model Model name
     * @param config Configuration map (provider-specific)
     * @return SpiceResult with agent or error
     */
    fun create(
        provider: String,
        model: String,
        config: Map<String, Any> = emptyMap()
    ): SpiceResult<Agent>

    // ===== Default Agent =====

    /**
     * Get default agent from configuration
     * Uses `spice.spring-ai.default-provider` from application.yml
     *
     * @return Default agent
     * @throws IllegalStateException if no default provider configured
     */
    fun default(): Agent

    // ===== Factory Query Methods =====

    /**
     * Check if a provider is available
     *
     * @param provider Provider name
     * @return True if provider is configured and available
     */
    fun isProviderAvailable(provider: String): Boolean

    /**
     * Get list of available providers
     *
     * @return List of provider names
     */
    fun getAvailableProviders(): List<String>
}

// ===== Provider-Specific Configuration Classes =====

/**
 * OpenAI Configuration
 */
data class OpenAIConfig(
    val agentId: String? = null,
    val agentName: String? = null,
    val agentDescription: String? = null,
    val apiKey: String? = null,
    val baseUrl: String? = null,
    val organizationId: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val topP: Double? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val systemPrompt: String? = null
)

/**
 * Anthropic Configuration
 */
data class AnthropicConfig(
    val agentId: String? = null,
    val agentName: String? = null,
    val agentDescription: String? = null,
    val apiKey: String? = null,
    val baseUrl: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    val systemPrompt: String? = null
)

/**
 * Ollama Configuration
 */
data class OllamaConfig(
    val agentId: String? = null,
    val agentName: String? = null,
    val agentDescription: String? = null,
    val baseUrl: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    val numGpu: Int? = null,
    val numThread: Int? = null,
    val systemPrompt: String? = null
)

/**
 * Azure OpenAI Configuration
 */
data class AzureOpenAIConfig(
    val agentId: String? = null,
    val agentName: String? = null,
    val agentDescription: String? = null,
    val apiKey: String? = null,
    val endpoint: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val systemPrompt: String? = null
)

/**
 * Vertex AI Configuration
 */
data class VertexAIConfig(
    val agentId: String? = null,
    val agentName: String? = null,
    val agentDescription: String? = null,
    val projectId: String? = null,
    val location: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val systemPrompt: String? = null
)

/**
 * Bedrock Configuration
 */
data class BedrockConfig(
    val agentId: String? = null,
    val agentName: String? = null,
    val agentDescription: String? = null,
    val region: String? = null,
    val accessKeyId: String? = null,
    val secretAccessKey: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val systemPrompt: String? = null
)
