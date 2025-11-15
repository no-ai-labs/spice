package io.github.noailabs.spice.graph.checkpoint

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.toolspec.OAIToolCall
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for Checkpoint (Spice 2.0 tool call based)
 */
class CheckpointTest {

    @Test
    fun `Checkpoint fromMessage extracts pendingToolCall from message`() {
        val toolCall = OAIToolCall.selection(
            items = listOf(mapOf("id" to "opt1", "label" to "Option 1")),
            promptMessage = "Choose"
        )

        val message = SpiceMessage.create("Test", "user")
            .withToolCall(toolCall)
            .withGraphContext("graph1", "node1", "run1")
            .transitionTo(ExecutionState.RUNNING, "Start")
            .transitionTo(ExecutionState.WAITING, "Paused")

        val checkpoint = Checkpoint.fromMessage(message, "graph1", "run1")

        // Verify pendingToolCall extracted
        assertNotNull(checkpoint.pendingToolCall)
        assertEquals("request_user_selection", checkpoint.pendingToolCall!!.function.name)
        assertEquals(toolCall.id, checkpoint.pendingToolCall!!.id)
    }

    @Test
    fun `Checkpoint fromMessage uses lastOrNull for multiple tool calls`() {
        // Simulate loop/retry scenario with multiple tool calls
        val oldToolCall = OAIToolCall.hitl("input", "Old question")
        val newToolCall = OAIToolCall.selection(
            items = listOf(mapOf("id" to "a", "label" to "A")),
            promptMessage = "New question"
        )

        val message = SpiceMessage.create("Test", "user")
            .withToolCalls(listOf(oldToolCall, newToolCall))  // Multiple tool calls
            .withGraphContext("graph1", "node1", "run1")
            .transitionTo(ExecutionState.RUNNING, "Start")
            .transitionTo(ExecutionState.WAITING, "Paused")

        val checkpoint = Checkpoint.fromMessage(message, "graph1", "run1")

        // Should get the LAST (most recent) tool call
        assertNotNull(checkpoint.pendingToolCall)
        assertEquals("request_user_selection", checkpoint.pendingToolCall!!.function.name)
        assertEquals(newToolCall.id, checkpoint.pendingToolCall!!.id)
    }

    @Test
    fun `Checkpoint fromMessage handles no HITL tool calls`() {
        // Message with non-HITL tool calls
        val toolCall = OAIToolCall.completion("Done")

        val message = SpiceMessage.create("Test", "user")
            .withToolCall(toolCall)
            .withGraphContext("graph1", "node1", "run1")
            .transitionTo(ExecutionState.RUNNING, "Start")
            .transitionTo(ExecutionState.WAITING, "Paused")

        val checkpoint = Checkpoint.fromMessage(message, "graph1", "run1")

        // No HITL tool call found
        assertNull(checkpoint.pendingToolCall)
    }

    @Test
    fun `Checkpoint fromMessage extracts REQUEST_USER_CONFIRMATION`() {
        val toolCall = OAIToolCall.confirmation("Are you sure?")

        val message = SpiceMessage.create("Test", "user")
            .withToolCall(toolCall)
            .withGraphContext("graph1", "node1", "run1")
            .transitionTo(ExecutionState.RUNNING, "Start")
            .transitionTo(ExecutionState.WAITING, "Paused")

        val checkpoint = Checkpoint.fromMessage(message, "graph1", "run1")

        assertNotNull(checkpoint.pendingToolCall)
        assertEquals("request_user_confirmation", checkpoint.pendingToolCall!!.function.name)
    }

    @Test
    fun `Checkpoint fromMessage requires WAITING state`() {
        val message = SpiceMessage.create("Test", "user")
            .withGraphContext("graph1", "node1", "run1")
            .transitionTo(ExecutionState.RUNNING, "Running")  // NOT WAITING

        assertThrows(IllegalArgumentException::class.java) {
            Checkpoint.fromMessage(message, "graph1", "run1")
        }
    }

    @Test
    fun `Checkpoint fromMessage requires nodeId`() {
        val message = SpiceMessage.create("Test", "user")
            .transitionTo(ExecutionState.RUNNING, "Start")
            .transitionTo(ExecutionState.WAITING, "Paused")
            // No nodeId set (no withGraphContext called)

        assertThrows(IllegalStateException::class.java) {
            Checkpoint.fromMessage(message, "graph1", "run1")
        }
    }

