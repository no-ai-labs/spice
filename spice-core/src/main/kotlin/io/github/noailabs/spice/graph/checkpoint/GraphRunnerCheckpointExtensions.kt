package io.github.noailabs.spice.graph.checkpoint

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.Graph
import io.github.noailabs.spice.graph.nodes.HumanResponse
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
 * Resume graph execution from checkpoint
 *
 * **Flow:**
 * 1. Load checkpoint from store
 * 2. Validate checkpoint not expired
 * 3. Reconstruct message from checkpoint
 * 4. Call GraphRunner.resume() with message
 * 5. Cleanup checkpoint on success
 *
 * @param graph Graph to resume
 * @param checkpointId Checkpoint identifier
 * @param humanResponse Optional human response (for HITL resume)
 * @param store Checkpoint store
 * @param config Checkpoint configuration
 * @return SpiceResult with final message
 */
suspend fun GraphRunner.resumeFromCheckpoint(
    graph: Graph,
    checkpointId: String,
    humanResponse: HumanResponse? = null,
    store: CheckpointStore,
    config: CheckpointConfig = CheckpointConfig.DEFAULT
): SpiceResult<SpiceMessage> {

    // Load checkpoint
    val checkpoint = when (val result = store.load(checkpointId)) {
        is SpiceResult.Success -> result.value
        is SpiceResult.Failure -> return result
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

    // Add human response if provided
    val resumeMessage = if (humanResponse != null) {
        message.withData(
            mapOf(
                "human_response" to humanResponse,
                "selected_option" to humanResponse.selectedOption,
                "free_text" to humanResponse.freeText
            )
        ).transitionTo(
            ExecutionState.RUNNING,
            "Resuming after human input"
        )
    } else {
        message.transitionTo(
            ExecutionState.RUNNING,
            "Resuming from checkpoint"
        )
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
