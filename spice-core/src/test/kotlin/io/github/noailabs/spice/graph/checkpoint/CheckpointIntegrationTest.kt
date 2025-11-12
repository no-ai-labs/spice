package io.github.noailabs.spice.graph.checkpoint

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.Comm
import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.dsl.graph
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import io.github.noailabs.spice.graph.runner.RunStatus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CheckpointIntegrationTest {

    @Test
    fun `test checkpoint saves every N nodes`() = runTest {
        // Given: Graph with 3 agents
        val agent1 = SimpleAgent("agent1", "Step 1")
        val agent2 = SimpleAgent("agent2", "Step 2")
        val agent3 = SimpleAgent("agent3", "Step 3")

        val graph = graph("checkpoint-test") {
            agent("node1", agent1)
            agent("node2", agent2)
            agent("node3", agent3)
            output("result") { ctx -> ctx.state["node3"] }
        }

        val store = InMemoryCheckpointStore()
        val config = CheckpointConfig(saveEveryNNodes = 1)

        // When: Run with checkpointing
        val runner = DefaultGraphRunner()
        val report = runner.runWithCheckpoint(graph, mapOf("input" to "Start"), store, config).getOrThrow()

        // Then: Verify execution succeeded
        assertEquals(RunStatus.SUCCESS, report.status)

        // Checkpoints are cleaned up on success
        val checkpoints = store.listByGraph("checkpoint-test").getOrThrow()
        assertTrue(checkpoints.isEmpty(), "Checkpoints should be cleaned up on success")
    }

    @Test
    fun `test checkpoint saves on error`() = runTest {
        // Given: Graph with failing agent
        val agent1 = SimpleAgent("agent1", "Step 1")
        val failingAgent = FailingAgent("failing-agent")

        val graph = graph("error-checkpoint-test") {
            agent("node1", agent1)
            agent("failing", failingAgent)
            output("result") { ctx -> ctx.state["failing"] }
        }

        val store = InMemoryCheckpointStore()
        val config = CheckpointConfig(saveOnError = true)

        // When: Run with checkpointing (expect failure)
        val runner = DefaultGraphRunner()
        val result = runner.runWithCheckpoint(graph, mapOf("input" to "Start"), store, config)

        // Then: Verify execution failed
        assertTrue(result.isFailure)

        // Checkpoint should be saved
        val checkpoints = store.listByGraph("error-checkpoint-test").getOrThrow()
        assertTrue(checkpoints.isNotEmpty(), "Checkpoint should be saved on error")
    }

    @Test
    fun `test resume from checkpoint`() = runTest {
        // Given: Graph with 3 agents where middle one fails
        val agent1 = SimpleAgent("agent1", "Step 1")
        val conditionalAgent = ConditionalAgent("conditional-agent")
        val agent3 = SimpleAgent("agent3", "Step 3")

        val graph = graph("resume-test") {
            agent("node1", agent1)
            agent("node2", conditionalAgent)
            agent("node3", agent3)
            output("result") { ctx -> ctx.state["node3"] }
        }

        val store = InMemoryCheckpointStore()
        val config = CheckpointConfig(saveEveryNNodes = 1, saveOnError = true)

        // When: First run fails
        val runner = DefaultGraphRunner()
        conditionalAgent.shouldFail = true
        val failResult = runner.runWithCheckpoint(graph, mapOf("input" to "Start"), store, config)
        assertTrue(failResult.isFailure)

        // Get the checkpoint
        val checkpoints = store.listByGraph("resume-test").getOrThrow()
        assertNotNull(checkpoints.firstOrNull())
        val checkpointId = checkpoints.first().id

        // Fix the agent and resume
        conditionalAgent.shouldFail = false
        val resumeResult = runner.resume(graph, checkpointId, store, config).getOrThrow()

        // Then: Verify execution succeeded
        assertEquals(RunStatus.SUCCESS, resumeResult.status)
    }

    @Test
    fun `test InMemoryCheckpointStore save and load`() = runTest {
        // Given: CheckpointStore
        val store = InMemoryCheckpointStore()

        val checkpoint = Checkpoint(
            id = "test-checkpoint-1",
            runId = "run-123",
            graphId = "graph-abc",
            currentNodeId = "node1",
            state = mapOf("key1" to "value1", "key2" to 42)
        )

        // When: Save checkpoint
        val savedId = store.save(checkpoint).getOrThrow()

        // Then: Can load it back
        assertEquals("test-checkpoint-1", savedId)

        val loaded = store.load(savedId).getOrThrow()
        assertEquals(checkpoint.id, loaded.id)
        assertEquals(checkpoint.runId, loaded.runId)
        assertEquals(checkpoint.graphId, loaded.graphId)
        assertEquals(checkpoint.currentNodeId, loaded.currentNodeId)
        assertEquals("value1", loaded.state["key1"])
        assertEquals(42L, loaded.state["key2"])  // JSON numbers become Long after serialization
    }

    @Test
    fun `test InMemoryCheckpointStore list by run`() = runTest {
        // Given: Multiple checkpoints for same run
        val store = InMemoryCheckpointStore()

        val runId = "run-123"
        store.save(Checkpoint("cp1", runId, "graph1", "node1", emptyMap())).getOrThrow()
        store.save(Checkpoint("cp2", runId, "graph1", "node2", emptyMap())).getOrThrow()
        store.save(Checkpoint("cp3", "run-456", "graph1", "node1", emptyMap())).getOrThrow()

        // When: List by run
        val checkpoints = store.listByRun(runId).getOrThrow()

        // Then: Only checkpoints for that run
        assertEquals(2, checkpoints.size)
        assertTrue(checkpoints.all { it.runId == runId })
    }

    @Test
    fun `test InMemoryCheckpointStore delete by run`() = runTest {
        // Given: Checkpoints for multiple runs
        val store = InMemoryCheckpointStore()

        val runId = "run-123"
        store.save(Checkpoint("cp1", runId, "graph1", "node1", emptyMap())).getOrThrow()
        store.save(Checkpoint("cp2", runId, "graph1", "node2", emptyMap())).getOrThrow()
        store.save(Checkpoint("cp3", "run-456", "graph1", "node1", emptyMap())).getOrThrow()

        // When: Delete by run
        store.deleteByRun(runId).getOrThrow()

        // Then: Only that run's checkpoints deleted
        val remaining = store.listByGraph("graph1").getOrThrow()
        assertEquals(1, remaining.size)
        assertEquals("run-456", remaining.first().runId)
    }
}

