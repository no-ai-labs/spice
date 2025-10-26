package io.github.noailabs.spice.graph

/**
 * Core abstraction for a node in the execution graph.
 * Every node represents a unit of work that can be executed.
 */
interface Node {
    val id: String
    suspend fun run(ctx: NodeContext): NodeResult
}

/**
 * Execution context passed to each node.
 * Contains the current state and metadata for the graph execution.
 */
data class NodeContext(
    val graphId: String,
    val state: MutableMap<String, Any?>,
    val metadata: MutableMap<String, Any> = mutableMapOf()
)

/**
 * Result of a node execution.
 */
data class NodeResult(
    val data: Any?,
    val metadata: Map<String, Any> = emptyMap(),
    val nextEdges: List<String> = emptyList()
)
