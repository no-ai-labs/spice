package io.github.noailabs.spice.springboot.ai.factory

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.springboot.ai.adapters.ChatModelToAgentAdapter
import io.github.noailabs.spice.springboot.ai.config.SpringAIProperties
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.model.function.FunctionCallback
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.ai.anthropic.api.AnthropicApi
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.ai.ollama.api.OllamaApi
import org.springframework.ai.ollama.api.OllamaOptions

/**
 * 🏭 Default Spring AI Agent Factory Implementation
 *
 * Creates Spice agents from Spring AI ChatModels with support for multiple providers.
 *
 * **Supported Providers:**
 * - OpenAI (GPT-4, GPT-3.5, etc.)
 * - Anthropic (Claude 3.5, etc.)
 * - Ollama (Local LLMs: Llama, Mistral, etc.)
 * - Azure OpenAI (Future)
 * - Vertex AI (Future)
 * - Bedrock (Future)
 *
 * **Design:**
 * - Uses Spring AI's ChatModel abstraction
 * - Wraps ChatModel in ChatModelToAgentAdapter
 * - Supports dynamic provider selection
 * - Integrates with Spring Boot configuration
 *
 * @property properties Spring AI configuration properties
 * @property functionCallbacks Optional function callbacks for tool support
 *
 * @since 1.0.0
 * @author Spice Framework
 */
