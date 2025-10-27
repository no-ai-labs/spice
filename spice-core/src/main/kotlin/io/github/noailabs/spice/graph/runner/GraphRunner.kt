package io.github.noailabs.spice.graph.runner

import io.github.noailabs.spice.AgentContext
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.Graph
import io.github.noailabs.spice.graph.GraphValidator
import io.github.noailabs.spice.graph.NodeContext
import io.github.noailabs.spice.graph.NodeResult
import io.github.noailabs.spice.graph.checkpoint.Checkpoint
import io.github.noailabs.spice.graph.checkpoint.CheckpointConfig
import io.github.noailabs.spice.graph.checkpoint.CheckpointStore
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
    /**
     * Execute a graph without checkpointing.
     */
    suspend fun run(
        graph: Graph,
        input: Map<String, Any?>
    ): SpiceResult<RunReport>

    /**
     * Execute a graph with automatic checkpointing.
     */
    suspend fun runWithCheckpoint(
        graph: Graph,
        input: Map<String, Any?>,
        store: CheckpointStore,
        config: CheckpointConfig = CheckpointConfig()
    ): SpiceResult<RunReport>

    /**
     * Resume execution from a checkpoint.
     */
    suspend fun resume(
        graph: Graph,
        checkpointId: String,
        store: CheckpointStore,
        config: CheckpointConfig = CheckpointConfig()
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
    ): SpiceResult<RunReport> {
        // Validate graph before execution
        return when (val validationResult = GraphValidator.validate(graph)) {
            is SpiceResult.Failure -> SpiceResult.failure(validationResult.error)
            is SpiceResult.Success -> runValidatedGraph(graph, input)
        }
    }

    private suspend fun runValidatedGraph(
        graph: Graph,
        input: Map<String, Any?>
    ): SpiceResult<RunReport> {
        return SpiceResult.catchingSuspend {
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

                // ✨ Execute node with retry support
                var retryCount = 0
                val maxRetries = 3
                var skipNode = false

                val result: NodeResult = run executeLoop@{
                    while (retryCount <= maxRetries) {
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

                        when (resultOrError) {
                            is SpiceResult.Success -> return@executeLoop resultOrError.value
                            is SpiceResult.Failure -> {
                                // ✨ onError middleware chain
                                val errorAction = executeOnErrorChain(graph.middleware, resultOrError.error, runContext)

                                when (errorAction) {
                                    ErrorAction.RETRY -> {
                                        if (retryCount < maxRetries) {
                                            retryCount++
                                            continue  // Retry the node
                                        } else {
                                            // Max retries exceeded, propagate error
                                            val graphEndTime = Instant.now()
                                            val failureReport = RunReport(
                                                graphId = graph.id,
                                                status = RunStatus.FAILED,
                                                result = null,
                                                duration = Duration.between(graphStartTime, graphEndTime),
                                                nodeReports = nodeReports,
                                                error = resultOrError.error.toException()
                                            )
                                            executeOnFinishChain(graph.middleware, failureReport)
                                            throw resultOrError.error.toException()
                                        }
                                    }

                                    ErrorAction.SKIP -> {
                                        // Skip this node, return dummy result
                                        skipNode = true
                                        return@executeLoop NodeResult(data = null)
                                    }

                                    is ErrorAction.CONTINUE -> {
                                        // Use the provided replacement result
                                        skipNode = false  // Not skipped, we have a result
                                        return@executeLoop NodeResult(data = errorAction.result)
                                    }

                                    ErrorAction.PROPAGATE -> {
                                        val graphEndTime = Instant.now()
                                        val failureReport = RunReport(
                                            graphId = graph.id,
                                            status = RunStatus.FAILED,
                                            result = null,
                                            duration = Duration.between(graphStartTime, graphEndTime),
                                            nodeReports = nodeReports,
                                            error = resultOrError.error.toException()
                                        )
                                        executeOnFinishChain(graph.middleware, failureReport)
                                        throw resultOrError.error.toException()
                                    }
                                }
                            }
                        }
                    }
                    // Should never reach here
                    throw IllegalStateException("Node execution loop exited unexpectedly")
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
                        status = if (skipNode) NodeStatus.SKIPPED else NodeStatus.SUCCESS,
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
        var action: ErrorAction = ErrorAction.PROPAGATE
        for (middleware in middlewares) {
            val middlewareAction = middleware.onError(error.toException(), runContext)
            if (middlewareAction !is ErrorAction.PROPAGATE) {
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

    /**
     * Execute graph with checkpoint support.
     */
    override suspend fun runWithCheckpoint(
        graph: Graph,
        input: Map<String, Any?>,
        store: CheckpointStore,
        config: CheckpointConfig
    ): SpiceResult<RunReport> {
        // Validate graph before execution
        return when (val validationResult = GraphValidator.validate(graph)) {
            is SpiceResult.Failure -> SpiceResult.failure(validationResult.error)
            is SpiceResult.Success -> runValidatedGraphWithCheckpoint(graph, input, store, config)
        }
    }

    private suspend fun runValidatedGraphWithCheckpoint(
        graph: Graph,
        input: Map<String, Any?>,
        store: CheckpointStore,
        config: CheckpointConfig
    ): SpiceResult<RunReport> {
        val agentContext = coroutineContext[AgentContext]

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

        return executeGraphWithCheckpoint(
            graph = graph,
            runContext = runContext,
            nodeContext = nodeContext,
            startNodeId = graph.entryPoint,
            store = store,
            config = config
        )
    }

    /**
     * Resume execution from a checkpoint.
     */
    override suspend fun resume(
        graph: Graph,
        checkpointId: String,
        store: CheckpointStore,
        config: CheckpointConfig
    ): SpiceResult<RunReport> = SpiceResult.catchingSuspend {
        // Load checkpoint
        val checkpoint = store.load(checkpointId).getOrThrow()

        val agentContext = checkpoint.agentContext ?: coroutineContext[AgentContext]

        val runContext = RunContext(
            graphId = graph.id,
            runId = checkpoint.runId,
            agentContext = agentContext
        )

        val nodeContext = NodeContext(
            graphId = graph.id,
            state = checkpoint.state.toMutableMap(),
            agentContext = agentContext
        )

        // Start from the next node after the checkpoint
        val startNodeId = graph.edges
            .firstOrNull { edge -> edge.from == checkpoint.currentNodeId }
            ?.to

        executeGraphWithCheckpoint(
            graph = graph,
            runContext = runContext,
            nodeContext = nodeContext,
            startNodeId = startNodeId,
            store = store,
            config = config
        ).getOrThrow()
    }.recoverWith { error ->
        SpiceResult.failure(error)
    }

    /**
     * Core graph execution logic with checkpoint support.
     * Extracted to avoid code duplication between runWithCheckpoint and resume.
     */
    private suspend fun executeGraphWithCheckpoint(
        graph: Graph,
        runContext: RunContext,
        nodeContext: NodeContext,
        startNodeId: String?,
        store: CheckpointStore,
        config: CheckpointConfig
    ): SpiceResult<RunReport> = SpiceResult.catchingSuspend {
        val nodeReports = mutableListOf<NodeReport>()
        val graphStartTime = Instant.now()
        var lastCheckpointTime = graphStartTime
        var nodesExecutedSinceCheckpoint = 0

        // Only execute onStart for fresh runs (not for resume)
        if (startNodeId == graph.entryPoint) {
            executeOnStartChain(graph.middleware, runContext)
        }

        var currentNodeId: String? = startNodeId

        while (currentNodeId != null) {
            val node = graph.nodes[currentNodeId]
                ?: throw IllegalStateException("Node not found: $currentNodeId")

            val startTime = Instant.now()

            // ✨ Execute node with retry support
            var retryCount = 0
            val maxRetries = 3
            var skipNode = false

            val result: NodeResult = run executeLoop@{
                while (retryCount <= maxRetries) {
                    val nodeRequest = NodeRequest(
                        nodeId = currentNodeId,
                        input = nodeContext.state["_previous"],
                        context = runContext
                    )

                    val resultOrError = executeNodeChain(graph.middleware, nodeRequest) { req ->
                        node.run(nodeContext)
                    }

                    when (resultOrError) {
                        is SpiceResult.Success -> return@executeLoop resultOrError.value
                        is SpiceResult.Failure -> {
                            // Save checkpoint on error if configured
                            if (config.saveOnError) {
                                saveCheckpoint(runContext, nodeContext, currentNodeId, store)
                            }

                            // ✨ Handle ErrorAction (RETRY, SKIP, CONTINUE)
                            val errorAction = executeOnErrorChain(graph.middleware, resultOrError.error, runContext)

                            when (errorAction) {
                                ErrorAction.RETRY -> {
                                    if (retryCount < maxRetries) {
                                        retryCount++
                                        continue  // Retry the node
                                    } else {
                                        // Max retries exceeded, propagate error
                                        val graphEndTime = Instant.now()
                                        val failureReport = RunReport(
                                            graphId = graph.id,
                                            status = RunStatus.FAILED,
                                            result = null,
                                            duration = Duration.between(graphStartTime, graphEndTime),
                                            nodeReports = nodeReports,
                                            error = resultOrError.error.toException()
                                        )
                                        executeOnFinishChain(graph.middleware, failureReport)
                                        throw resultOrError.error.toException()
                                    }
                                }

                                ErrorAction.SKIP -> {
                                    // Skip this node, return dummy result
                                    skipNode = true
                                    return@executeLoop NodeResult(data = null)
                                }

                                is ErrorAction.CONTINUE -> {
                                    // Use the provided replacement result
                                    skipNode = false  // Not skipped, we have a result
                                    return@executeLoop NodeResult(data = errorAction.result)
                                }

                                ErrorAction.PROPAGATE -> {
                                    val graphEndTime = Instant.now()
                                    val failureReport = RunReport(
                                        graphId = graph.id,
                                        status = RunStatus.FAILED,
                                        result = null,
                                        duration = Duration.between(graphStartTime, graphEndTime),
                                        nodeReports = nodeReports,
                                        error = resultOrError.error.toException()
                                    )
                                    executeOnFinishChain(graph.middleware, failureReport)
                                    throw resultOrError.error.toException()
                                }
                            }
                        }
                    }
                }
                // Should never reach here
                throw IllegalStateException("Node execution loop exited unexpectedly")
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
                    status = if (skipNode) NodeStatus.SKIPPED else NodeStatus.SUCCESS,
                    output = result.data
                )
            )

            nodesExecutedSinceCheckpoint++

            // Check if we should save a checkpoint
            val shouldCheckpoint = (config.saveEveryNNodes != null && nodesExecutedSinceCheckpoint >= config.saveEveryNNodes) ||
                    (config.saveEveryNSeconds != null && Duration.between(lastCheckpointTime, endTime).seconds >= config.saveEveryNSeconds)

            if (shouldCheckpoint) {
                saveCheckpoint(runContext, nodeContext, currentNodeId, store).getOrThrow()
                nodesExecutedSinceCheckpoint = 0
                lastCheckpointTime = endTime
            }

            // Find next node
            currentNodeId = graph.edges
                .firstOrNull { edge -> edge.from == currentNodeId && edge.condition(result) }
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

        executeOnFinishChain(graph.middleware, report)

        // Clean up checkpoints on success
        store.deleteByRun(runContext.runId)

        report
    }.recoverWith { error ->
        SpiceResult.failure(error)
    }

    /**
     * Save a checkpoint of current execution state.
     */
    private suspend fun saveCheckpoint(
        runContext: RunContext,
        nodeContext: NodeContext,
        currentNodeId: String,
        store: CheckpointStore
    ): SpiceResult<String> {
        val checkpoint = Checkpoint(
            id = "${runContext.runId}-${currentNodeId}-${System.currentTimeMillis()}",
            runId = runContext.runId,
            graphId = runContext.graphId,
            currentNodeId = currentNodeId,
            state = nodeContext.state.toMap(),
            agentContext = nodeContext.agentContext,
            timestamp = Instant.now()
        )

        return store.save(checkpoint)
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
