package io.github.noailabs.spice.graph.nodes

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.ToolContext
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
        // Extract tool parameters from message
        val params = paramMapper(message)

        // Filter out null values and ensure type is Map<String, Any>
        val nonNullParams: Map<String, Any> = params.filterValues { it != null }
            .mapValues { it.value!! }

        // Create ToolContext from message metadata
        val toolContext = ToolContext(
            agentId = message.from,
            userId = message.getMetadata("userId"),
            tenantId = message.getMetadata("tenantId"),
            correlationId = message.correlationId,
            metadata = message.metadata.filterValues { it != null }.mapValues { it.value!! }
        )

        // Execute tool
        return when (val result = tool.execute(nonNullParams, toolContext)) {
            is SpiceResult.Success -> {
                val toolResult = result.value

                // Embed tool result in message
                val dataUpdates = buildMap<String, Any> {
                    toolResult.result?.let { put("tool_result", it) }
                    put("tool_success", toolResult.success)
                    put("tool_name", tool.name)
                }
                val metadataUpdates = mapOf<String, Any>(
                    "tool_executed" to tool.name,
                    "tool_success" to toolResult.success
                )

                val output = message
                    .withData(dataUpdates)
                    .withMetadata(metadataUpdates)

                SpiceResult.success(output)
            }
            is SpiceResult.Failure -> {
                // Propagate tool error
                SpiceResult.failure(result.error)
            }
        }
    }
}
