package io.github.spice.agents

import io.github.spice.*
import io.github.spice.dsl.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * 🤖 Modern GPT Agent
 * 
 * Simplified GPT agent implementation using new DSL structure.
 * Clean, maintainable, and optimized for Mentat workflows.
 */

/**
 * GPT configuration
 */
data class GPTConfig(
    override val apiKey: String,
    override val model: String = "gpt-4",
    override val maxTokens: Int = 4096,
    override val temperature: Double = 0.7,
    override val systemPrompt: String = "You are a helpful AI assistant."
) : LLMConfig

/**
 * OpenAI API client
 */
class OpenAIApiClient(config: GPTConfig) : AbstractLLMClient<GPTConfig>(config) {
    
    override suspend fun sendMessage(content: String): String = makeApiRequest(content)
    
    override fun buildRequestBody(content: String): JsonObject = buildJsonObject {
        put("model", config.model)
        put("max_tokens", config.maxTokens)
        put("temperature", config.temperature)
        put("messages", buildJsonArray {
            add(buildJsonObject {
                put("role", "system")
                put("content", config.systemPrompt)
            })
            add(buildJsonObject {
                put("role", "user")
                put("content", content)
            })
        })
    }
    
    override fun getApiEndpoint(): String = "https://api.openai.com/v1/chat/completions"
    
    override fun getAuthHeader(): String = "Bearer ${config.apiKey}"
    
    override fun extractResponseContent(response: JsonObject): String {
        val content = response["choices"]?.jsonArray?.get(0)?.jsonObject
            ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
        return content ?: "No response from GPT"
    }
}

/**
 * Create modern GPT agent using DSL
 */
fun gptAgent(
    id: String = "gpt-agent-${System.currentTimeMillis()}",
    apiKey: String,
    model: String = "gpt-4",
    systemPrompt: String = "You are a helpful AI assistant.",
    debugEnabled: Boolean = false
): Agent {
    val config = GPTConfig(
        apiKey = apiKey,
        model = model,
        systemPrompt = systemPrompt
    )
    
    val agent = modernLLMAgent(id, config, ::OpenAIApiClient)
    
    return if (debugEnabled) {
        buildAgent {
            this.id = agent.id
            name = agent.name
            description = agent.description
            
            handle { comm ->
                println("[GPT] Processing message: ${comm.content}")
                agent.processComm(comm)
            }
        }
    } else {
        agent
    }
}

/**
 * Create mock GPT agent for testing (no API calls)
 */
fun mockGPTAgent(
    id: String = "mock-gpt-${System.currentTimeMillis()}",
    personality: String = "professional",
    debugEnabled: Boolean = false
): Agent {
    return buildAgent {
        this.id = id
        name = "Mock GPT"
        description = "Mock GPT agent for testing and development"
        
        if (debugEnabled) {
            debugMode(enabled = true, prefix = "[MOCK-GPT]")
        }
        
        handle { comm ->
            // Simulate API delay
            delay(150)
            
            val response = when {
                comm.content.contains("hello", ignoreCase = true) -> 
                    "Hello! How can I assist you today?"
                comm.content.contains("explain", ignoreCase = true) -> 
                    "Let me explain that for you. ${comm.content.substringAfter("explain").trim()} involves..."
                comm.content.contains("code", ignoreCase = true) -> 
                    "Here's a solution for your coding request: ```kotlin\n// Your code here\n```"
                personality == "technical" -> 
                    "Technical analysis: ${comm.content}. Implementation details follow..."
                personality == "casual" -> 
                    "Hey! So about ${comm.content} - here's what I think..."
                else -> 
                    "I understand you're asking about: ${comm.content}. Here's my response..."
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
 * GPT agent builder for advanced configuration
 */
class GPTAgentBuilder {
    var id: String = "gpt-${System.currentTimeMillis()}"
    var apiKey: String = ""
    var model: String = "gpt-4"
    var systemPrompt: String = "You are a helpful AI assistant."
    var debugEnabled: Boolean = false
    var maxTokens: Int = 4096
    var temperature: Double = 0.7
    
    fun build(): Agent {
        require(apiKey.isNotEmpty()) { "OpenAI API key is required" }
        
        return gptAgent(
            id = id,
            apiKey = apiKey,
            model = model,
            systemPrompt = systemPrompt,
            debugEnabled = debugEnabled
        )
    }
}

/**
 * DSL for building GPT agents
 */
fun gpt(init: GPTAgentBuilder.() -> Unit): Agent {
    val builder = GPTAgentBuilder()
    builder.init()
    return builder.build()
}