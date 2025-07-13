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
 * 🌐 Dedicated Agent for communicating with OpenRouter API
 * Supports various LLM models from multiple providers through a unified interface
 * 
 * OpenRouter provides access to models from:
 * - OpenAI (GPT-4, GPT-3.5)
 * - Anthropic (Claude)
 * - Google (Gemini)
 * - Meta (Llama)
 * - Mistral, Cohere, and many more
 */
class OpenRouterAgent(
    id: String = "openrouter-agent",
    name: String = "OpenRouter Multi-Model Agent",
    private val apiKey: String,
    private val model: String = "openai/gpt-4",
    private val maxTokens: Int = 1000,
    private val temperature: Double = 0.7,
    private val baseUrl: String = "https://openrouter.ai/api/v1",
    private val timeout: Duration = Duration.ofSeconds(120), // Longer timeout for various models
    private val siteName: String? = null, // Optional site name for OpenRouter
    private val siteUrl: String? = null   // Optional site URL for OpenRouter
) : BaseAgent(
    id = id,
    name = name,
    description = "Multi-provider Agent using OpenRouter API for unified LLM access",
    capabilities = listOf(
        "multi_provider",
        "openai_compatible",
        "anthropic_models",
        "google_models", 
        "meta_models",
        "mistral_models",
        "function_calling",
        "large_context",
        "vision" // Available for vision-capable models
    )
) {
    
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(timeout)
        .build()
    
    private val json = Json { ignoreUnknownKeys = true }
    
    override suspend fun processMessage(message: Message): Message {
        return try {
            val chatRequest = OpenRouterChatRequest(
                model = model,
                messages = buildMessages(message),
                max_tokens = maxTokens,
                temperature = temperature
            )
            
            val response = sendChatRequest(chatRequest)
            
            message.createReply(
                content = response.choices.firstOrNull()?.message?.content 
                    ?: "No response received from OpenRouter",
                sender = id,
                type = MessageType.TEXT,
                metadata = mapOf<String, String>(
                    "model" to model,
                    "usage_prompt_tokens" to (response.usage?.prompt_tokens?.toString() ?: "0"),
                    "usage_completion_tokens" to (response.usage?.completion_tokens?.toString() ?: "0"),
                    "usage_total_tokens" to (response.usage?.total_tokens?.toString() ?: "0"),
                    "finish_reason" to (response.choices.firstOrNull()?.finish_reason ?: "unknown"),
                    "provider" to "openrouter",
                    "provider_name" to (response.model ?: model)
                )
            )
            
        } catch (e: Exception) {
            message.createReply(
                content = "OpenRouter processing failed: ${e.message}",
                sender = id,
                type = MessageType.ERROR,
                metadata = mapOf<String, String>(
                    "error_type" to "openrouter_error",
                    "provider" to "openrouter",
                    "model" to model
                )
            )
        }
    }
    
    /**
     * 🎯 Function Calling support for compatible models
     */
    suspend fun processWithFunctions(
        message: Message, 
        functions: List<OpenRouterFunction>
    ): Message {
        return try {
            val chatRequest = OpenRouterChatRequest(
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
                            "provider" to "openrouter",
                            "model" to model
                        )
                    )
                }
                else -> {
                    // Regular text response
                    message.createReply(
                        content = choice?.message?.content ?: "No response received",
                        sender = id,
                        type = MessageType.TEXT,
                        metadata = mapOf<String, String>(
                            "provider" to "openrouter",
                            "model" to model
                        )
                    )
                }
            }
            
        } catch (e: Exception) {
            message.createReply(
                content = "OpenRouter function calling failed: ${e.message}",
                sender = id,
                type = MessageType.ERROR,
                metadata = mapOf<String, String>(
                    "error_type" to "openrouter_function_error",
                    "provider" to "openrouter"
                )
            )
        }
    }
    
    /**
     * 📊 Get available models from OpenRouter
     */
    suspend fun getAvailableModels(): List<OpenRouterModel> {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/models"))
                .header("Authorization", "Bearer $apiKey")
                .apply {
                    siteName?.let { header("HTTP-Referer", it) }
                    siteUrl?.let { header("X-Title", it) }
                }
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val modelResponse = json.decodeFromString<OpenRouterModelsResponse>(response.body())
                modelResponse.data
            } else {
                emptyList()
            }
            
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 🔄 Switch model dynamically
     */
    fun withModel(newModel: String): OpenRouterAgent {
        return OpenRouterAgent(
            id = id,
            name = name,
            apiKey = apiKey,
            model = newModel,
            maxTokens = maxTokens,
            temperature = temperature,
            baseUrl = baseUrl,
            timeout = timeout,
            siteName = siteName,
            siteUrl = siteUrl
        )
    }
    
    /**
     * 🎨 Vision support for compatible models
     */
    suspend fun processVisionMessage(message: Message, imageUrls: List<String>): Message {
        return try {
            val visionRequest = OpenRouterVisionChatRequest(
                model = model,
                messages = buildVisionMessages(message, imageUrls),
                max_tokens = maxTokens,
                temperature = temperature
            )
            
            val response = sendVisionChatRequest(visionRequest)
            
            message.createReply(
                content = response.choices.firstOrNull()?.message?.content 
                    ?: "No vision response received",
                sender = id,
                type = MessageType.TEXT,
                metadata = mapOf<String, String>(
                    "provider" to "openrouter",
                    "model" to model,
                    "vision_enabled" to "true",
                    "image_count" to imageUrls.size.toString()
                )
            )
            
        } catch (e: Exception) {
            message.createReply(
                content = "OpenRouter vision processing failed: ${e.message}",
                sender = id,
                type = MessageType.ERROR,
                metadata = mapOf<String, String>(
                    "error_type" to "openrouter_vision_error",
                    "provider" to "openrouter"
                )
            )
        }
    }
    
    private fun buildMessages(message: Message): List<OpenRouterMessage> {
        return listOf(
            OpenRouterMessage(
                role = "user",
                content = message.content
            )
        )
    }
    
    private fun buildVisionMessages(message: Message, imageUrls: List<String>): List<OpenRouterVisionMessage> {
        val content = mutableListOf<OpenRouterMessageContent>()
        
        // Add text
        content.add(OpenRouterMessageContent(
            type = "text",
            text = message.content
        ))
        
        // Add images
        imageUrls.forEach { url ->
            content.add(OpenRouterMessageContent(
                type = "image_url",
                image_url = OpenRouterImageUrl(url = url)
            ))
        }
        
        return listOf(
            OpenRouterVisionMessage(
                role = "user",
                content = content
            )
        )
    }
    
    private suspend fun sendChatRequest(chatRequest: OpenRouterChatRequest): OpenRouterChatResponse {
        val requestBody = json.encodeToString(chatRequest)
        
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/chat/completions"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .apply {
                siteName?.let { header("HTTP-Referer", it) }
                siteUrl?.let { header("X-Title", it) }
            }
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(timeout)
            .build()
        
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() != 200) {
            throw RuntimeException("OpenRouter API error: ${response.statusCode()} - ${response.body()}")
        }
        
        return json.decodeFromString<OpenRouterChatResponse>(response.body())
    }
    
    private suspend fun sendVisionChatRequest(visionRequest: OpenRouterVisionChatRequest): OpenRouterChatResponse {
        val requestBody = json.encodeToString(visionRequest)
        
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/chat/completions"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .apply {
                siteName?.let { header("HTTP-Referer", it) }
                siteUrl?.let { header("X-Title", it) }
            }
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(timeout)
            .build()
        
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() != 200) {
            throw RuntimeException("OpenRouter Vision API error: ${response.statusCode()} - ${response.body()}")
        }
        
        return json.decodeFromString<OpenRouterChatResponse>(response.body())
    }
}

