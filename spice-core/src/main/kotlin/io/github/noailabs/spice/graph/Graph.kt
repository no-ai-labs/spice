package io.github.noailabs.spice.graph
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.event.ToolCallEventBus
import io.github.noailabs.spice.events.EventBus
import io.github.noailabs.spice.graph.checkpoint.CheckpointStore
import io.github.noailabs.spice.graph.middleware.Middleware
import io.github.noailabs.spice.idempotency.IdempotencyStore
import io.github.noailabs.spice.tool.ToolLifecycleListeners

/**
 * 🕸️ Graph for Spice Framework 1.0.0
 *
 * Represents a directed graph of nodes with execution flow control.
 *
 * **BREAKING CHANGE from 0.x:**
 * - EventBus integration (optional)
 * - IdempotencyStore integration (optional)
 * - Edge conditions use SpiceMessage instead of NodeResult
 * - Built-in state machine support
 *
 * **New Features (1.0.0):**
 * - Event-driven workflows via EventBus
 * - Idempotent execution via IdempotencyStore
 * - Async node execution support
 * - Better observability hooks
 *
 * **New Features (2.0.0):**
 * - Tool call event streaming via ToolCallEventBus
 * - Type-safe event subscriptions for tool call lifecycle
 * - Multi-agent orchestration support
 *
 * **Usage:**
 * ```kotlin
 * val graph = graph("workflow") {
 *     // Optional: Add event bus
 *     eventBus(RedisStreamEventBus(redis))
 *
 *     // Optional: Add tool call event bus (Spice 2.0)
 *     toolCallEventBus(InMemoryToolCallEventBus())
 *
 *     // Optional: Add idempotency
 *     idempotencyStore(RedisIdempotencyStore(redis))
 *
 *     // Define nodes
 *     agent("process", myAgent)
 *     output("result")
 *
 *     // Define edges
 *     edge("process", "result")
 * }
 * ```
 *
 * @property id Unique graph identifier
 * @property nodes Map of node ID to Node instance
 * @property edges List of edges defining flow control
 * @property entryPoint ID of the starting node
 * @property middleware List of middleware for cross-cutting concerns
 * @property allowCycles Whether to allow cycles in the graph (default: false for DAG)
 * @property eventBus Optional event bus for pub/sub (topic-based)
 * @property toolCallEventBus Optional tool call event bus for type-safe tool call lifecycle events (Spice 2.0)
 * @property idempotencyStore Optional store for idempotent execution
 * @property checkpointStore Optional store for checkpoint persistence (HITL workflows)
 * @property toolLifecycleListeners Optional listeners for tool execution lifecycle events (telemetry, metrics, alerting)
 * @since 1.0.0
 */
data class Graph(
    val id: String,
    val nodes: Map<String, Node>,
    val edges: List<Edge>,
    val entryPoint: String,
    val middleware: List<Middleware> = emptyList(),
    val allowCycles: Boolean = false,
    val eventBus: EventBus? = null,
    val toolCallEventBus: ToolCallEventBus? = null,
    val idempotencyStore: IdempotencyStore? = null,
    val checkpointStore: CheckpointStore? = null,
    val toolLifecycleListeners: ToolLifecycleListeners? = null
)

/**
 * 🔗 Edge for Spice Framework 1.0.0
 *
 * Represents a directed edge between two nodes with conditional routing.
 *
 * **BREAKING CHANGE from 0.x:**
 * - Condition parameter changed from `(NodeResult) -> Boolean` to `(SpiceMessage) -> Boolean`
 * - Can now access full message context, state, tool calls, etc.
 *
 * **Edge Routing:**
 * - Edges are evaluated in priority order (lower number = higher priority)
 * - First matching edge is followed
 * - Fallback edges used when no regular edges match
 *
 * **Usage:**
 * ```kotlin
 * // Simple edge
 * edge("nodeA", "nodeB")
 *
 * // Conditional edge based on message state
 * edge("nodeA", "nodeB") { message ->
 *     message.state == ExecutionState.RUNNING
 * }
 *
 * // Conditional edge based on metadata
 * edge("nodeA", "nodeB") { message ->
 *     message.getMetadata<String>("status") == "success"
 * }
 *
 * // Priority routing
 * edge("nodeA", "nodeB", priority = 1)  // Checked first
 * edge("nodeA", "nodeC", priority = 2)  // Checked second
 *
 * // Fallback edge
 * edge("nodeA", "error", isFallback = true)  // Used when no other edges match
 * ```
 *
 * @property from Source node ID (use "*" for wildcard matching all nodes)
 * @property to Target node ID
 * @property priority Lower values evaluated first (default: 0)
 * @property isFallback Only used when no regular edges match (default: false)
 * @property name Optional name for debugging
 * @property condition Predicate to determine if edge should be followed
 * @since 1.0.0
 */
