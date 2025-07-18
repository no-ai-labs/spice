package io.github.spice.agents

import io.github.spice.*
import io.github.spice.dsl.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*

/**
 * 🤖 Abstract LLM Agent Base
 * 
 * Common base class for LLM agents (Claude, GPT, etc.)
 * Reduces code duplication and provides consistent interface.
 */

/**
 * Common LLM configuration interface
 */
interface LLMConfig {
    val apiKey: String
    val model: String
    val maxTokens: Int
    val temperature: Double
    val systemPrompt: String
}

/**
 * Abstract API client for LLM services
 */
abstract class AbstractLLMClient<T : LLMConfig>(protected val config: T) {
    protected val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
    
    /**
     * Send message to LLM API
     */
    abstract suspend fun sendMessage(content: String): String
    
    /**
     * Build request body for API call
     */
    protected abstract fun buildRequestBody(content: String): JsonObject
    
    /**
     * Get API endpoint URL
     */
    protected abstract fun getApiEndpoint(): String
    
    /**
     * Get authorization header
     */
    protected abstract fun getAuthHeader(): String
    
    /**
     * Common request logic
     */
    protected open suspend fun makeApiRequest(content: String): String {
        return try {
            val requestBody = buildRequestBody(content)
            
            val response: JsonObject = httpClient.post(getApiEndpoint()) {
                headers {
                    append(HttpHeaders.Authorization, getAuthHeader())
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
                setBody(requestBody)
            }.body()
            
            extractResponseContent(response)
        } catch (e: Exception) {
            "Error calling API: ${e.message}"
        }
    }
    
    /**
     * Extract content from API response
     */
    protected abstract fun extractResponseContent(response: JsonObject): String
    
    fun close() {
        httpClient.close()
    }
}

/**
 * Create a modern LLM agent using DSL
 */
fun <T : LLMConfig> modernLLMAgent(
    id: String,
    config: T,
    clientFactory: (T) -> AbstractLLMClient<T>
): Agent = buildAgent {
    this.id = id
    name = "$id-agent"
    description = "Modern LLM Agent powered by ${config.model}"
    
    val client = clientFactory(config)
    
    handle { comm ->
        when (comm.type) {
            CommType.TEXT -> {
                val response = client.sendMessage(comm.content)
                comm.reply(
                    content = response,
                    from = id
                )
            }
            CommType.SYSTEM -> {
                comm.reply(
                    content = "System message acknowledged",
                    from = id,
                    type = CommType.SYSTEM
                )
            }
            CommType.ERROR -> {
                comm.reply(
                    content = "Error received: ${comm.content}",
                    from = id,
                    type = CommType.ERROR
                )
            }
            else -> {
                comm.reply(
                    content = "Unsupported message type: ${comm.type}",
                    from = id,
                    type = CommType.ERROR
                )
            }
        }
    }
}