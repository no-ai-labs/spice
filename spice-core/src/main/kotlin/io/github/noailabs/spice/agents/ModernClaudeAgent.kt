package io.github.noailabs.spice.agents

import io.github.noailabs.spice.*
import io.github.noailabs.spice.config.*
import io.github.noailabs.spice.dsl.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * 🤖 Modern Claude Agent
 * 
 * Simplified Claude agent implementation using new DSL structure.
 * Clean, maintainable, and optimized for Mentat workflows.
 */

/**
 * Claude configuration
 */
data class ClaudeConfig(
    override val apiKey: String,
    override val model: String = "claude-3-sonnet-20240229",
    override val maxTokens: Int = 4096,
    override val temperature: Double = 0.7,
    override val systemPrompt: String = "You are Claude, a helpful AI assistant created by Anthropic."
) : LLMConfig

/**
 * Claude API client
 */
class ClaudeApiClient(config: ClaudeConfig) : AbstractLLMClient<ClaudeConfig>(config) {
    
    override suspend fun sendMessage(content: String): String = makeApiRequest(content)
    
    override fun buildRequestBody(content: String): JsonObject = buildJsonObject {
        put("model", config.model)
        put("max_tokens", config.maxTokens)
        put("temperature", config.temperature)
        put("system", config.systemPrompt)
        put("messages", buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("content", content)
            })
        })
    }
    
    override fun getApiEndpoint(): String = "https://api.anthropic.com/v1/messages"
    
    override fun getAuthHeader(): String = "x-api-key ${config.apiKey}"
    
    override fun extractResponseContent(response: JsonObject): String {
        val content = response["content"]?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content
        return content ?: "No response from Claude"
    }
    
    override suspend fun makeApiRequest(content: String): String {
        return try {
            val requestBody = buildRequestBody(content)
            
            val response: JsonObject = httpClient.post(getApiEndpoint()) {
                header("x-api-key", config.apiKey)
                header("anthropic-version", "2023-06-01")
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.body()
            
            extractResponseContent(response)
        } catch (e: Exception) {
            "Error calling Claude API: ${e.message}"
        }
    }
}

/**
 * Create modern Claude agent using DSL
 */
fun claudeAgent(
    id: String = "claude-agent-${System.currentTimeMillis()}",
    apiKey: String,
    model: String = "claude-3-sonnet-20240229",
    systemPrompt: String = "You are Claude, a helpful AI assistant.",
    debugEnabled: Boolean = false
): Agent {
    val config = ClaudeConfig(
        apiKey = apiKey,
        model = model,
        systemPrompt = systemPrompt
    )
    
    val agent = modernLLMAgent(id, config, ::ClaudeApiClient)
    
    return if (debugEnabled) {
        buildAgent {
            this.id = agent.id
            name = agent.name
            description = agent.description
            
            handle { comm ->
                println("[CLAUDE] Processing message: ${comm.content}")
                agent.processComm(comm)
            }
        }
    } else {
        agent
    }
}

/**
 * Create mock Claude agent for testing (no API calls)
 */
fun mockClaudeAgent(
    id: String = "mock-claude-${System.currentTimeMillis()}",
    personality: String = "helpful",
    debugEnabled: Boolean = false
): Agent {
    return buildAgent {
        this.id = id
        name = "Mock Claude"
        description = "Mock Claude agent for testing and development"
        
        if (debugEnabled) {
            debugMode(enabled = true, prefix = "[MOCK-CLAUDE]")
        }
        
        handle { comm ->
            // Simulate API delay
            delay(100)
            
            val response = when {
                comm.content.contains("hello", ignoreCase = true) -> 
                    "Hello! I'm Claude, how can I help you today?"
                comm.content.contains("code", ignoreCase = true) -> 
                    "I'd be happy to help you with coding! What programming task are you working on?"
                comm.content.contains("analysis", ignoreCase = true) -> 
                    "I can analyze that for you. Let me think through this systematically..."
                personality == "concise" -> 
                    "Got it. ${comm.content.take(20)}... - Here's my response."
                personality == "verbose" -> 
                    "Thank you for your question about '${comm.content}'. I've considered this carefully and here's my detailed response..."
                else -> 
                    "I understand you're asking about: ${comm.content}. Let me help you with that."
            }
            
            Comm(
                content = response,
                from = id,
                to = comm.from,
                type = CommType.TEXT,
                data = mapOf(
                    "mock" to "true",
                    "timestamp" to System.currentTimeMillis().toString()
                )
            )
        }
    }
}

/**
 * Claude agent builder for advanced configuration
 */
class ClaudeAgentBuilder {
    var id: String = "claude-${System.currentTimeMillis()}"
    var apiKey: String = ""
    var model: String = "claude-3-sonnet-20240229"
    var systemPrompt: String = "You are Claude, a helpful AI assistant."
    var debugEnabled: Boolean = false
    var maxTokens: Int = 4096
    var temperature: Double = 0.7
    
    fun build(): Agent {
        require(apiKey.isNotEmpty()) { "Claude API key is required" }
        
        return claudeAgent(
            id = id,
            apiKey = apiKey,
            model = model,
            systemPrompt = systemPrompt,
            debugEnabled = debugEnabled
        )
    }
}

/**
 * DSL for building Claude agents
 */
fun claude(init: ClaudeAgentBuilder.() -> Unit): Agent {
    val builder = ClaudeAgentBuilder()
    builder.init()
    return builder.build()
}

/**
 * Create Claude agent from SpiceConfig
 */
fun claudeAgentFromConfig(
    id: String = "claude-agent-${System.currentTimeMillis()}",
    configOverrides: (AnthropicConfig.() -> Unit)? = null
): Agent {
    val config = SpiceConfig.current().providers.getTyped<AnthropicConfig>("anthropic")
        ?: throw IllegalStateException("Anthropic configuration not found in SpiceConfig")
    
    // Apply any overrides
    configOverrides?.invoke(config)
    
    // Validate configuration
    config.validate()
    
    return claudeAgent(
        id = id,
        apiKey = config.apiKey,
        model = config.model,
        systemPrompt = config.systemPrompt,
        debugEnabled = SpiceConfig.current().debug.enabled
    )
}