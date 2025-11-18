package io.github.noailabs.spice.graph

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.checkpoint.Checkpoint
import io.github.noailabs.spice.graph.checkpoint.CheckpointStore
import io.github.noailabs.spice.graph.nodes.HumanNode
import io.github.noailabs.spice.graph.nodes.HumanOption
import io.github.noailabs.spice.toolspec.OAIToolCall
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Integration tests for HITL flow with tool calls (Spice 2.0)
 */
class HITLIntegrationTest {

    /**
     * In-memory checkpoint store for testing
     */
    class InMemoryCheckpointStore : CheckpointStore {
        private val checkpoints = mutableMapOf<String, Checkpoint>()

        override suspend fun save(checkpoint: Checkpoint): SpiceResult<String> {
            checkpoints[checkpoint.id] = checkpoint
            return SpiceResult.success(checkpoint.id)
        }

        override suspend fun load(checkpointId: String): SpiceResult<Checkpoint> {
            val checkpoint = checkpoints[checkpointId]
            return if (checkpoint != null) {
                SpiceResult.success(checkpoint)
            } else {
                SpiceResult.failure(
                    SpiceError.CheckpointError(
                        message = "Checkpoint not found: $checkpointId",
                        checkpointId = checkpointId
                    )
                )
            }
        }

        override suspend fun delete(checkpointId: String): SpiceResult<Unit> {
            checkpoints.remove(checkpointId)
            return SpiceResult.success(Unit)
        }

        override suspend fun listByGraph(graphId: String): SpiceResult<List<Checkpoint>> {
            val list = checkpoints.values.filter { it.graphId == graphId }
            return SpiceResult.success(list)
        }

        override suspend fun listByRun(runId: String): SpiceResult<List<Checkpoint>> {
            val list = checkpoints.values.filter { it.runId == runId }
            return SpiceResult.success(list)
        }

        override suspend fun deleteByRun(runId: String): SpiceResult<Unit> {
            checkpoints.entries.removeIf { it.value.runId == runId }
            return SpiceResult.success(Unit)
        }

        override suspend fun deleteExpired(): SpiceResult<Int> {
            val count = checkpoints.values.count { it.isExpired() }
            checkpoints.entries.removeIf { it.value.isExpired() }
            return SpiceResult.success(count)
        }

        override suspend fun exists(checkpointId: String): Boolean {
            return checkpoints.containsKey(checkpointId)
        }

        // Spice 2.0: Tool call ID based queries
        override suspend fun loadByPendingToolCallId(toolCallId: String): SpiceResult<Checkpoint> {
            val checkpoint = checkpoints.values.firstOrNull {
                it.pendingToolCall?.id == toolCallId
            }
            return if (checkpoint != null) {
                SpiceResult.success(checkpoint)
            } else {
                SpiceResult.failure(
                    SpiceError.CheckpointError(
                        message = "No checkpoint found with pending tool call ID: $toolCallId",
                        context = mapOf("toolCallId" to toolCallId)
                    )
                )
            }
        }

        override suspend fun loadByResponseToolCallId(toolCallId: String): SpiceResult<Checkpoint> {
            val checkpoint = checkpoints.values.firstOrNull {
                it.responseToolCall?.id == toolCallId
            }
            return if (checkpoint != null) {
                SpiceResult.success(checkpoint)
            } else {
                SpiceResult.failure(
                    SpiceError.CheckpointError(
                        message = "No checkpoint found with response tool call ID: $toolCallId",
                        context = mapOf("toolCallId" to toolCallId)
                    )
                )
            }
        }

        override suspend fun listByToolCallId(toolCallId: String): SpiceResult<List<Checkpoint>> {
            val list = checkpoints.values.filter {
                it.pendingToolCall?.id == toolCallId || it.responseToolCall?.id == toolCallId
            }
            return SpiceResult.success(list)
        }
    }

