package io.github.noailabs.spice.agents

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.AgentRuntime
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.agents.http.AnthropicClient
import io.github.noailabs.spice.agents.http.AnthropicRequest
import io.github.noailabs.spice.agents.http.AnthropicMessage

/**
 * 🤖 Claude Agent - Standalone Anthropic Agent Implementation
 *
 * Standalone agent that calls Anthropic API directly using Ktor HTTP client.
 * No Spring dependency required.
 *
 * **Features:**
 * - Direct Anthropic API integration
 * - Tool calling support
 * - Long context support (200K tokens)
 * - Compatible with Spice 1.0.0 Agent interface
 *
 * **Usage:**
 * ```kotlin
 * val agent = ClaudeAgent(
 *     apiKey = System.getenv("ANTHROPIC_API_KEY"),
 *     model = "claude-3-5-sonnet-20241022",
 *     systemPrompt = "You are a helpful AI assistant."
 * )
 *
 * val response = agent.processMessage(
 *     SpiceMessage.create("Hello!", "user")
 * )
 * ```
 *
 * @property apiKey Anthropic API key
 * @property model Model name (default: "claude-3-5-sonnet-20241022")
 * @property systemPrompt System prompt prepended to conversations
 * @property temperature Sampling temperature (0.0 - 1.0)
 * @property maxTokens Maximum tokens to generate
 * @property tools List of tools available to the agent
 * @property baseUrl Custom API base URL
 *
 * @since 1.0.0
 * @author Spice Framework
 */
class ClaudeAgent(
    private val apiKey: String,
    private val model: String = "claude-3-5-sonnet-20241022",
    private val systemPrompt: String? = null,
    private val temperature: Double = 0.7,
    private val maxTokens: Int = 1024,
    private val tools: List<Tool> = emptyList(),
    private val baseUrl: String = "https://api.anthropic.com",
    override val id: String = "claude-$model",
    override val name: String = "Claude Agent ($model)",
    override val description: String = "Anthropic Claude agent using $model",
    override val capabilities: List<String> = listOf("chat", "completion", "tools", "long-context")
) : Agent {

    private val client = AnthropicClient(
        apiKey = apiKey,
        baseUrl = baseUrl
    )

    /**
     * Process a message using Anthropic API
     *
     * @param message Input message
     * @return Response message or error
     */
    override suspend fun processMessage(message: SpiceMessage): SpiceResult<SpiceMessage> {
        return try {
            // Build messages array
            val messages = listOf(
                AnthropicMessage(
                    role = "user",
                    content = message.content
                )
            )

            // Build request
            val request = AnthropicRequest(
                model = model,
                messages = messages,
                maxTokens = maxTokens,
                temperature = temperature,
                system = systemPrompt
            )

            // Call Anthropic API
            val response = client.createMessage(request)

            // Extract content
            val content = response.content.firstOrNull()?.text
                ?: return SpiceResult.failure(
                    SpiceError.executionError("Anthropic returned empty response")
                )

            // Create response message
            val responseMessage = message.reply(
                content = content,
                from = id
            ).copy(
                data = message.data + mapOf(
                    "model" to model,
                    "stop_reason" to (response.stopReason ?: "unknown"),
                    "usage" to mapOf(
                        "input_tokens" to response.usage.inputTokens,
                        "output_tokens" to response.usage.outputTokens,
                        "total_tokens" to (response.usage.inputTokens + response.usage.outputTokens)
                    )
                )
            )

            SpiceResult.success(responseMessage)
        } catch (e: Exception) {
            SpiceResult.failure(
                SpiceError.executionError(
                    message = "Claude Agent failed: ${e.message}",
                    cause = e
                )
            )
        }
    }

    override suspend fun processMessage(
        message: SpiceMessage,
        runtime: AgentRuntime
    ): SpiceResult<SpiceMessage> {
        return processMessage(message)
    }

    override fun getTools(): List<Tool> = tools

    override fun canHandle(message: SpiceMessage): Boolean = true

    override fun isReady(): Boolean = apiKey.isNotBlank()
}
