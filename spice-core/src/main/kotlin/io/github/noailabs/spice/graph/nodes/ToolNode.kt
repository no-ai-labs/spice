package io.github.noailabs.spice.graph.nodes

import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.graph.Node
import io.github.noailabs.spice.graph.NodeContext
import io.github.noailabs.spice.graph.NodeResult

/**
 * Node that executes a Tool.
 * Wraps the existing Spice Tool abstraction for use in graphs.
 */
class ToolNode(
    override val id: String,
    val tool: Tool,
    val paramMapper: (NodeContext) -> Map<String, Any?> = { it.state }
) : Node {
    override suspend fun run(ctx: NodeContext): NodeResult {
        // Map context to tool parameters
        val params = paramMapper(ctx)

        // Filter out null values (Tool.execute expects Map<String, Any>)
        val nonNullParams = params.filterValues { it != null }
            .mapValues { it.value!! }

        // Execute tool (returns SpiceResult<ToolResult>)
        val spiceResult = tool.execute(nonNullParams)

        // Extract ToolResult
        val toolResult = spiceResult.getOrThrow()

        // Return result
        return NodeResult(
            data = toolResult.result,
            metadata = mapOf(
                "toolName" to tool.name,
                "toolSuccess" to toolResult.success
            )
        )
    }
}
