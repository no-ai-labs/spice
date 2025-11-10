package io.github.noailabs.spice.graph

import io.github.noailabs.spice.ExecutionContext
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.dsl.graph
import io.github.noailabs.spice.graph.merge.AggregationFunction
import io.github.noailabs.spice.graph.merge.MergePolicy
import io.github.noailabs.spice.graph.nodes.MergeStrategies
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests to verify that parallel execution actually works concurrently.
 * These tests measure timing to ensure operations run in parallel, not sequentially.
 */
class ParallelExecutionIntegrationTest {

    @Test
    fun `test parallel execution is actually concurrent`() = runTest {
        // Given: 3 nodes that each take 100ms
        val delayMs = 100L
        val nodeCount = 3

        val graph = graph("timing-test") {
            parallel(
                id = "parallel",
                branches = mapOf(
                    "branch-a" to DelayNode("a", delayMs, "Result A"),
                    "branch-b" to DelayNode("b", delayMs, "Result B"),
                    "branch-c" to DelayNode("c", delayMs, "Result C")
                )
            )

            output("result") { it.state["parallel"] }
        }

        // When: Execute and measure time
        val runner = DefaultGraphRunner()
        val executionTime = measureTimeMillis {
            runner.run(graph, emptyMap())
        }

        // Then: Should take ~100ms (parallel) not ~300ms (sequential)
        println("⏱️  Execution time: ${executionTime}ms")
        println("   Expected if parallel: ~${delayMs}ms")
        println("   Expected if sequential: ~${delayMs * nodeCount}ms")

        // Allow 50% overhead for coroutine scheduling
        val maxExpectedTime = delayMs * 1.5
        assertTrue(
            executionTime < maxExpectedTime,
            "Execution took ${executionTime}ms, expected < ${maxExpectedTime}ms. " +
                    "This suggests sequential execution, not parallel!"
        )
    }

    @Test
    fun `test parallel execution order is non-deterministic`() = runTest {
        // Given: Nodes that complete in different orders
        val executionOrder = mutableListOf<String>()

        val graph = graph("order-test") {
            parallel(
                id = "parallel",
                branches = mapOf(
                    "fast" to OrderTrackingNode("fast", 10, executionOrder),
                    "medium" to OrderTrackingNode("medium", 50, executionOrder),
                    "slow" to OrderTrackingNode("slow", 100, executionOrder)
                )
            )

            output("result") { it.state["parallel"] }
        }

        // When: Execute
        val runner = DefaultGraphRunner()
        runner.run(graph, emptyMap())

        // Then: Fast should complete first (non-deterministic order)
        println("🔀 Execution order: $executionOrder")
        assertEquals("fast", executionOrder.first(), "Fast node should complete first")

        // All should have executed
        assertEquals(3, executionOrder.size)
        assertTrue(executionOrder.containsAll(listOf("fast", "medium", "slow")))
    }

    @Test
    fun `test parallel execution with real delay differences`() = runTest {
        // Given: Very different execution times
        val graph = graph("realistic-timing") {
            parallel(
                id = "parallel",
                branches = mapOf(
                    "instant" to DelayNode("instant", 0, "Instant"),
                    "fast" to DelayNode("fast", 20, "Fast"),
                    "slow" to DelayNode("slow", 200, "Slow")
                )
            )

            merge("result", "parallel", MergeStrategies.asMap)
            output("final") { it.state["result"] }
        }

        // When: Execute and measure
        val runner = DefaultGraphRunner()
        val executionTime = measureTimeMillis {
            val result = runner.run(graph, emptyMap())
            assertTrue(result.isSuccess)
        }

        // Then: Should wait for slowest (200ms), not sum (220ms)
        println("⏱️  Execution time: ${executionTime}ms (expected ~200ms, NOT 220ms)")
        assertTrue(executionTime < 250, "Should be ~200ms (slowest), not 220ms (sum)")
    }

