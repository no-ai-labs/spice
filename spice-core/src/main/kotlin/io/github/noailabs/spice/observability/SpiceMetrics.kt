package io.github.noailabs.spice.observability

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.*

/**
 * 📊 Spice Metrics - Centralized metrics collection
 *
 * Provides structured metrics for:
 * - Agent operations
 * - Swarm coordination
 * - LLM interactions
 * - Tool executions
 * - System health
 */
object SpiceMetrics {

    private val meter: Meter by lazy {
        ObservabilityConfig.get().getMeter("io.github.noailabs.spice")
    }

    // ==========================================
    // AGENT METRICS
    // ==========================================

    private val agentCallsCounter: LongCounter by lazy {
        meter.counterBuilder("spice.agent.calls")
            .setDescription("Total number of agent calls")
            .setUnit("calls")
            .build()
    }

    private val agentLatencyHistogram: LongHistogram by lazy {
        meter.histogramBuilder("spice.agent.latency")
            .setDescription("Agent call latency")
            .setUnit("ms")
            .ofLongs()
            .build()
    }

    private val agentErrorsCounter: LongCounter by lazy {
        meter.counterBuilder("spice.agent.errors")
            .setDescription("Total number of agent errors")
            .setUnit("errors")
            .build()
    }

    fun recordAgentCall(
        agentId: String,
        agentName: String,
        latencyMs: Long,
        success: Boolean
    ) {
        if (!ObservabilityConfig.isEnabled()) return

        val attrs = Attributes.builder()
            .put("agent.id", agentId)
            .put("agent.name", agentName)
            .put("success", success)
            .build()

        agentCallsCounter.add(1, attrs)
        agentLatencyHistogram.record(latencyMs, attrs)

        if (!success) {
            agentErrorsCounter.add(1, attrs)
        }
    }

    // ==========================================
    // SWARM METRICS
    // ==========================================

    private val swarmOperationsCounter: LongCounter by lazy {
        meter.counterBuilder("spice.swarm.operations")
            .setDescription("Total number of swarm operations")
            .setUnit("operations")
            .build()
    }

    private val swarmLatencyHistogram: LongHistogram by lazy {
        meter.histogramBuilder("spice.swarm.latency")
            .setDescription("Swarm operation latency")
            .setUnit("ms")
            .ofLongs()
            .build()
    }

    private val swarmSuccessRateGauge: ObservableDoubleGauge by lazy {
        meter.gaugeBuilder("spice.swarm.success_rate")
            .setDescription("Swarm operation success rate")
            .setUnit("ratio")
            .buildWithCallback { measurement ->
                // This will be populated by SwarmAgent
            }
    }

    fun recordSwarmOperation(
        swarmId: String,
        strategyType: String,
        latencyMs: Long,
        successRate: Double,
        participatingAgents: Int
    ) {
        if (!ObservabilityConfig.isEnabled()) return

        val attrs = Attributes.builder()
            .put("swarm.id", swarmId)
            .put("strategy.type", strategyType)
            .put("participating_agents", participatingAgents.toLong())
            .build()

        swarmOperationsCounter.add(1, attrs)
        swarmLatencyHistogram.record(latencyMs, attrs)
    }

    // ==========================================
    // LLM METRICS
    // ==========================================

    private val llmCallsCounter: LongCounter by lazy {
        meter.counterBuilder("spice.llm.calls")
            .setDescription("Total number of LLM API calls")
            .setUnit("calls")
            .build()
    }

    private val llmTokensCounter: LongCounter by lazy {
        meter.counterBuilder("spice.llm.tokens")
            .setDescription("Total tokens used")
            .setUnit("tokens")
            .build()
    }

    private val llmCostCounter: DoubleCounter by lazy {
        meter.counterBuilder("spice.llm.cost")
            .setDescription("Estimated LLM cost")
            .setUnit("usd")
            .ofDoubles()
            .build()
    }

    private val llmLatencyHistogram: LongHistogram by lazy {
        meter.histogramBuilder("spice.llm.latency")
            .setDescription("LLM API call latency")
            .setUnit("ms")
            .ofLongs()
            .build()
    }

