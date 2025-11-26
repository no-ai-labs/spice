package io.github.noailabs.spice.springboot.statemachine.actions

import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.retry.ExecutionRetryPolicy
import io.github.noailabs.spice.retry.RetryClassifier
import io.github.noailabs.spice.springboot.statemachine.config.StateMachineProperties
import kotlin.time.Duration.Companion.milliseconds

/**
 * Provides exponential/fixed/linear backoff calculations for retryable tool failures.
 *
 * Delegates to core retry package for classification and policy calculation.
 * Maintains backward compatibility with StateMachineProperties.Retry configuration.
 *
 * @since 1.0.4 - Now delegates to core retry package
 */
class ToolRetryAction(
    private val retryProperties: StateMachineProperties.Retry,
    private val classifier: RetryClassifier = RetryClassifier.DEFAULT
) {
    /**
     * Core retry policy derived from properties
     */
    private val corePolicy: ExecutionRetryPolicy by lazy {
        ExecutionRetryPolicy(
            maxAttempts = retryProperties.maxAttempts,
            initialDelay = retryProperties.initialBackoffMs.milliseconds,
            maxDelay = retryProperties.maxBackoffMs.milliseconds,
            backoffMultiplier = when (retryProperties.backoffStrategy) {
                StateMachineProperties.Retry.BackoffStrategy.EXPONENTIAL -> 2.0
                StateMachineProperties.Retry.BackoffStrategy.FIXED -> 1.0
                StateMachineProperties.Retry.BackoffStrategy.LINEAR -> 1.0
            },
            jitterFactor = 0.0 // Legacy behavior: no jitter
        )
    }

    /**
     * Calculate next backoff delay.
     *
     * @param attempt Current attempt number (0-based for backward compatibility)
     * @return Delay in milliseconds, or null if retry should stop
     */
    fun nextBackoff(attempt: Int): Long? {
        if (!retryProperties.enabled) return null
        if (attempt >= retryProperties.maxAttempts) return null

        val nextAttempt = attempt + 1

        // Use legacy calculation for FIXED and LINEAR strategies
        val delay = when (retryProperties.backoffStrategy) {
            StateMachineProperties.Retry.BackoffStrategy.EXPONENTIAL ->
                corePolicy.calculateDelay(nextAttempt).inWholeMilliseconds

            StateMachineProperties.Retry.BackoffStrategy.FIXED ->
                retryProperties.initialBackoffMs

            StateMachineProperties.Retry.BackoffStrategy.LINEAR ->
                retryProperties.initialBackoffMs * nextAttempt
        }.coerceAtMost(retryProperties.maxBackoffMs)

        return delay
    }

    /**
     * Check if an error is retryable.
     *
     * Delegates to core RetryClassifier for consistent classification.
     *
     * @param error The error to check
     * @return true if the error should be retried
     */
    fun isRetryable(error: SpiceError?): Boolean {
        if (error == null) return false
        return classifier.classify(error).shouldRetry
    }

    /**
     * Get the core retry policy for integration with new retry system.
     */
    fun toExecutionRetryPolicy(): ExecutionRetryPolicy = corePolicy
}
