package io.github.noailabs.spice.springboot.statemachine.actions

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.Graph
import io.github.noailabs.spice.graph.runner.GraphRunner

/**
 * Executes a graph via the configured GraphRunner.
 */
class NodeExecutionAction(
    private val graphRunner: GraphRunner
) {
    suspend fun execute(graph: Graph, message: SpiceMessage): SpiceResult<SpiceMessage> {
        return graphRunner.execute(graph, message)
    }
}
