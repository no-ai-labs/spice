package io.github.noailabs.spice.extensions.sparql

import io.github.noailabs.spice.AgentContext
import io.github.noailabs.spice.context.ContextExtensionRegistry
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Tests for Named Graphs Context Extension
 */
class NamedGraphsExtensionTest {

    @AfterEach
    fun cleanup() {
        // Clear registry after each test
        ContextExtensionRegistry.clear()
    }

    @Test
    fun `test NamedGraphsExtension adds graphs to context`() = runTest {
        val extension = NamedGraphsExtension { context ->
            listOf("http://example.com/graph1", "http://example.com/graph2")
        }

        val baseContext = AgentContext.of("tenantId" to "TEST")
        val enriched = extension.enrich(baseContext)

        val graphs = enriched.getNamedGraphs()
        assertNotNull(graphs)
        assertEquals(2, graphs.size)
        assertEquals("http://example.com/graph1", graphs[0])
        assertEquals("http://example.com/graph2", graphs[1])
    }

    @Test
    fun `test NamedGraphsExtension with tenant-only strategy`() = runTest {
        val extension = NamedGraphsExtension(
            NamedGraphsStrategies.tenantOnly("http://example.com/graphs")
        )

        val context = AgentContext.of("tenantId" to "CHIC")
        val enriched = extension.enrich(context)

        val graphs = enriched.getNamedGraphs()
        assertNotNull(graphs)
        assertEquals(1, graphs.size)
        assertEquals("http://example.com/graphs/tenant/CHIC", graphs[0])
    }

    @Test
    fun `test NamedGraphsExtension with tenant + shared strategy`() = runTest {
        val extension = NamedGraphsExtension(
            NamedGraphsStrategies.tenantWithShared("http://example.com/graphs")
        )

        val context = AgentContext.of("tenantId" to "ACME")
        val enriched = extension.enrich(context)

        val graphs = enriched.getNamedGraphs()
        assertNotNull(graphs)
        assertEquals(2, graphs.size)
        assertEquals("http://example.com/graphs/tenant/ACME", graphs[0])
        assertEquals("http://example.com/graphs/shared", graphs[1])
    }

    @Test
    fun `test NamedGraphsExtension with hierarchical strategy`() = runTest {
        val extension = NamedGraphsExtension(
            NamedGraphsStrategies.hierarchical("http://example.com/graphs")
        )

        val context = AgentContext.of(
            "tenantId" to "CHIC",
            "userId" to "user-123"
        )
        val enriched = extension.enrich(context)

        val graphs = enriched.getNamedGraphs()
        assertNotNull(graphs)
        assertEquals(3, graphs.size)
        assertEquals("http://example.com/graphs/user/user-123", graphs[0])
        assertEquals("http://example.com/graphs/tenant/CHIC", graphs[1])
        assertEquals("http://example.com/graphs/shared", graphs[2])
    }

    @Test
    fun `test NamedGraphsExtension with type-partitioned strategy`() = runTest {
        val types = listOf("policy", "product", "order")
        val extension = NamedGraphsExtension(
            NamedGraphsStrategies.typePartitioned("http://example.com/graphs", types)
        )

        val context = AgentContext.of("tenantId" to "CHIC")
        val enriched = extension.enrich(context)

        val graphs = enriched.getNamedGraphs()
        assertNotNull(graphs)
        assertEquals(3, graphs.size)
        assertEquals("http://example.com/graphs/tenant/CHIC/policy", graphs[0])
        assertEquals("http://example.com/graphs/tenant/CHIC/product", graphs[1])
        assertEquals("http://example.com/graphs/tenant/CHIC/order", graphs[2])
    }

    @Test
    fun `test NamedGraphsExtension with session-based strategy`() = runTest {
        val extension = NamedGraphsExtension(
            NamedGraphsStrategies.sessionBased("http://example.com/graphs")
        )

        val context = AgentContext.of(
            "tenantId" to "CHIC",
            "sessionId" to "session-456"
        )
        val enriched = extension.enrich(context)

        val graphs = enriched.getNamedGraphs()
        assertNotNull(graphs)
        assertEquals(2, graphs.size)
        assertEquals("http://example.com/graphs/session/session-456", graphs[0])
        assertEquals("http://example.com/graphs/tenant/CHIC", graphs[1])
    }

    @Test
    fun `test Named Graphs Builder helpers`() {
        val baseUri = "http://example.com/graphs"

        assertEquals(
            "http://example.com/graphs/tenant/CHIC",
            NamedGraphsBuilder.tenantGraph(baseUri, "CHIC")
        )

        assertEquals(
            "http://example.com/graphs/user/user-123",
            NamedGraphsBuilder.userGraph(baseUri, "user-123")
        )

        assertEquals(
            "http://example.com/graphs/session/session-456",
            NamedGraphsBuilder.sessionGraph(baseUri, "session-456")
        )

        assertEquals(
            "http://example.com/graphs/type/policy",
            NamedGraphsBuilder.typeGraph(baseUri, "policy")
        )

        assertEquals(
            "http://example.com/graphs/shared",
            NamedGraphsBuilder.sharedGraph(baseUri)
        )

        assertEquals(
            "http://example.com/graphs/tenant/CHIC/policy",
            NamedGraphsBuilder.compositeGraph(baseUri, "tenant", "CHIC", "policy")
        )
    }

