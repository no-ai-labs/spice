package io.github.noailabs.spice.graph

import io.github.noailabs.spice.AgentContext
import io.github.noailabs.spice.error.SpiceResult

/**
 * Core abstraction for a node in the execution graph.
 * Every node represents a unit of work that can be executed.
 *
 * Returns SpiceResult for consistent error handling across Spice framework.
 */
interface Node {
    val id: String
    suspend fun run(ctx: NodeContext): SpiceResult<NodeResult>
}

/**
 * Execution context passed to each node.
 * Contains the current state and metadata for the graph execution.
 *
 * @property agentContext Optional AgentContext for multi-tenant support and context propagation
 */
data class NodeContext(
    val graphId: String,
    val state: MutableMap<String, Any?>,
    val metadata: MutableMap<String, Any> = mutableMapOf(),
    val agentContext: AgentContext? = null  // ✨ Context propagation support!
)

/**
 * Result of a node execution.
 */
data class NodeResult(
    val data: Any?,
    val metadata: Map<String, Any> = emptyMap(),
    val nextEdges: List<String> = emptyList()
)
