package io.github.noailabs.spice.validation

import io.github.noailabs.spice.ExecutionState
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.toolspec.OAIToolCall
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for SpiceMessage validation
 * Focuses on Phase 1: Relaxed validation for event-first architecture
 */
class ValidationTest {

    private val validator = SpiceMessageValidator()

    @Test
    fun `validation passes for message with content only`() {
        val message = SpiceMessage.create(
            content = "Hello, world!",
            from = "user123"
        )

        val result = validator.validate(message)

        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `validation passes for message with toolCalls only and empty content`() {
        val toolCall = OAIToolCall.userInput(
            text = "User input",
            metadata = mapOf("userId" to "user123")
        )

        val message = SpiceMessage(
            content = "", // Empty content
            from = "user123",
            correlationId = SpiceMessage.generateMessageId(),
            toolCalls = listOf(toolCall),
            state = ExecutionState.READY
        )

        val result = validator.validate(message)

        assertTrue(result.isValid, "Message with toolCalls and empty content should be valid")
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `validation passes for message with both content and toolCalls`() {
        val toolCall = OAIToolCall.userResponse(
            text = "Yes",
            responseType = "confirmation"
        )

        val message = SpiceMessage(
            content = "Yes",
            from = "user123",
            correlationId = SpiceMessage.generateMessageId(),
            toolCalls = listOf(toolCall),
            state = ExecutionState.READY
        )

        val result = validator.validate(message)

        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `validation fails for message with empty content and no toolCalls`() {
        val message = SpiceMessage(
            content = "", // Empty
            from = "user123",
            correlationId = SpiceMessage.generateMessageId(),
            toolCalls = emptyList(), // No tool calls
            state = ExecutionState.READY
        )

        val result = validator.validate(message)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == "content" })
        assertTrue(result.errors.any { it.message.contains("Either content or toolCalls must be present") })
    }

    @Test
    fun `validation fails for message with blank content and no toolCalls`() {
        val message = SpiceMessage(
            content = "   ", // Blank
            from = "user123",
            correlationId = SpiceMessage.generateMessageId(),
            toolCalls = emptyList(),
            state = ExecutionState.READY
        )

        val result = validator.validate(message)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == "content" })
    }

    @Test
    fun `validation fails for message with null-like empty content and no toolCalls`() {
        // Since content is non-null String, test with empty string (equivalent to null check)
        val message = SpiceMessage(
            content = "", // Empty (equivalent to null check)
            from = "user123",
            correlationId = SpiceMessage.generateMessageId(),
            toolCalls = emptyList(),
            state = ExecutionState.READY
        )

        val result = validator.validate(message)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == "content" })
    }

    @Test
    fun `validation fails for message with blank correlationId`() {
        val message = SpiceMessage(
            content = "Test",
            from = "user123",
            correlationId = "", // Blank
            state = ExecutionState.READY
        )

        val result = validator.validate(message)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == "correlationId" })
    }

    @Test
    fun `validation validates tool calls within message`() {
        // Create invalid tool call (no "call_" prefix)
        val invalidToolCall = OAIToolCall(
            id = "invalid_id", // Should start with "call_"
            type = "function",
            function = io.github.noailabs.spice.toolspec.ToolCallFunction(
                name = "test_function"
            )
        )

        val message = SpiceMessage(
            content = "",
            from = "user123",
            correlationId = SpiceMessage.generateMessageId(),
            toolCalls = listOf(invalidToolCall),
            state = ExecutionState.READY
        )

        val result = validator.validate(message)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field.startsWith("toolCalls[0]") })
    }

    @Test
    fun `validation passes for fromUserInput message`() {
        val message = SpiceMessage.fromUserInput(
            text = "Hello!",
            userId = "user123",
            metadata = mapOf("sessionId" to "session456")
        )

        val result = validator.validate(message)

        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `validation supports multiple tool calls`() {
        val toolCall1 = OAIToolCall.userInput("Input 1")
        val toolCall2 = OAIToolCall.toolMessage("Progress...", "processor")

        val message = SpiceMessage(
            content = "",
            from = "system",
            correlationId = SpiceMessage.generateMessageId(),
            toolCalls = listOf(toolCall1, toolCall2),
            state = ExecutionState.READY
        )

        val result = validator.validate(message)

        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `validation detects invalid tool calls in list`() {
        val validToolCall = OAIToolCall.userInput("Valid")
        val invalidToolCall = OAIToolCall(
            id = "bad_id", // Invalid - should start with "call_"
            type = "function",
            function = io.github.noailabs.spice.toolspec.ToolCallFunction(name = "test")
        )

        val message = SpiceMessage(
            content = "",
            from = "user",
            correlationId = SpiceMessage.generateMessageId(),
            toolCalls = listOf(validToolCall, invalidToolCall),
            state = ExecutionState.READY
        )

        val result = validator.validate(message)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field.contains("toolCalls[1]") })
    }

    @Test
    fun `event-first message with structured payload validates correctly`() {
        // Simulate a pure event message (e.g., webhook callback)
        val structuredPayload = mapOf(
            "event" to "payment_completed",
            "transaction_id" to "txn_123",
            "amount" to 99.99
        )

        val toolCall = OAIToolCall.userInput(
            text = "", // No text
            metadata = structuredPayload,
            inputType = "webhook"
        )

        val message = SpiceMessage(
            content = "", // No content
            from = "webhook_service",
            correlationId = SpiceMessage.generateMessageId(),
            toolCalls = listOf(toolCall),
            metadata = structuredPayload,
            state = ExecutionState.READY
        )

        val result = validator.validate(message)

        assertTrue(result.isValid, "Pure event messages should be valid")
        assertTrue(result.errors.isEmpty())
    }
}
