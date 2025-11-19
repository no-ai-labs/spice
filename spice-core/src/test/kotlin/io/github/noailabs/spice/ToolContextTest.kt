package io.github.noailabs.spice

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

/**
 * Tests for ToolContext and related context classes
 */
class ToolContextTest {

    @Test
    fun `ToolContext from creates context with all sections populated`() {
        val message = SpiceMessage.create("test content", "agent-1")
            .withMetadata(mapOf(
                "userId" to "user123",
                "tenantId" to "tenant456",
                "sessionToken" to "token789",
                "isLoggedIn" to true,
                "traceId" to "trace-abc",
                "spanId" to "span-def",
                "parentSpanId" to "parent-ghi",
                "subgraphDepth" to 2,
                "customField" to "customValue"
            ))
            .withGraphContext("graph-1", "node-1", "run-1")

        val context = ToolContext.from(message, "agent-1")

        // Verify agentId
        assertEquals("agent-1", context.agentId)

        // Verify correlationId
        assertEquals(message.correlationId, context.correlationId)

        // Verify auth context
        assertEquals("user123", context.auth.userId)
        assertEquals("tenant456", context.auth.tenantId)
        assertEquals("token789", context.auth.sessionToken)
        assertTrue(context.auth.isLoggedIn)

        // Verify tracing context
        assertEquals("trace-abc", context.tracing.traceId)
        assertEquals("span-def", context.tracing.spanId)
        assertEquals("parent-ghi", context.tracing.parentSpanId)

        // Verify graph context
        assertEquals("graph-1", context.graph.graphId)
        assertEquals("run-1", context.graph.runId)
        assertEquals("node-1", context.graph.nodeId)
        assertEquals(2, context.graph.subgraphDepth)

        // Verify deprecated fields for backward compatibility
        @Suppress("DEPRECATION")
        assertEquals("user123", context.userId)
        @Suppress("DEPRECATION")
        assertEquals("tenant456", context.tenantId)

        // Verify custom metadata is included but internal fields are filtered
        assertEquals("customValue", context.getMetadata<String>("customField"))
        // Internal fields should NOT be in metadata
        assertNull(context.getMetadata<String>("userId"))
        assertNull(context.getMetadata<String>("traceId"))
    }

    @Test
    fun `ToolContext from handles missing metadata gracefully`() {
        val message = SpiceMessage.create("test", "agent")

        val context = ToolContext.from(message, "agent")

        // Auth context should have nulls
        assertNull(context.auth.userId)
        assertNull(context.auth.tenantId)
        assertNull(context.auth.sessionToken)
        assertFalse(context.auth.isLoggedIn)

        // Tracing context should have nulls
        assertNull(context.tracing.traceId)
        assertNull(context.tracing.spanId)
        assertNull(context.tracing.parentSpanId)

        // Graph context should have nulls/defaults
        assertNull(context.graph.graphId)
        assertNull(context.graph.runId)
        assertNull(context.graph.nodeId)
        assertEquals(0, context.graph.subgraphDepth)
    }

    @Test
    fun `AuthContext requireUserId throws when userId is null`() {
        val auth = AuthContext()

        assertThrows<IllegalStateException> {
            auth.requireUserId()
        }
    }

    @Test
    fun `AuthContext requireUserId returns userId when present`() {
        val auth = AuthContext(userId = "user123")

        assertEquals("user123", auth.requireUserId())
    }

    @Test
    fun `AuthContext requireTenantId throws when tenantId is null`() {
        val auth = AuthContext()

        assertThrows<IllegalStateException> {
            auth.requireTenantId()
        }
    }

    @Test
    fun `AuthContext requireTenantId returns tenantId when present`() {
        val auth = AuthContext(tenantId = "tenant456")

        assertEquals("tenant456", auth.requireTenantId())
    }

    @Test
    fun `TracingContext isEnabled returns true when traceId present`() {
        val tracing = TracingContext(traceId = "trace-123")

        assertTrue(tracing.isEnabled())
    }

    @Test
    fun `TracingContext isEnabled returns false when traceId null`() {
        val tracing = TracingContext()

        assertFalse(tracing.isEnabled())
    }

    @Test
    fun `TracingContext requireTraceId throws when traceId is null`() {
        val tracing = TracingContext()

        assertThrows<IllegalStateException> {
            tracing.requireTraceId()
        }
    }

    @Test
    fun `TracingContext requireTraceId returns traceId when present`() {
        val tracing = TracingContext(traceId = "trace-abc")

        assertEquals("trace-abc", tracing.requireTraceId())
    }

