package io.github.noailabs.spice.retry

import io.github.noailabs.spice.error.SpiceError
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Retry Context
 *
 * Tracks the state of retry attempts for a single operation.
 * Used by RetrySupervisor to manage retry logic.
 *
 * **Properties:**
 * - Immutable state (creates new instance on each update)
 * - Tracks attempt count, last error, cumulative delay
 * - Provides helper methods for logging/metrics
 *
 * **Usage:**
 * ```kotlin
 * var context = RetryContext.initial("my-tool", "tenant-123")
 *
 * // On first error
 * context = context.recordAttempt(error, delay = 200.milliseconds)
 *
 * // On second error
 * context = context.recordAttempt(error, delay = 400.milliseconds)
 *
 * // Check state
 * println(context.attemptNumber)     // 3 (initial + 2 retries)
 * println(context.totalRetryDelay)   // 600ms
 * println(context.isFirstAttempt())  // false
 * ```
 *
 * @property nodeId Node ID where retry is occurring
 * @property tenantId Tenant ID (from message metadata)
 * @property attemptNumber Current attempt number (1-based)
 * @property lastError Most recent error
 * @property lastStatusCode HTTP status code from last error (if available)
 * @property lastErrorCode Application error code from last error
 * @property totalRetryDelay Cumulative delay spent waiting for retries
 * @property errors History of all errors encountered
 * @property startTimeMs Timestamp when first attempt started
 *
 * @since 1.0.4
 */
data class RetryContext(
    val nodeId: String,
    val tenantId: String?,
    val attemptNumber: Int,
    val lastError: SpiceError?,
    val lastStatusCode: Int?,
    val lastErrorCode: String?,
    val totalRetryDelay: Duration,
    val errors: List<SpiceError>,
    val startTimeMs: Long
) {
    /**
     * Check if this is the first attempt (no retries yet)
     */
    fun isFirstAttempt(): Boolean = attemptNumber == 1

    /**
     * Get total elapsed time since first attempt
     */
    fun elapsedTime(): Duration =
        (System.currentTimeMillis() - startTimeMs).milliseconds

    /**
     * Get retry count (attempts - 1)
     */
    fun retryCount(): Int = maxOf(0, attemptNumber - 1)

    /**
     * Record a failed attempt and prepare for retry.
     *
     * @param error The error that occurred
     * @param retryDelay Delay before the next retry
     * @return New context with updated state
     */
    fun recordAttempt(error: SpiceError, retryDelay: Duration): RetryContext {
        val statusCode = when (error) {
            is SpiceError.RetryableError -> error.statusCode
            is SpiceError.NetworkError -> error.statusCode
            is SpiceError.RateLimitError -> 429
            else -> error.context["statusCode"] as? Int
        }

        val errorCode = when (error) {
            is SpiceError.RetryableError -> error.errorCode
            else -> error.code
        }

        return copy(
            attemptNumber = attemptNumber + 1,
            lastError = error,
            lastStatusCode = statusCode,
            lastErrorCode = errorCode,
            totalRetryDelay = totalRetryDelay + retryDelay,
            errors = errors + error
        )
    }

    /**
     * Create log context map for structured logging.
     */
    fun toLogContext(): Map<String, Any?> = mapOf(
        "nodeId" to nodeId,
        "tenantId" to tenantId,
        "attemptNumber" to attemptNumber,
        "retryCount" to retryCount(),
        "lastStatusCode" to lastStatusCode,
        "lastErrorCode" to lastErrorCode,
        "totalRetryDelayMs" to totalRetryDelay.inWholeMilliseconds,
        "elapsedMs" to elapsedTime().inWholeMilliseconds
    ).filterValues { it != null }

    /**
     * Create metric labels map for metrics collection.
     *
     * **Spec-compliant labels:**
     * - `toolName`: Node/tool identifier (maps from nodeId)
     * - `tenantId`: Tenant identifier (if available)
     * - `statusCode`: HTTP status code (if available)
     * - `errorCode`: Application error code (if available)
     */
    fun toMetricLabels(): Map<String, String> = buildMap {
        put("toolName", nodeId)  // Spec uses toolName for node identifier
        tenantId?.let { put("tenantId", it) }
        lastStatusCode?.let { put("statusCode", it.toString()) }
        lastErrorCode?.let { put("errorCode", it) }
    }

    companion object {
        /**
         * Create initial context for first attempt.
         */
        fun initial(nodeId: String, tenantId: String? = null): RetryContext = RetryContext(
            nodeId = nodeId,
            tenantId = tenantId,
            attemptNumber = 1,
            lastError = null,
            lastStatusCode = null,
            lastErrorCode = null,
            totalRetryDelay = Duration.ZERO,
            errors = emptyList(),
            startTimeMs = System.currentTimeMillis()
        )
    }
}

/**
 * Result of retry supervision.
 */
sealed class RetryResult<out T> {
    /**
     * Operation succeeded (possibly after retries)
     */
    data class Success<T>(
        val value: T,
        val context: RetryContext
    ) : RetryResult<T>()

    /**
     * All retry attempts exhausted
     */
    data class Exhausted(
        val context: RetryContext,
        val finalError: SpiceError
    ) : RetryResult<Nothing>()

    /**
     * Error is not retryable
     */
    data class NotRetryable(
        val context: RetryContext,
        val error: SpiceError
    ) : RetryResult<Nothing>()

    /**
     * Check if result is successful
     */
    fun isSuccess(): Boolean = this is Success

    /**
     * Get value if successful, null otherwise
     */
    fun getOrNull(): T? = when (this) {
        is Success -> value
        else -> null
    }

    /**
     * Get the final error (for Exhausted or NotRetryable)
     */
    fun errorOrNull(): SpiceError? = when (this) {
        is Exhausted -> finalError
        is NotRetryable -> error
        else -> null
    }

    /**
     * Get the retry context
     */
    fun context(): RetryContext = when (this) {
        is Success -> context
        is Exhausted -> context
        is NotRetryable -> context
    }
}
