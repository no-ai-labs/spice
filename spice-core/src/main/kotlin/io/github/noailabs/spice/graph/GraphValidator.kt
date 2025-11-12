package io.github.noailabs.spice.graph

import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult

/**
 * Validates graph structure before execution.
 * Catches common graph errors early to prevent runtime failures.
 */
object GraphValidator {

    /**
     * Validate a graph and return validation errors if any.
     */
    fun validate(graph: Graph): SpiceResult<Unit> {
        val errors = mutableListOf<String>()

        // 1. Check for empty graph
        if (graph.nodes.isEmpty()) {
            errors.add("Graph must have at least one node")
        }

        // 2. Check entry point exists
        if (!graph.nodes.containsKey(graph.entryPoint)) {
            errors.add("Entry point '${graph.entryPoint}' does not exist in graph")
        }

        // 3. Check all edges reference existing nodes (allow "*" as wildcard)
        graph.edges.forEach { edge ->
            if (edge.from != "*" && !graph.nodes.containsKey(edge.from)) {
                errors.add("Edge references non-existent 'from' node: ${edge.from}")
            }
            if (!graph.nodes.containsKey(edge.to)) {
                errors.add("Edge references non-existent 'to' node: ${edge.to}")
            }
        }

        // 4. Check for cycles (DFS-based cycle detection) if not explicitly allowed
        if (!graph.allowCycles) {
            val cycleNodes = detectCycles(graph)
            if (cycleNodes.isNotEmpty()) {
                errors.add("Graph contains cycles involving nodes: ${cycleNodes.joinToString(", ")}")
            }
        }

        // 5. Check for unreachable nodes
        val unreachableNodes = findUnreachableNodes(graph)
        if (unreachableNodes.isNotEmpty()) {
            errors.add("Graph contains unreachable nodes: ${unreachableNodes.joinToString(", ")}")
        }

        return if (errors.isEmpty()) {
            SpiceResult.success(Unit)
        } else {
            SpiceResult.failure(
                SpiceError.ValidationError(
                    message = "Graph validation failed: ${errors.joinToString("; ")}",
                    context = mapOf("errors" to errors)
                )
            )
        }
    }

    /**
     * Detect cycles in the graph using DFS.
     * Returns list of nodes involved in cycles.
     */
    private fun detectCycles(graph: Graph): List<String> {
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        val cycleNodes = mutableSetOf<String>()

        fun dfs(nodeId: String): Boolean {
            if (recursionStack.contains(nodeId)) {
                cycleNodes.add(nodeId)
                return true
            }

            if (visited.contains(nodeId)) {
                return false
            }

            visited.add(nodeId)
            recursionStack.add(nodeId)

            // Visit all outgoing nodes
            val outgoingEdges = graph.edges.filter { it.from == nodeId }
            for (edge in outgoingEdges) {
                if (dfs(edge.to)) {
                    cycleNodes.add(nodeId)
                }
            }

            recursionStack.remove(nodeId)
            return false
        }

        // Start DFS from entry point
        graph.nodes.keys.forEach { nodeId ->
            if (!visited.contains(nodeId)) {
                dfs(nodeId)
            }
        }

        return cycleNodes.toList()
    }

    /**
     * Find nodes that are unreachable from the entry point.
     * Considers wildcard edges ("*") as potentially reaching all nodes.
     */
    private fun findUnreachableNodes(graph: Graph): List<String> {
        val reachable = mutableSetOf<String>()

        // Check if there are any wildcard edges
        val hasWildcardEdges = graph.edges.any { it.from == "*" }

        // If there are wildcard edges, collect all their target nodes
        val wildcardTargets = if (hasWildcardEdges) {
            graph.edges.filter { it.from == "*" }.map { it.to }.toSet()
        } else {
            emptySet()
        }

        fun dfs(nodeId: String) {
            if (reachable.contains(nodeId)) return

            reachable.add(nodeId)

            // Visit all outgoing nodes from specific edges
            graph.edges
                .filter { it.from == nodeId }
                .forEach { edge -> dfs(edge.to) }

            // If current node can potentially use wildcard edges, mark those targets as reachable
            if (hasWildcardEdges) {
                wildcardTargets.forEach { target -> dfs(target) }
            }
        }

        // Start DFS from entry point
        if (graph.nodes.containsKey(graph.entryPoint)) {
            dfs(graph.entryPoint)
        }

        // Find unreachable nodes
        return graph.nodes.keys.filter { !reachable.contains(it) }
    }

    /**
     * Check if graph has any terminal nodes (nodes with no outgoing edges).
     * This is not necessarily an error, but can be useful for analysis.
     */
    fun findTerminalNodes(graph: Graph): List<String> {
        val nodesWithOutgoing = graph.edges.map { it.from }.toSet()
        return graph.nodes.keys.filter { !nodesWithOutgoing.contains(it) }
    }

    /**
     * Check if graph is a DAG (Directed Acyclic Graph).
     */
    fun isDAG(graph: Graph): Boolean {
        return detectCycles(graph).isEmpty()
    }
}
