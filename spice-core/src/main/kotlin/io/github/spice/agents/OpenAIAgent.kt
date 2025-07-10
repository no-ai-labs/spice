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
 * 🤖 Dedicated Agent for communicating with OpenAI API
 * Supports various OpenAI models including GPT-4, GPT-3.5-turbo
 */
class OpenAIAgent(
    id: String = "openai-agent",
    name: String = "OpenAI GPT Agent",
    private val apiKey: String,
    private val model: String = "gpt-4",
    private val maxTokens: Int = 1000,
    private val temperature: Double = 0.7,
    private val baseUrl: String = "https://api.openai.com/v1",
    private val timeout: Duration = Duration.ofSeconds(60)
) : BaseAgent(
    id = id,
    name = name,
    description = "Cloud-based Agent using OpenAI GPT models",
    capabilities = listOf(
        "gpt_4",
        "gpt_3_5_turbo",
        "large_context",
        "function_calling",
        "json_mode",
        "vision" // GPT-4V support
    )
) {
    
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(timeout)
        .build()
    
    private val json = Json { ignoreUnknownKeys = true }
    
    override suspend fun processMessage(message: Message): Message {
        return try {
            val chatRequest = OpenAIChatRequest(
                model = model,
                messages = buildMessages(message),
                max_tokens = maxTokens,
                temperature = temperature
            )
            
            val response = sendChatRequest(chatRequest)
            
            message.createReply(
                content = response.choices.firstOrNull()?.message?.content 
                    ?: "No response received from OpenAI",
                sender = id,
                type = MessageType.TEXT,
                metadata = mapOf<String, String>(
                    "model" to model,
                    "usage_prompt_tokens" to (response.usage?.prompt_tokens?.toString() ?: "0"),
                    "usage_completion_tokens" to (response.usage?.completion_tokens?.toString() ?: "0"),
                    "usage_total_tokens" to (response.usage?.total_tokens?.toString() ?: "0"),
                    "finish_reason" to (response.choices.firstOrNull()?.finish_reason ?: "unknown"),
                    "provider" to "openai"
                )
            )
            
        } catch (e: Exception) {
            message.createReply(
                content = "OpenAI processing failed: ${e.message}",
                sender = id,
                type = MessageType.ERROR,
                metadata = mapOf<String, String>(
                    "error_type" to "openai_error",
                    "provider" to "openai",
                    "model" to model
                )
            )
        }
    }
    
    /**
     * 🎯 Function Calling support
     */
    suspend fun processWithFunctions(
        message: Message, 
        functions: List<OpenAIFunction>
    ): Message {
        return try {
            val chatRequest = OpenAIChatRequest(
                model = model,
                messages = buildMessages(message),
                max_tokens = maxTokens,
                temperature = temperature,
                functions = functions,
                function_call = "auto"
            )
            
            val response = sendChatRequest(chatRequest)
            val choice = response.choices.firstOrNull()
            
            when {
                choice?.message?.function_call != null -> {
                    // Function call response
                    message.createReply(
                        content = "Function call: ${choice.message.function_call.name}",
                        sender = id,
                        type = MessageType.TOOL_CALL,
                        metadata = mapOf<String, String>(
                            "function_name" to choice.message.function_call.name,
                            "function_arguments" to choice.message.function_call.arguments,
                            "provider" to "openai"
                        )
                    )
                }
                else -> {
                    // Regular text response
                    message.createReply(
                        content = choice?.message?.content ?: "No OpenAI response",
                        sender = id,
                        type = MessageType.TEXT,
                        metadata = mapOf<String, String>("provider" to "openai")
                    )
                }
            }
            
        } catch (e: Exception) {
            message.createReply(
                content = "OpenAI Function processing failed: ${e.message}",
                sender = id,
                type = MessageType.ERROR,
                metadata = mapOf<String, String>(
                    "error_type" to "function_calling_error",
                    "provider" to "openai"
                )
            )
        }
    }
    
    /**
     * 🖼️ Vision support (GPT-4V)
     */
    suspend fun processWithVision(
        message: Message,
        imageUrls: List<String>
    ): Message {
        return try {
            val visionMessages = buildVisionMessages(message, imageUrls)
            
            val chatRequest = OpenAIVisionChatRequest(
                model = if (model.contains("gpt-4")) "gpt-4-vision-preview" else model,
                messages = visionMessages,
                max_tokens = maxTokens,
                temperature = temperature
            )
            
            val response = sendVisionChatRequest(chatRequest)
            
            message.createReply(
                content = response.choices.firstOrNull()?.message?.content 
                    ?: "No vision analysis result",
                sender = id,
                type = MessageType.TEXT,
                metadata = mapOf<String, String>(
                    "model" to "gpt-4-vision-preview",
                    "image_count" to imageUrls.size.toString(),
                    "provider" to "openai"
                )
            )
            
        } catch (e: Exception) {
            message.createReply(
                content = "Vision processing failed: ${e.message}",
                sender = id,
                type = MessageType.ERROR,
                metadata = mapOf<String, String>(
                    "error_type" to "vision_error",
                    "provider" to "openai"
                )
            )
        }
    }
    
    /**
     * 📊 Get model information
     */
    suspend fun getModelInfo(): OpenAIModelInfo {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/models/$model"))
                .header("Authorization", "Bearer $apiKey")
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val modelInfo = json.decodeFromString<OpenAIModelResponse>(response.body())
                OpenAIModelInfo(
                    available = true,
                    model = modelInfo.id,
                    created = modelInfo.created,
                    ownedBy = modelInfo.owned_by
                )
            } else {
                OpenAIModelInfo(
                    available = false,
                    model = model,
                    error = "Model information query failed: ${response.statusCode()}"
                )
            }
            
        } catch (e: Exception) {
            OpenAIModelInfo(
                available = false,
                model = model,
                error = "Model information query error: ${e.message}"
            )
        }
    }
    
    override fun isReady(): Boolean {
        return apiKey.isNotBlank() && apiKey.startsWith("sk-")
    }
    
    private fun buildMessages(message: Message): List<OpenAIMessage> {
        return listOf(
            OpenAIMessage(
                role = "user",
                content = message.content
            )
        )
    }
    
    private fun buildVisionMessages(message: Message, imageUrls: List<String>): List<OpenAIVisionMessage> {
        val content = mutableListOf<OpenAIMessageContent>()
        
        // Add text
        content.add(OpenAIMessageContent(
            type = "text",
            text = message.content
        ))
        
        // Add images
        imageUrls.forEach { url ->
            content.add(OpenAIMessageContent(
                type = "image_url",
                image_url = OpenAIImageUrl(url = url)
            ))
        }
        
        return listOf(
            OpenAIVisionMessage(
                role = "user",
                content = content
            )
        )
    }
    
    private suspend fun sendChatRequest(chatRequest: OpenAIChatRequest): OpenAIChatResponse {
        val requestBody = json.encodeToString(chatRequest)
        
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/chat/completions"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(timeout)
            .build()
        
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() != 200) {
            throw RuntimeException("OpenAI API error: ${response.statusCode()} - ${response.body()}")
        }
        
        return json.decodeFromString<OpenAIChatResponse>(response.body())
    }
    
    private suspend fun sendVisionChatRequest(chatRequest: OpenAIVisionChatRequest): OpenAIChatResponse {
        val requestBody = json.encodeToString(chatRequest)
        
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/chat/completions"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(timeout)
            .build()
        
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() != 200) {
            throw RuntimeException("OpenAI Vision API error: ${response.statusCode()} - ${response.body()}")
        }
        
        return json.decodeFromString<OpenAIChatResponse>(response.body())
    }
}