/**
 * 🌐 OpenRouter API data classes
 */
@Serializable
data class OpenRouterChatRequest(
    val model: String,
    val messages: List<OpenRouterMessage>,
    val max_tokens: Int? = null,
    val temperature: Double? = null,
    val functions: List<OpenRouterFunction>? = null,
    val function_call: String? = null
)

@Serializable
data class OpenRouterVisionChatRequest(
    val model: String,
    val messages: List<OpenRouterVisionMessage>,
    val max_tokens: Int? = null,
    val temperature: Double? = null
)

@Serializable
data class OpenRouterMessage(
    val role: String,
    val content: String
)

@Serializable
data class OpenRouterVisionMessage(
    val role: String,
    val content: List<OpenRouterMessageContent>
)

@Serializable
data class OpenRouterMessageContent(
    val type: String,
    val text: String? = null,
    val image_url: OpenRouterImageUrl? = null
)

@Serializable
data class OpenRouterImageUrl(
    val url: String
)

@Serializable
data class OpenRouterFunction(
    val name: String,
    val description: String,
    val parameters: Map<String, String>
)

@Serializable
data class OpenRouterFunctionCall(
    val name: String,
    val arguments: String
)

@Serializable
data class OpenRouterChatResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<OpenRouterChoice>,
    val usage: OpenRouterUsage?
)

@Serializable
data class OpenRouterChoice(
    val index: Int,
    val message: OpenRouterResponseMessage,
    val finish_reason: String
)

@Serializable
data class OpenRouterResponseMessage(
    val role: String,
    val content: String?,
    val function_call: OpenRouterFunctionCall? = null
)

@Serializable
data class OpenRouterUsage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)

@Serializable
data class OpenRouterModelsResponse(
    val data: List<OpenRouterModel>
)

/**
 * 📊 OpenRouter model information
 */
@Serializable
data class OpenRouterModel(
    val id: String,
    val name: String,
    val description: String? = null,
    val context_length: Int? = null,
    val pricing: OpenRouterPricing? = null,
    val top_provider: OpenRouterProvider? = null
)

@Serializable
data class OpenRouterPricing(
    val prompt: String,
    val completion: String
)

@Serializable
data class OpenRouterProvider(
    val max_completion_tokens: Int? = null,
    val is_moderated: Boolean? = null
)
