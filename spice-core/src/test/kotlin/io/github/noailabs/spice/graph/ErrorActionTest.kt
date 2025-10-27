package io.github.noailabs.spice.graph

import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.middleware.ErrorAction
import io.github.noailabs.spice.graph.middleware.Middleware
import io.github.noailabs.spice.graph.middleware.NodeRequest
import io.github.noailabs.spice.graph.middleware.RunContext
import io.github.noailabs.spice.graph.nodes.OutputNode
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import io.github.noailabs.spice.graph.runner.NodeStatus
import io.github.noailabs.spice.graph.runner.RunStatus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ErrorActionTest {

    @Test
    fun `test RETRY action retries failed node`() = runTest {
        // Given: Failing node with retry middleware
        var attemptCount = 0
        val failingNode = object : Node {
            override val id = "failing-node"

            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                attemptCount++
                return if (attemptCount < 3) {
                    SpiceResult.failure(SpiceError.AgentError("Attempt $attemptCount failed"))
                } else {
                    SpiceResult.success(NodeResult(data = "Success on attempt $attemptCount"))
                }
            }
        }

        val retryMiddleware = object : Middleware {
            override suspend fun onError(err: Throwable, ctx: RunContext): ErrorAction {
                return ErrorAction.RETRY
            }
        }

        val graph = Graph(
            id = "retry-graph",
            nodes = mapOf(
                "failing-node" to failingNode,
                "next" to OutputNode("next")
            ),
            edges = listOf(
                Edge("failing-node", "next")
            ),
            entryPoint = "failing-node",
            middleware = listOf(retryMiddleware)
        )

        // When: Run graph
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, emptyMap())

        // Then: Should succeed after retries
        assertTrue(result.isSuccess)
        assertEquals(3, attemptCount)
        assertEquals(RunStatus.SUCCESS, result.getOrThrow().status)
    }

    @Test
    fun `test RETRY action fails after max retries`() = runTest {
        // Given: Always failing node with retry middleware
        var attemptCount = 0
        val failingNode = object : Node {
            override val id = "always-failing"

            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                attemptCount++
                return SpiceResult.failure(SpiceError.AgentError("Attempt $attemptCount failed"))
            }
        }

        val retryMiddleware = object : Middleware {
            override suspend fun onError(err: Throwable, ctx: RunContext): ErrorAction {
                return ErrorAction.RETRY
            }
        }

        val graph = Graph(
            id = "max-retry-graph",
            nodes = mapOf(
                "always-failing" to failingNode
            ),
            edges = emptyList(),
            entryPoint = "always-failing",
            middleware = listOf(retryMiddleware)
        )

        // When: Run graph
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, emptyMap())

        // Then: Should fail after max retries (3)
        assertTrue(result.isFailure)
        assertEquals(4, attemptCount)  // 1 initial + 3 retries
    }

    @Test
    fun `test SKIP action skips failed node and continues`() = runTest {
        // Given: Failing node with skip middleware
        val failingNode = object : Node {
            override val id = "failing-node"

            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.failure(SpiceError.AgentError("This node fails"))
            }
        }

        val skipMiddleware = object : Middleware {
            override suspend fun onError(err: Throwable, ctx: RunContext): ErrorAction {
                return ErrorAction.SKIP
            }
        }

        val graph = Graph(
            id = "skip-graph",
            nodes = mapOf(
                "failing-node" to failingNode,
                "next" to OutputNode("next")
            ),
            edges = listOf(
                Edge("failing-node", "next")
            ),
            entryPoint = "failing-node",
            middleware = listOf(skipMiddleware)
        )

        // When: Run graph
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, emptyMap())

        // Then: Should succeed with skipped node
        assertTrue(result.isSuccess)
        val report = result.getOrThrow()
        assertEquals(RunStatus.SUCCESS, report.status)
        assertEquals(2, report.nodeReports.size)

        // Verify first node was skipped
        val failedNodeReport = report.nodeReports.find { it.nodeId == "failing-node" }
        assertEquals(NodeStatus.SKIPPED, failedNodeReport?.status)

        // Verify second node executed
        val nextNodeReport = report.nodeReports.find { it.nodeId == "next" }
        assertEquals(NodeStatus.SUCCESS, nextNodeReport?.status)
    }

    @Test
    fun `test CONTINUE action skips failed node`() = runTest {
        // Given: Failing node with continue middleware
        val failingNode = object : Node {
            override val id = "failing-node"

            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.failure(SpiceError.AgentError("This node fails"))
            }
        }

        val continueMiddleware = object : Middleware {
            override suspend fun onError(err: Throwable, ctx: RunContext): ErrorAction {
                return ErrorAction.CONTINUE
            }
        }

        val graph = Graph(
            id = "continue-graph",
            nodes = mapOf(
                "failing-node" to failingNode,
                "next" to OutputNode("next")
            ),
            edges = listOf(
                Edge("failing-node", "next")
            ),
            entryPoint = "failing-node",
            middleware = listOf(continueMiddleware)
        )

        // When: Run graph
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, emptyMap())

        // Then: Should succeed with skipped node (same as SKIP for now)
        assertTrue(result.isSuccess)
        val report = result.getOrThrow()
        assertEquals(RunStatus.SUCCESS, report.status)

        val failedNodeReport = report.nodeReports.find { it.nodeId == "failing-node" }
        assertEquals(NodeStatus.SKIPPED, failedNodeReport?.status)
    }

    @Test
    fun `test PROPAGATE action propagates error`() = runTest {
        // Given: Failing node with propagate middleware
        val failingNode = object : Node {
            override val id = "failing-node"

            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.failure(SpiceError.AgentError("This node fails"))
            }
        }

        val propagateMiddleware = object : Middleware {
            override suspend fun onError(err: Throwable, ctx: RunContext): ErrorAction {
                return ErrorAction.PROPAGATE
            }
        }

        val graph = Graph(
            id = "propagate-graph",
            nodes = mapOf(
                "failing-node" to failingNode
            ),
            edges = emptyList(),
            entryPoint = "failing-node",
            middleware = listOf(propagateMiddleware)
        )

        // When: Run graph
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, emptyMap())

        // Then: Should fail
        assertTrue(result.isFailure)
    }

    @Test
    fun `test middleware chain with different error actions`() = runTest {
        // Given: Graph with multiple nodes using different error actions
        var node1Attempts = 0
        val retryNode = object : Node {
            override val id = "retry-node"

            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                node1Attempts++
                return if (node1Attempts < 2) {
                    SpiceResult.failure(SpiceError.AgentError("Retry needed"))
                } else {
                    SpiceResult.success(NodeResult(data = "retry-success"))
                }
            }
        }

        val skipNode = object : Node {
            override val id = "skip-node"

            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.failure(SpiceError.AgentError("Skip this"))
            }
        }

        val smartMiddleware = object : Middleware {
            override suspend fun onError(err: Throwable, ctx: RunContext): ErrorAction {
                // Retry on "retry-node", skip on "skip-node"
                return when {
                    err.message?.contains("Retry needed") == true -> ErrorAction.RETRY
                    err.message?.contains("Skip this") == true -> ErrorAction.SKIP
                    else -> ErrorAction.PROPAGATE
                }
            }
        }

        val graph = Graph(
            id = "mixed-action-graph",
            nodes = mapOf(
                "retry-node" to retryNode,
                "skip-node" to skipNode,
                "final" to OutputNode("final")
            ),
            edges = listOf(
                Edge("retry-node", "skip-node"),
                Edge("skip-node", "final")
            ),
            entryPoint = "retry-node",
            middleware = listOf(smartMiddleware)
        )

        // When: Run graph
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, emptyMap())

        // Then: Should succeed with mixed statuses
        assertTrue(result.isSuccess)
        val report = result.getOrThrow()
        assertEquals(3, report.nodeReports.size)

        // Verify retry node succeeded after retry
        val retryReport = report.nodeReports.find { it.nodeId == "retry-node" }
        assertEquals(NodeStatus.SUCCESS, retryReport?.status)
        assertEquals(2, node1Attempts)

        // Verify skip node was skipped
        val skipReport = report.nodeReports.find { it.nodeId == "skip-node" }
        assertEquals(NodeStatus.SKIPPED, skipReport?.status)

        // Verify final node executed
        val finalReport = report.nodeReports.find { it.nodeId == "final" }
        assertEquals(NodeStatus.SUCCESS, finalReport?.status)
    }
}
