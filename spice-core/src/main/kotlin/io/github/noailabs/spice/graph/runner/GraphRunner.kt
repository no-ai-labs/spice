package io.github.noailabs.spice.graph.runner

import io.github.noailabs.spice.AgentContext
import io.github.noailabs.spice.graph.Graph
import io.github.noailabs.spice.graph.NodeContext
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.coroutineContext

/**
 * Interface for executing graphs.
 */
interface GraphRunner {
    suspend fun run(
        graph: Graph,
        input: Map<String, Any?>
    ): RunReport
}

/**
 * Default implementation of GraphRunner with sequential execution.
 */
class DefaultGraphRunner : GraphRunner {
    override suspend fun run(
        graph: Graph,
        input: Map<String, Any?>
    ): RunReport {
        // ✨ Get AgentContext from coroutine context (auto-propagated!)
        val agentContext = coroutineContext[AgentContext]

        val ctx = NodeContext(
            graphId = graph.id,
            state = input.toMutableMap(),
            agentContext = agentContext  // Pass context to all nodes
        )

        val nodeReports = mutableListOf<NodeReport>()
        var currentNodeId: String? = graph.entryPoint
        val graphStartTime = Instant.now()

        try {
            while (currentNodeId != null) {
                val node = graph.nodes[currentNodeId]
                    ?: throw IllegalStateException("Node not found: $currentNodeId")

                val startTime = Instant.now()

                // Execute node
                val result = node.run(ctx)

                val endTime = Instant.now()

                // Store result in context with node id as key
                ctx.state[currentNodeId] = result.data
                // Also store as "_previous" for next node to use
                ctx.state["_previous"] = result.data

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

            // Get last node's result
            val finalNodeId = nodeReports.lastOrNull()?.nodeId
            val finalResult = if (finalNodeId != null) ctx.state[finalNodeId] else null

            return RunReport(
                graphId = graph.id,
                status = RunStatus.SUCCESS,
                result = finalResult,
                duration = Duration.between(graphStartTime, graphEndTime),
                nodeReports = nodeReports
            )

        } catch (e: Exception) {
            val graphEndTime = Instant.now()

            return RunReport(
                graphId = graph.id,
                status = RunStatus.FAILED,
                result = null,
                duration = Duration.between(graphStartTime, graphEndTime),
                nodeReports = nodeReports,
                error = e
            )
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
