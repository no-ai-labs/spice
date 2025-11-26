package io.github.noailabs.spice.retry

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Execution Retry Policy
 *
 * Configuration for automatic retry behavior in graph execution.
 * Defines retry attempts, delays, and backoff strategy.
 *
 * **Default Policy:**
 * - maxAttempts: 3 (total attempts including initial)
 * - initialDelay: 200ms
 * - maxDelay: 10s
 * - backoffMultiplier: 2.0
 * - jitterFactor: 0.1 (10% randomization)
 *
 * **Attempt Counting:**
 * - maxAttempts = total number of attempts (including initial)
 * - maxAttempts = 3 means: initial attempt + up to 2 retries = 3 total
 * - maxAttempts = 1 means: initial attempt only, no retries
 *
 * **Usage:**
 * ```kotlin
 * // Use defaults (3 total attempts)
 * val policy = ExecutionRetryPolicy.DEFAULT
 *
 * // Custom policy (5 total attempts)
 * val policy = ExecutionRetryPolicy(
 *     maxAttempts = 5,
 *     initialDelay = 500.milliseconds,
 *     maxDelay = 30.seconds,
 *     backoffMultiplier = 2.0
 * )
 *
 * // No retry (1 attempt only)
 * val policy = ExecutionRetryPolicy.NO_RETRY
 * ```
 *
 * @property maxAttempts Maximum TOTAL attempts including initial (not just retries)
 * @property initialDelay Initial delay before first retry
 * @property maxDelay Maximum delay between retries (caps exponential backoff)
 * @property backoffMultiplier Multiplier for exponential backoff
 * @property jitterFactor Randomization factor (0.0-1.0) to prevent thundering herd
 *
 * @since 1.0.4
 */
