package io.github.noailabs.spice

import io.github.noailabs.spice.toolspec.OAIToolCall
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 🌟 Unified Message Schema for Spice Framework 1.0.0
 *
 * SpiceMessage replaces the fragmented Comm/NodeResult/NodeContext pattern with a single,
 * comprehensive message type that supports:
 * - State machine (READY → RUNNING → WAITING → COMPLETED/FAILED)
 * - Idempotency (causationId for deduplication)
 * - Event bus integration (correlationId for message grouping)
 * - Tool calls (OAI ToolSpec compatible)
 * - Full execution context tracking
 *
 * **Architecture Benefits:**
 * - Single message type flows through entire graph execution
 * - State transitions are explicit and validated
 * - Idempotent execution via causation tracking
 * - Event-driven workflows via correlation tracking
 *
 * **Migration from 0.10.0:**
 * ```kotlin
 * // Old (0.10.0)
 * val comm = Comm(content = "input", from = "user")
 * val result = agent.processComm(comm)
 * val nodeResult = NodeResult.fromContext(ctx, data = result.content)
 *
 * // New (1.0.0)
 * val message = SpiceMessage(content = "input", from = "user")
 * val result = agent.processMessage(message)
 * // result is already a SpiceMessage, no conversion needed
 * ```
 *
 * @property id Unique identifier for this message
 * @property correlationId Groups related messages in a conversation/workflow
 * @property causationId Parent message ID that caused this message (for idempotency)
 * @property content Human-readable message content
 * @property data Structured data payload (JSON-serializable)
 * @property toolCalls OpenAI-compatible tool call specifications
 * @property state Current execution state (state machine)
 * @property stateHistory Audit trail of state transitions
 * @property metadata Execution context metadata (tenantId, userId, etc.)
 * @property graphId ID of the graph executing this message
 * @property nodeId Current node processing this message
 * @property runId Unique execution run identifier
 * @property from Message sender (user, agent, system)
 * @property to Message recipient (optional)
 * @property timestamp Message creation time
 * @property expiresAt Message expiration time (for TTL)
 *
 * @author Spice Framework
 * @since 1.0.0
 */
