package io.github.noailabs.spice.graph.nodes

import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.ToolContext
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

        // Execute tool with context if available
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

        // Extract ToolResult
        val toolResult = spiceResult.getOrThrow()

        // Return result
        return NodeResult(
            data = toolResult.result,
            metadata = mapOf(
                "toolName" to tool.name,
                "toolSuccess" to toolResult.success,
                "tenantId" to (ctx.agentContext?.tenantId ?: "none"),
                "userId" to (ctx.agentContext?.userId ?: "none")
            )
        )
    }
}
