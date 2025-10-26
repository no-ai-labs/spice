package io.github.noailabs.spice.graph

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.Comm
import io.github.noailabs.spice.ParameterSchema
import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.ToolResult
import io.github.noailabs.spice.ToolSchema
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.dsl.graph
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import io.github.noailabs.spice.graph.runner.RunStatus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GraphIntegrationTest {

    @Test
    fun `test simple agent graph execution`() = runTest {
        // Given: Simple agent that greets
        val greetAgent = object : Agent {
            override val id = "greet-agent"
            override val name = "Greeter"
            override val description = "A friendly greeter"
            override val capabilities = emptyList<String>()

            override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
                return SpiceResult.success(comm.reply("Hello, ${comm.content}!", id))
            }

            override fun canHandle(comm: Comm) = true
            override fun getTools() = emptyList<Tool>()
            override fun isReady() = true
        }

        // When: Create and run graph
        val graph = graph("greeting-graph") {
            agent("greeter", greetAgent)
            output("result") { ctx -> ctx.state["greeter"] }
        }

        val runner = DefaultGraphRunner()
        val report = runner.run(graph, mapOf("input" to "World"))

        // Then: Check result
        assertEquals(RunStatus.SUCCESS, report.status)
        assertEquals("Hello, World!", report.result)
        assertEquals(2, report.nodeReports.size)
        assertNotNull(report.nodeReports.find { it.nodeId == "greeter" })
        assertNotNull(report.nodeReports.find { it.nodeId == "result" })
    }

    @Test
    fun `test agent chain execution`() = runTest {
        // Given: Two agents in sequence
        val agent1 = object : Agent {
            override val id = "agent1"
            override val name = "Agent 1"
            override val description = "First agent"
            override val capabilities = emptyList<String>()

            override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
                return SpiceResult.success(comm.reply("Step 1: ${comm.content}", id))
            }

            override fun canHandle(comm: Comm) = true
            override fun getTools() = emptyList<Tool>()
            override fun isReady() = true
        }

        val agent2 = object : Agent {
            override val id = "agent2"
            override val name = "Agent 2"
            override val description = "Second agent"
            override val capabilities = emptyList<String>()

            override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
                return SpiceResult.success(comm.reply("Step 2: ${comm.content}", id))
            }

            override fun canHandle(comm: Comm) = true
            override fun getTools() = emptyList<Tool>()
            override fun isReady() = true
        }

        // When: Create chain graph
        val graph = graph("chain-graph") {
            agent("first", agent1)
            agent("second", agent2)
            output("final") { ctx -> ctx.state["second"] }
        }

        val runner = DefaultGraphRunner()
        val report = runner.run(graph, mapOf("input" to "Start"))

        // Then: Verify execution
        assertEquals(RunStatus.SUCCESS, report.status)
        assertEquals("Step 2: Step 1: Start", report.result)
        assertEquals(3, report.nodeReports.size)
    }

    @Test
    fun `test tool node execution`() = runTest {
        // Given: Simple tool
        val upperCaseTool = object : Tool {
            override val name = "uppercase"
            override val description = "Converts text to uppercase"
            override val schema = ToolSchema("test", "test", emptyMap())

            override suspend fun execute(parameters: Map<String, Any>): SpiceResult<ToolResult> {
                val text = parameters["text"]?.toString() ?: ""
                return SpiceResult.success(
                    ToolResult(
                        success = true,
                        result = text.uppercase()
                    )
                )
            }
        }

        // When: Create graph with tool
        val graph = graph("tool-graph") {
            tool("converter", upperCaseTool) { ctx ->
                mapOf("text" to (ctx.state["input"] ?: ""))
            }
            output("result") { ctx -> ctx.state["converter"] }
        }

        val runner = DefaultGraphRunner()
        val report = runner.run(graph, mapOf("input" to "hello world"))

        // Then: Verify result
        assertEquals(RunStatus.SUCCESS, report.status)
        assertEquals("HELLO WORLD", report.result)
    }

    @Test
    fun `test mixed agent and tool graph`() = runTest {
        // Given: Agent and tool
        val agent = object : Agent {
            override val id = "processor"
            override val name = "Processor"
            override val description = "Processing agent"
            override val capabilities = emptyList<String>()

            override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
                return SpiceResult.success(comm.reply("Processed: ${comm.content}", id))
            }

            override fun canHandle(comm: Comm) = true
            override fun getTools() = emptyList<Tool>()
            override fun isReady() = true
        }

        val tool = object : Tool {
            override val name = "formatter"
            override val description = "Formats text"
            override val schema = ToolSchema("test", "test", emptyMap())

            override suspend fun execute(parameters: Map<String, Any>): SpiceResult<ToolResult> {
                val text = parameters["text"]?.toString() ?: ""
                return SpiceResult.success(
                    ToolResult(
                        success = true,
                        result = "[$text]"
                    )
                )
            }
        }

        // When: Create mixed graph
        val graph = graph("mixed-graph") {
            agent("process", agent)
            tool("format", tool) { ctx ->
                mapOf("text" to (ctx.state["process"] ?: ""))
            }
            output("final") { ctx -> ctx.state["format"] }
        }

        val runner = DefaultGraphRunner()
        val report = runner.run(graph, mapOf("input" to "data"))

        // Then: Verify result
        assertEquals(RunStatus.SUCCESS, report.status)
        assertEquals("[Processed: data]", report.result)
        assertEquals(3, report.nodeReports.size)
    }

    @Test
    fun `test graph with empty nodes fails`() = runTest {
        // When/Then: Creating graph with no nodes should fail
        try {
            graph("empty-graph") {
                // No nodes added
            }.also {
                // Trigger build by accessing property
                it.nodes
            }
            error("Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            assertEquals("Graph must have at least one node", e.message)
        }
    }
}
