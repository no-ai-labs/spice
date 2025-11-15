package io.github.noailabs.spice.springboot.ai.adapters

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.toolspec.OAIToolCall
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.tool.function.FunctionToolCallback

/**
 * 🔄 Message Adapter: Bidirectional conversion between SpiceMessage and Spring AI Message
 *
 * Handles conversion of:
 * - SpiceMessage → Spring AI Prompt/Message
 * - Spring AI ChatResponse → SpiceMessage
 * - OAIToolCall → Spring AI FunctionToolCallback
 *
 * **Key Mappings:**
 * - SpiceMessage.content → UserMessage.content
 * - SpiceMessage.metadata → Message.metadata
 * - SpiceMessage.toolCalls → ChatOptions.functionCallbacks
 *
 * @since 1.0.0
 * @author Spice Framework
 */
object MessageAdapter {

    /**
     * Convert SpiceMessage to Spring AI Prompt
     *
     * @param message Spice message
     * @param systemPrompt Optional system prompt (prepended if provided)
     * @param options Chat options (temperature, max tokens, etc.)
     * @param functionCallbacks Tool functions available to the agent
     * @return Spring AI Prompt
     */
    fun toPrompt(
        message: SpiceMessage,
        systemPrompt: String? = null,
        options: ChatOptions? = null,
        functionCallbacks: List<FunctionToolCallback<*, *>> = emptyList()
    ): Prompt {
        val messages = mutableListOf<Message>()

        // Add system message if provided
        if (systemPrompt != null) {
            messages.add(SystemMessage(systemPrompt))
        }

        // Add user message
        messages.add(UserMessage(message.content))

        // Use provided options (function callbacks are registered at ChatModel level)
        val finalOptions = options

        return Prompt(messages, finalOptions)
    }

    /**
     * Convert Spring AI ChatResponse to SpiceMessage
     *
     * @param response Spring AI chat response
     * @param originalMessage Original Spice message (for context preservation)
     * @param agentId Agent ID that generated the response
     * @return SpiceMessage with response content
     */
    fun toSpiceMessage(
        response: ChatResponse,
        originalMessage: SpiceMessage,
        agentId: String
    ): SpiceMessage {
        val generation = response.results.firstOrNull() ?: throw IllegalStateException("ChatResponse has no result")
        val output = generation.output ?: throw IllegalStateException("Generation has no output")

        // Extract content
        val content = output.text ?: ""

        // Extract metadata from response (simplified - Spring AI metadata may vary)
        val responseMetadata: Map<String, Any> = emptyMap()

        // Preserve original correlation ID and causation
        return originalMessage.reply(
            content = content,
            from = agentId
        ).copy(
            metadata = originalMessage.metadata + responseMetadata,
            data = originalMessage.data + mapOf(
                "spring_ai_model" to (responseMetadata["model"] ?: "unknown"),
                "spring_ai_finish_reason" to (generation.metadata?.finishReason ?: "unknown"),
                "spring_ai_usage" to (responseMetadata["usage"] ?: emptyMap<String, Any>())
            )
        )
    }

    /**
     * Convert Spring AI AssistantMessage to SpiceMessage
     *
     * @param assistantMessage Spring AI assistant message
     * @param correlationId Correlation ID for the message
     * @param agentId Agent ID
     * @return SpiceMessage
     */
    fun assistantMessageToSpiceMessage(
        assistantMessage: AssistantMessage,
        correlationId: String,
        agentId: String
    ): SpiceMessage {
        return SpiceMessage.create(
            content = assistantMessage.text ?: "",
            from = agentId,
            correlationId = correlationId
        )
    }

    /**
     * Convert OAIToolCall to Spring AI function call format
     *
     * Note: Spring AI uses FunctionCallback registration at ChatModel level,
     * not individual tool call conversion. This method extracts function names
     * for reference.
     *
     * @param toolCall OAI tool call
     * @return Function name to be called
     */
    fun extractFunctionName(toolCall: OAIToolCall): String {
        return toolCall.function.name
    }

    /**
     * Extract tool calls from SpiceMessage
     *
     * @param message Spice message
     * @return List of function names to be called
     */
    fun extractToolCalls(message: SpiceMessage): List<String> {
        return message.toolCalls.map { it.function.name }
    }
}
