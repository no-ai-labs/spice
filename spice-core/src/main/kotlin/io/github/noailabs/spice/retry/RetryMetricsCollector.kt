package io.github.noailabs.spice.retry

import io.github.noailabs.spice.error.SpiceError

/**
 * Retry Metrics Collector
 *
 * Collects metrics for retry operations.
 * Designed to integrate with Micrometer, Prometheus, or custom metrics systems.
 *
 * **Spec-Compliant Metrics:**
 * - `retry_attempts_total` - Total retry attempts (counter)
 * - `retry_exhausted_total` - Total exhausted retries (counter)
 * - `retry_success_total` - Total successful retries (counter)
 * - `retry_delay_seconds` - Retry delay histogram
 * - `retry_non_retryable_total` - Total non-retryable errors (counter)
 *
 * **Spec-Compliant Labels:**
 * - `toolName` - Tool/node identifier where retry occurred
 * - `tenantId` - Tenant ID (if available)
 * - `statusCode` - HTTP status code (if available)
 * - `errorCode` - Error code
 *
 * **Usage:**
 * ```kotlin
 * // Use default (logging) collector
 * val collector = RetryMetricsCollector.LOGGING
 *
 * // Implement Micrometer collector
 * class MicrometerRetryMetrics(private val registry: MeterRegistry) : RetryMetricsCollector {
 *     override fun recordRetryAttempt(context: RetryContext, delayMs: Long) {
 *         Counter.builder("retry_attempts_total")
 *             .tags(context.toMetricLabels().toTags())  // toolName, tenantId, statusCode, errorCode
 *             .register(registry)
 *             .increment()
 *
 *         DistributionSummary.builder("retry_delay_seconds")
 *             .tags(context.toMetricLabels().toTags())
 *             .register(registry)
 *             .record(delayMs / 1000.0)
 *     }
 * }
 * ```
 *
 * @since 1.0.4
 */
interface RetryMetricsCollector {

    /**
     * Record a retry attempt.
     *
     * Called before each retry delay.
     *
     * @param context Current retry context
     * @param delayMs Delay in milliseconds before next attempt
     */
    fun recordRetryAttempt(context: RetryContext, delayMs: Long)

    /**
     * Record retry exhaustion (all attempts failed).
     *
     * @param context Final retry context
     * @param finalError The last error
     */
    fun recordRetryExhausted(context: RetryContext, finalError: SpiceError)

    /**
     * Record successful retry (after at least one retry).
     *
     * @param context Final retry context
     */
    fun recordRetrySuccess(context: RetryContext)

    /**
     * Record non-retryable error (no retry attempted).
     *
     * @param context Retry context
     * @param error The non-retryable error
     */
    fun recordNonRetryable(context: RetryContext, error: SpiceError) {
        // Default: no-op (override if needed)
    }

    companion object {
        /**
         * No-op collector (no metrics)
         */
        val NOOP: RetryMetricsCollector = object : RetryMetricsCollector {
            override fun recordRetryAttempt(context: RetryContext, delayMs: Long) {}
            override fun recordRetryExhausted(context: RetryContext, finalError: SpiceError) {}
            override fun recordRetrySuccess(context: RetryContext) {}
        }

        /**
         * Logging collector (logs metrics)
         */
        val LOGGING: RetryMetricsCollector = LoggingRetryMetricsCollector()

        /**
         * In-memory collector for testing
         */
        fun inMemory(): InMemoryRetryMetricsCollector = InMemoryRetryMetricsCollector()
    }
}

/**
 * Logging-based metrics collector.
 *
 * Logs retry events with structured context for log aggregation.
 * Uses spec-compliant label names (toolName, tenantId, statusCode, errorCode).
 */
internal class LoggingRetryMetricsCollector : RetryMetricsCollector {

    override fun recordRetryAttempt(context: RetryContext, delayMs: Long) {
        println(buildString {
            append("[RETRY] ")
            append("toolName=${context.nodeId} ")  // Spec label: toolName
            append("attempt=${context.attemptNumber} ")
            append("delayMs=$delayMs ")
            context.tenantId?.let { append("tenantId=$it ") }
            context.lastStatusCode?.let { append("statusCode=$it ") }
            context.lastErrorCode?.let { append("errorCode=$it ") }
            append("totalDelayMs=${context.totalRetryDelay.inWholeMilliseconds}")
        })
    }

    override fun recordRetryExhausted(context: RetryContext, finalError: SpiceError) {
        println(buildString {
            append("[RETRY_EXHAUSTED] ")  // Changed from HANDOFF to spec-compliant name
            append("toolName=${context.nodeId} ")  // Spec label: toolName
            append("attempts=${context.attemptNumber} ")
            append("errorCode=${finalError.code} ")
            context.tenantId?.let { append("tenantId=$it ") }
            context.lastStatusCode?.let { append("statusCode=$it ") }
            append("totalDelayMs=${context.totalRetryDelay.inWholeMilliseconds} ")
            append("elapsedMs=${context.elapsedTime().inWholeMilliseconds}")
        })
    }

