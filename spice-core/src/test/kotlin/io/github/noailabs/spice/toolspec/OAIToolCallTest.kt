package io.github.noailabs.spice.toolspec

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for OAIToolCall factory methods
 * Focuses on Phase 1: Event-First Architecture
 */
class OAIToolCallTest {

    @Test
    fun `userInput creates tool call with correct structure`() {
        val toolCall = OAIToolCall.userInput(
            text = "Hello, world!",
            metadata = mapOf("userId" to "user123", "sessionId" to "session456"),
            inputType = "chat"
        )

        // Verify structure
        assertEquals("user_input", toolCall.function.name)
        assertEquals("function", toolCall.type)
        assertTrue(toolCall.id.startsWith("call_"))

        // Verify arguments
        assertEquals("Hello, world!", toolCall.function.getArgumentString("text"))
        assertEquals("chat", toolCall.function.getArgumentString("input_type"))

        val metadata = toolCall.function.getArgumentMap("metadata")
        assertNotNull(metadata)
        assertEquals("user123", metadata!!["userId"])
        assertEquals("session456", metadata["sessionId"])
    }

    @Test
    fun `userInput with minimal parameters uses defaults`() {
        val toolCall = OAIToolCall.userInput(
            text = "Simple message"
        )

        assertEquals("user_input", toolCall.function.name)
        assertEquals("Simple message", toolCall.function.getArgumentString("text"))
        assertEquals("chat", toolCall.function.getArgumentString("input_type"))
        assertNull(toolCall.function.getArgument("metadata"))
    }

    @Test
    fun `userInput supports different input types`() {
        val inputTypes = listOf("chat", "form", "voice", "attachment", "webhook")

        inputTypes.forEach { type ->
            val toolCall = OAIToolCall.userInput(
                text = "Test message",
                inputType = type
            )

            assertEquals(type, toolCall.function.getArgumentString("input_type"))
        }
    }

    @Test
    fun `userResponse creates tool call with text only`() {
        val toolCall = OAIToolCall.userResponse(
            text = "Yes, I agree"
        )

        assertEquals("user_response", toolCall.function.name)
        assertEquals("Yes, I agree", toolCall.function.getArgumentString("text"))
        assertEquals("text", toolCall.function.getArgumentString("response_type"))
        assertNull(toolCall.function.getArgument("structured_data"))
    }

    @Test
    fun `userResponse creates tool call with structured data only`() {
        val structuredData = mapOf(
            "selected_option" to "option1",
            "selected_id" to "123",
            "confirmed" to true
        )

        val toolCall = OAIToolCall.userResponse(
            structuredData = structuredData,
            responseType = "selection"
        )

        assertEquals("user_response", toolCall.function.name)
        assertNull(toolCall.function.getArgumentString("text"))

        val data = toolCall.function.getArgumentMap("structured_data")
        assertNotNull(data)
        assertEquals("option1", data!!["selected_option"])
        assertEquals("123", data["selected_id"])
        assertEquals(true, data["confirmed"])
        assertEquals("selection", toolCall.function.getArgumentString("response_type"))
    }

    @Test
    fun `userResponse supports both text and structured data`() {
        val structuredData = mapOf("selection" to "option1")

        val toolCall = OAIToolCall.userResponse(
            text = "I choose option 1",
            structuredData = structuredData,
            responseType = "hybrid"
        )

        assertEquals("I choose option 1", toolCall.function.getArgumentString("text"))
        assertNotNull(toolCall.function.getArgumentMap("structured_data"))
        assertEquals("hybrid", toolCall.function.getArgumentString("response_type"))
    }

    @Test
    fun `userResponse supports different response types`() {
        val responseTypes = listOf("text", "selection", "confirmation", "form")

        responseTypes.forEach { type ->
            val toolCall = OAIToolCall.userResponse(
                text = "Response",
                responseType = type
            )

            assertEquals(type, toolCall.function.getArgumentString("response_type"))
        }
    }

    @Test
    fun `userResponse includes metadata when provided`() {
        val metadata = mapOf("timestamp" to "2025-01-15T10:00:00Z", "nodeId" to "node123")

        val toolCall = OAIToolCall.userResponse(
            text = "Response",
            metadata = metadata
        )

        val storedMetadata = toolCall.function.getArgumentMap("metadata")
        assertNotNull(storedMetadata)
        assertEquals("2025-01-15T10:00:00Z", storedMetadata!!["timestamp"])
        assertEquals("node123", storedMetadata["nodeId"])
    }

    @Test
    fun `tool call IDs are unique`() {
        val toolCall1 = OAIToolCall.userInput("Message 1")
        val toolCall2 = OAIToolCall.userInput("Message 2")
        val toolCall3 = OAIToolCall.userResponse("Response 1")

        assertNotEquals(toolCall1.id, toolCall2.id)
        assertNotEquals(toolCall1.id, toolCall3.id)
        assertNotEquals(toolCall2.id, toolCall3.id)

        // All should start with "call_"
        assertTrue(toolCall1.id.startsWith("call_"))
        assertTrue(toolCall2.id.startsWith("call_"))
        assertTrue(toolCall3.id.startsWith("call_"))
    }

    @Test
    fun `userInput and userResponse are compatible with existing tool calls`() {
        // Ensure new tool calls work alongside existing ones
        val userInput = OAIToolCall.userInput("Hello")
        val selection = OAIToolCall.selection(
            items = listOf(mapOf("id" to "1", "name" to "Option 1")),
            promptMessage = "Choose"
        )
        val completion = OAIToolCall.completion("Done")

        // All should have consistent structure
        assertEquals("function", userInput.type)
        assertEquals("function", selection.type)
        assertEquals("function", completion.type)

        assertTrue(userInput.id.startsWith("call_"))
        assertTrue(selection.id.startsWith("call_"))
        assertTrue(completion.id.startsWith("call_"))
    }
}
