# Tool-Level Caching

Complete guide to optimizing tool performance with intelligent caching, including TTL expiration, LRU eviction, context-aware keys, and comprehensive metrics.

## Table of Contents

- [Overview](#overview)
- [Quick Start](#quick-start)
- [Core Concepts](#core-concepts)
- [Configuration](#configuration)
- [Cache Key Generation](#cache-key-generation)
- [Usage Patterns](#usage-patterns)
- [Advanced Techniques](#advanced-techniques)
- [Performance](#performance)
- [Monitoring & Debugging](#monitoring--debugging)
- [Best Practices](#best-practices)
- [Testing](#testing)
- [Troubleshooting](#troubleshooting)
- [API Reference](#api-reference)

## Overview

Tool-Level Caching provides intelligent, automatic caching for any tool in the Spice Framework. It dramatically reduces execution time for expensive operations like database queries, external API calls, and heavy computations.

### Why Tool-Level Caching?

**Without Caching:**
```
Request 1: fetch_user(id=123) → Database query (100ms)
Request 2: fetch_user(id=123) → Database query (100ms)
Request 3: fetch_user(id=123) → Database query (100ms)
Total: 300ms
```

**With Caching:**
```
Request 1: fetch_user(id=123) → Database query (100ms) → CACHED
Request 2: fetch_user(id=123) → Cache hit (0.001ms)
Request 3: fetch_user(id=123) → Cache hit (0.001ms)
Total: ~100ms (66% faster!)
```

### Key Features

| Feature | Description | Benefit |
|---------|-------------|---------|
| ⏱️ **TTL Expiration** | Automatic time-based invalidation | Fresh data without manual clearing |
| 🔄 **LRU Eviction** | Least Recently Used removal | Bounded memory usage |
| 🎯 **Context-Aware Keys** | Tenant/user/session in keys | Perfect for multi-tenancy |
| 📊 **Metrics** | Hits, misses, hit rate tracking | Monitor cache efficiency |
| 🔧 **Custom Keys** | Full control over key generation | Optimize for your use case |
| 🔒 **Thread-Safe** | Concurrent access without locks | High-performance under load |
| ✨ **DSL Integration** | Works with `contextAwareTool` | Clean, declarative syntax |

### When to Use Caching

✅ **Good Use Cases:**
- Database queries (user lookups, policy retrieval)
- External API calls (weather, geocoding, translation)
- Expensive computations (NLP, image processing, PDF parsing)
- Static/semi-static data (configuration, catalogs)
- High-frequency repeated requests

❌ **Bad Use Cases:**
- Real-time data (stock prices, live updates)
- User-specific writes (mutations should invalidate)
- One-time operations (no repeated access)
- Tiny operations (<1ms execution time)

## Quick Start

### 1. Basic Caching

The simplest way to add caching:

```kotlin
// Original tool
val userLookup = SimpleTool("user_lookup") { params ->
    val userId = params["id"] as String
    database.findUser(userId)  // Expensive!
}

// Add caching with one line
val cachedUserLookup = userLookup.cached(
    ttl = 300,      // Cache for 5 minutes
    maxSize = 1000  // Keep up to 1000 users
)

// Use it normally
val result = cachedUserLookup.execute(mapOf("id" to "user-123"))
// First call: Database query (slow)
// Second call: Cache hit (instant!)
```

### 2. Context-Aware Caching

For multi-tenant tools, use the `cache {}` DSL:

```kotlin
val policyLookup = contextAwareTool("policy_lookup") {
    description = "Get tenant policy configuration"

    param("policyType", "string", "Policy type", required = true)

    // 🎯 Configure caching with context awareness
    cache {
        // Cache key includes tenant ID automatically
        keyBuilder = { params, context ->
            "${context.tenantId}|policy:${params["policyType"]}"
        }
        ttl = 600      // 10 minutes
        maxSize = 500  // 500 policies per tenant
    }

    execute { params, context ->
        val policyType = params["policyType"] as String
        val tenantId = context.tenantId!!

        // Expensive policy fetch
        policyService.getPolicy(tenantId, policyType)
    }
}

// Execute with context
withAgentContext("tenantId" to "ACME") {
    val result = policyLookup.execute(mapOf("policyType" to "premium"))
    // First call: Fetches from service
    // Second call: Cache hit (tenant-isolated)
}
```

### 3. Zero-Config Caching

For simple cases, let the framework handle everything:

```kotlin
val weatherTool = contextAwareTool("weather") {
    param("city", "string", required = true)

    // ✨ Default caching: auto key, 1hr TTL, 1000 entries
    cache { }

    execute { params, context ->
        val city = params["city"] as String
        weatherApi.getWeather(city)
    }
}
```

## Core Concepts

### Cache Lifecycle

```
┌─────────────────────────────────────────────────────────┐
│ 1. Request arrives with parameters                      │
└─────────────┬───────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────────────────┐
│ 2. Generate cache key from params + context             │
│    Example: "tenant:ACME|user:123|doc:456"             │
└─────────────┬───────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────────────────┐
│ 3. Check cache for existing entry                       │
└─────────────┬───────────────────────────────────────────┘
              │
        ┌─────┴─────┐
        │           │
     HIT ✓       MISS ✗
        │           │
        ▼           ▼
   ┌────────┐  ┌─────────────────────────┐
   │ Return │  │ 4. Execute tool         │
   │ cached │  │ 5. Store result in cache│
   │ result │  │ 6. Return result        │
   └────────┘  └─────────────────────────┘
```

### Cache Entry Structure

```kotlin
data class CacheEntry(
    val result: SpiceResult<ToolResult>,  // Cached tool result
    val timestamp: Long,                   // Creation time (milliseconds)
    val accessTime: Long                   // Last access time (for LRU)
)
```

### TTL (Time-To-Live)

TTL defines how long an entry stays valid:

```kotlin
cache { ttl = 300 }  // 300 seconds = 5 minutes

// Timeline:
// t=0s   : Entry created
// t=150s : Entry accessed → Cache hit
// t=300s : Entry expires
// t=301s : Entry accessed → Cache miss (expired)
```

**Expiration Check:**
```kotlin
fun isExpired(entry: CacheEntry): Boolean {
    val age = System.currentTimeMillis() - entry.timestamp
    return age > (ttl * 1000)
}
```

### LRU (Least Recently Used) Eviction

When cache is full, the least recently accessed entry is removed:

```kotlin
cache { maxSize = 3 }

// Operations:
put("A") → Cache: [A]
put("B") → Cache: [A, B]
put("C") → Cache: [A, B, C]  // Full!
get("A") → Cache: [B, C, A]  // A moved to end (recently used)
put("D") → Cache: [C, A, D]  // B evicted (least recently used)
```

### Context-Aware Keys

Cache keys can include context information for multi-tenant isolation:

```kotlin
// Without context: UNSAFE for multi-tenant
cache {
    keyBuilder = { params, _ ->
        "doc:${params["id"]}"  // ❌ Same key for all tenants!
    }
}

// With context: SAFE for multi-tenant
cache {
    keyBuilder = { params, context ->
        "${context.tenantId}|doc:${params["id"]}"  // ✅ Tenant-isolated!
    }
}
```

## Configuration

### CacheConfigBlock DSL

```kotlin
cache {
    // Custom key builder
    keyBuilder = { params, context ->
        "${context.tenantId}|${params["id"]}"
    }

    // Time-to-live in seconds
    ttl = 300

    // Maximum cache size
    maxSize = 1000

    // Enable/disable metrics collection
    enableMetrics = true
}
```

### ToolCacheConfig (Direct API)

```kotlin
val config = ToolCacheConfig(
    maxSize = 1000,
    ttl = 3600,
    enableMetrics = true,
    keyBuilder = { params, context ->
        "${context.tenantId}:${params["id"]}"
    }
)

val cachedTool = CachedTool(baseTool, config)
```

### Default Values

```kotlin
ToolCacheConfig(
    maxSize = 1000,         // 1000 entries
    ttl = 3600,             // 1 hour
    enableMetrics = true,   // Metrics enabled
    keyBuilder = null       // Auto-generated keys
)
```

## Cache Key Generation

### Automatic Key Generation

When `keyBuilder` is not provided, keys are generated automatically:

```kotlin
// Inputs:
params = mapOf("sku" to "ABC123", "version" to "v2")
context = AgentContext(tenantId = "ACME", userId = "user-456")

// Generated key:
"sku=ABC123|version=v2::tenantId=ACME|userId=user-456"
// → SHA-256 hash → "7f3a8c2d..."
```

**Algorithm:**
1. Sort parameters alphabetically
2. Format as `key1=value1|key2=value2`
3. Append context as `::tenantId=X|userId=Y`
4. Compute SHA-256 hash for consistent length

**Parameters Excluded:**
- Internal parameters starting with `__` (e.g., `__context`, `__internal`)
- Null values

### Custom Key Builders

#### Simple Keys

```kotlin
cache {
    keyBuilder = { params, _ ->
        params["id"] as String  // Just use ID
    }
}
```

#### Multi-Parameter Keys

```kotlin
cache {
    keyBuilder = { params, context ->
        val userId = params["userId"] as String
        val docId = params["docId"] as String
        "${context.tenantId}:$userId:$docId"
    }
}
```

#### Normalized Keys

```kotlin
cache {
    keyBuilder = { params, context ->
        val query = (params["query"] as String).lowercase().trim()
        val category = params["category"] as? String ?: "all"
        "${context.tenantId}|search:$query:$category"
    }
}
```

#### Conditional Keys

```kotlin
cache {
    keyBuilder = { params, context ->
        val includeArchived = params["includeArchived"] as? Boolean ?: false

        if (includeArchived) {
            // Different cache for archived queries
            "${context.tenantId}|with-archived|${params["query"]}"
        } else {
            "${context.tenantId}|active-only|${params["query"]}"
        }
    }
}
```

#### Hierarchical Keys

```kotlin
cache {
    keyBuilder = { params, context ->
        // Cache at different levels
        when {
            context.userId != null ->
                "user:${context.userId}|${params["id"]}"
            context.tenantId != null ->
                "tenant:${context.tenantId}|${params["id"]}"
            else ->
                "global|${params["id"]}"
        }
    }
}
```

## Usage Patterns

### Pattern 1: Database Query Caching

**Scenario:** Reduce database load for frequently accessed users.

```kotlin
val userProfileTool = contextAwareTool("get_user_profile") {
    description = "Fetch complete user profile"

    param("userId", "string", "User identifier", required = true)
    param("includePreferences", "boolean", "Include user preferences", required = false)

    cache {
        keyBuilder = { params, context ->
            val userId = params["userId"] as String
            val includePrefs = params["includePreferences"] as? Boolean ?: false
            "${context.tenantId}|user:$userId|prefs:$includePrefs"
        }
        ttl = 600       // 10 minutes (profiles don't change often)
        maxSize = 5000  // Cache 5000 user profiles
    }

    execute { params, context ->
        val userId = params["userId"] as String
        val includePrefs = params["includePreferences"] as? Boolean ?: false

        // Expensive database queries
        val profile = userRepository.findById(userId, context.tenantId)

        if (includePrefs) {
            val prefs = preferencesRepository.findByUserId(userId)
            profile.copy(preferences = prefs)
        } else {
            profile
        }
    }
}

// Usage:
withAgentContext("tenantId" to "ACME") {
    // First call: Database queries (slow)
    val profile1 = userProfileTool.execute(mapOf("userId" to "u123"))

    // Second call: Cache hit (instant!)
    val profile2 = userProfileTool.execute(mapOf("userId" to "u123"))
}
```

**Metrics:**
- Without caching: 150ms average query time
- With caching: 0.001ms on cache hit
- Expected hit rate: 85-95% for active users

### Pattern 2: External API Caching

**Scenario:** Cache slow third-party API responses.

```kotlin
val geocodingTool = contextAwareTool("geocode_address") {
    description = "Convert address to coordinates"

    param("address", "string", "Full address", required = true)
    param("country", "string", "Country code", required = false)

    cache {
        keyBuilder = { params, _ ->
            // Geocoding doesn't need tenant isolation
            val address = (params["address"] as String).lowercase().trim()
            val country = params["country"] as? String ?: "US"
            "geocode:$country:$address"
        }
        ttl = 86400    // 24 hours (addresses don't move!)
        maxSize = 10000 // Cache 10k addresses
    }

    execute { params, context ->
        val address = params["address"] as String
        val country = params["country"] as? String ?: "US"

        // Expensive external API call (500ms+)
        geocodingApiClient.geocode(address, country)
    }
}

// Usage:
val coords = geocodingTool.execute(mapOf(
    "address" to "1600 Amphitheatre Parkway, Mountain View, CA"
))
```

**Benefits:**
- Reduces API costs (pay-per-request APIs)
- Faster response (no network latency)
- Resilience (works even if API is down temporarily)

### Pattern 3: Computation Caching

**Scenario:** Cache expensive document analysis results.

```kotlin
val documentAnalysisTool = contextAwareTool("analyze_document") {
    description = "Perform NLP analysis on document"

    param("documentId", "string", "Document identifier", required = true)
    param("analysisType", "string", "Type of analysis", required = true)

    cache {
        keyBuilder = { params, context ->
            val docId = params["documentId"] as String
            val analysisType = params["analysisType"] as String
            "${context.tenantId}|doc:$docId|analysis:$analysisType"
        }
        ttl = 3600      // 1 hour
        maxSize = 1000  // 1000 analysis results
    }

    execute { params, context ->
        val docId = params["documentId"] as String
        val analysisType = params["analysisType"] as String

        // Load document
        val document = documentRepository.load(docId, context.tenantId)

        // Expensive NLP processing (5-30 seconds!)
        when (analysisType) {
            "sentiment" -> nlpService.analyzeSentiment(document.content)
            "entities" -> nlpService.extractEntities(document.content)
            "summary" -> nlpService.generateSummary(document.content)
            else -> throw IllegalArgumentException("Unknown analysis type")
        }
    }
}

// Usage:
withAgentContext("tenantId" to "ACME") {
    // First analysis: 15 seconds
    val sentiment = documentAnalysisTool.execute(mapOf(
        "documentId" to "doc-789",
        "analysisType" to "sentiment"
    ))

    // Re-run same analysis: 0.001 seconds (cached!)
    val sentiment2 = documentAnalysisTool.execute(mapOf(
        "documentId" to "doc-789",
        "analysisType" to "sentiment"
    ))
}
```

### Pattern 4: Configuration Caching

**Scenario:** Cache rarely-changing tenant configuration.

```kotlin
val tenantConfigTool = contextAwareTool("get_tenant_config") {
    description = "Get tenant configuration"

    cache {
        keyBuilder = { _, context ->
            // Key only by tenant (no parameters)
            "config:${context.tenantId}"
        }
        ttl = 3600      // 1 hour
        maxSize = 100   // 100 tenants
    }

    execute { _, context ->
        val tenantId = context.tenantId!!

        // Load configuration (database + compute defaults)
        configService.loadTenantConfig(tenantId)
    }
}

// Manual cache invalidation on config update
fun updateTenantConfig(tenantId: String, newConfig: Config) {
    configService.save(tenantId, newConfig)
    tenantConfigTool.clearCache()  // Invalidate cache
}
```

### Pattern 5: Search Result Caching

**Scenario:** Cache search results for common queries.

```kotlin
val searchTool = contextAwareTool("search_documents") {
    description = "Full-text document search"

    param("query", "string", "Search query", required = true)
    param("filters", "object", "Search filters", required = false)
    param("page", "integer", "Page number", required = false)

    cache {
        keyBuilder = { params, context ->
            val query = (params["query"] as String).lowercase().trim()
            val filters = params["filters"] as? Map<*, *> ?: emptyMap<String, Any>()
            val page = params["page"] as? Int ?: 1

            // Serialize filters to string
            val filterStr = filters.entries
                .sortedBy { it.key.toString() }
                .joinToString("|") { "${it.key}=${it.value}" }

            "${context.tenantId}|search:$query|filters:$filterStr|page:$page"
        }
        ttl = 300       // 5 minutes (search indexes update frequently)
        maxSize = 5000  // Cache popular searches
    }

    execute { params, context ->
        val query = params["query"] as String
        val filters = params["filters"] as? Map<*, *> ?: emptyMap<String, Any>()
        val page = params["page"] as? Int ?: 1

        // Expensive full-text search
        searchEngine.search(
            query = query,
            filters = filters,
            page = page,
            tenantId = context.tenantId
        )
    }
}
```

### Pattern 6: Multi-Level Caching

**Scenario:** Cache at different granularities for flexibility.

```kotlin
val productDataTool = contextAwareTool("get_product_data") {
    description = "Get product information with variant data"

    param("sku", "string", "Product SKU", required = true)
    param("includeInventory", "boolean", "Include inventory data", required = false)

    cache {
        keyBuilder = { params, context ->
            val sku = params["sku"] as String
            val includeInventory = params["includeInventory"] as? Boolean ?: false

            if (includeInventory) {
                // Shorter TTL for inventory (changes frequently)
                // Signal: use different cache namespace
                "${context.tenantId}|product-with-inv:$sku"
            } else {
                // Longer TTL for base product data
                "${context.tenantId}|product-base:$sku"
            }
        }
        // Base TTL (can be overridden by key logic)
        ttl = 600
        maxSize = 10000
    }

    execute { params, context ->
        val sku = params["sku"] as String
        val includeInventory = params["includeInventory"] as? Boolean ?: false

        val baseProduct = productRepository.findBySku(sku, context.tenantId)

        if (includeInventory) {
            val inventory = inventoryService.getInventory(sku)
            baseProduct.copy(inventory = inventory)
        } else {
            baseProduct
        }
    }
}
```

## Advanced Techniques

### Cache Warming

Pre-populate cache with frequently accessed data:

```kotlin
suspend fun warmUserCache(tenantId: String, userIds: List<String>) {
    withAgentContext("tenantId" to tenantId) {
        userIds.forEach { userId ->
            // Execute once to populate cache
            userLookupTool.execute(mapOf("userId" to userId))
        }
    }
}

// Call during application startup or maintenance window
warmUserCache("ACME", listOf("admin", "user-1", "user-2"))
```

### Conditional Caching

Cache only certain results:

```kotlin
val conditionalCacheTool = contextAwareTool("conditional_cache") {
    param("priority", "string", required = true)

    cache {
        keyBuilder = { params, context ->
            val priority = params["priority"] as String

            // Only cache non-urgent requests
            if (priority == "urgent") {
                // Use timestamp to prevent caching
                "${context.tenantId}|urgent:${System.currentTimeMillis()}"
            } else {
                "${context.tenantId}|normal:${params["id"]}"
            }
        }
        ttl = 300
        maxSize = 1000
    }

    execute { params, context ->
        // Execute logic...
    }
}
```

### Cache Layers

Combine tool caching with application-level caching:

```kotlin
// L1: Application cache (e.g., Redis)
val appCache = RedisCacheManager()

// L2: Tool-level cache (in-memory)
val cachedTool = baseTool.cached(ttl = 300)

// Check L1 first, then L2
suspend fun getCachedResult(key: String): Result {
    // Try L1 (Redis)
    appCache.get(key)?.let { return it }

    // Try L2 (Tool cache)
    val result = cachedTool.execute(params)

    // Store in L1 for next time
    appCache.set(key, result)

    return result
}
```

### Cache Partitioning

Separate caches for different use cases:

```kotlin
// High-frequency, short-lived cache
val shortLivedTool = baseTool.cached(ttl = 60, maxSize = 10000)

// Low-frequency, long-lived cache
val longLivedTool = baseTool.cached(ttl = 3600, maxSize = 100)

// Route based on context
fun selectTool(context: AgentContext): Tool {
    return if (context.metadata["cacheStrategy"] == "short") {
        shortLivedTool
    } else {
        longLivedTool
    }
}
```

### Dynamic TTL

Adjust TTL based on data characteristics:

```kotlin
cache {
    keyBuilder = { params, context ->
        val dataType = params["dataType"] as String
        val id = params["id"] as String

        // Encode TTL hint in key
        val ttl = when (dataType) {
            "static" -> 86400   // 24 hours
            "dynamic" -> 300    // 5 minutes
            "realtime" -> 10    // 10 seconds
            else -> 600
        }

        "${context.tenantId}|$dataType:$id|ttl:$ttl"
    }
    ttl = 600  // Default TTL
    maxSize = 5000
}
```

## Performance

### Benchmarks

Typical performance characteristics:

| Operation | Time | Notes |
|-----------|------|-------|
| Cache Hit | ~1 µs | In-memory map lookup |
| Cache Miss | Tool execution + ~1 µs | Store in cache |
| Key Generation (Auto) | ~10 µs | SHA-256 hash |
| Key Generation (Custom) | ~0.1 µs | String concatenation |
| LRU Eviction | ~50 µs | Find + remove LRU entry |
| Metrics Update | ~0.01 µs | Atomic increment |

### Memory Usage

Estimate memory footprint:

```kotlin
// Formula:
// Memory (MB) = (maxSize × avgResultSize) / 1024 / 1024

// Example 1: User profiles
// maxSize = 5000
// avgResultSize = 2 KB
// Memory = (5000 × 2048) / 1024 / 1024 ≈ 10 MB

// Example 2: Search results
// maxSize = 10000
// avgResultSize = 10 KB
// Memory = (10000 × 10240) / 1024 / 1024 ≈ 98 MB
```

**Monitoring Memory:**

```kotlin
val stats = cachedTool.getCacheStats()
val estimatedMemoryMB = (stats.size * 2) / 1024  // Assume 2KB per entry

if (estimatedMemoryMB > 100) {
    println("Warning: Cache using ${estimatedMemoryMB}MB")
}
```

### Optimization Tips

#### 1. Right-Size Your Cache

```kotlin
// Too small: Low hit rate, frequent evictions
cache { maxSize = 10 }  // ❌

// Too large: Wasted memory
cache { maxSize = 1000000 }  // ❌

// Just right: Based on working set
cache { maxSize = 5000 }  // ✅
```

#### 2. Choose Optimal TTL

```kotlin
// Static data: Long TTL
cache { ttl = 86400 }  // 24 hours

// Semi-static: Medium TTL
cache { ttl = 3600 }  // 1 hour

// Dynamic data: Short TTL
cache { ttl = 300 }  // 5 minutes

// Real-time data: Don't cache!
// (no cache block)
```

#### 3. Efficient Key Builders

```kotlin
// ❌ Inefficient: Serialize to JSON
cache {
    keyBuilder = { params, context ->
        json.encodeToString(params)  // Slow!
    }
}

// ✅ Efficient: String concatenation
cache {
    keyBuilder = { params, context ->
        "${context.tenantId}|${params["id"]}"  // Fast!
    }
}
```

#### 4. Disable Metrics in Production (if not needed)

```kotlin
cache {
    enableMetrics = false  // Saves ~0.01µs per operation
}
```

### Concurrency Performance

CachedTool is highly concurrent:

```kotlin
// Load test: 1000 concurrent requests
val tool = baseTool.cached(ttl = 3600)

repeat(1000) {
    launch {
        tool.execute(params)
    }
}

// Performance:
// - No lock contention
// - ConcurrentHashMap scales linearly
// - Atomic metrics updates
```

## Monitoring & Debugging

### Cache Statistics

Get real-time cache metrics:

```kotlin
val stats = cachedTool.getCacheStats()

println("""
    Tool: ${stats.toolName}
    Size: ${stats.size} / ${stats.maxSize}
    Hits: ${stats.hits}
    Misses: ${stats.misses}
    Hit Rate: ${stats.hitRate * 100}%
    TTL: ${stats.ttl}s
""".trimIndent())
```

**Output:**
```
Tool: user_lookup
Size: 347 / 1000
Hits: 8532
Misses: 1247
Hit Rate: 87.23%
TTL: 600s
```

### Logging

Enable debug logging to trace cache behavior:

```kotlin
// In your logging config
logger("io.github.noailabs.spice.performance.CachedTool").level = Level.DEBUG

// Logs:
// [DEBUG] Cache key generated: tenant:ACME|user:123
// [DEBUG] Cache HIT for key: tenant:ACME|user:123
// [DEBUG] Cache MISS for key: tenant:ACME|user:456
// [DEBUG] LRU eviction: removed key tenant:OLD|user:789
```

### Metrics Export

Export cache metrics to monitoring systems:

```kotlin
// Periodically export to Prometheus/Grafana
fun exportCacheMetrics() {
    val tools = listOf(userLookup, policyLookup, documentAnalysis)

    tools.forEach { tool ->
        val stats = tool.getCacheStats()

        metricsRegistry.gauge("cache_size", stats.size.toDouble())
        metricsRegistry.gauge("cache_hit_rate", stats.hitRate)
        metricsRegistry.counter("cache_hits", stats.hits)
        metricsRegistry.counter("cache_misses", stats.misses)
    }
}

// Schedule export every 30 seconds
scheduler.scheduleAtFixedRate(::exportCacheMetrics, 0, 30, TimeUnit.SECONDS)
```

### Debug Utilities

Inspect cache contents:

```kotlin
// Access internal cache (for debugging only!)
val cachedTool = userLookup.cached(ttl = 300)
val internalCache = (cachedTool as CachedTool).getCache()

// Print all keys
internalCache.keys.forEach { key ->
    println("Cached key: $key")
}

// Check specific entry
val entry = internalCache["tenant:ACME|user:123"]
if (entry != null) {
    val age = System.currentTimeMillis() - entry.timestamp
    println("Entry age: ${age / 1000}s")
    println("Expires in: ${(ttl * 1000 - age) / 1000}s")
}
```

## Best Practices

### 1. Always Include Tenant in Multi-Tenant Systems

```kotlin
// ❌ BAD: Tenant data leak
cache {
    keyBuilder = { params, _ ->
        "user:${params["userId"]}"  // Same key for all tenants!
    }
}

// ✅ GOOD: Tenant isolation
cache {
    keyBuilder = { params, context ->
        "${context.tenantId}|user:${params["userId"]}"
    }
}
```

### 2. Cache Only Successful Results

CachedTool automatically skips caching errors:

```kotlin
execute { params, context ->
    val result = riskyOperation()

    if (result.isError) {
        // Not cached! ✅
        return@execute ToolResult.error(result.message)
    }

    // Cached only if successful ✅
    ToolResult.success(result.data)
}
```

### 3. Choose TTL Based on Data Volatility

```kotlin
// User profiles: change infrequently
cache { ttl = 3600 }  // 1 hour

// Search results: change frequently
cache { ttl = 300 }  // 5 minutes

// Real-time stock prices: don't cache
// (no cache block)
```

### 4. Monitor Hit Rates

```kotlin
// Low hit rate = ineffective caching
val stats = cachedTool.getCacheStats()

if (stats.hitRate < 0.5) {
    log.warn("Low cache hit rate: ${stats.hitRate}")
    // Consider:
    // - Increasing maxSize
    // - Increasing TTL
    // - Reviewing key builder logic
}
```

### 5. Clear Cache on Mutations

```kotlin
// When data changes, invalidate cache
fun updateUser(userId: String, newData: UserData) {
    userRepository.update(userId, newData)
    userLookupTool.clearCache()  // Invalidate entire cache
}

// Or use short TTL for frequently updated data
cache { ttl = 60 }  // 1 minute
```

### 6. Test Cache Behavior

```kotlin
@Test
fun `test caching works`() = runBlocking {
    var execCount = 0

    val tool = contextAwareTool("test") {
        cache { ttl = 300 }
        execute { _, _ ->
            execCount++
            "result"
        }
    }

    withAgentContext("tenantId" to "TEST") {
        tool.execute(mapOf("id" to "123"))
        tool.execute(mapOf("id" to "123"))
        tool.execute(mapOf("id" to "123"))

        assertEquals(1, execCount)  // Only executed once!
    }
}
```

### 7. Handle Missing Context Gracefully

```kotlin
cache {
    keyBuilder = { params, context ->
        val tenantId = context.tenantId ?: "default"  // Fallback
        "$tenantId|${params["id"]}"
    }
}
```

### 8. Use Structured Keys

```kotlin
// ❌ BAD: Ambiguous
"user123policy456"  // Where does user end and policy start?

// ✅ GOOD: Clear structure
"tenant:ACME|user:123|policy:456"
```

## Testing

### Unit Tests

Test cache hit/miss behavior:

```kotlin
@Test
fun `first call misses cache, second hits`() = runBlocking {
    val tool = baseTool.cached(ttl = 300)

    withAgentContext("tenantId" to "TEST") {
        tool.execute(mapOf("id" to "123"))  // Miss
        tool.execute(mapOf("id" to "123"))  // Hit

        val stats = tool.getCacheStats()
        assertEquals(1, stats.hits)
        assertEquals(1, stats.misses)
    }
}
```

Test TTL expiration:

```kotlin
@Test
fun `cache expires after TTL`() = runBlocking {
    val tool = baseTool.cached(ttl = 1)  // 1 second

    // Note: .cached() uses parameter-only cache keys (no context needed)
    tool.execute(mapOf("id" to "123"))
    delay(1100)  // Wait for expiration
    tool.execute(mapOf("id" to "123"))

    val stats = tool.getCacheStats()
    assertEquals(0, stats.hits)  // Both were misses
    assertEquals(2, stats.misses)
}
```

Test LRU eviction:

```kotlin
@Test
fun `LRU eviction removes least recently used`() = runBlocking {
    val tool = baseTool.cached(ttl = 3600, maxSize = 2)

    tool.execute(mapOf("id" to "A"))  // Cache: [A]
    tool.execute(mapOf("id" to "B"))  // Cache: [A, B]
    tool.execute(mapOf("id" to "A"))  // Cache: [B, A] (A accessed)
    tool.execute(mapOf("id" to "C"))  // Cache: [A, C] (B evicted!)

    val stats = tool.getCacheStats()
    assertEquals(2, stats.size)
}
```

Test tenant isolation:

```kotlin
@Test
fun `different tenants have separate cache entries`() = runBlocking {
    val tool = contextAwareTool("test") {
        cache {
            keyBuilder = { params, context ->
                "${context.tenantId}|${params["id"]}"
            }
        }
        execute { params, _ -> "result:${params["id"]}" }
    }

    // Tenant A
    withAgentContext("tenantId" to "TENANT_A") {
        tool.execute(mapOf("id" to "123"))
    }

    // Tenant B (different cache entry!)
    withAgentContext("tenantId" to "TENANT_B") {
        tool.execute(mapOf("id" to "123"))
    }

    val stats = tool.getCacheStats()
    assertEquals(0, stats.hits)  // Both were misses (different keys)
    assertEquals(2, stats.misses)
    assertEquals(2, stats.size)  // 2 separate entries
}
```

### Integration Tests

Test with real database:

```kotlin
@Test
fun `caching reduces database queries`() = runBlocking {
    var dbQueryCount = 0

    val mockRepo = object : UserRepository {
        override fun findById(id: String): User {
            dbQueryCount++
            return User(id, "Test User")
        }
    }

    val tool = contextAwareTool("user_lookup") {
        cache { ttl = 300 }
        execute { params, _ ->
            mockRepo.findById(params["id"] as String)
        }
    }

    repeat(10) {
        tool.execute(mapOf("id" to "user-123"))
    }

    assertEquals(1, dbQueryCount)  // Only 1 query despite 10 calls!
}
```

### Load Tests

Test concurrent access:

```kotlin
@Test
fun `cache handles concurrent access`() = runBlocking {
    val tool = baseTool.cached(ttl = 3600)

    // 1000 concurrent requests
    val jobs = List(1000) {
        async {
            tool.execute(mapOf("id" to (it % 10).toString()))
        }
    }

    jobs.awaitAll()

    val stats = tool.getCacheStats()
    assertTrue(stats.size <= 10)  // At most 10 unique entries
    assertTrue(stats.hits > 900)  // High hit rate
}
```

## Troubleshooting

### Cache Not Working

**Symptom:** Every request is a cache miss.

**Possible Causes:**

1. **Context not propagated:**
   ```kotlin
   // ❌ Wrong: No context
   tool.execute(params)

   // ✅ Correct: With context
   withAgentContext("tenantId" to "ACME") {
       tool.execute(params)
   }
   ```

2. **Inconsistent parameters:**
   ```kotlin
   // These generate different cache keys:
   tool.execute(mapOf("id" to "123", "debug" to true))
   tool.execute(mapOf("id" to "123", "debug" to false))
   ```

3. **TTL too short:**
   ```kotlin
   cache { ttl = 1 }  // Expires too quickly!
   ```

### Low Hit Rate

**Symptom:** Hit rate is consistently below 50%.

**Solutions:**

1. **Increase cache size:**
   ```kotlin
   cache { maxSize = 10000 }  // Was 1000
   ```

2. **Increase TTL:**
   ```kotlin
   cache { ttl = 3600 }  // Was 300
   ```

3. **Review key builder:**
   ```kotlin
   // ❌ Too specific (creates many keys)
   keyBuilder = { params, context ->
       "${context.tenantId}|${params}|${System.currentTimeMillis()}"
   }

   // ✅ More general (better reuse)
   keyBuilder = { params, context ->
       "${context.tenantId}|${params["id"]}"
   }
   ```

### Memory Issues

**Symptom:** High memory usage or OOM errors.

**Solutions:**

1. **Reduce maxSize:**
   ```kotlin
   cache { maxSize = 1000 }  // Was 10000
   ```

2. **Reduce TTL:**
   ```kotlin
   cache { ttl = 300 }  // Entries expire faster
   ```

3. **Manually cleanup:**
   ```kotlin
   scheduler.scheduleAtFixedRate({
       cachedTool.cleanupExpired()
   }, 0, 5, TimeUnit.MINUTES)
   ```

### Stale Data

**Symptom:** Getting outdated cached results.

**Solutions:**

1. **Reduce TTL:**
   ```kotlin
   cache { ttl = 60 }  // 1 minute instead of 1 hour
   ```

2. **Clear cache on mutations:**
   ```kotlin
   fun updateData() {
       repository.update()
       cachedTool.clearCache()
   }
   ```

3. **Use cache versioning:**
   ```kotlin
   cache {
       keyBuilder = { params, context ->
           val version = getCurrentDataVersion()
           "${context.tenantId}|${params["id"]}|v:$version"
       }
   }
   ```

### Tenant Data Leak

**Symptom:** Users seeing other tenants' data.

**Solution:** Always include tenant in cache key:

```kotlin
// ❌ INSECURE
cache {
    keyBuilder = { params, _ ->
        "user:${params["userId"]}"
    }
}

// ✅ SECURE
cache {
    keyBuilder = { params, context ->
        "${context.tenantId}|user:${params["userId"]}"
    }
}
```

## API Reference

### CachedTool

```kotlin
class CachedTool(
    private val delegate: Tool,
    private val config: ToolCacheConfig = ToolCacheConfig()
) : Tool {
    // Get cache statistics
    fun getCacheStats(): ToolCacheStats

    // Clear all cached entries
    fun clearCache()

    // Remove expired entries
    fun cleanupExpired()

    // Execute with caching
    override suspend fun execute(parameters: Map<String, Any>): SpiceResult<ToolResult>
}
```

### ToolCacheConfig

```kotlin
data class ToolCacheConfig(
    val maxSize: Int = 1000,
    val ttl: Long = 3600,
    val enableMetrics: Boolean = true,
    val keyBuilder: ((Map<String, Any>, AgentContext?) -> String)? = null
)
```

### ToolCacheStats

```kotlin
data class ToolCacheStats(
    val toolName: String,
    val size: Int,
    val maxSize: Int,
    val hits: Long,
    val misses: Long,
    val hitRate: Double,
    val ttl: Long
) {
    override fun toString(): String =
        """
        Tool Cache Statistics ($toolName):
        - Size: $size / $maxSize
        - Hits: $hits
        - Misses: $misses
        - Hit Rate: ${"%.2f".format(hitRate * 100)}%
        - TTL: ${ttl}s
        """.trimIndent()
}
```

### Extension Functions

```kotlin
// Wrap tool with caching
fun Tool.cached(
    keyBuilder: ((Map<String, Any>, AgentContext?) -> String)? = null,
    ttl: Long = 3600,
    maxSize: Int = 1000,
    enableMetrics: Boolean = true
): Tool

// Create cached tool
fun cachedTool(
    delegate: Tool,
    config: ToolCacheConfig = ToolCacheConfig()
): Tool
```

### DSL (ContextAwareTool)

```kotlin
contextAwareTool("my_tool") {
    // ... tool config ...

    cache {
        keyBuilder = { params, context ->
            "${context.tenantId}|${params["id"]}"
        }
        ttl = 300
        maxSize = 1000
        enableMetrics = true
    }

    execute { params, context ->
        // ... tool logic ...
    }
}
```

## Related Documentation

- [Output Validation](../dsl-guide/output-validation.md) - Validate cached results
- [Context-Aware Tools](../dsl-guide/context-aware-tools.md) - Build tools with context
- [Tool Pipeline DSL](../orchestration/tool-pipeline.md) - Chain cached tools
- [Performance Overview](./overview.md) - Other optimization techniques

## Summary

Tool-Level Caching provides:

✅ **Automatic caching** with minimal configuration
✅ **TTL-based expiration** for fresh data
✅ **LRU eviction** for bounded memory
✅ **Context-aware keys** for multi-tenancy
✅ **Comprehensive metrics** for monitoring
✅ **Thread-safe** for high concurrency
✅ **Flexible key builders** for custom logic

Start optimizing your tools today! 🚀