    fun recordLLMCall(
        provider: String,
        model: String,
        latencyMs: Long,
        inputTokens: Int = 0,
        outputTokens: Int = 0,
        success: Boolean = true,
        estimatedCostUsd: Double = 0.0
    ) {
        if (!ObservabilityConfig.isEnabled()) return

        val attrs = Attributes.builder()
            .put("llm.provider", provider)
            .put("llm.model", model)
            .put("success", success)
            .build()

        llmCallsCounter.add(1, attrs)
        llmLatencyHistogram.record(latencyMs, attrs)

        if (inputTokens > 0 || outputTokens > 0) {
            val tokenAttrs = Attributes.builder()
                .put("llm.provider", provider)
                .put("llm.model", model)
                .put("token.type", "input")
                .build()
            llmTokensCounter.add(inputTokens.toLong(), tokenAttrs)

            val outputTokenAttrs = Attributes.builder()
                .put("llm.provider", provider)
                .put("llm.model", model)
                .put("token.type", "output")
                .build()
            llmTokensCounter.add(outputTokens.toLong(), outputTokenAttrs)
        }

        if (estimatedCostUsd > 0) {
            llmCostCounter.add(estimatedCostUsd, attrs)
        }
    }

    // ==========================================
    // TOOL METRICS
    // ==========================================

    private val toolExecutionsCounter: LongCounter by lazy {
        meter.counterBuilder("spice.tool.executions")
            .setDescription("Total number of tool executions")
            .setUnit("executions")
            .build()
    }

    private val toolLatencyHistogram: LongHistogram by lazy {
        meter.histogramBuilder("spice.tool.latency")
            .setDescription("Tool execution latency")
            .setUnit("ms")
            .ofLongs()
            .build()
    }

    fun recordToolExecution(
        toolName: String,
        agentId: String,
        latencyMs: Long,
        success: Boolean
    ) {
        if (!ObservabilityConfig.isEnabled()) return

        val attrs = Attributes.builder()
            .put("tool.name", toolName)
            .put("agent.id", agentId)
            .put("success", success)
            .build()

        toolExecutionsCounter.add(1, attrs)
        toolLatencyHistogram.record(latencyMs, attrs)
    }

    // ==========================================
    // ERROR METRICS
    // ==========================================

    private val errorsCounter: LongCounter by lazy {
        meter.counterBuilder("spice.errors")
            .setDescription("Total number of errors")
            .setUnit("errors")
            .build()
    }

    fun recordError(
        errorType: String,
        component: String,
        message: String? = null
    ) {
        if (!ObservabilityConfig.isEnabled()) return

        val attrs = Attributes.builder()
            .put("error.type", errorType)
            .put("component", component)
            .also { builder ->
                if (message != null) {
                    builder.put("error.message", message)
                }
            }
            .build()

        errorsCounter.add(1, attrs)
    }

    // ==========================================
    // SYSTEM METRICS
    // ==========================================

    private val activeAgentsGauge: ObservableLongGauge by lazy {
        meter.gaugeBuilder("spice.system.active_agents")
            .setDescription("Number of active agents")
            .setUnit("agents")
            .ofLongs()
            .buildWithCallback { measurement ->
                // To be populated by AgentRegistry
            }
    }

    private val memoryUsageGauge: ObservableLongGauge by lazy {
        meter.gaugeBuilder("spice.system.memory_usage")
            .setDescription("JVM memory usage")
            .setUnit("bytes")
            .ofLongs()
            .buildWithCallback { measurement ->
                val runtime = Runtime.getRuntime()
                val usedMemory = runtime.totalMemory() - runtime.freeMemory()
                measurement.record(usedMemory)
            }
    }
}

/**
 * Extension function for easy metrics recording
 */
inline fun <T> recordMetrics(
    component: String,
    operation: String,
    block: () -> T
): T {
    val startTime = System.currentTimeMillis()
    return try {
        val result = block()
        val latency = System.currentTimeMillis() - startTime
        SpiceTracer.setAttribute("${component}.${operation}.latency_ms", latency)
        result
    } catch (e: Exception) {
        SpiceMetrics.recordError(
            errorType = e::class.simpleName ?: "Unknown",
            component = component,
            message = e.message
        )
        throw e
    }
}
