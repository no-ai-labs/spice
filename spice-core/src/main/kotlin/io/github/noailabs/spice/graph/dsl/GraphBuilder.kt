package io.github.noailabs.spice.graph.dsl

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.events.EventBus
import io.github.noailabs.spice.graph.Edge
import io.github.noailabs.spice.graph.Graph
import io.github.noailabs.spice.graph.Node
import io.github.noailabs.spice.graph.middleware.Middleware
import io.github.noailabs.spice.graph.nodes.AgentNode
import io.github.noailabs.spice.graph.nodes.HumanNode
import io.github.noailabs.spice.graph.nodes.HumanOption
import io.github.noailabs.spice.graph.nodes.OutputNode
import io.github.noailabs.spice.graph.nodes.ToolNode
import io.github.noailabs.spice.idempotency.IdempotencyStore
import kotlin.time.Duration

/**
 * 🏗️ GraphBuilder DSL for Spice Framework 1.0.0
 *
 * Fluent API for building graphs with SpiceMessage-based execution.
 *
 * **BREAKING CHANGE from 0.x:**
 * - All nodes work with SpiceMessage (not Comm/NodeContext)
 * - EventBus and IdempotencyStore are first-class citizens
 * - Edge conditions use SpiceMessage
 *
 * **Usage:**
 * ```kotlin
 * val graph = graph("booking-workflow") {
 *     // Configure event bus (optional)
 *     eventBus(myEventBus)
 *
 *     // Configure idempotency (optional)
 *     idempotencyStore(myStore)
 *
 *     // Define nodes
 *     agent("search", searchAgent)
 *     human("select", "Please select a reservation")
 *     agent("confirm", confirmAgent)
 *     output("result")
 *
 *     // Define edges
 *     edge("search", "select")
 *     edge("select", "confirm")
 *     edge("confirm", "result")
 * }
 * ```
 *
 * @since 1.0.0
 */
class GraphBuilder(val id: String) {
    private val nodes = mutableMapOf<String, Node>()
    private val edges = mutableListOf<Edge>()
    private var entryPoint: String? = null
    private val middleware = mutableListOf<Middleware>()
    private var allowCycles = false
    private var eventBus: EventBus? = null
    private var idempotencyStore: IdempotencyStore? = null

    /**
     * Add an agent node
     *
     * @param id Node ID
     * @param agent Agent instance
     */
    fun agent(id: String, agent: Agent) {
        nodes[id] = AgentNode(agent)
        if (entryPoint == null) entryPoint = id
    }

    /**
     * Add a tool node
     *
     * @param id Node ID
     * @param tool Tool instance
     * @param paramMapper Function to extract tool parameters from message
     */
    fun tool(
        id: String,
        tool: Tool,
        paramMapper: (SpiceMessage) -> Map<String, Any?> = { it.data }
    ) {
        nodes[id] = ToolNode(id, tool, paramMapper)
        if (entryPoint == null) entryPoint = id
    }

    /**
     * Add a human-in-the-loop node
     *
     * @param id Node ID
     * @param prompt Message to display to human
     * @param options Multiple choice options (optional)
     * @param timeout Timeout duration (optional)
     * @param allowFreeText Allow free-form text input
     */
    fun human(
        id: String,
        prompt: String,
        options: List<HumanOption> = emptyList(),
        timeout: Duration? = null,
        allowFreeText: Boolean = options.isEmpty()
    ) {
        nodes[id] = HumanNode(id, prompt, options, timeout, allowFreeText = allowFreeText)
        if (entryPoint == null) entryPoint = id
    }

    /**
     * Add an output node (final result)
     *
     * @param id Node ID
     * @param selector Function to extract final output from message
     */
    fun output(
        id: String,
        selector: (SpiceMessage) -> Any? = { it.content }
    ) {
        nodes[id] = OutputNode(id, selector)
    }

