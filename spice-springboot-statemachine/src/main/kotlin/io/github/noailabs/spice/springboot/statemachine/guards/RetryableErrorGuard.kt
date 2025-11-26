package io.github.noailabs.spice.springboot.statemachine.guards

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.retry.RetryClassifier
import io.github.noailabs.spice.springboot.statemachine.core.SpiceEvent
import org.springframework.statemachine.StateContext
import org.springframework.statemachine.guard.Guard

/**
 * Filters transitions to RETRY only when the last error is retryable.
 *
 * Delegates to core RetryClassifier for consistent retry classification
 * across the entire Spice framework.
 *
 * @since 1.0.4 - Now delegates to core RetryClassifier
 */
class RetryableErrorGuard(
    private val classifier: RetryClassifier = RetryClassifier.DEFAULT
) : Guard<ExecutionState, SpiceEvent> {

    override fun evaluate(context: StateContext<ExecutionState, SpiceEvent>): Boolean {
        val error = context.extendedState.variables["lastError"] as? SpiceError
        return isRetryable(error)
    }

    /**
     * Check if an error is retryable.
     *
     * Delegates to core RetryClassifier for consistent classification.
     * Now supports:
     * - SpiceError.RetryableError (new in 1.0.4)
     * - SpiceError.ToolError
     * - SpiceError.NetworkError (5xx, 408, 429)
     * - SpiceError.TimeoutError
     * - SpiceError.RateLimitError
     *
     * @param error The error to check
     * @return true if the error should be retried
     */
    fun isRetryable(error: SpiceError?): Boolean {
        if (error == null) return false
        return classifier.classify(error).shouldRetry
    }
}