    @Test
    fun `HumanNode creates checkpoint with pendingToolCall`() = runTest {
        val node = HumanNode(
            id = "select_node",
            prompt = "Choose an option",
            options = listOf(
                HumanOption("opt1", "Option 1"),
                HumanOption("opt2", "Option 2")
            )
        )

        val inputMessage = SpiceMessage.create("Start", "user")
            .withGraphContext("test_graph", "select_node", "run_123")
            .transitionTo(ExecutionState.RUNNING, "Start")

        // Execute node
        val result = node.run(inputMessage)
        assertTrue(result.isSuccess)

        val outputMessage = result.getOrThrow()
        assertEquals(ExecutionState.WAITING, outputMessage.state)

        // Create checkpoint
        val checkpoint = Checkpoint.fromMessage(outputMessage, "test_graph", "run_123")

        // Verify checkpoint has pendingToolCall
        assertNotNull(checkpoint.pendingToolCall)
        assertEquals("request_user_selection", checkpoint.pendingToolCall!!.function.name)

        // Verify tool call details
        val items = checkpoint.pendingToolCall!!.function.getArgumentList("items")
        assertEquals(2, items?.size)
        assertEquals("Choose an option", checkpoint.pendingToolCall!!.function.getArgumentString("prompt_message"))
    }

    @Test
    fun `Resume with USER_RESPONSE tool call updates checkpoint`() = runTest {
        val store = InMemoryCheckpointStore()

        // Create initial checkpoint with pending tool call
        val pendingToolCall = OAIToolCall.selection(
            items = listOf(mapOf("id" to "opt1", "label" to "Option 1")),
            promptMessage = "Choose"
        )

        val waitingMessage = SpiceMessage.create("Waiting", "system")
            .withToolCall(pendingToolCall)
            .withGraphContext("graph1", "node1", "run1")
            .transitionTo(ExecutionState.RUNNING, "Start")
            .transitionTo(ExecutionState.WAITING, "Paused")

        val checkpoint = Checkpoint.fromMessage(waitingMessage, "graph1", "run1")
        store.save(checkpoint)

        // Create user response with USER_RESPONSE tool call
        val responseToolCall = OAIToolCall.userResponse(
            text = "Option 1",
            structuredData = mapOf("selected_option" to "opt1")
        )

        val userResponseMessage = SpiceMessage.create("Option 1", "user")
            .withToolCall(responseToolCall)

        // Simulate resume logic (extract response and update checkpoint)
        val loadedCheckpoint = store.load(checkpoint.id).getOrThrow()
        assertNull(loadedCheckpoint.responseToolCall)  // Initially null

        // Update checkpoint with response
        val updatedCheckpoint = loadedCheckpoint.copy(responseToolCall = responseToolCall)
        store.save(updatedCheckpoint)

        // Verify response persisted
        val reloadedCheckpoint = store.load(checkpoint.id).getOrThrow()
        assertNotNull(reloadedCheckpoint.responseToolCall)
        assertEquals("user_response", reloadedCheckpoint.responseToolCall!!.function.name)
        assertEquals("Option 1", reloadedCheckpoint.responseToolCall!!.function.getArgumentString("text"))

        val structuredData = reloadedCheckpoint.responseToolCall!!.function.getArgumentMap("structured_data")
        assertEquals("opt1", structuredData!!["selected_option"])
    }

    @Test
    fun `Multiple tool calls uses lastOrNull for checkpoint`() = runTest {
        // Simulate retry scenario
        val firstAttempt = OAIToolCall.hitl("input", "First try")
        val secondAttempt = OAIToolCall.selection(
            items = listOf(mapOf("id" to "a", "label" to "A")),
            promptMessage = "Second try"
        )

        val message = SpiceMessage.create("Test", "user")
            .withToolCalls(listOf(firstAttempt, secondAttempt))
            .withGraphContext("graph1", "node1", "run1")
            .transitionTo(ExecutionState.RUNNING, "Start")
            .transitionTo(ExecutionState.WAITING, "Retry")

        val checkpoint = Checkpoint.fromMessage(message, "graph1", "run1")

        // Should capture the LAST tool call (most recent)
        assertEquals("request_user_selection", checkpoint.pendingToolCall!!.function.name)
        assertEquals(secondAttempt.id, checkpoint.pendingToolCall!!.id)
    }

