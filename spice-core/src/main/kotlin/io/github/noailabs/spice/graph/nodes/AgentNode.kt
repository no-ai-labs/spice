package io.github.noailabs.spice.graph.nodes

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.Comm
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.Node
import io.github.noailabs.spice.graph.NodeContext
import io.github.noailabs.spice.graph.NodeResult
import io.github.noailabs.spice.toAgentContext

/**
 * Node that executes an Agent.
 * Wraps the existing Spice Agent abstraction for use in graphs.
 */
class AgentNode(
    override val id: String,
    val agent: Agent,
    val inputKey: String? = null  // If null, use "input" or last node's result
) : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        // Get input: use inputKey, or "_previous", or "input"
        val inputContent = when {
            inputKey != null -> ctx.state[inputKey]?.toString() ?: ""
            ctx.state.containsKey("_previous") -> ctx.state["_previous"]?.toString() ?: ""
            ctx.state.containsKey("input") -> ctx.state["input"]?.toString() ?: ""
            else -> ""
        }

        // 🔥 Extract previous Comm's data for propagation
        // Priority: _previousComm (from previous node) -> comm (initial state) -> metadata (fallback)
        val previousComm = ctx.state["_previousComm"] as? Comm
            ?: ctx.state["comm"] as? Comm  // 🆕 Support initial Comm from graph input

        val previousData = previousComm?.data
            ?: (ctx.state["metadata"] as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { it.value.toString() }  // 🆕 Support direct metadata map
            ?: emptyMap()

        // Create Comm from input (with ExecutionContext and propagated data)
        val comm = Comm(
            content = inputContent,
            from = "graph-${ctx.graphId}",
            context = ctx.context,  // ✨ Context propagation via ExecutionContext!
            data = previousData           // 🔥 Metadata propagation!
        )

        // Execute agent and map to NodeResult (chain SpiceResult)
        return agent.processComm(comm)
            .map { response ->
                // 🔥 Store full response Comm in metadata for next node to access
                val additional = buildMap<String, Any> {
                    put("agentId", agent.id)
                    put("agentName", agent.name)
                    put("_previousComm", response)  // Store Comm in metadata

                    // 🆕 Propagate comm.data to metadata for non-AgentNode access
                    // This allows DynamicHumanNode and other nodes to read agent data
                    response.data.forEach { (key, value) ->
                        put(key, value)
                    }

                    ctx.context.tenantId?.let { put("tenantId", it) }
                    ctx.context.userId?.let { put("userId", it) }
                }
                NodeResult.fromContext(ctx, data = response.content, additional = additional)
            }
    }
}
