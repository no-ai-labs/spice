package io.github.noailabs.spice.performance

import io.github.noailabs.spice.*
import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Comprehensive tests for CachedAgent
 */
class CachedAgentTest {

    @Test
    fun `test cache hit on duplicate request`() = runBlocking {
        var callCount = 0
        val agent = createTestAgent { callCount++ }
        val cached = CachedAgent(agent)

        val comm = Comm(content = "test", from = "user")

        // First call - cache miss
        val result1 = cached.processComm(comm)
        assertTrue(result1.isSuccess)
        assertEquals(1, callCount)

        // Second call - cache hit
        val result2 = cached.processComm(comm)
        assertTrue(result2.isSuccess)
        assertEquals(1, callCount) // Should not call agent again

        // Verify stats
        val stats = cached.getCacheStats()
        assertEquals(1, stats.hits)
        assertEquals(1, stats.misses)
        assertEquals(0.5, stats.hitRate)
    }

    @Test
    fun `test cache miss on different request`() = runBlocking {
        var callCount = 0
        val agent = createTestAgent { callCount++ }
        val cached = CachedAgent(agent)

        val comm1 = Comm(content = "test1", from = "user")
        val comm2 = Comm(content = "test2", from = "user")

        // Both should be cache misses
        cached.processComm(comm1)
        cached.processComm(comm2)

        assertEquals(2, callCount)

        val stats = cached.getCacheStats()
        assertEquals(0, stats.hits)
        assertEquals(2, stats.misses)
        assertEquals(0.0, stats.hitRate)
    }

    @Test
    fun `test TTL expiration`() = runBlocking {
        var callCount = 0
        val agent = createTestAgent { callCount++ }
        val cached = CachedAgent(
            agent,
            CachedAgent.CacheConfig(
                maxSize = 100,
                ttlSeconds = 1, // 1 second TTL
                enableMetrics = true
            )
        )

        val comm = Comm(content = "test", from = "user")

        // First call
        cached.processComm(comm)
        assertEquals(1, callCount)

        // Wait for TTL to expire
        delay(1100)

        // Should be cache miss due to expiration
        cached.processComm(comm)
        assertEquals(2, callCount)
    }

    @Test
    fun `test LRU eviction when cache is full`() = runBlocking {
        var callCount = 0
        val agent = createTestAgent { callCount++ }
        val cached = CachedAgent(
            agent,
            CachedAgent.CacheConfig(
                maxSize = 2, // Small cache
                ttlSeconds = 3600,
                enableMetrics = true
            )
        )

        val comm1 = Comm(content = "test1", from = "user")
        val comm2 = Comm(content = "test2", from = "user")
        val comm3 = Comm(content = "test3", from = "user")

        // Fill cache
        cached.processComm(comm1)
        delay(10) // Small delay to ensure different timestamps
        cached.processComm(comm2)
        assertEquals(2, callCount)

        // Access comm1 to make it more recent
        delay(10)
        cached.processComm(comm1)
        assertEquals(2, callCount) // Cache hit

        // Add comm3 - should evict comm2 (LRU)
        delay(10)
        cached.processComm(comm3)
        assertEquals(3, callCount)

        // comm1 should still be in cache (recently accessed)
        cached.processComm(comm1)
        assertEquals(3, callCount) // Cache hit

        // comm2 should be evicted (least recently used)
        cached.processComm(comm2)
        assertTrue(callCount >= 4, "comm2 should cause a cache miss, callCount: $callCount")

        val stats = cached.getCacheStats()
        assertTrue(stats.size <= 2, "Cache should not exceed maxSize, actual: ${stats.size}") // Should not exceed maxSize
    }

    @Test
    fun `test bypass cache flag`() = runBlocking {
        var callCount = 0
        val agent = createTestAgent { callCount++ }
        val cached = CachedAgent(
            agent,
            CachedAgent.CacheConfig(
                maxSize = 100,
                ttlSeconds = 3600,
                respectBypass = true
            )
        )

        val comm = Comm(
            content = "test",
            from = "user",
            data = mapOf("bypass_cache" to "true")
        )

        // Both calls should hit the agent (bypass cache)
        cached.processComm(comm)
        cached.processComm(comm)

        assertEquals(2, callCount)

        val stats = cached.getCacheStats()
        assertEquals(0, stats.hits) // No cache hits due to bypass
    }

    @Test
    fun `test cache only stores successful results`() = runBlocking {
        var callCount = 0
        var shouldFail = true

        val agent = object : BaseAgent("test", "Test", "Test agent") {
            override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
                callCount++
                return if (shouldFail) {
                    SpiceResult.failure(io.github.noailabs.spice.error.SpiceError.agentError("Simulated error"))
                } else {
                    SpiceResult.success(comm.reply("OK", id))
                }
            }
        }

        val cached = CachedAgent(agent)
        val comm = Comm(content = "test", from = "user")

