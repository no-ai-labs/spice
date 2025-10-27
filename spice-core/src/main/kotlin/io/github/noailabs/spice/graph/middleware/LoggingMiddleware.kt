package io.github.noailabs.spice.graph.middleware

import io.github.noailabs.spice.graph.NodeResult
import io.github.noailabs.spice.graph.runner.RunReport
import org.slf4j.LoggerFactory

/**
 * Middleware that logs graph and node execution events.
 */
class LoggingMiddleware : Middleware {
    private val logger = LoggerFactory.getLogger(LoggingMiddleware::class.java)

    override suspend fun onStart(ctx: RunContext, next: suspend () -> Unit) {
        logger.info("🚀 Starting graph execution: graphId={}, runId={}", ctx.graphId, ctx.runId)
        next()
    }

    override suspend fun onNode(req: NodeRequest, next: suspend (NodeRequest) -> NodeResult): NodeResult {
        logger.debug("▶️  Executing node: nodeId={}, runId={}", req.nodeId, req.context.runId)

        val result = next(req)

        logger.debug("✅ Node completed: nodeId={}, output={}", req.nodeId, result.data)

        return result
    }

    override suspend fun onError(err: Throwable, ctx: RunContext): ErrorAction {
        logger.error("❌ Graph execution failed: graphId={}, runId={}, error={}",
            ctx.graphId, ctx.runId, err.message, err)
        return ErrorAction.PROPAGATE
    }

    override suspend fun onFinish(report: RunReport) {
        logger.info("🏁 Graph execution finished: graphId={}, status={}, duration={}ms, nodes={}",
            report.graphId, report.status, report.duration.toMillis(), report.nodeReports.size)
    }
}
