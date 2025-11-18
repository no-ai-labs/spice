package io.github.noailabs.spice

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * 🎯 Execution State Machine for Spice Framework 1.0.0
 *
 * Defines the lifecycle states of a SpiceMessage as it flows through graph execution.
 * Enforces valid state transitions and provides audit trail via StateTransition records.
 *
 * **State Transition Rules:**
 * ```
 * READY ──────────→ RUNNING
 *                      │
 *         ┌────────────┼────────────┐
 *         ↓            ↓            ↓
 *     WAITING ──→ COMPLETED      FAILED
 *         │
 *         └──────→ FAILED
 * ```
 *
 * **Allowed Transitions:**
 * - `READY → RUNNING`: Message starts execution
 * - `RUNNING → WAITING`: External input required (HITL, event)
 * - `RUNNING → COMPLETED`: Successful completion
 * - `RUNNING → FAILED`: Execution error
 * - `WAITING → RUNNING`: Resume after external input
 * - `WAITING → FAILED`: Timeout or cancellation
 *
 * **Forbidden Transitions:**
 * - `COMPLETED → *`: Terminal state, cannot transition
 * - `FAILED → *`: Terminal state, cannot transition
 * - `READY → WAITING`: Cannot wait before starting
 * - `READY → COMPLETED/FAILED`: Cannot skip execution
 *
 * **Usage Example:**
 * ```kotlin
 * // Start execution
 * var message = SpiceMessage.create("input", "user")
 * message = message.transitionTo(ExecutionState.RUNNING, "Graph started")
 *
 * // Wait for human input
 * message = message.transitionTo(ExecutionState.WAITING, "HITL required", nodeId = "human-1")
 *
 * // Resume after input
 * message = message.transitionTo(ExecutionState.RUNNING, "User responded")
 *
 * // Complete
 * message = message.transitionTo(ExecutionState.COMPLETED, "Graph finished")
 * ```
 *
 * @author Spice Framework
 * @since 1.0.0
 */
@Serializable
enum class ExecutionState {
    /**
     * Message created but not yet started
     * Initial state for all new messages
     * Next states: RUNNING
     */
    READY,

    /**
     * Currently executing in a graph
     * Active processing state
     * Next states: WAITING, COMPLETED, FAILED
     */
    RUNNING,

    /**
     * Waiting for external input
     * Examples: human-in-the-loop, event subscription, async callback
     * Next states: RUNNING (resume), FAILED (timeout/cancel)
     */
    WAITING,

    /**
     * Successfully completed execution
     * Terminal state - no further transitions allowed
     */
    COMPLETED,

    /**
     * Execution failed with error
     * Terminal state - no further transitions allowed
     */
    FAILED;

    /**
     * Check if transition to target state is allowed
     *
     * @param target Target state
     * @return true if transition is valid
     */
    fun canTransitionTo(target: ExecutionState): Boolean {
        return when (this) {
            READY -> target == RUNNING
            RUNNING -> target in setOf(WAITING, COMPLETED, FAILED)
            WAITING -> target in setOf(RUNNING, FAILED)
            COMPLETED -> false  // Terminal state
            FAILED -> false     // Terminal state
        }
    }

    /**
     * Get all valid next states from current state
     *
     * @return Set of valid target states
     */
    fun validNextStates(): Set<ExecutionState> {
        return when (this) {
            READY -> setOf(RUNNING)
            RUNNING -> setOf(WAITING, COMPLETED, FAILED)
            WAITING -> setOf(RUNNING, FAILED)
            COMPLETED -> emptySet()
            FAILED -> emptySet()
        }
    }

    /**
     * Check if this is a terminal state
     * Terminal states cannot transition to any other state
     *
     * @return true if state is COMPLETED or FAILED
     */
    fun isTerminal(): Boolean {
        return this == COMPLETED || this == FAILED
    }

    /**
     * Check if this is an active state
     * Active states indicate ongoing execution
     *
     * @return true if state is RUNNING or WAITING
     */
    fun isActive(): Boolean {
        return this == RUNNING || this == WAITING
    }

    /**
     * Human-readable description of the state
     */
    fun description(): String {
        return when (this) {
            READY -> "Ready to start execution"
            RUNNING -> "Currently executing"
            WAITING -> "Waiting for external input"
            COMPLETED -> "Successfully completed"
            FAILED -> "Execution failed"
        }
    }
}

/**
 * 📝 State Transition Record
 *
 * Immutable record of a state transition for audit trail and debugging.
 * Stored in SpiceMessage.stateHistory for complete execution replay.
 *
 * **Use Cases:**
 * - Debugging: Understand why execution failed
 * - Audit: Track who/what triggered state changes
 * - Replay: Reconstruct execution flow
 * - Metrics: Measure time spent in each state
 *
 * **Example:**
 * ```kotlin
 * val transition = StateTransition(
 *     from = ExecutionState.RUNNING,
 *     to = ExecutionState.WAITING,
 *     timestamp = Clock.System.now(),
 *     reason = "User selection required",
 *     nodeId = "selection-node-1"
 * )
 * ```
 *
 * @property from Previous state
 * @property to New state
 * @property timestamp When transition occurred
 * @property reason Why transition occurred (for debugging)
 * @property nodeId Node ID where transition occurred (optional)
 *
 * @author Spice Framework
 * @since 1.0.0
 */
