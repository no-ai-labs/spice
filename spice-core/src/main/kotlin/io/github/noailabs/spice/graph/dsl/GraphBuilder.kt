package io.github.noailabs.spice.graph.dsl

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.event.ToolCallEventBus
import io.github.noailabs.spice.events.EventBus
import io.github.noailabs.spice.graph.Edge
import io.github.noailabs.spice.graph.Graph
import io.github.noailabs.spice.graph.Node
import io.github.noailabs.spice.graph.checkpoint.CheckpointStore
import io.github.noailabs.spice.graph.middleware.Middleware
import io.github.noailabs.spice.graph.nodes.AgentNode
import io.github.noailabs.spice.graph.nodes.DecisionNode
import io.github.noailabs.spice.graph.nodes.HumanNode
import io.github.noailabs.spice.graph.nodes.HumanOption
import io.github.noailabs.spice.graph.nodes.OutputNode
import io.github.noailabs.spice.graph.nodes.SubgraphNode
import io.github.noailabs.spice.graph.nodes.ToolNode
import io.github.noailabs.spice.idempotency.IdempotencyStore
import io.github.noailabs.spice.tool.ToolLifecycleListener
import io.github.noailabs.spice.tool.ToolLifecycleListeners
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
    private var toolCallEventBus: ToolCallEventBus? = null
    private var idempotencyStore: IdempotencyStore? = null
    private var checkpointStore: CheckpointStore? = null
    private var toolLifecycleListeners: ToolLifecycleListeners? = null

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
     * Add a decision node for conditional routing
     *
     * **Example:**
     * ```kotlin
     * decision("check_payment") {
     *     branch("has_payment", "payment_handler")
     *         .whenData("paymentMethod") { it != null }
     *     branch("no_payment", "default_handler")
     *         .otherwise()
     * }
     * ```
     *
     * Or shorter syntax:
     * ```kotlin
     * decision("route") {
     *     "handler_a".whenData("type") { it == "A" }
     *     "handler_b".whenData("type") { it == "B" }
     *     "default".otherwise()
     * }
     * ```
     *
     * @param id Node ID
     * @param block DSL block for defining branches
     */
    fun decision(
        id: String,
        block: DecisionNodeBuilder.() -> Unit
    ) {
        val builder = DecisionNodeBuilder(id)
        builder.block()
        nodes[id] = builder.build()
        // Add auto-generated edges from decision node to targets
        edges.addAll(builder.generatedEdges)
        if (entryPoint == null) entryPoint = id
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
     * Add a subgraph node that executes a child graph
     *
     * **Example:**
     * ```kotlin
     * graph("parent") {
     *     agent("start", startAgent)
     *
     *     subgraph("child-workflow") {
     *         agent("step1", agent1)
     *         human("input", "Enter value")
     *         output("result")
     *
     *         edge("step1", "input")
     *         edge("input", "result")
     *     }
     *
     *     agent("end", endAgent)
     *
     *     edge("start", "child-workflow")
     *     edge("child-workflow", "end")
     * }
     * ```
     *
     * **Context Propagation:**
     * - Metadata (userId, tenantId, traceId, etc.) flows to child
     * - Child's subgraphDepth is parent's depth + 1
     * - Child's result is available in parent's data as "subgraph_result"
     *
     * **HITL Support:**
     * - If child pauses at HumanNode, parent also pauses
     * - Resume propagates through parent → child
     * - Checkpoints are namespaced: `{parentRunId}:subgraph:{childId}`
     *
     * @param id Subgraph node ID (also used as child graph ID)
     * @param maxDepth Maximum nesting depth (default: 10)
     * @param preserveKeys Metadata keys to preserve across boundaries
     * @param block DSL block for defining child graph
     */
    fun subgraph(
        id: String,
        maxDepth: Int = 10,
        preserveKeys: Set<String> = SubgraphNode.DEFAULT_PRESERVE_KEYS,
        block: GraphBuilder.() -> Unit
    ) {
        // Build child graph using nested DSL
        val childBuilder = GraphBuilder(id)
        childBuilder.block()
        val childGraph = childBuilder.build()

        // Create SubgraphNode (runtime runner will be injected by GraphRunner)
        val subgraphNode = SubgraphNode(
            id = id,
            childGraph = childGraph,
            maxDepth = maxDepth,
            preserveKeys = preserveKeys
        )

        nodes[id] = subgraphNode
        if (entryPoint == null) entryPoint = id
    }

    /**
     * Add a subgraph node with an existing Graph instance
     *
     * **Example:**
     * ```kotlin
     * val childGraph = graph("child") {
     *     agent("process", processor)
     *     output("result")
     *     edge("process", "result")
     * }
     *
     * graph("parent") {
     *     agent("start", startAgent)
     *     subgraph("child-step", childGraph)
     *     agent("end", endAgent)
     *
     *     edge("start", "child-step")
     *     edge("child-step", "end")
     * }
     * ```
     *
     * @param id Subgraph node ID
     * @param childGraph Pre-built child graph
     * @param maxDepth Maximum nesting depth (default: 10)
     * @param preserveKeys Metadata keys to preserve across boundaries
     */
    fun subgraph(
        id: String,
        childGraph: Graph,
        maxDepth: Int = 10,
        preserveKeys: Set<String> = SubgraphNode.DEFAULT_PRESERVE_KEYS
    ) {
        val subgraphNode = SubgraphNode(
            id = id,
            childGraph = childGraph,
            maxDepth = maxDepth,
            preserveKeys = preserveKeys
        )

        nodes[id] = subgraphNode
        if (entryPoint == null) entryPoint = id
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
     * Configure tool call event bus for type-safe tool call lifecycle events (Spice 2.0)
     */
    fun toolCallEventBus(toolCallEventBus: ToolCallEventBus) {
        this.toolCallEventBus = toolCallEventBus
    }

    /**
     * Configure idempotency store for duplicate detection
     */
    fun idempotencyStore(store: IdempotencyStore) {
        this.idempotencyStore = store
    }

    /**
     * Configure checkpoint store for HITL workflows
     */
    fun checkpointStore(store: CheckpointStore) {
        this.checkpointStore = store
    }

    /**
     * Configure tool lifecycle listeners for telemetry, metrics, and alerting
     *
     * **Usage:**
     * ```kotlin
     * graph("workflow") {
     *     toolLifecycleListeners(
     *         ToolLifecycleListeners.of(
     *             MetricsListener(),
     *             SlackAlertListener()
     *         )
     *     )
     *     // ...
     * }
     * ```
     */
    fun toolLifecycleListeners(listeners: ToolLifecycleListeners) {
        this.toolLifecycleListeners = listeners
    }

    /**
     * Configure tool lifecycle listeners from vararg
     *
     * **Usage:**
     * ```kotlin
     * graph("workflow") {
     *     toolLifecycleListeners(
     *         MetricsListener(),
     *         SlackAlertListener()
     *     )
     *     // ...
     * }
     * ```
     */
    fun toolLifecycleListeners(vararg listeners: ToolLifecycleListener) {
        this.toolLifecycleListeners = ToolLifecycleListeners.of(*listeners)
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
            toolCallEventBus = toolCallEventBus,
            idempotencyStore = idempotencyStore,
            checkpointStore = checkpointStore,
            toolLifecycleListeners = toolLifecycleListeners
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
