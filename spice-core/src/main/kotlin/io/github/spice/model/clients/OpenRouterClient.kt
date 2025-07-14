package io.github.spice.model.clients

import io.github.spice.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 🌐 OpenRouterClient - OpenRouter API ModelClient implementation
 * 
 * ModelClient that can access various LLM models through OpenRouter API.
 * Supports various models like Claude, GPT, Llama, etc. using OpenAI-compatible API.
 */
class OpenRouterClient(
    private val config: ModelClientConfig
) : ModelClient {
    
    override val id: String = "openrouter-client-${System.currentTimeMillis()}"
    override val modelName: String = config.defaultModel
    override val description: String = "OpenRouter client for ${config.defaultModel}"
    
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(config.timeoutMs.toLong()))
        .build()
    
    private val json = Json { ignoreUnknownKeys = true }
    
    @Volatile
    private var requestCount = 0
    @Volatile
    private var successCount = 0
    @Volatile
    private var totalResponseTime = 0L
    
    override suspend fun chat(
        messages: List<ModelMessage>,
        systemPrompt: String?,
        metadata: Map<String, String>
    ): ModelResponse {
        val startTime = System.currentTimeMillis()
        requestCount++
        
        return try {
            val request = buildOpenRouterRequest(messages, systemPrompt)
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            val responseTime = System.currentTimeMillis() - startTime
            totalResponseTime += responseTime
            
            if (response.statusCode() == 200) {
                successCount++
                val openRouterResponse = json.decodeFromString<OpenRouterResponse>(response.body())
                
                val choice = openRouterResponse.choices.firstOrNull()
                val content = choice?.message?.content ?: ""
                
                val usage = TokenUsage(
                    inputTokens = openRouterResponse.usage?.promptTokens ?: 0,
                    outputTokens = openRouterResponse.usage?.completionTokens ?: 0,
                    totalTokens = openRouterResponse.usage?.totalTokens ?: 0
                )
                
                ModelResponse.success(
                    content = content,
                    usage = usage,
                    responseTimeMs = responseTime,
                    metadata = mapOf(
                        "model_used" to (openRouterResponse.model ?: config.defaultModel),
                        "generation_id" to (openRouterResponse.id ?: "unknown")
                    )
                )
            } else {
                val errorBody = response.body()
                throw Exception("OpenRouter API error: ${response.statusCode()} - $errorBody")
            }
        } catch (e: Exception) {
            throw Exception("Failed to communicate with OpenRouter: ${e.message}", e)
        }
    }
    
    override suspend fun chatStream(
        messages: List<ModelMessage>,
        systemPrompt: String?,
        metadata: Map<String, String>
    ): Flow<ModelStreamChunk> = flow {
        // OpenRouter streaming implementation would go here
        // For now, fall back to regular chat
        val response = chat(messages, systemPrompt, metadata)
        emit(ModelStreamChunk(
            content = response.content,
            isComplete = true
        ))
    }
    
    override fun isReady(): Boolean {
        return config.apiKey.isNotEmpty()
    }
    
    override suspend fun getStatus(): ClientStatus {
        val successRate = if (requestCount > 0) (successCount.toDouble() / requestCount) * 100 else 0.0
        val avgResponseTime = if (requestCount > 0) totalResponseTime.toDouble() / requestCount else 0.0
        
        return ClientStatus(
            clientId = id,
            status = if (isReady()) "READY" else "OFFLINE",
            totalRequests = requestCount,
            successfulRequests = successCount,
            averageResponseTimeMs = avgResponseTime,
            metadata = mapOf(
                "model" to config.defaultModel,
                "base_url" to "https://openrouter.ai/api/v1", // This should ideally be configurable
                "app_name" to "Spice-Framework" // This should ideally be configurable
            )
        )
    }
    
    /**
     * Get a list of available models from OpenRouter.
     */
    suspend fun getAvailableModels(): List<OpenRouterModel> {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://openrouter.ai/api/v1/models"))
                .header("Authorization", "Bearer ${config.apiKey}")
                .header("HTTP-Referer", "https://github.com/spice-framework") // This should ideally be configurable
                .header("X-Title", "Spice-Framework") // This should ideally be configurable
                .GET()
                .timeout(Duration.ofSeconds(config.timeoutMs.toLong()))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val modelsResponse = json.decodeFromString<OpenRouterModelsResponse>(response.body())
                modelsResponse.data
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get information about a specific model.
     */
    suspend fun getModelInfo(modelId: String): OpenRouterModel? {
        return getAvailableModels().find { it.id == modelId }
    }
    
    private fun buildOpenRouterRequest(messages: List<ModelMessage>, systemPrompt: String? = null): HttpRequest {
        val endpoint = "https://openrouter.ai/api/v1/chat/completions"
        
        val allMessages = mutableListOf<OpenRouterMessage>()
        
        // Add system prompt if provided
        systemPrompt?.let { 
            allMessages.add(OpenRouterMessage(role = "system", content = it))
        }
        
        // Convert ModelMessage to OpenRouterMessage
        allMessages.addAll(messages.map { message ->
            OpenRouterMessage(
                role = message.role,
                content = message.content
            )
        })
        
        val requestBody = OpenRouterRequest(
            model = config.defaultModel,
            messages = allMessages,
            maxTokens = 4000, // This should ideally be configurable
            temperature = 0.7, // This should ideally be configurable
            stream = false
        )
        
        return HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .header("HTTP-Referer", "https://github.com/spice-framework") // This should ideally be configurable
            .header("X-Title", "Spice-Framework") // This should ideally be configurable
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))
            .timeout(Duration.ofSeconds(config.timeoutMs.toLong()))
            .build()
    }

    /**
     * Create high-performance model dedicated OpenRouterClient
     */
    fun forHighPerformance(
        apiKey: String,
        model: String = "anthropic/claude-3-haiku"
    ): OpenRouterClient {
        val config = ModelClientConfig(
            apiKey = apiKey,
            defaultModel = model,
            timeoutMs = 60000,
            maxRetries = 2,
            temperature = 0.3 // More consistent output
        )
        return OpenRouterClient(config)
    }
}

