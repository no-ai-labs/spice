package io.github.noailabs.spice.mcp

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Disabled
import kotlin.test.*

/**
 * 🧪 Test Suite for MCP Client
 * 
 * Note: These tests require a running MCP server or will be mocked
 */
class MCPClientTest {
    
    @Test
    fun `test Map to JsonObject conversion`() {
        val map = mapOf(
            "string" to "hello",
            "number" to 42,
            "boolean" to true,
            "null" to null,
            "list" to listOf(1, 2, 3),
            "nested" to mapOf("key" to "value")
        )
        
        val json = map.toJsonObject()
        
        assertEquals("hello", json["string"]?.jsonPrimitive?.content)
        assertEquals(42, json["number"]?.jsonPrimitive?.int)
        assertEquals(true, json["boolean"]?.jsonPrimitive?.boolean)
        assertTrue(json["null"] is JsonNull)
        
        val list = json["list"]?.jsonArray
        assertNotNull(list)
        assertEquals(3, list.size)
        assertEquals(1, list[0].jsonPrimitive.int)
        
        val nested = json["nested"]?.jsonObject
        assertNotNull(nested)
        assertEquals("value", nested["key"]?.jsonPrimitive?.content)
    }
    
    @Test
    fun `test MCPTool data class`() {
        val tool = MCPTool(
            name = "test_tool",
            description = "A test tool",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("input") {
                        put("type", "string")
                    }
                }
            }
        )
        
        assertEquals("test_tool", tool.name)
        assertEquals("A test tool", tool.description)
        assertNotNull(tool.parameters)
    }
    
    @Disabled("Requires actual MCP server")
    @Test
    fun `test MCPClient initialization`() = runBlocking {
        val client = MCPClient(
            serverUrl = "http://localhost:8080",
            apiKey = "test-key"
        )
        
        // Would test actual server connection
        client.close()
    }
    
    @Test
    fun `test complex Map conversion with various types`() {
        val complexMap = mapOf(
            "id" to "123",
            "count" to 42L,
            "ratio" to 3.14,
            "items" to listOf(
                mapOf("name" to "item1", "value" to 10),
                mapOf("name" to "item2", "value" to 20)
            ),
            "metadata" to mapOf(
                "created" to "2024-01-01",
                "tags" to listOf("tag1", "tag2"),
                "active" to true
            ),
            "empty" to emptyList<String>(),
            "nullValue" to null
        )
        
        val json = complexMap.toJsonObject()
        
        // Verify structure
        assertEquals("123", json["id"]?.jsonPrimitive?.content)
        assertEquals(42L, json["count"]?.jsonPrimitive?.long)
        assertEquals(3.14, json["ratio"]?.jsonPrimitive?.double)
        
        // Verify nested list of maps
        val items = json["items"]?.jsonArray
        assertNotNull(items)
        assertEquals(2, items.size)
        
        val item1 = items[0].jsonObject
        assertEquals("item1", item1["name"]?.jsonPrimitive?.content)
        assertEquals(10, item1["value"]?.jsonPrimitive?.int)
        
        // Verify nested map
        val metadata = json["metadata"]?.jsonObject
        assertNotNull(metadata)
        assertEquals("2024-01-01", metadata["created"]?.jsonPrimitive?.content)
        
        val tags = metadata["tags"]?.jsonArray
        assertNotNull(tags)
        assertEquals(2, tags.size)
        assertEquals("tag1", tags[0].jsonPrimitive.content)
        
        // Verify empty list
        val emptyList = json["empty"]?.jsonArray
        assertNotNull(emptyList)
        assertEquals(0, emptyList.size)
        
        // Verify null
        assertTrue(json["nullValue"] is JsonNull)
    }
}