package io.github.noailabs.spice.graph.checkpoint

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.toolspec.OAIToolCall
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

    // Spice 2.0: Tool call based HITL (event-first architecture)
    val pendingToolCall: OAIToolCall? = null,      // REQUEST_USER_INPUT/SELECTION from HITL Tool
    val responseToolCall: OAIToolCall? = null,     // USER_RESPONSE from user

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

            // Spice 2.0: Extract tool call from message (REQUEST_USER_INPUT or REQUEST_USER_SELECTION)
            // Use lastOrNull to get the most recent tool call (handles loops/retries where multiple tool calls accumulate)
            // Supports both legacy names and Spice 1.0.6+ HITL Tool standard names
            val pendingToolCall = message.toolCalls.lastOrNull {
                it.function.name in listOf(
                    // Legacy names (backward compatibility)
                    "request_user_input",
                    "request_user_selection",
                    "request_user_confirmation",
                    // Spice 1.0.6+ HITL Tool standard names (from OAIToolCall.Companion.ToolNames)
                    OAIToolCall.Companion.ToolNames.HITL_REQUEST_INPUT,      // "hitl_request_input"
                    OAIToolCall.Companion.ToolNames.HITL_REQUEST_SELECTION   // "hitl_request_selection"
                )
            }

            return Checkpoint(
                id = generateId(),
                runId = runId,
                graphId = graphId,
                currentNodeId = message.nodeId ?: error("WAITING message must have nodeId"),
                message = message,
                state = message.data,
                metadata = message.metadata,
                pendingToolCall = pendingToolCall,
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
