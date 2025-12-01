package io.github.noailabs.spice.tools

import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.ToolContext
import io.github.noailabs.spice.ToolResult
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.hitl.result.HITLMetadata
import io.github.noailabs.spice.hitl.result.HITLOption
import io.github.noailabs.spice.hitl.result.HitlEventEmitter
import io.github.noailabs.spice.hitl.result.NoOpHitlEventEmitter
import io.github.noailabs.spice.toolspec.OAIToolCall

/**
 * HITL Request Selection Tool
 *
 * This tool requests selection from predefined options and returns
 * a WAITING_HITL status to pause graph execution until the user responds.
 *
 * **Key Design Decisions:**
 * - Returns `ToolResult.waitingHitl()` to signal HITL pause
 * - Generates stable `tool_call_id` using format: `hitl_{runId}_{nodeId}_{invocationIndex}`
 * - Same ID is generated on retry (idempotent), different ID for new invocations (loop-safe)
 * - Uses Port Interface pattern (HitlEventEmitter) for external notification
 * - Supports both single and multiple selection modes
 *
 * **Usage in Graph DSL:**
 * ```kotlin
 * graph("booking-flow") {
 *     hitlSelection("select-room", "Select a room type") {
 *         option("standard", "Standard Room", "Basic room with 1 bed")
 *         option("deluxe", "Deluxe Room", "Premium room with 2 beds")
 *         option("suite", "Suite", "Luxury suite with living area")
 *     }
 *     output("result")
 *
 *     edge("select-room", "result")
 * }
 * ```
 *
 * **Parameter Mapping:**
 * The tool expects the following parameters from the message:
 * - `prompt` (required): The prompt message to display to the user
 * - `options` (required): List of selection options (as List<Map> or List<HITLOption>)
 * - `selection_type` (optional): "single" (default) or "multiple"
 * - `timeout` (optional): Timeout in milliseconds
 *
 * @property emitter Event emitter for notifying external systems
 * @since 1.0.6
 */
