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
 * 🧠 Dedicated Agent for communicating with Anthropic Claude API
 * Supports various models including Claude-3, Claude-3.5-Sonnet, Claude-3-Haiku
 */
class AnthropicAgent(
    id: String = "anthropic-agent",
    name: String = "Anthropic Claude Agent",
    private val apiKey: String,
    private val model: String = "claude-3-5-sonnet-20241022",
    private val maxTokens: Int = 1000,
    private val temperature: Double = 0.7,
    private val baseUrl: String = "https://api.anthropic.com/v1",
    private val timeout: Duration = Duration.ofSeconds(60)
) : BaseAgent(
    id = id,
    name = name,
    description = "High-performance inference Agent using Anthropic Claude models",
    capabilities = listOf(
        "claude_3_5_sonnet",
        "claude_3_opus",
        "claude_3_haiku",
        "large_context", // 200K tokens
        "tool_use",
        "vision",
        "reasoning"
    )
) {
    
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(timeout)
        .build()
    
    private val json = Json { ignoreUnknownKeys = true }
    
    override suspend fun processMessage(message: Message): Message {
        return try {
            val chatRequest = ClaudeMessageRequest(
                model = model,
                max_tokens = maxTokens,
                temperature = temperature,
                messages = listOf(
                    ClaudeMessage(
                        role = "user",
                        content = message.content
                    )
                )
            )
            
            val response = sendMessageRequest(chatRequest)
            
            message.createReply(
                content = response.content.firstOrNull()?.text 
                    ?: "No response received from Claude",
                sender = id,
                type = MessageType.TEXT,
                metadata = mapOf<String, String>(
                    "model" to model,
                    "usage_input_tokens" to (response.usage?.input_tokens?.toString() ?: "0"),
                    "usage_output_tokens" to (response.usage?.output_tokens?.toString() ?: "0"),
                    "stop_reason" to (response.stop_reason ?: "unknown"),
                    "provider" to "anthropic"
                )
            )
            
        } catch (e: Exception) {
            message.createReply(
                content = "Claude processing failed: ${e.message}",
                sender = id,
                type = MessageType.ERROR,
                metadata = mapOf<String, String>(
                    "error_type" to "anthropic_error",
                    "provider" to "anthropic",
                    "model" to model
                )
            )
        }
    }
    
    /**
     * 🛠️ Tool Use support
     */
    suspend fun processWithTools(
        message: Message,
        tools: List<ClaudeTool>
    ): Message {
        return try {
            val chatRequest = ClaudeMessageRequest(
                model = model,
                max_tokens = maxTokens,
                temperature = temperature,
                messages = listOf(
                    ClaudeMessage(
                        role = "user",
                        content = message.content
                    )
                ),
                tools = tools
            )
            
            val response = sendMessageRequest(chatRequest)
            
            // Tool use response handling
            val toolUseContent = response.content.firstOrNull { it.type == "tool_use" }
            
            if (toolUseContent != null) {
                message.createReply(
                    content = "Tool usage: ${toolUseContent.name}",
                    sender = id,
                    type = MessageType.TOOL_CALL,
                    metadata = mapOf<String, String>(
                        "tool_name" to (toolUseContent.name ?: "unknown"),
                        "tool_id" to (toolUseContent.id ?: "unknown"),
                        "provider" to "anthropic"
                    )
                )
            } else {
                message.createReply(
                    content = response.content.firstOrNull()?.text ?: "No Claude response",
                    sender = id,
                    type = MessageType.TEXT,
                    metadata = mapOf<String, String>("provider" to "anthropic")
                )
            }
            
        } catch (e: Exception) {
            message.createReply(
                content = "Claude Tool processing failed: ${e.message}",
                sender = id,
                type = MessageType.ERROR,
                metadata = mapOf<String, String>(
                    "error_type" to "tool_use_error",
                    "provider" to "anthropic"
                )
            )
        }
    }
    
    /**
     * 🖼️ Vision support
     */
    suspend fun processWithVision(
        message: Message,
        imageBase64: String,
        imageMediaType: String = "image/jpeg"
    ): Message {
        return try {
            val visionContent = listOf(
                ClaudeContentBlock(
                    type = "text",
                    text = message.content
                ),
                ClaudeContentBlock(
                    type = "image",
                    source = ClaudeImageSource(
                        type = "base64",
                        media_type = imageMediaType,
                        data = imageBase64
                    )
                )
            )
            
            val chatRequest = ClaudeVisionMessageRequest(
                model = model,
                max_tokens = maxTokens,
                temperature = temperature,
                messages = listOf(
                    ClaudeVisionMessage(
                        role = "user",
                        content = visionContent
                    )
                )
            )
            
            val response = sendVisionMessageRequest(chatRequest)
            
            message.createReply(
                content = response.content.firstOrNull()?.text 
                    ?: "No vision analysis result",
                sender = id,
                type = MessageType.TEXT,
                metadata = mapOf<String, String>(
                    "model" to model,
                    "vision_enabled" to "true",
                    "provider" to "anthropic"
                )
            )
            
        } catch (e: Exception) {
            message.createReply(
                content = "Vision processing failed: ${e.message}",
                sender = id,
                type = MessageType.ERROR,
                metadata = mapOf<String, String>(
                    "error_type" to "vision_error",
                    "provider" to "anthropic"
                )
            )
        }
    }
    
    /**
     * 📊 Get model information
     */
    fun getModelInfo(): ClaudeModelInfo {
        return ClaudeModelInfo(
            available = isReady(),
            model = model,
            contextWindow = when {
                model.contains("claude-3") -> 200000
                else -> 100000
            },
            capabilities = capabilities
        )
    }
    
    override fun isReady(): Boolean {
        return apiKey.isNotBlank() && apiKey.startsWith("sk-ant-")
    }
    
    private suspend fun sendMessageRequest(request: ClaudeMessageRequest): ClaudeMessageResponse {
        val requestBody = json.encodeToString(request)
        
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/messages"))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(timeout)
            .build()
        
        val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() != 200) {
            throw RuntimeException("Claude API error: ${response.statusCode()} - ${response.body()}")
        }
        
        return json.decodeFromString<ClaudeMessageResponse>(response.body())
    }
    
    private suspend fun sendVisionMessageRequest(request: ClaudeVisionMessageRequest): ClaudeMessageResponse {
        val requestBody = json.encodeToString(request)
        
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/messages"))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(timeout)
            .build()
        
        val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() != 200) {
            throw RuntimeException("Claude Vision API error: ${response.statusCode()} - ${response.body()}")
        }
        
        return json.decodeFromString<ClaudeMessageResponse>(response.body())
    }
}