    @Test
    fun `Checkpoint stores both pending and response tool calls for audit`() = runTest {
        val pendingCall = OAIToolCall.hitl("input", "Enter name")
        val responseCall = OAIToolCall.userResponse(text = "Alice")

        val message = SpiceMessage.create("Waiting", "system")
            .withToolCall(pendingCall)
            .withGraphContext("graph1", "node1", "run1")
            .transitionTo(ExecutionState.RUNNING, "Start")
            .transitionTo(ExecutionState.WAITING, "Paused")

        val checkpoint = Checkpoint.fromMessage(message, "graph1", "run1")
            .copy(responseToolCall = responseCall)

        // Both tool calls stored for complete audit trail
        assertNotNull(checkpoint.pendingToolCall)
        assertNotNull(checkpoint.responseToolCall)

        assertEquals("request_user_input", checkpoint.pendingToolCall!!.function.name)
        assertEquals("user_response", checkpoint.responseToolCall!!.function.name)

        // Can reconstruct the full interaction
        assertEquals("Enter name", checkpoint.pendingToolCall!!.function.getArgumentString("question"))
        assertEquals("Alice", checkpoint.responseToolCall!!.function.getArgumentString("text"))
    }

    @Test
    fun `Tool call IDs enable correlation between request and response`() = runTest {
        val pendingCall = OAIToolCall.selection(
            items = listOf(mapOf("id" to "a", "label" to "A")),
            promptMessage = "Choose"
        )

        val responseCall = OAIToolCall.userResponse(
            text = "A",
            structuredData = mapOf(
                "selected_option" to "a",
                "responding_to" to pendingCall.id  // Correlation!
            )
        )

        val checkpoint = Checkpoint(
            id = "cp1",
            runId = "run1",
            graphId = "graph1",
            currentNodeId = "node1",
            pendingToolCall = pendingCall,
            responseToolCall = responseCall
        )

        // Can correlate request and response
        val respondingTo = checkpoint.responseToolCall!!.function
            .getArgumentMap("structured_data")!!["responding_to"]

        assertEquals(pendingCall.id, respondingTo)
    }

    @Test
    fun `Free text input with tool call`() = runTest {
        val node = HumanNode(
            id = "text_node",
            prompt = "Enter your feedback",
            options = emptyList()
        )

        val inputMessage = SpiceMessage.create("Start", "user")
            .withGraphContext("graph1", "text_node", "run1")
            .transitionTo(ExecutionState.RUNNING, "Start")

        val result = node.run(inputMessage)
        val outputMessage = result.getOrThrow()

        // Verify REQUEST_USER_INPUT emitted
        val toolCall = outputMessage.findToolCall("request_user_input")
        assertNotNull(toolCall)
        assertEquals("Enter your feedback", toolCall!!.function.getArgumentString("question"))

        // Checkpoint captures it
        val checkpoint = Checkpoint.fromMessage(outputMessage, "graph1", "run1")
        assertEquals("request_user_input", checkpoint.pendingToolCall!!.function.name)

        // User responds
        val userResponse = OAIToolCall.userResponse(
            text = "Great product!",
            responseType = "text"
        )

        val updatedCheckpoint = checkpoint.copy(responseToolCall = userResponse)

        assertEquals("Great product!", updatedCheckpoint.responseToolCall!!.function.getArgumentString("text"))
    }

    @Test
    fun `Checkpoint without HITL tool call has null pendingToolCall`() = runTest {
        // Message with only non-HITL tool calls
        val completionCall = OAIToolCall.completion("Done")

        val message = SpiceMessage.create("Complete", "system")
            .withToolCall(completionCall)
            .withGraphContext("graph1", "node1", "run1")
            .transitionTo(ExecutionState.RUNNING, "Start")
            .transitionTo(ExecutionState.WAITING, "End")

        val checkpoint = Checkpoint.fromMessage(message, "graph1", "run1")

        // No HITL tool call found
        assertNull(checkpoint.pendingToolCall)
    }

    @Test
    fun `Load checkpoint by pending tool call ID`() = runTest {
        val store = InMemoryCheckpointStore()

        val pendingCall = OAIToolCall.selection(
            items = listOf(mapOf("id" to "opt1", "label" to "Option 1")),
            promptMessage = "Choose"
        )

        val message = SpiceMessage.create("Waiting", "system")
            .withToolCall(pendingCall)
            .withGraphContext("graph1", "node1", "run1")
            .transitionTo(ExecutionState.RUNNING, "Start")
            .transitionTo(ExecutionState.WAITING, "Paused")

        val checkpoint = Checkpoint.fromMessage(message, "graph1", "run1")
        store.save(checkpoint)

        // Query by pending tool call ID
        val loaded = store.loadByPendingToolCallId(pendingCall.id).getOrThrow()

        assertEquals(checkpoint.id, loaded.id)
        assertEquals(pendingCall.id, loaded.pendingToolCall!!.id)
    }

