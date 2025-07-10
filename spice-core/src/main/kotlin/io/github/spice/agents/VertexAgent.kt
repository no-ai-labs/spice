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
 * 🤖 Dedicated Agent for communicating with Google Vertex AI API
 * Supports various Google models including Gemini Pro, Bison, PaLM
 */
class VertexAgent(
    id: String = "vertex-agent",
    name: String = "Google Vertex AI Agent",
    private val projectId: String,
    private val location: String = "us-central1",
    private val accessToken: String,
    private val model: String = "gemini-1.5-pro",
    private val maxTokens: Int = 1000,
    private val temperature: Double = 0.7,
    private val timeout: Duration = Duration.ofSeconds(60)
) : BaseAgent(
    id = id,
    name = name,
    description = "Cloud-based Agent using Google Vertex AI models",
    capabilities = listOf(
        "gemini_pro",
        "gemini_flash",
        "palm_bison",
        "large_context",
        "multimodal",
        "function_calling",
        "code_generation"
    )
) {
    
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(timeout)
        .build()
    
    private val json = Json { ignoreUnknownKeys = true }
    
    private val baseUrl = "https://$location-aiplatform.googleapis.com/v1/projects/$projectId/locations/$location/publishers/google/models"
    
    override suspend fun processMessage(message: Message): Message {
        return try {
            val request = when {
                model.contains("gemini") -> createGeminiRequest(message)
                model.contains("bison") -> createBisonRequest(message)
                else -> createGeminiRequest(message) // Default
            }
            
            val response = sendGenerateRequest(request)
            
            message.createReply(
                content = extractContent(response),
                sender = id,
                type = MessageType.TEXT,
                metadata = mapOf<String, String>(
                    "model" to model,
                    "usage_input_tokens" to (response.usageMetadata?.promptTokenCount?.toString() ?: "0"),
                    "usage_output_tokens" to (response.usageMetadata?.candidatesTokenCount?.toString() ?: "0"),
                    "usage_total_tokens" to (response.usageMetadata?.totalTokenCount?.toString() ?: "0"),
                    "provider" to "vertex_ai"
                )
            )
            
        } catch (e: Exception) {
            message.createReply(
                content = "Vertex AI processing failed: ${e.message}",
                sender = id,
                type = MessageType.ERROR,
                metadata = mapOf<String, String>(
                    "error_type" to "vertex_ai_error",
                    "provider" to "vertex_ai",
                    "model" to model
                )
            )
        }
    }
    
    /**
     * 🔧 Function Calling support (Gemini)
     */
    suspend fun processWithFunctions(
        message: Message,
        functions: List<VertexFunction>
    ): Message {
        return try {
            val request = VertexGenerateRequest(
                contents = listOf(
                    VertexContent(
                        role = "user",
                        parts = listOf(
                            VertexPart(text = message.content)
                        )
                    )
                ),
                tools = listOf(
                    VertexTool(
                        function_declarations = functions
                    )
                ),
                generation_config = VertexGenerationConfig(
                    temperature = temperature,
                    max_output_tokens = maxTokens
                )
            )
            
            val response = sendGenerateRequest(request)
            
            // Function call response handling
            val functionCall = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.function_call
            
            if (functionCall != null) {
                message.createReply(
                    content = "Function call: ${functionCall.name}",
                    sender = id,
                    type = MessageType.TOOL_CALL,
                    metadata = mapOf<String, String>(
                        "function_name" to functionCall.name,
                        "function_args" to (functionCall.args?.toString() ?: "{}"),
                        "provider" to "vertex_ai"
                    )
                )
            } else {
                message.createReply(
                    content = extractContent(response),
                    sender = id,
                    type = MessageType.TEXT,
                    metadata = mapOf<String, String>("provider" to "vertex_ai")
                )
            }
            
        } catch (e: Exception) {
            message.createReply(
                content = "Vertex AI Function processing failed: ${e.message}",
                sender = id,
                type = MessageType.ERROR,
                metadata = mapOf<String, String>(
                    "error_type" to "function_calling_error",
                    "provider" to "vertex_ai"
                )
            )
        }
    }
    
    /**
     * 🖼️ Multimodal support (Gemini Vision)
     */
    suspend fun processWithVision(
        message: Message,
        imageBase64: String,
        mimeType: String = "image/jpeg"
    ): Message {
        return try {
            val request = VertexGenerateRequest(
                contents = listOf(
                    VertexContent(
                        role = "user",
                        parts = listOf(
                            VertexPart(text = message.content),
                            VertexPart(
                                inline_data = VertexInlineData(
                                    mime_type = mimeType,
                                    data = imageBase64
                                )
                            )
                        )
                    )
                ),
                generation_config = VertexGenerationConfig(
                    temperature = temperature,
                    max_output_tokens = maxTokens
                )
            )
            
            val response = sendGenerateRequest(request)
            
            message.createReply(
                content = extractContent(response),
                sender = id,
                type = MessageType.TEXT,
                metadata = mapOf<String, String>(
                    "model" to model,
                    "vision_enabled" to "true",
                    "provider" to "vertex_ai"
                )
            )
            
        } catch (e: Exception) {
            message.createReply(
                content = "Vision processing failed: ${e.message}",
                sender = id,
                type = MessageType.ERROR,
                metadata = mapOf<String, String>(
                    "error_type" to "vision_error",
                    "provider" to "vertex_ai"
                )
            )
        }
    }
    
    /**
     * 📊 Get model information
     */
    fun getModelInfo(): VertexModelInfo {
        return VertexModelInfo(
            available = isReady(),
            model = model,
            projectId = projectId,
            location = location,
            capabilities = capabilities
        )
    }
    
    override fun isReady(): Boolean {
        return projectId.isNotBlank() && accessToken.isNotBlank()
    }
    
    private fun createGeminiRequest(message: Message): VertexGenerateRequest {
        return VertexGenerateRequest(
            contents = listOf(
                VertexContent(
                    role = "user",
                    parts = listOf(
                        VertexPart(text = message.content)
                    )
                )
            ),
            generation_config = VertexGenerationConfig(
                temperature = temperature,
                max_output_tokens = maxTokens
            )
        )
    }
    
    private fun createBisonRequest(message: Message): VertexGenerateRequest {
        return VertexGenerateRequest(
            instances = listOf(
                VertexInstance(
                    prompt = message.content
                )
            ),
            parameters = VertexParameters(
                temperature = temperature,
                maxOutputTokens = maxTokens
            )
        )
    }
    
    private fun extractContent(response: VertexGenerateResponse): String {
        return when {
            response.candidates?.isNotEmpty() == true -> {
                response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "No Gemini response"
            }
            response.predictions?.isNotEmpty() == true -> {
                response.predictions.firstOrNull()?.content ?: "No Bison response"
            }
            else -> "No response received from Vertex AI"
        }
    }
    
    private suspend fun sendGenerateRequest(request: VertexGenerateRequest): VertexGenerateResponse {
        val endpoint = when {
            model.contains("gemini") -> "$baseUrl/$model:generateContent"
            model.contains("bison") -> "$baseUrl/$model:predict"
            else -> "$baseUrl/$model:generateContent"
        }
        
        val requestBody = json.encodeToString(request)
        
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $accessToken")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(timeout)
            .build()
        
        val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() != 200) {
            throw RuntimeException("Vertex AI API error: ${response.statusCode()} - ${response.body()}")
        }
        
        return json.decodeFromString<VertexGenerateResponse>(response.body())
    }
}

