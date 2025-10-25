package io.github.noailabs.spice.performance

import io.github.noailabs.spice.*
import io.github.noailabs.spice.dsl.contextAwareTool
import io.github.noailabs.spice.dsl.withAgentContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for CachedTool
 */
class CachedToolTest {

    private lateinit var executionCount: AtomicInteger
    private lateinit var expensiveTool: Tool

    @BeforeEach
    fun setup() {
        executionCount = AtomicInteger(0)

        // Create an expensive tool that we want to cache
        expensiveTool = SimpleTool(
            name = "expensive_query",
            description = "Simulates expensive database query",
            parameterSchemas = mapOf(
                "id" to ParameterSchema("string", "Entity ID", true)
            )
        ) { params ->
            // Simulate expensive operation
            executionCount.incrementAndGet()
            Thread.sleep(100)  // Simulate latency
            val id = params["id"] as String
            ToolResult.success("Result for $id")
        }
    }

    @Test
    fun `test basic caching - cache hit`() = runBlocking {
        val cachedTool = CachedTool(
            delegate = expensiveTool,
            config = CachedTool.ToolCacheConfig(
                maxSize = 100,
                ttl = 10
            )
        )

        // First call - cache miss
        val result1 = cachedTool.execute(mapOf("id" to "123"))
        assertTrue(result1.isSuccess)
        assertEquals(1, executionCount.get())

        // Second call with same params - cache hit
        val result2 = cachedTool.execute(mapOf("id" to "123"))
        assertTrue(result2.isSuccess)
        assertEquals(1, executionCount.get())  // Still 1, no new execution

        // Third call - cache hit
        val result3 = cachedTool.execute(mapOf("id" to "123"))
        assertTrue(result3.isSuccess)
        assertEquals(1, executionCount.get())

        // Verify cache stats
        val stats = cachedTool.getCacheStats()
        assertEquals(2, stats.hits)
        assertEquals(1, stats.misses)
        assertTrue(stats.hitRate > 0.66)  // 2/3 = 0.666...
    }

    @Test
    fun `test cache miss on different parameters`() = runBlocking {
        val cachedTool = CachedTool(expensiveTool)

        // Three different calls
        cachedTool.execute(mapOf("id" to "123"))
        cachedTool.execute(mapOf("id" to "456"))
        cachedTool.execute(mapOf("id" to "789"))

        assertEquals(3, executionCount.get())

        val stats = cachedTool.getCacheStats()
        assertEquals(0, stats.hits)
        assertEquals(3, stats.misses)
        assertEquals(0.0, stats.hitRate)
    }

    @Test
    fun `test TTL expiration`() = runBlocking {
        val cachedTool = CachedTool(
            expensiveTool,
            CachedTool.ToolCacheConfig(ttl = 1)  // 1 second TTL
        )

        // First call
        cachedTool.execute(mapOf("id" to "123"))
        assertEquals(1, executionCount.get())

        // Immediate second call - cache hit
        cachedTool.execute(mapOf("id" to "123"))
        assertEquals(1, executionCount.get())

        // Wait for TTL to expire
        delay(1100)

        // Third call after expiration - cache miss
        cachedTool.execute(mapOf("id" to "123"))
        assertEquals(2, executionCount.get())
    }

    @Test
    fun `test LRU eviction`() = runBlocking {
        val cachedTool = CachedTool(
            expensiveTool,
            CachedTool.ToolCacheConfig(maxSize = 2)  // Only cache 2 entries
        )

        // Fill cache with 2 entries
        cachedTool.execute(mapOf("id" to "1"))
        cachedTool.execute(mapOf("id" to "2"))
        assertEquals(2, executionCount.get())

        // Access "1" to make it more recently used
        cachedTool.execute(mapOf("id" to "1"))
        assertEquals(2, executionCount.get())  // Cache hit

        // Add third entry - should evict "2" (LRU)
        cachedTool.execute(mapOf("id" to "3"))
        assertEquals(3, executionCount.get())

        // Access "1" again - should be cache hit
        cachedTool.execute(mapOf("id" to "1"))
        assertEquals(3, executionCount.get())  // Still cached

        // Access "2" again - should be cache miss (evicted)
        cachedTool.execute(mapOf("id" to "2"))
        assertEquals(4, executionCount.get())  // Re-executed
    }