@Serializable
data class SpiceMessage(
    // ===== Identification =====
    /**
     * Unique identifier for this message
     * Format: msg_{uuid}
     */
    val id: String = generateMessageId(),

    /**
     * Groups related messages in a conversation or workflow
     * All messages in a single user session share the same correlationId
     * Used by event bus for message routing and aggregation
     */
    val correlationId: String,

    /**
     * Parent message ID that caused this message to be created
     * Used for idempotency: duplicate messages with same causationId are deduplicated
     * Null for root messages (user input, cron triggers)
     */
    val causationId: String? = null,

    // ===== Content =====
    /**
     * Human-readable message content
     * Primary communication payload
     */
    val content: String,

    /**
     * Structured data payload
     * JSON-serializable map for passing complex data between nodes
     * Note: Use @Contextual to handle polymorphic types
     */
    val data: Map<String, @Contextual Any> = emptyMap(),

    /**
     * OpenAI-compatible tool call specifications
     * Supports industry-standard tool calling (OpenAI, Anthropic, LangChain)
     * @since 0.10.0
     */
    val toolCalls: List<OAIToolCall> = emptyList(),

    // ===== Execution State =====
    /**
     * Current execution state (state machine)
     * Transitions:
     * - READY → RUNNING
     * - RUNNING → WAITING | COMPLETED | FAILED
     * - WAITING → RUNNING | FAILED
     */
    val state: ExecutionState = ExecutionState.READY,

    /**
     * Audit trail of state transitions
     * Records all state changes with timestamp and reason
     * Useful for debugging and execution replay
     */
    val stateHistory: List<StateTransition> = emptyList(),

    /**
     * Execution context metadata
     * Common keys: tenantId, userId, correlationId, sessionId
     * Propagated through graph execution
     */
    val metadata: Map<String, @Contextual Any> = emptyMap(),

    // ===== Graph Context =====
    /**
     * ID of the graph executing this message
     * Null for messages outside graph execution
     */
    val graphId: String? = null,

    /**
     * Current node processing this message
     * Null for messages between graph executions
     */
    val nodeId: String? = null,

    /**
     * Unique execution run identifier
     * Links all messages in a single graph execution
     */
    val runId: String? = null,

    // ===== Actors =====
    /**
     * Message sender
     * Examples: "user", "agent-reservation", "system"
     */
    val from: String,

    /**
     * Message recipient (optional)
     * Examples: "agent-confirmation", "user", null (broadcast)
     */
    val to: String? = null,

    // ===== Temporal =====
    /**
     * Message creation timestamp
     * Uses Kotlin datetime for multiplatform support
     */
    val timestamp: Instant = Clock.System.now(),

    /**
     * Message expiration time (TTL)
     * Null = no expiration
     * Used for cache eviction and message cleanup
     */
    val expiresAt: Instant? = null
) {
    companion object {
        /**
         * Generate unique message ID
         * Format: msg_{uuid}
         */
        fun generateMessageId(): String {
            return "msg_${UUID.randomUUID().toString().replace("-", "").take(24)}"
        }

        /**
         * Create a new message with minimal fields (convenience constructor)
         * Useful for testing and simple use cases
         *
         * @param content Message content
         * @param from Sender identifier
         * @param correlationId Optional correlation ID (generated if not provided)
         * @return SpiceMessage in READY state
         */
        fun create(
            content: String,
            from: String,
            correlationId: String = generateMessageId()
        ): SpiceMessage {
            return SpiceMessage(
                content = content,
                from = from,
                correlationId = correlationId,
                state = ExecutionState.READY
            )
        }

        /**
         * Create a message from user input
         *
         * Normalizes all user input (chat, form, voice, webhook) as USER_INPUT tool call event.
         * This is the recommended way to handle user-initiated messages in Spice 2.0.
         *
         * @param text User's text input
         * @param userId User identifier
         * @param metadata Additional metadata (sessionId, tenantId, etc.)
         * @param inputType Type of input (chat, form, voice, attachment, webhook)
         * @param correlationId Optional correlation ID (generated if not provided)
         * @return SpiceMessage with USER_INPUT tool call in READY state
         */
        fun fromUserInput(
            text: String,
            userId: String,
            metadata: Map<String, Any> = emptyMap(),
            inputType: String = "chat",
            correlationId: String = generateMessageId()
        ): SpiceMessage {
            val toolCall = OAIToolCall.userInput(
                text = text,
                metadata = metadata,
                inputType = inputType
            )

            return SpiceMessage(
                content = text, // Keep content for backward compatibility
                from = userId,
                correlationId = correlationId,
                toolCalls = listOf(toolCall),
                metadata = metadata,
                state = ExecutionState.READY
            )
        }
    }

    /**
     * Create a reply message
     * Preserves correlationId for conversation continuity
     * Sets causationId to this message's ID for idempotency tracking
     *
     * @param content Reply content
     * @param from Sender identifier
     * @return New SpiceMessage in READY state
     */
    fun reply(content: String, from: String): SpiceMessage {
        return copy(
            id = generateMessageId(),
            content = content,
            from = from,
            causationId = this.id,  // Track causation for idempotency
            state = ExecutionState.READY,
            stateHistory = emptyList(),  // Fresh state history for new message
            timestamp = Clock.System.now()
        )
    }

    /**
     * Transition to a new state
     * Validates transition and records history
     *
     * @param newState Target state
     * @param reason Transition reason (for audit trail)
     * @param nodeId Node ID where transition occurred
     * @return New SpiceMessage with updated state
     * @throws IllegalStateException if transition is invalid
     */
    fun transitionTo(
        newState: ExecutionState,
        reason: String? = null,
        nodeId: String? = null
    ): SpiceMessage {
        // Validate transition
        if (!state.canTransitionTo(newState)) {
            throw IllegalStateException(
                "Invalid state transition: $state → $newState"
            )
        }

        // Record transition
        val transition = StateTransition(
            from = state,
            to = newState,
            timestamp = Clock.System.now(),
            reason = reason,
            nodeId = nodeId
        )

        return copy(
            state = newState,
            stateHistory = stateHistory + transition
        )
    }

    /**
     * Add a tool call to this message
     * Returns new message with updated toolCalls list
     *
     * @param toolCall Tool call to add
     * @return New SpiceMessage with tool call
     */
    fun withToolCall(toolCall: OAIToolCall): SpiceMessage {
        return copy(toolCalls = toolCalls + toolCall)
    }

    /**
     * Add multiple tool calls to this message
     *
     * @param calls Tool calls to add
     * @return New SpiceMessage with tool calls
     */
    fun withToolCalls(calls: List<OAIToolCall>): SpiceMessage {
        return copy(toolCalls = toolCalls + calls)
    }

    /**
     * Update metadata
     * Merges new metadata with existing metadata
     *
     * @param updates Metadata updates
     * @return New SpiceMessage with merged metadata
     */
    fun withMetadata(updates: Map<String, Any>): SpiceMessage {
        return copy(metadata = metadata + updates)
    }

    /**
     * Update data
     * Merges new data with existing data
     *
     * @param updates Data updates
     * @return New SpiceMessage with merged data
     */
    fun withData(updates: Map<String, Any>): SpiceMessage {
        return copy(data = data + updates)
    }

    /**
     * Update graph context
     *
     * @param graphId Graph ID
     * @param nodeId Current node ID
     * @param runId Run ID
     * @return New SpiceMessage with updated graph context
     */
    fun withGraphContext(
        graphId: String? = this.graphId,
        nodeId: String? = this.nodeId,
        runId: String? = this.runId
    ): SpiceMessage {
        return copy(
            graphId = graphId,
            nodeId = nodeId,
            runId = runId
        )
    }

    /**
     * Check if message has expired
     * @return true if expiresAt is in the past
     */
    fun isExpired(): Boolean {
        return expiresAt?.let { Clock.System.now() > it } ?: false
    }

    /**
     * Check if message has tool calls
     */
    fun hasToolCalls(): Boolean = toolCalls.isNotEmpty()

    /**
     * Check if message has a specific tool call by function name
     */
    fun hasToolCall(functionName: String): Boolean {
        return toolCalls.any { it.function.name == functionName }
    }

    /**
     * Get tool call by function name
     */
    fun findToolCall(functionName: String): OAIToolCall? {
        return toolCalls.find { it.function.name == functionName }
    }

    /**
     * Get all tool call function names
     */
    fun getToolCallNames(): List<String> {
        return toolCalls.map { it.function.name }
    }

    /**
     * Check if message is in terminal state (COMPLETED or FAILED)
     */
    fun isTerminal(): Boolean {
        return state == ExecutionState.COMPLETED || state == ExecutionState.FAILED
    }

    /**
     * Check if message is waiting for external input
     */
    fun isWaiting(): Boolean {
        return state == ExecutionState.WAITING
    }

    /**
     * Check if message is currently executing
     */
    fun isRunning(): Boolean {
        return state == ExecutionState.RUNNING
    }

    /**
     * Get metadata value by key with type safety
     */
    inline fun <reified T> getMetadata(key: String): T? {
        return metadata[key] as? T
    }

    /**
     * Get data value by key with type safety
     */
    inline fun <reified T> getData(key: String): T? {
        return data[key] as? T
    }
}
