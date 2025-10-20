package io.github.noailabs.spice.observability

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.Comm
import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.error.SpiceResult
import io.opentelemetry.api.trace.SpanKind

/**
 * 🔍 TracedAgent - Agent wrapper with OpenTelemetry tracing
 *
 * Automatically adds distributed tracing to any agent:
 * - Traces all processComm calls
 * - Records latency metrics
 * - Tracks success/failure rates
 * - Adds contextual attributes
 *
 * Usage:
 * ```kotlin
 * val myAgent = buildAgent { ... }
 * val tracedAgent = TracedAgent(myAgent)
 * ```
 */
class TracedAgent(
    private val delegate: Agent,
    private val enableMetrics: Boolean = true
) : Agent by delegate {

    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        if (!ObservabilityConfig.isEnabled()) {
            return delegate.processComm(comm)
        }

        val startTime = System.currentTimeMillis()

        return SpiceTracer.traced(
            spanName = "agent.processComm",
            spanKind = SpanKind.INTERNAL,
            attributes = mapOf(
                "agent.id" to delegate.id,
                "agent.name" to delegate.name,
                "comm.from" to comm.from,
                "comm.to" to (comm.to ?: ""),
                "comm.type" to comm.type.name,
                "comm.id" to comm.id
            )
        ) { span ->
            try {
                // Add content preview (first 100 chars)
                val contentPreview = comm.content.take(100)
                span.setAttribute("comm.content_preview", contentPreview)

                // Execute delegate
                val result = delegate.processComm(comm)

                // Calculate latency
                val latencyMs = System.currentTimeMillis() - startTime

                // Record outcome
                result.fold(
                    onSuccess = { response ->
                        span.setAttribute("success", true)
                        span.setAttribute("response.length", response.content.length.toLong())

                        // Record metrics
                        if (enableMetrics) {
                            SpiceMetrics.recordAgentCall(
                                agentId = delegate.id,
                                agentName = delegate.name,
                                latencyMs = latencyMs,
                                success = true
                            )
                        }

                        SpiceTracer.addEvent("agent.processComm.success", mapOf(
                            "latency_ms" to latencyMs.toString(),
                            "response_length" to response.content.length.toString()
                        ))
                    },
                    onFailure = { error ->
                        span.setAttribute("success", false)
                        span.setAttribute("error.type", error::class.simpleName ?: "Unknown")
                        span.setAttribute("error.message", error.message ?: "")

                        // Record metrics
                        if (enableMetrics) {
                            SpiceMetrics.recordAgentCall(
                                agentId = delegate.id,
                                agentName = delegate.name,
                                latencyMs = latencyMs,
                                success = false
                            )

                            SpiceMetrics.recordError(
                                errorType = error::class.simpleName ?: "Unknown",
                                component = "agent.${delegate.id}",
                                message = error.message
                            )
                        }

                        SpiceTracer.addEvent("agent.processComm.failure", mapOf(
                            "latency_ms" to latencyMs.toString(),
                            "error_type" to (error::class.simpleName ?: "Unknown")
                        ))
                    }
                )

                result
            } catch (e: Exception) {
                val latencyMs = System.currentTimeMillis() - startTime

                span.setAttribute("success", false)
                span.recordException(e)

                // Record metrics
                if (enableMetrics) {
                    SpiceMetrics.recordAgentCall(
                        agentId = delegate.id,
                        agentName = delegate.name,
                        latencyMs = latencyMs,
                        success = false
                    )

                    SpiceMetrics.recordError(
                        errorType = e::class.simpleName ?: "Unknown",
                        component = "agent.${delegate.id}",
                        message = e.message
                    )
                }

                throw e
            }
        }
    }

    override fun getTools(): List<Tool> = delegate.getTools()

    override fun canHandle(comm: Comm): Boolean = delegate.canHandle(comm)

    override fun isReady(): Boolean = delegate.isReady()

    override val id: String get() = delegate.id
    override val name: String get() = delegate.name
    override val description: String get() = delegate.description
    override val capabilities: List<String> get() = delegate.capabilities
}

/**
 * Extension function to wrap any agent with tracing
 */
fun Agent.traced(enableMetrics: Boolean = true): Agent {
    return if (this is TracedAgent) {
        this // Already traced
    } else {
        TracedAgent(this, enableMetrics)
    }
}

/**
 * DSL function to create a traced agent
 */
fun tracedAgent(
    delegate: Agent,
    enableMetrics: Boolean = true
): Agent = TracedAgent(delegate, enableMetrics)
