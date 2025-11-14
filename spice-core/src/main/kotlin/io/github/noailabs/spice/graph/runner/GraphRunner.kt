package io.github.noailabs.spice.graph.runner

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.events.EventBus
import io.github.noailabs.spice.graph.Edge
import io.github.noailabs.spice.graph.Graph
import io.github.noailabs.spice.graph.middleware.Middleware
import io.github.noailabs.spice.idempotency.IdempotencyKey
import io.github.noailabs.spice.idempotency.IdempotencyStore
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 🚀 GraphRunner for Spice Framework 1.0.0
 *
 * Executes graphs with SpiceMessage-based flow.
 *
 * **BREAKING CHANGE from 0.x:**
 * - Input/output is SpiceMessage (not Map/Comm)
 * - Built-in idempotency checking via IdempotencyStore
 * - Built-in event publishing via EventBus
 * - State machine transitions (READY → RUNNING → COMPLETED/FAILED)
 * - HITL support via WAITING state
 *
 * **Architecture:**
 * ```
 * Input Message (READY)
 *   ↓
 * GraphRunner.execute()
 *   ↓ [transition to RUNNING]
 * Execute Entry Node
 *   ↓
 * Follow Edges
 *   ↓
 * Execute Nodes (with idempotency + events)
 *   ↓
 * Output Message (COMPLETED/FAILED/WAITING)
 * ```
 *
 * **Usage:**
 * ```kotlin
 * val runner = DefaultGraphRunner()
 * val message = SpiceMessage.create(
 *     content = "Process this",
 *     from = "user",
 *     correlationId = "req-123"
 * )
 *
 * val result = runner.execute(graph, message)
 * ```
 *
 * @since 1.0.0
 */
interface GraphRunner {
    /**
     * Execute graph with given input message
     *
     * **Flow:**
     * 1. Validate graph structure
     * 2. Transition message to RUNNING state
     * 3. Execute nodes sequentially following edges
     * 4. Check idempotency before each node (if configured)
     * 5. Publish events before/after each node (if configured)
     * 6. Handle state transitions (WAITING for HITL, COMPLETED for success)
     * 7. Return final message
     *
     * @param graph Graph to execute
     * @param message Input message (typically READY state)
     * @return SpiceResult with final message (COMPLETED/FAILED/WAITING)
     */
    suspend fun execute(graph: Graph, message: SpiceMessage): SpiceResult<SpiceMessage>

    /**
     * Resume graph execution from WAITING state (HITL pattern)
     *
     * Used when a HumanNode paused execution and user provided input.
     *
     * @param graph Graph definition
     * @param message Message in WAITING state with human response data
     * @return SpiceResult with final message
     */
    suspend fun resume(graph: Graph, message: SpiceMessage): SpiceResult<SpiceMessage>
}

/**
 * 🔧 Default GraphRunner implementation
 *
 * Executes nodes sequentially with idempotency and event publishing.
 *
 * **Features:**
 * - Middleware chain support for cross-cutting concerns
 * - Automatic idempotency checking (if IdempotencyStore configured)
 * - Automatic event publishing (if EventBus configured)
 * - State machine validation at each step
 * - HITL pause/resume support
 *
 * @property enableIdempotency Enable idempotent execution (default: true if store configured)
 * @property enableEvents Enable event publishing (default: true if bus configured)
 * @since 1.0.0
 */
