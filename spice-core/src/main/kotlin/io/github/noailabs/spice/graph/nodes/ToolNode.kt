package io.github.noailabs.spice.graph.nodes

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.ToolContext
import io.github.noailabs.spice.ToolResult
import io.github.noailabs.spice.ToolResultStatus
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.Node
import io.github.noailabs.spice.tool.StaticToolResolver
import io.github.noailabs.spice.tool.ToolResolver

/**
 * 🔧 Tool Node for Spice Framework 1.0.5
 *
 * Executes a Tool and integrates result into graph execution.
 * Supports both static and dynamic tool resolution via ToolResolver.
 *
 * **Dynamic Tool Selection (1.0.5+):**
 * - Static tool: `tool("search", searchTool)` - existing pattern, 100% compatible
 * - Dynamic tool: `tool("fetch", ToolResolver.byRegistry { msg -> msg.getData("toolId")!! })`
 *
 * **Architecture:**
 * ```
 * Input Message (RUNNING)
 *   ↓
 * ToolResolver.resolve(message) → Tool
 *   ↓
 * Extract params from message.data
 *   ↓
 * Tool.execute(params)
 *   ↓
 * Output Message (RUNNING)
 *   - Tool result in data
 *   - Success flag in metadata
 * ```
 *
 * **Usage:**
 * ```kotlin
 * // Static tool (existing pattern)
 * graph("booking") {
 *     tool("search", searchTool) { message ->
 *         mapOf(
 *             "query" to message.getData<String>("search_query"),
 *             "userId" to message.getMetadata<String>("userId")
 *         )
 *     }
 * }
 *
 * // Dynamic tool (new pattern)
 * graph("generic") {
 *     tool("fetch", ToolResolver.byRegistry(
 *         nameSelector = { msg -> msg.getData<String>("toolId")!! },
 *         namespace = "stayfolio"
 *     )) { msg ->
 *         mapOf("userId" to msg.getMetadata<String>("userId"))
 *     }
 * }
 * ```
 *
 * @property resolver ToolResolver for static or dynamic tool resolution
 * @property paramMapper Function to extract tool parameters from message
 * @since 1.0.0, enhanced in 1.0.5
 */
