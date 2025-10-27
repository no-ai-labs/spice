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
    private val edges = mutableListOf<Edge>()
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
     * Add a conditional edge between two nodes.
     * This allows explicit control over graph flow based on node results.
     *
     * @param from Source node ID
     * @param to Destination node ID
     * @param condition Predicate that determines if this edge should be followed (default: always true)
     */
    fun edge(from: String, to: String, condition: (io.github.noailabs.spice.graph.NodeResult) -> Boolean = { true }) {
        edges.add(Edge(from = from, to = to, condition = condition))
    }

    /**
     * Automatically connect the current node to the previous node.
     */
    private fun connectToPrevious(currentId: String) {
        lastNodeId?.let { prevId ->
            edges.add(Edge(from = prevId, to = currentId))
        }
    }

    /**
     * Build the final Graph instance.
     */
    fun build(): Graph {
        require(nodes.isNotEmpty()) { "Graph must have at least one node" }

        return Graph(
            id = id,
            nodes = nodes,
            edges = edges,
            entryPoint = nodes.keys.first(),
            middleware = middlewares
        )
    }
}
