package io.github.noailabs.spice.toolspec

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * 🔧 OpenAI Tool Call Specification
 *
 * Industry-standard tool calling schema compatible with:
 * - OpenAI Function Calling API
 * - Anthropic Claude Tool Use
 * - LangChain Tool Protocol
 * - LlamaIndex Agent Tools
 *
 * **Standard Structure:**
 * ```json
 * {
 *   "id": "call_abc123",
 *   "type": "function",
 *   "function": {
 *     "name": "request_user_selection",
 *     "arguments": {...}
 *   }
 * }
 * ```
 *
 * **Common Use Cases:**
 * - Selection: `request_user_selection`
 * - Confirmation: `request_user_confirmation`
 * - HITL: `request_user_input`
 * - Completion: `workflow_completed`
 * - Tool Message: `tool_message`
 * - Error: `system_error`
 *
 * **References:**
 * - [OpenAI Function Calling](https://platform.openai.com/docs/guides/function-calling)
 * - [Anthropic Tool Use](https://docs.anthropic.com/claude/docs/tool-use)
 *
 * @author Spice Framework
 * @since 0.10.0
 */
@Serializable
data class OAIToolCall(
    /**
     * Unique identifier for this tool call
     * Format: call_{random_id}
     */
    val id: String = generateToolCallId(),

    /**
     * Type of the tool call
     * Always "function" per OpenAI spec
     */
    val type: String = "function",

    /**
     * Function call details
     */
    val function: ToolCallFunction
) {
    companion object {
        /**
         * Generate unique tool call ID
         * Format: call_abc123def456ghi789012
         */
        fun generateToolCallId(): String {
            return "call_${UUID.randomUUID().toString().replace("-", "").take(24)}"
        }

        /**
         * Standard tool names used in Spice workflows
         */
        object ToolNames {
            const val REQUEST_USER_SELECTION = "request_user_selection"
            const val REQUEST_USER_CONFIRMATION = "request_user_confirmation"
            const val REQUEST_USER_INPUT = "request_user_input"
            const val WORKFLOW_COMPLETED = "workflow_completed"
            const val TOOL_MESSAGE = "tool_message"
            const val SYSTEM_ERROR = "system_error"
        }

        /**
         * Create Selection ToolCall
         *
         * @param items List of selection items with id, name, description
         * @param promptMessage Message to display to user
         * @param selectionType Type of selection (single, multiple, etc.)
         * @param metadata Additional metadata
         */
        fun selection(
            items: List<Map<String, Any?>>,
            promptMessage: String,
            selectionType: String = "single",
            metadata: Map<String, Any> = emptyMap()
        ): OAIToolCall {
            return OAIToolCall(
                function = ToolCallFunction(
                    name = ToolNames.REQUEST_USER_SELECTION,
                    arguments = buildMap {
                        put("items", items)
                        put("prompt_message", promptMessage)
                        put("selection_type", selectionType)
                        if (metadata.isNotEmpty()) {
                            put("metadata", metadata)
                        }
                    }
                )
            )
        }

        /**
         * Create Confirmation ToolCall
         *
         * @param message Confirmation message
         * @param options List of options (default: ["Yes", "No"])
         * @param confirmationType Type of confirmation
         * @param metadata Additional metadata
         */
        fun confirmation(
            message: String,
            options: List<String> = listOf("Yes", "No"),
            confirmationType: String = "general",
            metadata: Map<String, Any> = emptyMap()
        ): OAIToolCall {
            return OAIToolCall(
                function = ToolCallFunction(
                    name = ToolNames.REQUEST_USER_CONFIRMATION,
                    arguments = buildMap {
                        put("message", message)
                        put("options", options)
                        put("confirmation_type", confirmationType)
                        if (metadata.isNotEmpty()) {
                            put("metadata", metadata)
                        }
                    }
                )
            )
        }

        /**
         * Create HITL (Human-in-the-Loop) ToolCall
         *
         * @param type Type of input needed
         * @param question Question to ask user
         * @param context Additional context
         */
        fun hitl(
            type: String,
            question: String,
            context: Map<String, Any> = emptyMap()
        ): OAIToolCall {
            return OAIToolCall(
                function = ToolCallFunction(
                    name = ToolNames.REQUEST_USER_INPUT,
                    arguments = buildMap {
                        put("type", type)
                        put("question", question)
                        if (context.isNotEmpty()) {
                            put("context", context)
                        }
                    }
                )
            )
        }

        /**
         * Create Completion ToolCall
         *
         * @param message Completion message
         * @param workflowId Optional workflow ID
         * @param reasoning Optional reasoning for completion
         * @param metadata Additional metadata
         */
        fun completion(
            message: String,
            workflowId: String? = null,
            reasoning: String? = null,
            metadata: Map<String, Any> = emptyMap()
        ): OAIToolCall {
            return OAIToolCall(
                function = ToolCallFunction(
                    name = ToolNames.WORKFLOW_COMPLETED,
                    arguments = buildMap {
                        put("message", message)
                        if (workflowId != null) {
                            put("workflow_id", workflowId)
                        }
                        if (reasoning != null) {
                            put("reasoning", reasoning)
                        }
                        if (metadata.isNotEmpty()) {
                            put("metadata", metadata)
                        }
                    }
                )
            )
        }

        /**
         * Create ToolMessage ToolCall
         *
         * @param message Tool message content
         * @param toolName Name of the tool
         * @param isIntermediate Whether this is an intermediate message
         * @param metadata Additional metadata
         */
        fun toolMessage(
            message: String,
            toolName: String,
            isIntermediate: Boolean = true,
            metadata: Map<String, Any> = emptyMap()
        ): OAIToolCall {
            return OAIToolCall(
                function = ToolCallFunction(
                    name = ToolNames.TOOL_MESSAGE,
                    arguments = buildMap {
                        put("message", message)
                        put("tool_name", toolName)
                        put("is_intermediate", isIntermediate)
                        if (metadata.isNotEmpty()) {
                            put("metadata", metadata)
                        }
                    }
                )
            )
        }

        /**
         * Create Error ToolCall
         *
         * @param message Error message
         * @param errorType Type of error
         * @param isRecoverable Whether the error is recoverable
         */
        fun error(
            message: String,
            errorType: String,
            isRecoverable: Boolean = true
        ): OAIToolCall {
            return OAIToolCall(
                function = ToolCallFunction(
                    name = ToolNames.SYSTEM_ERROR,
                    arguments = mapOf(
                        "message" to message,
                        "error_type" to errorType,
                        "is_recoverable" to isRecoverable
                    )
                )
            )
        }
    }

    /**
     * Get function name
     */
    fun getFunctionName(): String = function.name

    /**
     * Get arguments
     */
    fun getArguments(): Map<String, Any> = function.arguments

    /**
     * Convert to Map (for serialization)
     */
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "type" to type,
        "function" to function.toMap()
    )
}

/**
 * Tool Call Function
 *
 * Represents the function being called with its arguments
 */
@Serializable
data class ToolCallFunction(
    /**
     * Function name
     * Examples: request_user_selection, workflow_completed
     */
    val name: String,

    /**
     * Function arguments (JSON-serializable map)
     * Note: Uses @Contextual to handle polymorphic Any type
     */
    val arguments: Map<String, @Contextual Any> = emptyMap()
) {
    /**
     * Convert to Map
     */
    fun toMap(): Map<String, Any> = mapOf(
        "name" to name,
        "arguments" to arguments
    )

    /**
     * Get argument value
     */
    fun getArgument(key: String): Any? = arguments[key]

    /**
     * Get argument as String
     */
    fun getArgumentString(key: String): String? = arguments[key]?.toString()

    /**
     * Get argument as List
     */
    @Suppress("UNCHECKED_CAST")
    fun getArgumentList(key: String): List<Any>? = arguments[key] as? List<Any>

    /**
     * Get argument as Map
     */
    @Suppress("UNCHECKED_CAST")
    fun getArgumentMap(key: String): Map<String, Any>? = arguments[key] as? Map<String, Any>

    /**
     * Get argument as Boolean
     */
    fun getArgumentBoolean(key: String): Boolean? = when (val value = arguments[key]) {
        is Boolean -> value
        is String -> value.toBoolean()
        else -> null
    }

    /**
     * Get argument as Int
     */
    fun getArgumentInt(key: String): Int? = when (val value = arguments[key]) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }
}

