package io.github.noailabs.spice.graph.nodes

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.ToolContext
import io.github.noailabs.spice.ToolResult
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.Node

/**
 * 🔧 Tool Node for Spice Framework 1.0.0
 *
 * Executes a Tool and integrates result into graph execution.
 *
 * **BREAKING CHANGE from 0.x:**
 * - Tool parameters extracted from SpiceMessage.data
 * - Tool result embedded in output message
 * - No separate ToolContext conversion
 *
 * **Architecture:**
 * ```
 * Input Message (RUNNING)
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
 * val searchTool = ReservationSearchTool()
 *
 * graph("booking") {
 *     tool("search", searchTool) { message ->
 *         // Extract tool params from message
 *         mapOf(
 *             "query" to message.getData<String>("search_query"),
 *             "userId" to message.getMetadata<String>("userId")
 *         )
 *     }
 *     output("result")
 *     edge("search", "result")
 * }
 * ```
 *
 * @property tool The tool to execute
 * @property paramMapper Function to extract tool parameters from message
 * @since 1.0.0
 */
class ToolNode(
    override val id: String,
    val tool: Tool,
    val paramMapper: (SpiceMessage) -> Map<String, Any?> = { it.data }
) : Node {

    /**
     * Execute tool with message
     *
     * **Flow:**
     * 1. Extract parameters from message using paramMapper
     * 2. Filter out null values
     * 3. Create ToolContext from message metadata
     * 4. Execute tool
     * 5. Embed result in output message
     *
     * @param message Input message with tool parameters
     * @return SpiceResult with tool result or error
     */
    override suspend fun run(message: SpiceMessage): SpiceResult<SpiceMessage> {
        // Prepare invocation using shared helper
        val (nonNullParams, toolContext) = prepareInvocation(message)

        // Execute tool
        return when (val result = tool.execute(nonNullParams, toolContext)) {
            is SpiceResult.Success -> {
                val output = buildOutputMessage(message, result.value, tool.name)
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

            // Embed tool result in message
            val dataUpdates = buildMap<String, Any> {
                toolResult.result?.let { put("tool_result", it) }
                put("tool_success", toolResult.success)
                put("tool_name", toolName)
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
                put("tool_success", toolResult.success)
                if (toolMetadata.isNotEmpty()) {
                    putAll(toolMetadata)
                }
            }

            return message
                .withData(dataUpdates)
                .withMetadata(metadataUpdates)
        }
    }
}
