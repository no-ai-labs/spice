package io.github.noailabs.spice.graph.nodes

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlin.time.Duration.Companion.minutes

/**
 * Tests for HumanNode (Spice 2.0 tool call based)
 */
class HumanNodeTest {

    @Test
    fun `HumanNode emits REQUEST_USER_SELECTION tool call when options provided`() = runTest {
        val options = listOf(
            HumanOption("opt1", "Option 1", "First option"),
            HumanOption("opt2", "Option 2", "Second option")
        )

        val node = HumanNode(
            id = "select_node",
            prompt = "Please choose an option",
            options = options
        )

        val inputMessage = SpiceMessage.create("Input", "user")
            .transitionTo(ExecutionState.RUNNING, "Start")

        val result = node.run(inputMessage)

        assertTrue(result.isSuccess)
        val outputMessage = result.getOrThrow()

        // Verify state transition
        assertEquals(ExecutionState.WAITING, outputMessage.state)

        // Verify tool call emitted
        assertEquals(1, outputMessage.toolCalls.size)
        val toolCall = outputMessage.toolCalls.first()

        assertEquals("request_user_selection", toolCall.function.name)

        // Verify prompt message
        assertEquals("Please choose an option", toolCall.function.getArgumentString("prompt_message"))

        // Verify items
        val items = toolCall.function.getArgumentList("items")
        assertNotNull(items)
        assertEquals(2, items!!.size)

        // Verify metadata
        val metadata = toolCall.function.getArgumentMap("metadata")
        assertNotNull(metadata)
        assertEquals("select_node", metadata!!["node_id"])
    }

    @Test
    fun `HumanNode emits REQUEST_USER_INPUT tool call when no options provided`() = runTest {
        val node = HumanNode(
            id = "input_node",
            prompt = "Enter your name",
            options = emptyList()
        )

        val inputMessage = SpiceMessage.create("Input", "user")
            .transitionTo(ExecutionState.RUNNING, "Start")

        val result = node.run(inputMessage)

        assertTrue(result.isSuccess)
        val outputMessage = result.getOrThrow()

        // Verify state transition
        assertEquals(ExecutionState.WAITING, outputMessage.state)

        // Verify tool call emitted
        assertEquals(1, outputMessage.toolCalls.size)
        val toolCall = outputMessage.toolCalls.first()

        assertEquals("request_user_input", toolCall.function.name)

        // Verify question
        assertEquals("Enter your name", toolCall.function.getArgumentString("question"))

        // Verify type
        assertEquals("input", toolCall.function.getArgumentString("type"))

        // Verify context
        val context = toolCall.function.getArgumentMap("context")
        assertNotNull(context)
        assertEquals("input_node", context!!["node_id"])
    }

    @Test
    fun `HumanNode sets metadata with pause information`() = runTest {
        val node = HumanNode(
            id = "meta_node",
            prompt = "Test"
        )

        val inputMessage = SpiceMessage.create("Input", "user")
            .transitionTo(ExecutionState.RUNNING, "Start")

        val result = node.run(inputMessage)
        val outputMessage = result.getOrThrow()

        // Verify metadata
        assertEquals("meta_node", outputMessage.getMetadata<String>("paused_node_id"))
        assertNotNull(outputMessage.getMetadata<String>("paused_at"))
    }

    @Test
    fun `HumanNode handles timeout configuration`() = runTest {
        val timeout = 5.minutes

        val node = HumanNode(
            id = "timeout_node",
            prompt = "Quick choice",
            options = listOf(HumanOption("a", "A")),
            timeout = timeout
        )

        val inputMessage = SpiceMessage.create("Input", "user")
            .transitionTo(ExecutionState.RUNNING, "Start")

        val result = node.run(inputMessage)
        val outputMessage = result.getOrThrow()

        // Verify tool call metadata includes expiration
        val toolCall = outputMessage.toolCalls.first()
        val metadata = toolCall.function.getArgumentMap("metadata")
        assertNotNull(metadata!!["expires_at"])
        // Expires at should be parseable as an Instant
        val expiresAtStr = metadata["expires_at"] as String
        assertTrue(expiresAtStr.isNotEmpty())
    }

    @Test
    fun `HumanNode tool call includes option metadata`() = runTest {
        val options = listOf(
            HumanOption("opt1", "Option 1", "First", mapOf("priority" to "high")),
            HumanOption("opt2", "Option 2", "Second", mapOf("priority" to "low"))
        )

        val node = HumanNode(
            id = "rich_node",
            prompt = "Choose",
            options = options
        )

        val inputMessage = SpiceMessage.create("Input", "user")
            .transitionTo(ExecutionState.RUNNING, "Start")

        val result = node.run(inputMessage)
        val outputMessage = result.getOrThrow()

        val toolCall = outputMessage.toolCalls.first()
        val items = toolCall.function.getArgumentList("items")

        // Verify first item includes all fields
        val firstItem = items!![0] as Map<*, *>
        assertEquals("opt1", firstItem["id"])
        assertEquals("Option 1", firstItem["label"])
        assertEquals("First", firstItem["description"])

        val metadata = firstItem["metadata"] as Map<*, *>
        assertEquals("high", metadata["priority"])
    }

    @Test
    fun `HumanNode with allowFreeText false`() = runTest {
        val node = HumanNode(
            id = "strict_node",
            prompt = "Pick one",
            options = listOf(HumanOption("a", "A")),
            allowFreeText = false
        )

        val inputMessage = SpiceMessage.create("Input", "user")
            .transitionTo(ExecutionState.RUNNING, "Start")

        val result = node.run(inputMessage)
        val outputMessage = result.getOrThrow()

        // Verify allowFreeText in tool call metadata
        val toolCall = outputMessage.toolCalls.first()
        val metadata = toolCall.function.getArgumentMap("metadata")
        assertEquals("false", metadata!!["allow_free_text"])
    }

    @Test
    fun `HumanNode tool call has unique ID`() = runTest {
        val node = HumanNode(id = "unique_node", prompt = "Test")

        val msg1 = SpiceMessage.create("Input1", "user")
            .transitionTo(ExecutionState.RUNNING, "Start")
        val msg2 = SpiceMessage.create("Input2", "user")
            .transitionTo(ExecutionState.RUNNING, "Start")

        val result1 = node.run(msg1)
        val result2 = node.run(msg2)

        val toolCall1 = result1.getOrThrow().toolCalls.first()
        val toolCall2 = result2.getOrThrow().toolCalls.first()

        // Each run generates unique tool call ID
        assertNotEquals(toolCall1.id, toolCall2.id)
        assertTrue(toolCall1.id.startsWith("call_"))
        assertTrue(toolCall2.id.startsWith("call_"))
    }
}