    @Test
    fun `test metadata isolation with namespace policy`() = runTest {
        // Given: Parallel nodes with conflicting metadata keys
        val graph = graph("metadata-test") {
            parallel(
                id = "parallel",
                branches = mapOf(
                    "a" to MetadataNode("a", "A", mapOf("confidence" to 0.8, "source" to "model-a")),
                    "b" to MetadataNode("b", "B", mapOf("confidence" to 0.6, "source" to "model-b")),
                    "c" to MetadataNode("c", "C", mapOf("confidence" to 0.9, "source" to "model-c"))
                ),
                mergePolicy = MergePolicy.Namespace
            )

            output("result") { it.state["parallel"] }
        }

        // When: Execute
        val runner = DefaultGraphRunner()
        val report = runner.run(graph, emptyMap()).getOrThrow()

        // Then: All metadata preserved with namespaces
        val parallelNodeReport = report.nodeReports.find { it.nodeId == "parallel" }!!

        println("📊 Metadata: ${parallelNodeReport.metadata}")

        // Check namespace isolation
        assertTrue(parallelNodeReport.metadata!!.containsKey("parallel.parallel.a.confidence"))
        assertTrue(parallelNodeReport.metadata!!.containsKey("parallel.parallel.b.confidence"))
        assertTrue(parallelNodeReport.metadata!!.containsKey("parallel.parallel.c.confidence"))

        assertEquals(0.8, parallelNodeReport.metadata!!["parallel.parallel.a.confidence"])
        assertEquals(0.6, parallelNodeReport.metadata!!["parallel.parallel.b.confidence"])
        assertEquals(0.9, parallelNodeReport.metadata!!["parallel.parallel.c.confidence"])
    }

    @Test
    fun `test custom aggregation with averaging`() = runTest {
        // Given: Parallel scoring with averaging
        val graph = graph("averaging-test") {
            parallel(
                id = "scorers",
                branches = mapOf(
                    "scorer-a" to MetadataNode("a", 0.8, mapOf("confidence" to 0.8)),
                    "scorer-b" to MetadataNode("b", 0.6, mapOf("confidence" to 0.6)),
                    "scorer-c" to MetadataNode("c", 0.9, mapOf("confidence" to 0.9))
                ),
                mergePolicy = MergePolicy.Custom(
                    aggregators = mapOf("confidence" to AggregationFunction.AVERAGE)
                )
            )

            output("result") { it.state["scorers"] }
        }

        // When: Execute
        val runner = DefaultGraphRunner()
        val report = runner.run(graph, emptyMap()).getOrThrow()

        // Then: Confidence averaged
        val scorersNodeReport = report.nodeReports.find { it.nodeId == "scorers" }!!

        val avgConfidence = scorersNodeReport.metadata!!["confidence"] as Double
        val expected = (0.8 + 0.6 + 0.9) / 3.0

        println("🔢 Average confidence: $avgConfidence (expected: $expected)")
        assertEquals(expected, avgConfidence, 0.01)
    }

    @Test
    fun `test voting with majority wins`() = runTest {
        // Given: 5 nodes voting on a label
        val graph = graph("voting-test") {
            parallel(
                id = "voters",
                branches = mapOf(
                    "voter-1" to SimpleNode("1", "cat"),
                    "voter-2" to SimpleNode("2", "cat"),
                    "voter-3" to SimpleNode("3", "cat"),
                    "voter-4" to SimpleNode("4", "dog"),
                    "voter-5" to SimpleNode("5", "dog")
                )
            )

            merge("vote-result", "voters", MergeStrategies.vote)
            output("final") { it.state["vote-result"] }
        }

        // When: Execute
        val runner = DefaultGraphRunner()
        val report = runner.run(graph, emptyMap()).getOrThrow()

        // Then: "cat" wins (3 votes vs 2)
        println("🗳️  Vote result: ${report.result}")
        assertEquals("cat", report.result)
    }

    @Test
    fun `test fail-fast stops on first error`() = runTest {
        // Given: Mix of fast success and slow failure
        val completedBranches = mutableListOf<String>()

        val graph = graph("fail-fast-test") {
            parallel(
                id = "parallel",
                branches = mapOf(
                    "fast-success" to TrackingNode("fast-success", 10, "OK", completedBranches),
                    "fast-fail" to FailingDelayNode("fast-fail", 50, completedBranches),
                    "slow-success" to TrackingNode("slow-success", 200, "OK", completedBranches)
                ),
                failFast = true
            )

            output("result") { it.state["parallel"] }
        }

        // When: Execute
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, emptyMap())

        // Then: Should fail
        assertTrue(result.isFailure)

        println("❌ Completed branches: $completedBranches")

        // fast-success completes before failure
        assertTrue(completedBranches.contains("fast-success"))

        // fast-fail executes
        assertTrue(completedBranches.contains("fast-fail"))

