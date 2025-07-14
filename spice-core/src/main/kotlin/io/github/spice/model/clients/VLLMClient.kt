package io.github.spice.model.clients

import io.github.spice.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.time.Duration

/**
 * 🚀 VLLMClient - High-performance VLLM model client
 * 
 * ModelClient that communicates with vLLM server to provide high-performance inference.
 * Supports batch-optimized inference using OpenAI-compatible API.
 */
class VLLMClient(
    private val config: VLLMConfig
) : ModelClient {
    
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(config.timeoutMs))
        .build()
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private var statistics = VLLMStatistics()
    
    override val clientId: String = "vllm-${config.baseUrl.hashCode()}"
    override val modelName: String = config.model
    override val description: String = "VLLM high-performance inference client for ${config.model}"
    
    override suspend fun chatCompletion(
        messages: List<ModelMessage>,
        systemPrompt: String?,
        metadata: Map<String, Any>
    ): ModelResponse {
        val startTime = System.currentTimeMillis()
        
        return try {
            val requestMessages = buildRequestMessages(messages, systemPrompt)
            val request = VLLMChatRequest(
                model = config.model,
                messages = requestMessages,
                temperature = config.temperature,
                maxTokens = config.maxTokens,
                stream = false
            )
            
            val httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("${config.baseUrl}/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .apply {
                    config.apiKey?.let { header("Authorization", "Bearer $it") }
                }
                .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(VLLMChatRequest.serializer(), request)))
                .build()
            
            val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
            val responseTime = System.currentTimeMillis() - startTime
            
            if (response.statusCode() == 200) {
                val chatResponse = json.decodeFromString(VLLMChatResponse.serializer(), response.body())
                
                statistics = statistics.copy(
                    totalRequests = statistics.totalRequests + 1,
                    successfulRequests = statistics.successfulRequests + 1,
                    totalResponseTime = statistics.totalResponseTime + responseTime
                )
                
                val usage = chatResponse.usage?.let {
                    TokenUsage(
                        inputTokens = it.promptTokens,
                        outputTokens = it.completionTokens,
                        totalTokens = it.totalTokens
                    )
                }
                
                ModelResponse.success(
                    content = chatResponse.choices.firstOrNull()?.message?.content ?: "",
                    usage = usage,
                    responseTimeMs = responseTime,
                    metadata = mapOf(
                        "model" to config.model,
                        "provider" to "vllm",
                        "finish_reason" to (chatResponse.choices.firstOrNull()?.finishReason ?: "unknown")
                    )
                )
            } else {
                statistics = statistics.copy(
                    totalRequests = statistics.totalRequests + 1,
                    totalResponseTime = statistics.totalResponseTime + responseTime
                )
                
                ModelResponse.failure(
                    error = "VLLM API error: ${response.statusCode()} - ${response.body()}",
                    responseTimeMs = responseTime
                )
            }
            
        } catch (e: Exception) {
            val responseTime = System.currentTimeMillis() - startTime
            statistics = statistics.copy(
                totalRequests = statistics.totalRequests + 1,
                totalResponseTime = statistics.totalResponseTime + responseTime
            )
            
            ModelResponse.failure(
                error = "VLLM client error: ${e.message}",
                responseTimeMs = responseTime
            )
        }
    }
    
    override suspend fun chatCompletionStream(
        messages: List<ModelMessage>,
        systemPrompt: String?,
        metadata: Map<String, Any>
    ): Flow<ModelStreamChunk> = flow {
        // Streaming implementation for VLLM
        emit(ModelStreamChunk("Streaming not yet implemented for VLLM", true))
    }
    
    override fun isReady(): Boolean {
        return try {
            // Simple health check
            val request = HttpRequest.newBuilder()
                .uri(URI.create("${config.baseUrl}/health"))
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() == 200
        } catch (e: Exception) {
            false
        }
    }
    
    override fun getStatus(): ModelClientStatus {
        val avgResponseTime = if (statistics.totalRequests > 0) {
            statistics.totalResponseTime.toDouble() / statistics.totalRequests
        } else {
            0.0
        }
        
        return ModelClientStatus(
            isAvailable = isReady(),
            lastRequestTime = System.currentTimeMillis(),
            totalRequests = statistics.totalRequests,
            successfulRequests = statistics.successfulRequests,
            averageResponseTime = avgResponseTime,
            totalTokenUsage = TokenUsage(0, 0) // Will be implemented with proper tracking
        )
    }
    
    /**
     * Get server information
     */
    suspend fun getServerInfo(): Map<String, Any> {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("${config.baseUrl}/v1/models"))
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                mapOf(
                    "status" to "available",
                    "models" to response.body(),
                    "baseUrl" to config.baseUrl
                )
            } else {
                mapOf(
                    "status" to "error",
                    "error" to "Failed to get server info: ${response.statusCode()}"
                )
            }
        } catch (e: Exception) {
            mapOf(
                "status" to "error",
                "error" to "Connection failed: ${e.message}"
            )
        }
    }
    
    private fun buildRequestMessages(messages: List<ModelMessage>, systemPrompt: String?): List<VLLMMessage> {
        val requestMessages = mutableListOf<VLLMMessage>()
        
        // Add system prompt if provided
        systemPrompt?.let {
            requestMessages.add(VLLMMessage("system", it))
        }
        
        // Add conversation messages
        messages.forEach { message ->
            requestMessages.add(VLLMMessage(message.role, message.content))
        }
        
        return requestMessages
    }
    
    companion object {
        /**
         * Create VLLMClient with custom URL setting
         */
        fun create(
            baseUrl: String,
            model: String,
            apiKey: String? = null,
            temperature: Double = 0.7,
            maxTokens: Int = 2048,
            timeoutMs: Long = 30000
        ): VLLMClient {
            val config = VLLMConfig(
                baseUrl = baseUrl,
                model = model,
                apiKey = apiKey,
                temperature = temperature,
                maxTokens = maxTokens,
                timeoutMs = timeoutMs
            )
            return VLLMClient(config)
        }
        
        /**
         * Create VLLMClient for local development
         */
        fun forLocalDevelopment(
            model: String = "meta-llama/Llama-2-7b-chat-hf",
            port: Int = 8000,
            temperature: Double = 0.7
        ): VLLMClient {
            return create(
                baseUrl = "http://localhost:$port",
                model = model,
                temperature = temperature
            )
        }
        
        /**
         * Create VLLMClient for remote server
         */
        fun forRemoteServer(
            host: String,
            port: Int = 8000,
            model: String,
            apiKey: String? = null,
            useHttps: Boolean = false
        ): VLLMClient {
            val protocol = if (useHttps) "https" else "http"
            return create(
                baseUrl = "$protocol://$host:$port",
                model = model,
                apiKey = apiKey
            )
        }
    }
}