/**
 * ToolCall Builder (DSL)
 *
 * Provides fluent API for building tool calls
 *
 * Example:
 * ```kotlin
 * val toolCall = toolCall {
 *     functionName("request_user_selection")
 *     argument("items", listOf(...))
 *     argument("prompt_message", "Select an option")
 * }
 * ```
 */
class ToolCallBuilder {
    private var id: String? = null
    private var type: String = "function"
    private var functionName: String = ""
    private val arguments = mutableMapOf<String, Any>()

    /**
     * Set custom tool call ID
     */
    fun id(value: String) {
        id = value
    }

    /**
     * Set function name
     */
    fun functionName(value: String) {
        functionName = value
    }

    /**
     * Add single argument
     */
    fun argument(key: String, value: Any) {
        arguments[key] = value
    }

    /**
     * Add multiple arguments
     */
    fun arguments(values: Map<String, Any>) {
        arguments.putAll(values)
    }

    /**
     * Build the tool call
     */
    fun build(): OAIToolCall {
        require(functionName.isNotBlank()) { "Function name is required" }

        return OAIToolCall(
            id = id ?: OAIToolCall.generateToolCallId(),
            type = type,
            function = ToolCallFunction(
                name = functionName,
                arguments = arguments
            )
        )
    }
}

/**
 * DSL function for creating tool calls
 *
 * Example:
 * ```kotlin
 * val toolCall = toolCall {
 *     functionName("request_user_selection")
 *     argument("items", listOf(...))
 * }
 * ```
 */
fun toolCall(builder: ToolCallBuilder.() -> Unit): OAIToolCall {
    return ToolCallBuilder().apply(builder).build()
}
