package io.github.noailabs.spice.graph.dsl

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.graph.Edge
import io.github.noailabs.spice.graph.Graph
import io.github.noailabs.spice.graph.Node
import io.github.noailabs.spice.graph.NodeContext
import io.github.noailabs.spice.graph.middleware.Middleware
import io.github.noailabs.spice.graph.nodes.AgentNode
import io.github.noailabs.spice.graph.nodes.OutputNode
import io.github.noailabs.spice.graph.nodes.ToolNode
import io.github.noailabs.spice.graph.nodes.ParallelNode
import io.github.noailabs.spice.graph.nodes.MergeNode
import io.github.noailabs.spice.graph.nodes.MergeStrategies
import io.github.noailabs.spice.graph.merge.MergePolicy

/**
 * DSL entry point for creating graphs.
 *
 * Example:
 * ```kotlin
 * val graph = graph("my-workflow") {
 *     agent("greeter", myAgent)
 *     tool("processor", myTool)
 *     output("result")
 * }
 * ```
 */
fun graph(id: String, block: GraphBuilder.() -> Unit): Graph {
    return GraphBuilder(id).apply(block).build()
}

/**
 * Builder for constructing graphs using DSL.
 */
class GraphBuilder(val id: String) {
    private val nodes = mutableMapOf<String, Node>()
    private val autoEdges = mutableListOf<Edge>()        // Automatically added edges (sequential flow)
    private val explicitEdges = mutableListOf<Edge>()    // User-defined conditional edges
    private val middlewares = mutableListOf<Middleware>()
    private var lastNodeId: String? = null

    /**
     * Add an Agent node to the graph.
     *
     * @param id Unique identifier for this node
     * @param agent The Spice Agent to execute
     */
    fun agent(id: String, agent: Agent) {
        val node = AgentNode(id, agent)
        nodes[id] = node
        connectToPrevious(id)
        lastNodeId = id
    }

    /**
     * Add a Tool node to the graph.
     *
     * @param id Unique identifier for this node
     * @param tool The Spice Tool to execute
     * @param paramMapper Optional function to map context to tool parameters
     */
    fun tool(
        id: String,
        tool: Tool,
        paramMapper: (NodeContext) -> Map<String, Any?> = { it.state }
    ) {
        val node = ToolNode(id, tool, paramMapper)
        nodes[id] = node
        connectToPrevious(id)
        lastNodeId = id
    }

    /**
     * Add a Human node to the graph for HITL (Human-in-the-Loop).
     * This node pauses graph execution and waits for human input.
     *
     * @param id Unique identifier for this node
     * @param prompt Message to show the human
     * @param options List of options for multiple choice (empty for free text)
     * @param timeout Optional timeout duration
     * @param validator Optional function to validate human response
     */
    fun humanNode(
        id: String,
        prompt: String,
        options: List<io.github.noailabs.spice.graph.nodes.HumanOption> = emptyList(),
        timeout: java.time.Duration? = null,
        validator: ((io.github.noailabs.spice.graph.nodes.HumanResponse) -> Boolean)? = null
    ) {
        val node = io.github.noailabs.spice.graph.nodes.HumanNode(
            id = id,
            prompt = prompt,
            options = options,
            timeout = timeout,
            validator = validator
        )
        nodes[id] = node
        connectToPrevious(id)
        lastNodeId = id
    }

    /**
     * Add an Output node to the graph.
     * This typically marks the end of the graph execution.
     *
     * @param id Unique identifier for this node (defaults to "output")
     * @param selector Function to select the output value from context
     */
    fun output(
        id: String = "output",
        selector: (NodeContext) -> Any? = { it.state["result"] }
    ) {
        val node = OutputNode(id, selector)
        nodes[id] = node
        connectToPrevious(id)
        lastNodeId = id
    }

    /**
     * Add middleware to the graph.
     * Middleware will be executed in the order they are added.
     *
     * @param middleware The middleware to add
     */
    fun middleware(middleware: Middleware) {
        middlewares.add(middleware)
    }