        // First call - fails
        val result1 = cached.processComm(comm)
        assertTrue(result1.isFailure)
        assertEquals(1, callCount)

        // Second call - should still call agent (failure not cached)
        val result2 = cached.processComm(comm)
        assertTrue(result2.isFailure)
        assertEquals(2, callCount)

        // Now succeed
        shouldFail = false
        val result3 = cached.processComm(comm)
        assertTrue(result3.isSuccess)
        assertEquals(3, callCount)

        // Fourth call - should be cached
        val result4 = cached.processComm(comm)
        assertTrue(result4.isSuccess)
        assertEquals(3, callCount) // No additional call

        val stats = cached.getCacheStats()
        assertEquals(1, stats.hits)
    }

    @Test
    fun `test clear cache`() = runBlocking {
        var callCount = 0
        val agent = createTestAgent { callCount++ }
        val cached = CachedAgent(agent)

        val comm = Comm(content = "test", from = "user")

        // First call
        cached.processComm(comm)
        assertEquals(1, callCount)

        // Second call - cache hit
        cached.processComm(comm)
        assertEquals(1, callCount)

        // Clear cache
        cached.clearCache()

        // Third call - should call agent again
        cached.processComm(comm)
        assertEquals(2, callCount)

        val stats = cached.getCacheStats()
        assertEquals(0, stats.hits) // Stats also cleared
        assertEquals(1, stats.misses)
    }

    @Test
    fun `test cleanup expired entries`() = runBlocking {
        val agent = createTestAgent()
        val cached = CachedAgent(
            agent,
            CachedAgent.CacheConfig(
                maxSize = 100,
                ttlSeconds = 1, // 1 second TTL
                enableMetrics = true
            )
        )

        // Add some entries
        cached.processComm(Comm(content = "test1", from = "user"))
        cached.processComm(Comm(content = "test2", from = "user"))

        var stats = cached.getCacheStats()
        assertEquals(2, stats.size)

        // Wait for expiration
        delay(1100)

        // Cleanup
        cached.cleanupExpired()

        stats = cached.getCacheStats()
        assertEquals(0, stats.size)
    }

    @Test
    fun `test cache with different context`() = runBlocking {
        var callCount = 0
        val agent = createTestAgent { callCount++ }
        val cached = CachedAgent(agent)

        // Same content, different from field
        val comm1 = Comm(content = "test", from = "user1")
        val comm2 = Comm(content = "test", from = "user2")

        cached.processComm(comm1)
        cached.processComm(comm2)

        // Should be different cache keys (different context)
        assertEquals(2, callCount)

        val stats = cached.getCacheStats()
        assertEquals(0, stats.hits)
        assertEquals(2, stats.misses)
    }

    @Test
    fun `test cache statistics`() = runBlocking {
        val agent = createTestAgent()
        val cached = CachedAgent(
            agent,
            CachedAgent.CacheConfig(
                maxSize = 100,
                ttlSeconds = 3600
            )
        )

        val comm1 = Comm(content = "test1", from = "user")
        val comm2 = Comm(content = "test2", from = "user")

        // 2 misses
        cached.processComm(comm1)
        cached.processComm(comm2)

        // 2 hits
        cached.processComm(comm1)
        cached.processComm(comm2)

        val stats = cached.getCacheStats()
        assertEquals(2, stats.size)
        assertEquals(100, stats.maxSize)
        assertEquals(2, stats.hits)
        assertEquals(2, stats.misses)
        assertEquals(0.5, stats.hitRate) // 50% hit rate
        assertEquals(3600, stats.ttlSeconds)

        // Verify toString
        val statsString = stats.toString()
        assertTrue(statsString.contains("Hit Rate: 50.00%"))
    }

    @Test
    fun `test cached extension function prevents double wrapping`() = runBlocking {
        val agent = createTestAgent()
        val cached1 = agent.cached()
        val cached2 = cached1.cached() // Should return same instance

        assertTrue(cached1 === cached2) // Same instance
    }

    @Test
    fun `test delegate methods pass through`() = runBlocking {
        val agent = createTestAgent()
        val cached = CachedAgent(agent)

        // Verify delegate methods work
        assertEquals("test-agent", cached.id)
        assertEquals("Test Agent", cached.name)
        assertEquals("Test agent for caching", cached.description)
        assertEquals(emptyList(), cached.capabilities)
        assertEquals(emptyList(), cached.getTools())
        assertTrue(cached.isReady())
        assertTrue(cached.canHandle(Comm(content = "test", from = "user")))
    }

    // Helper function to create test agent
    private fun createTestAgent(onCall: () -> Unit = {}): Agent {
        return object : BaseAgent(
            id = "test-agent",
            name = "Test Agent",
            description = "Test agent for caching"
        ) {
            override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
                onCall()
                return SpiceResult.success(
                    comm.reply("Response: ${comm.content}", id)
                )
            }
        }
    }
}
