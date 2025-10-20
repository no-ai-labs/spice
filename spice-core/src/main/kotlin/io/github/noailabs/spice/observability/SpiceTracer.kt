package io.github.noailabs.spice.observability

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * 🔍 Spice Tracer - Simplified tracing utilities
 *
 * Provides convenient functions for creating and managing OpenTelemetry spans
 * with coroutine support.
 */
object SpiceTracer {

    private val tracer: Tracer by lazy {
        ObservabilityConfig.getTracer()
    }

    /**
     * Execute a block of code within a traced span
     */
    suspend fun <T> traced(
        spanName: String,
        spanKind: SpanKind = SpanKind.INTERNAL,
        attributes: Map<String, String> = emptyMap(),
        block: suspend (Span) -> T
    ): T {
        if (!ObservabilityConfig.isEnabled()) {
            // If observability is disabled, just execute the block
            return block(Span.getInvalid())
        }

        val span = tracer.spanBuilder(spanName)
            .setSpanKind(spanKind)
            .startSpan()

        // Add attributes
        attributes.forEach { (key, value) ->
            span.setAttribute(key, value)
        }

        return try {
            // Make span current and execute block
            span.makeCurrent().use {
                val result = block(span)
                span.setStatus(StatusCode.OK)
                result
            }
        } catch (e: Exception) {
            span.setStatus(StatusCode.ERROR, e.message ?: "Unknown error")
            span.recordException(e)
            throw e
        } finally {
            span.end()
        }
    }

    /**
     * Execute a block with a child span
     */
    suspend fun <T> childSpan(
        spanName: String,
        attributes: Map<String, String> = emptyMap(),
        block: suspend (Span) -> T
    ): T {
        return traced(spanName, SpanKind.INTERNAL, attributes, block)
    }

    /**
     * Get the current span
     */
    fun currentSpan(): Span {
        return Span.current()
    }

    /**
     * Add an attribute to the current span
     */
    fun setAttribute(key: String, value: String) {
        currentSpan().setAttribute(key, value)
    }

    /**
     * Add an attribute to the current span
     */
    fun setAttribute(key: String, value: Long) {
        currentSpan().setAttribute(key, value)
    }

    /**
     * Add an attribute to the current span
     */
    fun setAttribute(key: String, value: Double) {
        currentSpan().setAttribute(key, value)
    }

    /**
     * Add an attribute to the current span
     */
    fun setAttribute(key: String, value: Boolean) {
        currentSpan().setAttribute(key, value)
    }

    /**
     * Add an event to the current span
     */
    fun addEvent(name: String, attributes: Map<String, String> = emptyMap()) {
        val span = currentSpan()
        if (attributes.isEmpty()) {
            span.addEvent(name)
        } else {
            val attrs = io.opentelemetry.api.common.Attributes.builder()
            attributes.forEach { (k, v) -> attrs.put(k, v) }
            span.addEvent(name, attrs.build())
        }
    }

    /**
     * Record an exception in the current span
     */
    fun recordException(exception: Throwable) {
        currentSpan().recordException(exception)
    }

    /**
     * Set span status to error
     */
    fun setError(message: String) {
        currentSpan().setStatus(StatusCode.ERROR, message)
    }
}

/**
 * Coroutine context element for carrying OpenTelemetry context
 */
class OtelContextElement(val context: Context) : AbstractCoroutineContextElement(OtelContextElement) {
    companion object Key : CoroutineContext.Key<OtelContextElement>
}

/**
 * Extension function to execute a suspend block within a traced span
 */
suspend fun <T> traceSpan(
    spanName: String,
    attributes: Map<String, String> = emptyMap(),
    block: suspend () -> T
): T = SpiceTracer.traced(spanName, attributes = attributes) { block() }
