package io.github.spice.model

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * 🤖 ModelClient - Unified interface for various LLM models
 * 
 * Provides a unified interface for various LLM models.
 * Agents interact with LLMs through ModelClient,
 * and specific model implementations can be replaced.
 */
interface ModelClient {
    
    /**
     * Client identifier
     */
    val clientId: String
    
    /**
     * Model name
     */
    val modelName: String
    
    /**
     * Client description
     */
    val description: String
    
    /**
     * Chat completion request
     * 
     * @param messages conversation message list
     * @param systemPrompt system prompt (optional)
     * @param metadata additional metadata
     * @return completion response
     */
    suspend fun chatCompletion(
        messages: List<ModelMessage>,
        systemPrompt: String? = null,
        metadata: Map<String, Any> = emptyMap()
    ): ModelResponse
    
    /**
     * Streaming chat completion request
     * 
     * @param messages conversation message list
     * @param systemPrompt system prompt (optional)
     * @param metadata additional metadata
     * @return streaming response Flow
     */
    suspend fun chatCompletionStream(
        messages: List<ModelMessage>,
        systemPrompt: String? = null,
        metadata: Map<String, Any> = emptyMap()
    ): Flow<ModelStreamChunk>
    
    /**
     * Check if client is ready
     */
    fun isReady(): Boolean
    
    /**
     * Get client status
     */
    fun getStatus(): ModelClientStatus
}

/**
 * 📤 ModelResponse - Model response
 */
@Serializable
data class ModelResponse(
    /**
     * Response content
     */
    val content: String,
    
    /**
     * Success status
     */
    val success: Boolean = true,
    
    /**
     * Error message (on failure)
     */
    val error: String? = null,
    
    /**
     * Usage information
     */
    val usage: TokenUsage? = null,
    
    /**
     * Response metadata
     */
    val metadata: Map<String, String> = emptyMap(),
    
    /**
     * Response generation time (milliseconds)
     */
    val responseTimeMs: Long = 0
) {
    
    companion object {
        /**
         * Create success response
         */
        fun success(
            content: String,
            usage: TokenUsage? = null,
            metadata: Map<String, String> = emptyMap(),
            responseTimeMs: Long = 0
        ): ModelResponse {
            return ModelResponse(
                content = content,
                success = true,
                usage = usage,
                metadata = metadata,
                responseTimeMs = responseTimeMs
            )
        }
        
        /**
         * Create failure response
         */
        fun error(
            error: String,
            metadata: Map<String, String> = emptyMap(),
            responseTimeMs: Long = 0
        ): ModelResponse {
            return ModelResponse(
                content = "",
                success = false,
                error = error,
                metadata = metadata,
                responseTimeMs = responseTimeMs
            )
        }
    }
}

/**
 * 🌊 ModelStreamChunk - Streaming response chunk
 */
@Serializable
data class ModelStreamChunk(
    /**
     * Chunk content
     */
    val content: String,
    
    /**
     * Stream completion status
     */
    val isComplete: Boolean = false,
    
    /**
     * Chunk index
     */
    val index: Int = 0,
    
    /**
     * Chunk metadata
     */
    val metadata: Map<String, String> = emptyMap()
)

/**
 * 📊 TokenUsage - Token usage information
 */
@Serializable
data class TokenUsage(
    /**
     * Input token count
     */
    val inputTokens: Int,
    
    /**
     * Output token count
     */
    val outputTokens: Int,
    
    /**
     * Total token count
     */
    val totalTokens: Int = inputTokens + outputTokens,
    
    /**
     * Estimated cost (USD)
     */
    val estimatedCost: Double? = null
) {
    
    /**
     * Token efficiency (output/input ratio)
     */
    val efficiency: Double get() = if (inputTokens > 0) outputTokens.toDouble() / inputTokens else 0.0
}

/**
 * 📈 ModelClientStatus - Client status information
 */
@Serializable
data class ModelClientStatus(
    /**
     * Client availability
     */
    val isAvailable: Boolean,
    
    /**
     * Last request time
     */
    val lastRequestTime: Long? = null,
    
    /**
     * Total request count
     */
    val totalRequests: Int = 0,
    
    /**
     * Successful request count
     */
    val successfulRequests: Int = 0,
    
    /**
     * Average response time (milliseconds)
     */
    val averageResponseTimeMs: Double = 0.0,
    
    /**
     * Total token usage
     */
    val totalTokenUsage: TokenUsage? = null,
    
    /**
     * Add status information
     */
    val metadata: Map<String, String> = emptyMap()
) {
    
    /**
     * Success rate (%)
     */
    val successRate: Double get() = if (totalRequests > 0) (successfulRequests.toDouble() / totalRequests) * 100 else 0.0
    
    /**
     * Check if client is available
     */
    val isReady: Boolean get() = isAvailable
    
    /**
     * Status summary string
     */
    fun getSummary(): String {
        return "Client: ${if (isAvailable) "Available" else "Unavailable"}, " +
            "Requests: $successfulRequests/$totalRequests (${String.format("%.1f", successRate)}%), " +
            "Avg Response: ${String.format("%.0f", averageResponseTimeMs)}ms"
    }
}

/**
 * ⚙️ ModelClientConfig - Client configuration
 */
data class ModelClientConfig(
    /**
     * API key
     */
    val apiKey: String,
    
    /**
     * Default model name
     */
    val defaultModel: String,
    
    /**
     * Request timeout (milliseconds)
     */
    val timeoutMs: Long = 30_000,
    
    /**
     * Maximum retry count
     */
    val maxRetries: Int = 3,
    
    /**
     * Default temperature value
     */
    val defaultTemperature: Double = 0.7,
    
    /**
     * Maximum token count
     */
    val maxTokens: Int = 4096,
    
    /**
     * Add setting
     */
    val additionalConfig: Map<String, Any> = emptyMap()
) 