    /**
     * Add a ParallelNode to execute multiple branches concurrently.
     *
     * Example:
     * ```kotlin
     * parallel("data-processing",
     *     branches = mapOf(
     *         "fetch" to fetchNode,
     *         "validate" to validateNode,
     *         "transform" to transformNode
     *     ),
     *     mergePolicy = MergePolicy.Namespace
     * )
     * ```
     *
     * @param id Unique identifier for this parallel node
     * @param branches Map of branch ID to Node for each parallel branch
     * @param mergePolicy How to merge metadata from parallel branches (default: Namespace)
     * @param failFast If true, fail entire parallel execution on first branch failure (default: true)
     */
    fun parallel(
        id: String,
        branches: Map<String, Node>,
        mergePolicy: MergePolicy = MergePolicy.Namespace,
        failFast: Boolean = true
    ) {
        val node = ParallelNode(
            id = id,
            branches = branches,
            mergePolicy = mergePolicy,
            failFast = failFast
        )
        nodes[id] = node
        connectToPrevious(id)
        lastNodeId = id
    }

    /**
     * Add a MergeNode to aggregate results from a ParallelNode.
     *
     * Example:
     * ```kotlin
     * merge("aggregate", parallelNodeId = "data-processing") { results ->
     *     mapOf(
     *         "fetchedData" to results["fetch"],
     *         "isValid" to results["validate"],
     *         "transformed" to results["transform"]
     *     )
     * }
     * ```
     *
     * Common merge strategies available in MergeStrategies:
     * - `MergeStrategies.first` - Take first non-null result
     * - `MergeStrategies.last` - Take last non-null result
     * - `MergeStrategies.concatList` - Combine all into list
     * - `MergeStrategies.vote` - Select most common result
     * - `MergeStrategies.average` - Average numeric results
     * - `MergeStrategies.asMap` - Return all as map (no merging)
     *
     * @param id Unique identifier for this merge node
     * @param parallelNodeId ID of the ParallelNode whose results to merge
     * @param merger Function that combines parallel branch results into single output
     */
    fun merge(
        id: String,
        parallelNodeId: String,
        merger: (Map<String, Any?>) -> Any?
    ) {
        val node = MergeNode(
            id = id,
            parallelNodeId = parallelNodeId,
            merger = merger
        )
        nodes[id] = node
        connectToPrevious(id)
        lastNodeId = id
    }

    /**
     * Add a conditional edge between two nodes.
     * This allows explicit control over graph flow based on node results.
     *
     * Explicit edges take priority over automatic sequential edges.
     * If you define any explicit edge from a node, automatic edges from that node are ignored.
     *
     * @param from Source node ID
     * @param to Destination node ID
     * @param condition Predicate that determines if this edge should be followed (default: always true)
     */
    fun edge(from: String, to: String, condition: (io.github.noailabs.spice.graph.NodeResult) -> Boolean = { true }) {
        explicitEdges.add(Edge(from = from, to = to, condition = condition))
    }

    /**
     * Automatically connect the current node to the previous node.
     * These auto-edges are only used if no explicit edge is defined from the same node.
     */
    private fun connectToPrevious(currentId: String) {
        lastNodeId?.let { prevId ->
            autoEdges.add(Edge(from = prevId, to = currentId))
        }
    }

    /**
     * Build the final Graph instance.
     *
     * Merges explicit and automatic edges with the following priority:
     * 1. All explicit edges are included
     * 2. Automatic edges are only included if there is NO explicit edge from the same source node
     *
     * This ensures that user-defined conditional edges take precedence over sequential flow.
     */
    fun build(): Graph {
        require(nodes.isNotEmpty()) { "Graph must have at least one node" }

        // Collect all source nodes that have explicit edges
        val explicitFromNodes = explicitEdges.map { it.from }.toSet()

        // Filter out auto-edges that conflict with explicit edges
        val nonConflictingAutoEdges = autoEdges.filterNot { it.from in explicitFromNodes }

        // Merge: explicit edges first, then non-conflicting auto-edges
        val finalEdges = explicitEdges + nonConflictingAutoEdges

        return Graph(
            id = id,
            nodes = nodes,
            edges = finalEdges,
            entryPoint = nodes.keys.first(),
            middleware = middlewares
        )
    }
}
