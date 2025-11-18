package io.github.noailabs.spice.springboot.ai.adapters

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.AgentRuntime
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.StreamingChatModel
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.tool.function.FunctionToolCallback
import reactor.core.publisher.Mono

/**
 * 🔌 ChatModel to Agent Adapter
 *
 * Wraps Spring AI's ChatModel as a Spice Agent.
 * Implements the Spice Agent interface while delegating to Spring AI's ChatModel.
 *
 * **Features:**
 * - Synchronous and streaming support
 * - Tool (FunctionToolCallback) integration
 * - Metadata preservation
 * - Correlation tracking
 * - Error handling with SpiceResult
 *
 * **Usage:**
 * ```kotlin
 * val chatModel = OpenAiChatModel(apiKey, model)
 * val agent = ChatModelToAgentAdapter(
 *     chatModel = chatModel,
 *     agentId = "gpt-4-agent",
 *     agentName = "GPT-4 Assistant",
 *     systemPrompt = "You are a helpful assistant"
 * )
 *
 * val response = agent.processMessage(message)
 * ```
 *
 * @property chatModel Spring AI ChatModel instance
 * @property agentId Unique agent identifier
 * @property agentName Human-readable agent name
 * @property agentDescription Agent description
 * @property systemPrompt System prompt prepended to all conversations
 * @property defaultOptions Default chat options (temperature, max tokens, etc.)
 * @property functionCallbacks Tool functions available to the agent
 * @property capabilities Agent capabilities list
 *
 * @since 1.0.0
 * @author Spice Framework
 */
class ChatModelToAgentAdapter(
    private val chatModel: ChatModel,
    override val id: String,
    override val name: String,
    override val description: String,
    private val systemPrompt: String? = null,
    private val defaultOptions: ChatOptions? = null,
    private val functionCallbacks: List<FunctionToolCallback<*, *>> = emptyList(),
    override val capabilities: List<String> = listOf("chat", "completion")
) : Agent {

    /**
     * Process a message using Spring AI ChatModel
     *
     * @param message Input message
     * @return Response message or error
     */
    override suspend fun processMessage(message: SpiceMessage): SpiceResult<SpiceMessage> {
        return try {
            // Convert SpiceMessage to Spring AI Prompt
            val prompt = MessageAdapter.toPrompt(
                message = message,
                systemPrompt = systemPrompt,
                options = defaultOptions,
                functionCallbacks = functionCallbacks
            )

            // Call Spring AI ChatModel
            val response = if (chatModel is reactor.core.publisher.Mono<*>) {
                // If chatModel.call returns Mono, await it
                @Suppress("UNCHECKED_CAST")
                (chatModel.call(prompt) as Mono<org.springframework.ai.chat.model.ChatResponse>)
                    .awaitSingleOrNull()
                    ?: return SpiceResult.failure(SpiceError.executionError("ChatModel returned null response"))
            } else {
                // Synchronous call
                chatModel.call(prompt)
            }

            // Convert Spring AI ChatResponse to SpiceMessage
            val spiceMessage = MessageAdapter.toSpiceMessage(
                response = response,
                originalMessage = message,
                agentId = id
            )

            SpiceResult.success(spiceMessage)
        } catch (e: Exception) {
            SpiceResult.failure(
                SpiceError.executionError(
                    message = "Failed to process message with Spring AI: ${e.message}",
                    cause = e
                )
            )
        }
    }

    /**
     * Process a message with runtime context
     *
     * @param message Input message
     * @param runtime Agent runtime
     * @return Response message or error
     */
    override suspend fun processMessage(
        message: SpiceMessage,
        runtime: AgentRuntime
    ): SpiceResult<SpiceMessage> {
        // For now, delegate to basic processMessage
        // Future enhancement: Use runtime for tool execution, agent chaining, etc.
        return processMessage(message)
    }

    /**
     * Get tools available to this agent
     *
     * @return List of tools (empty - Spring AI uses FunctionCallback)
     */
    override fun getTools(): List<Tool> {
        // Spring AI uses FunctionCallback instead of Spice Tool
        // Tool conversion handled by ToolAdapter
        return emptyList()
    }

    /**
     * Check if agent can handle the message
     *
     * @param message Message to check
     * @return Always true (delegates to ChatModel)
     */
    override fun canHandle(message: SpiceMessage): Boolean = true

    /**
     * Check if agent is ready
     *
     * @return Always true (ChatModel is stateless)
     */
    override fun isReady(): Boolean = true
}

/**
 * 🌊 Streaming Chat Model to Agent Adapter
 *
 * Wraps Spring AI's StreamingChatModel as a Spice Agent with streaming support.
 *
 * **Usage:**
 * ```kotlin
 * val streamingModel = OpenAiChatModel(apiKey, model)
 * val agent = StreamingChatModelToAgentAdapter(
 *     chatModel = streamingModel,
 *     agentId = "gpt-4-streaming",
 *     agentName = "GPT-4 Streaming Assistant"
 * )
 * ```
 *
 * @since 1.0.0
 * @author Spice Framework
 */
class StreamingChatModelToAgentAdapter(
    private val chatModel: StreamingChatModel,
    override val id: String,
    override val name: String,
    override val description: String,
    private val systemPrompt: String? = null,
    private val defaultOptions: ChatOptions? = null,
    private val functionCallbacks: List<FunctionToolCallback<*, *>> = emptyList(),
    override val capabilities: List<String> = listOf("chat", "completion", "streaming")
) : Agent {

    /**
     * Process a message using Spring AI StreamingChatModel
     * Collects all streaming chunks into a single response
     *
     * @param message Input message
     * @return Complete response message or error
     */
    override suspend fun processMessage(message: SpiceMessage): SpiceResult<SpiceMessage> {
        return try {
            // Convert SpiceMessage to Spring AI Prompt
            val prompt = MessageAdapter.toPrompt(
                message = message,
                systemPrompt = systemPrompt,
                options = defaultOptions,
                functionCallbacks = functionCallbacks
            )

            // Call Spring AI StreamingChatModel and collect all chunks
            val chunks = mutableListOf<String>()
            chatModel.stream(prompt)
                .doOnNext { chatResponse ->
                    chatResponse.results.firstOrNull()?.output?.text?.let { content ->
                        chunks.add(content)
                    }
                }
                .collectList()
                .awaitSingle()

            // Combine all chunks into single content
            val fullContent = chunks.joinToString("")

            // Create response message
            val spiceMessage = message.reply(
                content = fullContent,
                from = id
            ).copy(
                data = message.data + mapOf("streaming" to true)
            )

            SpiceResult.success(spiceMessage)
        } catch (e: Exception) {
            SpiceResult.failure(
                SpiceError.executionError(
                    message = "Failed to process streaming message: ${e.message}",
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

    override fun getTools(): List<Tool> = emptyList()

    override fun canHandle(message: SpiceMessage): Boolean = true

    override fun isReady(): Boolean = true
}
