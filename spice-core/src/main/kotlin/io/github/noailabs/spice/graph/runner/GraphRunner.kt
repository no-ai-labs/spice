package io.github.noailabs.spice.graph.runner

import io.github.noailabs.spice.AgentContext
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.Graph
import io.github.noailabs.spice.graph.NodeContext
import io.github.noailabs.spice.graph.NodeResult
import io.github.noailabs.spice.graph.middleware.ErrorAction
import io.github.noailabs.spice.graph.middleware.NodeRequest
import io.github.noailabs.spice.graph.middleware.RunContext
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.coroutineContext

/**
 * Interface for executing graphs.
 * Returns SpiceResult for consistent error handling.
 */
interface GraphRunner {
    suspend fun run(
        graph: Graph,
        input: Map<String, Any?>
    ): SpiceResult<RunReport>
}

/**
 * Default implementation of GraphRunner with sequential execution.
 * Supports middleware chain for intercepting graph and node execution.
 */
class DefaultGraphRunner : GraphRunner {
    override suspend fun run(
        graph: Graph,
        input: Map<String, Any?>
    ): SpiceResult<RunReport> = SpiceResult.catchingSuspend {
        // ✨ Get AgentContext from coroutine context (auto-propagated!)
        val agentContext = coroutineContext[AgentContext]

        // Create run context for middleware
        val runContext = RunContext(
            graphId = graph.id,
            runId = UUID.randomUUID().toString(),
            agentContext = agentContext
        )

        val nodeContext = NodeContext(
            graphId = graph.id,
            state = input.toMutableMap(),
            agentContext = agentContext
        )

        val nodeReports = mutableListOf<NodeReport>()
        val graphStartTime = Instant.now()

        // ✨ onStart middleware chain
        executeOnStartChain(graph.middleware, runContext)

        var currentNodeId: String? = graph.entryPoint

        while (currentNodeId != null) {
            val node = graph.nodes[currentNodeId]
                ?: throw IllegalStateException("Node not found: $currentNodeId")

            val startTime = Instant.now()

            // ✨ Execute node through middleware chain
            val nodeRequest = NodeRequest(
                nodeId = currentNodeId,
                input = nodeContext.state["_previous"],
                context = runContext
            )

            val resultOrError = executeNodeChain(
                graph.middleware,
                nodeRequest
            ) { req ->
                // Actual node execution
                node.run(nodeContext)
            }

            // Handle node execution result
            val result = when (resultOrError) {
                is SpiceResult.Success -> resultOrError.value
                is SpiceResult.Failure -> {
                    // ✨ onError middleware chain
                    val errorAction = executeOnErrorChain(graph.middleware, resultOrError.error, runContext)

                    val graphEndTime = Instant.now()
                    val failureReport = RunReport(
                        graphId = graph.id,
                        status = RunStatus.FAILED,
                        result = null,
                        duration = Duration.between(graphStartTime, graphEndTime),
                        nodeReports = nodeReports,
                        error = resultOrError.error.toException()
                    )

                    // ✨ onFinish middleware chain
                    executeOnFinishChain(graph.middleware, failureReport)

                    throw resultOrError.error.toException()
                }
            }

            val endTime = Instant.now()

            // Store result in context
            nodeContext.state[currentNodeId] = result.data
            nodeContext.state["_previous"] = result.data

            // Record node execution
            nodeReports.add(
                NodeReport(
                    nodeId = currentNodeId,
                    startTime = startTime,
                    duration = Duration.between(startTime, endTime),
                    status = NodeStatus.SUCCESS,
                    output = result.data
                )
            )

            // Find next node
            currentNodeId = graph.edges
                .firstOrNull { edge ->
                    edge.from == currentNodeId && edge.condition(result)
                }
                ?.to
        }

        val graphEndTime = Instant.now()
        val finalNodeId = nodeReports.lastOrNull()?.nodeId
        val finalResult = if (finalNodeId != null) nodeContext.state[finalNodeId] else null

        val report = RunReport(
            graphId = graph.id,
            status = RunStatus.SUCCESS,
            result = finalResult,
            duration = Duration.between(graphStartTime, graphEndTime),
            nodeReports = nodeReports
        )

        // ✨ onFinish middleware chain
        executeOnFinishChain(graph.middleware, report)

        report
    }.recoverWith { error ->
        // Catch any unexpected errors not handled by node execution
        SpiceResult.failure(error)
    }

    /**
     * Execute onStart middleware chain.
     */
    private suspend fun executeOnStartChain(
        middlewares: List<io.github.noailabs.spice.graph.middleware.Middleware>,
        runContext: RunContext
    ) {
        var index = 0
        suspend fun next() {
            if (index < middlewares.size) {
                val middleware = middlewares[index++]
                middleware.onStart(runContext) { next() }
            }
        }
        next()
    }

    /**
     * Execute onNode middleware chain.
     */
    private suspend fun executeNodeChain(
        middlewares: List<io.github.noailabs.spice.graph.middleware.Middleware>,
        request: NodeRequest,
        actualExecution: suspend (NodeRequest) -> SpiceResult<NodeResult>
    ): SpiceResult<NodeResult> {
        var index = 0
        suspend fun next(req: NodeRequest): SpiceResult<NodeResult> {
            return if (index < middlewares.size) {
                val middleware = middlewares[index++]
                middleware.onNode(req) { next(it) }
            } else {
                actualExecution(req)
            }
        }
        return next(request)
    }

    /**
     * Execute onError middleware chain.
     */
    private suspend fun executeOnErrorChain(
        middlewares: List<io.github.noailabs.spice.graph.middleware.Middleware>,
        error: SpiceError,
        runContext: RunContext
    ): ErrorAction {
        var action = ErrorAction.PROPAGATE
        for (middleware in middlewares) {
            val middlewareAction = middleware.onError(error.toException(), runContext)
            if (middlewareAction != ErrorAction.PROPAGATE) {
                action = middlewareAction
                break
            }
        }
        return action
    }

    /**
     * Execute onFinish middleware chain.
     */
    private suspend fun executeOnFinishChain(
        middlewares: List<io.github.noailabs.spice.graph.middleware.Middleware>,
        report: RunReport
    ) {
        for (middleware in middlewares) {
            middleware.onFinish(report)
        }
    }
}

/**
 * Report of a graph execution.
 */
data class RunReport(
    val graphId: String,
    val status: RunStatus,
    val result: Any?,
    val duration: Duration,
    val nodeReports: List<NodeReport>,
    val error: Throwable? = null
)

/**
 * Status of a graph execution.
 */
enum class RunStatus {
    SUCCESS,
    FAILED,
    CANCELLED
}

/**
 * Report of a single node execution.
 */
data class NodeReport(
    val nodeId: String,
    val startTime: Instant,
    val duration: Duration,
    val status: NodeStatus,
    val output: Any?
)

/**
 * Status of a node execution.
 */
enum class NodeStatus {
    SUCCESS,
    FAILED,
    SKIPPED
}
