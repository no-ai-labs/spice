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
import io.github.noailabs.spice.graph.nodes.EngineDecisionNode
import io.github.noailabs.spice.graph.nodes.OutputNode
import io.github.noailabs.spice.graph.nodes.SubgraphNode
import io.github.noailabs.spice.graph.nodes.ToolNode
import io.github.noailabs.spice.template.TemplateResolver
import io.github.noailabs.spice.routing.DecisionEngine
import io.github.noailabs.spice.routing.DecisionLifecycleListener
import io.github.noailabs.spice.hitl.HITLMetadata
import io.github.noailabs.spice.hitl.HITLOption
import io.github.noailabs.spice.hitl.HitlEventEmitter
import io.github.noailabs.spice.hitl.NoOpHitlEventEmitter
import io.github.noailabs.spice.idempotency.IdempotencyStore
import io.github.noailabs.spice.retry.ExecutionRetryPolicy
import io.github.noailabs.spice.ToolRegistry
import io.github.noailabs.spice.tool.ToolLifecycleListener
import io.github.noailabs.spice.tool.ToolLifecycleListeners
import io.github.noailabs.spice.tool.ToolResolver
import io.github.noailabs.spice.tool.ToolResolverValidation
import io.github.noailabs.spice.tools.HitlRequestInputTool
import io.github.noailabs.spice.tools.HitlRequestSelectionTool
import mu.KotlinLogging
import kotlin.time.Duration