/**
 * Simple test agent that returns a fixed message.
 */
class SimpleAgent(
    override val id: String,
    private val message: String
) : Agent {
    override val name = "SimpleAgent"
    override val description = "Test agent"
    override val capabilities = emptyList<String>()

    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        return SpiceResult.success(comm.reply("$message: ${comm.content}", id))
    }

    override fun canHandle(comm: Comm) = true
    override fun getTools() = emptyList<Tool>()
    override fun isReady() = true
}

/**
 * Agent that always fails.
 */
class FailingAgent(override val id: String) : Agent {
    override val name = "FailingAgent"
    override val description = "Test agent that fails"
    override val capabilities = emptyList<String>()

    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        return SpiceResult.failure(SpiceError.AgentError("Intentional failure", null))
    }

    override fun canHandle(comm: Comm) = true
    override fun getTools() = emptyList<Tool>()
    override fun isReady() = true
}

/**
 * Agent that can be toggled between success and failure.
 */
class ConditionalAgent(override val id: String) : Agent {
    override val name = "ConditionalAgent"
    override val description = "Test agent with conditional failure"
    override val capabilities = emptyList<String>()

    var shouldFail = false

    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        return if (shouldFail) {
            SpiceResult.failure(SpiceError.AgentError("Conditional failure", null))
        } else {
            SpiceResult.success(comm.reply("Success: ${comm.content}", id))
        }
    }

    override fun canHandle(comm: Comm) = true
    override fun getTools() = emptyList<Tool>()
    override fun isReady() = true
}
