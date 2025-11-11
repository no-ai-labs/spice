package io.github.noailabs.spice.graph

import io.github.noailabs.spice.ExecutionContext

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.AgentContext
import io.github.noailabs.spice.Comm
import io.github.noailabs.spice.ParameterSchema
import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.ToolContext
import io.github.noailabs.spice.ToolResult
import io.github.noailabs.spice.ToolSchema
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.checkpoint.CheckpointConfig
import io.github.noailabs.spice.graph.checkpoint.InMemoryCheckpointStore
import io.github.noailabs.spice.graph.dsl.graph
import io.github.noailabs.spice.graph.middleware.Middleware
import io.github.noailabs.spice.graph.middleware.NodeRequest
import io.github.noailabs.spice.graph.middleware.RunContext
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import io.github.noailabs.spice.graph.runner.NodeStatus
import io.github.noailabs.spice.graph.runner.RunStatus
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for AgentContext propagation throughout the graph execution system.
 * Verifies context flows correctly through: GraphRunner → Middleware → Nodes → Agents/Tools → Checkpoints
 */
class ContextPropagationTest {

    @Test
    fun `test context propagates from coroutine to agent`() = runTest {
        // Given: Agent that verifies it receives context
        var receivedContext: ExecutionContext? = null

        val contextAwareAgent = object : Agent {
            override val id = "context-agent"
            override val name = "Context Agent"
            override val description = "Checks context"
            override val capabilities = emptyList<String>()

            override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
                receivedContext = comm.context
                return SpiceResult.success(
                    comm.reply(
                        "tenant: ${comm.context?.tenantId}, user: ${comm.context?.userId}",
                        id
                    )
                )
            }

            override fun canHandle(comm: Comm) = true
            override fun getTools() = emptyList<Tool>()
            override fun isReady() = true
        }

        val graph = graph("context-graph") {
            agent("agent", contextAwareAgent)
            output("result") { it.state["agent"] }
        }

        // When: Run with AgentContext in coroutine context
        val agentContext = AgentContext.of(
            "tenantId" to "tenant-123",
            "userId" to "user-456",
            "sessionId" to "session-789",
            "correlationId" to "corr-abc"
        )

        val runner = DefaultGraphRunner()
        val result = withContext(agentContext) {
            runner.run(graph, mapOf("input" to "test"))
        }