    @Test
    fun `test custom key builder`() = runBlocking {
        val customKeyBuilder: (Map<String, Any>, AgentContext) -> String = { params, context ->
            // Ignore version parameter in cache key
            val id = params["id"] as String
            val tenantId = context.tenantId ?: "default"
            "$tenantId:$id"
        }

        val cachedTool = CachedTool(
            expensiveTool,
            CachedTool.ToolCacheConfig(keyBuilder = customKeyBuilder)
        )

        val context = AgentContext(mapOf("tenantId" to "TENANT-A"))

        // Call with context
        cachedTool.execute(mapOf("id" to "123", "version" to "v1", "__context" to context))
        assertEquals(1, executionCount.get())

        // Call with different version but same id - should be cache hit
        cachedTool.execute(mapOf("id" to "123", "version" to "v2", "__context" to context))
        assertEquals(1, executionCount.get())  // Cache hit!

        // Different id - cache miss
        cachedTool.execute(mapOf("id" to "456", "version" to "v1", "__context" to context))
        assertEquals(2, executionCount.get())
    }

    @Test
    fun `test clearCache`() = runBlocking {
        val cachedTool = CachedTool(expensiveTool)

        // Fill cache
        cachedTool.execute(mapOf("id" to "123"))
        cachedTool.execute(mapOf("id" to "456"))
        assertEquals(2, executionCount.get())

        // Cache hits
        cachedTool.execute(mapOf("id" to "123"))
        cachedTool.execute(mapOf("id" to "456"))
        assertEquals(2, executionCount.get())

        // Clear cache
        cachedTool.clearCache()

        // Now should be cache misses
        cachedTool.execute(mapOf("id" to "123"))
        cachedTool.execute(mapOf("id" to "456"))
        assertEquals(4, executionCount.get())

        // Stats should be reset
        val stats = cachedTool.getCacheStats()
        assertEquals(0, stats.hits)
        assertEquals(2, stats.misses)
    }

    @Test
    fun `test contextAwareTool with cache DSL`() = runBlocking {
        val callCount = AtomicInteger(0)

        val tool = contextAwareTool("cached_lookup") {
            description = "Cached lookup tool"
            param("id", "string", "Entity ID", required = true)

            // Configure caching
            cache {
                keyBuilder = { params, context ->
                    "${context.tenantId}|${params["id"]}"
                }
                ttl = 300
                maxSize = 100
            }

            execute { params, context ->
                callCount.incrementAndGet()
                "Result for ${params["id"]} in tenant ${context.tenantId}"
            }
        }

        // Execute with context
        withAgentContext("tenantId" to "TENANT-A") {
            val result1 = tool.execute(mapOf("id" to "123"))
            assertTrue(result1.isSuccess)
            assertEquals(1, callCount.get())

            // Second call - should be cached
            val result2 = tool.execute(mapOf("id" to "123"))
            assertTrue(result2.isSuccess)
            assertEquals(1, callCount.get())  // No additional execution

            // Different ID - cache miss
            val result3 = tool.execute(mapOf("id" to "456"))
            assertTrue(result3.isSuccess)
            assertEquals(2, callCount.get())

            // Different tenant (same ID) - cache miss
            withAgentContext("tenantId" to "TENANT-B") {
                val result4 = tool.execute(mapOf("id" to "123"))
                assertTrue(result4.isSuccess)
                assertEquals(3, callCount.get())
            }
        }
    }

    @Test
    fun `test cached extension function`() = runBlocking {
        val tool = SimpleTool(
            name = "test",
            description = "Test tool",
            parameterSchemas = emptyMap(),
            executor = { params ->
                executionCount.incrementAndGet()
                ToolResult.success("Result")
            }
        )

        // Wrap with caching using extension function
        val cached = tool.cached(ttl = 60, maxSize = 50)

        // Verify it's wrapped
        assertTrue(cached is CachedTool)

        // Test caching works
        cached.execute(mapOf("param" to "value"))
        cached.execute(mapOf("param" to "value"))

        assertEquals(1, executionCount.get())
    }

    @Test
    fun `test cache stats accuracy`() = runBlocking {
        val cachedTool = CachedTool(expensiveTool)

        // 3 unique calls
        cachedTool.execute(mapOf("id" to "1"))
        cachedTool.execute(mapOf("id" to "2"))
        cachedTool.execute(mapOf("id" to "3"))

        // 2 cache hits for "1"
        cachedTool.execute(mapOf("id" to "1"))
        cachedTool.execute(mapOf("id" to "1"))

        // 1 cache hit for "2"
        cachedTool.execute(mapOf("id" to "2"))

        val stats = cachedTool.getCacheStats()
        assertEquals(3, stats.size)  // 3 unique entries
        assertEquals(3, stats.hits)   // 3 cache hits
        assertEquals(3, stats.misses) // 3 cache misses
        assertEquals(0.5, stats.hitRate)  // 3/(3+3) = 0.5
    }
}