    @Test
    fun `test namedGraphsExtension DSL with tenant-only`() = runTest {
        val extension = namedGraphsExtension {
            baseUri = "http://kaibrain.com/graphs"
            tenantOnly()
        }

        val context = AgentContext.of("tenantId" to "TEST")
        val enriched = extension.enrich(context)

        val graphs = enriched.getNamedGraphs()
        assertNotNull(graphs)
        assertEquals(1, graphs.size)
        assertEquals("http://kaibrain.com/graphs/tenant/TEST", graphs[0])
    }

    @Test
    fun `test namedGraphsExtension DSL with custom strategy`() = runTest {
        val extension = namedGraphsExtension {
            baseUri = "http://kaibrain.com/graphs"

            strategy { context ->
                buildList {
                    context.userId?.let { add(userGraph(it)) }
                    context.tenantId?.let { add(tenantGraph(it)) }
                    add(shared())
                }
            }
        }

        val context = AgentContext.of(
            "tenantId" to "CHIC",
            "userId" to "user-123"
        )
        val enriched = extension.enrich(context)

        val graphs = enriched.getNamedGraphs()
        assertNotNull(graphs)
        assertEquals(3, graphs.size)
        assertEquals("http://kaibrain.com/graphs/user/user-123", graphs[0])
        assertEquals("http://kaibrain.com/graphs/tenant/CHIC", graphs[1])
        assertEquals("http://kaibrain.com/graphs/shared", graphs[2])
    }

    @Test
    fun `test extension registration and enrichment`() = runTest {
        val extension = namedGraphsExtension {
            baseUri = "http://example.com/graphs"
            tenantWithShared()
        }

        ContextExtensionRegistry.register(extension)

        val baseContext = AgentContext.of("tenantId" to "TEST")
        val enriched = ContextExtensionRegistry.enrichContext(baseContext)

        val graphs = enriched.getNamedGraphs()
        assertNotNull(graphs)
        assertEquals(2, graphs.size)
        assertTrue(graphs.contains("http://example.com/graphs/tenant/TEST"))
        assertTrue(graphs.contains("http://example.com/graphs/shared"))
    }

    @Test
    fun `test extension handles errors gracefully`() = runTest {
        val extension = NamedGraphsExtension { context ->
            throw RuntimeException("Test error")
        }

        val baseContext = AgentContext.of("tenantId" to "TEST")
        val enriched = extension.enrich(baseContext)

        // Should return original context on error
        assertNull(enriched.getNamedGraphs())
    }

    @Test
    fun `test empty tenant returns empty graphs for tenant-only`() = runTest {
        val extension = NamedGraphsExtension(
            NamedGraphsStrategies.tenantOnly("http://example.com/graphs")
        )

        val context = AgentContext.of()  // No tenantId
        val enriched = extension.enrich(context)

        val graphs = enriched.getNamedGraphs()
        // Should be empty list
        assertTrue(graphs == null || graphs.isEmpty())
    }

    @Test
    fun `test tenant-with-shared returns only shared when no tenant`() = runTest {
        val extension = NamedGraphsExtension(
            NamedGraphsStrategies.tenantWithShared("http://example.com/graphs")
        )

        val context = AgentContext.of()  // No tenantId
        val enriched = extension.enrich(context)

        val graphs = enriched.getNamedGraphs()
        assertNotNull(graphs)
        assertEquals(1, graphs.size)
        assertEquals("http://example.com/graphs/shared", graphs[0])
    }

    @Test
    fun `test withFallback strategy uses fallback on error`() = runTest {
        val primary: suspend (AgentContext) -> List<String> = { context ->
            throw RuntimeException("Primary failed")
        }

        val fallback = NamedGraphsStrategies.tenantOnly("http://fallback.com/graphs")
        val strategy = NamedGraphsStrategies.withFallback(primary, fallback)

        val extension = NamedGraphsExtension(strategy)
        val context = AgentContext.of("tenantId" to "TEST")
        val enriched = extension.enrich(context)

        val graphs = enriched.getNamedGraphs()
        assertNotNull(graphs)
        assertEquals(1, graphs.size)
        assertEquals("http://fallback.com/graphs/tenant/TEST", graphs[0])
    }

    @Test
    fun `test withFallback strategy uses fallback on empty result`() = runTest {
        val primary: suspend (AgentContext) -> List<String> = { context ->
            emptyList()  // Returns empty
        }

        val fallback = NamedGraphsStrategies.tenantOnly("http://fallback.com/graphs")
        val strategy = NamedGraphsStrategies.withFallback(primary, fallback)

        val extension = NamedGraphsExtension(strategy)
        val context = AgentContext.of("tenantId" to "TEST")
        val enriched = extension.enrich(context)

        val graphs = enriched.getNamedGraphs()
        assertNotNull(graphs)
        assertEquals(1, graphs.size)
        assertEquals("http://fallback.com/graphs/tenant/TEST", graphs[0])
    }

    @Test
    fun `test extension key is correct`() {
        val extension = namedGraphsExtension {
            tenantOnly()
        }

        assertEquals("named-graphs", extension.key)
    }
}