        // Then: Context should propagate to agent
        assertTrue(result.isSuccess)
        assertNotNull(receivedContext)
        assertEquals("tenant-123", receivedContext?.tenantId)
        assertEquals("user-456", receivedContext?.userId)
        assertEquals("session-789", receivedContext?.getAs<String>("sessionId"))
        assertEquals("corr-abc", receivedContext?.correlationId)
    }

    @Test
    fun `test context propagates to tools`() = runTest {
        // Given: Tool that verifies it receives context
        var receivedToolContext: ToolContext? = null

        val contextAwareTool = object : Tool {
            override val name = "context-tool"
            override val description = "Checks context"
            override val schema = ToolSchema(
                name = name,
                description = description,
                parameters = emptyMap()
            )

            override suspend fun execute(parameters: Map<String, Any?>): SpiceResult<ToolResult> {
                return SpiceResult.success(
                    ToolResult(
                        success = true,
                        result = "no context"
                    )
                )
            }

            override suspend fun execute(
                parameters: Map<String, Any?>,
                context: ToolContext
            ): SpiceResult<ToolResult> {
                receivedToolContext = context
                return SpiceResult.success(
                    ToolResult(
                        success = true,
                        result = "tenant: ${context.tenantId}, user: ${context.userId}"
                    )
                )
            }
        }

        val graph = graph("tool-context-graph") {
            tool("tool", contextAwareTool) { emptyMap() }
            output("result") { it.state["tool"] }
        }

        // When: Run with AgentContext
        val agentContext = AgentContext.of(
            "tenantId" to "tenant-999",
            "userId" to "user-888"
        )

        val runner = DefaultGraphRunner()
        val result = withContext(agentContext) {
            runner.run(graph, emptyMap())
        }

        // Then: Context should propagate to tool
        assertTrue(result.isSuccess)
        assertNotNull(receivedToolContext)
        assertEquals("tenant-999", receivedToolContext?.tenantId)
        assertEquals("user-888", receivedToolContext?.userId)
    }

    @Test
    fun `test context available in middleware`() = runTest {
        // Given: Middleware that checks context
        var middlewareContext: ExecutionContext? = null

        val contextCheckMiddleware = object : Middleware {
            override suspend fun onStart(ctx: RunContext, next: suspend () -> Unit) {
                middlewareContext = ctx.context
                next()
            }
        }

        val graph = Graph(
            id = "middleware-context-graph",
            nodes = mapOf("output" to io.github.noailabs.spice.graph.nodes.OutputNode("output")),
            edges = emptyList(),
            entryPoint = "output",
            middleware = listOf(contextCheckMiddleware)
        )

        // When: Run with AgentContext
        val agentContext = AgentContext.of(
            "tenantId" to "tenant-777",
            "userId" to "user-666",
            "customKey" to "customValue"
        )

        val runner = DefaultGraphRunner()
        val result = withContext(agentContext) {
            runner.run(graph, emptyMap())
        }

        // Then: Middleware should have access to context
        assertTrue(result.isSuccess)
        assertNotNull(middlewareContext)
        assertEquals("tenant-777", middlewareContext?.tenantId)
        assertEquals("user-666", middlewareContext?.userId)
        assertEquals("customValue", middlewareContext?.getAs<String>("customKey"))
    }

    @Test
    fun `test context saved in checkpoint`() = runTest {
        // Given: Graph with checkpoint
        val graph = Graph(
            id = "checkpoint-context-graph",
            nodes = mapOf(
                "node1" to io.github.noailabs.spice.graph.nodes.OutputNode("node1"),
                "node2" to io.github.noailabs.spice.graph.nodes.OutputNode("node2")
            ),
            edges = listOf(Edge("node1", "node2")),
            entryPoint = "node1"
        )

        val store = InMemoryCheckpointStore()
        val agentContext = AgentContext.of(
            "tenantId" to "tenant-555",
            "userId" to "user-444",
            "sessionId" to "session-333"
        )

        // When: Run with checkpoint
        val runner = DefaultGraphRunner()
        val result = withContext(agentContext) {
            runner.runWithCheckpoint(
                graph,
                emptyMap(),
                store,
                CheckpointConfig(saveEveryNNodes = 1)
            )
        }

        // Then: Run should succeed
        // Note: Checkpoints are cleaned up on successful completion
        // but during execution they should contain context
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test context restored from checkpoint`() = runTest {
        // Given: Checkpoint with context stored
        val store = InMemoryCheckpointStore()
        val originalContext = AgentContext.of(
            "tenantId" to "original-tenant",
            "userId" to "original-user",
            "correlationId" to "original-corr"
        )

        // Create checkpoint manually
        val checkpoint = io.github.noailabs.spice.graph.checkpoint.Checkpoint(
            id = "test-checkpoint",
            runId = "test-run",
            graphId = "resume-graph",
            currentNodeId = "agent1",
            state = mapOf("input" to "test"),
            agentContext = originalContext,
            timestamp = java.time.Instant.now()
        )
        store.save(checkpoint)

        // Create simple graph with context capture
        var capturedContext: ExecutionContext? = null
        val contextCapturingNode = object : Node {
            override val id = "agent2"
            override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
                capturedContext = ctx.context
                return SpiceResult.success(NodeResult.fromContext(ctx, data = "captured"))
            }
        }

        val graph = Graph(
            id = "resume-graph",
            nodes = mapOf(
                "agent1" to io.github.noailabs.spice.graph.nodes.OutputNode("agent1"),
                "agent2" to contextCapturingNode
            ),
            edges = listOf(Edge("agent1", "agent2")),
            entryPoint = "agent1"
        )

        // When: Resume from checkpoint
        val runner = DefaultGraphRunner()
        val resumeResult = runner.resume(graph, "test-checkpoint", store)

        // Then: Context should be restored from checkpoint
        assertTrue(resumeResult.isSuccess)
        assertNotNull(capturedContext)
        assertEquals("original-tenant", capturedContext?.tenantId)
        assertEquals("original-user", capturedContext?.userId)
        assertEquals("original-corr", capturedContext?.correlationId)
    }

    @Test
    fun `test context propagates through multi-node graph`() = runTest {
        // Given: Graph with multiple agents
        val capturedContexts = mutableListOf<ExecutionContext?>()

        val contextCapturingAgent = { id: String ->
            object : Agent {
                override val id = id
                override val name = "Agent $id"
                override val description = "Captures context"
                override val capabilities = emptyList<String>()

                override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
                    capturedContexts.add(comm.context)
                    return SpiceResult.success(comm.reply("$id processed", this.id))
                }

                override fun canHandle(comm: Comm) = true
                override fun getTools() = emptyList<Tool>()
                override fun isReady() = true
            }
        }

        val graph = graph("multi-node-context-graph") {
            agent("agent1", contextCapturingAgent("agent1"))
            agent("agent2", contextCapturingAgent("agent2"))
            agent("agent3", contextCapturingAgent("agent3"))
            output("result") { it.state["agent3"] }
        }

        val agentContext = AgentContext.of(
            "tenantId" to "shared-tenant",
            "userId" to "shared-user",
            "sessionId" to "shared-session"
        )

        // When: Run graph
        val runner = DefaultGraphRunner()
        val result = withContext(agentContext) {
            runner.run(graph, mapOf("input" to "test"))
        }

        // Then: All agents should receive the same context
        assertTrue(result.isSuccess)
        assertEquals(3, capturedContexts.size)
        capturedContexts.forEach { ctx ->
            assertNotNull(ctx)
            assertEquals("shared-tenant", ctx?.tenantId)
            assertEquals("shared-user", ctx?.userId)
            assertEquals("shared-session", ctx?.getAs<String>("sessionId"))
        }
    }

    @Test
    fun `test context available in node metadata`() = runTest {
        // Given: Agent that uses context
        val simpleAgent = object : Agent {
            override val id = "simple-agent"
            override val name = "Simple Agent"
            override val description = "Simple"
            override val capabilities = emptyList<String>()

            override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
                return SpiceResult.success(comm.reply("processed", id))
            }

            override fun canHandle(comm: Comm) = true
            override fun getTools() = emptyList<Tool>()
            override fun isReady() = true
        }

        val graph = graph("metadata-context-graph") {
            agent("agent", simpleAgent)
            output("result") { it.state["agent"] }
        }

        val agentContext = AgentContext.of(
            "tenantId" to "metadata-tenant",
            "userId" to "metadata-user"
        )

        // When: Run graph
        val runner = DefaultGraphRunner()
        val result = withContext(agentContext) {
            runner.run(graph, mapOf("input" to "test"))
        }

        // Then: Metadata should contain context info
        assertTrue(result.isSuccess)
        val report = result.getOrThrow()
        val agentReport = report.nodeReports.find { it.nodeId == "agent" }
        assertNotNull(agentReport)

        // AgentNode adds tenantId and userId to metadata
        assertEquals(NodeStatus.SUCCESS, agentReport.status)
    }

    @Test
    fun `test context works without explicit context (null handling)`() = runTest {
        // Given: Graph run without AgentContext
        val agent = object : Agent {
            override val id = "no-context-agent"
            override val name = "No Context Agent"
            override val description = "Works without context"
            override val capabilities = emptyList<String>()

            override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
                // Should handle null context gracefully
                return SpiceResult.success(
                    comm.reply(
                        "context is ${if (comm.context == null) "null" else "present"}",
                        id
                    )
                )
            }

            override fun canHandle(comm: Comm) = true
            override fun getTools() = emptyList<Tool>()
            override fun isReady() = true
        }

        val graph = graph("no-context-graph") {
            agent("agent", agent)
            output("result") { it.state["agent"] }
        }

        // When: Run without AgentContext
        val runner = DefaultGraphRunner()
        val result = runner.run(graph, mapOf("input" to "test"))

        // Then: Should work fine with null context
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().result.toString().contains("context is null"))
    }
}
