package io.github.spice

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.test.assertFalse

class MessageJsonSerializationTest {

    @Test
    fun `basic Message JSON serialization test`() {
        // Given: Simple Message
        val message = Message(
            id = "test-123",
            type = MessageType.TEXT,
            content = "Hello, World!",
            agentId = "test-agent",
            timestamp = 1234567890L,
            metadata = mapOf("key" to "value")
        )

        // When: Serialize to JSON
        val json = Json.encodeToString(message)

        // Then: JSON should contain all fields
        assertTrue(json.contains("\"id\":\"test-123\""))
        assertTrue(json.contains("\"type\":\"TEXT\""))
        assertTrue(json.contains("\"content\":\"Hello, World!\""))
        assertTrue(json.contains("\"agentId\":\"test-agent\""))
        assertTrue(json.contains("\"timestamp\":1234567890"))
        assertTrue(json.contains("\"metadata\":{\"key\":\"value\"}"))

        println("Serialized JSON: $json")
    }

    @Test
    fun `Message JSON deserialization test`() {
        // Given: JSON string
        val json = """
            {
                "id": "test-456",
                "type": "DATA",
                "content": "Test data",
                "agentId": "data-agent",
                "timestamp": 9876543210,
                "metadata": {"source": "test"}
            }
        """.trimIndent()

        // When: Deserialize from JSON
        val message = Json.decodeFromString<Message>(json)

        // Then: Verify same as original
        assertEquals("test-456", message.id)
        assertEquals(MessageType.DATA, message.type)
        assertEquals("Test data", message.content)
        assertEquals("data-agent", message.agentId)
        assertEquals(9876543210L, message.timestamp)
        assertEquals(mapOf("source" to "test"), message.metadata)
    }

    @Test
    fun `all MessageType JSON serialization test`() {
        // Given: Messages with all types
        val messages = MessageType.values().map { type ->
            Message(
                id = "test-${type.name}",
                type = type,
                content = "Content for $type",
                agentId = "test-agent"
            )
        }

        // When: Serialize and deserialize
        val json = Json.encodeToString(messages)
        val deserializedMessages = Json.decodeFromString<List<Message>>(json)

        // Then: MessageType should be preserved correctly
        assertEquals(messages.size, deserializedMessages.size)
        messages.zip(deserializedMessages).forEach { (original, deserialized) ->
            assertEquals(original.type, deserialized.type)
            assertEquals(original.content, deserialized.content)
        }
    }

    @Test
    fun `Message with complex metadata JSON serialization test`() {
        // Given: Message with complex metadata
        val complexMetadata = mapOf(
            "stringValue" to "test",
            "numberValue" to 42,
            "booleanValue" to true,
            "listValue" to listOf("a", "b", "c"),
            "mapValue" to mapOf(
                "nested" to "value",
                "count" to 10
            )
        )

        val message = Message(
            id = "complex-test",
            type = MessageType.TOOL_CALL,
            content = "Complex metadata test",
            agentId = "complex-agent",
            metadata = complexMetadata
        )

        // When: Serialize to JSON
        val json = Json.encodeToString(message)
        val deserializedMessage = Json.decodeFromString<Message>(json)

        // Then: All complex data should be preserved
        assertEquals(message.id, deserializedMessage.id)
        assertEquals(message.type, deserializedMessage.type)
        assertEquals(message.content, deserializedMessage.content)
        assertEquals(message.agentId, deserializedMessage.agentId)

        // Detailed metadata verification
        assertEquals("test", deserializedMessage.metadata["stringValue"])
        assertEquals(42, deserializedMessage.metadata["numberValue"])
        assertEquals(true, deserializedMessage.metadata["booleanValue"])
        
        // List and Map require special handling as they come as JsonElement
        assertTrue(deserializedMessage.metadata.containsKey("listValue"))
        assertTrue(deserializedMessage.metadata.containsKey("mapValue"))
    }

