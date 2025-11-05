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

class ConditionalEdgeTest {

    @Test
    fun `test conditional edge based on node result`() = runTest {
        // Given: Graph with conditional edges based on result
        val checkNode = object : Node {
            override val id = "check"

            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                val value = ctx.state["input"] as? Int ?: 0
                val dataValue = if (value > 10) "high" else "low"
                return SpiceResult.success(
                    NodeResult.fromContext(ctx, data = dataValue, additional = mapOf("value" to value))
                )
            }
        }

        val highNode = object : Node {
            override val id = "high-path"

            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.success(NodeResult.fromContext(ctx, data = "Processed high value"))
            }
        }

        val lowNode = object : Node {
            override val id = "low-path"

            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.success(NodeResult.fromContext(ctx, data = "Processed low value"))
            }
        }

        val graph = Graph(
            id = "conditional-graph",
            nodes = mapOf(
                "check" to checkNode,
                "high-path" to highNode,
                "low-path" to lowNode,
                "output" to OutputNode("output")
            ),
            edges = listOf(
                Edge("check", "high-path") { result -> result.data == "high" },
                Edge("check", "low-path") { result -> result.data == "low" },
                Edge("high-path", "output"),
                Edge("low-path", "output")
            ),
            entryPoint = "check"
        )

        // When: Run with high value
        val runner = DefaultGraphRunner()
        val highResult = runner.run(graph, mapOf("input" to 15)).getOrThrow()

        // Then: Should take high path
        assertEquals(RunStatus.SUCCESS, highResult.status)
        assertEquals(3, highResult.nodeReports.size)
        assertTrue(highResult.nodeReports.any { it.nodeId == "high-path" })

        // When: Run with low value
        val lowResult = runner.run(graph, mapOf("input" to 5)).getOrThrow()

        // Then: Should take low path
        assertEquals(RunStatus.SUCCESS, lowResult.status)
        assertEquals(3, lowResult.nodeReports.size)
        assertTrue(lowResult.nodeReports.any { it.nodeId == "low-path" })
    }

    @Test
    fun `test conditional edge with manual graph construction`() = runTest {
        // Given: Graph built manually with conditional edges
        val decisionNode = object : Node {
            override val id = "decision"

            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                val command = ctx.state["command"] as? String ?: ""
                return SpiceResult.success(NodeResult.fromContext(ctx, data = command))
            }
        }

        val actionA = object : Node {
            override val id = "action-a"

            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.success(NodeResult.fromContext(ctx, data = "Executed A"))
            }
        }

        val actionB = object : Node {
            override val id = "action-b"

            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.success(NodeResult.fromContext(ctx, data = "Executed B"))
            }
        }

        val manualGraph = Graph(
            id = "decision-graph",
            nodes = mapOf(
                "decision" to decisionNode,
                "action-a" to actionA,
                "action-b" to actionB,
                "output" to OutputNode("output")
            ),
            edges = listOf(
                Edge("decision", "action-a") { result -> result.data == "run-a" },
                Edge("decision", "action-b") { result -> result.data == "run-b" },
                Edge("action-a", "output"),
                Edge("action-b", "output")
            ),
            entryPoint = "decision"
        )

        // When: Run with command "run-a"
        val runner = DefaultGraphRunner()
        val resultA = runner.run(manualGraph, mapOf("command" to "run-a")).getOrThrow()

        // Then: Should execute action-a
        assertEquals(RunStatus.SUCCESS, resultA.status)
        assertTrue(resultA.nodeReports.any { it.nodeId == "action-a" })

        // When: Run with command "run-b"
        val resultB = runner.run(manualGraph, mapOf("command" to "run-b")).getOrThrow()

        // Then: Should execute action-b
        assertEquals(RunStatus.SUCCESS, resultB.status)
        assertTrue(resultB.nodeReports.any { it.nodeId == "action-b" })
    }

    @Test
    fun `test DSL conditional edges override automatic edges`() = runTest {
        // Given: Graph built with DSL that has conditional edges
        var checkNodeExecuted = false
        var pathATaken = false
        var pathBTaken = false

        val testGraph = graph("dsl-conditional-test") {
            // Add nodes sequentially (this creates auto-edges)
            agent("check", object : io.github.noailabs.spice.Agent {
                override val id = "check-agent"
                override val name = "Check Agent"
                override val description = "Checks condition"
                override val capabilities = emptyList<String>()

                override suspend fun processComm(comm: io.github.noailabs.spice.Comm): io.github.noailabs.spice.error.SpiceResult<io.github.noailabs.spice.Comm> {
                    checkNodeExecuted = true
                    val value = comm.data["value"]?.toIntOrNull() ?: 0
                    val route = if (value > 10) "path-a" else "path-b"
                    return io.github.noailabs.spice.error.SpiceResult.success(
                        comm.copy(data = comm.data + mapOf("route" to route))
                    )
                }

                override fun canHandle(comm: io.github.noailabs.spice.Comm) = true
                override fun getTools() = emptyList<io.github.noailabs.spice.Tool>()
                override fun isReady() = true
            })

            agent("path-a", object : io.github.noailabs.spice.Agent {
                override val id = "path-a-agent"
                override val name = "Path A"
                override val description = "Path A"
                override val capabilities = emptyList<String>()

                override suspend fun processComm(comm: io.github.noailabs.spice.Comm): io.github.noailabs.spice.error.SpiceResult<io.github.noailabs.spice.Comm> {
                    pathATaken = true
                    return io.github.noailabs.spice.error.SpiceResult.success(comm.copy(content = "Path A executed"))
                }

                override fun canHandle(comm: io.github.noailabs.spice.Comm) = true
                override fun getTools() = emptyList<io.github.noailabs.spice.Tool>()
                override fun isReady() = true
            })

            agent("path-b", object : io.github.noailabs.spice.Agent {
                override val id = "path-b-agent"
                override val name = "Path B"
                override val description = "Path B"
                override val capabilities = emptyList<String>()

                override suspend fun processComm(comm: io.github.noailabs.spice.Comm): io.github.noailabs.spice.error.SpiceResult<io.github.noailabs.spice.Comm> {
                    pathBTaken = true
                    return io.github.noailabs.spice.error.SpiceResult.success(comm.copy(content = "Path B executed"))
                }

                override fun canHandle(comm: io.github.noailabs.spice.Comm) = true
                override fun getTools() = emptyList<io.github.noailabs.spice.Tool>()
                override fun isReady() = true
            })

            output("result")

            // Define explicit conditional edges (should override auto-edges from "check")
            edge("check", "path-a") { result ->
                val comm = result.metadata["_previousComm"] as? io.github.noailabs.spice.Comm
                comm?.data?.get("route") == "path-a"
            }
            edge("check", "path-b") { result ->
                val comm = result.metadata["_previousComm"] as? io.github.noailabs.spice.Comm
                comm?.data?.get("route") == "path-b"
            }
            edge("path-a", "result")
            edge("path-b", "result")
        }

        // When: Run with high value (should take path-a)
        val runner = DefaultGraphRunner()
        checkNodeExecuted = false
        pathATaken = false
        pathBTaken = false

        val highResult = runner.run(testGraph, mapOf(
            "comm" to io.github.noailabs.spice.Comm(
                content = "test",
                from = "user",
                data = mapOf("value" to "15")
            )
        )).getOrThrow()

        println("=== High Value Test ===")
        println("Node reports: ${highResult.nodeReports.map { it.nodeId }}")
        highResult.nodeReports.forEach { report ->
            println("Node ${report.nodeId}: status=${report.status}, output=${report.output}")
        }

        // Then: Should execute path-a, not path-b
        assertEquals(RunStatus.SUCCESS, highResult.status)
        assertTrue(checkNodeExecuted, "Check node should be executed")
        assertTrue(pathATaken, "Path A should be taken for value > 10")
        assertTrue(!pathBTaken, "Path B should NOT be taken for value > 10")

        // When: Run with low value (should take path-b)
        checkNodeExecuted = false
        pathATaken = false
        pathBTaken = false

        val lowResult = runner.run(testGraph, mapOf(
            "comm" to io.github.noailabs.spice.Comm(
                content = "test",
                from = "user",
                data = mapOf("value" to "5")
            )
        )).getOrThrow()

        println("=== Low Value Test ===")
        println("Node reports: ${lowResult.nodeReports.map { it.nodeId }}")
        lowResult.nodeReports.forEach { report ->
            println("Node ${report.nodeId}: status=${report.status}, output=${report.output}")
        }

        // Then: Should execute path-b, not path-a
        assertEquals(RunStatus.SUCCESS, lowResult.status)
        assertTrue(checkNodeExecuted, "Check node should be executed")
        assertTrue(!pathATaken, "Path A should NOT be taken for value <= 10")
        assertTrue(pathBTaken, "Path B should be taken for value <= 10")
    }

    @Test
    fun `test multiple conditional edges priority`() = runTest {
        // Given: Node with multiple outgoing edges
        val sourceNode = object : Node {
            override val id = "source"

            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.success(NodeResult.fromContext(ctx, data = "matching-both"))
            }
        }

        val firstMatch = object : Node {
            override val id = "first-match"

            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.success(NodeResult.fromContext(ctx, data = "First matched"))
            }
        }

        val secondMatch = object : Node {
            override val id = "second-match"

            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                return SpiceResult.success(NodeResult.fromContext(ctx, data = "Second matched"))
            }
        }

        val graph = Graph(
            id = "priority-graph",
            nodes = mapOf(
                "source" to sourceNode,
                "first-match" to firstMatch,
                "second-match" to secondMatch,
                "output" to OutputNode("output")
            ),
            edges = listOf(
                // Both conditions match, but first one should be selected
                Edge("source", "first-match") { result -> result.data.toString().contains("matching") },
                Edge("source", "second-match") { result -> result.data.toString().contains("both") },
                Edge("first-match", "output"),
                Edge("second-match", "output")
            ),
            entryPoint = "source"
        )

        // When: Run graph
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, emptyMap()).getOrThrow()

        // Then: Should take first matching edge
        assertEquals(RunStatus.SUCCESS, result.status)
        assertTrue(result.nodeReports.any { it.nodeId == "first-match" })
        assertTrue(result.nodeReports.none { it.nodeId == "second-match" })
    }
}
