package io.github.noailabs.spice.retry

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Retry Supervisor
 *
 * Orchestrates automatic retry logic for node execution.
 * Handles retry classification, delay calculation, metrics, and error conversion.
 *
 * **Responsibilities:**
 * 1. Classify errors as retryable or not
 * 2. Apply retry policy (delay calculation, attempt counting)
 * 3. Collect metrics
 * 4. Convert to ExecutionError on exhaustion with metadata
 *
 * **Integration:**
 * - Used by GraphRunner to wrap node execution
 * - Middleware callbacks are NOT called during retries (only on final result)
 *
 * **Usage:**
 * ```kotlin
 * val supervisor = RetrySupervisor()
 *
 * // Execute with automatic retry
 * val result = supervisor.executeWithRetry(
 *     message = message,
 *     nodeId = "my-tool",
 *     policy = ExecutionRetryPolicy.DEFAULT
 * ) { msg ->
 *     // Your node execution logic
 *     myNode.run(msg)
 * }
 *
 * when (result) {
 *     is RetryResult.Success -> // Handle success
 *     is RetryResult.Exhausted -> // All retries failed
 *     is RetryResult.NotRetryable -> // Error was not retryable
 * }
 * ```
 *
 * @property classifier Retry classifier to determine if errors are retryable
 * @property policyResolver Resolver for policy overrides
 * @property metricsCollector Metrics collector for retry events
 *
 * @since 1.0.4
 */
class RetrySupervisor(
    private val classifier: RetryClassifier = RetryClassifier.DEFAULT,
    private val policyResolver: RetryPolicyResolver = RetryPolicyResolver.default(),
    private val metricsCollector: RetryMetricsCollector = RetryMetricsCollector.LOGGING
) {

    /**
     * Execute operation with automatic retry.
     *
     * @param message Current message (for context extraction)
     * @param nodeId Node ID for metrics and logging
     * @param policy Retry policy to apply
     * @param operation The operation to execute (returns SpiceResult)
     * @return RetryResult indicating success, exhaustion, or non-retryable error
     */
    suspend fun <T> executeWithRetry(
        message: SpiceMessage,
        nodeId: String,
        policy: ExecutionRetryPolicy = ExecutionRetryPolicy.DEFAULT,
        operation: suspend (SpiceMessage, attemptNumber: Int) -> SpiceResult<T>
    ): RetryResult<T> {
        val tenantId = message.getMetadata<String>("tenantId")
        var context = RetryContext.initial(nodeId, tenantId)

        while (true) {
            // Execute operation
            val result = operation(message, context.attemptNumber)

            when (result) {
                is SpiceResult.Success -> {
                    // Success - record metrics if this was a retry
                    if (!context.isFirstAttempt()) {
                        metricsCollector.recordRetrySuccess(context)
                    }
                    return RetryResult.Success(result.value, context)
                }

                is SpiceResult.Failure -> {
                    val error = result.error

                    // Use passed policy, with optional override from resolver
                    // The resolver can provide more specific policies for certain errors/tenants/endpoints
                    val resolvedPolicy = policyResolver.resolveOrDefault(message, error, nodeId, policy)

                    // Classify error
                    val classification = classifier.classify(error)

                    if (!classification.shouldRetry) {
                        // Non-retryable error
                        metricsCollector.recordNonRetryable(context, error)
                        return RetryResult.NotRetryable(context, error)
                    }

                    // Check if we have MORE retries available after this failed attempt
                    // hasMoreRetries(3) with maxAttempts=3 returns false (exhausted)
                    if (!resolvedPolicy.hasMoreRetries(context.attemptNumber)) {
                        // Exhausted - convert to ExecutionError with metadata
                        val finalError = convertToExecutionError(error, context, nodeId)
                        metricsCollector.recordRetryExhausted(context, finalError)
                        return RetryResult.Exhausted(context, finalError)
                    }

                    // Calculate delay
                    val suggestedDelay = classification.suggestedDelayMs?.milliseconds
                        ?: error.getRetryAfterHint()?.milliseconds
                    val delay = resolvedPolicy.calculateDelay(context.attemptNumber, suggestedDelay)

                    // Record metrics before waiting
                    metricsCollector.recordRetryAttempt(context, delay.inWholeMilliseconds)

                    // Update context
                    context = context.recordAttempt(error, delay)

                    // Wait before retry
                    delay(delay)
                }
            }
        }
    }

    /**
     * Execute with default policy resolved from message/error.
     */
    suspend fun <T> executeWithRetry(
        message: SpiceMessage,
        nodeId: String,
        operation: suspend (SpiceMessage, attemptNumber: Int) -> SpiceResult<T>
    ): RetryResult<T> {
        return executeWithRetry(message, nodeId, ExecutionRetryPolicy.DEFAULT, operation)
    }

    /**
     * Convert error to ExecutionError with retry metadata.
     *
     * Called when all retry attempts are exhausted.
     * Preserves original error information while adding retry context.
     *
     * **Metadata includes:**
     * - `lastError`: The last error message (for debugging)
     * - `lastErrorCode`: The last error code
     * - `lastStatusCode`: HTTP status code if available
     * - `originalError`: The original error that triggered retries
     * - `totalAttempts`: Total number of attempts made
     */
    private fun convertToExecutionError(
        originalError: SpiceError,
        context: RetryContext,
        nodeId: String
    ): SpiceError.ExecutionError {
        // Use the last error's cause if available, otherwise use original error as cause
        val lastError = context.lastError ?: originalError
        val effectiveCause = lastError.cause ?: lastError.toException()

        return SpiceError.ExecutionError(
            message = buildString {
                append("Retry exhausted after ${context.attemptNumber} attempts: ")
                append(originalError.message)
            },
            graphId = null,
            nodeId = nodeId,
            cause = effectiveCause,
            context = buildMap {
                put("retriesExhausted", true)
                put("totalAttempts", context.attemptNumber)
                put("totalRetryDelayMs", context.totalRetryDelay.inWholeMilliseconds)
                put("elapsedMs", context.elapsedTime().inWholeMilliseconds)

                // Last error details (for debugging)
                put("lastError", lastError.message)
                context.lastStatusCode?.let { put("lastStatusCode", it) }
                context.lastErrorCode?.let { put("lastErrorCode", it) }

                // Original error details
                put("originalErrorCode", originalError.code)
                put("originalError", originalError.message)

                // Error history
                put("errorHistory", context.errors.map { mapOf("code" to it.code, "message" to it.message) })
            }
        )
    }

    /**
     * Get retry-after hint from SpiceError.
     */
    private fun SpiceError.getRetryAfterHint(): Long? = when (this) {
        is SpiceError.RetryableError -> retryHint?.retryAfterMs
        is SpiceError.RateLimitError -> retryAfterMs
        else -> null
    }

    companion object {
        /**
         * Create supervisor with default configuration.
         */
        fun default(): RetrySupervisor = RetrySupervisor()

        /**
         * Create supervisor with custom policy resolver.
         */
        fun withPolicyResolver(resolver: RetryPolicyResolver): RetrySupervisor =
            RetrySupervisor(policyResolver = resolver)

        /**
         * Create supervisor with custom metrics collector.
         */
        fun withMetrics(collector: RetryMetricsCollector): RetrySupervisor =
            RetrySupervisor(metricsCollector = collector)

        /**
         * Create supervisor with no retry (passthrough).
         */
        fun noRetry(): RetrySupervisor = RetrySupervisor(
            classifier = RetryClassifier.NEVER_RETRY,
            policyResolver = RetryPolicyResolver.noRetry()
        )
    }
}

