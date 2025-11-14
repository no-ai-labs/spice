package io.github.noailabs.spice.graph.checkpoint

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.graph.nodes.HumanInteraction
import io.github.noailabs.spice.graph.nodes.HumanResponse
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * 💾 Checkpoint for Spice Framework 1.0.0
 *
 * Represents a saved execution state for graph resumption.
 * Used for HITL workflows, long-running processes, and crash recovery.
 *
 * **Architecture:**
 * - Checkpoint saved when message enters WAITING state
 * - Restored when resuming execution via GraphRunner.resume()
 * - Serialized to JSON for storage (Redis, DB, file system)
 *
 * **Key Fields:**
 * - `id`: Unique checkpoint identifier
 * - `runId`: Links to specific graph execution run
 * - `graphId`: Graph definition identifier
 * - `currentNodeId`: Node where execution paused
 * - `state`: Complete execution state (nested Maps/Lists supported)
 * - `message`: The SpiceMessage at pause point
 *
 * **Serialization:**
 * - Use CheckpointSerializer for production (preserves types)
 * - Direct kotlinx.serialization for simple cases
 *
 * @since 1.0.0
 */
@Serializable
data class Checkpoint(
    val id: String,
    val runId: String,
    val graphId: String,
    val currentNodeId: String,

    // Execution state (nested structures preserved)
    val state: Map<String, @Contextual Any> = emptyMap(),
    val metadata: Map<String, @Contextual Any> = emptyMap(),

    // SpiceMessage at checkpoint
    val message: SpiceMessage? = null,

    // Execution status
    val executionState: GraphExecutionState = GraphExecutionState.WAITING_FOR_HUMAN,

    // Human interaction data (if paused for HITL)
    val pendingInteraction: HumanInteraction? = null,
    val humanResponse: HumanResponse? = null,

    // Timestamps
    val timestamp: Instant = Clock.System.now(),
    val expiresAt: Instant? = null
) {
    companion object {
        /**
         * Generate unique checkpoint ID
         */
        fun generateId(): String {
            return "cp_${Clock.System.now().toEpochMilliseconds()}_${(Math.random() * 1000000).toInt()}"
        }

        /**
         * Create checkpoint from message in WAITING state
         */
        fun fromMessage(
            message: SpiceMessage,
            graphId: String,
            runId: String
        ): Checkpoint {
            require(message.state == io.github.noailabs.spice.ExecutionState.WAITING) {
                "Can only create checkpoint from WAITING message, got: ${message.state}"
            }

            return Checkpoint(
                id = generateId(),
                runId = runId,
                graphId = graphId,
                currentNodeId = message.nodeId ?: error("WAITING message must have nodeId"),
                message = message,
                state = message.data,
                metadata = message.metadata,
                pendingInteraction = message.getData<HumanInteraction>("human_interaction"),
                timestamp = Clock.System.now()
            )
        }
    }

    /**
     * Check if checkpoint has expired
     */
    fun isExpired(): Boolean {
        return expiresAt?.let { Clock.System.now() > it } ?: false
    }
}

/**
 * 📊 Graph Execution State (for checkpoints)
 */
@Serializable
enum class GraphExecutionState {
    RUNNING,
    WAITING_FOR_HUMAN,
    COMPLETED,
    FAILED,
    CANCELLED
}
