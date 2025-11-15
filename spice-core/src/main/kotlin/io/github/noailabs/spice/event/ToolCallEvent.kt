package io.github.noailabs.spice.event

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.toolspec.OAIToolCall
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * 🎯 Tool Call Event (Spice 2.0)
 *
 * Event-driven architecture for tool calls in multi-agent orchestration.
 * All tool call lifecycle events inherit from this sealed class.
 *
 * **Event Flow:**
 * ```
 * Agent/Node
 *     ↓
 * Emitted → EventBus → Subscribers
 *     ↓
 * Received → Processing
 *     ↓
 * Completed/Failed → EventBus → Subscribers
 * ```
 *
 * **Use Cases:**
 * - Multi-agent orchestration (agent A emits tool call → agent B receives)
 * - Audit trail (track all tool call lifecycle events)
 * - Debugging (inspect tool call flow)
 * - Replay (reconstruct execution from events)
 *
 * @property id Unique event ID
 * @property toolCall The tool call this event is about
 * @property message SpiceMessage context
 * @property timestamp When event occurred
 * @property metadata Additional event metadata
 *
 * @since 2.0.0
 */
@Serializable
sealed class ToolCallEvent {
    abstract val id: String
    abstract val toolCall: OAIToolCall
    abstract val message: SpiceMessage
    abstract val timestamp: Instant
    abstract val metadata: Map<String, @Contextual Any>

    /**
     * 📤 Tool Call Emitted
     *
     * A node or agent has emitted a tool call.
     * This is the start of the tool call lifecycle.
     *
     * **Examples:**
     * - HumanNode emits REQUEST_USER_SELECTION
     * - Agent emits REQUEST_USER_CONFIRMATION
     * - Tool node emits tool call result
     *
     * @property emittedBy Node/Agent ID that emitted the tool call
     * @property graphId Graph ID (if in graph execution)
     * @property runId Run ID (if in graph execution)
     */
    @Serializable
    data class Emitted(
        override val id: String = generateEventId(),
        override val toolCall: OAIToolCall,
        override val message: SpiceMessage,
        override val timestamp: Instant = Clock.System.now(),
        override val metadata: Map<String, @Contextual Any> = emptyMap(),
        val emittedBy: String,
        val graphId: String? = null,
        val runId: String? = null
    ) : ToolCallEvent()

    /**
     * 📥 Tool Call Received
     *
     * A subscriber has received and acknowledged a tool call.
     * Used for multi-agent orchestration.
     *
     * **Examples:**
     * - Agent B receives tool call emitted by Agent A
     * - Frontend receives REQUEST_USER_INPUT
     * - Monitoring service receives all tool calls
     *
     * @property receivedBy Subscriber ID that received the tool call
     * @property originalEventId ID of the Emitted event
     */
    @Serializable
    data class Received(
        override val id: String = generateEventId(),
        override val toolCall: OAIToolCall,
        override val message: SpiceMessage,
        override val timestamp: Instant = Clock.System.now(),
        override val metadata: Map<String, @Contextual Any> = emptyMap(),
        val receivedBy: String,
        val originalEventId: String
    ) : ToolCallEvent()

    /**
     * ✅ Tool Call Completed
     *
     * A tool call has been successfully completed.
     *
     * **Examples:**
     * - User responded to REQUEST_USER_INPUT → USER_RESPONSE
     * - Tool execution finished successfully
     * - Agent completed processing
     *
     * @property result The result of the tool call (may be null)
     * @property completedBy Who completed the tool call (agent/user/system)
     * @property originalEventId ID of the Emitted event
     * @property durationMs How long the tool call took
     */
    @Serializable
    data class Completed(
        override val id: String = generateEventId(),
        override val toolCall: OAIToolCall,
        override val message: SpiceMessage,
        override val timestamp: Instant = Clock.System.now(),
        override val metadata: Map<String, @Contextual Any> = emptyMap(),
        val result: @Contextual Any? = null,
        val completedBy: String,
        val originalEventId: String,
        val durationMs: Long
    ) : ToolCallEvent()

    /**
     * ❌ Tool Call Failed
     *
     * A tool call has failed with an error.
     *
     * **Examples:**
     * - Tool execution threw exception
     * - Validation failed
     * - Timeout occurred
     * - User cancelled
     *
     * @property error The error that occurred
     * @property failedBy Who/what caused the failure
     * @property originalEventId ID of the Emitted event
     * @property isRetryable Whether the tool call can be retried
     */
    @Serializable
    data class Failed(
        override val id: String = generateEventId(),
        override val toolCall: OAIToolCall,
        override val message: SpiceMessage,
        override val timestamp: Instant = Clock.System.now(),
        override val metadata: Map<String, @Contextual Any> = emptyMap(),
        @Contextual val error: SpiceError,
        val failedBy: String,
        val originalEventId: String,
        val isRetryable: Boolean = false
    ) : ToolCallEvent()

    /**
     * 🔄 Tool Call Retrying
     *
     * A failed tool call is being retried.
     *
     * @property attemptNumber Which retry attempt this is (1, 2, 3...)
     * @property previousEventId ID of the previous Failed event
     * @property maxAttempts Maximum number of retry attempts
     */
    @Serializable
    data class Retrying(
        override val id: String = generateEventId(),
        override val toolCall: OAIToolCall,
        override val message: SpiceMessage,
        override val timestamp: Instant = Clock.System.now(),
        override val metadata: Map<String, @Contextual Any> = emptyMap(),
        val attemptNumber: Int,
        val previousEventId: String,
        val maxAttempts: Int
    ) : ToolCallEvent()

    /**
     * ⏸️ Tool Call Cancelled
     *
     * A tool call was cancelled before completion.
     *
     * **Examples:**
     * - User cancelled HITL interaction
     * - Timeout occurred
     * - Graph execution was stopped
     *
     * @property cancelledBy Who cancelled the tool call
     * @property originalEventId ID of the Emitted event
     * @property reason Why it was cancelled
     */
    @Serializable
    data class Cancelled(
        override val id: String = generateEventId(),
        override val toolCall: OAIToolCall,
        override val message: SpiceMessage,
        override val timestamp: Instant = Clock.System.now(),
        override val metadata: Map<String, @Contextual Any> = emptyMap(),
        val cancelledBy: String,
        val originalEventId: String,
        val reason: String
    ) : ToolCallEvent()

    companion object {
        /**
         * Generate unique event ID
         */
        fun generateEventId(): String {
            return "evt_${Clock.System.now().toEpochMilliseconds()}_${(Math.random() * 1000000).toInt()}"
        }

        /**
         * Get event type name
         */
        fun ToolCallEvent.typeName(): String = when (this) {
            is Emitted -> "EMITTED"
            is Received -> "RECEIVED"
            is Completed -> "COMPLETED"
            is Failed -> "FAILED"
            is Retrying -> "RETRYING"
            is Cancelled -> "CANCELLED"
        }

        /**
         * Check if event is terminal (Completed, Failed, Cancelled)
         */
        fun ToolCallEvent.isTerminal(): Boolean = when (this) {
            is Completed, is Failed, is Cancelled -> true
            else -> false
        }

        /**
         * Get correlation chain
         * Returns the originalEventId for events that have one
         */
        fun ToolCallEvent.getCorrelationId(): String? = when (this) {
            is Emitted -> id  // Emitted events are the root
            is Received -> originalEventId
            is Completed -> originalEventId
            is Failed -> originalEventId
            is Retrying -> previousEventId
            is Cancelled -> originalEventId
        }
    }
}