class DefaultGraphRunner(
    private val enableIdempotency: Boolean = true,
    private val enableEvents: Boolean = true
) : GraphRunner {

    /**
     * Execute graph with message
     */
    override suspend fun execute(graph: Graph, message: SpiceMessage): SpiceResult<SpiceMessage> {
        // Validate graph structure
        val validation = validateGraph(graph)
        if (validation is SpiceResult.Failure) {
            return validation
        }

        // Validate message state (should be READY or RUNNING)
        if (message.state.isTerminal()) {
            return SpiceResult.failure(
                SpiceError.executionError(
                    "Cannot execute graph with terminal message state: ${message.state}",
                    graphId = graph.id
                )
            )
        }

        // Transition to RUNNING state if not already
        val runningMessage = if (message.state == ExecutionState.READY) {
            message.transitionTo(
                newState = ExecutionState.RUNNING,
                reason = "Graph execution started",
                nodeId = graph.entryPoint
            )
        } else {
            message
        }

        // Set graph context
        val contextualMessage = runningMessage.withGraphContext(
            graphId = graph.id,
            nodeId = null,
            runId = runningMessage.runId ?: generateRunId()
        )

        // Publish graph started event
        if (enableEvents && graph.eventBus != null) {
            publishGraphStarted(graph, contextualMessage)
        }

        // Execute nodes
        return executeNodes(
            graph = graph,
            message = contextualMessage,
            startNodeId = graph.entryPoint
        )
    }

    /**
     * Resume execution from WAITING state
     */
    override suspend fun resume(graph: Graph, message: SpiceMessage): SpiceResult<SpiceMessage> {
        // Validate message is in WAITING state
        if (message.state != ExecutionState.WAITING) {
            return SpiceResult.failure(
                SpiceError.executionError(
                    "Cannot resume graph with message state: ${message.state}. Expected WAITING.",
                    graphId = graph.id
                )
            )
        }

        // Transition back to RUNNING
        val runningMessage = message.transitionTo(
            newState = ExecutionState.RUNNING,
            reason = "Resuming after human input",
            nodeId = message.nodeId
        )

        // Find next node after the WAITING node
        val nextNodeId = findNextNode(
            currentNodeId = message.nodeId ?: return SpiceResult.failure(
                SpiceError.executionError("Cannot resume: message has no nodeId", graphId = graph.id)
            ),
            message = runningMessage,
            graph = graph
        )

        // Continue execution
        return executeNodes(
            graph = graph,
            message = runningMessage,
            startNodeId = nextNodeId
        )
    }

    /**
     * Core node execution loop
     */
    private suspend fun executeNodes(
        graph: Graph,
        message: SpiceMessage,
        startNodeId: String?
    ): SpiceResult<SpiceMessage> {
        var currentMessage = message
        var currentNodeId: String? = startNodeId

        while (currentNodeId != null) {
            val nodeId = currentNodeId
            val node = graph.nodes[nodeId]
                ?: return SpiceResult.failure(
                    SpiceError.executionError("Node not found: $nodeId", graphId = graph.id, nodeId = nodeId)
                )

            // Update message with current node context
            currentMessage = currentMessage.withGraphContext(
                graphId = graph.id,
                nodeId = nodeId,
                runId = currentMessage.runId
            )

            // Check idempotency (skip if already executed)
            if (enableIdempotency && graph.idempotencyStore != null) {
                val cachedResult = checkIdempotency(graph.idempotencyStore, currentMessage, nodeId)
                if (cachedResult != null) {
                    currentMessage = cachedResult
                    currentNodeId = findNextNode(nodeId, currentMessage, graph)
                    continue
                }
            }

            // Publish node started event
            if (enableEvents && graph.eventBus != null) {
                publishNodeStarted(graph, currentMessage, nodeId)
            }

            // Execute middleware chain (onBeforeNode)
            val beforeResult = executeBeforeNodeMiddleware(graph.middleware, currentMessage)
            if (beforeResult is SpiceResult.Failure) {
                return beforeResult
            }

            // Execute node
            val result = node.run(currentMessage)

            when (result) {
                is SpiceResult.Success -> {
                    val outputMessage = result.value

                    // Execute middleware chain (onAfterNode)
                    val afterResult = executeAfterNodeMiddleware(graph.middleware, outputMessage)
                    val finalMessage = when (afterResult) {
                        is SpiceResult.Success -> afterResult.value
                        is SpiceResult.Failure -> return afterResult
                    }

                    // Save idempotency result
                    if (enableIdempotency && graph.idempotencyStore != null) {
                        saveIdempotency(graph.idempotencyStore, currentMessage, nodeId, finalMessage)
                    }

                    // Publish node completed event
                    if (enableEvents && graph.eventBus != null) {
                        publishNodeCompleted(graph, finalMessage, nodeId)
                    }

                    // Check if we need to pause for HITL
                    if (finalMessage.state == ExecutionState.WAITING) {
                        // Publish HITL requested event
                        if (enableEvents && graph.eventBus != null) {
                            publishHitlRequested(graph, finalMessage, nodeId)
                        }
                        return SpiceResult.success(finalMessage)
                    }

                    // Check if we reached terminal state
                    if (finalMessage.state.isTerminal()) {
                        // Publish graph completed event
                        if (enableEvents && graph.eventBus != null) {
                            publishGraphCompleted(graph, finalMessage)
                        }
                        return SpiceResult.success(finalMessage)
                    }

                    // Continue to next node
                    currentMessage = finalMessage
                    currentNodeId = findNextNode(nodeId, finalMessage, graph)
                }
                is SpiceResult.Failure -> {
                    // Execute error middleware
                    val errorAction = executeErrorMiddleware(graph.middleware, result.error, currentMessage)

                    return when (errorAction) {
                        is ErrorAction.Propagate -> {
                            // Transition to FAILED state
                            val failedMessage = currentMessage.transitionTo(
                                newState = ExecutionState.FAILED,
                                reason = result.error.message,
                                nodeId = nodeId
                            )

                            // Publish graph failed event
                            if (enableEvents && graph.eventBus != null) {
                                publishGraphFailed(graph, failedMessage)
                            }

                            SpiceResult.failure(result.error)
                        }
                        is ErrorAction.Skip -> {
                            // Skip node and continue
                            currentNodeId = findNextNode(nodeId, currentMessage, graph)
                            continue
                        }
                        is ErrorAction.Retry -> {
                            // Retry current node (stay on same nodeId)
                            continue
                        }
                        is ErrorAction.Fallback -> {
                            // Use fallback message and continue
                            currentMessage = errorAction.message
                            currentNodeId = findNextNode(nodeId, currentMessage, graph)
                            continue
                        }
                    }
                }
            }
        }

        // No more nodes - graph completed
        val completedMessage = if (!currentMessage.state.isTerminal()) {
            currentMessage.transitionTo(
                newState = ExecutionState.COMPLETED,
                reason = "Graph execution completed (no more nodes)",
                nodeId = null
            )
        } else {
            currentMessage
        }

        // Publish graph completed event
        if (enableEvents && graph.eventBus != null) {
            publishGraphCompleted(graph, completedMessage)
        }

        return SpiceResult.success(completedMessage)
    }

    /**
     * Find next node based on edge conditions
     */
    private fun findNextNode(
        currentNodeId: String,
        message: SpiceMessage,
        graph: Graph
    ): String? {
        // Get all edges from current node
        val edges = graph.edges.filter { it.from == currentNodeId || it.from == "*" }

        // Separate regular and fallback edges
        val regularEdges = edges.filter { !it.isFallback }.sortedBy { it.priority }
        val fallbackEdges = edges.filter { it.isFallback }.sortedBy { it.priority }

        // Try regular edges first
        val matchingEdge = regularEdges.firstOrNull { it.condition(message) }
        if (matchingEdge != null) {
            return matchingEdge.to
        }

        // Try fallback edges
        return fallbackEdges.firstOrNull()?.to
    }

    /**
     * Validate graph structure
     */
    private fun validateGraph(graph: Graph): SpiceResult<Unit> {
        // Check entry point exists
        if (graph.entryPoint !in graph.nodes) {
            return SpiceResult.failure(
                SpiceError.validationError(
                    "Entry point '${graph.entryPoint}' not found in graph nodes",
                    graphId = graph.id
                )
            )
        }

        // Check all edge references exist
        for (edge in graph.edges) {
            if (edge.from != "*" && edge.from !in graph.nodes) {
                return SpiceResult.failure(
                    SpiceError.validationError(
                        "Edge references non-existent node: ${edge.from}",
                        graphId = graph.id
                    )
                )
            }
            if (edge.to !in graph.nodes) {
                return SpiceResult.failure(
                    SpiceError.validationError(
                        "Edge references non-existent node: ${edge.to}",
                        graphId = graph.id
                    )
                )
            }
        }

        // Check for cycles (if not allowed)
        if (!graph.allowCycles) {
            val cycleCheck = detectCycles(graph)
            if (cycleCheck is SpiceResult.Failure) {
                return cycleCheck
            }
        }

        return SpiceResult.success(Unit)
    }

    /**
     * Detect cycles in graph
     */
    private fun detectCycles(graph: Graph): SpiceResult<Unit> {
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()

        fun dfs(nodeId: String): Boolean {
            visited.add(nodeId)
            recursionStack.add(nodeId)

            val outgoingEdges = graph.edges.filter { it.from == nodeId }
            for (edge in outgoingEdges) {
                if (edge.to !in visited) {
                    if (dfs(edge.to)) return true
                } else if (edge.to in recursionStack) {
                    return true // Cycle detected
                }
            }

            recursionStack.remove(nodeId)
            return false
        }

        if (dfs(graph.entryPoint)) {
            return SpiceResult.failure(
                SpiceError.validationError(
                    "Graph contains cycles (set allowCycles=true to permit)",
                    graphId = graph.id
                )
            )
        }

        return SpiceResult.success(Unit)
    }

    /**
     * Check idempotency cache
     */
    private suspend fun checkIdempotency(
        store: IdempotencyStore,
        message: SpiceMessage,
        nodeId: String
    ): SpiceMessage? {
        val key = IdempotencyKey.fromNode(
            nodeId = nodeId,
            data = message.data + mapOf("correlationId" to message.correlationId)
        )

        return store.get(key)
    }

    /**
     * Save idempotency result
     */
    private suspend fun saveIdempotency(
        store: IdempotencyStore,
        inputMessage: SpiceMessage,
        nodeId: String,
        outputMessage: SpiceMessage
    ) {
        val key = IdempotencyKey.fromNode(
            nodeId = nodeId,
            data = inputMessage.data + mapOf("correlationId" to inputMessage.correlationId)
        )

        store.save(key, outputMessage, ttl = 3600.seconds) // 1 hour TTL
    }

    /**
     * Execute before-node middleware chain
     */
    private suspend fun executeBeforeNodeMiddleware(
        middlewares: List<Middleware>,
        message: SpiceMessage
    ): SpiceResult<SpiceMessage> {
        var currentMessage = message
        for (middleware in middlewares) {
            val result = middleware.beforeNode(currentMessage)
            when (result) {
                is SpiceResult.Success -> currentMessage = result.value
                is SpiceResult.Failure -> return result
            }
        }
        return SpiceResult.success(currentMessage)
    }

    /**
     * Execute after-node middleware chain
     */
    private suspend fun executeAfterNodeMiddleware(
        middlewares: List<Middleware>,
        message: SpiceMessage
    ): SpiceResult<SpiceMessage> {
        var currentMessage = message
        for (middleware in middlewares) {
            val result = middleware.afterNode(currentMessage)
            when (result) {
                is SpiceResult.Success -> currentMessage = result.value
                is SpiceResult.Failure -> return result
            }
        }
        return SpiceResult.success(currentMessage)
    }

    /**
     * Execute error middleware chain
     */
    private suspend fun executeErrorMiddleware(
        middlewares: List<Middleware>,
        error: SpiceError,
        message: SpiceMessage
    ): ErrorAction {
        for (middleware in middlewares) {
            val action = middleware.onError(error, message)
            if (action !is ErrorAction.Propagate) {
                return action
            }
        }
        return ErrorAction.Propagate
    }

    /**
     * Publish graph started event
     */
    private suspend fun publishGraphStarted(graph: Graph, message: SpiceMessage) {
        graph.eventBus?.publish(
            topic = "graph.${graph.id}.started",
            message = message.withMetadata(
                mapOf("event" to "graph_started", "timestamp" to Clock.System.now().toString())
            )
        )
    }

    /**
     * Publish graph completed event
     */
    private suspend fun publishGraphCompleted(graph: Graph, message: SpiceMessage) {
        graph.eventBus?.publish(
            topic = "graph.${graph.id}.completed",
            message = message.withMetadata(
                mapOf("event" to "graph_completed", "timestamp" to Clock.System.now().toString())
            )
        )
    }

    /**
     * Publish graph failed event
     */
    private suspend fun publishGraphFailed(graph: Graph, message: SpiceMessage) {
        graph.eventBus?.publish(
            topic = "graph.${graph.id}.failed",
            message = message.withMetadata(
                mapOf("event" to "graph_failed", "timestamp" to Clock.System.now().toString())
            )
        )
    }

    /**
     * Publish node started event
     */
    private suspend fun publishNodeStarted(graph: Graph, message: SpiceMessage, nodeId: String) {
        graph.eventBus?.publish(
            topic = "node.${graph.id}.$nodeId.started",
            message = message.withMetadata(
                mapOf("event" to "node_started", "nodeId" to nodeId, "timestamp" to Clock.System.now().toString())
            )
        )
    }

    /**
     * Publish node completed event
     */
    private suspend fun publishNodeCompleted(graph: Graph, message: SpiceMessage, nodeId: String) {
        graph.eventBus?.publish(
            topic = "node.${graph.id}.$nodeId.completed",
            message = message.withMetadata(
                mapOf("event" to "node_completed", "nodeId" to nodeId, "timestamp" to Clock.System.now().toString())
            )
        )
    }

    /**
     * Publish HITL requested event
     */
    private suspend fun publishHitlRequested(graph: Graph, message: SpiceMessage, nodeId: String) {
        graph.eventBus?.publish(
            topic = "hitl.${graph.id}.$nodeId.requested",
            message = message.withMetadata(
                mapOf("event" to "hitl_requested", "nodeId" to nodeId, "timestamp" to Clock.System.now().toString())
            )
        )
    }

    /**
     * Generate unique run ID
     */
    private fun generateRunId(): String {
        return "run_${Clock.System.now().toEpochMilliseconds()}_${(Math.random() * 1000000).toInt()}"
    }
}

/**
 * 🎬 Error Action for middleware error handling
 *
 * Determines how to handle node execution errors.
 *
 * @since 1.0.0
 */
sealed class ErrorAction {
    /**
     * Propagate error and fail graph execution
     */
    object Propagate : ErrorAction()

    /**
     * Skip current node and continue to next
     */
    object Skip : ErrorAction()

    /**
     * Retry current node execution
     */
    object Retry : ErrorAction()

    /**
     * Use fallback message and continue
     */
    data class Fallback(val message: SpiceMessage) : ErrorAction()
}