private val logger = KotlinLogging.logger {}

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
 *     hitlSelection("select", "Please select a reservation") {
 *         option("res1", "Reservation 1")
 *         option("res2", "Reservation 2")
 *     }
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
    private var retryPolicy: ExecutionRetryPolicy? = null
    private var retryEnabled: Boolean? = null
    private var validateResolvers = true
    private var hitlEventEmitter: HitlEventEmitter = NoOpHitlEventEmitter

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
     * Add a tool node with static tool binding
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
     * Add a tool node with dynamic tool resolution (1.0.5+)
     *
     * Supports both static and dynamic tool selection:
     * - Static: `tool("search", ToolResolver.static(searchTool))`
     * - Dynamic: `tool("fetch", ToolResolver.byRegistry { msg -> msg.getData("toolId")!! })`
     * - Fallback: `tool("smart", ToolResolver.fallback(primary, secondary))`
     *
     * **Example:**
     * ```kotlin
     * graph("generic-workflow") {
     *     // Dynamic tool selection from registry
     *     tool("dynamic-fetch", ToolResolver.byRegistry(
     *         nameSelector = { msg -> msg.getData<String>("toolId")!! },
     *         namespace = "stayfolio",
     *         expectedTools = setOf("list_reservations", "list_coupons"),
     *         strict = false  // WARNING on missing (default)
     *     )) { message ->
     *         mapOf("userId" to message.getMetadata<String>("userId"))
     *     }
     *
     *     // Custom dynamic selection
     *     tool("smart-tool", ToolResolver.dynamic("complexity-based") { msg ->
     *         val complexity = msg.getData<Int>("complexity") ?: 0
     *         if (complexity > 50) SpiceResult.success(advancedTool)
     *         else SpiceResult.success(simpleTool)
     *     })
     * }
     * ```
     *
     * @param id Node ID
     * @param resolver ToolResolver for static or dynamic tool selection
     * @param paramMapper Function to extract tool parameters from message
     * @since 1.0.5
     */
    fun tool(
        id: String,
        resolver: ToolResolver,
        paramMapper: (SpiceMessage) -> Map<String, Any?> = { it.data }
    ) {
        nodes[id] = ToolNode(id, resolver, paramMapper)
        if (entryPoint == null) entryPoint = id
    }

    /**
     * Configure HITL event emitter for external system notification
     *
     * The emitter is used by HITL tools to notify external systems
     * (UI, webhook, message queue) when user input is required.
     *
     * @param emitter Event emitter implementation
     * @since 1.0.6
     */
    fun hitlEventEmitter(emitter: HitlEventEmitter) {
        this.hitlEventEmitter = emitter
    }

    /**
     * Add a HITL input node for free-form text input (1.0.6+)
     *
     * This creates a ToolNode with HitlRequestInputTool bound to it.
     * When executed, it returns WAITING_HITL status and pauses graph execution.
     *
     * **Usage:**
     * ```kotlin
     * graph("form-flow") {
     *     hitlInput("get-name", "What is your name?")
     *     hitlInput("get-email", "What is your email?") {
     *         validation("email", "pattern" to "^[\\w.-]+@[\\w.-]+\\.\\w+\$")
     *         timeout(60_000)  // 60 seconds
     *     }
     *     output("result")
     *
     *     edge("get-name", "get-email")
     *     edge("get-email", "result")
     * }
     * ```
     *
     * @param id Node ID (also used for stable tool_call_id generation)
     * @param prompt Message displayed to the user
     * @param config Optional configuration block
     * @since 1.0.6
     */
    fun hitlInput(
        id: String,
        prompt: String,
        config: HitlInputConfigBuilder.() -> Unit = {}
    ) {
        val configBuilder = HitlInputConfigBuilder().apply(config)
        val tool = HitlRequestInputTool(hitlEventEmitter)

        nodes[id] = ToolNode(id, tool) { message ->
            // Get and increment invocation index for loop-safe ID generation
            val currentIndex = (message.data[HITLMetadata.INVOCATION_INDEX_KEY] as? Number)?.toInt() ?: 0
            val nextIndex = currentIndex + 1

            HitlRequestInputTool.params(
                prompt = prompt,
                validationRules = configBuilder.validationRules,
                timeout = configBuilder.timeout
            ) + (HITLMetadata.INVOCATION_INDEX_KEY to nextIndex)
        }
        if (entryPoint == null) entryPoint = id
    }

    /**
     * Add a HITL selection node for predefined option selection (1.0.6+)
     *
     * This creates a ToolNode with HitlRequestSelectionTool bound to it.
     * When executed, it returns WAITING_HITL status and pauses graph execution.
     *
     * **Usage:**
     * ```kotlin
     * graph("booking-flow") {
     *     hitlSelection("select-room", "Select a room type") {
     *         option("standard", "Standard Room", "Basic room with 1 bed")
     *         option("deluxe", "Deluxe Room", "Premium room with 2 beds")
     *         option("suite", "Suite", "Luxury suite with living area")
     *         selectionType("single")  // or "multiple"
     *         timeout(120_000)  // 2 minutes
     *     }
     *     output("result")
     *
     *     edge("select-room", "result")
     * }
     * ```
     *
     * @param id Node ID (also used for stable tool_call_id generation)
     * @param prompt Message displayed to the user
     * @param config Configuration block for options and settings
     * @since 1.0.6
     */
    fun hitlSelection(
        id: String,
        prompt: String,
        config: HitlSelectionConfigBuilder.() -> Unit
    ) {
        val configBuilder = HitlSelectionConfigBuilder().apply(config)
        val tool = HitlRequestSelectionTool(hitlEventEmitter)

        nodes[id] = ToolNode(id, tool) { message ->
            // Get and increment invocation index for loop-safe ID generation
            val currentIndex = (message.data[HITLMetadata.INVOCATION_INDEX_KEY] as? Number)?.toInt() ?: 0
            val nextIndex = currentIndex + 1

            HitlRequestSelectionTool.params(
                prompt = prompt,
                options = configBuilder.options,
                selectionType = configBuilder.selectionType,
                timeout = configBuilder.timeout
            ) + (HITLMetadata.INVOCATION_INDEX_KEY to nextIndex)
        }
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
     * Add a decision node powered by an external DecisionEngine (1.0.7+)
     *
     * Unlike [decision] which uses inline conditions, decisionNode delegates
     * routing decisions to an external [DecisionEngine]. This enables:
     * - ML/LLM-based routing decisions
     * - External service consultation
     * - Complex business logic encapsulation
     * - Easy testing via engine mocking
     *
     * **Example:**
     * ```kotlin
     * graph("approval-flow") {
     *     decisionNode("check-amount")
     *         .by(amountClassifier)
     *         .on(StandardResult.YES).to("auto-approve")
     *         .on(StandardResult.NO).to("reject")
     *         .on(DelegationResult.DELEGATE_TO_AGENT("supervisor")).to("supervisor-review")
     *         .otherwise("manual-review")
     *
     *     agent("auto-approve", autoApproveAgent)
     *     agent("reject", rejectAgent)
     *     agent("supervisor-review", supervisorAgent)
     *     agent("manual-review", manualAgent)
     *     output("done")
     *
     *     edge("auto-approve", "done")
     *     edge("reject", "done")
     *     edge("supervisor-review", "done")
     *     edge("manual-review", "done")
     * }
     * ```
     *
     * **Important:** Routing is based on [DecisionResult.resultId], not object equality.
     * Two results with the same resultId will route to the same target.
     *
     * @param id Node ID
     * @return EngineDecisionNodeBuilder for fluent configuration
     * @since 1.0.7
     */
    fun decisionNode(id: String): EngineDecisionNodeBuilder {
        val builder = EngineDecisionNodeBuilder(id)
        // Store builder for deferred processing in build()
        pendingEngineDecisionNodes[id] = builder
        if (entryPoint == null) entryPoint = id
        return builder
    }

    // Storage for deferred processing of engine decision nodes
    private val pendingEngineDecisionNodes = mutableMapOf<String, EngineDecisionNodeBuilder>()

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
     *         hitlInput("input", "Enter value")
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
     * - If child pauses at HITL node, parent also pauses
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
     * Add a subgraph node with inputMapping and outputMapping (1.2.0+)
     *
     * **inputMapping (Parent → Child):**
     * Maps parent context to child's initial data using template expressions.
     * Format: `childKey → template/literal`
     *
     * **outputMapping (Child → Parent):**
     * Maps child's output data to parent's context after subgraph completes.
     * Format: `childKey → parentKey`
     *
     * **Example:**
     * ```kotlin
     * graph("reservation-cancel") {
     *     tool("list_bookings", listTool)
     *     hitlSelection("select_reservation", "Select booking to cancel") { ... }
     *
     *     // Subgraph with mapping
     *     subgraph(
     *         id = "confirm_cancel",
     *         childGraph = genericConfirmationGraph,
     *         inputMapping = mapOf(
     *             "preselectedItemId" to "{{data.selectedBookingId}}",
     *             "confirmationType" to "cancel"
     *         ),
     *         outputMapping = mapOf(
     *             "confirmed" to "user_confirm"
     *         )
     *     )
     *
     *     decision("check_confirm") {
     *         "execute_cancel".whenData("user_confirm") { it == "true" }
     *         "cancel_aborted".otherwise()
     *     }
     *
     *     edge("list_bookings", "select_reservation")
     *     edge("select_reservation", "confirm_cancel")
     *     edge("confirm_cancel", "check_confirm")
     * }
     * ```
     *
     * @param id Subgraph node ID
     * @param childGraph Pre-built child graph
     * @param inputMapping Parent → Child mapping (childKey → template)
     * @param outputMapping Child → Parent mapping (childKey → parentKey)
     * @param maxDepth Maximum nesting depth (default: 10)
     * @param preserveKeys Metadata keys to preserve across boundaries
     * @param templateResolver Custom template resolver (default: Spice's built-in)
     * @since 1.2.0
     */
    fun subgraph(
        id: String,
        childGraph: Graph,
        inputMapping: Map<String, Any> = emptyMap(),
        outputMapping: Map<String, String> = emptyMap(),
        maxDepth: Int = 10,
        preserveKeys: Set<String> = SubgraphNode.DEFAULT_PRESERVE_KEYS,
        templateResolver: TemplateResolver = TemplateResolver.DEFAULT
    ) {
        val subgraphNode = SubgraphNode(
            id = id,
            childGraph = childGraph,
            maxDepth = maxDepth,
            preserveKeys = preserveKeys,
            inputMapping = inputMapping,
            outputMapping = outputMapping,
            templateResolver = templateResolver
        )

        nodes[id] = subgraphNode
        if (entryPoint == null) entryPoint = id
    }

    /**
     * Add a subgraph node with DSL block, inputMapping, and outputMapping (1.2.0+)
     *
     * Combines inline child graph definition with data mapping.
     *
     * **Example:**
     * ```kotlin
     * graph("parent") {
     *     subgraph(
     *         id = "confirmation",
     *         inputMapping = mapOf("itemId" to "{{data.selectedId}}"),
     *         outputMapping = mapOf("result" to "confirmationResult")
     *     ) {
     *         hitlSelection("confirm", "Confirm action?") {
     *             option("yes", "Yes")
     *             option("no", "No")
     *         }
     *         output("done") { msg -> msg.getData<String>("user_response") }
     *         edge("confirm", "done")
     *     }
     * }
     * ```
     *
     * @param id Subgraph node ID (also used as child graph ID)
     * @param inputMapping Parent → Child mapping (childKey → template)
     * @param outputMapping Child → Parent mapping (childKey → parentKey)
     * @param maxDepth Maximum nesting depth (default: 10)
     * @param preserveKeys Metadata keys to preserve across boundaries
     * @param templateResolver Custom template resolver (default: Spice's built-in)
     * @param block DSL block for defining child graph
     * @since 1.2.0
     */
    fun subgraph(
        id: String,
        inputMapping: Map<String, Any> = emptyMap(),
        outputMapping: Map<String, String> = emptyMap(),
        maxDepth: Int = 10,
        preserveKeys: Set<String> = SubgraphNode.DEFAULT_PRESERVE_KEYS,
        templateResolver: TemplateResolver = TemplateResolver.DEFAULT,
        block: GraphBuilder.() -> Unit
    ) {
        // Build child graph using nested DSL
        val childBuilder = GraphBuilder(id)
        childBuilder.block()
        val childGraph = childBuilder.build()

        val subgraphNode = SubgraphNode(
            id = id,
            childGraph = childGraph,
            maxDepth = maxDepth,
            preserveKeys = preserveKeys,
            inputMapping = inputMapping,
            outputMapping = outputMapping,
            templateResolver = templateResolver
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
     * Skip ToolResolver validation at build time.
     *
     * Use this when building graphs before the ToolRegistry is populated
     * (e.g., in tests or early application wiring). By default, validation
     * is enabled and will log warnings/errors for missing expected tools.
     *
     * **Usage:**
     * ```kotlin
     * graph("early-build") {
     *     skipResolverValidation()  // Don't validate - registry not ready yet
     *     tool("dynamic", ToolResolver.byRegistry(...))
     * }
     * ```
     *
     * @since 1.0.5
     */
    fun skipResolverValidation() {
        this.validateResolvers = false
    }

    /**
     * Configure retry policy for automatic retry on retryable errors.
     *
     * When configured, ToolNodes that return retryable errors (5xx, 429, timeout, etc.)
     * will be automatically retried according to this policy.
     *
     * **Default Behavior:**
     * - If not configured, no automatic retry is performed
     * - Use ExecutionRetryPolicy.DEFAULT for standard retry: 3 attempts, 200→400→800ms
     *
     * **Usage:**
     * ```kotlin
     * graph("resilient-workflow") {
     *     // Use default retry policy
     *     retryPolicy(ExecutionRetryPolicy.DEFAULT)
     *
     *     // Or customize
     *     retryPolicy(ExecutionRetryPolicy(
     *         maxAttempts = 5,
     *         initialDelay = 500.milliseconds,
     *         maxDelay = 30.seconds,
     *         backoffMultiplier = 2.0
     *     ))
     *
     *     tool("external-api", apiTool)
     *     output("result")
     *     edge("external-api", "result")
     * }
     * ```
     *
     * **Retryable Errors:**
     * - HTTP 5xx (server errors)
     * - HTTP 408 (Request Timeout)
     * - HTTP 429 (Too Many Requests) - respects Retry-After header
     * - Network exceptions (SocketException, ConnectException, etc.)
     * - SpiceError.RetryableError
     *
     * **Non-Retryable Errors:**
     * - HTTP 4xx (except 408, 429)
     * - Validation errors
     * - Authentication errors
     *
     * @param policy Retry policy to apply
     * @since 1.0.4
     */
    fun retryPolicy(policy: ExecutionRetryPolicy) {
        this.retryPolicy = policy
    }

    /**
     * Enable default retry policy.
     *
     * Shorthand for `retryPolicy(ExecutionRetryPolicy.DEFAULT)`.
     *
     * @since 1.0.4
     */
    fun enableRetry() {
        this.retryPolicy = ExecutionRetryPolicy.DEFAULT
        this.retryEnabled = true
    }

    /**
     * Disable retry for this graph.
     *
     * Explicitly disables retry even if the runner has a default policy.
     * Equivalent to setting maxAttempts = 0 (single attempt, no retries).
     *
     * **Usage:**
     * ```kotlin
     * graph("no-retry-workflow") {
     *     disableRetry()  // No automatic retry even if runner has default policy
     *     tool("api", apiTool)
     * }
     * ```
     *
     * @since 1.0.4
     */
    fun disableRetry() {
        this.retryPolicy = ExecutionRetryPolicy.NO_RETRY
        this.retryEnabled = false
    }

    /**
     * Process pending engine decision nodes added via decisionNode() DSL.
     *
     * This method:
     * 1. Builds each EngineDecisionNode from its builder
     * 2. Adds the node to the nodes map
     * 3. Adds auto-generated edges from the builder
     * 4. Clears the pending map
     */
    private fun processPendingEngineDecisionNodes() {
        if (pendingEngineDecisionNodes.isEmpty()) return

        logger.debug { "[Graph '$id'] Processing ${pendingEngineDecisionNodes.size} pending engine decision nodes" }

        for ((nodeId, builder) in pendingEngineDecisionNodes) {
            try {
                // Build the node
                val node = builder.build()
                nodes[nodeId] = node

                // Add auto-generated edges
                edges.addAll(builder.generatedEdges)

                logger.debug {
                    "[Graph '$id'] Added EngineDecisionNode '$nodeId' with engine '${node.engineId}' " +
                    "and ${builder.generatedEdges.size} edges"
                }
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Failed to build EngineDecisionNode '$nodeId': ${e.message}",
                    e
                )
            }
        }

        pendingEngineDecisionNodes.clear()
    }

    /**
     * Validate all ToolNode resolvers at build time.
     *
     * Iterates through all nodes, finds ToolNodes, and validates their resolvers
     * against the current ToolRegistry state.
     *
     * Validation is skipped if:
     * - skipResolverValidation() was called
     * - ToolRegistry is empty (tools not yet registered)
     *
     * @throws IllegalStateException if any resolver validation returns ERROR level
     */
    private fun validateToolResolvers() {
        // Skip if explicitly disabled
        if (!validateResolvers) {
            logger.debug { "[Graph '$id'] ToolResolver validation skipped (explicitly disabled)" }
            return
        }

        // Skip if registry is empty (tools not yet registered)
        if (ToolRegistry.getAll().isEmpty()) {
            logger.debug { "[Graph '$id'] ToolResolver validation skipped (registry empty)" }
            return
        }

        val errors = mutableListOf<String>()

        nodes.values.filterIsInstance<ToolNode>().forEach { toolNode ->
            val validations = toolNode.resolver.validate(ToolRegistry)

            validations.forEach { validation ->
                val message = "[Graph '$id', Node '${toolNode.id}'] ${validation.message}"
                when (validation.level) {
                    ToolResolverValidation.Level.INFO -> logger.info { message }
                    ToolResolverValidation.Level.WARNING -> logger.warn { message }
                    ToolResolverValidation.Level.ERROR -> {
                        logger.error { message }
                        errors.add(message)
                    }
                }
            }
        }

        if (errors.isNotEmpty()) {
            throw IllegalStateException(
                "ToolResolver validation failed with ${errors.size} error(s):\n" +
                    errors.joinToString("\n") { "  - $it" }
            )
        }
    }

    /**
     * Build the graph
     *
     * Validates all ToolNode resolvers at build time:
     * - INFO messages are logged for dynamic resolvers
     * - WARNING messages are logged for missing expected tools (non-strict)
     * - ERROR messages throw IllegalStateException (strict mode)
     */
    fun build(): Graph {
        // Process pending engine decision nodes
        processPendingEngineDecisionNodes()

        require(nodes.isNotEmpty()) { "Graph must have at least one node" }
        require(entryPoint != null) { "Graph must have an entry point" }
        require(entryPoint in nodes) { "Entry point '$entryPoint' not found in nodes" }

        // Validate ToolNode resolvers at build time
        validateToolResolvers()

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
            toolLifecycleListeners = toolLifecycleListeners,
            retryPolicy = retryPolicy,
            retryEnabled = retryEnabled
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
 * Helper: Create a HITL option for selection
 */
fun hitlOption(id: String, label: String, description: String? = null): HITLOption {
    return HITLOption(id = id, label = label, description = description)
}

// ============================================================
// HITL Configuration Builders (1.0.6+)
// ============================================================

/**
 * Configuration builder for hitlInput()
 *
 * @since 1.0.6
 */
class HitlInputConfigBuilder {
    internal var validationRules: Map<String, Any> = emptyMap()
    internal var timeout: Long? = null

    /**
     * Add a validation rule
     *
     * Common validation rules:
     * - "min_length" to 1
     * - "max_length" to 1000
     * - "pattern" to "^[\\w.-]+@[\\w.-]+\\.\\w+\$" (email regex)
     * - "required" to true
     *
     * @param name Rule name
     * @param value Rule configuration
     */
    fun validation(name: String, vararg value: Pair<String, Any>) {
        validationRules = validationRules + (name to mapOf(*value))
    }

    /**
     * Add simple validation rule
     */
    fun validation(name: String, value: Any) {
        validationRules = validationRules + (name to value)
    }

    /**
     * Set timeout in milliseconds
     */
    fun timeout(ms: Long) {
        this.timeout = ms
    }

    /**
     * Set timeout using Duration
     */
    fun timeout(duration: Duration) {
        this.timeout = duration.inWholeMilliseconds
    }
}

/**
 * Configuration builder for hitlSelection()
 *
 * @since 1.0.6
 */
class HitlSelectionConfigBuilder {
    internal val options = mutableListOf<HITLOption>()
    internal var selectionType: String = "single"
    internal var timeout: Long? = null

    /**
     * Add an option with id and label
     */
    fun option(id: String, label: String) {
        options.add(HITLOption(id = id, label = label))
    }

    /**
     * Add an option with id, label, and description
     */
    fun option(id: String, label: String, description: String) {
        options.add(HITLOption(id = id, label = label, description = description))
    }

    /**
     * Add an option with full configuration
     */
    fun option(
        id: String,
        label: String,
        description: String? = null,
        metadata: Map<String, Any> = emptyMap()
    ) {
        options.add(HITLOption(
            id = id,
            label = label,
            description = description,
            metadata = metadata
        ))
    }

    /**
     * Add a pre-built option
     */
    fun option(opt: HITLOption) {
        options.add(opt)
    }

    /**
     * Add multiple options at once
     */
    fun options(vararg opts: HITLOption) {
        options.addAll(opts)
    }

    /**
     * Add options from a list
     */
    fun options(opts: List<HITLOption>) {
        options.addAll(opts)
    }

    /**
     * Set selection type: "single" or "multiple"
     */
    fun selectionType(type: String) {
        this.selectionType = type
    }

    /**
     * Allow single selection (default)
     */
    fun singleSelection() {
        this.selectionType = "single"
    }

    /**
     * Allow multiple selection
     */
    fun multipleSelection() {
        this.selectionType = "multiple"
    }

    /**
     * Set timeout in milliseconds
     */
    fun timeout(ms: Long) {
        this.timeout = ms
    }

    /**
     * Set timeout using Duration
     */
    fun timeout(duration: Duration) {
        this.timeout = duration.inWholeMilliseconds
    }
}
