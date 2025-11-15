package io.github.noailabs.spice.agents.http

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 🌐 OpenAI HTTP Client
 *
 * Standalone HTTP client for OpenAI API using Ktor.
 * No Spring dependency required.
 *
 * @property apiKey OpenAI API key
 * @property baseUrl API base URL
 * @property organizationId Optional organization ID
 *
 * @since 1.0.0
 * @author Spice Framework
 */
class OpenAIClient(
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1",
    private val organizationId: String? = null
) {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            })
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
        }
    }

    /**
     * Create chat completion
     *
     * @param request Chat completion request
     * @return Chat completion response
     */
    suspend fun createChatCompletion(request: OpenAIRequest): OpenAIResponse {
        return client.post("$baseUrl/chat/completions") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $apiKey")
            organizationId?.let { header("OpenAI-Organization", it) }
            setBody(request)
        }.body()
    }

    /**
     * Close the HTTP client
     */
    fun close() {
        client.close()
    }
}

/**
 * OpenAI Chat Completion Request
 */
@Serializable
data class OpenAIRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val tools: List<Map<String, @Contextual Any>>? = null,
    val stream: Boolean = false
)

/**
 * OpenAI Message
 */
@Serializable
data class OpenAIMessage(
    val role: String,
    val content: String
)

/**
 * OpenAI Chat Completion Response
 */
@Serializable
data class OpenAIResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<OpenAIChoice>,
    val usage: OpenAIUsage? = null
)

/**
 * OpenAI Choice
 */
@Serializable
data class OpenAIChoice(
    val index: Int,
    val message: OpenAIMessage,
    val finishReason: String? = null
)

/**
 * OpenAI Usage Stats
 */
@Serializable
data class OpenAIUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)
