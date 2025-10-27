package io.github.noailabs.spice.graph

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.AgentContext
import io.github.noailabs.spice.Comm
import io.github.noailabs.spice.ParameterSchema
import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.ToolContext
import io.github.noailabs.spice.ToolResult
import io.github.noailabs.spice.ToolSchema
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.dsl.graph
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import io.github.noailabs.spice.graph.runner.RunStatus
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
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

    @Test
    fun `test context propagation in graph`() = runTest {
        // Given: Agent that captures context
        var capturedTenantId: String? = null
        var capturedUserId: String? = null

        val contextAgent = object : Agent {
            override val id = "context-agent"
            override val name = "Context Agent"
            override val description = "Captures context"
            override val capabilities = emptyList<String>()

            override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
                // Capture context from comm
                capturedTenantId = comm.context?.tenantId
                capturedUserId = comm.context?.userId

                return SpiceResult.success(
                    comm.reply("Context: tenant=$capturedTenantId, user=$capturedUserId", id)
                )
            }

            override fun canHandle(comm: Comm) = true
            override fun getTools() = emptyList<Tool>()
            override fun isReady() = true
        }

        // When: Run graph with AgentContext
        val agentContext = AgentContext.of(
            "tenantId" to "tenant-123",
            "userId" to "user-456"
        )

        val graph = graph("context-test") {
            agent("contextAgent", contextAgent)
            output("result") { ctx -> ctx.state["contextAgent"] }
        }

        val runner = DefaultGraphRunner()

        // ✨ Execute within AgentContext!
        val report = withContext(agentContext) {
            runner.run(graph, mapOf("input" to "test"))
        }

        // Then: Verify context was propagated
        assertEquals(RunStatus.SUCCESS, report.status)
        assertEquals("tenant-123", capturedTenantId)
        assertEquals("user-456", capturedUserId)
        assertEquals("Context: tenant=tenant-123, user=user-456", report.result)

        // Verify metadata also captured context
        val nodeReport = report.nodeReports.find { it.nodeId == "contextAgent" }
        assertNotNull(nodeReport)
    }

    @Test
    fun `test context propagation to tools`() = runTest {
        // Given: Tool that captures context
        var capturedTenantId: String? = null
        var capturedUserId: String? = null

        val contextTool = object : Tool {
            override val name = "context-tool"
            override val description = "Captures context"
            override val schema = ToolSchema("test", "test", emptyMap())

            override suspend fun execute(parameters: Map<String, Any>): SpiceResult<ToolResult> {
                return SpiceResult.success(
                    ToolResult(success = true, result = "No context")
                )
            }

            override suspend fun execute(
                parameters: Map<String, Any>,
                context: ToolContext
            ): SpiceResult<ToolResult> {
                // Capture context
                capturedTenantId = context.tenantId
                capturedUserId = context.userId

                return SpiceResult.success(
                    ToolResult(
                        success = true,
                        result = "Tool context: tenant=${context.tenantId}, user=${context.userId}"
                    )
                )
            }
        }

        // When: Run graph with AgentContext
        val agentContext = AgentContext.of(
            "tenantId" to "tenant-789",
            "userId" to "user-abc"
        )

        val graph = graph("tool-context-test") {
            tool("contextTool", contextTool)
            output("result") { ctx -> ctx.state["contextTool"] }
        }

        val runner = DefaultGraphRunner()

        // ✨ Execute within AgentContext!
        val report = withContext(agentContext) {
            runner.run(graph, mapOf("input" to "test"))
        }

        // Then: Verify context was propagated to tool
        assertEquals(RunStatus.SUCCESS, report.status)
        assertEquals("tenant-789", capturedTenantId)
        assertEquals("user-abc", capturedUserId)
        assertEquals("Tool context: tenant=tenant-789, user=user-abc", report.result)
    }

    @Test
    fun `test middleware execution order`() = runTest {
        // Given: Middleware that tracks execution order
        val executionLog = mutableListOf<String>()

        val middleware1 = object : io.github.noailabs.spice.graph.middleware.Middleware {
            override suspend fun onStart(ctx: io.github.noailabs.spice.graph.middleware.RunContext, next: suspend () -> Unit) {
                executionLog.add("middleware1-start-before")
                next()
                executionLog.add("middleware1-start-after")
            }

            override suspend fun onNode(
                req: io.github.noailabs.spice.graph.middleware.NodeRequest,
                next: suspend (io.github.noailabs.spice.graph.middleware.NodeRequest) -> io.github.noailabs.spice.graph.NodeResult
            ): io.github.noailabs.spice.graph.NodeResult {
                executionLog.add("middleware1-node-${req.nodeId}-before")
                val result = next(req)
                executionLog.add("middleware1-node-${req.nodeId}-after")
                return result
            }

            override suspend fun onFinish(report: io.github.noailabs.spice.graph.runner.RunReport) {
                executionLog.add("middleware1-finish")
            }
        }

        val middleware2 = object : io.github.noailabs.spice.graph.middleware.Middleware {
            override suspend fun onStart(ctx: io.github.noailabs.spice.graph.middleware.RunContext, next: suspend () -> Unit) {
                executionLog.add("middleware2-start-before")
                next()
                executionLog.add("middleware2-start-after")
            }

            override suspend fun onNode(
                req: io.github.noailabs.spice.graph.middleware.NodeRequest,
                next: suspend (io.github.noailabs.spice.graph.middleware.NodeRequest) -> io.github.noailabs.spice.graph.NodeResult
            ): io.github.noailabs.spice.graph.NodeResult {
                executionLog.add("middleware2-node-${req.nodeId}-before")
                val result = next(req)
                executionLog.add("middleware2-node-${req.nodeId}-after")
                return result
            }

            override suspend fun onFinish(report: io.github.noailabs.spice.graph.runner.RunReport) {
                executionLog.add("middleware2-finish")
            }
        }

        val agent = object : Agent {
            override val id = "test-agent"
            override val name = "Test"
            override val description = "Test agent"
            override val capabilities = emptyList<String>()

            override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
                executionLog.add("agent-execution")
                return SpiceResult.success(comm.reply("result", id))
            }

            override fun canHandle(comm: Comm) = true
            override fun getTools() = emptyList<Tool>()
            override fun isReady() = true
        }

        // When: Create graph with middleware
        val graph = graph("middleware-test") {
            middleware(middleware1)
            middleware(middleware2)
            agent("agent", agent)
            output("result") { ctx -> ctx.state["agent"] }
        }

        val runner = DefaultGraphRunner()
        val report = runner.run(graph, mapOf("input" to "test"))

        // Then: Verify middleware execution order
        assertEquals(RunStatus.SUCCESS, report.status)

        // Verify onStart chain (middleware1 wraps middleware2)
        assertEquals("middleware1-start-before", executionLog[0])
        assertEquals("middleware2-start-before", executionLog[1])
        assertEquals("middleware2-start-after", executionLog[2])
        assertEquals("middleware1-start-after", executionLog[3])

        // Verify onNode chain for agent node
        assertEquals("middleware1-node-agent-before", executionLog[4])
        assertEquals("middleware2-node-agent-before", executionLog[5])
        assertEquals("agent-execution", executionLog[6])
        assertEquals("middleware2-node-agent-after", executionLog[7])
        assertEquals("middleware1-node-agent-after", executionLog[8])

        // Verify onNode chain for output node
        assertEquals("middleware1-node-result-before", executionLog[9])
        assertEquals("middleware2-node-result-before", executionLog[10])
        assertEquals("middleware2-node-result-after", executionLog[11])
        assertEquals("middleware1-node-result-after", executionLog[12])

        // Verify onFinish (called in order)
        assertEquals("middleware1-finish", executionLog[13])
        assertEquals("middleware2-finish", executionLog[14])
    }

    @Test
    fun `test metrics middleware`() = runTest {
        // Given: MetricsMiddleware
        val metricsMiddleware = io.github.noailabs.spice.graph.middleware.MetricsMiddleware()

        val agent = object : Agent {
            override val id = "slow-agent"
            override val name = "Slow"
            override val description = "Slow agent"
            override val capabilities = emptyList<String>()

            override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
                kotlinx.coroutines.delay(10) // Simulate work
                return SpiceResult.success(comm.reply("done", id))
            }

            override fun canHandle(comm: Comm) = true
            override fun getTools() = emptyList<Tool>()
            override fun isReady() = true
        }

        // When: Run graph multiple times
        val graph = graph("metrics-test") {
            middleware(metricsMiddleware)
            agent("agent", agent)
            output("result") { ctx -> ctx.state["agent"] }
        }

        val runner = DefaultGraphRunner()
        runner.run(graph, mapOf("input" to "test1"))
        runner.run(graph, mapOf("input" to "test2"))

        // Then: Verify metrics collected
        val metrics = metricsMiddleware.getMetrics()
        assertEquals(2, metrics.graphExecutionCount)
        assertNotNull(metrics.nodeExecutionTimes["agent"])
        assertEquals(2, metrics.nodeExecutionTimes["agent"]?.count)
    }
}