data class ExecutionRetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelay: Duration = 200.milliseconds,
    val maxDelay: Duration = 10.seconds,
    val backoffMultiplier: Double = 2.0,
    val jitterFactor: Double = 0.1
) {
    init {
        require(maxAttempts >= 0) { "maxAttempts must be non-negative" }
        require(initialDelay.isPositive()) { "initialDelay must be positive" }
        require(maxDelay.isPositive()) { "maxDelay must be positive" }
        require(maxDelay >= initialDelay) { "maxDelay must be >= initialDelay" }
        require(backoffMultiplier >= 1.0) { "backoffMultiplier must be >= 1.0" }
        require(jitterFactor in 0.0..1.0) { "jitterFactor must be between 0.0 and 1.0" }
    }

    /**
     * Calculate delay for given attempt number.
     *
     * @param attemptNumber Current attempt number (1-based)
     * @param retryAfterHint Optional override delay from error (e.g., Retry-After header)
     * @return Delay duration before next retry
     */
    fun calculateDelay(attemptNumber: Int, retryAfterHint: Duration? = null): Duration {
        // If we have a hint (e.g., Retry-After header), use it but cap at maxDelay
        retryAfterHint?.let { hint ->
            return minOf(hint, maxDelay)
        }

        // Calculate exponential backoff: initialDelay * (multiplier ^ (attempt - 1))
        val baseDelay = initialDelay.inWholeMilliseconds *
            Math.pow(backoffMultiplier, (attemptNumber - 1).toDouble())

        // Cap at maxDelay
        val cappedDelay = minOf(baseDelay, maxDelay.inWholeMilliseconds.toDouble())

        // Apply jitter
        val jitter = if (jitterFactor > 0) {
            val range = cappedDelay * jitterFactor
            (Math.random() * 2 * range) - range // -jitter to +jitter
        } else {
            0.0
        }

        val finalDelay = maxOf(0.0, cappedDelay + jitter)
        return finalDelay.toLong().milliseconds
    }

    /**
     * Check if retry is allowed for given attempt number.
     *
     * With maxAttempts as TOTAL attempts:
     * - maxAttempts = 3: attempts 1, 2, 3 allowed → shouldRetry(1)=true, shouldRetry(2)=true, shouldRetry(3)=true, shouldRetry(4)=false
     * - maxAttempts = 1: only attempt 1 allowed (no retries) → shouldRetry(1)=true, shouldRetry(2)=false
     *
     * @param currentAttempt Current attempt number (1-based, where 1 is initial attempt)
     * @return true if this attempt is allowed (currentAttempt <= maxAttempts)
     */
    fun shouldRetry(currentAttempt: Int): Boolean {
        return currentAttempt <= maxAttempts
    }

    /**
     * Check if MORE retries are available after current attempt.
     *
     * Use this to decide whether to retry after a failure:
     * - After attempt 1 fails with maxAttempts=3: hasMoreRetries(1)=true (attempts 2,3 remain)
     * - After attempt 3 fails with maxAttempts=3: hasMoreRetries(3)=false (exhausted)
     *
     * @param currentAttempt The attempt that just completed (1-based)
     * @return true if more attempts are available
     */
    fun hasMoreRetries(currentAttempt: Int): Boolean {
        return currentAttempt < maxAttempts
    }

    /**
     * Check if this policy allows any retries (more than 1 attempt).
     *
     * - maxAttempts = 1: no retries (execute once) → false
     * - maxAttempts = 3: allows 2 retries → true
     */
    fun isRetryEnabled(): Boolean = maxAttempts > 1

    companion object {
        /**
         * Default retry policy: 3 total attempts, 200ms→400ms→800ms
         */
        val DEFAULT = ExecutionRetryPolicy()

        /**
         * No retry policy: execute once, no retries on failure
         */
        val NO_RETRY = ExecutionRetryPolicy(maxAttempts = 1)

        /**
         * Aggressive retry policy: 5 attempts, shorter initial delay
         */
        val AGGRESSIVE = ExecutionRetryPolicy(
            maxAttempts = 5,
            initialDelay = 100.milliseconds,
            maxDelay = 5.seconds,
            backoffMultiplier = 1.5
        )

        /**
         * Conservative retry policy: 3 attempts, longer delays
         */
        val CONSERVATIVE = ExecutionRetryPolicy(
            maxAttempts = 3,
            initialDelay = 1.seconds,
            maxDelay = 30.seconds,
            backoffMultiplier = 3.0
        )

        /**
         * Rate limit friendly policy: respect Retry-After, longer max delay
         */
        val RATE_LIMIT_FRIENDLY = ExecutionRetryPolicy(
            maxAttempts = 5,
            initialDelay = 1.seconds,
            maxDelay = 60.seconds,
            backoffMultiplier = 2.0,
            jitterFactor = 0.2
        )
    }
}

/**
 * Retry delay sequence generator.
 *
 * Generates the sequence of delays for visualization/debugging.
 *
 * @param policy The retry policy to use
 * @return Sequence of delays for each retry attempt
 */
fun ExecutionRetryPolicy.delaySequence(): Sequence<Duration> = sequence {
    var attempt = 1
    while (attempt <= maxAttempts) {
        yield(calculateDelay(attempt))
        attempt++
    }
}

/**
 * Get human-readable description of the retry policy.
 */
fun ExecutionRetryPolicy.describe(): String = buildString {
    if (!isRetryEnabled()) {
        append("No retry (1 attempt)")
        return@buildString
    }

    val retryCount = maxAttempts - 1  // maxAttempts=3 means 2 retries
    append("$maxAttempts total attempts ($retryCount retries), ")
    append("delays: ${initialDelay.inWholeMilliseconds}ms")

    if (maxAttempts > 1) {
        val delays = delaySequence().take(maxAttempts).toList()
        append(" → ")
        append(delays.drop(1).joinToString(" → ") { "${it.inWholeMilliseconds}ms" })
    }

    append(" (max: ${maxDelay.inWholeSeconds}s)")

    if (jitterFactor > 0) {
        append(", jitter: ${(jitterFactor * 100).toInt()}%")
    }
}
