package io.github.noailabs.spice.graph.runner

import io.github.noailabs.spice.AgentContext
import io.github.noailabs.spice.ExecutionContext
import io.github.noailabs.spice.toExecutionContext
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.Graph
import io.github.noailabs.spice.graph.GraphValidator
import io.github.noailabs.spice.graph.NodeContext
import io.github.noailabs.spice.graph.NodeResult
import io.github.noailabs.spice.graph.checkpoint.Checkpoint
import io.github.noailabs.spice.graph.checkpoint.MetadataValidator
import io.github.noailabs.spice.graph.checkpoint.NoopMetadataValidator
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

    /**
     * Resume execution after receiving human input.
     * Used for HITL (Human-in-the-Loop) pattern.
     */
    suspend fun resumeWithHumanResponse(
        graph: Graph,
        checkpointId: String,
        response: io.github.noailabs.spice.graph.nodes.HumanResponse,
        store: CheckpointStore
    ): SpiceResult<RunReport>

    /**
     * Get current human interactions waiting for response.
     * Returns list of pending interactions from a checkpoint.
     */
    suspend fun getPendingInteractions(
        checkpointId: String,
        store: CheckpointStore
    ): SpiceResult<List<io.github.noailabs.spice.graph.nodes.HumanInteraction>>
}

/**
 * Default implementation of GraphRunner with sequential execution.
 * Supports middleware chain for intercepting graph and node execution.
 */
