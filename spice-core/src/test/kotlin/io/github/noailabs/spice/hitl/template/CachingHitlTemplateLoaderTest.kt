package io.github.noailabs.spice.hitl.template

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class CachingHitlTemplateLoaderTest {

    // ===========================================
    // Test Helper: Fake Template Loader
    // ===========================================

    private class FakeHitlTemplateLoader(
        private val templates: Map<String, HitlTemplate>
    ) : HitlTemplateLoader {

        var loadCallCount = 0
            private set

        override fun load(id: String, tenantId: String?): HitlTemplate? {
            loadCallCount++
            val key = if (tenantId != null) "$tenantId:$id" else id
            return templates[key] ?: templates[id]
        }

        override fun listTemplateIds(tenantId: String?): List<String> =
            templates.keys.toList()
    }

    private val template1 = HitlTemplate.text("t1", "Prompt 1")
    private val template2 = HitlTemplate.text("t2", "Prompt 2")
    private val tenantTemplate = HitlTemplate.text("t1", "Tenant Prompt")

    // ===========================================
    // Cache Hit/Miss Tests
    // ===========================================

    @Test
    fun `first load delegates to underlying loader`() {
        val delegate = FakeHitlTemplateLoader(mapOf("t1" to template1))
        val cached = CachingHitlTemplateLoader(delegate)

        val result = cached.load("t1")

        assertEquals(template1, result)
        assertEquals(1, delegate.loadCallCount)
    }

    @Test
    fun `second load returns cached result without delegation`() {
        val delegate = FakeHitlTemplateLoader(mapOf("t1" to template1))
        val cached = CachingHitlTemplateLoader(delegate)

        cached.load("t1")
        cached.load("t1")

        assertEquals(1, delegate.loadCallCount) // Only one delegation
    }

    @Test
    fun `different template IDs are cached separately`() {
        val delegate = FakeHitlTemplateLoader(mapOf("t1" to template1, "t2" to template2))
        val cached = CachingHitlTemplateLoader(delegate)

        cached.load("t1")
        cached.load("t2")
        cached.load("t1")
        cached.load("t2")

        assertEquals(2, delegate.loadCallCount) // t1 and t2 each loaded once
    }

    // ===========================================
    // TTL Tests
    // ===========================================

    @Test
    fun `expired entry is reloaded`() {
        val delegate = FakeHitlTemplateLoader(mapOf("t1" to template1))
        val cached = CachingHitlTemplateLoader(delegate, ttl = 50.milliseconds)

        cached.load("t1")
        Thread.sleep(100) // Wait for expiration
        cached.load("t1")

        assertEquals(2, delegate.loadCallCount)
    }

    @Test
    fun `non-expired entry is not reloaded`() {
        val delegate = FakeHitlTemplateLoader(mapOf("t1" to template1))
        val cached = CachingHitlTemplateLoader(delegate, ttl = 10000.milliseconds) // 10 seconds

        cached.load("t1")
        Thread.sleep(50)
        cached.load("t1")

        assertEquals(1, delegate.loadCallCount)
    }

    // ===========================================
    // Null Caching Tests
    // ===========================================

    @Test
    fun `null result is cached when cacheNulls is true`() {
        val delegate = FakeHitlTemplateLoader(emptyMap())
        val cached = CachingHitlTemplateLoader(delegate, cacheNulls = true)

        cached.load("nonexistent")
        cached.load("nonexistent")

        assertEquals(1, delegate.loadCallCount)
    }

    @Test
    fun `null result is not cached when cacheNulls is false`() {
        val delegate = FakeHitlTemplateLoader(emptyMap())
        val cached = CachingHitlTemplateLoader(delegate, cacheNulls = false)

        cached.load("nonexistent")
        cached.load("nonexistent")

        assertEquals(2, delegate.loadCallCount)
    }

    @Test
    fun `getCacheStats reports null entry count`() {
        val delegate = FakeHitlTemplateLoader(mapOf("t1" to template1))
        val cached = CachingHitlTemplateLoader(delegate, cacheNulls = true)

        cached.load("t1")         // Not null
        cached.load("nonexistent") // Null

        val stats = cached.getCacheStats()
        assertEquals(2, stats.size)
        assertEquals(1, stats.nullEntryCount)
    }

    // ===========================================
    // Tenant Isolation Tests
    // ===========================================

    @Test
    fun `different tenants have isolated caches`() {
        val delegate = FakeHitlTemplateLoader(mapOf(
            "t1" to template1,
            "acme:t1" to tenantTemplate
        ))
        val cached = CachingHitlTemplateLoader(delegate)

        val defaultResult = cached.load("t1", tenantId = null)
        val acmeResult = cached.load("t1", tenantId = "acme")

        assertEquals(template1, defaultResult)
        assertEquals(tenantTemplate, acmeResult)
        assertEquals(2, delegate.loadCallCount)
    }

    @Test
    fun `same tenant same id uses cache`() {
        val delegate = FakeHitlTemplateLoader(mapOf("acme:t1" to tenantTemplate))
        val cached = CachingHitlTemplateLoader(delegate)

        cached.load("t1", tenantId = "acme")
        cached.load("t1", tenantId = "acme")

        assertEquals(1, delegate.loadCallCount)
    }

    // ===========================================
    // Cache Management Tests
    // ===========================================

    @Test
    fun `clearCache removes all entries`() {
        val delegate = FakeHitlTemplateLoader(mapOf("t1" to template1, "t2" to template2))
        val cached = CachingHitlTemplateLoader(delegate)
        cached.load("t1")
        cached.load("t2")

        cached.clearCache()

        assertEquals(0, cached.getCacheStats().size)

        // After clear, next load should delegate again
        cached.load("t1")
        assertEquals(3, delegate.loadCallCount)
    }

    @Test
    fun `clearCacheForTenant removes only tenant entries`() {
        val delegate = FakeHitlTemplateLoader(mapOf(
            "t1" to template1,
            "acme:t1" to tenantTemplate
        ))
        val cached = CachingHitlTemplateLoader(delegate)
        cached.load("t1", tenantId = null)
        cached.load("t1", tenantId = "acme")

        assertEquals(2, cached.getCacheStats().size)

        cached.clearCacheForTenant("acme")

        assertEquals(1, cached.getCacheStats().size)
    }

    @Test
    fun `invalidate removes specific entry`() {
        val delegate = FakeHitlTemplateLoader(mapOf("t1" to template1, "t2" to template2))
        val cached = CachingHitlTemplateLoader(delegate)
        cached.load("t1")
        cached.load("t2")

        cached.invalidate("t1")

        // t1 should reload, t2 should be cached
        cached.load("t1")
        cached.load("t2")
        assertEquals(3, delegate.loadCallCount) // t1 loaded twice, t2 once
    }

    @Test
    fun `evictExpired removes only expired entries and returns count`() {
        val delegate = FakeHitlTemplateLoader(mapOf("t1" to template1, "t2" to template2))
        val cached = CachingHitlTemplateLoader(delegate, ttl = 50.milliseconds)

        cached.load("t1")
        Thread.sleep(100) // t1 expires
        cached.load("t2") // t2 is fresh

        val evicted = cached.evictExpired()

        assertEquals(1, evicted)
        assertEquals(1, cached.getCacheStats().size) // Only t2 remains
    }

    @Test
    fun `evictExpired returns 0 when no entries expired`() {
        val delegate = FakeHitlTemplateLoader(mapOf("t1" to template1))
        val cached = CachingHitlTemplateLoader(delegate, ttl = 10000.milliseconds)

        cached.load("t1")

        val evicted = cached.evictExpired()

        assertEquals(0, evicted)
        assertEquals(1, cached.getCacheStats().size)
    }

    // ===========================================
    // maxSize Tests (FIFO-like eviction)
    // ===========================================

    @Test
    fun `maxSize 0 means unlimited`() {
        val templates = (1..10).associate { "t$it" to HitlTemplate.text("t$it", "Prompt $it") }
        val delegate = FakeHitlTemplateLoader(templates)
        val cached = CachingHitlTemplateLoader(delegate, maxSize = 0) // Unlimited

        for (i in 1..10) {
            cached.load("t$i")
        }

        assertEquals(10, cached.getCacheStats().size)
    }

    @Test
    fun `maxSize enforces cache size limit`() {
        val templates = (1..5).associate { "t$it" to HitlTemplate.text("t$it", "Prompt $it") }
        val delegate = FakeHitlTemplateLoader(templates)
        val cached = CachingHitlTemplateLoader(delegate, maxSize = 3)

        for (i in 1..5) {
            cached.load("t$i")
        }

        // Cache should have at most 3 entries
        assertTrue(cached.getCacheStats().size <= 3)
    }

    @Test
    fun `getCacheStats reports correct maxSize`() {
        val delegate = FakeHitlTemplateLoader(emptyMap())
        val cached = CachingHitlTemplateLoader(delegate, maxSize = 100)

        val stats = cached.getCacheStats()
        assertEquals(100, stats.maxSize)
    }

    // ===========================================
    // exists() Tests
    // ===========================================

    @Test
    fun `exists returns true for cached template`() {
        val delegate = FakeHitlTemplateLoader(mapOf("t1" to template1))
        val cached = CachingHitlTemplateLoader(delegate)

        cached.load("t1")
        val exists = cached.exists("t1")

        assertTrue(exists)
        assertEquals(1, delegate.loadCallCount) // Only initial load
    }

    @Test
    fun `exists returns false for cached null`() {
        val delegate = FakeHitlTemplateLoader(emptyMap())
        val cached = CachingHitlTemplateLoader(delegate, cacheNulls = true)

        cached.load("nonexistent")
        val exists = cached.exists("nonexistent")

        assertFalse(exists)
        assertEquals(1, delegate.loadCallCount) // Only initial load
    }

    // ===========================================
    // Factory Methods Tests
    // ===========================================

    @Test
    fun `wrap creates caching loader with defaults`() {
        val delegate = FakeHitlTemplateLoader(mapOf("t1" to template1))
        val cached = CachingHitlTemplateLoader.wrap(delegate)

        val result = cached.load("t1")
        assertNotNull(result)

        val stats = cached.getCacheStats()
        assertTrue(stats.cacheNulls)
        assertEquals(0, stats.maxSize) // Unlimited
    }

    @Test
    fun `withoutNullCaching creates loader without null caching`() {
        val delegate = FakeHitlTemplateLoader(emptyMap())
        val cached = CachingHitlTemplateLoader.withoutNullCaching(delegate)

        assertFalse(cached.getCacheStats().cacheNulls)
    }

    // ===========================================
    // listTemplateIds Tests (not cached)
    // ===========================================

    @Test
    fun `listTemplateIds delegates to underlying loader`() {
        val delegate = FakeHitlTemplateLoader(mapOf("t1" to template1, "t2" to template2))
        val cached = CachingHitlTemplateLoader(delegate)

        val ids = cached.listTemplateIds()

        assertEquals(listOf("t1", "t2"), ids)
    }
}