// OpenRouter API Data Classes
@Serializable
data class OpenRouterRequest(
    val model: String,
    val messages: List<OpenRouterMessage>,
    val maxTokens: Int,
    val temperature: Double,
    val stream: Boolean = false
)

@Serializable
data class OpenRouterMessage(
    val role: String,
    val content: String
)

@Serializable
data class OpenRouterResponse(
    val id: String?,
    val model: String?,
    val choices: List<OpenRouterChoice>,
    val usage: OpenRouterUsage?
)

@Serializable
data class OpenRouterChoice(
    val index: Int,
    val message: OpenRouterMessage,
    val finishReason: String?
)

@Serializable
data class OpenRouterUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

@Serializable
data class OpenRouterModelsResponse(
    val data: List<OpenRouterModel>
)

@Serializable
data class OpenRouterModel(
    val id: String,
    val name: String,
    val description: String?,
    val pricing: OpenRouterPricing?,
    val context_length: Int?,
    val architecture: OpenRouterArchitecture?,
    val top_provider: OpenRouterProvider?
)

@Serializable
data class OpenRouterPricing(
    val prompt: String?,
    val completion: String?
)

@Serializable
data class OpenRouterArchitecture(
    val modality: String?,
    val tokenizer: String?,
    val instruct_type: String?
)

@Serializable
data class OpenRouterProvider(
    val max_completion_tokens: Int?,
    val is_moderated: Boolean?
)

/**
 * 🏗️ Builder functions for OpenRouterClient
 */

/**
 * basic OpenRouterClient generation
 */
fun createOpenRouterClient(
    apiKey: String,
    model: String = "anthropic/claude-3.5-sonnet",
    baseUrl: String = "https://openrouter.ai/api/v1",
    appName: String = "Spice-Framework",
    siteUrl: String = "https://github.com/spice-framework",
    maxTokens: Int = 4000,
    temperature: Double = 0.7
): OpenRouterClient {
    val config = ModelClientConfig(
        apiKey = apiKey,
        defaultModel = model,
        timeoutMs = 60000,
        maxRetries = 2,
        temperature = temperature
    )
    return OpenRouterClient(config)
}

/**
 * Claude model dedicated OpenRouterClient generation
 */
fun createOpenRouterClaudeClient(
    apiKey: String,
    model: String = "anthropic/claude-3.5-sonnet",
    appName: String = "Spice-Framework"
): OpenRouterClient {
    return createOpenRouterClient(
        apiKey = apiKey,
        model = model,
        appName = appName
    )
}

/**
 * GPT model dedicated OpenRouterClient generation
 */
fun createOpenRouterGPTClient(
    apiKey: String,
    model: String = "openai/gpt-4o-mini",
    appName: String = "Spice-Framework"
): OpenRouterClient {
    return createOpenRouterClient(
        apiKey = apiKey,
        model = model,
        appName = appName
    )
}

/**
 * Llama model dedicated OpenRouterClient generation
 */
fun createOpenRouterLlamaClient(
    apiKey: String,
    model: String = "meta-llama/llama-3.1-8b-instruct",
    appName: String = "Spice-Framework"
): OpenRouterClient {
    return createOpenRouterClient(
        apiKey = apiKey,
        model = model,
        appName = appName
    )
}

/**
 * Create high-performance model dedicated OpenRouterClient
 */
fun createOpenRouterHighPerformanceClient(
    apiKey: String,
    model: String = "anthropic/claude-3.5-sonnet",
    appName: String = "Spice-Framework"
): OpenRouterClient {
    return createOpenRouterClient(
        apiKey = apiKey,
        model = model,
        appName = appName,
        maxTokens = 8000,
        temperature = 0.3 // More consistent output
    )
} 