class ToolNode(
    override val id: String,
    val resolver: ToolResolver,
    val paramMapper: (SpiceMessage) -> Map<String, Any?> = { it.data }
) : Node {

    /**
     * Backward-compatible constructor for static tool binding.
     * Wraps the tool in StaticToolResolver.
     */
    constructor(
        id: String,
        tool: Tool,
        paramMapper: (SpiceMessage) -> Map<String, Any?> = { it.data }
    ) : this(id, StaticToolResolver(tool), paramMapper)

    /**
     * Get the statically-bound tool if this node uses StaticToolResolver.
     * Returns null for dynamic resolvers.
     */
    val staticTool: Tool?
        get() = (resolver as? StaticToolResolver)?.tool

    /**
     * Legacy accessor for backward compatibility.
     * @throws IllegalStateException if resolver is not static
     */
    val tool: Tool
        get() = staticTool
            ?: error("Cannot access 'tool' on dynamic resolver. Use resolver.resolve() instead.")

    /**
     * Execute tool with message
     *
     * **Flow:**
     * 1. Resolve tool using resolver (static or dynamic)
     * 2. Extract parameters from message using paramMapper
     * 3. Filter out null values
     * 4. Create ToolContext from message metadata
     * 5. Execute tool
     * 6. Embed result in output message
     *
     * @param message Input message with tool parameters
     * @return SpiceResult with tool result or error
     */
    override suspend fun run(message: SpiceMessage): SpiceResult<SpiceMessage> {
        // Resolve tool (static or dynamic)
        val resolvedTool = when (val resolveResult = resolver.resolve(message)) {
            is SpiceResult.Success -> resolveResult.value
            is SpiceResult.Failure -> return SpiceResult.failure(resolveResult.error)
        }

        // Execute with resolved tool
        return executeWith(resolvedTool, message)
    }

    /**
     * Execute with a pre-resolved tool.
     *
     * This method is used by GraphRunner when it needs to resolve the tool
     * once and execute with listeners. Avoids double resolution.
     *
     * @param resolvedTool The tool to execute (already resolved)
     * @param message Input message with tool parameters
     * @return SpiceResult with tool result or error
     */
    suspend fun executeWith(resolvedTool: Tool, message: SpiceMessage): SpiceResult<SpiceMessage> {
        // Prepare invocation using shared helper
        val (nonNullParams, toolContext) = prepareInvocation(message)

        // Execute tool
        return when (val result = resolvedTool.execute(nonNullParams, toolContext)) {
            is SpiceResult.Success -> {
                val output = buildOutputMessage(message, result.value, resolvedTool.name)
                SpiceResult.success(output)
            }
            is SpiceResult.Failure -> {
                // Propagate tool error
                SpiceResult.failure(result.error)
            }
        }
    }

    /**
     * Prepare invocation parameters from message.
     *
     * Extracts and sanitizes tool parameters, and creates ToolContext.
     * This shared method ensures consistent parameter preparation between
     * ToolNode.run() and GraphRunner.executeToolNodeWithListeners().
     *
     * @param message Input message with tool parameters
     * @return Pair of (sanitized parameters, tool context)
     */
    fun prepareInvocation(message: SpiceMessage): Pair<Map<String, Any>, ToolContext> {
        // Extract tool parameters from message using paramMapper
        val params = paramMapper(message)

        // Filter out null values and ensure type is Map<String, Any>
        val nonNullParams: Map<String, Any> = params.filterValues { it != null }
            .mapValues { it.value!! }

        // Create ToolContext from message using factory method
        val toolContext = ToolContext.from(message, message.from)

        return Pair(nonNullParams, toolContext)
    }

    companion object {
        /**
         * Build output message from tool result.
         *
         * This is a shared helper used by both ToolNode.run() and
         * DefaultGraphRunner.executeToolNodeWithListeners() to ensure
         * consistent message building.
         *
         * @param message Input message
         * @param toolResult Tool execution result
         * @param toolName Name of the executed tool
         * @return Output message with tool result embedded
         */
        fun buildOutputMessage(
            message: SpiceMessage,
            toolResult: ToolResult,
            toolName: String
        ): SpiceMessage {
            val toolMetadata = toolResult.metadata.filterValues { it != null }.mapValues { it.value!! }

            // Determine if tool is awaiting HITL response
            val isAwaitingHitl = toolResult.status == ToolResultStatus.WAITING_HITL
            val isSuccess = toolResult.status == ToolResultStatus.SUCCESS

            // Embed tool result in message
            val dataUpdates = buildMap<String, Any> {
                toolResult.result?.let { put("tool_result", it) }
                put("tool_status", toolResult.status.name)
                @Suppress("DEPRECATION")
                put("tool_success", isSuccess) // Backward compatibility
                put("tool_name", toolName)
                // Store error info if present
                toolResult.errorCode?.let { put("tool_error_code", it) }
                toolResult.message?.let { put("tool_message", it) }
                // Store metadata under namespaced key for DecisionNode routing
                // Include tool_name for consistency with whenToolMetadata access pattern
                val lastMetadata = if (toolMetadata.isNotEmpty()) {
                    toolMetadata + ("tool_name" to toolName)
                } else {
                    mapOf("tool_name" to toolName)
                }
                put("_tool.lastMetadata", lastMetadata)
                if (toolMetadata.isNotEmpty()) {
                    putAll(toolMetadata)
                }
            }
            val metadataUpdates = buildMap<String, Any> {
                put("tool_executed", toolName)
                put("tool_status", toolResult.status.name)
                @Suppress("DEPRECATION")
                put("tool_success", isSuccess) // Backward compatibility
                if (isAwaitingHitl) {
                    put("hitl_awaiting_response", true)
                    // Preserve HITL-specific metadata for checkpoint/resume
                    toolResult.metadata["hitl_tool_call_id"]?.let { put("hitl_tool_call_id", it) }
                    toolResult.metadata["hitl_type"]?.let { put("hitl_type", it) }
                }
                if (toolMetadata.isNotEmpty()) {
                    putAll(toolMetadata)
                }
            }

            // Set execution state based on tool result status
            val executionState = when (toolResult.status) {
                ToolResultStatus.WAITING_HITL -> ExecutionState.WAITING
                ToolResultStatus.ERROR,
                ToolResultStatus.TIMEOUT,
                ToolResultStatus.CANCELLED -> ExecutionState.FAILED
                ToolResultStatus.SUCCESS -> message.state // Keep current state (typically RUNNING)
            }

            return message
                .withData(dataUpdates)
                .withMetadata(metadataUpdates)
                .copy(state = executionState)
        }
    }
}
