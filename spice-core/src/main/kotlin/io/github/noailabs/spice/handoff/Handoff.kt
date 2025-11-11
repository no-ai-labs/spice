package io.github.noailabs.spice.handoff

import io.github.noailabs.spice.AgentContext
import io.github.noailabs.spice.AnyValueMapSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Represents a request to hand off processing to a human agent.
 * Contains all necessary context and instructions for the human to take over.
 */
@Serializable
data class HandoffRequest(
    /**
     * Reason for the handoff (e.g., "Complex question requiring expert knowledge")
     */
    val reason: String,

    /**
     * Specific task(s) the human needs to perform
     */
    val tasks: List<HandoffTask>,

    /**
     * Priority level of this handoff
     */
    val priority: HandoffPriority = HandoffPriority.NORMAL,

    /**
     * Conversation history or context needed for the human
     */
    val conversationHistory: List<String> = emptyList(),

    /**
     * Metadata for routing and tracking
     */
    @Serializable(with = AnyValueMapSerializer::class)
    val metadata: Map<String, Any?> = emptyMap(),

    /**
     * When this handoff was created
     */
    val createdAt: String = Instant.now().toString(),

    /**
     * Original agent ID that initiated the handoff
     */
    val fromAgentId: String,

    /**
     * Target human agent/pool ID
     */
    val toAgentId: String = "human-agent-pool"
)

/**
 * A specific task for the human to complete
 */
@Serializable
data class HandoffTask(
    /**
     * Task ID for tracking
     */
    val id: String,

    /**
     * What the human needs to do
     */
    val description: String,

    /**
     * Type of task
     */
    val type: HandoffTaskType = HandoffTaskType.RESPOND,

    /**
     * Additional context for this specific task
     */
    @Serializable(with = AnyValueMapSerializer::class)
    val context: Map<String, Any?> = emptyMap(),

    /**
     * Whether this task is required or optional
     */
    val required: Boolean = true
)

/**
 * Types of tasks humans can perform
 */
@Serializable
enum class HandoffTaskType {
    RESPOND,        // Respond to customer query
    APPROVE,        // Approve/reject an action
    REVIEW,         // Review content
    INVESTIGATE,    // Investigate an issue
    ESCALATE,       // Further escalate
    CUSTOM          // Custom task type
}

/**
 * Priority levels for handoff requests
 */
@Serializable
enum class HandoffPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}

/**
 * Response from a human after completing a handoff
 */
@Serializable
data class HandoffResponse(
    /**
     * ID of the original handoff request
     */
    val handoffId: String,

    /**
     * Human agent ID who handled this
     */
    val humanAgentId: String,

    /**
     * The actual response/result
     */
    val result: String,

    /**
     * Completed tasks
     */
    val completedTasks: List<CompletedTask> = emptyList(),

    /**
     * Whether to return to automated processing
     */
    val returnToBot: Boolean = false,

    /**
     * Additional notes from the human
     */
    val notes: String? = null,

    /**
     * When this was completed
     */
    val completedAt: String = Instant.now().toString(),

    /**
     * Metadata
     */
    @Serializable(with = AnyValueMapSerializer::class)
    val metadata: Map<String, Any?> = emptyMap()
)

/**
 * A completed task with its result
 */
@Serializable
data class CompletedTask(
    val taskId: String,
    val result: String,
    val success: Boolean = true
)

/**
 * Metadata keys used in handoff context
 */
object HandoffMetadataKeys {
    const val HANDOFF_REQUEST = "handoff_request"
    const val HANDOFF_RESPONSE = "handoff_response"
    const val HANDOFF_ID = "handoff_id"
    const val ORIGINAL_AGENT = "original_agent_id"
    const val IS_HANDOFF = "is_handoff"
    const val IS_RETURN_FROM_HANDOFF = "is_return_from_handoff"
}
