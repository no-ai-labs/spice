package io.github.noailabs.spice.graph

import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.dsl.graph
import io.github.noailabs.spice.graph.nodes.OutputNode
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import io.github.noailabs.spice.graph.runner.RunStatus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for edge enhancements:
 * - Default edges (fallback)
 * - Edge priority
 * - Multiple conditions (OR/AND)
 * - Metadata helpers
 * - Dynamic routing (NodeResult.nextEdges)
 * - Wildcard matching
 */
class EdgeEnhancementsTest {

    @Test
    fun `test default edge fallback when no condition matches`() = runTest {
        // Given: Graph with conditional edges and a default edge
        val testNode = object : Node {
            override val id = "test"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.success(
                    NodeResult.fromContext(ctx, data = "unexpected")
                )
            }
        }

        val fallbackNode = object : Node {
            override val id = "fallback"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.success(
                    NodeResult.fromContext(ctx, data = "fallback executed")
                )
            }
        }

        val graph = graph("default-edge-test") {
            node(testNode)
            node(fallbackNode)
            output("output")

            // Conditional edge that won't match
            edge("test", "output", priority = 1) { result ->
                result.data == "expected"
            }

            // Default edge as fallback
            defaultEdge("test", "fallback")
            edge("fallback", "output")
        }

        // When: Run graph
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, emptyMap()).getOrThrow()

        // Then: Should use fallback edge
        assertEquals(RunStatus.SUCCESS, result.status)
        assertTrue(result.nodeReports.any { it.nodeId == "fallback" })
        assertEquals("fallback executed", result.nodeReports.find { it.nodeId == "fallback" }?.output)
    }

    @Test
    fun `test edge priority ordering`() = runTest {
        // Given: Graph with multiple edges with different priorities
        var executedPath: String? = null

        val decisionNode = object : Node {
            override val id = "decision"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.success(
                    NodeResult.fromContext(ctx, data = "proceed")
                )
            }
        }

        val priority1Node = object : Node {
            override val id = "priority1"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                executedPath = "priority1"
                return SpiceResult.success(NodeResult.fromContext(ctx, data = "p1"))
            }
        }

        val priority10Node = object : Node {
            override val id = "priority10"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                executedPath = "priority10"
                return SpiceResult.success(NodeResult.fromContext(ctx, data = "p10"))
            }
        }

        val graph = graph("priority-test") {
            node(decisionNode)
            node(priority1Node)
            node(priority10Node)
            output("output")

            // Both conditions match, but priority1 should execute first
            edge("decision", "priority10", priority = 10) { true }
            edge("decision", "priority1", priority = 1) { true }
            edge("priority1", "output")
            edge("priority10", "output")
        }

        // When: Run graph
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, emptyMap()).getOrThrow()

        // Then: Should execute priority1 (lower priority number = higher priority)
        assertEquals(RunStatus.SUCCESS, result.status)
        assertEquals("priority1", executedPath)
        assertTrue(result.nodeReports.any { it.nodeId == "priority1" })
    }

    @Test
    fun `test EdgeGroup with OR conditions`() = runTest {
        // Given: Graph with EdgeGroup using OR conditions
        val testNode = object : Node {
            override val id = "test"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.success(
                    NodeResult.fromContext(ctx, data = "second", additional = mapOf("status" to "pending"))
                )
            }
        }

        val targetNode = object : Node {
            override val id = "target"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.success(NodeResult.fromContext(ctx, data = "reached"))
            }
        }

        val graph = graph("or-conditions-test") {
            node(testNode)
            node(targetNode)
            output("output")

            complexEdge("test", "target") {
                where { result -> result.data == "first" }
                orWhen { result -> result.data == "second" }
                orWhen { result -> result.data == "third" }
            }
            edge("target", "output")
        }

        // When: Run graph
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, emptyMap()).getOrThrow()

        // Then: Should match second condition
        assertEquals(RunStatus.SUCCESS, result.status)
        assertTrue(result.nodeReports.any { it.nodeId == "target" })
    }

    @Test
    fun `test EdgeGroup with AND conditions`() = runTest {
        // Given: Graph with EdgeGroup using AND conditions
        val testNode = object : Node {
            override val id = "test"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.success(
                    NodeResult.fromContext(
                        ctx,
                        data = listOf(1, 2, 3),
                        additional = mapOf("validated" to true)
                    )
                )
            }
        }

        val targetNode = object : Node {
            override val id = "target"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.success(NodeResult.fromContext(ctx, data = "all conditions met"))
            }
        }

        val graph = graph("and-conditions-test") {
            node(testNode)
            node(targetNode)
            output("output")

            complexEdge("test", "target") {
                where { result -> result.data is List<*> }
                andWhen { result -> (result.data as List<*>).isNotEmpty() }
                andWhenMetadata("validated", equals = true)
            }
            edge("target", "output")
        }

        // When: Run graph
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, emptyMap()).getOrThrow()

        // Then: Should match all AND conditions
        assertEquals(RunStatus.SUCCESS, result.status)
        assertTrue(result.nodeReports.any { it.nodeId == "target" })
    }

    @Test
    fun `test metadata helper functions`() = runTest {
        // Given: Graph using metadata helpers
        val testNode = object : Node {
            override val id = "test"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.success(
                    NodeResult.fromContext(
                        ctx,
                        data = "test",
                        additional = mapOf(
                            "workflow_status" to "awaiting_confirmation",
                            "user_id" to "user123"
                        )
                    )
                )
            }
        }

        val confirmNode = object : Node {
            override val id = "confirm"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.success(NodeResult.fromContext(ctx, data = "confirmed"))
            }
        }

        val graph = graph("metadata-helper-test") {
            node(testNode)
            node(confirmNode)
            output("output")

            complexEdge("test", "confirm") {
                whenMetadata("workflow_status", equals = "awaiting_confirmation")
                andWhenMetadata("user_id", equals = "user123")
            }
            edge("confirm", "output")
        }

        // When: Run graph
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, emptyMap()).getOrThrow()

        // Then: Should match metadata conditions
        assertEquals(RunStatus.SUCCESS, result.status)
        assertTrue(result.nodeReports.any { it.nodeId == "confirm" })
    }

    @Test
    fun `test metadata contains helper`() = runTest {
        // Given: Graph using metadata contains helper
        val testNode = object : Node {
            override val id = "test"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.success(
                    NodeResult.fromContext(
                        ctx,
                        data = "test",
                        additional = mapOf("hitl_type" to "user_confirmation_required")
                    )
                )
            }
        }

        val targetNode = object : Node {
            override val id = "target"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.success(NodeResult.fromContext(ctx, data = "matched"))
            }
        }

        val graph = graph("metadata-contains-test") {
            node(testNode)
            node(targetNode)
            output("output")

            complexEdge("test", "target") {
                whenMetadataContains("hitl_type", "confirmation")
            }
            edge("target", "output")
        }

        // When: Run graph
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, emptyMap()).getOrThrow()

        // Then: Should match substring
        assertEquals(RunStatus.SUCCESS, result.status)
        assertTrue(result.nodeReports.any { it.nodeId == "target" })
    }

    @Test
    fun `test dynamic routing with NodeResult nextEdges`() = runTest {
        // Given: Graph with node that sets dynamic edges
        val dynamicNode = object : Node {
            override val id = "dynamic"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                // Dynamically decide next edge based on context
                val shouldSkip = ctx.state["skip"] as? Boolean ?: false
                val nextEdges = if (shouldSkip) {
                    listOf(Edge("dynamic", "output", priority = 1))
                } else {
                    listOf(Edge("dynamic", "processor", priority = 1))
                }

                return SpiceResult.success(
                    NodeResult.fromContext(ctx, data = "dynamic", nextEdges = nextEdges)
                )
            }
        }

        val processorNode = object : Node {
            override val id = "processor"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.success(NodeResult.fromContext(ctx, data = "processed"))
            }
        }

        val graph = graph("dynamic-routing-test") {
            node(dynamicNode)
            node(processorNode)
            output("output")

            // Static edges (will be overridden by NodeResult.nextEdges)
            edge("dynamic", "processor", priority = 10)  // Low priority, can be overridden
            edge("dynamic", "output", priority = 10)
            edge("processor", "output")
        }

        val runner = DefaultGraphRunner()

        // When: Run with skip=false
        val result1 = runner.run(graph, mapOf("skip" to false)).getOrThrow()

        // Then: Should go through processor
        assertEquals(RunStatus.SUCCESS, result1.status)
        assertTrue(result1.nodeReports.any { it.nodeId == "processor" })

        // When: Run with skip=true
        val result2 = runner.run(graph, mapOf("skip" to true)).getOrThrow()

        // Then: Should skip processor
        assertEquals(RunStatus.SUCCESS, result2.status)
        assertEquals(false, result2.nodeReports.any { it.nodeId == "processor" })
    }

    @Test
    fun `test wildcard edge matching`() = runTest {
        // Given: Graph with wildcard edge
        val node1 = object : Node {
            override val id = "node1"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.success(
                    NodeResult.fromContext(ctx, data = "n1", additional = mapOf("emergency" to true))
                )
            }
        }

        val node2 = object : Node {
            override val id = "node2"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.success(
                    NodeResult.fromContext(ctx, data = "n2", additional = mapOf("emergency" to true))
                )
            }
        }

        val emergencyNode = object : Node {
            override val id = "emergency"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.success(NodeResult.fromContext(ctx, data = "emergency handled"))
            }
        }

        val graph = graph("wildcard-test") {
            node(node1)
            node(node2)
            node(emergencyNode)
            output("output")

            // Wildcard edge: any node with emergency=true goes to emergency node
            complexEdge("*", "emergency", priority = 1) {
                whenMetadata("emergency", equals = true)
            }

            // Regular flow (higher priority numbers, so wildcard takes precedence)
            edge("node1", "node2", priority = 10)
            edge("node1", "emergency", priority = 10)  // Explicit edge for validator
            edge("node2", "output", priority = 10)
            edge("node2", "emergency", priority = 10)  // Explicit edge for validator
            edge("emergency", "output")
        }

        // When: Run graph (node1 has emergency=true, should trigger wildcard)
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, emptyMap()).getOrThrow()

        // Then: Should route to emergency node
        assertEquals(RunStatus.SUCCESS, result.status)
        assertTrue(result.nodeReports.any { it.nodeId == "emergency" })
    }

    @Test
    fun `test edge with name for debugging`() = runTest {
        // Given: Graph with named edges
        val testNode = object : Node {
            override val id = "test"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.success(NodeResult.fromContext(ctx, data = "test"))
            }
        }

        val graph = graph("named-edge-test") {
            node(testNode)
            output("output")

            edge("test", "output", name = "test-to-output")
        }

        // When: Build graph
        // Then: Edge should have name (can be used for debugging/logging)
        val edge = graph.edges.find { it.from == "test" && it.to == "output" }
        assertEquals("test-to-output", edge?.name)
    }

    @Test
    fun `test complex routing scenario`() = runTest {
        // Given: Complex graph with priority, fallback, and conditions
        val analyzerNode = object : Node {
            override val id = "analyzer"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                val score = ctx.state["score"] as? Int ?: 50
                return SpiceResult.success(
                    NodeResult.fromContext(
                        ctx,
                        data = score,
                        additional = mapOf(
                            "score" to score,
                            "category" to when {
                                score >= 90 -> "excellent"
                                score >= 70 -> "good"
                                score >= 50 -> "fair"
                                else -> "poor"
                            }
                        )
                    )
                )
            }
        }

        val excellentNode = object : Node {
            override val id = "excellent"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.success(NodeResult.fromContext(ctx, data = "excellent path"))
            }
        }

        val goodNode = object : Node {
            override val id = "good"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.success(NodeResult.fromContext(ctx, data = "good path"))
            }
        }

        val defaultProcessNode = object : Node {
            override val id = "default-process"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.success(NodeResult.fromContext(ctx, data = "default path"))
            }
        }

        val graph = graph("complex-routing") {
            node(analyzerNode)
            node(excellentNode)
            node(goodNode)
            node(defaultProcessNode)
            output("output")

            // Priority routing based on category
            complexEdge("analyzer", "excellent", priority = 1, name = "route-excellent") {
                whenMetadata("category", equals = "excellent")
            }
            complexEdge("analyzer", "good", priority = 2, name = "route-good") {
                whenMetadata("category", equals = "good")
            }

            // Default fallback for other categories
            defaultEdge("analyzer", "default-process", name = "route-default")

            // All paths lead to output
            edge("excellent", "output")
            edge("good", "output")
            edge("default-process", "output")
        }

        val runner = DefaultGraphRunner()

        // Test excellent path
        val excellentResult = runner.run(graph, mapOf("score" to 95)).getOrThrow()
        assertEquals(RunStatus.SUCCESS, excellentResult.status)
        assertTrue(excellentResult.nodeReports.any { it.nodeId == "excellent" })

        // Test good path
        val goodResult = runner.run(graph, mapOf("score" to 75)).getOrThrow()
        assertEquals(RunStatus.SUCCESS, goodResult.status)
        assertTrue(goodResult.nodeReports.any { it.nodeId == "good" })

        // Test default path
        val defaultResult = runner.run(graph, mapOf("score" to 30)).getOrThrow()
        assertEquals(RunStatus.SUCCESS, defaultResult.status)
        assertTrue(defaultResult.nodeReports.any { it.nodeId == "default-process" })
    }
}