data class Edge(
    val from: String,
    val to: String,
    val priority: Int = 0,
    val isFallback: Boolean = false,
    val name: String? = null,
    val condition: (SpiceMessage) -> Boolean = { true }
)

/**
 * 🔗 Edge Group Builder
 *
 * Builder for creating edges with multiple conditions.
 * Supports OR and AND composition of conditions.
 *
 * **BREAKING CHANGE from 0.x:**
 * - Conditions use SpiceMessage instead of NodeResult
 *
 * **Usage:**
 * ```kotlin
 * edgeGroup("nodeA", "nodeB")
 *     .where { it.getMetadata<String>("status") == "success" }
 *     .andWhen { it.hasToolCalls() }
 *     .build()
 *
 * edgeGroup("nodeA", "nodeC")
 *     .where { it.state == ExecutionState.FAILED }
 *     .orWhen { it.getMetadata<Boolean>("skipable") == true }
 *     .build()
 * ```
 *
 * @since 1.0.0
 */
class EdgeGroup(
    val from: String,
    val to: String,
    val priority: Int = 0,
    private var name: String? = null
) {
    private val conditions = mutableListOf<(SpiceMessage) -> Boolean>()
    private var combineMode: CombineMode? = null

    enum class CombineMode { OR, AND }

    /**
     * Add a condition (first condition, doesn't set combine mode)
     */
    fun where(condition: (SpiceMessage) -> Boolean): EdgeGroup {
        conditions.add(condition)
        return this
    }

    /**
     * Add an OR condition (any condition must match)
     */
    fun orWhen(condition: (SpiceMessage) -> Boolean): EdgeGroup {
        if (combineMode == null) {
            combineMode = CombineMode.OR
        } else if (combineMode == CombineMode.AND) {
            throw IllegalStateException("Cannot mix AND and OR conditions in same EdgeGroup")
        }
        conditions.add(condition)
        return this
    }

    /**
     * Add an AND condition (all conditions must match)
     */
    fun andWhen(condition: (SpiceMessage) -> Boolean): EdgeGroup {
        if (combineMode == null) {
            combineMode = CombineMode.AND
        } else if (combineMode == CombineMode.OR) {
            throw IllegalStateException("Cannot mix OR and AND conditions in same EdgeGroup")
        }
        conditions.add(condition)
        return this
    }

    /**
     * Metadata helper: Check if metadata key equals specific value
     */
    fun whenMetadata(key: String, equals: Any?): EdgeGroup {
        conditions.add { message -> message.metadata[key] == equals }
        return this
    }

    /**
     * Metadata helper: Check if metadata key is not null
     */
    fun whenMetadataNotNull(key: String): EdgeGroup {
        conditions.add { message -> message.metadata[key] != null }
        return this
    }

    /**
     * Data helper: Check if data key equals specific value
     */
    fun whenData(key: String, equals: Any?): EdgeGroup {
        conditions.add { message -> message.data[key] == equals }
        return this
    }

    /**
     * State helper: Check if message is in specific state
     */
    fun whenState(state: io.github.noailabs.spice.ExecutionState): EdgeGroup {
        conditions.add { message -> message.state == state }
        return this
    }

    /**
     * Tool call helper: Check if message has tool calls
     */
    fun whenHasToolCalls(): EdgeGroup {
        conditions.add { message -> message.hasToolCalls() }
        return this
    }

    /**
     * Tool call helper: Check if message has specific tool call
     */
    fun whenHasToolCall(functionName: String): EdgeGroup {
        conditions.add { message -> message.hasToolCall(functionName) }
        return this
    }

    /**
     * Set edge name (for debugging)
     */
    fun named(name: String): EdgeGroup {
        this.name = name
        return this
    }

    /**
     * Build the final Edge
     */
    fun build(): Edge {
        val finalCondition: (SpiceMessage) -> Boolean = when {
            conditions.isEmpty() -> {{ true }}
            combineMode == CombineMode.OR || conditions.size == 1 ->
                { message -> conditions.any { it(message) } }
            combineMode == CombineMode.AND ->
                { message -> conditions.all { it(message) } }
            else -> {{ true }}  // Shouldn't happen
        }

        return Edge(
            from = from,
            to = to,
            priority = priority,
            name = name,
            condition = finalCondition
        )
    }
}