class DefaultSpringAIAgentFactory(
    private val properties: SpringAIProperties,
    private val functionCallbacks: List<FunctionCallback> = emptyList()
) : SpringAIAgentFactory {

    // ===== OpenAI =====

    override fun openai(model: String, config: OpenAIConfig): Agent {
        val apiKey = config.apiKey ?: properties.openai.apiKey
            ?: throw IllegalArgumentException("OpenAI API key not provided")

        val api = OpenAiApi(
            config.baseUrl ?: properties.openai.baseUrl ?: "https://api.openai.com",
            apiKey
        )

        val options = OpenAiChatOptions.builder()
            .withModel(model)
            .withTemperature(
                config.temperature
                    ?: properties.openai.temperature
                    ?: properties.chat.defaultTemperature
            )
            .apply {
                config.maxTokens?.let { withMaxTokens(it) }
                    ?: properties.openai.maxTokens?.let { withMaxTokens(it) }
                    ?: properties.chat.defaultMaxTokens?.let { withMaxTokens(it) }

                config.topP?.let { withTopP(it) }
                    ?: properties.openai.topP?.let { withTopP(it) }

                config.frequencyPenalty?.let { withFrequencyPenalty(it) }
                    ?: properties.openai.frequencyPenalty?.let { withFrequencyPenalty(it) }

                config.presencePenalty?.let { withPresencePenalty(it) }
                    ?: properties.openai.presencePenalty?.let { withPresencePenalty(it) }
            }
            .build()

        val chatModel = OpenAiChatModel(api, options)

        return ChatModelToAgentAdapter(
            chatModel = chatModel,
            id = config.agentId ?: "openai-$model",
            name = config.agentName ?: "OpenAI $model",
            description = config.agentDescription ?: "OpenAI agent using $model",
            systemPrompt = config.systemPrompt ?: properties.chat.defaultSystemPrompt,
            defaultOptions = options,
            functionCallbacks = functionCallbacks,
            capabilities = listOf("chat", "completion", "tools")
        )
    }

    // ===== Anthropic =====

    override fun anthropic(model: String, config: AnthropicConfig): Agent {
        val apiKey = config.apiKey ?: properties.anthropic.apiKey
            ?: throw IllegalArgumentException("Anthropic API key not provided")

        val api = AnthropicApi(apiKey)

        val options = AnthropicChatOptions.builder()
            .withModel(model)
            .withTemperature(
                config.temperature
                    ?: properties.anthropic.temperature
                    ?: properties.chat.defaultTemperature
            )
            .apply {
                config.maxTokens?.let { withMaxTokens(it) }
                    ?: properties.anthropic.maxTokens?.let { withMaxTokens(it) }
                    ?: properties.chat.defaultMaxTokens?.let { withMaxTokens(it) }

                config.topP?.let { withTopP(it) }
                    ?: properties.anthropic.topP?.let { withTopP(it) }

                config.topK?.let { withTopK(it) }
                    ?: properties.anthropic.topK?.let { withTopK(it) }
            }
            .build()

        val chatModel = AnthropicChatModel(api, options)

        return ChatModelToAgentAdapter(
            chatModel = chatModel,
            id = config.agentId ?: "anthropic-$model",
            name = config.agentName ?: "Anthropic $model",
            description = config.agentDescription ?: "Anthropic agent using $model",
            systemPrompt = config.systemPrompt ?: properties.chat.defaultSystemPrompt,
            defaultOptions = options,
            functionCallbacks = functionCallbacks,
            capabilities = listOf("chat", "completion", "long-context", "tools")
        )
    }

    // ===== Ollama =====

    override fun ollama(model: String, config: OllamaConfig): Agent {
        val baseUrl = config.baseUrl ?: properties.ollama.baseUrl

        val api = OllamaApi(baseUrl)

        val options = OllamaOptions.builder()
            .withModel(model)
            .withTemperature(
                config.temperature
                    ?: properties.ollama.temperature
                    ?: properties.chat.defaultTemperature
            )
            .apply {
                config.maxTokens?.let { withNumPredict(it) }
                    ?: properties.ollama.maxTokens?.let { withNumPredict(it) }
                    ?: properties.chat.defaultMaxTokens?.let { withNumPredict(it) }

                config.topP?.let { withTopP(it) }
                    ?: properties.ollama.topP?.let { withTopP(it) }

                config.topK?.let { withTopK(it) }
                    ?: properties.ollama.topK?.let { withTopK(it) }

                // Note: numGpu and numThread might not be available in all Spring AI versions
                // Remove or comment out if not supported
            }
            .build()

        val chatModel = OllamaChatModel(
            api,
            options,
            null, // functionCallbackContext
            emptyList(), // toolFunctionCallbacks
            null, // observationRegistry
            null // modelManagementOptions
        )

        return ChatModelToAgentAdapter(
            chatModel = chatModel,
            id = config.agentId ?: "ollama-$model",
            name = config.agentName ?: "Ollama $model",
            description = config.agentDescription ?: "Local LLM using $model via Ollama",
            systemPrompt = config.systemPrompt ?: properties.chat.defaultSystemPrompt,
            defaultOptions = options,
            functionCallbacks = functionCallbacks,
            capabilities = listOf("chat", "completion", "local")
        )
    }

    // ===== Azure OpenAI (Placeholder) =====

    override fun azureOpenai(deploymentName: String, config: AzureOpenAIConfig): Agent {
        // TODO: Implement Azure OpenAI support
        throw UnsupportedOperationException("Azure OpenAI support not yet implemented")
    }

    // ===== Vertex AI (Placeholder) =====

    override fun vertexAi(model: String, config: VertexAIConfig): Agent {
        // TODO: Implement Vertex AI support
        throw UnsupportedOperationException("Vertex AI support not yet implemented")
    }

    // ===== Bedrock (Placeholder) =====

    override fun bedrock(model: String, config: BedrockConfig): Agent {
        // TODO: Implement Bedrock support
        throw UnsupportedOperationException("Bedrock support not yet implemented")
    }

    // ===== Dynamic Creation =====

    override fun create(
        provider: String,
        model: String,
        config: Map<String, Any>
    ): SpiceResult<Agent> {
        return try {
            val agent = when (provider.lowercase()) {
                "openai" -> {
                    val openaiConfig = OpenAIConfig(
                        agentId = config["agentId"] as? String,
                        agentName = config["agentName"] as? String,
                        agentDescription = config["agentDescription"] as? String,
                        apiKey = config["apiKey"] as? String,
                        baseUrl = config["baseUrl"] as? String,
                        organizationId = config["organizationId"] as? String,
                        temperature = (config["temperature"] as? Number)?.toDouble(),
                        maxTokens = (config["maxTokens"] as? Number)?.toInt(),
                        topP = (config["topP"] as? Number)?.toDouble(),
                        frequencyPenalty = (config["frequencyPenalty"] as? Number)?.toDouble(),
                        presencePenalty = (config["presencePenalty"] as? Number)?.toDouble(),
                        systemPrompt = config["systemPrompt"] as? String
                    )
                    openai(model, openaiConfig)
                }
                "anthropic" -> {
                    val anthropicConfig = AnthropicConfig(
                        agentId = config["agentId"] as? String,
                        agentName = config["agentName"] as? String,
                        agentDescription = config["agentDescription"] as? String,
                        apiKey = config["apiKey"] as? String,
                        baseUrl = config["baseUrl"] as? String,
                        temperature = (config["temperature"] as? Number)?.toDouble(),
                        maxTokens = (config["maxTokens"] as? Number)?.toInt(),
                        topP = (config["topP"] as? Number)?.toDouble(),
                        topK = (config["topK"] as? Number)?.toInt(),
                        systemPrompt = config["systemPrompt"] as? String
                    )
                    anthropic(model, anthropicConfig)
                }
                "ollama" -> {
                    val ollamaConfig = OllamaConfig(
                        agentId = config["agentId"] as? String,
                        agentName = config["agentName"] as? String,
                        agentDescription = config["agentDescription"] as? String,
                        baseUrl = config["baseUrl"] as? String,
                        temperature = (config["temperature"] as? Number)?.toDouble(),
                        maxTokens = (config["maxTokens"] as? Number)?.toInt(),
                        topP = (config["topP"] as? Number)?.toDouble(),
                        topK = (config["topK"] as? Number)?.toInt(),
                        numGpu = (config["numGpu"] as? Number)?.toInt(),
                        numThread = (config["numThread"] as? Number)?.toInt(),
                        systemPrompt = config["systemPrompt"] as? String
                    )
                    ollama(model, ollamaConfig)
                }
                else -> {
                    return SpiceResult.failure(
                        SpiceError.validationError("Unknown provider: $provider")
                    )
                }
            }
            SpiceResult.success(agent)
        } catch (e: Exception) {
            SpiceResult.failure(
                SpiceError.executionError(
                    message = "Failed to create agent: ${e.message}",
                    cause = e
                )
            )
        }
    }

    // ===== Default Agent =====

    override fun default(): Agent {
        val defaultProvider = properties.defaultProvider
            ?: getAvailableProviders().firstOrNull()
            ?: throw IllegalStateException("No providers configured")

        return when (defaultProvider.lowercase()) {
            "openai" -> {
                val model = properties.openai.model
                openai(model, OpenAIConfig())
            }
            "anthropic" -> {
                val model = properties.anthropic.model
                anthropic(model, AnthropicConfig())
            }
            "ollama" -> {
                val model = properties.ollama.model
                ollama(model, OllamaConfig())
            }
            else -> throw IllegalStateException("Unknown default provider: $defaultProvider")
        }
    }

    // ===== Provider Query Methods =====

    override fun isProviderAvailable(provider: String): Boolean {
        return when (provider.lowercase()) {
            "openai" -> properties.openai.enabled && properties.openai.apiKey != null
            "anthropic" -> properties.anthropic.enabled && properties.anthropic.apiKey != null
            "ollama" -> properties.ollama.enabled
            "azure-openai" -> properties.azureOpenai.enabled && properties.azureOpenai.apiKey != null
            "vertex-ai" -> properties.vertexAi.enabled && properties.vertexAi.projectId != null
            "bedrock" -> properties.bedrock.enabled
            else -> false
        }
    }

    override fun getAvailableProviders(): List<String> {
        val providers = mutableListOf<String>()
        if (properties.openai.enabled && properties.openai.apiKey != null) providers.add("openai")
        if (properties.anthropic.enabled && properties.anthropic.apiKey != null) providers.add("anthropic")
        if (properties.ollama.enabled) providers.add("ollama")
        if (properties.azureOpenai.enabled && properties.azureOpenai.apiKey != null) providers.add("azure-openai")
        if (properties.vertexAi.enabled && properties.vertexAi.projectId != null) providers.add("vertex-ai")
        if (properties.bedrock.enabled) providers.add("bedrock")
        return providers
    }
}