    override fun recordRetrySuccess(context: RetryContext) {
        println(buildString {
            append("[RETRY_SUCCESS] ")
            append("toolName=${context.nodeId} ")  // Spec label: toolName
            append("attempts=${context.attemptNumber} ")
            context.tenantId?.let { append("tenantId=$it ") }
            append("totalDelayMs=${context.totalRetryDelay.inWholeMilliseconds} ")
            append("elapsedMs=${context.elapsedTime().inWholeMilliseconds}")
        })
    }

    override fun recordNonRetryable(context: RetryContext, error: SpiceError) {
        println(buildString {
            append("[RETRY_NON_RETRYABLE] ")  // Consistent naming
            append("toolName=${context.nodeId} ")  // Spec label: toolName
            append("errorCode=${error.code} ")
            context.tenantId?.let { append("tenantId=$it ") }
        })
    }
}

/**
 * In-memory metrics collector for testing.
 *
 * Stores all events for assertion in tests.
 */
class InMemoryRetryMetricsCollector : RetryMetricsCollector {

    private val _retryAttempts = mutableListOf<RetryAttemptEvent>()
    private val _exhaustedEvents = mutableListOf<RetryExhaustedEvent>()
    private val _successEvents = mutableListOf<RetrySuccessEvent>()
    private val _nonRetryableEvents = mutableListOf<NonRetryableEvent>()

    val retryAttempts: List<RetryAttemptEvent> get() = _retryAttempts.toList()
    val exhaustedEvents: List<RetryExhaustedEvent> get() = _exhaustedEvents.toList()
    val successEvents: List<RetrySuccessEvent> get() = _successEvents.toList()
    val nonRetryableEvents: List<NonRetryableEvent> get() = _nonRetryableEvents.toList()

    override fun recordRetryAttempt(context: RetryContext, delayMs: Long) {
        _retryAttempts.add(RetryAttemptEvent(context.copy(), delayMs))
    }

    override fun recordRetryExhausted(context: RetryContext, finalError: SpiceError) {
        _exhaustedEvents.add(RetryExhaustedEvent(context.copy(), finalError))
    }

    override fun recordRetrySuccess(context: RetryContext) {
        _successEvents.add(RetrySuccessEvent(context.copy()))
    }

    override fun recordNonRetryable(context: RetryContext, error: SpiceError) {
        _nonRetryableEvents.add(NonRetryableEvent(context.copy(), error))
    }

    /**
     * Clear all recorded events
     */
    fun clear() {
        _retryAttempts.clear()
        _exhaustedEvents.clear()
        _successEvents.clear()
        _nonRetryableEvents.clear()
    }

    /**
     * Get total retry attempt count
     */
    fun totalRetryAttempts(): Int = _retryAttempts.size

    /**
     * Get total exhausted count
     */
    fun totalExhausted(): Int = _exhaustedEvents.size

    /**
     * Get total success count
     */
    fun totalSuccess(): Int = _successEvents.size

    /**
     * Get retry attempts for specific node
     */
    fun retryAttemptsForNode(nodeId: String): List<RetryAttemptEvent> =
        _retryAttempts.filter { it.context.nodeId == nodeId }

    /**
     * Get retry attempts for specific tenant
     */
    fun retryAttemptsForTenant(tenantId: String): List<RetryAttemptEvent> =
        _retryAttempts.filter { it.context.tenantId == tenantId }

    data class RetryAttemptEvent(val context: RetryContext, val delayMs: Long)
    data class RetryExhaustedEvent(val context: RetryContext, val finalError: SpiceError)
    data class RetrySuccessEvent(val context: RetryContext)
    data class NonRetryableEvent(val context: RetryContext, val error: SpiceError)
}

/**
 * Composite metrics collector that delegates to multiple collectors.
 */
class CompositeRetryMetricsCollector(
    private val collectors: List<RetryMetricsCollector>
) : RetryMetricsCollector {

    constructor(vararg collectors: RetryMetricsCollector) : this(collectors.toList())

    override fun recordRetryAttempt(context: RetryContext, delayMs: Long) {
        collectors.forEach { it.recordRetryAttempt(context, delayMs) }
    }

    override fun recordRetryExhausted(context: RetryContext, finalError: SpiceError) {
        collectors.forEach { it.recordRetryExhausted(context, finalError) }
    }

    override fun recordRetrySuccess(context: RetryContext) {
        collectors.forEach { it.recordRetrySuccess(context) }
    }

    override fun recordNonRetryable(context: RetryContext, error: SpiceError) {
        collectors.forEach { it.recordNonRetryable(context, error) }
    }
}

/**
 * Extension to combine multiple collectors
 */
operator fun RetryMetricsCollector.plus(other: RetryMetricsCollector): RetryMetricsCollector {
    return when {
        this is CompositeRetryMetricsCollector -> CompositeRetryMetricsCollector(this.collectors + other)
        other is CompositeRetryMetricsCollector -> CompositeRetryMetricsCollector(listOf(this) + other.collectors)
        else -> CompositeRetryMetricsCollector(this, other)
    }
}

// Extension property to access collectors in CompositeRetryMetricsCollector
private val CompositeRetryMetricsCollector.collectors: List<RetryMetricsCollector>
    get() = this::class.java.getDeclaredField("collectors").let {
        it.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        it.get(this) as List<RetryMetricsCollector>
    }