class DefaultGraphRunner(
    private val metadataValidator: MetadataValidator = NoopMetadataValidator
) : GraphRunner {
    override suspend fun run(
        graph: Graph,
        input: Map<String, Any?>
    ): SpiceResult<RunReport> {
        // Validate graph before execution
        val validationResult = GraphValidator.validate(graph)
        if (validationResult is SpiceResult.Failure) {
            return SpiceResult.failure(validationResult.error)
        }
        return runValidatedGraph(graph, input)
    }

    private suspend fun runValidatedGraph(
        graph: Graph,
        input: Map<String, Any?>
    ): SpiceResult<RunReport> {
        return SpiceResult.catchingSuspend {
            // ✨ Build ExecutionContext from coroutine AgentContext and input metadata
            val agentContext = coroutineContext[AgentContext]
            val initialMetaUntyped = (input["metadata"] as? Map<*, *>) ?: emptyMap<Any, Any>()
            val initialMeta = initialMetaUntyped.entries.associate { it.key.toString() to (it.value as Any) }
            val execContext = (agentContext?.toExecutionContext(initialMeta)) ?: ExecutionContext.of(initialMeta)

            // Create run context for middleware
            val runContext = RunContext(
                graphId = graph.id,
                runId = UUID.randomUUID().toString(),
                context = execContext
            )

            var nodeContext = NodeContext.create(
                graphId = graph.id,
                state = input,
                context = execContext
            )

            val nodeReports = mutableListOf<NodeReport>()
            val graphStartTime = Instant.now()

            // ✨ onStart middleware chain
            executeOnStartChain(graph.middleware, runContext)

            var currentNodeId: String? = graph.entryPoint

            while (currentNodeId != null) {
                val nodeId = currentNodeId ?: error("Current node id is null during execution")
                val node = graph.nodes[nodeId]
                    ?: throw IllegalStateException("Node not found: $nodeId")

                val startTime = Instant.now()

                // ✨ Execute node with retry support
                var retryCount = 0
                val maxRetries = 3
                var skipNode = false

                val result: NodeResult = run executeLoop@{
                    while (retryCount <= maxRetries) {
                        val nodeRequest = NodeRequest(
                            nodeId = nodeId,
                            input = nodeContext.state["_previous"],
                            context = runContext
                        )

                        val resultOrError = executeNodeChain(
                            graph.middleware,
                            nodeRequest
                        ) { _ ->
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
                                        return@executeLoop NodeResult.fromContext(
                                            ctx = nodeContext,
                                            data = null
                                        )
                                    }

                                    is ErrorAction.CONTINUE -> {
                                        // Use the provided replacement result
                                        skipNode = false  // Not skipped, we have a result
                                        return@executeLoop NodeResult.fromContext(
                                            ctx = nodeContext,
                                            data = errorAction.result
                                        )
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

                // Store result in context and propagate metadata
                val previousMetadata = nodeContext.context.toMap()
                val enrichedContext = nodeContext.context.plusAll(result.metadata)

                // 🔥 Propagate result.metadata to state for next node access
                // This ensures all node results (including Comm.data from AgentNode) are accessible via state
                val stateUpdates = mutableMapOf<String, Any?>(
                    nodeId to result.data,
                    "_previous" to result.data
                )
                // Add all metadata to state
                result.metadata.forEach { (key, value) ->
                    stateUpdates[key] = value
                }

                nodeContext = nodeContext
                    .withState(stateUpdates)
                    .withContext(enrichedContext)

                // Record node execution
                val metadataChanges = result.metadata.filter { (k, v) -> previousMetadata[k] != v }
                nodeReports.add(
                    NodeReport(
                        nodeId = nodeId,
                        startTime = startTime,
                        duration = Duration.between(startTime, endTime),
                        status = if (skipNode) NodeStatus.SKIPPED else NodeStatus.SUCCESS,
                        output = result.data,
                        metadata = enrichedContext.toMap(),
                        metadataChanges = if (metadataChanges.isEmpty()) null else metadataChanges
                    )
                )

                // Find next node
                val nextId = graph.edges
                    .firstOrNull { edge -> edge.from == nodeId && edge.condition(result) }
                    ?.to
                currentNodeId = nextId
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
        suspend fun next(requestParam: NodeRequest): SpiceResult<NodeResult> {
            return if (index < middlewares.size) {
                val middleware = middlewares[index++]
                middleware.onNode(requestParam) { next(it) }
            } else {
                actualExecution(requestParam)
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
        val validationResult = GraphValidator.validate(graph)
        if (validationResult is SpiceResult.Failure) {
            return SpiceResult.failure(validationResult.error)
        }
        return runValidatedGraphWithCheckpoint(graph, input, store, config)
    }

    private suspend fun runValidatedGraphWithCheckpoint(
        graph: Graph,
        input: Map<String, Any?>,
        store: CheckpointStore,
        config: CheckpointConfig
    ): SpiceResult<RunReport> {
        val agentContext = coroutineContext[AgentContext]
        val inputMetaUntyped = (input["metadata"] as? Map<*, *>) ?: emptyMap<Any, Any>()
        val inputMeta = inputMetaUntyped.entries.associate { it.key.toString() to (it.value as Any) }
        val execContext = (agentContext?.toExecutionContext(inputMeta)) ?: ExecutionContext.of(inputMeta)

        val runContext = RunContext(
            graphId = graph.id,
            runId = UUID.randomUUID().toString(),
            context = execContext
        )

        var nodeContext = NodeContext.create(
            graphId = graph.id,
            state = input,
            context = execContext
        )

        // Validate initial metadata
        val validation = metadataValidator.validate(nodeContext.context.toMap())
        if (validation is SpiceResult.Failure) {
            return SpiceResult.failure(validation.error)
        }

        return executeGraphWithCheckpoint(
            graph = graph,
            runContext = runContext,
            initialNodeContext = nodeContext,
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
    ): SpiceResult<RunReport> {
        return SpiceResult.catchingSuspend {
            // Load checkpoint
            val checkpoint = store.load(checkpointId).getOrThrow()

            val agentContext = checkpoint.agentContext ?: coroutineContext[AgentContext]
            val resumeContext = (agentContext?.toExecutionContext(checkpoint.metadata))
                ?: ExecutionContext.of(checkpoint.metadata)

            val runContext = RunContext(
                graphId = graph.id,
                runId = checkpoint.runId,
                context = resumeContext
            )

        val nodeContext = NodeContext.create(
            graphId = graph.id,
            state = checkpoint.state,
            context = resumeContext
        )

            // Validate restored metadata
            val validationResume = metadataValidator.validate(nodeContext.context.toMap())
            when (validationResume) {
                is SpiceResult.Failure -> throw validationResume.error.toException()
                is SpiceResult.Success -> Unit
            }

            // Start from the next node after the checkpoint
            val startNodeId = graph.edges
                .firstOrNull { edge -> edge.from == checkpoint.currentNodeId }
                ?.to

            executeGraphWithCheckpoint(
                graph = graph,
                runContext = runContext,
                initialNodeContext = nodeContext,
                startNodeId = startNodeId,
                store = store,
                config = config
            ).getOrThrow()
        }.recoverWith { error ->
            SpiceResult.failure(error)
        }
    }

    /**
     * Core graph execution logic with checkpoint support.
     * Extracted to avoid code duplication between runWithCheckpoint and resume.
     */
    private suspend fun executeGraphWithCheckpoint(
        graph: Graph,
        runContext: RunContext,
        initialNodeContext: NodeContext,
        startNodeId: String?,
        store: CheckpointStore,
        config: CheckpointConfig
    ): SpiceResult<RunReport> = SpiceResult.catchingSuspend {
        var nodeContext = initialNodeContext
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
            val nodeId = currentNodeId ?: error("Current node id is null during execution")
            val node = graph.nodes[nodeId]
                ?: throw IllegalStateException("Node not found: $nodeId")

            val startTime = Instant.now()

            // ✨ Execute node with retry support
            var retryCount = 0
            val maxRetries = 3
            var skipNode = false

            val result: NodeResult = run executeLoop@{
                while (retryCount <= maxRetries) {
                    val nodeRequest = NodeRequest(
                        nodeId = nodeId,
                        input = nodeContext.state["_previous"],
                        context = runContext
                    )

                    val resultOrError = executeNodeChain(graph.middleware, nodeRequest) { _ ->
                        node.run(nodeContext)
                    }

                    when (resultOrError) {
                        is SpiceResult.Success -> return@executeLoop resultOrError.value
                        is SpiceResult.Failure -> {
                            // Save checkpoint on error if configured
                            if (config.saveOnError) {
                                saveCheckpoint(runContext, nodeContext, nodeId, store)
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
                                    return@executeLoop NodeResult.fromContext(
                                        ctx = nodeContext,
                                        data = null
                                    )
                                }

                                is ErrorAction.CONTINUE -> {
                                    // Use the provided replacement result
                                    skipNode = false  // Not skipped, we have a result
                                    return@executeLoop NodeResult.fromContext(
                                        ctx = nodeContext,
                                        data = errorAction.result
                                    )
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

                // Store result and propagate metadata
                val previousMetadata = nodeContext.context.toMap()
                val enrichedContext = nodeContext.context.plusAll(result.metadata)

                // 🔥 Propagate result.metadata to state for next node access
                // This ensures all node results (including Comm.data from AgentNode) are accessible via state
                val stateUpdates = mutableMapOf<String, Any?>(
                    nodeId to result.data,
                    "_previous" to result.data
                )
                // Add all metadata to state
                result.metadata.forEach { (key, value) ->
                    stateUpdates[key] = value
                }

                nodeContext = nodeContext
                    .withState(stateUpdates)
                    .withContext(enrichedContext)

            // Record node execution
            val metadataChanges = result.metadata.filter { (k, v) -> previousMetadata[k] != v }
            nodeReports.add(
                NodeReport(
                    nodeId = nodeId,
                    startTime = startTime,
                    duration = Duration.between(startTime, endTime),
                    status = if (skipNode) NodeStatus.SKIPPED else NodeStatus.SUCCESS,
                    output = result.data,
                    metadata = enrichedContext.toMap(),
                    metadataChanges = if (metadataChanges.isEmpty()) null else metadataChanges
                )
            )

            // ✨ Check if this is a HumanNode that requires human input
            if (result.data is io.github.noailabs.spice.graph.nodes.HumanInteraction) {
                val interaction = result.data as io.github.noailabs.spice.graph.nodes.HumanInteraction

                // Save checkpoint with WAITING_FOR_HUMAN state
                val checkpointId = saveCheckpoint(
                    runContext = runContext,
                    nodeContext = nodeContext,
                    currentNodeId = nodeId,
                    store = store,
                    executionState = io.github.noailabs.spice.graph.nodes.GraphExecutionState.WAITING_FOR_HUMAN,
                    pendingInteraction = interaction
                ).getOrThrow()

                // Return a paused report
                val pausedReport = RunReport(
                    graphId = graph.id,
                    status = RunStatus.PAUSED,
                    result = interaction,
                    duration = Duration.between(graphStartTime, endTime),
                    nodeReports = nodeReports,
                    checkpointId = checkpointId
                )

                executeOnFinishChain(graph.middleware, pausedReport)
                return@catchingSuspend pausedReport
            }

            nodesExecutedSinceCheckpoint++

            // Check if we should save a checkpoint
            val shouldCheckpoint = (config.saveEveryNNodes != null && nodesExecutedSinceCheckpoint >= config.saveEveryNNodes) ||
                    (config.saveEveryNSeconds != null && Duration.between(lastCheckpointTime, endTime).seconds >= config.saveEveryNSeconds)

            if (shouldCheckpoint) {
                saveCheckpoint(runContext, nodeContext, nodeId, store).getOrThrow()
                nodesExecutedSinceCheckpoint = 0
                lastCheckpointTime = endTime
            }

            // Find next node
            currentNodeId = graph.edges
                .firstOrNull { edge -> edge.from == nodeId && edge.condition(result) }
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
        store: CheckpointStore,
        executionState: io.github.noailabs.spice.graph.nodes.GraphExecutionState = io.github.noailabs.spice.graph.nodes.GraphExecutionState.RUNNING,
        pendingInteraction: io.github.noailabs.spice.graph.nodes.HumanInteraction? = null,
        humanResponse: io.github.noailabs.spice.graph.nodes.HumanResponse? = null
    ): SpiceResult<String> {
        val checkpoint = Checkpoint(
            id = "${runContext.runId}-${currentNodeId}-${System.currentTimeMillis()}",
            runId = runContext.runId,
            graphId = runContext.graphId,
            currentNodeId = currentNodeId,
            state = nodeContext.state.toMap(),
            agentContext = null,
            timestamp = Instant.now(),
            metadata = nodeContext.context.toMap(),  // 🔥 Save context to checkpoint!
            executionState = executionState,
            pendingInteraction = pendingInteraction,
            humanResponse = humanResponse
        )

        return store.save(checkpoint)
    }

    /**
     * Get pending human interactions from a checkpoint.
     */
    override suspend fun getPendingInteractions(
        checkpointId: String,
        store: CheckpointStore
    ): SpiceResult<List<io.github.noailabs.spice.graph.nodes.HumanInteraction>> = SpiceResult.catchingSuspend {
        val checkpoint = store.load(checkpointId).getOrThrow()

        if (checkpoint.executionState == io.github.noailabs.spice.graph.nodes.GraphExecutionState.WAITING_FOR_HUMAN &&
            checkpoint.pendingInteraction != null) {
            listOf(checkpoint.pendingInteraction)
        } else {
            emptyList()
        }
    }.recoverWith { error ->
        SpiceResult.failure(error)
    }

    /**
     * Resume execution after receiving human response.
     */
    override suspend fun resumeWithHumanResponse(
        graph: Graph,
        checkpointId: String,
        response: io.github.noailabs.spice.graph.nodes.HumanResponse,
        store: CheckpointStore
    ): SpiceResult<RunReport> {
        return SpiceResult.catchingSuspend {
            // Load checkpoint
            val checkpoint = store.load(checkpointId).getOrThrow()

            // Verify checkpoint is waiting for human
            if (checkpoint.executionState != io.github.noailabs.spice.graph.nodes.GraphExecutionState.WAITING_FOR_HUMAN) {
                throw IllegalStateException("Checkpoint is not waiting for human input (state: ${checkpoint.executionState})")
            }

            // Verify response matches the pending interaction
            if (checkpoint.pendingInteraction?.nodeId != response.nodeId) {
                throw IllegalArgumentException("Response nodeId doesn't match pending interaction")
            }

            // Check timeout
            checkpoint.pendingInteraction?.let { interaction ->
                val expiresAtStr = interaction.expiresAt
                val expiresAt = expiresAtStr?.let { Instant.parse(it) }
                if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
                    throw IllegalStateException("Human response timeout expired at $expiresAtStr")
                }
            }

            // Get HumanNode from graph to access validator
            val humanNode = graph.nodes[checkpoint.currentNodeId] as? io.github.noailabs.spice.graph.nodes.HumanNode

            // Validate response using HumanNode's validator
            humanNode?.validator?.let { validator ->
                if (!validator(response)) {
                    throw IllegalArgumentException("Human response failed validation")
                }
            }

            val agentContext = checkpoint.agentContext ?: coroutineContext[AgentContext]
            val resumeContext = (agentContext?.toExecutionContext(checkpoint.metadata))
                ?: ExecutionContext.of(checkpoint.metadata)

            val runContext = RunContext(
                graphId = graph.id,
                runId = checkpoint.runId,
                context = resumeContext
            )

            var nodeContext = NodeContext.create(
                graphId = graph.id,
                state = checkpoint.state,
                context = resumeContext
            )

            // Validate restored metadata
            val validationHuman = metadataValidator.validate(nodeContext.context.toMap())
            when (validationHuman) {
                is SpiceResult.Failure -> throw validationHuman.error.toException()
                is SpiceResult.Success -> Unit
            }

            // Store the human response in the node state AND merge metadata into context
            // This ensures next AgentNode can access HumanResponse.metadata via ExecutionContext
            val humanMetadata = response.metadata.mapValues { it.value as Any }
            nodeContext = nodeContext
                .withState(checkpoint.currentNodeId, response)
                .withState("_previous", response)
                .withContext(nodeContext.context.plusAll(humanMetadata))  // 🔥 Propagate to ExecutionContext

            // Find the next node after the HumanNode
            val currentResult = io.github.noailabs.spice.graph.NodeResult.fromContext(
                ctx = nodeContext,
                data = response,
                additional = humanMetadata  // 🔥 Explicitly include in metadata
            )
            val nextNodeId = graph.edges
                .firstOrNull { edge ->
                    edge.from == checkpoint.currentNodeId && edge.condition(currentResult)
                }
                ?.to

            // Continue execution from the next node
            executeGraphWithCheckpoint(
                graph = graph,
                runContext = runContext,
                initialNodeContext = nodeContext,
                startNodeId = nextNodeId,
                store = store,
                config = CheckpointConfig()
            ).getOrThrow()
        }.recoverWith { error ->
            SpiceResult.failure(error)
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
    val error: Throwable? = null,
    val checkpointId: String? = null  // For PAUSED state (HITL)
)

/**
 * Status of a graph execution.
 */
enum class RunStatus {
    SUCCESS,
    FAILED,
    CANCELLED,
    PAUSED  // Graph paused waiting for human input (HITL)
}

/**
 * Report of a single node execution.
 */
data class NodeReport(
    val nodeId: String,
    val startTime: Instant,
    val duration: Duration,
    val status: NodeStatus,
    val output: Any?,
    val metadata: Map<String, Any>? = null,
    val metadataChanges: Map<String, Any>? = null
)

/**
 * Status of a node execution.
 */
enum class NodeStatus {
    SUCCESS,
    FAILED,
    SKIPPED
}
