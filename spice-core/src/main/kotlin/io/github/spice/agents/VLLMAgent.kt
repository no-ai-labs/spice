package io.github.spice.agents

import io.github.spice.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * 🚀 High-performance Agent communicating with vLLM server
 * Provides batch-optimized inference using OpenAI Compatible API
 */
class VLLMAgent(
    id: String = "vllm-agent",
    name: String = "vLLM High-Performance Agent",
    private val baseUrl: String = "http://localhost:8000",
    private val model: String = "meta-llama/Llama-3.1-8B-Instruct",
    private val maxTokens: Int = 1000,
    private val temperature: Double = 0.7,
    private val timeout: Duration = Duration.ofSeconds(30)
) : BaseAgent(
    id = id,
    name = name,
    description = "High-performance inference Agent through vLLM server (batch processing optimized)",
    capabilities = listOf(
        "high_performance_inference",
        "batch_processing",
        "gpu_acceleration",
        "openai_compatible",
        "local_deployment"
    )
) {
    
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(timeout)
        .build()
    
    private val json = Json { ignoreUnknownKeys = true }
    
    override suspend fun processMessage(message: Message): Message {
        return try {
            val chatRequest = VLLMChatRequest(
                model = model,
                messages = listOf(
                    VLLMChatMessage(
                        role = "user",
                        content = message.content
                    )
                ),
                max_tokens = maxTokens,
                temperature = temperature,
                stream = false
            )
            
            val response = sendChatRequest(chatRequest)
            
            message.createReply(
                content = response.choices.firstOrNull()?.message?.content 
                    ?: "No response received from vLLM",
                sender = id,
                type = MessageType.TEXT,
                metadata = mapOf<String, String>(
                    "model" to model,
                    "usage_prompt_tokens" to (response.usage?.prompt_tokens?.toString() ?: "0"),
                    "usage_completion_tokens" to (response.usage?.completion_tokens?.toString() ?: "0"),
                    "usage_total_tokens" to (response.usage?.total_tokens?.toString() ?: "0"),
                    "finish_reason" to (response.choices.firstOrNull()?.finish_reason ?: "unknown"),
                    "provider" to "vllm"
                )
            )
            
        } catch (e: Exception) {
            message.createReply(
                content = "vLLM processing failed: ${e.message}",
                sender = id,
                type = MessageType.ERROR,
                metadata = mapOf<String, String>(
                    "error_type" to "vllm_error",
                    "provider" to "vllm",
                    "model" to model
                )
            )
        }
    }
    
    /**
     * 🌡️ Check server health status
     */
    suspend fun checkHealth(): VLLMHealthStatus {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/health"))
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                VLLMHealthStatus(
                    healthy = true,
                    model = model,
                    baseUrl = baseUrl,
                    message = "vLLM server is running normally"
                )
            } else {
                VLLMHealthStatus(
                    healthy = false,
                    model = model,
                    baseUrl = baseUrl,
                    message = "vLLM server response error: ${response.statusCode()}"
                )
            }
            
        } catch (e: Exception) {
            VLLMHealthStatus(
                healthy = false,
                model = model,
                baseUrl = baseUrl,
                message = "vLLM server connection failed: ${e.message}"
            )
        }
    }
    
    override fun isReady(): Boolean {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/health"))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() == 200
            
        } catch (e: Exception) {
            false
        }
    }
    
    private suspend fun sendChatRequest(chatRequest: VLLMChatRequest): VLLMChatResponse {
        val requestBody = json.encodeToString(chatRequest)
        
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/v1/chat/completions"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(timeout)
            .build()
        
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() != 200) {
            throw RuntimeException("vLLM API error: ${response.statusCode()} - ${response.body()}")
        }
        
        return json.decodeFromString<VLLMChatResponse>(response.body())
    }
}

/**
 * 💬 vLLM Chat API request data classes
 */
@Serializable
data class VLLMChatRequest(
    val model: String,
    val messages: List<VLLMChatMessage>,
    val max_tokens: Int = 1000,
    val temperature: Double = 0.7,
    val stream: Boolean = false
)

@Serializable
data class VLLMChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class VLLMChatResponse(
    val choices: List<VLLMChoice>,
    val usage: VLLMUsage? = null
)

@Serializable
data class VLLMChoice(
    val message: VLLMResponseMessage,
    val finish_reason: String
)

@Serializable
data class VLLMResponseMessage(
    val role: String,
    val content: String
)

@Serializable
data class VLLMUsage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)

/**
 * 🌡️ vLLM server health status
 */
data class VLLMHealthStatus(
    val healthy: Boolean,
    val model: String,
    val baseUrl: String,
    val message: String
) 