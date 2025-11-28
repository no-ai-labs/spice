package io.github.noailabs.spice.springboot.statemachine.core

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.event.ToolCallEvent
import io.github.noailabs.spice.graph.Graph
import io.github.noailabs.spice.graph.checkpoint.CheckpointStore
import io.github.noailabs.spice.graph.runner.GraphRunner
import io.github.noailabs.spice.springboot.statemachine.actions.CheckpointSaveAction
import io.github.noailabs.spice.springboot.statemachine.actions.EventPublishAction
import io.github.noailabs.spice.springboot.statemachine.actions.NodeExecutionAction
import io.github.noailabs.spice.springboot.statemachine.actions.ToolRetryAction
import io.github.noailabs.spice.springboot.statemachine.events.WorkflowCompletedEvent
import io.github.noailabs.spice.springboot.statemachine.events.WorkflowResumedEvent
import io.github.noailabs.spice.springboot.statemachine.guards.RetryableErrorGuard
import io.github.noailabs.spice.springboot.statemachine.transformer.MessageTransformer
import io.github.noailabs.spice.springboot.statemachine.transformer.TransformerChain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory

/**
 * Bridges GraphRunner execution with the Spring state machine.
 *
 * Supports MessageTransformers for injecting context, tracing, and custom logic
 * at various points during graph execution:
 * - beforeExecution: Before graph starts (global context injection)
 * - beforeNode: Before each node executes (per-node tracing, auth)
 * - afterNode: After each node completes (result transformation)
 * - afterExecution: After graph completes (cleanup, telemetry)
 *
 * **HITL Resume Support (since 1.0.5):**
 * The adapter now supports direct HITL resume via `resume(runId, options)`:
 * ```kotlin
 * // Pause at HumanNode
 * val result1 = adapter.execute(graph, message)
 * // result1.state == ExecutionState.WAITING
 *
 * // Later - resume with user response
 * val result2 = adapter.resume(runId, userResponse, graph)
 * ```
 *
 * @param graphRunner GraphRunner for executing and resuming graphs
 * @param checkpointStore CheckpointStore for loading/saving checkpoints
 * @param transformers List of transformers to apply (executed in order)
 */
