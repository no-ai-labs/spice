package io.github.noailabs.spice.graph.nodes

import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.ToolContext
import io.github.noailabs.spice.error.SpiceResult
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
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        // Map context to tool parameters
        val params = paramMapper(ctx)

        // Filter out null values (Tool.execute expects Map<String, Any>)
        val nonNullParams = params.filterValues { it != null }
            .mapValues { it.value!! }

        // Execute tool with context if available, then map to NodeResult
        val spiceResult = if (ctx.agentContext != null) {
            // ✨ Context-aware tool execution!
            val toolContext = ToolContext(
                agentId = "graph-${ctx.graphId}",
                userId = ctx.agentContext.userId,
                tenantId = ctx.agentContext.tenantId,
                correlationId = ctx.agentContext.getAs("correlationId"),
                metadata = ctx.agentContext.toMap()
            )
            tool.execute(nonNullParams, toolContext)
        } else {
            tool.execute(nonNullParams)
        }

        // Chain SpiceResult
        return spiceResult.map { toolResult ->
            val additional = buildMap<String, Any> {
                put("toolName", tool.name)
                put("toolSuccess", toolResult.success)
                ctx.agentContext?.tenantId?.let { put("tenantId", it) }
                ctx.agentContext?.userId?.let { put("userId", it) }
            }
            NodeResult.fromContext(ctx, data = toolResult.result, additional = additional)
        }
    }
}
