package io.github.noailabs.spice.graph.nodes

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.Comm
import io.github.noailabs.spice.graph.Node
import io.github.noailabs.spice.graph.NodeContext
import io.github.noailabs.spice.graph.NodeResult

/**
 * Node that executes an Agent.
 * Wraps the existing Spice Agent abstraction for use in graphs.
 */
class AgentNode(
    override val id: String,
    val agent: Agent,
    val inputKey: String? = null  // If null, use "input" or last node's result
) : Node {
    override suspend fun run(ctx: NodeContext): NodeResult {
        // Get input: use inputKey, or "_previous", or "input"
        val inputContent = when {
            inputKey != null -> ctx.state[inputKey]?.toString() ?: ""
            ctx.state.containsKey("_previous") -> ctx.state["_previous"]?.toString() ?: ""
            ctx.state.containsKey("input") -> ctx.state["input"]?.toString() ?: ""
            else -> ""
        }

        // Create Comm from input (with AgentContext if available)
        val comm = Comm(
            content = inputContent,
            from = "graph-${ctx.graphId}",
            context = ctx.agentContext  // ✨ Context propagation!
        )

        // Execute agent (returns SpiceResult<Comm>)
        val spiceResult = agent.processComm(comm)

        // Extract Comm
        val response = spiceResult.getOrThrow()

        // Return result
        return NodeResult(
            data = response.content,
            metadata = mapOf(
                "agentId" to agent.id,
                "agentName" to (agent.name ?: "unknown"),
                "tenantId" to (ctx.agentContext?.tenantId ?: "none"),
                "userId" to (ctx.agentContext?.userId ?: "none")
            )
        )
    }
}
