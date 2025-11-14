package io.github.noailabs.spice.graph.checkpoint

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.dsl.graph
import io.github.noailabs.spice.graph.nodes.HumanOption
import io.github.noailabs.spice.graph.nodes.HumanResponse
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CheckpointIntegrationTest {

    @Test
    fun `test checkpoint saves on HITL`() = runTest {
        // Given: Graph with HumanNode
        val agent1 = SimpleAgent("agent1", "First step")
        val agent2 = SimpleAgent("agent2", "After human input")

        val graph = graph("hitl-test") {
            agent("start", agent1)
            human("select", "Please choose an option", options = listOf(
                HumanOption("opt1", "Option 1"),
                HumanOption("opt2", "Option 2")
            ))
            agent("end", agent2)

            edge("start", "select")
            edge("select", "end")
        }

        val store = InMemoryCheckpointStore()
        val config = CheckpointConfig.DEFAULT

        // When: Execute with checkpoint support
        val runner = DefaultGraphRunner()
        val inputMessage = SpiceMessage.create("Start workflow", "user")

        val result = runner.executeWithCheckpoint(graph, inputMessage, store, config)

        // Then: Execution should pause at WAITING state
        assertTrue(result.isSuccess)
        val finalMessage = (result as SpiceResult.Success).value
        assertEquals(ExecutionState.WAITING, finalMessage.state)

        // Checkpoint should be saved
        val checkpoints = store.listByGraph("hitl-test").getOrThrow()
        assertEquals(1, checkpoints.size)

        val checkpoint = checkpoints.first()
        assertEquals("hitl-test", checkpoint.graphId)
        assertEquals("select", checkpoint.currentNodeId)
        assertNotNull(checkpoint.pendingInteraction)
    }

    @Test
    fun `test resume from checkpoint with human response`() = runTest {
        // Given: Graph with HITL
        val agent1 = SimpleAgent("agent1", "Step 1")
        val agent2 = SimpleAgent("agent2", "Step 2")

        val graph = graph("resume-test") {
            agent("start", agent1)
            human("select", "Select option")
            agent("end", agent2)

            edge("start", "select")
            edge("select", "end")
        }

        val store = InMemoryCheckpointStore()
        val runner = DefaultGraphRunner()

        // When: Execute and pause at HITL
        val inputMessage = SpiceMessage.create("Start", "user")
        val pauseResult = runner.executeWithCheckpoint(graph, inputMessage, store, CheckpointConfig.DEFAULT)
        assertTrue(pauseResult.isSuccess)

        // Get checkpoint ID
        val checkpoints = store.listByGraph("resume-test").getOrThrow()
        assertEquals(1, checkpoints.size)
        val checkpointId = checkpoints.first().id

        // Resume with human response
        val humanResponse = HumanResponse(
            nodeId = "select",
            selectedOption = "opt1"
        )

        val resumeResult = runner.resumeFromCheckpoint(graph, checkpointId, humanResponse, store, CheckpointConfig.DEFAULT)

        // Then: Execution should complete
        assertTrue(resumeResult.isSuccess)
        val finalMessage = (resumeResult as SpiceResult.Success).value
        assertEquals(ExecutionState.COMPLETED, finalMessage.state)

        // Checkpoints should be cleaned up (auto-cleanup enabled)
        val remainingCheckpoints = store.listByGraph("resume-test").getOrThrow()
        assertTrue(remainingCheckpoints.isEmpty())
    }

    @Test
    fun `test checkpoint serialization preserves nested structures`() = runTest {
        // Given: Checkpoint with nested data
        val nestedData = mapOf(
            "simple" to "string",
            "number" to 42,
            "nested_map" to mapOf(
                "inner_key" to "inner_value",
                "inner_number" to 123
            ),
            "list" to listOf(
                mapOf("id" to "1", "name" to "Item 1"),
                mapOf("id" to "2", "name" to "Item 2")
            )
        )

        val checkpoint = Checkpoint(
            id = "test-cp",
            runId = "run-1",
            graphId = "graph-1",
            currentNodeId = "node-1",
            state = nestedData
        )

        val store = InMemoryCheckpointStore()

        // When: Save and load
        store.save(checkpoint).getOrThrow()
        val loaded = store.load("test-cp").getOrThrow()

        // Then: Nested structures preserved
        assertEquals("string", loaded.state["simple"])

        @Suppress("UNCHECKED_CAST")
        val nestedMap = loaded.state["nested_map"] as Map<String, Any>
        assertEquals("inner_value", nestedMap["inner_key"])

        @Suppress("UNCHECKED_CAST")
        val list = loaded.state["list"] as List<Map<String, Any>>
        assertEquals(2, list.size)
        assertEquals("Item 1", list[0]["name"])
    }

    @Test
    fun `test InMemoryCheckpointStore delete expired`() = runTest {
        // Given: Store with expired and valid checkpoints
        val store = InMemoryCheckpointStore()

        val expired = Checkpoint(
            id = "expired",
            runId = "run-1",
            graphId = "graph-1",
            currentNodeId = "node-1",
            expiresAt = kotlinx.datetime.Instant.fromEpochMilliseconds(0) // Past
        )

        val valid = Checkpoint(
            id = "valid",
            runId = "run-2",
            graphId = "graph-1",
            currentNodeId = "node-1",
            expiresAt = kotlinx.datetime.Instant.fromEpochMilliseconds(Long.MAX_VALUE) // Future
        )

        store.save(expired).getOrThrow()
        store.save(valid).getOrThrow()

        // When: Delete expired
        val deletedCount = store.deleteExpired().getOrThrow()

        // Then: Only expired checkpoint deleted
        assertEquals(1, deletedCount)
        assertEquals(1, store.size())
        assertTrue(store.exists("valid"))
        assertTrue(!store.exists("expired"))
    }

    @Test
    fun `test checkpoint config presets`() {
        // Verify config presets are valid
        val default = CheckpointConfig.DEFAULT
        assertTrue(default.saveOnHitl)
        assertEquals(null, default.saveEveryNNodes)
        assertTrue(default.autoCleanup)

        val aggressive = CheckpointConfig.AGGRESSIVE
        assertEquals(1, aggressive.saveEveryNNodes)
        assertTrue(aggressive.saveOnError)

        val minimal = CheckpointConfig.MINIMAL
        assertTrue(minimal.saveOnHitl)
        assertTrue(minimal.autoCleanup)

        val disabled = CheckpointConfig.DISABLED
        assertTrue(!disabled.saveOnHitl)
        assertTrue(!disabled.autoCleanup)
    }

    @Test
    fun `test checkpoint expiration check`() {
        val expired = Checkpoint(
            id = "test",
            runId = "run-1",
            graphId = "graph-1",
            currentNodeId = "node-1",
            expiresAt = kotlinx.datetime.Instant.fromEpochMilliseconds(0)
        )

        assertTrue(expired.isExpired())

        val valid = Checkpoint(
            id = "test2",
            runId = "run-1",
            graphId = "graph-1",
            currentNodeId = "node-1",
            expiresAt = kotlinx.datetime.Instant.fromEpochMilliseconds(Long.MAX_VALUE)
        )

        assertTrue(!valid.isExpired())
    }
}

/**
 * Simple test agent using 1.0 API
 */
class SimpleAgent(
    override val id: String,
    private val message: String
) : Agent {
    override val name = "SimpleAgent"
    override val description = "Test agent"
    override val capabilities = emptyList<String>()

    override suspend fun processMessage(message: SpiceMessage): SpiceResult<SpiceMessage> {
        return SpiceResult.success(
            message.reply("$message: ${message.content}", id)
        )
    }

    override fun canHandle(message: SpiceMessage) = true
    override fun getTools() = emptyList<Tool>()
    override fun isReady() = true
}