/**
 * 🧠 Claude API data classes
 */
@Serializable
data class ClaudeMessageRequest(
    val model: String,
    val max_tokens: Int,
    val temperature: Double = 0.7,
    val messages: List<ClaudeMessage>,
    val tools: List<ClaudeTool>? = null
)

@Serializable
data class ClaudeVisionMessageRequest(
    val model: String,
    val max_tokens: Int,
    val temperature: Double = 0.7,
    val messages: List<ClaudeVisionMessage>
)

@Serializable
data class ClaudeMessage(
    val role: String,
    val content: String? = null
)

@Serializable
data class ClaudeVisionMessage(
    val role: String,
    val content: List<ClaudeContentBlock>
)

@Serializable
data class ClaudeContentBlock(
    val type: String,
    val text: String? = null,
    val name: String? = null,
    val id: String? = null,
    val input: Map<String, String>? = null,
    val source: ClaudeImageSource? = null
)

@Serializable
data class ClaudeImageSource(
    val type: String,
    val media_type: String,
    val data: String
)

@Serializable
data class ClaudeTool(
    val name: String,
    val description: String,
    val input_schema: Map<String, String>
)

@Serializable
data class ClaudeMessageResponse(
    val content: List<ClaudeContentBlock>,
    val usage: ClaudeUsage? = null,
    val stop_reason: String? = null
)

@Serializable
data class ClaudeUsage(
    val input_tokens: Int,
    val output_tokens: Int
)

/**
 * 📊 Claude model information
 */
data class ClaudeModelInfo(
    val available: Boolean,
    val model: String,
    val contextWindow: Int,
    val capabilities: List<String>
) 