@Serializable
data class StateTransition(
    /**
     * Previous execution state
     */
    val from: ExecutionState,

    /**
     * New execution state
     */
    val to: ExecutionState,

    /**
     * Timestamp when transition occurred
     * Uses kotlinx.datetime for multiplatform support
     */
    val timestamp: Instant,

    /**
     * Human-readable reason for transition
     * Examples: "Graph started", "HITL required", "Execution error: timeout"
     * Useful for debugging and audit logs
     */
    val reason: String? = null,

    /**
     * Node ID where transition occurred
     * Null for transitions outside graph execution (e.g., initial READY state)
     */
    val nodeId: String? = null
) {
    /**
     * Calculate duration since this transition
     * Useful for measuring execution time
     *
     * @param now Current timestamp (defaults to Clock.System.now())
     * @return Duration since transition in milliseconds
     */
    fun durationSince(now: Instant = kotlinx.datetime.Clock.System.now()): Long {
        return (now - timestamp).inWholeMilliseconds
    }

    /**
     * Check if transition is valid according to state machine rules
     *
     * @return true if transition follows allowed paths
     */
    fun isValid(): Boolean {
        return from.canTransitionTo(to)
    }

    /**
     * Human-readable representation of transition
     * Format: "RUNNING → WAITING at 2025-01-15T10:30:00Z (HITL required)"
     */
    override fun toString(): String {
        val reasonPart = reason?.let { " ($it)" } ?: ""
        val nodePart = nodeId?.let { " [node: $it]" } ?: ""
        return "$from → $to at $timestamp$reasonPart$nodePart"
    }
}

/**
 * 📊 Execution State Statistics
 *
 * Aggregate statistics from state transition history.
 * Useful for performance monitoring and debugging.
 *
 * **Usage:**
 * ```kotlin
 * val stats = ExecutionStateStats.fromHistory(message.stateHistory)
 * println("Total execution time: ${stats.totalDurationMs}ms")
 * println("Time spent waiting: ${stats.waitingDurationMs}ms")
 * println("Transition count: ${stats.transitionCount}")
 * ```
 *
 * @property transitionCount Total number of state transitions
 * @property totalDurationMs Total execution time in milliseconds
 * @property runningDurationMs Time spent in RUNNING state
 * @property waitingDurationMs Time spent in WAITING state
 * @property currentState Current execution state
 * @property failureReason Reason for failure (if state is FAILED)
 *
 * @author Spice Framework
 * @since 1.0.0
 */
data class ExecutionStateStats(
    val transitionCount: Int,
    val totalDurationMs: Long,
    val runningDurationMs: Long,
    val waitingDurationMs: Long,
    val currentState: ExecutionState,
    val failureReason: String? = null
) {
    companion object {
        /**
         * Calculate statistics from state transition history
         *
         * @param history List of state transitions
         * @param now Current timestamp for duration calculation
         * @return ExecutionStateStats
         */
        fun fromHistory(
            history: List<StateTransition>,
            now: Instant = kotlinx.datetime.Clock.System.now()
        ): ExecutionStateStats {
            if (history.isEmpty()) {
                return ExecutionStateStats(
                    transitionCount = 0,
                    totalDurationMs = 0,
                    runningDurationMs = 0,
                    waitingDurationMs = 0,
                    currentState = ExecutionState.READY
                )
            }

            val currentState = history.last().to
            val startTime = history.first().timestamp
            val totalDuration = (now - startTime).inWholeMilliseconds

            // Calculate time spent in each state
            var runningMs = 0L
            var waitingMs = 0L

            for (i in history.indices) {
                val transition = history[i]
                val nextTransition = history.getOrNull(i + 1)
                val endTime = nextTransition?.timestamp ?: now

                val duration = (endTime - transition.timestamp).inWholeMilliseconds

                when (transition.to) {
                    ExecutionState.RUNNING -> runningMs += duration
                    ExecutionState.WAITING -> waitingMs += duration
                    else -> {}  // Don't count terminal states
                }
            }

            // Extract failure reason if applicable
            val failureReason = if (currentState == ExecutionState.FAILED) {
                history.lastOrNull { it.to == ExecutionState.FAILED }?.reason
            } else {
                null
            }

            return ExecutionStateStats(
                transitionCount = history.size,
                totalDurationMs = totalDuration,
                runningDurationMs = runningMs,
                waitingDurationMs = waitingMs,
                currentState = currentState,
                failureReason = failureReason
            )
        }
    }

    /**
     * Calculate percentage of time spent in RUNNING state
     */
    fun runningPercentage(): Double {
        return if (totalDurationMs > 0) {
            (runningDurationMs.toDouble() / totalDurationMs) * 100
        } else {
            0.0
        }
    }

    /**
     * Calculate percentage of time spent in WAITING state
     */
    fun waitingPercentage(): Double {
        return if (totalDurationMs > 0) {
            (waitingDurationMs.toDouble() / totalDurationMs) * 100
        } else {
            0.0
        }
    }

    /**
     * Check if execution is healthy (low waiting percentage)
     * Threshold: > 80% running time
     */
    fun isHealthy(): Boolean {
        return runningPercentage() > 80.0
    }
}