    @Test
    fun `Message chain JSON serialization test`() {
        // Given: Message chain (parent-child relationship)
        val parentMessage = Message(
            id = "parent-1",
            type = MessageType.TEXT,
            content = "Parent message",
            agentId = "parent-agent"
        )

        val childMessage = Message(
            id = "child-1",
            type = MessageType.TEXT,
            content = "Child message",
            agentId = "child-agent",
            parentId = "parent-1"
        )

        val messageChain = listOf(parentMessage, childMessage)

        // When: Serialize Message chain to JSON array
        val json = Json.encodeToString(messageChain)
        val deserializedChain = Json.decodeFromString<List<Message>>(json)

        // Then: Chain structure should be preserved
        assertEquals(2, deserializedChain.size)
        
        val deserializedParent = deserializedChain[0]
        val deserializedChild = deserializedChain[1]
        
        assertEquals("parent-1", deserializedParent.id)
        assertEquals(null, deserializedParent.parentId)
        
        assertEquals("child-1", deserializedChild.id)
        assertEquals("parent-1", deserializedChild.parentId)
    }

    @Test
    fun `ToolResult JSON serialization test`() {
        // Given: Success and failure ToolResult
        val successResult = ToolResult(
            success = true,
            data = mapOf(
                "result" to "Operation completed",
                "count" to 5
            )
        )

        val failureResult = ToolResult(
            success = false,
            error = "Operation failed"
        )

        // When: Serialize to JSON
        val successJson = Json.encodeToString(successResult)
        val failureJson = Json.encodeToString(failureResult)

        val deserializedSuccess = Json.decodeFromString<ToolResult>(successJson)
        val deserializedFailure = Json.decodeFromString<ToolResult>(failureJson)

        // Then: ToolResult data should be preserved
        assertTrue(deserializedSuccess.success)
        // Success case
        assertEquals("Operation completed", deserializedSuccess.data["result"])
        assertEquals(5, deserializedSuccess.data["count"])
        assertNull(deserializedSuccess.error)

        // Failure case
        assertFalse(deserializedFailure.success)
        assertEquals("Operation failed", deserializedFailure.error)
        assertTrue(deserializedFailure.data.isEmpty())
    }

    @Test
    fun `ToolSchema JSON serialization test`() {
        // Given: ToolSchema with parameters
        val toolSchema = ToolSchema(
            name = "test_tool",
            description = "Test tool for serialization",
            parameters = mapOf(
                "input" to "string",
                "count" to "number",
                "enabled" to "boolean"
            )
        )

        // When: Serialize to JSON
        val json = Json.encodeToString(toolSchema)
        val deserializedSchema = Json.decodeFromString<ToolSchema>(json)

        // Then: ToolSchema data should be preserved
        assertEquals("test_tool", deserializedSchema.name)
        assertEquals("Test tool for serialization", deserializedSchema.description)
        assertEquals(3, deserializedSchema.parameters.size)
        assertEquals("string", deserializedSchema.parameters["input"])
        assertEquals("number", deserializedSchema.parameters["count"])
        assertEquals("boolean", deserializedSchema.parameters["enabled"])
    }

    @Test
    fun `Message creation from JSON and AgentEngine integration test`() {
        // Given: Message creation from JSON string
        val jsonMessage = """
            {
                "id": "integration-test",
                "type": "TEXT",
                "content": "Test integration with AgentEngine",
                "agentId": "integration-agent",
                "timestamp": ${System.currentTimeMillis()},
                "metadata": {"test": "integration"}
            }
        """.trimIndent()

        // When: Deserialize Message from JSON
        val message = Json.decodeFromString<Message>(jsonMessage)

        // Then: Message should be valid for AgentEngine
        assertEquals("integration-test", message.id)
        assertEquals(MessageType.TEXT, message.type)
        assertEquals("Test integration with AgentEngine", message.content)
        assertEquals("integration-agent", message.agentId)
        assertEquals("integration", message.metadata["test"])

        // Additional verification: can be serialized again
        val reserializedJson = Json.encodeToString(message)
        val redeserializedMessage = Json.decodeFromString<Message>(reserializedJson)
        
        assertEquals(message.id, redeserializedMessage.id)
        assertEquals(message.type, redeserializedMessage.type)
        assertEquals(message.content, redeserializedMessage.content)

        // Verify data integrity after deserialization
        assertTrue(redeserializedMessage.metadata.containsKey("test"))
        assertEquals("integration", redeserializedMessage.metadata["test"])
    }
} 