class HitlRequestSelectionTool(
    private val emitter: HitlEventEmitter = NoOpHitlEventEmitter,
    override val name: String = OAIToolCall.Companion.ToolNames.HITL_REQUEST_SELECTION,
    override val description: String = "Request selection from predefined options"
) : Tool {

    override suspend fun execute(
        params: Map<String, Any>,
        context: ToolContext
    ): SpiceResult<ToolResult> {
        // Extract required parameters
        val prompt = params["prompt"] as? String
            ?: return SpiceResult.success(
                ToolResult.error(
                    error = "Missing required parameter: prompt",
                    errorCode = "MISSING_PARAM"
                )
            )

        // Extract options (can be List<Map> or List<HITLOption>)
        val rawOptions = params["options"]
        val options: List<HITLOption> = when (rawOptions) {
            is List<*> -> rawOptions.mapNotNull { parseOption(it) }
            else -> return SpiceResult.success(
                ToolResult.error(
                    error = "Missing or invalid required parameter: options",
                    errorCode = "MISSING_PARAM"
                )
            )
        }

        if (options.isEmpty()) {
            return SpiceResult.success(
                ToolResult.error(
                    error = "Options list cannot be empty",
                    errorCode = "INVALID_PARAM"
                )
            )
        }

        // Extract optional parameters
        val selectionType = params["selection_type"] as? String ?: "single"
        val timeout = (params["timeout"] as? Number)?.toLong()
        val allowFreeText = params["allow_free_text"] as? Boolean ?: false

        // Get invocation index for loop-safe ID generation
        val invocationIndex = (params[HITLMetadata.INVOCATION_INDEX_KEY] as? Number)?.toInt() ?: 0

        // Get graph context (required for stable tool_call_id generation)
        val runId = context.graph.runId
        val nodeId = context.graph.nodeId
        val graphId = context.graph.graphId

        if (runId == null || nodeId == null) {
            return SpiceResult.success(
                ToolResult.error(
                    error = "HITL tool requires graph context (runId and nodeId)",
                    errorCode = "MISSING_CONTEXT"
                )
            )
        }

        // Generate stable tool_call_id (same ID on retry, different ID for new invocations)
        val toolCallId = HITLMetadata.generateToolCallId(runId, nodeId, invocationIndex)

        // Create HITL metadata
        val metadata = HITLMetadata.forSelection(
            runId = runId,
            nodeId = nodeId,
            prompt = prompt,
            options = options,
            invocationIndex = invocationIndex,
            graphId = graphId,
            timeout = timeout,
            additionalMetadata = buildMap {
                put("selection_type", selectionType)
                put("allow_free_text", allowFreeText)  // Always record (true or false)
                put("agent_id", context.agentId)
                context.correlationId?.takeIf { it.isNotEmpty() }?.let { put("correlation_id", it) }
                context.auth.userId?.takeIf { it.isNotEmpty() }?.let { put("user_id", it) }
                context.auth.tenantId?.takeIf { it.isNotEmpty() }?.let { put("tenant_id", it) }
            }
        )

        // Emit HITL request to external systems (via Port Interface)
        val emitResult = emitter.emitHitlRequest(metadata)
        if (emitResult is SpiceResult.Failure) {
            return SpiceResult.success(
                ToolResult.error(
                    error = "Failed to emit HITL request: ${emitResult.error.message}",
                    errorCode = "EMIT_FAILED"
                )
            )
        }

        // Return WAITING_HITL result
        // This will cause GraphRunner to pause execution and save checkpoint
        return SpiceResult.success(
            ToolResult.waitingHitl(
                toolCallId = toolCallId,
                prompt = prompt,
                hitlType = HITLMetadata.TYPE_SELECTION,
                metadata = buildMap {
                    put("options", options.map { it.toMap() })
                    put("selection_type", selectionType)
                    put("allow_free_text", allowFreeText)  // Always record (true or false)
                    put("timeout", timeout ?: 0L)
                    put("run_id", runId)
                    put("node_id", nodeId)
                    graphId?.takeIf { it.isNotEmpty() }?.let { put("graph_id", it) }
                    // Store invocation index for next HITL request in loops
                    put(HITLMetadata.INVOCATION_INDEX_KEY, invocationIndex)
                }
            )
        )
    }

    /**
     * Parse an option from various formats
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseOption(raw: Any?): HITLOption? {
        return when (raw) {
            is HITLOption -> raw
            is Map<*, *> -> {
                val map = raw as Map<String, Any?>
                val id = map["id"]?.toString() ?: return null
                val label = map["label"]?.toString() ?: map["name"]?.toString() ?: id
                val description = map["description"]?.toString()
                val metadata = (map["metadata"] as? Map<String, Any>) ?: emptyMap()
                HITLOption(
                    id = id,
                    label = label,
                    description = description,
                    metadata = metadata
                )
            }
            else -> null
        }
    }

    /**
     * Extension function to convert HITLOption to Map
     */
    private fun HITLOption.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "label" to label,
        "description" to description,
        "metadata" to metadata
    )

    companion object {
        /**
         * Create a HitlRequestSelectionTool with default settings
         */
        fun create(emitter: HitlEventEmitter = NoOpHitlEventEmitter): HitlRequestSelectionTool =
            HitlRequestSelectionTool(emitter)

        /**
         * Create parameters map for this tool
         *
         * Helper method for building tool parameters in DSL.
         *
         * @param prompt The prompt message to display
         * @param options List of selection options
         * @param selectionType "single" or "multiple"
         * @param timeout Optional timeout in milliseconds
         * @param allowFreeText Whether to allow free text input in addition to selection (default: false)
         * @since 1.5.5 Added allowFreeText parameter
         */
        fun params(
            prompt: String,
            options: List<HITLOption>,
            selectionType: String = "single",
            timeout: Long? = null,
            allowFreeText: Boolean = false
        ): Map<String, Any> = buildMap {
            put("prompt", prompt)
            put("options", options)
            put("selection_type", selectionType)
            if (timeout != null) {
                put("timeout", timeout)
            }
            put("allow_free_text", allowFreeText)  // Always record (true or false)
        }

        /**
         * DSL builder for creating options
         */
        fun options(builder: OptionsBuilder.() -> Unit): List<HITLOption> {
            return OptionsBuilder().apply(builder).build()
        }
    }

    /**
     * DSL Builder for creating HITL options
     */
    class OptionsBuilder {
        private val options = mutableListOf<HITLOption>()

        /**
         * Add an option with id and label
         */
        fun option(id: String, label: String) {
            options.add(HITLOption(id = id, label = label))
        }

        /**
         * Add an option with id, label, and description
         */
        fun option(id: String, label: String, description: String) {
            options.add(HITLOption(id = id, label = label, description = description))
        }

        /**
         * Add an option with full configuration
         */
        fun option(
            id: String,
            label: String,
            description: String? = null,
            metadata: Map<String, Any> = emptyMap()
        ) {
            options.add(HITLOption(
                id = id,
                label = label,
                description = description,
                metadata = metadata
            ))
        }

        /**
         * Add a pre-built option
         */
        fun option(opt: HITLOption) {
            options.add(opt)
        }

        internal fun build(): List<HITLOption> = options.toList()
    }
}