    @Test
    fun `Load checkpoint by response tool call ID`() = runTest {
        val store = InMemoryCheckpointStore()

        val pendingCall = OAIToolCall.hitl("input", "Name?")
        val responseCall = OAIToolCall.userResponse(text = "Alice")

        val message = SpiceMessage.create("Waiting", "system")
            .withToolCall(pendingCall)
            .withGraphContext("graph1", "node1", "run1")
            .transitionTo(ExecutionState.RUNNING, "Start")
            .transitionTo(ExecutionState.WAITING, "Paused")

        val checkpoint = Checkpoint.fromMessage(message, "graph1", "run1")
            .copy(responseToolCall = responseCall)

        store.save(checkpoint)

        // Query by response tool call ID
        val loaded = store.loadByResponseToolCallId(responseCall.id).getOrThrow()

        assertEquals(checkpoint.id, loaded.id)
        assertEquals(responseCall.id, loaded.responseToolCall!!.id)
    }

    @Test
    fun `List checkpoints by tool call ID finds both pending and response`() = runTest {
        val store = InMemoryCheckpointStore()

        val toolCall = OAIToolCall.selection(
            items = listOf(mapOf("id" to "a", "label" to "A")),
            promptMessage = "Choose"
        )

        // Checkpoint 1: Tool call as pending
        val message1 = SpiceMessage.create("Waiting", "system")
            .withToolCall(toolCall)
            .withGraphContext("graph1", "node1", "run1")
            .transitionTo(ExecutionState.RUNNING, "Start")
            .transitionTo(ExecutionState.WAITING, "Paused")

        val checkpoint1 = Checkpoint.fromMessage(message1, "graph1", "run1")
        store.save(checkpoint1)

        // Checkpoint 2: Same tool call as response (simulating replay/audit)
        val message2 = SpiceMessage.create("Complete", "system")
            .withGraphContext("graph1", "node2", "run1")
            .transitionTo(ExecutionState.RUNNING, "Resume")
            .transitionTo(ExecutionState.WAITING, "Paused again")

        val checkpoint2 = Checkpoint.fromMessage(message2, "graph1", "run1")
            .copy(responseToolCall = toolCall)  // Same tool call as response

        store.save(checkpoint2)

        // Query by tool call ID - should find both checkpoints
        val found = store.listByToolCallId(toolCall.id).getOrThrow()

        assertEquals(2, found.size)
        assertTrue(found.any { it.id == checkpoint1.id })
        assertTrue(found.any { it.id == checkpoint2.id })
    }

    @Test
    fun `Load by pending tool call ID returns error if not found`() = runTest {
        val store = InMemoryCheckpointStore()

        val result = store.loadByPendingToolCallId("nonexistent_id")

        assertTrue(result.isFailure)
        val error = (result as SpiceResult.Failure).error
        assertEquals("CHECKPOINT_ERROR", error.code)
    }

    @Test
    fun `Load by response tool call ID returns error if not found`() = runTest {
        val store = InMemoryCheckpointStore()

        val result = store.loadByResponseToolCallId("nonexistent_id")

        assertTrue(result.isFailure)
        val error = (result as SpiceResult.Failure).error
        assertEquals("CHECKPOINT_ERROR", error.code)
    }

    @Test
    fun `List by tool call ID returns empty list if not found`() = runTest {
        val store = InMemoryCheckpointStore()

        val result = store.listByToolCallId("nonexistent_id").getOrThrow()

        assertEquals(0, result.size)
    }

    @Test
    fun `Checkpoint cleanup after terminal state`() = runTest {
        val store = InMemoryCheckpointStore()
        val runId = "run_cleanup"

        // Create multiple checkpoints for same run
        val cp1 = Checkpoint(
            id = "cp1",
            runId = runId,
            graphId = "graph1",
            currentNodeId = "node1"
        )
        val cp2 = Checkpoint(
            id = "cp2",
            runId = runId,
            graphId = "graph1",
            currentNodeId = "node2"
        )

        store.save(cp1)
        store.save(cp2)

        // Verify both exist
        val before = store.listByRun(runId).getOrThrow()
        assertEquals(2, before.size)

        // Cleanup all checkpoints for run
        store.deleteByRun(runId)

        // Verify all deleted
        val after = store.listByRun(runId).getOrThrow()
        assertEquals(0, after.size)
    }
}
