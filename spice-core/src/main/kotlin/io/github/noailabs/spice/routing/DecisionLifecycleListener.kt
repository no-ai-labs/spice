package io.github.noailabs.spice.routing

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.routing.constants.RoutingMetrics
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Lifecycle listener for decision engine invocations.
 *
 * Implement this interface to observe decision engine lifecycle events
 * for metrics, logging, tracing, etc.
 *
 * ## Example Implementation
 * ```kotlin
 * class MetricsDecisionListener(private val meterRegistry: MeterRegistry) : DecisionLifecycleListener {
 *     override suspend fun onDecisionStart(context: DecisionContext) {
 *         meterRegistry.counter(RoutingMetrics.ROUTING_ATTEMPTS_TOTAL,
 *             RoutingMetrics.LABEL_ENGINE_ID, context.engineId
 *         ).increment()
 *     }
 *
 *     override suspend fun onDecisionComplete(context: DecisionContext, result: DecisionResult, durationMs: Long) {
 *         meterRegistry.timer(RoutingMetrics.ROUTING_DURATION_SECONDS,
 *             RoutingMetrics.LABEL_ENGINE_ID, context.engineId,
 *             RoutingMetrics.LABEL_RESULT, result.resultId
 *         ).record(durationMs, TimeUnit.MILLISECONDS)
 *     }
 * }
 * ```
 *
 * @since 1.0.7
 */
interface DecisionLifecycleListener {

    /**
     * Called when decision evaluation starts.
     */
    suspend fun onDecisionStart(context: DecisionContext)

    /**
     * Called when decision evaluation completes successfully.
     *
     * @param context Decision context
     * @param result The decision result
     * @param durationMs Time taken in milliseconds
     */
    suspend fun onDecisionComplete(context: DecisionContext, result: DecisionResult, durationMs: Long)

    /**
     * Called when fallback is used (no explicit mapping matched).
     *
     * @param context Decision context
     * @param originalResultId The original result ID that didn't match
     * @param fallbackTarget The fallback target used
     * @param durationMs Time taken in milliseconds
     */
    suspend fun onDecisionFallback(
        context: DecisionContext,
        originalResultId: String,
        fallbackTarget: String,
        durationMs: Long
    )

    /**
     * Called when decision evaluation fails with an error.
     *
     * @param context Decision context
     * @param error The error that occurred
     * @param durationMs Time taken in milliseconds
     */
    suspend fun onDecisionError(context: DecisionContext, error: SpiceError, durationMs: Long)

    companion object {
        /**
         * No-op listener that does nothing.
         */
        val NOOP: DecisionLifecycleListener = object : DecisionLifecycleListener {
            override suspend fun onDecisionStart(context: DecisionContext) {}
            override suspend fun onDecisionComplete(context: DecisionContext, result: DecisionResult, durationMs: Long) {}
            override suspend fun onDecisionFallback(context: DecisionContext, originalResultId: String, fallbackTarget: String, durationMs: Long) {}
            override suspend fun onDecisionError(context: DecisionContext, error: SpiceError, durationMs: Long) {}
        }

        /**
         * Logging listener using SLF4J.
         */
        val LOGGING: DecisionLifecycleListener = LoggingDecisionListener()

        /**
         * Combine multiple listeners.
         */
        fun composite(vararg listeners: DecisionLifecycleListener): DecisionLifecycleListener =
            CompositeDecisionListener(listeners.toList())
    }
}

/**
 * Context information for decision lifecycle events.
 *
 * @property engine The decision engine being invoked
 * @property message The message being evaluated
 * @property graphId Current graph ID (if in graph context)
 * @property nodeId Current node ID (if in node context)
 * @property runId Current run ID (for checkpoint/resume)
 * @property startTime When the decision started
 * @property attemptNumber Attempt number (for retries)
 */
data class DecisionContext(
    val engine: DecisionEngine,
    val message: SpiceMessage,
    val graphId: String?,
    val nodeId: String?,
    val runId: String?,
    val startTime: Instant = Instant.now(),
    val attemptNumber: Int = 1
) {
    /** Engine ID for convenience */
    val engineId: String get() = engine.id

    /** User ID from message metadata */
    val userId: String? get() = message.getMetadata<String>("userId")

    /** Tenant ID from message metadata */
    val tenantId: String? get() = message.getMetadata<String>("tenantId")

    /** Trace ID from message metadata */
    val traceId: String? get() = message.getMetadata<String>("traceId")

    companion object {
        /**
         * Create context from message and engine.
         */
        fun from(engine: DecisionEngine, message: SpiceMessage): DecisionContext {
            return DecisionContext(
                engine = engine,
                message = message,
                graphId = message.graphId,
                nodeId = message.nodeId,
                runId = message.runId
            )
        }
    }
}

/**
 * Logging implementation of DecisionLifecycleListener.
 */
internal class LoggingDecisionListener : DecisionLifecycleListener {
    private val logger = LoggerFactory.getLogger(DecisionLifecycleListener::class.java)

    override suspend fun onDecisionStart(context: DecisionContext) {
        logger.debug(
            "[Decision] START engine={} graph={} node={} run={}",
            context.engineId,
            context.graphId,
            context.nodeId,
            context.runId
        )
    }

    override suspend fun onDecisionComplete(context: DecisionContext, result: DecisionResult, durationMs: Long) {
        logger.info(
            "[Decision] COMPLETE engine={} result={} duration={}ms graph={} node={}",
            context.engineId,
            result.resultId,
            durationMs,
            context.graphId,
            context.nodeId
        )
    }

    override suspend fun onDecisionFallback(
        context: DecisionContext,
        originalResultId: String,
        fallbackTarget: String,
        durationMs: Long
    ) {
        logger.warn(
            "[Decision] FALLBACK engine={} originalResult={} fallbackTarget={} duration={}ms graph={} node={}",
            context.engineId,
            originalResultId,
            fallbackTarget,
            durationMs,
            context.graphId,
            context.nodeId
        )
    }

    override suspend fun onDecisionError(context: DecisionContext, error: SpiceError, durationMs: Long) {
        logger.error(
            "[Decision] ERROR engine={} error={} code={} duration={}ms graph={} node={}",
            context.engineId,
            error.message,
            error.code,
            durationMs,
            context.graphId,
            context.nodeId
        )
    }
}

/**
 * Composite listener that delegates to multiple listeners.
 */
internal class CompositeDecisionListener(
    private val listeners: List<DecisionLifecycleListener>
) : DecisionLifecycleListener {

    override suspend fun onDecisionStart(context: DecisionContext) {
        listeners.forEach { it.onDecisionStart(context) }
    }

    override suspend fun onDecisionComplete(context: DecisionContext, result: DecisionResult, durationMs: Long) {
        listeners.forEach { it.onDecisionComplete(context, result, durationMs) }
    }

    override suspend fun onDecisionFallback(
        context: DecisionContext,
        originalResultId: String,
        fallbackTarget: String,
        durationMs: Long
    ) {
        listeners.forEach { it.onDecisionFallback(context, originalResultId, fallbackTarget, durationMs) }
    }

    override suspend fun onDecisionError(context: DecisionContext, error: SpiceError, durationMs: Long) {
        listeners.forEach { it.onDecisionError(context, error, durationMs) }
    }
}
