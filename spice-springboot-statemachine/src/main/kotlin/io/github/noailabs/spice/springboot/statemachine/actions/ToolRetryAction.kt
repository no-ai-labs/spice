package io.github.noailabs.spice.springboot.statemachine.actions

import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.springboot.statemachine.config.StateMachineProperties
import kotlin.math.pow

/**
 * Provides exponential/fixed/linear backoff calculations for retryable tool failures.
 */
class ToolRetryAction(
    private val retryProperties: StateMachineProperties.Retry
) {
    fun nextBackoff(attempt: Int): Long? {
        if (!retryProperties.enabled) return null
        if (attempt >= retryProperties.maxAttempts) return null
        val nextAttempt = attempt + 1
        val delay = when (retryProperties.backoffStrategy) {
            StateMachineProperties.Retry.BackoffStrategy.EXPONENTIAL ->
                (2.0.pow(nextAttempt - 1) * retryProperties.initialBackoffMs).toLong()

            StateMachineProperties.Retry.BackoffStrategy.FIXED -> retryProperties.initialBackoffMs
            StateMachineProperties.Retry.BackoffStrategy.LINEAR -> retryProperties.initialBackoffMs * nextAttempt
        }.coerceAtMost(retryProperties.maxBackoffMs)
        return delay
    }

    fun isRetryable(error: SpiceError?): Boolean {
        return when (error) {
            is SpiceError.ToolError,
            is SpiceError.NetworkError -> true
            else -> false
        }
    }
}
