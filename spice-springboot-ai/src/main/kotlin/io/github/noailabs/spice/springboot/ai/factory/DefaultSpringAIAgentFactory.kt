package io.github.noailabs.spice.springboot.ai.factory

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.springboot.ai.adapters.ChatModelToAgentAdapter
import io.github.noailabs.spice.springboot.ai.config.SpringAIProperties
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.tool.function.FunctionToolCallback
import org.springframework.ai.model.SimpleApiKey
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.ai.anthropic.api.AnthropicApi
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.ai.ollama.api.OllamaApi
import org.springframework.ai.ollama.api.OllamaChatOptions
import org.springframework.web.client.RestClient
import org.springframework.web.reactive.function.client.WebClient

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
    private val functionCallbacks: List<FunctionToolCallback<*, *>> = emptyList()
) : SpringAIAgentFactory {

    // ===== OpenAI =====

    override fun openai(model: String, config: OpenAIConfig): Agent {
        val apiKey = config.apiKey ?: properties.openai.apiKey
            ?: throw IllegalArgumentException("OpenAI API key not provided")

        val api = OpenAiApi.builder()
            .baseUrl(config.baseUrl ?: properties.openai.baseUrl ?: "https://api.openai.com")
            .apiKey(SimpleApiKey(apiKey))
            .restClientBuilder(RestClient.builder())
            .webClientBuilder(WebClient.builder())
            .build()

        val options = OpenAiChatOptions.builder()
            .model(model)
            .temperature(
                config.temperature
                    ?: properties.openai.temperature
                    ?: properties.chat.defaultTemperature
            )
            .apply {
                val maxTokens = config.maxTokens
                    ?: properties.openai.maxTokens
                    ?: properties.chat.defaultMaxTokens
                if (maxTokens != null) maxTokens(maxTokens)

                val topP = config.topP ?: properties.openai.topP
                if (topP != null) topP(topP)

                val freqPenalty = config.frequencyPenalty ?: properties.openai.frequencyPenalty
                if (freqPenalty != null) frequencyPenalty(freqPenalty)

                val presPenalty = config.presencePenalty ?: properties.openai.presencePenalty
                if (presPenalty != null) presencePenalty(presPenalty)
            }
            .build()

        val chatModel = OpenAiChatModel.builder()
            .openAiApi(api)
            .defaultOptions(options)
            .build()

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

        val api = AnthropicApi.builder()
            .apiKey(SimpleApiKey(apiKey))
            .restClientBuilder(RestClient.builder())
            .webClientBuilder(WebClient.builder())
            .build()

        val options = AnthropicChatOptions.builder()
            .model(model)
            .temperature(
                config.temperature
                    ?: properties.anthropic.temperature
                    ?: properties.chat.defaultTemperature
            )
            .apply {
                val maxTokens = config.maxTokens
                    ?: properties.anthropic.maxTokens
                    ?: properties.chat.defaultMaxTokens
                if (maxTokens != null) maxTokens(maxTokens)

                val topP = config.topP ?: properties.anthropic.topP
                if (topP != null) topP(topP)

                val topK = config.topK ?: properties.anthropic.topK
                if (topK != null) topK(topK)
            }
            .build()

        val chatModel = AnthropicChatModel.builder()
            .anthropicApi(api)
            .defaultOptions(options)
            .build()

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

        val api = OllamaApi.builder()
            .baseUrl(baseUrl)
            .restClientBuilder(RestClient.builder())
            .webClientBuilder(WebClient.builder())
            .build()

        val options = OllamaChatOptions.builder()
            .model(model)
            .temperature(
                config.temperature
                    ?: properties.ollama.temperature
                    ?: properties.chat.defaultTemperature
            )
            .apply {
                val maxTokens = config.maxTokens
                    ?: properties.ollama.maxTokens
                    ?: properties.chat.defaultMaxTokens
                if (maxTokens != null) numPredict(maxTokens)

                val topP = config.topP ?: properties.ollama.topP
                if (topP != null) topP(topP)

                val topK = config.topK ?: properties.ollama.topK
                if (topK != null) topK(topK)
            }
            .build()

        val chatModel = OllamaChatModel.builder()
            .ollamaApi(api)
            .defaultOptions(options)
            .build()

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