    @Test
    fun `GraphContext isSubgraph returns true when depth greater than 0`() {
        val graph = GraphContext(subgraphDepth = 1)

        assertTrue(graph.isSubgraph())
    }

    @Test
    fun `GraphContext isSubgraph returns false when depth is 0`() {
        val graph = GraphContext(subgraphDepth = 0)

        assertFalse(graph.isSubgraph())
    }

    @Test
    fun `GraphContext requireGraphId throws when graphId is null`() {
        val graph = GraphContext()

        assertThrows<IllegalStateException> {
            graph.requireGraphId()
        }
    }

    @Test
    fun `GraphContext requireGraphId returns graphId when present`() {
        val graph = GraphContext(graphId = "graph-123")

        assertEquals("graph-123", graph.requireGraphId())
    }

    @Test
    fun `GraphContext requireRunId throws when runId is null`() {
        val graph = GraphContext()

        assertThrows<IllegalStateException> {
            graph.requireRunId()
        }
    }

    @Test
    fun `GraphContext requireRunId returns runId when present`() {
        val graph = GraphContext(runId = "run-456")

        assertEquals("run-456", graph.requireRunId())
    }

    @Test
    fun `ToolContext getMetadata returns value for existing key`() {
        val context = ToolContext(
            agentId = "agent",
            metadata = mapOf("key1" to "value1", "key2" to 42)
        )

        assertEquals("value1", context.getMetadata<String>("key1"))
        assertEquals(42, context.getMetadata<Int>("key2"))
    }

    @Test
    fun `ToolContext getMetadata returns null for missing key`() {
        val context = ToolContext(agentId = "agent")

        assertNull(context.getMetadata<String>("nonexistent"))
    }

    @Test
    fun `ToolContext hasMetadata returns correct values`() {
        val context = ToolContext(
            agentId = "agent",
            metadata = mapOf("existing" to "value")
        )

        assertTrue(context.hasMetadata("existing"))
        assertFalse(context.hasMetadata("missing"))
    }

    @Test
    fun `ToolContext backward compatibility with old constructor`() {
        // Old way of creating ToolContext should still work
        @Suppress("DEPRECATION")
        val context = ToolContext(
            agentId = "agent-1",
            userId = "user123",
            tenantId = "tenant456",
            correlationId = "corr789",
            metadata = mapOf("custom" to "value")
        )

        @Suppress("DEPRECATION")
        assertEquals("user123", context.userId)
        @Suppress("DEPRECATION")
        assertEquals("tenant456", context.tenantId)
        assertEquals("corr789", context.correlationId)
        assertEquals("value", context.getMetadata<String>("custom"))
    }

    @Test
    fun `ToolContext metadata filters internal fields but keeps custom ones`() {
        val message = SpiceMessage.create("test", "agent")
            .withMetadata(mapOf(
                "custom1" to "value1",
                "custom2" to 100,
                "userId" to "user123",
                "traceId" to "trace-abc"
            ))

        val context = ToolContext.from(message, "agent")

        // Custom metadata should be accessible
        assertEquals("value1", context.getMetadata<String>("custom1"))
        assertEquals(100, context.getMetadata<Int>("custom2"))

        // Internal fields should be filtered out from metadata
        // (but still accessible via structured contexts)
        assertNull(context.getMetadata<String>("userId"))
        assertNull(context.getMetadata<String>("traceId"))

        // Structured contexts should have the values
        assertEquals("user123", context.auth.userId)
        assertEquals("trace-abc", context.tracing.traceId)
    }

    @Test
    fun `ToolContext metadata filtering preserves encapsulation`() {
        val message = SpiceMessage.create("test", "agent")
            .withMetadata(mapOf(
                "authCheckedAt" to 12345L,
                "spanOperation" to "graph:test",
                "lastNodeDuration" to 100L,
                "totalDuration" to 500L,
                "traceCompleted" to true,
                "customAppField" to "should-be-visible"
            ))

        val context = ToolContext.from(message, "agent")

        // Internal runtime fields should be filtered
        assertNull(context.getMetadata<Long>("authCheckedAt"))
        assertNull(context.getMetadata<String>("spanOperation"))
        assertNull(context.getMetadata<Long>("lastNodeDuration"))
        assertNull(context.getMetadata<Long>("totalDuration"))
        assertNull(context.getMetadata<Boolean>("traceCompleted"))

        // Custom application fields should be visible
        assertEquals("should-be-visible", context.getMetadata<String>("customAppField"))
    }
}
