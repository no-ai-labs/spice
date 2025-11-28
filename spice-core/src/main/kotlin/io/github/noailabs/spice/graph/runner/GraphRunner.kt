package io.github.noailabs.spice.graph.runner

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.Tool
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
import io.github.noailabs.spice.graph.Node
import io.github.noailabs.spice.graph.middleware.Middleware
import io.github.noailabs.spice.graph.checkpoint.SubgraphCheckpointContext
import io.github.noailabs.spice.graph.nodes.SubgraphNode
import io.github.noailabs.spice.graph.nodes.ToolNode
import io.github.noailabs.spice.graph.nodes.ToolNode.Companion.buildOutputMessage
import io.github.noailabs.spice.retry.ExecutionRetryPolicy
import io.github.noailabs.spice.retry.RetryResult
import io.github.noailabs.spice.retry.RetrySupervisor
import io.github.noailabs.spice.retry.RetrySupervisorConfig
import io.github.noailabs.spice.state.ExecutionStateMachine
import io.github.noailabs.spice.tool.ToolInvocationContext
import io.github.noailabs.spice.tool.ToolLifecycleListeners
import io.github.noailabs.spice.ToolContext
import io.github.noailabs.spice.validation.SchemaValidationPipeline
import io.github.noailabs.spice.validation.ValidationError
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

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
     * Used when a HITL node paused execution and user provided input.
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
    private val vectorCache: VectorCache? = null,
    private val fallbackToolLifecycleListeners: ToolLifecycleListeners? = null,
    private val retrySupervisor: RetrySupervisor = RetrySupervisor.default(),  // Zero-config: enabled by default
    private val defaultRetryPolicy: ExecutionRetryPolicy = ExecutionRetryPolicy.DEFAULT,
    private val enableRetryByDefault: Boolean = true  // Zero-config: retry enabled by default
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
     *
     * **Spice 1.3.0 Subgraph HITL Support:**
     * When resuming from a subgraph HITL, the message contains a subgraphStack
     * (stored by SubgraphNode.handleWaitingState). This method:
     * 1. Detects subgraphStack in message metadata
     * 2. Re-enters the subgraph chain (innermost first)
     * 3. Resumes child graph execution
     * 4. Applies outputMapping when child completes
     * 5. Continues parent execution
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

        // ===============================================================
        // Spice 1.3.0: Check for subgraph HITL resume
        // ===============================================================
        val subgraphStack = extractSubgraphStack(validatedMessage)
        if (subgraphStack.isNotEmpty()) {
            return resumeSubgraphHitl(graph, validatedMessage, subgraphStack)
        }

        // ===============================================================
        // Normal resume: no subgraph context
        // ===============================================================

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
     * Resume from subgraph HITL
     *
     * **Algorithm:**
     * 1. Pop the outermost context from the stack
     * 2. Find the SubgraphNode in the parent graph
     * 3. Resume the child graph with user response
     * 4. If child completes: apply outputMapping, continue parent
     * 5. If child is WAITING again: update stack, return WAITING
     *
     * @param parentGraph The graph containing the SubgraphNode
     * @param message The WAITING message with user response data
     * @param subgraphStack Stack of subgraph contexts (outermost first)
     */
    private suspend fun resumeSubgraphHitl(
        parentGraph: Graph,
        message: SpiceMessage,
        subgraphStack: List<SubgraphCheckpointContext>
    ): SpiceResult<SpiceMessage> {
        if (subgraphStack.isEmpty()) {
            return SpiceResult.failure(
                SpiceError.executionError(
                    "resumeSubgraphHitl called with empty stack",
                    graphId = parentGraph.id
                )
            )
        }

        // Pop outermost context (index 0)
        val currentContext = subgraphStack.first()
        val remainingStack = subgraphStack.drop(1)

        logger.info {
            "[GraphRunner] Resuming subgraph HITL: " +
            "parentNode=${currentContext.parentNodeId}, " +
            "childGraph=${currentContext.childGraphId}, " +
            "childNode=${currentContext.childNodeId}, " +
            "depth=${currentContext.depth}, " +
            "remainingStack=${remainingStack.size}"
        }

        // Find the SubgraphNode in parent graph
        val subgraphNode = parentGraph.nodes[currentContext.parentNodeId] as? SubgraphNode
            ?: return SpiceResult.failure(
                SpiceError.executionError(
                    "SubgraphNode not found: ${currentContext.parentNodeId}",
                    graphId = parentGraph.id,
                    nodeId = currentContext.parentNodeId
                )
            )

        // Get the child graph from the SubgraphNode
        val childGraph = subgraphNode.childGraph

        // Prepare message for child graph resume
        // - Set child graph context
        // - Keep user response data
        // - Attach remaining stack (for nested subgraphs)
        val childMetadata = message.metadata
            .minus(SubgraphCheckpointContext.STACK_METADATA_KEY)
            .minus(SubgraphCheckpointContext.METADATA_KEY)
            .let { baseMetadata ->
                if (remainingStack.isNotEmpty()) {
                    baseMetadata + (SubgraphCheckpointContext.STACK_METADATA_KEY to remainingStack)
                } else {
                    baseMetadata
                }
            }

        val childResumeMessage = message.copy(
            graphId = currentContext.childGraphId,
            nodeId = currentContext.childNodeId,
            runId = currentContext.childRunId,
            // Keep WAITING state - resume() will transition to RUNNING
            state = ExecutionState.WAITING,
            // Remove our context from metadata, keep remaining stack
            metadata = childMetadata
        )

        // Resume child graph (recursive call for nested subgraphs)
        val childResult = resume(childGraph, childResumeMessage)

        return when (childResult) {
            is SpiceResult.Success -> {
                val childOutput = childResult.value

                // Check if child is WAITING again (another HITL in the subgraph)
                if (childOutput.state == ExecutionState.WAITING) {
                    logger.debug {
                        "[GraphRunner] Child graph still WAITING after resume. " +
                        "Re-attaching subgraph context."
                    }

                    // Rebuild the stack with current context
                    @Suppress("UNCHECKED_CAST")
                    val childStack = (childOutput.metadata[SubgraphCheckpointContext.STACK_METADATA_KEY]
                        as? List<SubgraphCheckpointContext>) ?: emptyList()

                    // Update current context with new child node
                    val updatedContext = currentContext.copy(
                        childNodeId = childOutput.nodeId ?: currentContext.childNodeId
                    )

                    val newStack = listOf(updatedContext) + childStack

                    // Return WAITING with updated stack
                    return SpiceResult.success(
                        childOutput.copy(
                            // Parent graph context
                            graphId = currentContext.parentGraphId,
                            nodeId = currentContext.parentNodeId,
                            runId = currentContext.parentRunId,
                            metadata = childOutput.metadata
                                .minus(SubgraphCheckpointContext.STACK_METADATA_KEY)
                                .plus(SubgraphCheckpointContext.STACK_METADATA_KEY to newStack)
                        )
                    )
                }

                // Child completed - apply outputMapping
                logger.debug {
                    "[GraphRunner] Child graph completed. Applying outputMapping: ${currentContext.outputMapping}"
                }

                val mappedData = applyOutputMapping(
                    childOutput = childOutput,
                    outputMapping = currentContext.outputMapping,
                    parentData = message.data
                )

                // Create RUNNING message for parent continuation
                // Note: We create a new message with RUNNING state directly,
                // because childOutput.state is COMPLETED and we can't transition COMPLETED → RUNNING.
                // This is valid because we're starting a new execution phase in the parent graph.
                val cleanedMetadata = childOutput.metadata
                    .minus(SubgraphCheckpointContext.STACK_METADATA_KEY)
                    .minus(SubgraphCheckpointContext.METADATA_KEY)
                    .minus("waitingInSubgraph")
                    .minus("waitingSubgraphId")
                    .minus("waitingChildNodeId")

                val runningMessage = childOutput.copy(
                    graphId = currentContext.parentGraphId,
                    nodeId = currentContext.parentNodeId,
                    runId = currentContext.parentRunId,
                    state = ExecutionState.RUNNING,  // Directly set RUNNING state
                    data = mappedData,
                    metadata = cleanedMetadata
                )

                // Validate the message
                when (val guard = guardMessage(runningMessage, parentGraph, "resumeSubgraphHitl:continue", currentContext.parentNodeId)) {
                    is SpiceResult.Failure -> return guard
                    is SpiceResult.Success -> Unit
                }

                // Find next node in parent graph after the SubgraphNode
                val nextNodeId = findNextNode(
                    currentNodeId = currentContext.parentNodeId,
                    message = runningMessage,
                    graph = parentGraph
                )

                val idempotencyManager = if (enableIdempotency) {
                    parentGraph.idempotencyStore?.let { IdempotencyManager(it, cachePolicy) }
                } else {
                    null
                }

                // Continue parent execution
                executeNodes(
                    graph = parentGraph,
                    message = runningMessage,
                    startNodeId = nextNodeId,
                    idempotencyManager = idempotencyManager
                )
            }
            is SpiceResult.Failure -> {
                // Child failed - wrap error with context
                SpiceResult.failure(
                    childResult.error.withContext(
                        "resumeSubgraphHitl" to "true",
                        "childGraphId" to currentContext.childGraphId,
                        "parentNodeId" to currentContext.parentNodeId
                    )
                )
            }
        }
    }

    /**
     * Extract subgraph stack from message metadata
     *
     * **JSON Deserialization Note (Spice 1.3.2):**
     * When checkpoint is serialized/deserialized via JSON (e.g., Redis),
     * SubgraphCheckpointContext objects become Map<String, Any?>.
     * This method handles both native objects and Map representations.
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractSubgraphStack(message: SpiceMessage): List<SubgraphCheckpointContext> {
        val stackRaw = message.metadata[SubgraphCheckpointContext.STACK_METADATA_KEY]

        logger.debug {
            "[GraphRunner] extractSubgraphStack: " +
            "stackRaw type=${stackRaw?.javaClass?.name}, " +
            "stackRaw=$stackRaw"
        }

        if (stackRaw is List<*>) {
            val result = stackRaw.mapIndexedNotNull { index, item ->
                logger.debug {
                    "[GraphRunner] extractSubgraphStack: " +
                    "item[$index] type=${item?.javaClass?.name}"
                }

                when (item) {
                    is SubgraphCheckpointContext -> item
                    is Map<*, *> -> deserializeSubgraphContext(item as Map<String, Any?>)
                    else -> {
                        logger.warn {
                            "[GraphRunner] extractSubgraphStack: " +
                            "Unknown item type at index $index: ${item?.javaClass?.name}"
                        }
                        null
                    }
                }
            }

            logger.debug {
                "[GraphRunner] extractSubgraphStack: " +
                "extracted ${result.size} contexts from stack"
            }

            return result
        }

        // Try single context for backward compatibility
        val singleRaw = message.metadata[SubgraphCheckpointContext.METADATA_KEY]
        if (singleRaw != null) {
            logger.debug {
                "[GraphRunner] extractSubgraphStack: " +
                "trying single context, type=${singleRaw.javaClass.name}"
            }

            val context = when (singleRaw) {
                is SubgraphCheckpointContext -> singleRaw
                is Map<*, *> -> deserializeSubgraphContext(singleRaw as Map<String, Any?>)
                else -> null
            }
            if (context != null) {
                logger.debug {
                    "[GraphRunner] extractSubgraphStack: " +
                    "extracted single context: childGraphId=${context.childGraphId}"
                }
                return listOf(context)
            }
        }

        logger.debug { "[GraphRunner] extractSubgraphStack: no subgraph context found" }
        return emptyList()
    }

    /**
     * Deserialize SubgraphCheckpointContext from Map
     */
    private fun deserializeSubgraphContext(map: Map<String, Any?>): SubgraphCheckpointContext? {
        return try {
            SubgraphCheckpointContext(
                parentNodeId = map["parentNodeId"] as? String ?: return null,
                parentGraphId = map["parentGraphId"] as? String ?: return null,
                parentRunId = map["parentRunId"] as? String ?: return null,
                childGraphId = map["childGraphId"] as? String ?: return null,
                childNodeId = map["childNodeId"] as? String ?: return null,
                childRunId = map["childRunId"] as? String ?: return null,
                outputMapping = (map["outputMapping"] as? Map<*, *>)
                    ?.mapNotNull { (k, v) ->
                        if (k is String && v is String) k to v else null
                    }?.toMap() ?: emptyMap(),
                depth = (map["depth"] as? Number)?.toInt() ?: 1
            )
        } catch (e: Exception) {
            logger.warn(e) { "[GraphRunner] Failed to deserialize SubgraphCheckpointContext" }
            null
        }
    }

    /**
     * Apply outputMapping: rename child keys to parent keys
     */
    private fun applyOutputMapping(
        childOutput: SpiceMessage,
        outputMapping: Map<String, String>,
        parentData: Map<String, Any>
    ): Map<String, Any> {
        if (outputMapping.isEmpty()) {
            return parentData + childOutput.data
        }

        val mapped = mutableMapOf<String, Any>()
        val mappedChildKeys = mutableSetOf<String>()

        // Apply outputMapping: childKey → parentKey
        outputMapping.forEach { (childKey, parentKey) ->
            val childValue = childOutput.data[childKey]
            if (childValue != null) {
                mapped[parentKey] = childValue
                mappedChildKeys.add(childKey)
                logger.debug {
                    "[GraphRunner] outputMapping: child.$childKey → parent.$parentKey = $childValue"
                }
            }
        }

        // Include unmapped child keys
        childOutput.data.forEach { (key, value) ->
            if (key !in mappedChildKeys && key !in mapped) {
                mapped[key] = value
            }
        }

        // Merge with parent data (child takes precedence)
        return parentData + mapped
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

            // Execute node with retry for ALL node types
            // Use graph-level listeners if available, otherwise fallback to runner-level listeners
            val effectiveListeners = graph.toolLifecycleListeners ?: fallbackToolLifecycleListeners
            val result = executeNodeWithRetry(node, currentMessage, effectiveListeners, graph)

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
        // Debug: Log all edges in graph
        logger.debug("[findNextNode] Graph '{}' has {} total edges", graph.id, graph.edges.size)
        graph.edges.forEachIndexed { idx, edge ->
            logger.debug("[findNextNode] Edge[{}]: {} → {} (fallback={}, priority={})", idx, edge.from, edge.to, edge.isFallback, edge.priority)
        }

        // Get all edges from current node
        val edges = graph.edges.filter { it.from == currentNodeId || it.from == "*" }
        logger.debug("[findNextNode] Looking for edges from '{}', found {} matching edges", currentNodeId, edges.size)

        // Separate regular and fallback edges
        val regularEdges = edges.filter { !it.isFallback }.sortedBy { it.priority }
        val fallbackEdges = edges.filter { it.isFallback }.sortedBy { it.priority }
        logger.debug("[findNextNode] Regular edges: {}, Fallback edges: {}", regularEdges.size, fallbackEdges.size)

        // Try regular edges first
        val matchingEdge = regularEdges.firstOrNull { it.condition(message) }
        if (matchingEdge != null) {
            logger.debug("[findNextNode] Found matching regular edge: {} → {}", matchingEdge.from, matchingEdge.to)
            return matchingEdge.to
        }

        // Try fallback edges
        val fallbackResult = fallbackEdges.firstOrNull()?.to
        logger.debug("[findNextNode] No regular edge matched, fallback result: {}", fallbackResult)
        return fallbackResult
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
        is SpiceError.RateLimitError,
        is SpiceError.RetryableError -> true
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
     * Execute ANY node with retry support.
     *
     * This is the unified retry handler for ALL node types:
     * - ToolNode: with lifecycle listeners and tool resolution
     * - SubgraphNode: with runner injection
     * - AgentNode: standard execution
     * - Custom Node: standard execution
     *
     * Retry priority:
     * 1. Graph.retryEnabled = false → No retry (explicit disable)
     * 2. Graph.retryPolicy != null → Use graph policy (auto-enable)
     * 3. Runner's enableRetryByDefault → Use defaultRetryPolicy
     * 4. No retry
     *
     * @param node The Node to execute
     * @param message The input message
     * @param listeners Optional lifecycle listeners (for ToolNode)
     * @param graph The graph being executed (for graph-level retry policy)
     * @return SpiceResult with the output message
     */
    private suspend fun executeNodeWithRetry(
        node: Node,
        message: SpiceMessage,
        listeners: ToolLifecycleListeners?,
        graph: Graph
    ): SpiceResult<SpiceMessage> {
        // Determine effective retry configuration
        // Priority: Graph explicit > Graph implicit (retryPolicy set) > Runner default
        val effectiveRetryEnabled = when {
            graph.retryEnabled == false -> false  // Explicit disable
            graph.retryEnabled == true -> true    // Explicit enable
            graph.retryPolicy != null -> true     // Implicit enable (retryPolicy() called)
            else -> enableRetryByDefault          // Use runner default (Zero-config)
        }
        val effectiveRetryPolicy = graph.retryPolicy ?: defaultRetryPolicy

        // If retry is disabled, execute directly
        if (!effectiveRetryEnabled) {
            return executeNodeDirect(node, message, listeners, attemptNumber = 1)
        }

        // Execute with retry
        val result = retrySupervisor.executeWithRetry(
            message = message,
            nodeId = node.id,
            policy = effectiveRetryPolicy
        ) { currentMessage, attemptNumber ->
            executeNodeDirect(node, currentMessage, listeners, attemptNumber)
        }

        return when (result) {
            is RetryResult.Success -> SpiceResult.success(result.value)
            is RetryResult.Exhausted -> SpiceResult.failure(result.finalError)
            is RetryResult.NotRetryable -> SpiceResult.failure(result.error)
        }
    }

    /**
     * Execute a node directly without retry wrapping.
     *
     * Dispatches to the appropriate execution method based on node type.
     *
     * @param node The Node to execute
     * @param message The input message
     * @param listeners Optional lifecycle listeners (for ToolNode)
     * @param attemptNumber Current attempt number (1-based)
     * @return SpiceResult with the output message
     */
    private suspend fun executeNodeDirect(
        node: Node,
        message: SpiceMessage,
        listeners: ToolLifecycleListeners?,
        attemptNumber: Int
    ): SpiceResult<SpiceMessage> {
        return when (node) {
            is ToolNode -> {
                // Resolve tool first
                val resolvedTool = when (val resolveResult = node.resolver.resolve(message)) {
                    is SpiceResult.Success -> resolveResult.value
                    is SpiceResult.Failure -> return SpiceResult.failure(resolveResult.error)
                }
                executeToolNodeDirect(node, message, resolvedTool, listeners, attemptNumber)
            }
            is SubgraphNode -> {
                // Pass this runner to SubgraphNode for proper inheritance (thread-safe)
                node.runWithRunner(message, this)
            }
            else -> {
                // AgentNode, OutputNode, etc. - standard execution
                node.run(message)
            }
        }
    }

    /**
     * @deprecated Use executeNodeWithRetry instead which handles all node types.
     * This method is kept for backward compatibility with external callers.
     */
    private suspend fun executeToolNode(
        toolNode: ToolNode,
        message: SpiceMessage,
        listeners: ToolLifecycleListeners?,
        graph: Graph
    ): SpiceResult<SpiceMessage> {
        return executeNodeWithRetry(toolNode, message, listeners, graph)
    }

    /**
     * Direct tool execution without retry wrapping.
     *
     * @param toolNode The ToolNode being executed
     * @param message The input message
     * @param resolvedTool The pre-resolved tool
     * @param listeners Optional lifecycle listeners
     * @param attemptNumber Current attempt number (1-based)
     * @return SpiceResult with the output message
     */
    private suspend fun executeToolNodeDirect(
        toolNode: ToolNode,
        message: SpiceMessage,
        resolvedTool: Tool,
        listeners: ToolLifecycleListeners?,
        attemptNumber: Int
    ): SpiceResult<SpiceMessage> {
        return if (listeners != null) {
            executeToolNodeWithListeners(toolNode, message, resolvedTool, listeners, attemptNumber)
        } else {
            // No listeners - execute directly using the resolved tool
            toolNode.executeWith(resolvedTool, message)
        }
    }

    /**
     * Execute a ToolNode with lifecycle listener callbacks.
     *
     * Wraps tool execution with onInvoke/onSuccess/onFailure/onComplete callbacks.
     * Handles both SpiceResult.Success and SpiceResult.Failure from tool execution,
     * as well as ToolResult.success=false (tool returned error result).
     *
     * @param toolNode The ToolNode being executed
     * @param message The input message
     * @param resolvedTool The pre-resolved tool (resolved by executeToolNode)
     * @param listeners Lifecycle listeners to notify
     * @param attemptNumber Current attempt number (1-based), for retry visibility
     * @return SpiceResult with the output message
     */
    private suspend fun executeToolNodeWithListeners(
        toolNode: ToolNode,
        message: SpiceMessage,
        resolvedTool: Tool,
        listeners: ToolLifecycleListeners,
        attemptNumber: Int
    ): SpiceResult<SpiceMessage> {
        // Prepare invocation using shared helper
        val (nonNullParams, toolContext) = toolNode.prepareInvocation(message)

        // Create invocation context with resolved tool and attempt number
        val invocationContext = ToolInvocationContext.create(
            tool = resolvedTool,
            toolContext = toolContext,
            params = nonNullParams,
            attemptNumber = attemptNumber
        )

        // Notify listeners: onInvoke
        listeners.onInvoke(invocationContext)

        val startTime = Clock.System.now()

        try {
            // Execute resolved tool
            val result = resolvedTool.execute(nonNullParams, toolContext)
            val durationMs = Clock.System.now().toEpochMilliseconds() - startTime.toEpochMilliseconds()

            return when (result) {
                is SpiceResult.Success -> {
                    val toolResult = result.value

                    // Notify listeners: onSuccess (even if toolResult.success=false)
                    listeners.onSuccess(invocationContext, toolResult, durationMs)

                    // Build output message using shared helper
                    val output = buildOutputMessage(message, toolResult, resolvedTool.name)

                    SpiceResult.success(output)
                }
                is SpiceResult.Failure -> {
                    // Notify listeners: onFailure
                    listeners.onFailure(invocationContext, result.error, durationMs)
                    SpiceResult.failure(result.error)
                }
            }
        } finally {
            // Notify listeners: onComplete (always called)
            listeners.onComplete(invocationContext)
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
