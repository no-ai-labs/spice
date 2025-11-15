package io.github.noailabs.spice

import io.github.noailabs.spice.toolspec.OAIToolCall
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for SpiceMessage factory methods
 * Focuses on Phase 1: fromUserInput() factory
 */
class SpiceMessageTest {

    @Test
    fun `fromUserInput creates message with USER_INPUT tool call`() {
        val message = SpiceMessage.fromUserInput(
            text = "Hello, Spice!",
            userId = "user123"
        )

        // Verify basic fields
        assertEquals("Hello, Spice!", message.content)
        assertEquals("user123", message.from)
        assertEquals(ExecutionState.READY, message.state)
        assertNotNull(message.correlationId)

        // Verify tool call
        assertEquals(1, message.toolCalls.size)
        val toolCall = message.toolCalls.first()
        assertEquals("user_input", toolCall.function.name)
        assertEquals("Hello, Spice!", toolCall.function.getArgumentString("text"))
        assertEquals("chat", toolCall.function.getArgumentString("input_type"))
    }

    @Test
    fun `fromUserInput includes metadata in both message and tool call`() {
        val metadata = mapOf(
            "sessionId" to "session456",
            "tenantId" to "tenant789",
            "ip" to "192.168.1.1"
        )

        val message = SpiceMessage.fromUserInput(
            text = "Test",
            userId = "user123",
            metadata = metadata
        )

        // Metadata in message
        assertEquals("session456", message.metadata["sessionId"])
        assertEquals("tenant789", message.metadata["tenantId"])
        assertEquals("192.168.1.1", message.metadata["ip"])

        // Metadata in tool call
        val toolCall = message.toolCalls.first()
        val toolCallMetadata = toolCall.function.getArgumentMap("metadata")
        assertNotNull(toolCallMetadata)
        assertEquals("session456", toolCallMetadata!!["sessionId"])
        assertEquals("tenant789", toolCallMetadata["tenantId"])
        assertEquals("192.168.1.1", toolCallMetadata["ip"])
    }

    @Test
    fun `fromUserInput supports different input types`() {
        val inputTypes = listOf("chat", "form", "voice", "attachment", "webhook")

        inputTypes.forEach { type ->
            val message = SpiceMessage.fromUserInput(
                text = "Test input",
                userId = "user123",
                inputType = type
            )

            val toolCall = message.toolCalls.first()
            assertEquals(type, toolCall.function.getArgumentString("input_type"))
        }
    }

    @Test
    fun `fromUserInput allows custom correlationId`() {
        val customCorrelationId = "custom_correlation_123"

        val message = SpiceMessage.fromUserInput(
            text = "Test",
            userId = "user123",
            correlationId = customCorrelationId
        )

        assertEquals(customCorrelationId, message.correlationId)
    }

    @Test
    fun `fromUserInput generates unique correlationId by default`() {
        val message1 = SpiceMessage.fromUserInput("Test 1", "user1")
        val message2 = SpiceMessage.fromUserInput("Test 2", "user2")

        assertNotEquals(message1.correlationId, message2.correlationId)
    }

    @Test
    fun `fromUserInput message is backward compatible with content field`() {
        val message = SpiceMessage.fromUserInput(
            text = "Test message",
            userId = "user123"
        )

        // Content field should still be populated for backward compatibility
        assertEquals("Test message", message.content)
        assertFalse(message.content.isNullOrBlank())
    }

    @Test
    fun `fromUserInput message can be processed by agents`() {
        val message = SpiceMessage.fromUserInput(
            text = "Process this",
            userId = "user123",
            metadata = mapOf("priority" to "high")
        )

        // Simulate agent processing
        val response = message.reply("Processed", "agent1")

        assertEquals("Processed", response.content)
        assertEquals("agent1", response.from)
        assertEquals(message.correlationId, response.correlationId)
        assertEquals(message.id, response.causationId)
    }

    @Test
    fun `fromUserInput vs create - fromUserInput has tool call`() {
        val fromUserInput = SpiceMessage.fromUserInput("Test", "user123")
        val fromCreate = SpiceMessage.create("Test", "user123")

        // fromUserInput has tool call
        assertEquals(1, fromUserInput.toolCalls.size)
        assertEquals("user_input", fromUserInput.toolCalls.first().function.name)

        // create does not have tool call
        assertEquals(0, fromCreate.toolCalls.size)
    }

    @Test
    fun `multiple fromUserInput messages maintain conversation via correlationId`() {
        val correlationId = "conversation_123"

        val msg1 = SpiceMessage.fromUserInput(
            text = "First message",
            userId = "user123",
            correlationId = correlationId
        )

        val msg2 = SpiceMessage.fromUserInput(
            text = "Second message",
            userId = "user123",
            correlationId = correlationId
        )

        assertEquals(correlationId, msg1.correlationId)
        assertEquals(correlationId, msg2.correlationId)
        assertNotEquals(msg1.id, msg2.id) // Different message IDs
    }

    @Test
    fun `fromUserInput message has unique ID`() {
        val msg1 = SpiceMessage.fromUserInput("Test 1", "user1")
        val msg2 = SpiceMessage.fromUserInput("Test 2", "user2")

        assertNotEquals(msg1.id, msg2.id)
        assertTrue(msg1.id.startsWith("msg_"))
        assertTrue(msg2.id.startsWith("msg_"))
    }

    @Test
    fun `fromUserInput tool call has unique ID`() {
        val msg1 = SpiceMessage.fromUserInput("Test 1", "user1")
        val msg2 = SpiceMessage.fromUserInput("Test 2", "user2")

        val toolCall1 = msg1.toolCalls.first()
        val toolCall2 = msg2.toolCalls.first()

        assertNotEquals(toolCall1.id, toolCall2.id)
        assertTrue(toolCall1.id.startsWith("call_"))
        assertTrue(toolCall2.id.startsWith("call_"))
    }
}