/**
 * 🤖 OpenAI API data classes
 */
@Serializable
data class OpenAIChatRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val max_tokens: Int = 1000,
    val temperature: Double = 0.7,
    val functions: List<OpenAIFunction>? = null,
    val function_call: String? = null
)

@Serializable
data class OpenAIVisionChatRequest(
    val model: String,
    val messages: List<OpenAIVisionMessage>,
    val max_tokens: Int = 1000,
    val temperature: Double = 0.7
)

@Serializable
data class OpenAIMessage(
    val role: String,
    val content: String? = null,
    val function_call: OpenAIFunctionCall? = null
)

@Serializable
data class OpenAIVisionMessage(
    val role: String,
    val content: List<OpenAIMessageContent>,
    val function_call: OpenAIFunctionCall? = null
)

@Serializable
data class OpenAIMessageContent(
    val type: String,
    val text: String? = null,
    val image_url: OpenAIImageUrl? = null
)

@Serializable
data class OpenAIImageUrl(
    val url: String
)

@Serializable
data class OpenAIFunction(
    val name: String,
    val description: String,
    val parameters: Map<String, String>
)

@Serializable
data class OpenAIFunctionCall(
    val name: String,
    val arguments: String
)

@Serializable
data class OpenAIChatResponse(
    val choices: List<OpenAIChoice>,
    val usage: OpenAIUsage? = null
)

@Serializable
data class OpenAIChoice(
    val message: OpenAIResponseMessage,
    val finish_reason: String
)

@Serializable
data class OpenAIResponseMessage(
    val role: String,
    val content: String? = null,
    val function_call: OpenAIFunctionCall? = null
)

@Serializable
data class OpenAIUsage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)

@Serializable
data class OpenAIModelResponse(
    val id: String,
    val created: Long,
    val owned_by: String
)

/**
 * 📊 OpenAI model information
 */
data class OpenAIModelInfo(
    val available: Boolean,
    val model: String,
    val created: Long? = null,
    val ownedBy: String? = null,
    val error: String? = null
) 