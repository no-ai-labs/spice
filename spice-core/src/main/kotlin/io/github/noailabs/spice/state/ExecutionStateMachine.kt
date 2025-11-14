package io.github.noailabs.spice.state

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.StateTransition
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Helper for enforcing state transition rules at the framework boundary.
 */
class ExecutionStateMachine(
    private val clock: Clock = Clock.System
) {
    fun transition(
        message: SpiceMessage,
        target: ExecutionState,
        reason: String? = null,
        nodeId: String? = null,
        timestamp: Instant = clock.now()
    ): SpiceMessage {
        require(message.state.canTransitionTo(target)) {
            "Invalid state transition ${message.state} → $target"
        }

        val transition = StateTransition(
            from = message.state,
            to = target,
            timestamp = timestamp,
            reason = reason,
            nodeId = nodeId
        )

        return message.copy(
            state = target,
            stateHistory = message.stateHistory + transition
        )
    }

    fun ensureHistoryValid(message: SpiceMessage) {
        val invalid = message.stateHistory.firstOrNull { !it.isValid() }
        if (invalid != null) {
            throw IllegalStateException("Invalid transition detected: $invalid")
        }
    }

    fun assertTerminal(message: SpiceMessage) {
        require(message.state.isTerminal()) {
            "Message ${message.id} is not in a terminal state (${message.state})"
        }
    }
}
