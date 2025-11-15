package io.github.noailabs.spice.graph.checkpoint

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.event.ToolCallEvent
import io.github.noailabs.spice.graph.Graph
import io.github.noailabs.spice.graph.runner.GraphRunner
import kotlinx.datetime.Clock

/**
 * 🔌 Checkpoint Extensions for GraphRunner
 *
 * Extension methods to add checkpoint support to any GraphRunner implementation.
 *
 * **Architecture:**
 * - Wraps GraphRunner.execute() to save checkpoints
 * - Provides GraphRunner.resumeFromCheckpoint() to restore from checkpoint
 * - Automatic cleanup on success/failure
 *
 * @since 1.0.0
 */

/**
 * Execute graph with checkpoint support
 *
 * **Flow:**
 * 1. Execute graph normally via runner.execute()
 * 2. If message enters WAITING state → save checkpoint
 * 3. If error occurs → save checkpoint (if config.saveOnError)
 * 4. If success → delete checkpoints (if config.autoCleanup)
 *
 * @param graph Graph to execute
 * @param message Input message
 * @param store Checkpoint store
 * @param config Checkpoint configuration
 * @return SpiceResult with final message
 */
suspend fun GraphRunner.executeWithCheckpoint(
    graph: Graph,
    message: SpiceMessage,
    store: CheckpointStore,
    config: CheckpointConfig = CheckpointConfig.DEFAULT
): SpiceResult<SpiceMessage> {

    val runId = message.runId ?: SpiceMessage.generateMessageId()
    val contextualMessage = message.withGraphContext(
        graphId = graph.id,
        runId = runId
    )

    // Execute graph
    val result = this.execute(graph, contextualMessage)

    return when (result) {
        is SpiceResult.Success -> {
            val finalMessage = result.value

            // Save checkpoint if WAITING
            if (config.saveOnHitl && finalMessage.state == ExecutionState.WAITING) {
                val checkpoint = Checkpoint.fromMessage(
                    message = finalMessage,
                    graphId = graph.id,
                    runId = runId
                )

                // Add TTL expiration
                val checkpointWithTtl = checkpoint.copy(
                    expiresAt = Clock.System.now() + config.ttl
                )

                store.save(checkpointWithTtl)
            }

            // Cleanup on success
            if (config.autoCleanup && finalMessage.state.isTerminal()) {
                store.deleteByRun(runId)
            }

            result
        }
        is SpiceResult.Failure -> {
            // Save checkpoint on error if configured
            if (config.saveOnError) {
                val checkpoint = Checkpoint(
                    id = Checkpoint.generateId(),
                    runId = runId,
                    graphId = graph.id,
                    currentNodeId = contextualMessage.nodeId ?: graph.entryPoint,
                    message = contextualMessage,
                    state = contextualMessage.data,
                    metadata = contextualMessage.metadata,
                    executionState = GraphExecutionState.FAILED,
                    expiresAt = Clock.System.now() + config.ttl
                )
                store.save(checkpoint)
            }

            result
        }
    }
}

/**
 * Resume graph execution from checkpoint (Spice 2.0: Tool Call based)
 *
 * **Flow:**
 * 1. Load checkpoint from store
 * 2. Validate checkpoint not expired
 * 3. Reconstruct message from checkpoint
 * 4. Merge USER_RESPONSE tool call if provided
 * 5. Call GraphRunner.resume() with message
 * 6. Cleanup checkpoint on success
 *
 * **Spice 2.0 Usage:**
 * ```kotlin
 * val userResponseMsg = SpiceMessage.create("Yes", "user123")
 *     .withToolCall(OAIToolCall.userResponse(
 *         text = "Yes",
 *         structuredData = mapOf("selected_option" to "option1")
 *     ))
 *
 * runner.resumeFromCheckpoint(
 *     graph, checkpointId, userResponseMsg, store
 * )
 * ```
 *
 * @param graph Graph to resume
 * @param checkpointId Checkpoint identifier
 * @param userResponse SpiceMessage with USER_RESPONSE tool call (optional)
 * @param store Checkpoint store
 * @param config Checkpoint configuration
 * @return SpiceResult with final message
 */
suspend fun GraphRunner.resumeFromCheckpoint(
    graph: Graph,
    checkpointId: String,
    userResponse: SpiceMessage? = null,
    store: CheckpointStore,
    config: CheckpointConfig = CheckpointConfig.DEFAULT
): SpiceResult<SpiceMessage> {

    // Load checkpoint
    val checkpoint = when (val result = store.load(checkpointId)) {
        is SpiceResult.Success -> result.value
        is SpiceResult.Failure -> return SpiceResult.failure(result.error)
    }

    // Validate checkpoint
    if (checkpoint.isExpired()) {
        return SpiceResult.failure(
            SpiceError.validationError("Checkpoint expired: $checkpointId")
        )
    }

    // Reconstruct message
    val message = checkpoint.message ?: return SpiceResult.failure(
        SpiceError.executionError("Checkpoint has no message: $checkpointId")
    )

    // Spice 2.0: Handle USER_RESPONSE tool call
    val (resumeMessage, responseToolCall) = if (userResponse != null) {
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

        val msg = message
            .withData(responseData)
            .withToolCalls(userResponse.toolCalls)
            .transitionTo(ExecutionState.RUNNING, "Resuming after user response")

        Pair(msg, toolCall)
    } else {
        // No response - just resume
        val msg = message.transitionTo(ExecutionState.RUNNING, "Resuming from checkpoint")
        Pair(msg, null)
    }

    // Spice 2.0: Update checkpoint with responseToolCall for replay/debugging
    if (responseToolCall != null) {
        val updatedCheckpoint = checkpoint.copy(responseToolCall = responseToolCall)
        store.save(updatedCheckpoint)  // Persist the response for audit/replay

        // Publish ToolCallEvent.Completed for the pending tool call
        if (graph.toolCallEventBus != null && checkpoint.pendingToolCall != null) {
            val startTime = checkpoint.timestamp.toEpochMilliseconds()
            val endTime = Clock.System.now().toEpochMilliseconds()
            val durationMs = endTime - startTime

            val completedEvent = ToolCallEvent.Completed(
                toolCall = checkpoint.pendingToolCall,
                message = resumeMessage,
                result = responseToolCall,
                completedBy = resumeMessage.from,
                originalEventId = checkpoint.pendingToolCall.id,
                durationMs = durationMs,
                metadata = mapOf(
                    "checkpointId" to checkpointId,
                    "graphId" to graph.id,
                    "nodeId" to (checkpoint.currentNodeId),
                    "runId" to (resumeMessage.runId ?: "")
                )
            )

            graph.toolCallEventBus.publish(completedEvent)
        }
    }

    // Resume execution
    val result = this.resume(graph, resumeMessage)

    // Cleanup on success
    if (result.isSuccess && config.autoCleanup) {
        val finalMessage = (result as SpiceResult.Success).value
        if (finalMessage.state.isTerminal()) {
            store.deleteByRun(checkpoint.runId)
        }
    }

    return result
}

/**
 * Helper extension to check if ExecutionState is terminal
 */
private fun ExecutionState.isTerminal(): Boolean {
    return this == ExecutionState.COMPLETED || this == ExecutionState.FAILED
}