class GraphToStateMachineAdapter(
    private val stateMachineFactory: SpiceStateMachineFactory,
    private val nodeExecutionAction: NodeExecutionAction,
    private val checkpointSaveAction: CheckpointSaveAction,
    private val eventPublishAction: EventPublishAction,
    private val toolRetryAction: ToolRetryAction,
    private val retryableErrorGuard: RetryableErrorGuard,
    private val graphRunner: GraphRunner,
    private val checkpointStore: CheckpointStore,
    private val graphRegistry: Map<String, Graph> = emptyMap(),
    private val transformers: List<MessageTransformer> = emptyList(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val logger = LoggerFactory.getLogger(GraphToStateMachineAdapter::class.java)
    private val transformerChain = TransformerChain(transformers)

    suspend fun execute(graph: Graph, message: SpiceMessage): SpiceResult<SpiceMessage> = withContext(dispatcher) {
        // Apply beforeExecution transformers
        val transformedInput = transformerChain.beforeExecution(graph, message)
        val initialMessage = when (transformedInput) {
            is SpiceResult.Success -> transformedInput.value
            is SpiceResult.Failure -> {
                logger.error("Transformer chain failed in beforeExecution: ${transformedInput.error.message}")
                return@withContext transformedInput
            }
        }

        val stateMachine = stateMachineFactory.create()
        val springStateMachine = stateMachine.asSpringStateMachine()
        val runId = initialMessage.runId ?: "${graph.id}:${System.currentTimeMillis()}"
        springStateMachine.extendedState.variables["graphId"] = graph.id
        springStateMachine.extendedState.variables["runId"] = runId
        springStateMachine.extendedState.variables["graph"] = graph
        springStateMachine.extendedState.variables["nodeId"] = graph.entryPoint
        springStateMachine.extendedState.variables["message"] = initialMessage
        stateMachine.start()

        stateMachine.sendEvent(SpiceEvent.START)

        var attempt = 0
        var lastResult: SpiceResult<SpiceMessage>? = null
        var currentMessage = initialMessage
        var running = true
        while (running) {
            lastResult = nodeExecutionAction.execute(graph, currentMessage)
            springStateMachine.extendedState.variables["message"] =
                (lastResult as? SpiceResult.Success)?.value ?: springStateMachine.extendedState.variables["message"]
            when (lastResult) {
                is SpiceResult.Success -> {
                    currentMessage = lastResult.value
                    springStateMachine.extendedState.variables["nodeId"] = lastResult.value.nodeId
                    val nextState = lastResult.value.state
                    when (nextState) {
                        ExecutionState.WAITING -> {
                            stateMachine.sendEvent(SpiceEvent.PAUSE_FOR_HITL)
                            checkpointSaveAction.save(runId, graph, springStateMachine)
                            springStateMachine.extendedState.variables["checkpointSaved"] = true
                            running = false
                        }

                        ExecutionState.COMPLETED -> {
                            stateMachine.sendEvent(SpiceEvent.COMPLETE)
                            eventPublishAction.publishWorkflowCompleted(
                                WorkflowCompletedEvent(
                                    runId = runId,
                                    graphId = graph.id,
                                    finalState = ExecutionState.COMPLETED,
                                    timestamp = Clock.System.now(),
                                    metadata = mapOf("attempts" to attempt + 1)
                                )
                            )
                            running = false
                        }

                        ExecutionState.FAILED -> {
                            stateMachine.sendEvent(SpiceEvent.FAIL)
                            running = false
                        }

                        ExecutionState.RUNNING -> {
                            // Graph runner reported RUNNING meaning more work pending, loop again
                            continue
                        }

                        ExecutionState.READY -> running = false
                    }
                }

                is SpiceResult.Failure -> {
                    springStateMachine.extendedState.variables["lastError"] = lastResult.error
                    val backoff = nextBackoff(attempt, lastResult.error)
                    if (backoff != null) {
                        attempt += 1
                        springStateMachine.extendedState.variables["retryCount"] = attempt
                        logger.warn(
                            "Retrying graph {} (attempt {}), waiting {}ms due to {}",
                            graph.id,
                            attempt,
                            backoff,
                            lastResult.error.code
                        )
                        stateMachine.sendEvent(SpiceEvent.TOOL_ERROR)
                        delay(backoff)
                        stateMachine.sendEvent(SpiceEvent.RETRY)
                        continue
                    } else {
                        stateMachine.sendEvent(SpiceEvent.FAIL)
                        eventPublishAction.publishWorkflowCompleted(
                            WorkflowCompletedEvent(
                                runId = runId,
                                graphId = graph.id,
                                finalState = ExecutionState.FAILED,
                                timestamp = Clock.System.now(),
                                metadata = mapOf(
                                    "error" to lastResult.error.message,
                                    "attempts" to attempt + 1
                                )
                            )
                        )
                        running = false
                    }
                }
            }
        }

        stateMachine.stop()

        // Get the final result
        val finalResult = lastResult ?: SpiceResult.failure(
            SpiceError.executionError("Graph execution did not produce any result", graph.id)
        )

        // Apply afterExecution transformers
        val transformedFinalResult = when (finalResult) {
            is SpiceResult.Success -> {
                transformerChain.afterExecution(graph, initialMessage, finalResult.value)
            }
            is SpiceResult.Failure -> {
                // Still call afterExecution for cleanup, but with a failed message
                val failedMessage = initialMessage.copy(state = ExecutionState.FAILED)
                val cleanupResult = transformerChain.afterExecution(graph, initialMessage, failedMessage)
                // Return original failure
                finalResult
            }
        }

        transformedFinalResult
    }

    private fun nextBackoff(attempt: Int, error: SpiceError?): Long? {
        return if (retryableErrorGuard.isRetryable(error)) {
            toolRetryAction.nextBackoff(attempt)
        } else {
            null
        }
    }

    /**
     * Resume HITL workflow from checkpoint
     *
     * Loads checkpoint from store, validates it, merges user response,
     * and continues graph execution from the paused node.
     *
     * **Flow:**
     * 1. Load checkpoint from store by runId
     * 2. Validate checkpoint (expiration, state)
     * 3. Reconstruct message from checkpoint
     * 4. Merge user response (if provided)
     * 5. Call GraphRunner.resume() to continue execution
     * 6. Apply state machine events and cleanup
     *
     * **Usage:**
     * ```kotlin
     * // Simple resume
     * val result = adapter.resume("run-123", userResponse, graph)
     *
     * // Resume with options
     * val result = adapter.resume(
     *     runId = "run-123",
     *     userResponse = userResponse,
     *     graph = graph,
     *     options = ResumeOptions(publishEvents = false)
     * )
     *
     * // Resume using graph registry
     * val result = adapter.resume("run-123", userResponse)
     * ```
     *
     * @param runId Run ID (same as checkpointId used during execute)
     * @param userResponse User's response message (optional - can be null for confirmation-only flows)
     * @param graph Graph definition (optional if graphRegistry is configured)
     * @param options Resume options for fine-grained control
     * @return SpiceResult with final message (COMPLETED/FAILED/WAITING)
     *
     * @since 1.0.5
     */
    suspend fun resume(
        runId: String,
        userResponse: SpiceMessage? = null,
        graph: Graph? = null,
        options: ResumeOptions = ResumeOptions.DEFAULT
    ): SpiceResult<SpiceMessage> = withContext(dispatcher) {
        logger.info("Resuming workflow with runId: {}", runId)

        // 1. Load checkpoint from store by runId
        // Note: checkpoints are stored with their own IDs, not runId, so we use listByRun
        val checkpoint = when (val result = checkpointStore.listByRun(runId)) {
            is SpiceResult.Success -> {
                val checkpoints = result.value
                if (checkpoints.isEmpty()) {
                    val error = SpiceError.executionError(
                        "Checkpoint not found for runId: $runId"
                    ).withContext("runId" to runId)
                    logger.error("Failed to load checkpoint: {}", runId)
                    return@withContext if (options.throwOnError) {
                        throw IllegalStateException(error.message)
                    } else {
                        SpiceResult.failure(error)
                    }
                }
                // Get the latest checkpoint (by timestamp)
                checkpoints.maxByOrNull { it.timestamp } ?: checkpoints.first()
            }
            is SpiceResult.Failure -> {
                val error = SpiceError.executionError(
                    "Failed to query checkpoints for runId: $runId"
                ).withContext("runId" to runId)
                logger.error("Failed to load checkpoint: {}", runId)
                return@withContext if (options.throwOnError) {
                    throw IllegalStateException(error.message)
                } else {
                    SpiceResult.failure(error)
                }
            }
        }

        // 2. Validate checkpoint expiration
        if (options.validateExpiration) {
            // Check both checkpoint's own expiration and user-specified maxCheckpointAge
            val isExpiredByTtl = checkpoint.isExpired()
            val checkpointAge = Clock.System.now() - checkpoint.timestamp
            val isExpiredByMaxAge = checkpointAge > options.maxCheckpointAge

            if (isExpiredByTtl || isExpiredByMaxAge) {
                val reason = when {
                    isExpiredByTtl -> "checkpoint TTL expired at ${checkpoint.expiresAt}"
                    else -> "checkpoint age ($checkpointAge) exceeds maxCheckpointAge (${options.maxCheckpointAge})"
                }
                val error = SpiceError.validationError(
                    "Checkpoint expired: $runId ($reason)"
                ).withContext(
                    "runId" to runId,
                    "expiresAt" to checkpoint.expiresAt.toString(),
                    "checkpointAge" to checkpointAge.toString(),
                    "maxCheckpointAge" to options.maxCheckpointAge.toString()
                )
                logger.warn("Checkpoint expired: {} - {}", runId, reason)
                return@withContext if (options.throwOnError) {
                    throw IllegalStateException(error.message)
                } else {
                    SpiceResult.failure(error)
                }
            }
        }

        // 3. Resolve graph (from parameter or registry)
        logger.debug("[resume] GraphRegistry has {} graphs: {}", graphRegistry.size, graphRegistry.keys)
        logger.debug("[resume] Looking for graphId='{}' from checkpoint", checkpoint.graphId)
        val resolvedGraph = graph ?: graphRegistry[checkpoint.graphId]
        if (resolvedGraph != null) {
            logger.debug("[resume] Found graph '{}' with {} nodes, {} edges", resolvedGraph.id, resolvedGraph.nodes.size, resolvedGraph.edges.size)
            resolvedGraph.edges.forEachIndexed { idx, edge ->
                logger.debug("[resume] Graph '{}' Edge[{}]: {} → {}", resolvedGraph.id, idx, edge.from, edge.to)
            }
        }
        if (resolvedGraph == null) {
            val error = SpiceError.executionError(
                "Graph not found: ${checkpoint.graphId}. Provide graph parameter or configure graphRegistry."
            ).withContext("graphId" to checkpoint.graphId, "runId" to runId)
            logger.error("Graph not found for checkpoint: {}", checkpoint.graphId)
            return@withContext if (options.throwOnError) {
                throw IllegalStateException(error.message)
            } else {
                SpiceResult.failure(error)
            }
        }

        // 4. Reconstruct message from checkpoint
        val checkpointMessage = checkpoint.message
        if (checkpointMessage == null) {
            val error = SpiceError.executionError(
                "Checkpoint has no message: $runId"
            ).withContext("runId" to runId)
            logger.error("Checkpoint missing message: {}", runId)
            return@withContext if (options.throwOnError) {
                throw IllegalStateException(error.message)
            } else {
                SpiceResult.failure(error)
            }
        }

        // 4.5 Restore subgraphStack to checkpointMessage.metadata (Spice 1.3.2)
        // JSON serialization/deserialization may lose SubgraphCheckpointContext type info
        // in message.metadata, so we restore it from checkpoint.subgraphStack field
        val restoredCheckpointMessage = if (checkpoint.subgraphStack.isNotEmpty()) {
            logger.debug("Restoring subgraphStack to checkpointMessage.metadata: {} contexts", checkpoint.subgraphStack.size)
            checkpointMessage.withMetadata(
                checkpointMessage.metadata + mapOf(
                    io.github.noailabs.spice.graph.checkpoint.SubgraphCheckpointContext.STACK_METADATA_KEY to checkpoint.subgraphStack
                )
            )
        } else {
            checkpointMessage
        }

        // 5. Build resume message with user response
        val (resumeMessage, responseToolCall) = buildResumeMessage(
            checkpointMessage = restoredCheckpointMessage,
            userResponse = userResponse,
            options = options
        )

        // 6. Update checkpoint with response for audit trail (Spice 2.0)
        if (responseToolCall != null) {
            val updatedCheckpoint = checkpoint.copy(responseToolCall = responseToolCall)
            checkpointStore.save(updatedCheckpoint)

            // Publish ToolCallEvent.Completed
            val pendingToolCall = checkpoint.pendingToolCall
            val toolCallEventBus = resolvedGraph.toolCallEventBus
            if (options.publishEvents && toolCallEventBus != null && pendingToolCall != null) {
                val startTime = checkpoint.timestamp.toEpochMilliseconds()
                val endTime = Clock.System.now().toEpochMilliseconds()
                val durationMs = endTime - startTime

                val completedEvent = ToolCallEvent.Completed(
                    toolCall = pendingToolCall,
                    message = resumeMessage,
                    result = responseToolCall,
                    completedBy = resumeMessage.from,
                    originalEventId = pendingToolCall.id,
                    durationMs = durationMs,
                    metadata = mapOf(
                        "checkpointId" to runId,
                        "graphId" to resolvedGraph.id,
                        "nodeId" to checkpoint.currentNodeId,
                        "runId" to runId
                    )
                )
                toolCallEventBus.publish(completedEvent)
            }
        }

        // 7. Publish resume event
        if (options.publishEvents) {
            eventPublishAction.publishWorkflowResumed(
                WorkflowResumedEvent(
                    runId = runId,
                    graphId = resolvedGraph.id,
                    nodeId = checkpoint.currentNodeId,
                    timestamp = Clock.System.now(),
                    metadata = mapOf(
                        "checkpointAge" to (Clock.System.now().toEpochMilliseconds() - checkpoint.timestamp.toEpochMilliseconds())
                    )
                )
            )
        }

        // 8. Apply beforeExecution transformers
        val transformedInput = transformerChain.beforeExecution(resolvedGraph, resumeMessage)
        val inputMessage = when (transformedInput) {
            is SpiceResult.Success -> transformedInput.value
            is SpiceResult.Failure -> {
                logger.error("Transformer chain failed in beforeExecution: ${transformedInput.error.message}")
                return@withContext transformedInput
            }
        }

        // 9. Resume via GraphRunner
        val result = graphRunner.resume(resolvedGraph, inputMessage)

        // 10. Handle result
        when (result) {
            is SpiceResult.Success -> {
                val finalMessage = result.value

                // Apply afterExecution transformers
                val transformedResult = transformerChain.afterExecution(resolvedGraph, inputMessage, finalMessage)
                val outputMessage = when (transformedResult) {
                    is SpiceResult.Success -> transformedResult.value
                    is SpiceResult.Failure -> {
                        logger.error("Transformer chain failed in afterExecution: ${transformedResult.error.message}")
                        return@withContext transformedResult
                    }
                }

                // Publish completion events
                if (options.publishEvents) {
                    when (outputMessage.state) {
                        ExecutionState.COMPLETED -> {
                            eventPublishAction.publishWorkflowCompleted(
                                WorkflowCompletedEvent(
                                    runId = runId,
                                    graphId = resolvedGraph.id,
                                    finalState = ExecutionState.COMPLETED,
                                    timestamp = Clock.System.now(),
                                    metadata = mapOf("resumed" to true)
                                )
                            )
                        }
                        ExecutionState.FAILED -> {
                            eventPublishAction.publishWorkflowCompleted(
                                WorkflowCompletedEvent(
                                    runId = runId,
                                    graphId = resolvedGraph.id,
                                    finalState = ExecutionState.FAILED,
                                    timestamp = Clock.System.now(),
                                    metadata = mapOf("resumed" to true)
                                )
                            )
                        }
                        ExecutionState.WAITING -> {
                            // Paused again at another HumanNode - save new checkpoint
                            checkpointSaveAction.save(runId, resolvedGraph,
                                stateMachineFactory.create().asSpringStateMachine().apply {
                                    extendedState.variables["message"] = outputMessage
                                    extendedState.variables["nodeId"] = outputMessage.nodeId
                                }
                            )
                        }
                        else -> { /* RUNNING or READY - continue */ }
                    }
                }

                // Cleanup on terminal state
                if (options.autoCleanup && outputMessage.state.isTerminal()) {
                    checkpointStore.deleteByRun(runId)
                    logger.debug("Cleaned up checkpoint after completion: {}", runId)
                }

                SpiceResult.success(outputMessage)
            }
            is SpiceResult.Failure -> {
                // Apply error handling
                if (options.publishEvents) {
                    eventPublishAction.publishWorkflowCompleted(
                        WorkflowCompletedEvent(
                            runId = runId,
                            graphId = resolvedGraph.id,
                            finalState = ExecutionState.FAILED,
                            timestamp = Clock.System.now(),
                            metadata = mapOf(
                                "resumed" to true,
                                "error" to result.error.message
                            )
                        )
                    )
                }

                if (options.throwOnError) {
                    throw RuntimeException(result.error.message, result.error.cause)
                } else {
                    result
                }
            }
        }
    }

    /**
     * Resume using only runId - requires graphRegistry to be configured
     */
    suspend fun resume(
        runId: String,
        options: ResumeOptions = ResumeOptions.DEFAULT
    ): SpiceResult<SpiceMessage> = resume(runId, null, null, options)

    /**
     * Build resume message by merging checkpoint message with user response
     *
     * Note: The message must remain in WAITING state as graphRunner.resume()
     * expects to receive a WAITING message and handles the state transition internally.
     */
    private fun buildResumeMessage(
        checkpointMessage: SpiceMessage,
        userResponse: SpiceMessage?,
        options: ResumeOptions
    ): Pair<SpiceMessage, io.github.noailabs.spice.toolspec.OAIToolCall?> {
        if (userResponse == null) {
            // No user response - keep in WAITING state for graphRunner.resume()
            return Pair(checkpointMessage, null)
        }

        // Extract USER_RESPONSE tool call if present
        val toolCall = userResponse.findToolCall("user_response")

        val responseData = if (toolCall != null) {
            buildMap<String, Any> {
                toolCall.function.getArgumentString("text")?.let {
                    put("response_text", it)
                }
                toolCall.function.getArgumentMap("structured_data")?.let { data ->
                    put("structured_response", data)
                    data["selected_option"]?.let { put("selected_option", it) }
                }
                put("user_response_tool_call", toolCall)
            }
        } else {
            mapOf("response_text" to userResponse.content)
        }

        // Merge data: checkpoint.data + userResponse.data + responseData (later overrides earlier)
        val mergedData = checkpointMessage.data + userResponse.data + responseData

        // IMPORTANT: Preserve checkpoint metadata (including __subgraphStack for Subgraph HITL resume)
        // and merge with userResponseMetadata (userResponseMetadata takes precedence)
        val mergedMetadata = checkpointMessage.metadata + options.userResponseMetadata

        val msg = checkpointMessage
            .withData(mergedData)
            .withToolCalls(userResponse.toolCalls)
            .withMetadata(mergedMetadata)

        return Pair(msg, toolCall)
    }

}
