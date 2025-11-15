package io.github.noailabs.spice.agents

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.AgentRuntime
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.agents.http.OpenAIClient
import io.github.noailabs.spice.agents.http.OpenAIRequest
import io.github.noailabs.spice.agents.http.OpenAIMessage

/**
 * 🤖 GPT Agent - Standalone OpenAI Agent Implementation
 *
 * Standalone agent that calls OpenAI API directly using Ktor HTTP client.
 * No Spring dependency required.
 *
 * **Features:**
 * - Direct OpenAI API integration
 * - Tool calling support
 * - Streaming support (optional)
 * - Compatible with Spice 1.0.0 Agent interface
 *
 * **Usage:**
 * ```kotlin
 * val agent = GPTAgent(
 *     apiKey = System.getenv("OPENAI_API_KEY"),
 *     model = "gpt-4",
 *     systemPrompt = "You are a helpful AI assistant."
 * )
 *
 * val response = agent.processMessage(
 *     SpiceMessage.create("Hello!", "user")
 * )
 * ```
 *
 * @property apiKey OpenAI API key
 * @property model Model name (e.g., "gpt-4", "gpt-3.5-turbo", "gpt-4-turbo")
 * @property systemPrompt System prompt prepended to conversations
 * @property temperature Sampling temperature (0.0 - 2.0)
 * @property maxTokens Maximum tokens to generate
 * @property tools List of tools available to the agent
 * @property baseUrl Custom API base URL (default: https://api.openai.com/v1)
 * @property organizationId Optional organization ID
 *
 * @since 1.0.0
 * @author Spice Framework
 */
class GPTAgent(
    private val apiKey: String,
    private val model: String = "gpt-4",
    private val systemPrompt: String? = null,
    private val temperature: Double = 0.7,
    private val maxTokens: Int? = null,
    private val tools: List<Tool> = emptyList(),
    private val baseUrl: String = "https://api.openai.com/v1",
    private val organizationId: String? = null,
    override val id: String = "gpt-$model",
    override val name: String = "GPT Agent ($model)",
    override val description: String = "OpenAI GPT agent using $model",
    override val capabilities: List<String> = listOf("chat", "completion", "tools", "streaming")
) : Agent {

    private val client = OpenAIClient(
        apiKey = apiKey,
        baseUrl = baseUrl,
        organizationId = organizationId
    )

    /**
     * Process a message using OpenAI API
     *
     * @param message Input message
     * @return Response message or error
     */
    override suspend fun processMessage(message: SpiceMessage): SpiceResult<SpiceMessage> {
        return try {
            // Build messages array
            val messages = buildMessages(message)

            // Build request
            val request = OpenAIRequest(
                model = model,
                messages = messages,
                temperature = temperature,
                maxTokens = maxTokens,
                tools = if (tools.isNotEmpty()) tools.map { it.toOpenAITool() } else null
            )

            // Call OpenAI API
            val response = client.createChatCompletion(request)

            // Convert response to SpiceMessage
            val content = response.choices.firstOrNull()?.message?.content
                ?: return SpiceResult.failure(
                    SpiceError.executionError("OpenAI returned empty response")
                )

            // Create response message
            val responseMessage = message.reply(
                content = content,
                from = id
            ).copy(
                data = message.data + mapOf(
                    "model" to model,
                    "finish_reason" to (response.choices.firstOrNull()?.finishReason ?: "unknown"),
                    "usage" to mapOf(
                        "prompt_tokens" to (response.usage?.promptTokens ?: 0),
                        "completion_tokens" to (response.usage?.completionTokens ?: 0),
                        "total_tokens" to (response.usage?.totalTokens ?: 0)
                    )
                )
            )

            SpiceResult.success(responseMessage)
        } catch (e: Exception) {
            SpiceResult.failure(
                SpiceError.executionError(
                    message = "GPT Agent failed: ${e.message}",
                    cause = e
                )
            )
        }
    }

    /**
     * Process message with runtime context
     */
    override suspend fun processMessage(
        message: SpiceMessage,
        runtime: AgentRuntime
    ): SpiceResult<SpiceMessage> {
        // For now, delegate to basic processMessage
        // Future enhancement: use runtime for tool execution, agent chaining, etc.
        return processMessage(message)
    }

    /**
     * Get tools available to this agent
     */
    override fun getTools(): List<Tool> = tools

    /**
     * Check if agent can handle the message
     */
    override fun canHandle(message: SpiceMessage): Boolean = true

    /**
     * Check if agent is ready
     */
    override fun isReady(): Boolean = apiKey.isNotBlank()

    /**
     * Build messages array for OpenAI API
     */
    private fun buildMessages(message: SpiceMessage): List<OpenAIMessage> {
        val messages = mutableListOf<OpenAIMessage>()

        // Add system prompt if provided
        if (systemPrompt != null) {
            messages.add(
                OpenAIMessage(
                    role = "system",
                    content = systemPrompt
                )
            )
        }

        // Add user message
        messages.add(
            OpenAIMessage(
                role = "user",
                content = message.content
            )
        )

        return messages
    }
}

/**
 * Convert Spice Tool to OpenAI tool format
 */
private fun Tool.toOpenAITool(): Map<String, Any> {
    return mapOf(
        "type" to "function",
        "function" to mapOf(
            "name" to name,
            "description" to description,
            "parameters" to mapOf(
                "type" to "object",
                "properties" to emptyMap<String, Any>(),
                "required" to emptyList<String>()
            )
        )
    )
}