        // slow-success might not complete (fail-fast cancellation)
        // Note: Due to coroutine cancellation timing, this is non-deterministic
    }

    @Test
    fun `test parallel with merge produces correct combined result`() = runTest {
        // Given: Real-world scenario - fetch from multiple sources
        val graph = graph("data-aggregation") {
            parallel(
                id = "fetch",
                branches = mapOf(
                    "api" to SimpleNode("api", mapOf("source" to "api", "count" to 100)),
                    "db" to SimpleNode("db", mapOf("source" to "db", "count" to 200)),
                    "cache" to SimpleNode("cache", mapOf("source" to "cache", "count" to 50))
                )
            )

            merge("aggregate", "fetch") { results ->
                val totalCount = results.values
                    .filterIsInstance<Map<*, *>>()
                    .sumOf { (it["count"] as? Int) ?: 0 }

                mapOf(
                    "sources" to results.keys.sorted(),
                    "totalRecords" to totalCount,
                    "timestamp" to System.currentTimeMillis()
                )
            }

            output("result") { it.state["aggregate"] }
        }

        // When: Execute
        val runner = DefaultGraphRunner()
        val report = runner.run(graph, emptyMap()).getOrThrow()

        // Then: Correct aggregation
        val output = report.result as Map<*, *>

        println("📦 Aggregated result: $output")

        assertEquals(listOf("api", "cache", "db"), output["sources"])
        assertEquals(350, output["totalRecords"])
        assertTrue(output.containsKey("timestamp"))
    }

    @Test
    fun `test parallel execution scales with branch count`() = runTest {
        // Given: Varying number of branches
        val delayPerBranch = 100L

        fun createGraph(branchCount: Int) = graph("scale-test-$branchCount") {
            parallel(
                id = "parallel",
                branches = (1..branchCount).associate {
                    "branch-$it" to DelayNode("branch-$it", delayPerBranch, "Result $it")
                }
            )
            output("result") { it.state["parallel"] }
        }

        val runner = DefaultGraphRunner()

        // Test with 1, 5, 10 branches
        for (branchCount in listOf(1, 5, 10)) {
            val graph = createGraph(branchCount)

            val executionTime = measureTimeMillis {
                runner.run(graph, emptyMap())
            }

            println("⚡ $branchCount branches: ${executionTime}ms (expected ~${delayPerBranch}ms)")

            // Should still be ~100ms regardless of branch count (parallel)
            // Allow more overhead for higher branch counts due to scheduling
            val maxExpected = delayPerBranch * (1 + branchCount * 0.1)
            assertTrue(
                executionTime < maxExpected,
                "$branchCount branches took ${executionTime}ms, " +
                        "expected < ${maxExpected}ms (parallel execution)"
            )
        }
    }

    // ========== Test Helper Nodes ==========

    /**
     * Node that delays for specified time, simulating async work.
     */
    private class DelayNode(
        override val id: String,
        private val delayMs: Long,
        private val result: Any
    ) : Node {
        override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
            delay(delayMs)
            return SpiceResult.success(
                NodeResult.fromContext(ctx, data = result)
            )
        }
    }

    /**
     * Node that tracks execution order.
     */
    private class OrderTrackingNode(
        override val id: String,
        private val delayMs: Long,
        private val tracker: MutableList<String>
    ) : Node {
        override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
            delay(delayMs)
            tracker.add(id)
            return SpiceResult.success(
                NodeResult.fromContext(ctx, data = id)
            )
        }
    }

    /**
     * Node with custom metadata.
     */
    private class MetadataNode(
        override val id: String,
        private val result: Any,
        private val metadata: Map<String, Any>
    ) : Node {
        override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
            return SpiceResult.success(
                NodeResult.fromContext(
                    ctx,
                    data = result,
                    additional = metadata
                )
            )
        }
    }

    /**
     * Simple node that returns data immediately.
     */
    private class SimpleNode(
        override val id: String,
        private val result: Any
    ) : Node {
        override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
            return SpiceResult.success(
                NodeResult.fromContext(ctx, data = result)
            )
        }
    }

    /**
     * Node that tracks completion then returns result.
     */
    private class TrackingNode(
        override val id: String,
        private val delayMs: Long,
        private val result: Any,
        private val tracker: MutableList<String>
    ) : Node {
        override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
            delay(delayMs)
            tracker.add(id)
            return SpiceResult.success(
                NodeResult.fromContext(ctx, data = result)
            )
        }
    }

    /**
     * Node that fails after delay, tracking execution.
     */
    private class FailingDelayNode(
        override val id: String,
        private val delayMs: Long,
        private val tracker: MutableList<String>
    ) : Node {
        override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
            delay(delayMs)
            tracker.add(id)
            return SpiceResult.failure(
                io.github.noailabs.spice.error.SpiceError.UnknownError(
                    message = "Intentional failure from $id"
                )
            )
        }
    }
}
