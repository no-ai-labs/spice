package io.github.noailabs.spice.graph

import io.github.noailabs.spice.ExecutionContext
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.graph.dsl.graph
import io.github.noailabs.spice.graph.merge.AggregationFunction
import io.github.noailabs.spice.graph.merge.MergePolicy
import io.github.noailabs.spice.graph.nodes.MergeNode
import io.github.noailabs.spice.graph.nodes.MergeStrategies
import io.github.noailabs.spice.graph.nodes.OutputNode
import io.github.noailabs.spice.graph.nodes.ParallelNode
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParallelNodeTest {

    @Test
    fun `test ParallelNode executes branches concurrently`() = runTest {
        // Given: Simple nodes that return different values
        val nodeA = TestNode("branch-a", "Result A")
        val nodeB = TestNode("branch-b", "Result B")
        val nodeC = TestNode("branch-c", "Result C")

        val parallelNode = ParallelNode(
            id = "parallel",
            branches = mapOf(
                "a" to nodeA,
                "b" to nodeB,
                "c" to nodeC
            )
        )

        // When: Execute parallel node
        val ctx = NodeContext.create(
            graphId = "test-graph",
            state = emptyMap(),
            context = ExecutionContext.of(emptyMap())
        )
        val result = parallelNode.run(ctx)

        // Then: All results collected
        assertTrue(result.isSuccess)
        val data = (result as SpiceResult.Success).value.data as Map<*, *>
        assertEquals("Result A", data["a"])
        assertEquals("Result B", data["b"])
        assertEquals("Result C", data["c"])
    }

    @Test
    fun `test ParallelNode with Namespace metadata merge`() = runTest {
        // Given: Nodes that set metadata
        val nodeA = TestNode("branch-a", "A", metadata = mapOf("confidence" to 0.8))
        val nodeB = TestNode("branch-b", "B", metadata = mapOf("confidence" to 0.6))

        val parallelNode = ParallelNode(
            id = "parallel",
            branches = mapOf("a" to nodeA, "b" to nodeB),
            mergePolicy = MergePolicy.Namespace
        )

        // When: Execute
        val ctx = NodeContext.create(graphId = "test", state = emptyMap(), context = ExecutionContext.of(emptyMap()))
        val result = parallelNode.run(ctx)

        // Then: Metadata namespaced
        assertTrue(result.isSuccess)
        val metadata = (result as SpiceResult.Success).value.metadata
        assertEquals(0.8, metadata["parallel.parallel.a.confidence"])
        assertEquals(0.6, metadata["parallel.parallel.b.confidence"])
    }

    @Test
    fun `test ParallelNode with LastWrite metadata merge`() = runTest {
        // Given: Nodes with conflicting metadata
        val nodeA = TestNode("a", "A", metadata = mapOf("status" to "done-a"))
        val nodeB = TestNode("b", "B", metadata = mapOf("status" to "done-b"))

        val parallelNode = ParallelNode(
            id = "parallel",
            branches = mapOf("a" to nodeA, "b" to nodeB),
            mergePolicy = MergePolicy.LastWrite
        )

        // When: Execute
        val ctx = NodeContext.create(graphId = "test", state = emptyMap(), context = ExecutionContext.of(emptyMap()))
        val result = parallelNode.run(ctx)

        // Then: Last write wins
        assertTrue(result.isSuccess)
        val metadata = (result as SpiceResult.Success).value.metadata
        // Last write depends on iteration order, but one should win
        assertTrue(metadata["status"] == "done-a" || metadata["status"] == "done-b")
    }

    @Test
    fun `test ParallelNode with Custom aggregation`() = runTest {
        // Given: Nodes with numeric confidence scores
        val nodeA = TestNode("a", "A", metadata = mapOf("confidence" to 0.8, "label" to "cat"))
        val nodeB = TestNode("b", "B", metadata = mapOf("confidence" to 0.6, "label" to "cat"))
        val nodeC = TestNode("c", "C", metadata = mapOf("confidence" to 0.9, "label" to "dog"))

        val parallelNode = ParallelNode(
            id = "parallel",
            branches = mapOf("a" to nodeA, "b" to nodeB, "c" to nodeC),
            mergePolicy = MergePolicy.Custom(
                aggregators = mapOf(
                    "confidence" to AggregationFunction.AVERAGE,
                    "label" to AggregationFunction.VOTE
                )
            )
        )

        // When: Execute
        val ctx = NodeContext.create(graphId = "test", state = emptyMap(), context = ExecutionContext.of(emptyMap()))
        val result = parallelNode.run(ctx)

        // Then: Aggregated correctly
        assertTrue(result.isSuccess)
        val metadata = (result as SpiceResult.Success).value.metadata

        // Average confidence: (0.8 + 0.6 + 0.9) / 3 = 0.7666...
        val avgConfidence = metadata["confidence"] as Double
        assertTrue(avgConfidence > 0.76 && avgConfidence < 0.77)

        // Vote for label: "cat" appears twice, "dog" once
        assertEquals("cat", metadata["label"])
    }

    @Test
    fun `test MergeNode collects and merges parallel results`() = runTest {
        // Given: Graph with ParallelNode and MergeNode
        val graph = graph("parallel-merge-test") {
            parallel(
                id = "parallel",
                branches = mapOf(
                    "fetch" to TestNode("fetch", mapOf("items" to listOf(1, 2, 3))),
                    "validate" to TestNode("validate", true),
                    "transform" to TestNode("transform", "TRANSFORMED")
                )
            )

            merge(
                id = "aggregate",
                parallelNodeId = "parallel"
            ) { results ->
                mapOf(
                    "fetchedData" to results["fetch"],
                    "isValid" to results["validate"],
                    "transformed" to results["transform"]
                )
            }

            output("result") { it.state["aggregate"] }
        }

        // When: Execute graph
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, emptyMap())

        // Then: Results merged correctly
        assertTrue(result.isSuccess)
        val output = result.getOrThrow().result as Map<*, *>
        assertEquals(mapOf("items" to listOf(1, 2, 3)), output["fetchedData"])
        assertEquals(true, output["isValid"])
        assertEquals("TRANSFORMED", output["transformed"])
    }

    @Test
    fun `test MergeStrategies vote`() = runTest {
        // Given: Multiple agents voting
        val graph = graph("voting-test") {
            parallel(
                id = "voters",
                branches = mapOf(
                    "agent-a" to TestNode("a", "cat"),
                    "agent-b" to TestNode("b", "cat"),
                    "agent-c" to TestNode("c", "dog")
                )
            )

            merge(
                id = "vote-result",
                parallelNodeId = "voters",
                merger = MergeStrategies.vote
            )

            output("result") { it.state["vote-result"] }
        }

        // When: Execute
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, emptyMap())

        // Then: "cat" wins (2 votes vs 1)
        assertTrue(result.isSuccess)
        assertEquals("cat", result.getOrThrow().result)
    }

    @Test
    fun `test MergeStrategies average`() = runTest {
        // Given: Numeric results
        val graph = graph("average-test") {
            parallel(
                id = "scorers",
                branches = mapOf(
                    "scorer-a" to TestNode("a", 0.8),
                    "scorer-b" to TestNode("b", 0.6),
                    "scorer-c" to TestNode("c", 0.9)
                )
            )

            merge(
                id = "average-score",
                parallelNodeId = "scorers",
                merger = MergeStrategies.average
            )

            output("result") { it.state["average-score"] }
        }

        // When: Execute
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, emptyMap())

        // Then: Average = (0.8 + 0.6 + 0.9) / 3 = 0.7666...
        assertTrue(result.isSuccess)
        val avg = result.getOrThrow().result as Double
        assertTrue(avg > 0.76 && avg < 0.77)
    }

    @Test
    fun `test ParallelNode failFast on error`() = runTest {
        // Given: One failing branch
        val nodeA = TestNode("a", "success")
        val nodeB = FailingNode("b", "intentional failure")
        val nodeC = TestNode("c", "success")

        val parallelNode = ParallelNode(
            id = "parallel",
            branches = mapOf(
                "a" to nodeA,
                "b" to nodeB,
                "c" to nodeC
            ),
            failFast = true
        )

        // When: Execute
        val ctx = NodeContext.create(graphId = "test", state = emptyMap(), context = ExecutionContext.of(emptyMap()))
        val result = parallelNode.run(ctx)

        // Then: Entire parallel execution fails
        assertTrue(result.isFailure)
    }

    @Test
    fun `test ParallelNode with complex workflow`() = runTest {
        // Given: Real-world data processing workflow
        val graph = graph("data-processing") {
            // Parallel data collection
            parallel(
                id = "collect",
                branches = mapOf(
                    "api-data" to TestNode("api", mapOf("source" to "api", "count" to 100)),
                    "db-data" to TestNode("db", mapOf("source" to "db", "count" to 200)),
                    "file-data" to TestNode("file", mapOf("source" to "file", "count" to 50))
                )
            )

            // Merge collected data
            merge(
                id = "combine",
                parallelNodeId = "collect"
            ) { results ->
                val totalCount = results.values
                    .filterIsInstance<Map<*, *>>()
                    .sumOf { (it["count"] as? Int) ?: 0 }

                mapOf(
                    "sources" to results.keys.toList(),
                    "totalRecords" to totalCount,
                    "details" to results
                )
            }

            output("result") { it.state["combine"] }
        }

        // When: Execute
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, emptyMap())

        // Then: Data merged correctly
        assertTrue(result.isSuccess)
        val output = result.getOrThrow().result as Map<*, *>
        assertEquals(350, output["totalRecords"])
        assertEquals(3, (output["sources"] as List<*>).size)
    }

    // Helper test node
    private class TestNode(
        override val id: String,
        private val output: Any?,
        private val metadata: Map<String, Any> = emptyMap()
    ) : Node {
        override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
            return SpiceResult.success(
                NodeResult.fromContext(
                    ctx = ctx,
                    data = output,
                    additional = metadata
                )
            )
        }
    }

    // Helper failing node
    private class FailingNode(
        override val id: String,
        private val errorMessage: String
    ) : Node {
        override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
            return SpiceResult.failure(
                SpiceError.UnknownError(
                    message = errorMessage,
                    cause = Exception(errorMessage)
                )
            )
        }
    }
}
