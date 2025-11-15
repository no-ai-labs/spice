package io.github.noailabs.spice.agents.http

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 🌐 Anthropic HTTP Client
 *
 * Standalone HTTP client for Anthropic API using Ktor.
 * No Spring dependency required.
 *
 * @property apiKey Anthropic API key
 * @property baseUrl API base URL
 *
 * @since 1.0.0
 * @author Spice Framework
 */
class AnthropicClient(
    private val apiKey: String,
    private val baseUrl: String = "https://api.anthropic.com"
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
     * Create message completion
     *
     * @param request Message request
     * @return Message response
     */
    suspend fun createMessage(request: AnthropicRequest): AnthropicResponse {
        return client.post("$baseUrl/v1/messages") {
            contentType(ContentType.Application.Json)
            header("x-api-key", apiKey)
            header("anthropic-version", "2023-06-01")
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
 * Anthropic Message Request
 */
@Serializable
data class AnthropicRequest(
    val model: String,
    val messages: List<AnthropicMessage>,
    val maxTokens: Int = 1024,
    val temperature: Double? = null,
    val system: String? = null,
    val stream: Boolean = false
)

/**
 * Anthropic Message
 */
@Serializable
data class AnthropicMessage(
    val role: String,
    val content: String
)

/**
 * Anthropic Message Response
 */
@Serializable
data class AnthropicResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<AnthropicContent>,
    val model: String,
    val stopReason: String? = null,
    val usage: AnthropicUsage
)

/**
 * Anthropic Content Block
 */
@Serializable
data class AnthropicContent(
    val type: String,
    val text: String
)

/**
 * Anthropic Usage Stats
 */
@Serializable
data class AnthropicUsage(
    val inputTokens: Int,
    val outputTokens: Int
)
