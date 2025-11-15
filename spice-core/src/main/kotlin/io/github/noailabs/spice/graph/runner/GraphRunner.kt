package io.github.noailabs.spice.graph.runner

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.cache.CacheKind
import io.github.noailabs.spice.cache.CachePolicy
import io.github.noailabs.spice.cache.IdempotencyManager
import io.github.noailabs.spice.cache.VectorCache
import io.github.noailabs.spice.error.ErrorReport
import io.github.noailabs.spice.error.ErrorReportAdapter
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.event.ToolCallEvent
import io.github.noailabs.spice.events.EventBus
import io.github.noailabs.spice.graph.Edge
import io.github.noailabs.spice.graph.Graph
import io.github.noailabs.spice.graph.middleware.Middleware
import io.github.noailabs.spice.state.ExecutionStateMachine
import io.github.noailabs.spice.validation.SchemaValidationPipeline
import io.github.noailabs.spice.validation.ValidationError
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

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
    private val enableEvents: Boolean = true,
    private val validationPipeline: SchemaValidationPipeline = SchemaValidationPipeline(),
    private val stateMachine: ExecutionStateMachine = ExecutionStateMachine(),
    private val cachePolicy: CachePolicy = CachePolicy(),
    private val vectorCache: VectorCache? = null
) : GraphRunner {

    /**
     * Execute graph with message
     */
    override suspend fun execute(graph: Graph, message: SpiceMessage): SpiceResult<SpiceMessage> {
        // Validate graph structure
        when (val validation = validateGraph(graph)) {
            is SpiceResult.Failure -> return SpiceResult.failure(validation.error)
            is SpiceResult.Success -> Unit
        }

        // Validate incoming message before execution
        val initialMessage = when (val guard = guardMessage(message, graph, "execute:init")) {
            is SpiceResult.Failure -> return guard
            is SpiceResult.Success -> guard.value
        }
        recordIntentVector(initialMessage)

        // Validate message state (should be READY or RUNNING)
        if (initialMessage.state.isTerminal()) {
            return SpiceResult.failure(
                SpiceError.executionError(
                    "Cannot execute graph with terminal message state: ${initialMessage.state}",
                    graphId = graph.id
                )
            )
        }

        // Transition to RUNNING state if not already
        val runningMessage = if (initialMessage.state == ExecutionState.READY) {
            when (
                val transitioned = transitionAndGuard(
                    graph = graph,
                    message = initialMessage,
                    target = ExecutionState.RUNNING,
                    reason = "Graph execution started",
                    nodeId = graph.entryPoint
                )
            ) {
                is SpiceResult.Failure -> return transitioned
                is SpiceResult.Success -> transitioned.value
            }
        } else {
            initialMessage
        }

        // Set graph context
        val contextualMessage = run {
            val updated = runningMessage.withGraphContext(
                graphId = graph.id,
                nodeId = null,
                runId = runningMessage.runId ?: generateRunId()
            )
            when (val guard = guardMessage(updated, graph, "execute:context")) {
                is SpiceResult.Failure -> return guard
                is SpiceResult.Success -> guard.value
            }
        }

        val idempotencyManager = if (enableIdempotency) {
            graph.idempotencyStore?.let { IdempotencyManager(it, cachePolicy) }
        } else {
            null
        }

        // Publish graph started event
        if (enableEvents && graph.eventBus != null) {
            publishGraphStarted(graph, contextualMessage)
        }

        // Execute nodes
        return executeNodes(
            graph = graph,
            message = contextualMessage,
            startNodeId = graph.entryPoint,
            idempotencyManager = idempotencyManager
        )
    }

    /**
     * Resume execution from WAITING state
     */
    override suspend fun resume(graph: Graph, message: SpiceMessage): SpiceResult<SpiceMessage> {
        val validatedMessage = when (val guard = guardMessage(message, graph, "resume:init")) {
            is SpiceResult.Failure -> return guard
            is SpiceResult.Success -> guard.value
        }
        recordIntentVector(validatedMessage)

        // Validate message is in WAITING state
        if (validatedMessage.state != ExecutionState.WAITING) {
            return SpiceResult.failure(
                SpiceError.executionError(
                    "Cannot resume graph with message state: ${validatedMessage.state}. Expected WAITING.",
                    graphId = graph.id
                )
            )
        }

        // Transition back to RUNNING
        val runningMessage = when (
            val transitioned = transitionAndGuard(
                graph = graph,
                message = validatedMessage,
                target = ExecutionState.RUNNING,
                reason = "Resuming after human input",
                nodeId = validatedMessage.nodeId
            )
        ) {
            is SpiceResult.Failure -> return transitioned
            is SpiceResult.Success -> transitioned.value
        }

        // Find next node after the WAITING node
        val nextNodeId = findNextNode(
            currentNodeId = validatedMessage.nodeId ?: return SpiceResult.failure(
                SpiceError.executionError("Cannot resume: message has no nodeId", graphId = graph.id)
            ),
            message = runningMessage,
            graph = graph
        )

        val idempotencyManager = if (enableIdempotency) {
            graph.idempotencyStore?.let { IdempotencyManager(it, cachePolicy) }
        } else {
            null
        }

        // Continue execution
        return executeNodes(
            graph = graph,
            message = runningMessage,
            startNodeId = nextNodeId,
            idempotencyManager = idempotencyManager
        )
    }

    /**
     * Core node execution loop
     */
    private suspend fun executeNodes(
        graph: Graph,
        message: SpiceMessage,
        startNodeId: String?,
        idempotencyManager: IdempotencyManager?
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

            val intentSignature = resolveIntentSignature(currentMessage)

            // Check idempotency (skip if already executed)
            if (enableIdempotency && idempotencyManager != null) {
                val cachedResult = idempotencyManager.lookupStep(nodeId, intentSignature)
                if (cachedResult != null) {
                    val afterCached = executeAfterNodeMiddleware(graph.middleware, cachedResult)
                    currentMessage = when (afterCached) {
                        is SpiceResult.Success -> afterCached.value
                        is SpiceResult.Failure -> return afterCached
                    }
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

                    val validatedFinal = when (
                        val guard = guardMessage(finalMessage, graph, "execute:nodes.after", nodeId)
                    ) {
                        is SpiceResult.Failure -> return guard
                        is SpiceResult.Success -> guard.value
                    }

                    // Save idempotency result
                    if (enableIdempotency && idempotencyManager != null) {
                        val signatureForStorage = resolveIntentSignature(validatedFinal)
                        idempotencyManager.storeStep(nodeId, signatureForStorage, validatedFinal)
                    }

                    // Publish tool call events (Spice 2.0)
                    if (validatedFinal.hasToolCalls() && graph.toolCallEventBus != null) {
                        publishToolCallEvents(graph, validatedFinal, nodeId)
                    }

                    // Publish node completed event
                    if (enableEvents && graph.eventBus != null) {
                        publishNodeCompleted(graph, validatedFinal, nodeId)
                    }

                    // Check if we need to pause for HITL
                    if (validatedFinal.state == ExecutionState.WAITING) {
                        // Publish HITL requested event
                        if (enableEvents && graph.eventBus != null) {
                            publishHitlRequested(graph, validatedFinal, nodeId)
                        }
                        return SpiceResult.success(validatedFinal)
                    }

                    // Check if we reached terminal state
                    if (validatedFinal.state.isTerminal()) {
                        // Publish graph completed event
                        if (enableEvents && graph.eventBus != null) {
                            publishGraphCompleted(graph, validatedFinal)
                        }
                        return SpiceResult.success(validatedFinal)
                    }

                    // Continue to next node
                    currentMessage = validatedFinal
                    currentNodeId = findNextNode(nodeId, validatedFinal, graph)
                }
                is SpiceResult.Failure -> {
                    // Execute error middleware
                    val errorAction = executeErrorMiddleware(graph.middleware, result.error, currentMessage)

                    return when (errorAction) {
                        is ErrorAction.Propagate -> {
                            val failureMessage = when (
                                val transitioned = transitionAndGuard(
                                    graph = graph,
                                    message = currentMessage,
                                    target = ExecutionState.FAILED,
                                    reason = result.error.message,
                                    nodeId = nodeId
                                )
                            ) {
                                is SpiceResult.Failure -> return transitioned
                                is SpiceResult.Success -> transitioned.value
                            }

                            val report = buildErrorReport(result.error)
                            val enriched = failureMessage.withToolCall(
                                ErrorReportAdapter.toToolCall(report)
                            )

                            // Publish graph failed event
                            if (enableEvents && graph.eventBus != null) {
                                publishGraphFailed(graph, enriched)
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
            when (
                val transitioned = transitionAndGuard(
                    graph = graph,
                    message = currentMessage,
                    target = ExecutionState.COMPLETED,
                    reason = "Graph execution completed (no more nodes)",
                    nodeId = currentMessage.nodeId
                )
            ) {
                is SpiceResult.Failure -> return transitioned
                is SpiceResult.Success -> transitioned.value
            }
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

    private fun guardMessage(
        message: SpiceMessage,
        graph: Graph,
        stage: String,
        nodeId: String? = null
    ): SpiceResult<SpiceMessage> = try {
        stateMachine.ensureHistoryValid(message)
        val validation = validationPipeline.validateMessage(message)
        if (validation.isValid) {
            SpiceResult.success(message)
        } else {
            validationFailure(graph.id, stage, nodeId, validation.errors)
        }
    } catch (ex: IllegalStateException) {
        SpiceResult.failure(
            SpiceError.validationError(
                "State validation failed at $stage: ${ex.message}"
            ).withContext("graphId" to graph.id, "nodeId" to (nodeId ?: ""))
        )
    }

    private fun validationFailure(
        graphId: String,
        stage: String,
        nodeId: String?,
        errors: List<ValidationError>
    ): SpiceResult<SpiceMessage> {
        val detail = errors.joinToString("; ") { "${it.field}: ${it.message}" }
        val error = SpiceError.validationError(
            "Message validation failed during $stage: $detail"
        ).withContext(
            "graphId" to graphId,
            "nodeId" to (nodeId ?: ""),
            "stage" to stage
        )
        return SpiceResult.failure(error)
    }

    private fun transitionAndGuard(
        graph: Graph,
        message: SpiceMessage,
        target: ExecutionState,
        reason: String?,
        nodeId: String?
    ): SpiceResult<SpiceMessage> = try {
        val transitioned = message.transitionTo(
            newState = target,
            reason = reason,
            nodeId = nodeId
        )
        guardMessage(transitioned, graph, "state-transition:$target", nodeId)
    } catch (ex: IllegalStateException) {
        SpiceResult.failure(
            SpiceError.validationError(
                "Invalid state transition: ${ex.message}"
            ).withContext("graphId" to graph.id, "nodeId" to (nodeId ?: ""))
        )
    }

    private fun resolveIntentSignature(message: SpiceMessage): String {
        val explicit = message.metadata["intentSignature"] as? String
            ?: message.metadata["intent"] as? String
        if (explicit != null && explicit.isNotBlank()) return explicit
        val content = message.content
        return if (content.isBlank()) {
            message.id
        } else {
            content.take(100).hashCode().toString()
        }
    }

    private suspend fun recordIntentVector(message: SpiceMessage) {
        val cache = vectorCache ?: return
        val rawVector = message.metadata["intentVector"] ?: return
        val vector = when (rawVector) {
            is FloatArray -> rawVector
            is DoubleArray -> FloatArray(rawVector.size) { rawVector[it].toFloat() }
            is List<*> -> rawVector.mapNotNull { (it as? Number)?.toFloat() }.toFloatArray()
            else -> return
        }
        if (vector.isEmpty()) return
        val key = (message.metadata["intentKey"] as? String).orEmpty().ifBlank { message.correlationId }

        cache.put(
            key = key,
            vector = vector,
            metadata = mapOf(
                "correlationId" to message.correlationId,
                "from" to message.from,
                "graphId" to (message.graphId ?: "")
            ),
            ttl = cachePolicy.ttlFor(CacheKind.INTENT)
        )
    }

    private fun buildErrorReport(error: SpiceError): ErrorReport =
        ErrorReport(
            code = error.code,
            reason = error.message,
            recoverable = isRecoverable(error),
            context = error.context
        )

    private fun isRecoverable(error: SpiceError): Boolean = when (error) {
        is SpiceError.ToolError,
        is SpiceError.NetworkError,
        is SpiceError.TimeoutError,
        is SpiceError.RateLimitError -> true
        else -> false
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
     * Publish tool call emitted events (Spice 2.0)
     *
     * Publishes ToolCallEvent.Emitted for each tool call in the message.
     * Used for event-driven multi-agent orchestration.
     */
    private suspend fun publishToolCallEvents(
        graph: Graph,
        message: SpiceMessage,
        nodeId: String
    ) {
        val toolCallEventBus = graph.toolCallEventBus ?: return

        // Publish event for each tool call
        for (toolCall in message.toolCalls) {
            val event = ToolCallEvent.Emitted(
                toolCall = toolCall,
                message = message,
                emittedBy = nodeId,
                graphId = graph.id,
                runId = message.runId,
                metadata = mapOf(
                    "nodeId" to nodeId,
                    "graphId" to graph.id,
                    "state" to message.state.toString()
                )
            )

            // Publish to tool call event bus
            toolCallEventBus.publish(event)
        }
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
