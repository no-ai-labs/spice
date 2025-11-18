package io.github.noailabs.spice.springboot.statemachine.guards

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.springboot.statemachine.core.SpiceEvent
import org.springframework.statemachine.StateContext
import org.springframework.statemachine.guard.Guard

/**
 * Filters transitions to RETRY only when the last error is retryable.
 */
class RetryableErrorGuard : Guard<ExecutionState, SpiceEvent> {
    override fun evaluate(context: StateContext<ExecutionState, SpiceEvent>): Boolean {
        val error = context.extendedState.variables["lastError"] as? SpiceError
        return isRetryable(error)
    }

    fun isRetryable(error: SpiceError?): Boolean {
        return when (error) {
            is SpiceError.ToolError,
            is SpiceError.NetworkError -> true
            else -> false
        }
    }
}
