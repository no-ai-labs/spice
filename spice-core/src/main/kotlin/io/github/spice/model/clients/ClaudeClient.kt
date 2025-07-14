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
 * 🧠 ClaudeClient - Anthropic Claude API ModelClient implementation
 * 
 * ModelClient implementation that uses Anthropic's Claude API.
 * Supports models like Claude-3, Claude-2, etc.
 */
class ClaudeClient(
    private val config: ModelClientConfig,
    override val modelName: String = config.defaultModel
) : ModelClient {
    
    override val id: String = "claude-${modelName}"
    override val description: String = "Anthropic Claude API Client for $modelName"
    
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
            val claudeMessages = convertToClaudeMessages(messages)
            val requestBody = ClaudeMessageRequest(
                model = modelName,
                max_tokens = metadata["max_tokens"]?.toIntOrNull() ?: config.maxTokens,
                temperature = metadata["temperature"]?.toDoubleOrNull() ?: config.defaultTemperature,
                system = systemPrompt,
                messages = claudeMessages
            )
            
            val response = sendMessageRequest(requestBody)
            val responseTime = System.currentTimeMillis() - startTime
            totalResponseTime.addAndGet(responseTime)
            
            if (response.content.isNotEmpty()) {
                successfulRequests.incrementAndGet()
                val content = response.content.first()
                val usage = response.usage?.let {
                    TokenUsage(
                        inputTokens = it.input_tokens,
                        outputTokens = it.output_tokens,
                        totalTokens = it.input_tokens + it.output_tokens
                    )
                }
                
                ModelResponse.success(
                    content = content.text,
                    usage = usage,
                    metadata = mapOf(
                        "model" to modelName,
                        "stop_reason" to (response.stop_reason ?: "unknown"),
                        "provider" to "anthropic"
                    ),
                    responseTimeMs = responseTime
                )
            } else {
                ModelResponse.error(
                    error = "No content received from Claude",
                    responseTimeMs = responseTime
                )
            }
            
        } catch (e: Exception) {
            val responseTime = System.currentTimeMillis() - startTime
            totalResponseTime.addAndGet(responseTime)
            
            ModelResponse.error(
                error = "Claude API call failed: ${e.message}",
                metadata = mapOf(
                    "provider" to "anthropic",
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
                "provider" to "anthropic",
                "api_configured" to config.apiKey.isNotBlank().toString()
            )
        )
    }
    
    private fun convertToClaudeMessages(messages: List<ModelMessage>): List<ClaudeMessage> {
        return messages.filter { !it.isSystem }.map { message ->
            ClaudeMessage(
                role = message.role,
                content = message.content
            )
        }
    }
    
    private suspend fun sendMessageRequest(request: ClaudeMessageRequest): ClaudeMessageResponse {
        val requestBody = json.encodeToString(ClaudeMessageRequest.serializer(), request)
        
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("https://api.anthropic.com/v1/messages"))
            .header("Content-Type", "application/json")
            .header("x-api-key", config.apiKey)
            .header("anthropic-version", "2023-06-01")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()
        
        val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() != 200) {
            throw RuntimeException("Claude API returned status ${response.statusCode()}: ${response.body()}")
        }
        
        return json.decodeFromString(ClaudeMessageResponse.serializer(), response.body())
    }
}

// Claude API request/response models
@Serializable
private data class ClaudeMessageRequest(
    val model: String,
    val max_tokens: Int,
    val temperature: Double = 0.7,
    val system: String? = null,
    val messages: List<ClaudeMessage>
)

@Serializable
private data class ClaudeMessage(
    val role: String,
    val content: String
)

@Serializable
private data class ClaudeMessageResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ClaudeContent>,
    val model: String,
    val stop_reason: String? = null,
    val stop_sequence: String? = null,
    val usage: ClaudeUsage? = null
)

@Serializable
private data class ClaudeContent(
    val type: String,
    val text: String
)

@Serializable
private data class ClaudeUsage(
    val input_tokens: Int,
    val output_tokens: Int
) 