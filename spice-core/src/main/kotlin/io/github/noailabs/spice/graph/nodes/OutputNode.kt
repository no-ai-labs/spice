package io.github.noailabs.spice.graph.nodes

import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.Node
import io.github.noailabs.spice.graph.NodeContext
import io.github.noailabs.spice.graph.NodeResult

/**
 * Node that produces the final output of the graph.
 * By default, selects the "result" key from the context state.
 */
class OutputNode(
    override val id: String,
    val selector: (NodeContext) -> Any? = { it.state["result"] }
) : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        val output = selector(ctx)
        return SpiceResult.success(
            NodeResult(
                data = output,
                metadata = mapOf("isOutput" to true)
            )
        )
    }
}
