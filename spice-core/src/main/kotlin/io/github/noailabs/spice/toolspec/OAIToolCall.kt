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
            // Inbound events (user → system)
            const val USER_INPUT = "user_input"
            const val USER_RESPONSE = "user_response"

            // Outbound requests (system → user)
            const val REQUEST_USER_SELECTION = "request_user_selection"
            const val REQUEST_USER_CONFIRMATION = "request_user_confirmation"
            const val REQUEST_USER_INPUT = "request_user_input"

            // HITL Tool requests (1.0.6+)
            const val HITL_REQUEST_INPUT = "hitl_request_input"
            const val HITL_REQUEST_SELECTION = "hitl_request_selection"
            const val HITL_REQUEST_QUANTITY = "hitl_request_quantity"
            const val HITL_REQUEST_INFO = "hitl_request_info"
            const val HITL_REQUEST_ESCALATE = "hitl_request_escalate"

            // System events
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

        /**
         * Create User Input ToolCall
         *
         * Represents user-initiated input events (chat messages, form submissions, etc.)
         *
         * @param text User's text input
         * @param metadata Additional metadata (userId, sessionId, etc.)
         * @param inputType Type of input (chat, form, voice, attachment, webhook)
         */
        fun userInput(
            text: String,
            metadata: Map<String, Any> = emptyMap(),
            inputType: String = "chat"
        ): OAIToolCall {
            return OAIToolCall(
                function = ToolCallFunction(
                    name = ToolNames.USER_INPUT,
                    arguments = buildMap {
                        put("text", text)
                        put("input_type", inputType)
                        if (metadata.isNotEmpty()) {
                            put("metadata", metadata)
                        }
                    }
                )
            )
        }

        /**
         * Create User Response ToolCall
         *
         * Represents user response to HITL requests (selections, confirmations, etc.)
         *
         * @param text User's text response (optional)
         * @param structuredData Structured response data (selections, form data, etc.)
         * @param metadata Additional metadata
         * @param responseType Type of response (selection, confirmation, text, form)
         */
        fun userResponse(
            text: String? = null,
            structuredData: Map<String, Any>? = null,
            metadata: Map<String, Any> = emptyMap(),
            responseType: String = "text"
        ): OAIToolCall {
            return OAIToolCall(
                function = ToolCallFunction(
                    name = ToolNames.USER_RESPONSE,
                    arguments = buildMap {
                        if (text != null) {
                            put("text", text)
                        }
                        if (structuredData != null) {
                            put("structured_data", structuredData)
                        }
                        put("response_type", responseType)
                        if (metadata.isNotEmpty()) {
                            put("metadata", metadata)
                        }
                    }
                )
            )
        }

        // ============================================================
        // HITL Tool Factory Methods (1.0.6+)
        // ============================================================

        /**
         * Create HITL Input Request ToolCall
         *
         * Used by HitlRequestInputTool to request free-form text input from user.
         * The tool_call_id is stable across retries (format: hitl_{runId}_{nodeId}).
         *
         * @param toolCallId Stable identifier for this HITL request
         * @param prompt Message displayed to the user
         * @param runId Graph run ID for checkpoint correlation
         * @param nodeId Node ID that initiated this request
         * @param graphId Optional graph ID for context
         * @param validationRules Optional validation rules (e.g., min/max length, regex)
         * @param timeout Optional timeout in milliseconds
         * @param metadata Additional custom metadata
         */
        fun hitlInput(
            toolCallId: String,
            prompt: String,
            runId: String,
            nodeId: String,
            graphId: String? = null,
            validationRules: Map<String, Any> = emptyMap(),
            timeout: Long? = null,
            metadata: Map<String, Any> = emptyMap()
        ): OAIToolCall {
            return OAIToolCall(
                id = toolCallId,
                function = ToolCallFunction(
                    name = ToolNames.HITL_REQUEST_INPUT,
                    arguments = buildMap {
                        put("tool_call_id", toolCallId)
                        put("prompt", prompt)
                        put("hitl_type", "input")
                        put("run_id", runId)
                        put("node_id", nodeId)
                        if (graphId != null) {
                            put("graph_id", graphId)
                        }
                        if (validationRules.isNotEmpty()) {
                            put("validation_rules", validationRules)
                        }
                        if (timeout != null) {
                            put("timeout", timeout)
                        }
                        if (metadata.isNotEmpty()) {
                            put("metadata", metadata)
                        }
                    }
                )
            )
        }

        /**
         * Create HITL Selection Request ToolCall
         *
         * Used by HitlRequestSelectionTool to request selection from predefined options.
         * The tool_call_id is stable across retries (format: hitl_{runId}_{nodeId}).
         *
         * @param toolCallId Stable identifier for this HITL request
         * @param prompt Message displayed to the user
         * @param options List of selection options with id, label, description
         * @param runId Graph run ID for checkpoint correlation
         * @param nodeId Node ID that initiated this request
         * @param graphId Optional graph ID for context
         * @param selectionType Type of selection: "single" or "multiple"
         * @param timeout Optional timeout in milliseconds
         * @param metadata Additional custom metadata
         */
        fun hitlSelection(
            toolCallId: String,
            prompt: String,
            options: List<Map<String, Any?>>,
            runId: String,
            nodeId: String,
            graphId: String? = null,
            selectionType: String = "single",
            timeout: Long? = null,
            metadata: Map<String, Any> = emptyMap()
        ): OAIToolCall {
            return OAIToolCall(
                id = toolCallId,
                function = ToolCallFunction(
                    name = ToolNames.HITL_REQUEST_SELECTION,
                    arguments = buildMap {
                        put("tool_call_id", toolCallId)
                        put("prompt", prompt)
                        put("hitl_type", "selection")
                        put("options", options)
                        put("selection_type", selectionType)
                        put("run_id", runId)
                        put("node_id", nodeId)
                        if (graphId != null) {
                            put("graph_id", graphId)
                        }
                        if (timeout != null) {
                            put("timeout", timeout)
                        }
                        if (metadata.isNotEmpty()) {
                            put("metadata", metadata)
                        }
                    }
                )
            )
        }

        /**
         * Create HITL Quantity Request ToolCall (1.3.5+)
         *
         * Used for requesting quantity selection from the user.
         * Supports both single quantity and multi-quantity (per-item) selection.
         *
         * @param toolCallId Stable identifier for this HITL request
         * @param prompt Message displayed to the user
         * @param runId Graph run ID for checkpoint correlation
         * @param nodeId Node ID that initiated this request
         * @param graphId Optional graph ID for context
         * @param quantityType "single" for one quantity, "multiple" for per-item quantities
         * @param min Minimum allowed quantity
         * @param max Maximum allowed quantity
         * @param defaultValue Default quantity value
         * @param step Step increment for quantity selection
         * @param items List of items for multi-quantity (each with id, label, optional description)
         * @param timeout Optional timeout in milliseconds
         * @param metadata Additional custom metadata
         */
        fun hitlQuantity(
            toolCallId: String,
            prompt: String,
            runId: String,
            nodeId: String,
            graphId: String? = null,
            quantityType: String = "single",
            min: Int = 0,
            max: Int = 100,
            defaultValue: Int = 1,
            step: Int = 1,
            items: List<Map<String, Any?>>? = null,
            timeout: Long? = null,
            metadata: Map<String, Any> = emptyMap()
        ): OAIToolCall {
            return OAIToolCall(
                id = toolCallId,
                function = ToolCallFunction(
                    name = ToolNames.HITL_REQUEST_QUANTITY,
                    arguments = buildMap {
                        put("tool_call_id", toolCallId)
                        put("prompt", prompt)
                        put("hitl_type", "quantity")
                        put("quantity_type", quantityType)
                        put("min", min)
                        put("max", max)
                        put("default", defaultValue)
                        put("step", step)
                        put("run_id", runId)
                        put("node_id", nodeId)
                        if (graphId != null) {
                            put("graph_id", graphId)
                        }
                        if (items != null) {
                            put("items", items)
                        }
                        if (timeout != null) {
                            put("timeout", timeout)
                        }
                        if (metadata.isNotEmpty()) {
                            put("metadata", metadata)
                        }
                    }
                )
            )
        }

        /**
         * Create HITL Info Request ToolCall (1.3.5+)
         *
         * Used for displaying information to the user (acknowledgment only).
         *
         * @param toolCallId Stable identifier for this HITL request
         * @param prompt Message displayed to the user
         * @param runId Graph run ID for checkpoint correlation
         * @param nodeId Node ID that initiated this request
         * @param graphId Optional graph ID for context
         * @param autoProceed Whether to auto-proceed after display
         * @param timeout Optional timeout in milliseconds
         * @param metadata Additional custom metadata
         */
        fun hitlInfo(
            toolCallId: String,
            prompt: String,
            runId: String,
            nodeId: String,
            graphId: String? = null,
            autoProceed: Boolean = false,
            timeout: Long? = null,
            metadata: Map<String, Any> = emptyMap()
        ): OAIToolCall {
            return OAIToolCall(
                id = toolCallId,
                function = ToolCallFunction(
                    name = ToolNames.HITL_REQUEST_INFO,
                    arguments = buildMap {
                        put("tool_call_id", toolCallId)
                        put("prompt", prompt)
                        put("hitl_type", "info")
                        put("auto_proceed", autoProceed)
                        put("run_id", runId)
                        put("node_id", nodeId)
                        if (graphId != null) {
                            put("graph_id", graphId)
                        }
                        if (timeout != null) {
                            put("timeout", timeout)
                        }
                        if (metadata.isNotEmpty()) {
                            put("metadata", metadata)
                        }
                    }
                )
            )
        }

        /**
         * Create HITL Escalate Request ToolCall (1.3.5+)
         *
         * Used for escalating to human agent with optional reason.
         *
         * @param toolCallId Stable identifier for this HITL request
         * @param prompt Message displayed to the user/agent
         * @param runId Graph run ID for checkpoint correlation
         * @param nodeId Node ID that initiated this request
         * @param graphId Optional graph ID for context
         * @param reason Optional escalation reason
         * @param priority Escalation priority (low, normal, high, urgent)
         * @param timeout Optional timeout in milliseconds
         * @param metadata Additional custom metadata
         */
        fun hitlEscalate(
            toolCallId: String,
            prompt: String,
            runId: String,
            nodeId: String,
            graphId: String? = null,
            reason: String? = null,
            priority: String = "normal",
            timeout: Long? = null,
            metadata: Map<String, Any> = emptyMap()
        ): OAIToolCall {
            return OAIToolCall(
                id = toolCallId,
                function = ToolCallFunction(
                    name = ToolNames.HITL_REQUEST_ESCALATE,
                    arguments = buildMap {
                        put("tool_call_id", toolCallId)
                        put("prompt", prompt)
                        put("hitl_type", "escalate")
                        put("priority", priority)
                        put("run_id", runId)
                        put("node_id", nodeId)
                        if (graphId != null) {
                            put("graph_id", graphId)
                        }
                        if (reason != null) {
                            put("reason", reason)
                        }
                        if (timeout != null) {
                            put("timeout", timeout)
                        }
                        if (metadata.isNotEmpty()) {
                            put("metadata", metadata)
                        }
                    }
                )
            )
        }

        /**
         * Generate a stable HITL tool_call_id
         *
         * Format: hitl_{runId}_{nodeId}
         * This ensures the same ID is generated on retry.
         *
         * @param runId Current graph run ID
         * @param nodeId Current node ID
         * @return Stable tool_call_id for HITL requests
         */
        fun generateHitlToolCallId(runId: String, nodeId: String): String =
            "hitl_${runId}_${nodeId}"
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