// vLLM API Data Classes (OpenAI Compatible)
@Serializable
data class VLLMRequest(
    val model: String,
    val messages: List<VLLMMessage>,
    val maxTokens: Int,
    val temperature: Double,
    val stream: Boolean = false
)

@Serializable
data class VLLMMessage(
    val role: String,
    val content: String
)

@Serializable
data class VLLMResponse(
    val id: String,
    val choices: List<VLLMChoice>,
    val usage: VLLMUsage?
)

@Serializable
data class VLLMChoice(
    val index: Int,
    val message: VLLMMessage,
    val finishReason: String?
)

@Serializable
data class VLLMUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

@Serializable
data class VLLMModelsResponse(
    val data: List<VLLMModelInfo>
)

@Serializable
data class VLLMModelInfo(
    val id: String,
    val object: String = "model",
    val created: Long? = null,
    val owned_by: String? = null
)

/**
 * vLLM server information
 */
data class VLLMServerInfo(
    val baseUrl: String,
    val availableModels: List<String>,
    val isOnline: Boolean,
    val error: String? = null
)

/**
 * vLLM connection test result
 */
data class VLLMConnectionTest(
    val success: Boolean,
    val responseTimeMs: Long,
    val statusCode: Int,
    val message: String
)

/**
 * 🏗️ Builder functions for VLLMClient
 */
fun createVLLMClient(
    baseUrl: String = "http://localhost:8000",
    model: String = "meta-llama/Llama-3.1-8B-Instruct",
    maxTokens: Int = 1000,
    temperature: Double = 0.7,
    timeout: Duration = Duration.ofSeconds(30)
): VLLMClient {
    return VLLMClient(
        baseUrl = baseUrl,
        model = model,
        maxTokens = maxTokens,
        temperature = temperature,
        timeout = timeout
    )
}

/**
 * Llama 3.1 dedicated VLLMClient generation
 */
fun createLlamaVLLMClient(
    baseUrl: String = "http://localhost:8000",
    size: String = "8B" // 8B, 70B, 405B
): VLLMClient {
    return createVLLMClient(
        baseUrl = baseUrl,
        model = "meta-llama/Llama-3.1-${size}-Instruct"
    )
}

/**
 * CodeLlama dedicated VLLMClient generation
 */
fun createCodeLlamaVLLMClient(
    baseUrl: String = "http://localhost:8000",
    size: String = "7B" // 7B, 13B, 34B
): VLLMClient {
    return createVLLMClient(
        baseUrl = baseUrl,
        model = "codellama/CodeLlama-${size}-Instruct-hf"
    )
}

/**
* Custom URL setting for VLLMClient generation
 */
fun createCustomVLLMClient(
    baseUrl: String,
    model: String,
    maxTokens: Int = 1000,
    temperature: Double = 0.7,
    timeout: Duration = Duration.ofSeconds(30)
): VLLMClient {
    return VLLMClient(
        baseUrl = baseUrl,
        model = model,
        maxTokens = maxTokens,
        temperature = temperature,
        timeout = timeout
    )
}

/**
* to create a local VLLMClient generation
 */
fun createLocalVLLMClient(
    port: Int = 8000,
    model: String = "meta-llama/Llama-3.1-8B-Instruct"
): VLLMClient {
    return createVLLMClient(
        baseUrl = "http://localhost:$port",
        model = model
    )
}

/**
* for remote server VLLMClient generation
 */
fun createRemoteVLLMClient(
    host: String,
    port: Int = 8000,
    model: String = "meta-llama/Llama-3.1-8B-Instruct",
    useHttps: Boolean = false
): VLLMClient {
    val protocol = if (useHttps) "https" else "http"
    return createVLLMClient(
        baseUrl = "$protocol://$host:$port",
        model = model
    )
} 