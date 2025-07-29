package io.github.noailabs.spice.serialization

import io.github.noailabs.spice.serialization.SpiceSerializer.toJsonElement
import io.github.noailabs.spice.serialization.SpiceSerializer.toJsonObject
import io.github.noailabs.spice.serialization.SpiceSerializer.toJson
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test SpiceSerializer functionality
 */
class SpiceSerializerTest {
    
    @Test
    fun `test toJsonElement handles all types correctly`() {
        // Test null
        assertEquals(JsonNull, null.toJsonElement())
        
        // Test primitives
        assertEquals(JsonPrimitive("hello"), "hello".toJsonElement())
        assertEquals(JsonPrimitive(42), 42.toJsonElement())
        assertEquals(JsonPrimitive(3.14), 3.14.toJsonElement())
        assertEquals(JsonPrimitive(true), true.toJsonElement())
        
        // Test enum
        assertEquals(JsonPrimitive("RUNNABLE"), Thread.State.RUNNABLE.toJsonElement())
        
        // Test list (structure preserved!)
        val list = listOf("a", "b", "c")
        val listJson = list.toJsonElement()
        assertTrue(listJson is JsonArray)
        assertEquals(3, listJson.size)
        assertEquals(JsonPrimitive("a"), listJson[0])
        
        // Test nested map (structure preserved!)
        val map = mapOf(
            "name" to "test",
            "count" to 42,
            "nested" to mapOf("key" to "value")
        )
        val mapJson = map.toJsonElement()
        assertTrue(mapJson is JsonObject)
        assertEquals(JsonPrimitive("test"), mapJson["name"])
        assertEquals(JsonPrimitive(42), mapJson["count"])
        
        val nestedJson = mapJson["nested"]
        assertTrue(nestedJson is JsonObject)
        assertEquals(JsonPrimitive("value"), nestedJson["key"])
    }
    
    @Test
    fun `test toJsonMetadata preserves complex structures`() {
        // The old way would convert these to strings!
        val metadata = mapOf(
            "tags" to listOf("ai", "agent", "tool"),
            "config" to mapOf(
                "timeout" to 30,
                "retries" to 3,
                "features" to listOf("async", "batch")
            ),
            "metrics" to mapOf(
                "scores" to listOf(0.95, 0.87, 0.91)
            )
        )
        
        val jsonMetadata = SpiceSerializer.toJsonMetadata(metadata)
        
        // Verify list is preserved as JsonArray
        val tags = jsonMetadata["tags"]
        assertTrue(tags is JsonArray)
        assertEquals(3, tags.size)
        assertEquals(JsonPrimitive("ai"), tags[0])
        
        // Verify nested map is preserved
        val config = jsonMetadata["config"]
        assertTrue(config is JsonObject)
        assertEquals(JsonPrimitive(30), config["timeout"])
        
        // Verify nested list in nested map
        val features = config["features"]
        assertTrue(features is JsonArray)
        assertEquals(2, features.size)
        
        // Verify numbers in lists
        val metrics = jsonMetadata["metrics"] as JsonObject
        val scores = metrics["scores"] as JsonArray
        assertEquals(3, scores.size)
        assertEquals(JsonPrimitive(0.95), scores[0])
    }
    
    @Test
    fun `test Map toJsonObject extension`() {
        val map = mapOf(
            "string" to "value",
            "number" to 123,
            "boolean" to false,
            "null" to null,
            "list" to listOf(1, 2, 3),
            "map" to mapOf("nested" to true)
        )
        
        val jsonObject = map.toJsonObject()
        
        assertEquals(JsonPrimitive("value"), jsonObject["string"])
        assertEquals(JsonPrimitive(123), jsonObject["number"])
        assertEquals(JsonPrimitive(false), jsonObject["boolean"])
        assertEquals(JsonNull, jsonObject["null"])
        
        assertTrue(jsonObject["list"] is JsonArray)
        assertTrue(jsonObject["map"] is JsonObject)
    }
    
    @Test
    fun `test VectorStoreConfig serialization`() {
        val config = io.github.noailabs.spice.VectorStoreConfig(
            provider = "qdrant",
            host = "localhost",
            port = 6333,
            apiKey = "secret-key-123",
            collection = "documents",
            vectorSize = 768,
            config = mapOf("distance" to "cosine")
        )
        
        val json = config.toJson()
        
        assertEquals("qdrant", json["provider"]?.jsonPrimitive?.content)
        assertEquals("localhost", json["host"]?.jsonPrimitive?.content)
        assertEquals(6333, json["port"]?.jsonPrimitive?.int)
        assertEquals("[REDACTED]", json["apiKey"]?.jsonPrimitive?.content) // Security!
        assertEquals("documents", json["collection"]?.jsonPrimitive?.content)
        assertEquals(768, json["vectorSize"]?.jsonPrimitive?.int)
        
        val configMap = json["config"] as JsonObject
        assertEquals("cosine", configMap["distance"]?.jsonPrimitive?.content)
    }
    
    @Test
    fun `test instant serialization`() {
        val instant = java.time.Instant.parse("2024-01-01T12:00:00Z")
        val json = instant.toJsonElement()
        
        assertTrue(json is JsonPrimitive)
        assertEquals("2024-01-01T12:00:00Z", json.content)
    }
    
    @Test
    fun `test array conversion`() {
        val array = arrayOf("one", "two", "three")
        val json = array.toJsonElement()
        
        assertTrue(json is JsonArray)
        assertEquals(3, json.size)
        assertEquals(JsonPrimitive("one"), json[0])
        assertEquals(JsonPrimitive("two"), json[1])
        assertEquals(JsonPrimitive("three"), json[2])
    }
} 