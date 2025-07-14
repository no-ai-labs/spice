package io.github.spice.model.clients

import io.github.spice.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 🤖 OpenAIClient - OpenAI Chat Completions API ModelClient implementation
 * 
 * ModelClient implementation that uses OpenAI's Chat Completions API.
 * Supports models like GPT-3.5, GPT-4, etc.
 */
class OpenAIClient(
    private val config: ModelClientConfig, 

    
    override val modelName: String = config.defaultModel
) : ModelClient {
    
    override val id: String = "openai-${modelName}"
    override val description: String = "OpenAI API Client for $modelName"
    
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(config.timeoutMs))
        .build()
    
    private val json = Json { ignoreUnknownKeys = true }
    
// Track statistics
    private val totalRequests = AtomicInteger(0)
    private val successfulRequests = AtomicInteger(0)
    private val totalResponseTime = AtomicLong(0)
    private var lastRequestTime: Long? = null
    
    override suspend fun chat(
        messages: List<ModelMessage>,
        systemPrompt: String?,
        metadata: Map<String, String>
    ): ModelResponse {
        val startTime = System.currentTimeMillis()
        totalRequests.incrementAndGet()
        lastRequestTime = startTime
        
        return try {
            val openaiMessages = convertToOpenAIMessages(messages, systemPrompt)
            val requestBody = OpenAIChatRequest(
                model = modelName,
                messages = openaiMessages,
                temperature = metadata["temperature"]?.toDoubleOrNull() ?: config.defaultTemperature,
                max_tokens = metadata["max_tokens"]?.toIntOrNull() ?: config.maxTokens
            )
            
            val response = sendChatRequest(requestBody)
            val responseTime = System.currentTimeMillis() - startTime
            totalResponseTime.addAndGet(responseTime)
            
            if (response.choices.isNotEmpty()) {
                successfulRequests.incrementAndGet()
                val choice = response.choices.first()
                val usage = response.usage?.let { 
                    TokenUsage(
                        inputTokens = it.prompt_tokens,
                        outputTokens = it.completion_tokens,
                        totalTokens = it.total_tokens
                    )
                }
                
                ModelResponse.success(
                    content = choice.message.content,
                    usage = usage,
                    metadata = mapOf(
                        "model" to modelName,
                        "finish_reason" to (choice.finish_reason ?: "unknown"),
                        "provider" to "openai"
                    ),
                    responseTimeMs = responseTime
                )
            } else {
                ModelResponse.error(
                    error = "No response choices received from OpenAI",
                    responseTimeMs = responseTime
                )
            }
            
        } catch (e: Exception) {
            val responseTime = System.currentTimeMillis() - startTime
            totalResponseTime.addAndGet(responseTime)
            
            ModelResponse.error(
                error = "OpenAI API call failed: ${e.message}",
                metadata = mapOf(
                    "provider" to "openai",
                    "model" to modelName,
                    "error_type" to e::class.simpleName.orEmpty()
                ),
                responseTimeMs = responseTime
            )
        }
    }
    
    override suspend fun chatStream(
        messages: List<ModelMessage>,
        systemPrompt: String?,
        metadata: Map<String, String>
    ): Flow<ModelStreamChunk> = flow {
// Streaming implementation (future extension)
        val response = chat(messages, systemPrompt, metadata)
        if (response.success) {
            emit(ModelStreamChunk(
                content = response.content,
                isComplete = true,
                index = 0
            ))
        }
    }
    
    override fun isReady(): Boolean {
        return config.apiKey.isNotBlank()
    }
    
    override suspend fun getStatus(): ClientStatus {
        val totalReqs = totalRequests.get()
        val successReqs = successfulRequests.get()
        val avgResponseTime = if (totalReqs > 0) {
            totalResponseTime.get().toDouble() / totalReqs
        } else {
            0.0
        }
        
        return ClientStatus(
            clientId = id,
            status = if (isReady()) "READY" else "NOT_CONFIGURED",
            lastRequestTime = lastRequestTime,
            totalRequests = totalReqs,
            successfulRequests = successReqs,
            averageResponseTimeMs = avgResponseTime,
            metadata = mapOf(
                "model" to modelName,
                "provider" to "openai",
                "api_configured" to config.apiKey.isNotBlank().toString()
            )
        )
    }
    
    private fun convertToOpenAIMessages(
        messages: List<ModelMessage>, 
        systemPrompt: String?
    ): List<OpenAIMessage> {
        val result = mutableListOf<OpenAIMessage>()
        
        // system prompt add
        if (systemPrompt != null) {
            result.add(OpenAIMessage("system", systemPrompt))
        }
        
        // ModelMessage OpenAIMessageto conversion
        messages.forEach { message ->
            result.add(OpenAIMessage(message.role, message.content))
        }
        
        return result
    }
    
    private suspend fun sendChatRequest(request: OpenAIChatRequest): OpenAIChatResponse {
        val requestBody = json.encodeToString(OpenAIChatRequest.serializer(), request)
        
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("https://api.openai.com/v1/chat/completions"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${config.apiKey}")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()
        
        val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() != 200) {
            throw RuntimeException("OpenAI API returned status ${response.statusCode()}: ${response.body()}")
        }
        
        return json.decodeFromString(OpenAIChatResponse.serializer(), response.body())
    }
}

// OpenAI API request/response models
@Serializable
private data class OpenAIChatRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 4096,
    val stream: Boolean = false
)

@Serializable
private data class OpenAIMessage(
    val role: String,
    val content: String
)

@Serializable
private data class OpenAIChatResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<OpenAIChoice>,
    val usage: OpenAIUsage? = null
)

@Serializable
private data class OpenAIChoice(
    val index: Int,
    val message: OpenAIMessage,
    val finish_reason: String? = null
)

@Serializable
private data class OpenAIUsage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
) 