package io.github.noailabs.spice.event

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.toolspec.OAIToolCall
import io.github.noailabs.spice.toolspec.ToolCallFunction
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolCallEventSanitizerTest {

    private fun createTestEvent(metadata: Map<String, Any>): ToolCallEvent {
        val toolCall = OAIToolCall(
            id = "call_123",
            type = "function",
            function = ToolCallFunction(
                name = "test_tool",
                arguments = emptyMap()
            )
        )
        val message = SpiceMessage.create("test", "user")

        return ToolCallEvent.Emitted(
            toolCall = toolCall,
            message = message,
            emittedBy = "test_node",
            graphId = "test_graph",
            runId = "run_123",
            metadata = metadata
        )
    }

    @Test
    fun `no filtering when config has no filters`() {
        val config = MetadataFilterConfig.NONE
        val metadata = mapOf(
            "key1" to "value1",
            "key2" to "value2",
            "password" to "secret"
        )

        val result = ToolCallEventSanitizer.filterMetadata(metadata, config)

        assertEquals(metadata, result)
    }

    @Test
    fun `whitelist filtering includes only specified keys`() {
        val config = MetadataFilterConfig(
            includeMetadataKeys = setOf("key1", "key2")
        )
        val metadata = mapOf(
            "key1" to "value1",
            "key2" to "value2",
            "key3" to "value3",
            "password" to "secret"
        )

        val result = ToolCallEventSanitizer.filterMetadata(metadata, config)

        assertEquals(2, result.size)
        assertEquals("value1", result["key1"])
        assertEquals("value2", result["key2"])
        assertFalse(result.containsKey("key3"))
        assertFalse(result.containsKey("password"))
    }

    @Test
    fun `blacklist filtering removes specified keys`() {
        val config = MetadataFilterConfig(
            excludeMetadataKeys = setOf("password", "apiKey", "token")
        )
        val metadata = mapOf(
            "key1" to "value1",
            "key2" to "value2",
            "password" to "secret",
            "apiKey" to "api-key-123"
        )

        val result = ToolCallEventSanitizer.filterMetadata(metadata, config)

        assertEquals(2, result.size)
        assertEquals("value1", result["key1"])
        assertEquals("value2", result["key2"])
        assertFalse(result.containsKey("password"))
        assertFalse(result.containsKey("apiKey"))
    }

    @Test
    fun `combined whitelist and blacklist filtering`() {
        val config = MetadataFilterConfig(
            includeMetadataKeys = setOf("action_type", "tool_name", "status", "password"),
            excludeMetadataKeys = setOf("password", "token")
        )
        val metadata = mapOf(
            "action_type" to "escalate",
            "tool_name" to "search",
            "status" to "success",
            "password" to "secret",
            "other" to "data"
        )

        val result = ToolCallEventSanitizer.filterMetadata(metadata, config)

        // Whitelist applied first, then blacklist
        assertEquals(3, result.size)
        assertEquals("escalate", result["action_type"])
        assertEquals("search", result["tool_name"])
        assertEquals("success", result["status"])
        assertFalse(result.containsKey("password"))  // Excluded by blacklist
        assertFalse(result.containsKey("other"))     // Not in whitelist
    }

    @Test
    fun `empty whitelist with no filtering returns original metadata`() {
        val config = MetadataFilterConfig(
            includeMetadataKeys = null,  // null means all keys
            excludeMetadataKeys = null   // null means no exclusions
        )
        val metadata = mapOf(
            "key1" to "value1",
            "key2" to "value2"
        )

        val result = ToolCallEventSanitizer.filterMetadata(metadata, config)

        assertEquals(metadata, result)
    }

    @Test
    fun `sanitize event creates new event with filtered metadata`() {
        val config = MetadataFilterConfig(
            excludeMetadataKeys = setOf("password")
        )
        val originalMetadata = mapOf(
            "action_type" to "escalate",
            "password" to "secret"
        )
        val event = createTestEvent(originalMetadata)

        val sanitizedEvent = ToolCallEventSanitizer.sanitize(event, config)

        // Original event unchanged
        assertEquals(originalMetadata, event.metadata)

        // Sanitized event has filtered metadata
        assertEquals(1, sanitizedEvent.metadata.size)
        assertEquals("escalate", sanitizedEvent.metadata["action_type"])
        assertFalse(sanitizedEvent.metadata.containsKey("password"))
    }

    @Test
    fun `hasFilters returns correct values`() {
        assertFalse(MetadataFilterConfig.NONE.hasFilters())
        assertFalse(MetadataFilterConfig(null, null).hasFilters())
        assertFalse(MetadataFilterConfig(emptySet(), emptySet()).hasFilters())

        assertTrue(MetadataFilterConfig(setOf("key1"), null).hasFilters())
        assertTrue(MetadataFilterConfig(null, setOf("password")).hasFilters())
        assertTrue(MetadataFilterConfig(setOf("key1"), setOf("password")).hasFilters())
    }

    @Test
    fun `SECURITY_EXCLUDE preset filters common sensitive keys`() {
        val config = MetadataFilterConfig.SECURITY_EXCLUDE
        val metadata = mapOf(
            "action_type" to "test",
            "password" to "secret",
            "apiKey" to "key123",
            "token" to "tok123",
            "sessionToken" to "sess123",
            "accessToken" to "acc123",
            "refreshToken" to "ref123",
            "authorization" to "bearer xyz",
            "credential" to "cred123",
            "privateKey" to "pk123",
            "secret" to "shhh"
        )

        val result = ToolCallEventSanitizer.filterMetadata(metadata, config)

        assertEquals(1, result.size)
        assertEquals("test", result["action_type"])
    }

    @Test
    fun `sanitize preserves other event fields`() {
        val config = MetadataFilterConfig(
            excludeMetadataKeys = setOf("password")
        )
        val metadata = mapOf(
            "key1" to "value1",
            "password" to "secret"
        )
        val event = createTestEvent(metadata)

        val sanitizedEvent = ToolCallEventSanitizer.sanitize(event, config) as ToolCallEvent.Emitted

        // All other fields preserved
        val originalEvent = event as ToolCallEvent.Emitted
        assertEquals(originalEvent.toolCall, sanitizedEvent.toolCall)
        assertEquals(originalEvent.message, sanitizedEvent.message)
        assertEquals(originalEvent.emittedBy, sanitizedEvent.emittedBy)
        assertEquals(originalEvent.graphId, sanitizedEvent.graphId)
        assertEquals(originalEvent.runId, sanitizedEvent.runId)
        assertEquals(originalEvent.id, sanitizedEvent.id)
        assertEquals(originalEvent.timestamp, sanitizedEvent.timestamp)
    }
}
