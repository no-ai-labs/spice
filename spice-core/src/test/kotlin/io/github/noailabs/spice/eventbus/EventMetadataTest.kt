package io.github.noailabs.spice.eventbus

import io.github.noailabs.spice.AuthContext
import io.github.noailabs.spice.GraphContext
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.TracingContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for EventMetadata context conversion methods
 */
class EventMetadataTest {

    @Test
    fun `EventMetadata from SpiceMessage extracts all context fields`() {
        val message = SpiceMessage.create("test", "agent")
            .withMetadata(mapOf(
                "userId" to "user123",
                "tenantId" to "tenant456",
                "sessionToken" to "token789",
                "traceId" to "trace-abc",
                "spanId" to "span-def",
                "parentSpanId" to "parent-ghi",
                "isLoggedIn" to true,
                "subgraphDepth" to 2
            ))
            .withGraphContext("graph-1", "node-1", "run-1")

        val metadata = EventMetadata.from(
            message = message,
            source = "test-source",
            priority = 5
        )

        // Verify top-level fields
        assertEquals("test-source", metadata.source)
        assertEquals("user123", metadata.userId)
        assertEquals("tenant456", metadata.tenantId)
        assertEquals("trace-abc", metadata.traceId)
        assertEquals("span-def", metadata.spanId)
        assertEquals(5, metadata.priority)

        // Verify custom fields
        assertEquals("token789", metadata.custom["sessionToken"])
        assertEquals("parent-ghi", metadata.custom["parentSpanId"])
        assertEquals("graph-1", metadata.custom["graphId"])
        assertEquals("run-1", metadata.custom["runId"])
        assertEquals("node-1", metadata.custom["nodeId"])
        assertEquals("2", metadata.custom["subgraphDepth"])
        assertEquals("true", metadata.custom["isLoggedIn"])
    }

    @Test
    fun `EventMetadata from context objects maps correctly`() {
        val auth = AuthContext(
            userId = "user123",
            tenantId = "tenant456",
            sessionToken = "token789",
            isLoggedIn = true
        )

        val tracing = TracingContext(
            traceId = "trace-abc",
            spanId = "span-def",
            parentSpanId = "parent-ghi"
        )

        val graph = GraphContext(
            graphId = "graph-1",
            runId = "run-1",
            nodeId = "node-1",
            subgraphDepth = 3
        )

        val metadata = EventMetadata.from(
            auth = auth,
            tracing = tracing,
            graph = graph,
            source = "test-source",
            priority = 10
        )

        // Verify top-level fields
        assertEquals("test-source", metadata.source)
        assertEquals("user123", metadata.userId)
        assertEquals("tenant456", metadata.tenantId)
        assertEquals("trace-abc", metadata.traceId)
        assertEquals("span-def", metadata.spanId)
        assertEquals(10, metadata.priority)

        // Verify custom fields
        assertEquals("token789", metadata.custom["sessionToken"])
        assertEquals("true", metadata.custom["isLoggedIn"])
        assertEquals("parent-ghi", metadata.custom["parentSpanId"])
        assertEquals("graph-1", metadata.custom["graphId"])
        assertEquals("run-1", metadata.custom["runId"])
        assertEquals("node-1", metadata.custom["nodeId"])
        assertEquals("3", metadata.custom["subgraphDepth"])
    }

    @Test
    fun `EventMetadata toAuthContext extracts auth fields correctly`() {
        val metadata = EventMetadata(
            userId = "user123",
            tenantId = "tenant456",
            custom = mapOf(
                "sessionToken" to "token789",
                "isLoggedIn" to "true"
            )
        )

        val auth = metadata.toAuthContext()

        assertEquals("user123", auth.userId)
        assertEquals("tenant456", auth.tenantId)
        assertEquals("token789", auth.sessionToken)
        assertTrue(auth.isLoggedIn)
    }

    @Test
    fun `EventMetadata toAuthContext defaults isLoggedIn when userId present`() {
        val metadata = EventMetadata(userId = "user123")

        val auth = metadata.toAuthContext()

        assertTrue(auth.isLoggedIn)
    }

    @Test
    fun `EventMetadata toAuthContext defaults isLoggedIn to false when userId absent`() {
        val metadata = EventMetadata()

        val auth = metadata.toAuthContext()

        assertFalse(auth.isLoggedIn)
    }

    @Test
    fun `EventMetadata toTracingContext extracts tracing fields correctly`() {
        val metadata = EventMetadata(
            traceId = "trace-abc",
            spanId = "span-def",
            custom = mapOf("parentSpanId" to "parent-ghi")
        )

        val tracing = metadata.toTracingContext()

        assertEquals("trace-abc", tracing.traceId)
        assertEquals("span-def", tracing.spanId)
        assertEquals("parent-ghi", tracing.parentSpanId)
    }

    @Test
    fun `EventMetadata toGraphContext extracts graph fields correctly`() {
        val metadata = EventMetadata(
            custom = mapOf(
                "graphId" to "graph-1",
                "runId" to "run-1",
                "nodeId" to "node-1",
                "subgraphDepth" to "2"
            )
        )

        val graph = metadata.toGraphContext()

        assertEquals("graph-1", graph.graphId)
        assertEquals("run-1", graph.runId)
        assertEquals("node-1", graph.nodeId)
        assertEquals(2, graph.subgraphDepth)
    }

    @Test
    fun `EventMetadata toGraphContext handles missing subgraphDepth`() {
        val metadata = EventMetadata(
            custom = mapOf("graphId" to "graph-1")
        )

        val graph = metadata.toGraphContext()

        assertEquals("graph-1", graph.graphId)
        assertEquals(0, graph.subgraphDepth)
    }

    @Test
    fun `EventMetadata from handles empty contexts`() {
        val metadata = EventMetadata.from(
            auth = AuthContext(),
            tracing = TracingContext(),
            graph = GraphContext()
        )

        assertNull(metadata.userId)
        assertNull(metadata.tenantId)
        assertNull(metadata.traceId)
        assertNull(metadata.spanId)
        assertEquals("false", metadata.custom["isLoggedIn"])
        assertFalse(metadata.custom.containsKey("graphId"))
        assertFalse(metadata.custom.containsKey("subgraphDepth"))
    }

    @Test
    fun `EventMetadata EMPTY has all nulls and defaults`() {
        val empty = EventMetadata.EMPTY

        assertNull(empty.source)
        assertNull(empty.userId)
        assertNull(empty.tenantId)
        assertNull(empty.traceId)
        assertNull(empty.spanId)
        assertEquals(0, empty.priority)
        assertNull(empty.ttl)
        assertTrue(empty.custom.isEmpty())
    }
}
