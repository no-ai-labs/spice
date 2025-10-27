package io.github.noailabs.spice.graph

import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.checkpoint.CheckpointConfig
import io.github.noailabs.spice.graph.checkpoint.InMemoryCheckpointStore
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EdgeCaseTest {

    @Test
    fun `test graph with null node output`() = runTest {
        // Given: Node that returns null
        val nullNode = object : Node {
            override val id = "null-node"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.success(NodeResult(data = null))
            }
        }

        val graph = Graph(
            id = "null-graph",
            nodes = mapOf(
                "null-node" to nullNode,
                "next" to OutputNode("next")
            ),
            edges = listOf(Edge("null-node", "next")),
            entryPoint = "null-node"
        )

        // When: Run graph
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, emptyMap())

        // Then: Should succeed with null values
        assertTrue(result.isSuccess)
        val report = result.getOrThrow()
        assertNull(report.nodeReports.find { it.nodeId == "null-node" }?.output)
    }

    @Test
    fun `test graph with empty input map`() = runTest {
        // Given: Graph with no initial input
        val graph = Graph(
            id = "empty-input-graph",
            nodes = mapOf("output" to OutputNode("output")),
            edges = emptyList(),
            entryPoint = "output"
        )

        // When: Run with empty input
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, emptyMap())

        // Then: Should succeed
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test conditional edge that never matches`() = runTest {
        // Given: Graph with conditional edge that never passes
        val node1 = object : Node {
            override val id = "node1"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.success(NodeResult(data = "result"))
            }
        }

        val graph = Graph(
            id = "no-match-graph",
            nodes = mapOf(
                "node1" to node1,
                "node2" to OutputNode("node2")
            ),
            edges = listOf(
                Edge("node1", "node2") { result ->
                    // Condition that never matches
                    result.data == "impossible"
                }
            ),
            entryPoint = "node1"
        )

        // When: Run graph
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, emptyMap())

        // Then: Should succeed, stopping after node1
        assertTrue(result.isSuccess)
        val report = result.getOrThrow()
        assertEquals(1, report.nodeReports.size)
        assertEquals("node1", report.nodeReports[0].nodeId)
    }

    @Test
    fun `test multiple conditional edges from same node`() = runTest {
        // Given: Node with multiple outgoing edges
        val router = object : Node {
            override val id = "router"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.success(NodeResult(data = "route-b"))
            }
        }

        val graph = Graph(
            id = "multi-edge-graph",
            nodes = mapOf(
                "router" to router,
                "route-a" to OutputNode("route-a"),
                "route-b" to OutputNode("route-b"),
                "route-c" to OutputNode("route-c")
            ),
            edges = listOf(
                Edge("router", "route-a") { it.data == "route-a" },
                Edge("router", "route-b") { it.data == "route-b" },
                Edge("router", "route-c") { it.data == "route-c" }
            ),
            entryPoint = "router"
        )

        // When: Run graph
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, emptyMap())

        // Then: Should follow route-b only (first matching edge)
        assertTrue(result.isSuccess)
        val report = result.getOrThrow()
        assertEquals(2, report.nodeReports.size)
        assertNotNull(report.nodeReports.find { it.nodeId == "route-b" })
    }

    @Test
    fun `test node modifying shared state`() = runTest {
        // Given: Nodes that read and modify shared state
        val counter = object : Node {
            override val id = "counter"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                val current = (ctx.state["count"] as? Int) ?: 0
                ctx.state["count"] = current + 1
                return SpiceResult.success(NodeResult(data = current + 1))
            }
        }

        val graph = Graph(
            id = "state-graph",
            nodes = mapOf(
                "counter1" to counter,
                "counter2" to counter,
                "counter3" to counter,
                "output" to OutputNode("output")
            ),
            edges = listOf(
                Edge("counter1", "counter2"),
                Edge("counter2", "counter3"),
                Edge("counter3", "output")
            ),
            entryPoint = "counter1"
        )

        // When: Run graph
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, mapOf("count" to 0))

        // Then: State should be modified sequentially
        assertTrue(result.isSuccess)
        val report = result.getOrThrow()
        assertEquals(1, report.nodeReports[0].output)
        assertEquals(2, report.nodeReports[1].output)
        assertEquals(3, report.nodeReports[2].output)
    }

    @Test
    fun `test SKIP on entry point node`() = runTest {
        // Given: Entry point that fails with SKIP middleware
        val failingEntry = object : Node {
            override val id = "entry"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.failure(SpiceError.AgentError("Entry fails"))
            }
        }

        val skipMiddleware = object : Middleware {
            override suspend fun onError(err: Throwable, ctx: RunContext): ErrorAction {
                return ErrorAction.SKIP
            }
        }

        val graph = Graph(
            id = "skip-entry-graph",
            nodes = mapOf(
                "entry" to failingEntry,
                "next" to OutputNode("next")
            ),
            edges = listOf(Edge("entry", "next")),
            entryPoint = "entry",
            middleware = listOf(skipMiddleware)
        )

        // When: Run graph
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, emptyMap())

        // Then: Should succeed, skipping entry and continuing
        assertTrue(result.isSuccess)
        val report = result.getOrThrow()
        assertEquals(NodeStatus.SKIPPED, report.nodeReports[0].status)
        assertEquals(NodeStatus.SUCCESS, report.nodeReports[1].status)
    }

    @Test
    fun `test all nodes get skipped`() = runTest {
        // Given: All nodes fail with SKIP middleware
        val failingNode1 = object : Node {
            override val id = "fail1"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.failure(SpiceError.AgentError("Fail 1"))
            }
        }

        val failingNode2 = object : Node {
            override val id = "fail2"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.failure(SpiceError.AgentError("Fail 2"))
            }
        }

        val skipMiddleware = object : Middleware {
            override suspend fun onError(err: Throwable, ctx: RunContext): ErrorAction {
                return ErrorAction.SKIP
            }
        }

        val graph = Graph(
            id = "all-skip-graph",
            nodes = mapOf(
                "fail1" to failingNode1,
                "fail2" to failingNode2
            ),
            edges = listOf(Edge("fail1", "fail2")),
            entryPoint = "fail1",
            middleware = listOf(skipMiddleware)
        )

        // When: Run graph
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, emptyMap())

        // Then: Should succeed with all nodes skipped
        assertTrue(result.isSuccess)
        val report = result.getOrThrow()
        assertEquals(2, report.nodeReports.size)
        assertTrue(report.nodeReports.all { it.status == NodeStatus.SKIPPED })
    }

    @Test
    fun `test checkpoint with null values in state`() = runTest {
        // Given: Graph that stores null values
        val nullNode = object : Node {
            override val id = "null-node"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                ctx.state["nullValue"] = null
                ctx.state["actualValue"] = "data"
                return SpiceResult.success(NodeResult(data = null))
            }
        }

        val graph = Graph(
            id = "null-checkpoint-graph",
            nodes = mapOf(
                "null-node" to nullNode,
                "next" to OutputNode("next")
            ),
            edges = listOf(Edge("null-node", "next")),
            entryPoint = "null-node"
        )

        // When: Run with checkpoint
        val store = InMemoryCheckpointStore()
        val runner = DefaultGraphRunner()
        val result = runner.runWithCheckpoint(
            graph,
            emptyMap(),
            store,
            CheckpointConfig(saveEveryNNodes = 1)
        )

        // Then: Should succeed
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test resume with invalid checkpoint ID`() = runTest {
        // Given: Invalid checkpoint ID
        val graph = Graph(
            id = "resume-graph",
            nodes = mapOf("output" to OutputNode("output")),
            edges = emptyList(),
            entryPoint = "output"
        )

        val store = InMemoryCheckpointStore()
        val runner = DefaultGraphRunner()

        // When: Try to resume with non-existent checkpoint
        val result = runner.resume(graph, "invalid-checkpoint-id", store)

        // Then: Should fail
        assertTrue(result.isFailure)
        when (result) {
            is SpiceResult.Failure -> assertTrue(result.error.message.contains("not found"))
            is SpiceResult.Success -> throw AssertionError("Expected failure but got success")
        }
    }

    @Test
    fun `test deeply nested retries`() = runTest {
        // Given: Node that succeeds on 10th attempt
        var attemptCount = 0
        val deepRetryNode = object : Node {
            override val id = "deep-retry"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                attemptCount++
                return if (attemptCount < 4) {  // Will succeed on 4th attempt (max retries is 3)
                    SpiceResult.failure(SpiceError.AgentError("Attempt $attemptCount"))
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
            id = "deep-retry-graph",
            nodes = mapOf("deep-retry" to deepRetryNode),
            edges = emptyList(),
            entryPoint = "deep-retry",
            middleware = listOf(retryMiddleware)
        )

        // When: Run graph
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, emptyMap())

        // Then: Should succeed after multiple retries
        assertTrue(result.isSuccess)
        assertEquals(4, attemptCount)  // 1 initial + 3 retries
    }

    @Test
    fun `test middleware collecting node execution count`() = runTest {
        // Given: Middleware that counts node executions
        val nodeExecutionCount = mutableMapOf<String, Int>()

        val countingMiddleware = object : Middleware {
            override suspend fun onNode(
                req: NodeRequest,
                next: suspend (NodeRequest) -> SpiceResult<NodeResult>
            ): SpiceResult<NodeResult> {
                nodeExecutionCount[req.nodeId] = (nodeExecutionCount[req.nodeId] ?: 0) + 1
                return next(req)
            }
        }

        val graph = Graph(
            id = "counting-graph",
            nodes = mapOf(
                "node1" to OutputNode("node1"),
                "node2" to OutputNode("node2"),
                "node3" to OutputNode("node3")
            ),
            edges = listOf(
                Edge("node1", "node2"),
                Edge("node2", "node3")
            ),
            entryPoint = "node1",
            middleware = listOf(countingMiddleware)
        )

        // When: Run graph
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, emptyMap())

        // Then: All nodes should be counted
        assertTrue(result.isSuccess)
        assertEquals(1, nodeExecutionCount["node1"])
        assertEquals(1, nodeExecutionCount["node2"])
        assertEquals(1, nodeExecutionCount["node3"])
    }

    @Test
    fun `test single node graph`() = runTest {
        // Given: Graph with only one node
        val graph = Graph(
            id = "single-node-graph",
            nodes = mapOf("only" to OutputNode("only")),
            edges = emptyList(),
            entryPoint = "only"
        )

        // When: Run graph
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, emptyMap())

        // Then: Should succeed
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().nodeReports.size)
    }

    @Test
    fun `test graph with large state map`() = runTest {
        // Given: Node that creates large state
        val largeStateNode = object : Node {
            override val id = "large-state"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                // Add 100 items to state
                repeat(100) { i ->
                    ctx.state["item_$i"] = "value_$i"
                }
                return SpiceResult.success(NodeResult(data = "done"))
            }
        }

        val graph = Graph(
            id = "large-state-graph",
            nodes = mapOf("large-state" to largeStateNode),
            edges = emptyList(),
            entryPoint = "large-state"
        )

        // When: Run graph
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, emptyMap())

        // Then: Should succeed
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test checkpoint during retry attempt`() = runTest {
        // Given: Failing node with checkpoint and retry
        var attemptCount = 0
        val retryNode = object : Node {
            override val id = "retry-checkpoint"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                attemptCount++
                return if (attemptCount < 2) {
                    SpiceResult.failure(SpiceError.AgentError("Retry needed"))
                } else {
                    SpiceResult.success(NodeResult(data = "success"))
                }
            }
        }

        val retryMiddleware = object : Middleware {
            override suspend fun onError(err: Throwable, ctx: RunContext): ErrorAction {
                return ErrorAction.RETRY
            }
        }

        val graph = Graph(
            id = "retry-checkpoint-graph",
            nodes = mapOf("retry-checkpoint" to retryNode),
            edges = emptyList(),
            entryPoint = "retry-checkpoint",
            middleware = listOf(retryMiddleware)
        )

        // When: Run with checkpoint on error
        val store = InMemoryCheckpointStore()
        val runner = DefaultGraphRunner()
        val result = runner.runWithCheckpoint(
            graph,
            emptyMap(),
            store,
            CheckpointConfig(saveOnError = true)
        )

        // Then: Should succeed after retry
        assertTrue(result.isSuccess)
        assertEquals(2, attemptCount)
    }

    @Test
    fun `test node that throws exception instead of returning failure`() = runTest {
        // Given: Node that throws exception
        val throwingNode = object : Node {
            override val id = "throwing"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                throw IllegalStateException("Unexpected error")
            }
        }

        val graph = Graph(
            id = "throwing-graph",
            nodes = mapOf("throwing" to throwingNode),
            edges = emptyList(),
            entryPoint = "throwing"
        )

        // When: Run graph
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, emptyMap())

        // Then: Should fail
        assertTrue(result.isFailure)
    }
}
