package io.github.noailabs.spice.graph.middleware

import io.github.noailabs.spice.graph.NodeResult
import io.github.noailabs.spice.graph.runner.RunReport
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Middleware that collects execution metrics.
 */
class MetricsMiddleware : Middleware {
    private val nodeExecutionTimes = ConcurrentHashMap<String, MutableList<Long>>()
    private val graphExecutionTimes = mutableListOf<Long>()
    private val errorCounts = ConcurrentHashMap<String, Long>()

    override suspend fun onStart(ctx: RunContext, next: suspend () -> Unit) {
        ctx.metadata["startTime"] = Instant.now()
        next()
    }

    override suspend fun onNode(req: NodeRequest, next: suspend (NodeRequest) -> NodeResult): NodeResult {
        val startTime = Instant.now()

        val result = next(req)

        val duration = Duration.between(startTime, Instant.now()).toMillis()
        nodeExecutionTimes.computeIfAbsent(req.nodeId) { mutableListOf() }.add(duration)

        return result
    }

    override suspend fun onError(err: Throwable, ctx: RunContext): ErrorAction {
        val errorType = err::class.simpleName ?: "Unknown"
        errorCounts.merge(errorType, 1L, Long::plus)
        return ErrorAction.PROPAGATE
    }

    override suspend fun onFinish(report: RunReport) {
        graphExecutionTimes.add(report.duration.toMillis())
    }

    /**
     * Get metrics snapshot.
     */
    fun getMetrics(): Metrics {
        return Metrics(
            nodeExecutionTimes = nodeExecutionTimes.mapValues { (_, times) ->
                NodeMetrics(
                    count = times.size,
                    avgDurationMs = if (times.isNotEmpty()) times.average() else 0.0,
                    minDurationMs = times.minOrNull() ?: 0,
                    maxDurationMs = times.maxOrNull() ?: 0
                )
            },
            graphExecutionCount = graphExecutionTimes.size,
            avgGraphDurationMs = if (graphExecutionTimes.isNotEmpty()) graphExecutionTimes.average() else 0.0,
            errorCounts = errorCounts.toMap()
        )
    }

    /**
     * Reset all metrics.
     */
    fun reset() {
        nodeExecutionTimes.clear()
        graphExecutionTimes.clear()
        errorCounts.clear()
    }
}

/**
 * Metrics snapshot.
 */
data class Metrics(
    val nodeExecutionTimes: Map<String, NodeMetrics>,
    val graphExecutionCount: Int,
    val avgGraphDurationMs: Double,
    val errorCounts: Map<String, Long>
)

/**
 * Per-node metrics.
 */
data class NodeMetrics(
    val count: Int,
    val avgDurationMs: Double,
    val minDurationMs: Long,
    val maxDurationMs: Long
)