/**
 * Extension for SpiceResult to retry with supervisor.
 */
suspend fun <T> SpiceResult.Companion.withRetry(
    supervisor: RetrySupervisor,
    message: SpiceMessage,
    nodeId: String,
    policy: ExecutionRetryPolicy = ExecutionRetryPolicy.DEFAULT,
    operation: suspend (SpiceMessage, attemptNumber: Int) -> SpiceResult<T>
): SpiceResult<T> {
    return when (val result = supervisor.executeWithRetry(message, nodeId, policy, operation)) {
        is RetryResult.Success -> SpiceResult.success(result.value)
        is RetryResult.Exhausted -> SpiceResult.failure(result.finalError)
        is RetryResult.NotRetryable -> SpiceResult.failure(result.error)
    }
}

/**
 * Configuration for RetrySupervisor.
 */
data class RetrySupervisorConfig(
    val defaultPolicy: ExecutionRetryPolicy = ExecutionRetryPolicy.DEFAULT,
    val classifier: RetryClassifier = RetryClassifier.DEFAULT,
    val policyResolver: RetryPolicyResolver = RetryPolicyResolver.default(),
    val metricsCollector: RetryMetricsCollector = RetryMetricsCollector.LOGGING,
    val enabled: Boolean = true
) {
    fun toSupervisor(): RetrySupervisor? {
        if (!enabled) return null
        return RetrySupervisor(
            classifier = classifier,
            policyResolver = policyResolver,
            metricsCollector = metricsCollector
        )
    }

    companion object {
        val DEFAULT = RetrySupervisorConfig()
        val DISABLED = RetrySupervisorConfig(enabled = false)
    }
}