/**
 * 🤖 Vertex AI API data classes
 */
@Serializable
data class VertexGenerateRequest(
    val contents: List<VertexContent>? = null,
    val tools: List<VertexTool>? = null,
    val generation_config: VertexGenerationConfig? = null,
    // For Bison models
    val instances: List<VertexInstance>? = null,
    val parameters: VertexParameters? = null
)

@Serializable
data class VertexContent(
    val role: String,
    val parts: List<VertexPart>
)

@Serializable
data class VertexPart(
    val text: String? = null,
    val inline_data: VertexInlineData? = null,
    val function_call: VertexFunctionCall? = null
)

@Serializable
data class VertexInlineData(
    val mime_type: String,
    val data: String
)

@Serializable
data class VertexFunctionCall(
    val name: String,
    val args: Map<String, String>? = null
)

@Serializable
data class VertexTool(
    val function_declarations: List<VertexFunction>
)

@Serializable
data class VertexFunction(
    val name: String,
    val description: String,
    val parameters: Map<String, String>
)

@Serializable
data class VertexGenerationConfig(
    val temperature: Double,
    val max_output_tokens: Int
)

// For Bison models
@Serializable
data class VertexInstance(
    val prompt: String
)

@Serializable
data class VertexParameters(
    val temperature: Double,
    val maxOutputTokens: Int
)

@Serializable
data class VertexGenerateResponse(
    val candidates: List<VertexCandidate>? = null,
    val usageMetadata: VertexUsageMetadata? = null,
    // For Bison models
    val predictions: List<VertexPrediction>? = null
)

@Serializable
data class VertexCandidate(
    val content: VertexContent,
    val finishReason: String? = null
)

@Serializable
data class VertexUsageMetadata(
    val promptTokenCount: Int,
    val candidatesTokenCount: Int,
    val totalTokenCount: Int
)

// For Bison models
@Serializable
data class VertexPrediction(
    val content: String
)

/**
 * 📊 Vertex AI model information
 */
data class VertexModelInfo(
    val available: Boolean,
    val model: String,
    val projectId: String,
    val location: String,
    val capabilities: List<String>
) 