    /**
     * Add a custom node
     *
     * @param id Node ID (if not provided, uses node.id)
     * @param node Custom node implementation
     */
    fun node(id: String? = null, node: Node) {
        val nodeId = id ?: node.id
        nodes[nodeId] = node
        if (entryPoint == null) entryPoint = nodeId
    }

    /**
     * Add a simple edge between two nodes
     *
     * @param from Source node ID
     * @param to Target node ID
     * @param priority Lower values evaluated first (default: 0)
     * @param name Optional name for debugging
     */
    fun edge(
        from: String,
        to: String,
        priority: Int = 0,
        name: String? = null
    ) {
        edges.add(Edge(from, to, priority, name = name))
    }

    /**
     * Add a conditional edge
     *
     * @param from Source node ID
     * @param to Target node ID
     * @param priority Lower values evaluated first
     * @param name Optional name for debugging
     * @param condition Predicate to determine if edge should be followed
     */
    fun edge(
        from: String,
        to: String,
        priority: Int = 0,
        name: String? = null,
        condition: (SpiceMessage) -> Boolean
    ) {
        edges.add(Edge(from, to, priority, name = name, condition = condition))
    }

    /**
     * Add a fallback edge (used when no regular edges match)
     *
     * @param from Source node ID
     * @param to Target node ID
     * @param name Optional name for debugging
     */
    fun fallbackEdge(from: String, to: String, name: String? = null) {
        edges.add(Edge(from, to, priority = 0, isFallback = true, name = name))
    }

    /**
     * Add middleware
     */
    fun middleware(vararg middleware: Middleware) {
        this.middleware.addAll(middleware)
    }

    /**
     * Set entry point (first node to execute)
     */
    fun entryPoint(nodeId: String) {
        this.entryPoint = nodeId
    }

    /**
     * Allow cycles in the graph
     */
    fun allowCycles(allow: Boolean = true) {
        this.allowCycles = allow
    }

    /**
     * Configure event bus for event-driven workflows
     */
    fun eventBus(eventBus: EventBus) {
        this.eventBus = eventBus
    }

    /**
     * Configure idempotency store for duplicate detection
     */
    fun idempotencyStore(store: IdempotencyStore) {
        this.idempotencyStore = store
    }

    /**
     * Build the graph
     */
    fun build(): Graph {
        require(nodes.isNotEmpty()) { "Graph must have at least one node" }
        require(entryPoint != null) { "Graph must have an entry point" }
        require(entryPoint in nodes) { "Entry point '$entryPoint' not found in nodes" }

        return Graph(
            id = id,
            nodes = nodes,
            edges = edges,
            entryPoint = entryPoint!!,
            middleware = middleware,
            allowCycles = allowCycles,
            eventBus = eventBus,
            idempotencyStore = idempotencyStore
        )
    }
}

/**
 * DSL entry point for building a graph
 *
 * **Usage:**
 * ```kotlin
 * val graph = graph("my-graph") {
 *     agent("step1", myAgent)
 *     output("result")
 *     edge("step1", "result")
 * }
 * ```
 */
fun graph(id: String, block: GraphBuilder.() -> Unit): Graph {
    val builder = GraphBuilder(id)
    builder.block()
    return builder.build()
}

/**
 * 🎯 Edge Builder for complex routing logic
 *
 * **Usage:**
 * ```kotlin
 * graph("workflow") {
 *     // ... nodes ...
 *
 *     // Conditional routing based on state
 *     edge("agent", "success") { message ->
 *         message.state == ExecutionState.COMPLETED
 *     }
 *
 *     // Routing based on metadata
 *     edge("agent", "retry") { message ->
 *         message.getMetadata<Int>("retry_count")?.let { it < 3 } ?: false
 *     }
 *
 *     // Routing based on tool calls
 *     edge("agent", "human") { message ->
 *         message.hasToolCall("request_user_selection")
 *     }
 * }
 * ```
 */

/**
 * Helper: Create a human option
 */
fun humanOption(id: String, label: String, description: String? = null): HumanOption {
    return HumanOption(id, label, description)
}