    @Test
    fun `Checkpoint preserves message data and metadata`() {
        val data = mapOf("key1" to "value1", "key2" to 123)
        val metadata = mapOf("userId" to "user123", "sessionId" to "session456")

        val message = SpiceMessage.create("Test", "user")
            .withData(data)
            .withMetadata(metadata)
            .withGraphContext("graph1", "node1", "run1")
            .transitionTo(ExecutionState.RUNNING, "Start")
            .transitionTo(ExecutionState.WAITING, "Paused")

        val checkpoint = Checkpoint.fromMessage(message, "graph1", "run1")

        assertEquals(data, checkpoint.state)
        assertEquals(metadata, checkpoint.metadata)
    }

    @Test
    fun `Checkpoint responseToolCall can be set via copy`() {
        val pendingCall = OAIToolCall.hitl("input", "Name?")
        val responseCall = OAIToolCall.userResponse(text = "Alice")

        val message = SpiceMessage.create("Test", "user")
            .withToolCall(pendingCall)
            .withGraphContext("graph1", "node1", "run1")
            .transitionTo(ExecutionState.RUNNING, "Start")
            .transitionTo(ExecutionState.WAITING, "Paused")

        val checkpoint = Checkpoint.fromMessage(message, "graph1", "run1")

        // Initially no response
        assertNull(checkpoint.responseToolCall)

        // Update with response
        val updatedCheckpoint = checkpoint.copy(responseToolCall = responseCall)

        assertNotNull(updatedCheckpoint.responseToolCall)
        assertEquals("user_response", updatedCheckpoint.responseToolCall!!.function.name)
        assertEquals(responseCall.id, updatedCheckpoint.responseToolCall!!.id)

        // Original unchanged
        assertNull(checkpoint.responseToolCall)
    }

    @Test
    fun `Checkpoint generates unique IDs`() {
        val message = SpiceMessage.create("Test", "user")
            .withGraphContext("graph1", "node1", "run1")
            .transitionTo(ExecutionState.RUNNING, "Start")
            .transitionTo(ExecutionState.WAITING, "Paused")

        val cp1 = Checkpoint.fromMessage(message, "graph1", "run1")
        val cp2 = Checkpoint.fromMessage(message, "graph1", "run1")

        assertNotEquals(cp1.id, cp2.id)
        assertTrue(cp1.id.startsWith("cp_"))
        assertTrue(cp2.id.startsWith("cp_"))
    }

    @Test
    fun `Checkpoint isExpired works correctly`() {
        val message = SpiceMessage.create("Test", "user")
            .withGraphContext("graph1", "node1", "run1")
            .transitionTo(ExecutionState.RUNNING, "Start")
            .transitionTo(ExecutionState.WAITING, "Paused")

        // No expiration
        val cp1 = Checkpoint.fromMessage(message, "graph1", "run1")
        assertFalse(cp1.isExpired())

        // With future expiration
        val futureExpiration = kotlinx.datetime.Clock.System.now() + kotlin.time.Duration.parse("1h")
        val cp2 = cp1.copy(expiresAt = futureExpiration)
        assertFalse(cp2.isExpired())

        // With past expiration
        val pastExpiration = kotlinx.datetime.Clock.System.now() - kotlin.time.Duration.parse("1h")
        val cp3 = cp1.copy(expiresAt = pastExpiration)
        assertTrue(cp3.isExpired())
    }

    @Test
    fun `Checkpoint stores complete message for replay`() {
        val toolCall = OAIToolCall.selection(
            items = listOf(mapOf("id" to "a", "label" to "A")),
            promptMessage = "Choose"
        )

        val originalMessage = SpiceMessage.create("Original content", "user123")
            .withToolCall(toolCall)
            .withData(mapOf("context" to "test"))
            .withMetadata(mapOf("trace" to "abc"))
            .withGraphContext("graph1", "node1", "run1")
            .transitionTo(ExecutionState.RUNNING, "Start")
            .transitionTo(ExecutionState.WAITING, "Paused")

        val checkpoint = Checkpoint.fromMessage(originalMessage, "graph1", "run1")

        // Verify complete message stored
        assertNotNull(checkpoint.message)
        assertEquals(originalMessage.id, checkpoint.message!!.id)
        assertEquals(originalMessage.content, checkpoint.message!!.content)
        assertEquals(originalMessage.from, checkpoint.message!!.from)
        assertEquals(originalMessage.toolCalls.size, checkpoint.message!!.toolCalls.size)
        assertEquals(ExecutionState.WAITING, checkpoint.message!!.state)
    }
}
