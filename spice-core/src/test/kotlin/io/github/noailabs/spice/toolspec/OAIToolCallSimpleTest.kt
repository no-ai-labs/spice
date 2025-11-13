package io.github.noailabs.spice.toolspec

import io.github.noailabs.spice.Comm
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Simple test suite for OpenAI Tool Call Specification
 */
class OAIToolCallSimpleTest {

    @Test
    fun `test OAIToolCall creation with builder`() = runTest {
        val toolCall = toolCall {
            functionName("request_user_selection")
            argument("items", listOf(mapOf("id" to "1", "name" to "Item 1")))
            argument("prompt_message", "Select an item")
        }

        assertEquals("function", toolCall.type)
        assertEquals("request_user_selection", toolCall.getFunctionName())
        assertTrue(toolCall.id.startsWith("call_"))
        assertEquals(24, toolCall.id.removePrefix("call_").length)

        val items = toolCall.function.getArgumentList("items")
        assertNotNull(items)
        assertEquals(1, items?.size)
    }

    @Test
    fun `test OAIToolCall factory - selection`() = runTest {
        val items = listOf(
            mapOf("id" to "1", "name" to "Option 1", "description" to "First option"),
            mapOf("id" to "2", "name" to "Option 2", "description" to "Second option")
        )

        val toolCall = OAIToolCall.selection(
            items = items,
            promptMessage = "Please select an option",
            selectionType = "single",
            metadata = mapOf("priority" to 100)
        )

        assertEquals("request_user_selection", toolCall.getFunctionName())

        val retrievedItems = toolCall.function.getArgumentList("items")
        assertEquals(2, retrievedItems?.size)

        val promptMessage = toolCall.function.getArgumentString("prompt_message")
        assertEquals("Please select an option", promptMessage)

        val metadata = toolCall.function.getArgumentMap("metadata")
        assertEquals(100, metadata?.get("priority"))
    }

    @Test
    fun `test OAIToolCall factory - confirmation`() = runTest {
        val toolCall = OAIToolCall.confirmation(
            message = "Do you want to proceed?",
            options = listOf("Yes", "No", "Cancel"),
            confirmationType = "action_confirmation"
        )

        assertEquals("request_user_confirmation", toolCall.getFunctionName())
        assertEquals("Do you want to proceed?", toolCall.function.getArgumentString("message"))

        val options = toolCall.function.getArgumentList("options")
        assertEquals(3, options?.size)
        assertEquals("Yes", options?.get(0))
    }

    @Test
    fun `test Comm withToolCall - immutable pattern`() = runTest {
        val comm = Comm(content = "Hello", from = "test")

        val toolCall = OAIToolCall.selection(
            items = listOf(mapOf("id" to "1", "name" to "Item 1")),
            promptMessage = "Select"
        )

        val updatedComm = comm.withToolCall(toolCall)

        // Original comm unchanged
        assertFalse(comm.hasToolCalls())

        // Updated comm has tool call
        assertTrue(updatedComm.hasToolCalls())
        assertEquals(1, updatedComm.countToolCalls())
        assertEquals("request_user_selection", updatedComm.getToolCallNames()[0])
    }

    @Test
    fun `test Comm getToolCalls - retrieval and query`() = runTest {
        val comm = Comm(content = "Hello", from = "test")
            .withToolCall(OAIToolCall.selection(listOf(mapOf("id" to "1")), "Select"))
            .withToolCall(OAIToolCall.confirmation("Confirm?"))

        val toolCalls = comm.getToolCalls()
        assertEquals(2, toolCalls.size)

        val selectionCall = comm.findToolCall("request_user_selection")
        assertNotNull(selectionCall)
        assertEquals("Select", selectionCall?.function?.getArgumentString("prompt_message"))

        val names = comm.getToolCallNames()
        assertTrue(names.contains("request_user_selection"))
        assertTrue(names.contains("request_user_confirmation"))
    }

    @Test
    fun `test legacy field migration - menu_text to selection`() = runTest {
        val comm = Comm(
            content = "Hello",
            from = "user",
            data = mapOf(
                "menu_text" to "Option 1\nOption 2\nOption 3"
            )
        )

        val toolCalls = comm.getToolCalls()

        assertEquals(1, toolCalls.size)
        assertEquals("request_user_selection", toolCalls[0].getFunctionName())

        val items = toolCalls[0].function.getArgumentList("items")
        assertNotNull(items)
        assertEquals(3, items?.size)

        assertTrue(comm.hasLegacyFields())
    }

    @Test
    fun `test cleanup legacy fields`() = runTest {
        val comm = Comm(
            content = "Hello",
            from = "user",
            data = mapOf(
                "menu_text" to "Option 1",
                "workflow_message" to "Message",
                "custom_field" to "keep_me"
            )
        )

        assertTrue(comm.hasLegacyFields())

        // Add tool call
        val updatedComm = comm.withToolCall(OAIToolCall.selection(listOf(), "Select"))

        // Cleanup legacy fields
        val cleanComm = updatedComm.cleanupLegacyFields()

        assertFalse(cleanComm.hasLegacyFields())
        assertNull(cleanComm.data["menu_text"])
        assertNull(cleanComm.data["workflow_message"])
        assertEquals("keep_me", cleanComm.data["custom_field"])  // Custom field preserved
    }

    @Test
    fun `test empty tool calls handling`() = runTest {
        val comm = Comm(content = "Hello", from = "test")

        assertFalse(comm.hasToolCalls())
        assertEquals(0, comm.countToolCalls())
        assertEquals(emptyList<OAIToolCall>(), comm.getToolCalls())
        assertNull(comm.findToolCall("any_function"))
        assertEquals(emptyList<String>(), comm.getToolCallNames())
        assertEquals("No tool calls", comm.toolCallsToString())
    }

    @Test
    fun `test ToolCallFunction argument accessors`() = runTest {
        val function = ToolCallFunction(
            name = "test_function",
            arguments = mapOf(
                "string_arg" to "hello",
                "int_arg" to 42,
                "bool_arg" to true,
                "list_arg" to listOf("a", "b", "c"),
                "map_arg" to mapOf("key" to "value")
            )
        )

        assertEquals("hello", function.getArgumentString("string_arg"))
        assertEquals(42, function.getArgumentInt("int_arg"))
        assertEquals(true, function.getArgumentBoolean("bool_arg"))

        val list = function.getArgumentList("list_arg")
        assertEquals(3, list?.size)

        val map = function.getArgumentMap("map_arg")
        assertEquals("value", map?.get("key"))

        assertNull(function.getArgument("nonexistent"))
    }
}
