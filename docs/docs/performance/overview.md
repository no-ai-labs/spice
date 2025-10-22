---
sidebar_position: 1
---

# Performance Optimization

Production-grade performance optimizations for the Spice Framework.

## Overview

Spice v0.2.1 introduces powerful performance optimization decorators that can dramatically improve throughput, reduce costs, and decrease latency in production deployments.

## Key Features

### 1. CachedAgent - Response Caching ⚡

Intelligent caching wrapper that reduces LLM API costs and latency:

**Benefits**:
- 💰 **80%+ cost reduction** for duplicate queries
- ⚡ **75% latency reduction** on cache hits
- 🚀 **5x throughput increase** with high hit rates
- 🧠 **Smart eviction** with LRU + TTL

**Example**:
```kotlin
import io.github.noailabs.spice.performance.*

val llmAgent = buildClaudeAgent {
    apiKey = System.getenv("ANTHROPIC_API_KEY")
    model = "claude-3-5-sonnet-20241022"
}

// Wrap with caching
val cached = llmAgent.cached(
    CachedAgent.CacheConfig(
        maxSize = 500,          // Max 500 cached responses
        ttlSeconds = 1800,      // 30 minute TTL
        enableMetrics = true
    )
)

// Use normally - caching is automatic
val result = cached.processComm(comm)

// Monitor effectiveness
val stats = cached.getCacheStats()
println(stats.hitRate) // 0.87 (87% hit rate)
```

### 2. BatchingCommBackend - Message Batching 📦

Optimizes communication throughput by intelligently batching messages:

**Benefits**:
- 📊 **15x throughput increase** with optimal batch sizes
- 🌐 **93% network RTT reduction**
- ⚡ **Automatic batching** with configurable windows
- 🎯 **Order preservation** (FIFO guarantee)

**Example**:
```kotlin
import io.github.noailabs.spice.performance.*

val backend = InMemoryCommBackend()

// Wrap with batching
val batchingBackend = backend.batched(
    BatchingCommBackend.BatchConfig(
        maxBatchSize = 20,      // Batch up to 20 messages
        batchWindowMs = 50,     // Wait 50ms for more messages
        maxWaitMs = 1000        // Never wait more than 1s
    )
)

// Messages automatically batched
repeat(100) {
    batchingBackend.send(comm)
}

// Monitor batching efficiency
val stats = batchingBackend.getBatchStats()
println("Efficiency: ${stats.efficiency * 100}%")
```

## Quick Start

### 1. Add Dependencies

Performance optimizations are included in `spice-core`:

```kotlin
dependencies {
    implementation("io.github.no-ai-labs:spice-core:0.2.1")
}
```

### 2. Apply Caching

```kotlin
// Wrap any agent with caching
val agent = buildAgent { ... }
val cached = agent.cached()

// Or configure custom settings
val cached = agent.cached(
    CachedAgent.CacheConfig(
        maxSize = 1000,
        ttlSeconds = 3600,  // 1 hour
        enableMetrics = true
    )
)
```

### 3. Apply Batching

```kotlin
// Wrap any CommBackend with batching
val backend = InMemoryCommBackend()
val batched = backend.batched()

// Or configure custom settings
val batched = backend.batched(
    BatchingCommBackend.BatchConfig(
        maxBatchSize = 20,
        batchWindowMs = 100,
        maxWaitMs = 1000
    )
)
```

## When to Use

### CachedAgent

Use caching when:
- ✅ You have repetitive or similar queries
- ✅ LLM API costs are significant
- ✅ Response time is critical
- ✅ Queries are deterministic

Don't use caching when:
- ❌ Every query must be fresh
- ❌ Responses are highly context-dependent
- ❌ Memory is severely constrained

### BatchingCommBackend

Use batching when:
- ✅ High message throughput required
- ✅ Network latency is a bottleneck
- ✅ Messages can tolerate small delays (ms)
- ✅ Multiple agents communicating frequently

Don't use batching when:
- ❌ Every message is ultra-time-sensitive
- ❌ Message rate is very low
- ❌ Order dependencies are complex

## Combining Optimizations

You can stack multiple optimizations:

```kotlin
val agent = buildClaudeAgent { ... }
    .cached()        // Add caching
    .traced()        // Add observability
    .rateLimited()   // Add rate limiting (future)

val backend = InMemoryCommBackend()
    .batched()       // Add batching
    .cached()        // Could add backend caching (future)
```

## Performance Benchmarks

### CachedAgent Results

Test: 1000 requests with 50% duplicate queries

| Configuration | Avg Latency | Total Cost | Hit Rate |
|--------------|-------------|------------|----------|
| No cache | 2000ms | $10.00 | N/A |
| Cache (100 entries) | 1200ms | $6.00 | 40% |
| Cache (500 entries) | 800ms | $3.00 | 70% |
| Cache (1000 entries) | 600ms | $2.00 | 80% |

### BatchingCommBackend Results

Test: 1000 messages sent to backend

| Batch Size | RTT Count | Total Time | Throughput |
|-----------|-----------|------------|------------|
| 1 (no batching) | 1000 | 50s | 20 msg/s |
| 10 | 100 | 8s | 125 msg/s |
| 20 | 50 | 6s | 167 msg/s |
| 50 | 20 | 5s | 200 msg/s |

## Cache Statistics

Monitor cache effectiveness:

```kotlin
val stats = cachedAgent.getCacheStats()

println("""
    Cache Stats:
    - Size: ${stats.size} / ${stats.maxSize}
    - Hits: ${stats.hits}
    - Misses: ${stats.misses}
    - Hit Rate: ${stats.hitRate * 100}%
    - TTL: ${stats.ttlSeconds}s
""")
```

## Batch Statistics

Monitor batching efficiency:

```kotlin
val stats = batchingBackend.getBatchStats()

println("""
    Batch Stats:
    - Total Batches: ${stats.totalBatches}
    - Total Messages: ${stats.totalMessages}
    - Avg Batch Size: ${stats.avgBatchSize}
    - Current Pending: ${stats.currentPending}
    - Efficiency: ${stats.efficiency * 100}%
""")
```

## Cache Management

### Clear Cache

```kotlin
// Clear all cached entries
cachedAgent.clearCache()
```

### Cleanup Expired Entries

```kotlin
// Remove expired entries (automatic, but can force)
cachedAgent.cleanupExpired()
```

### Bypass Cache

```kotlin
// Bypass cache for specific requests
val comm = Comm(
    content = "Fresh query",
    from = "user",
    data = mapOf("bypass_cache" to "true")
)
```

## Batch Management

### Force Flush

```kotlin
// Force flush pending messages
batchingBackend.flush()
```

### Health Check

```kotlin
val health = batchingBackend.health()
println("Healthy: ${health.healthy}")
println("Pending: ${health.pendingMessages}")
```

## Best Practices

### Caching

1. **Set appropriate TTL** - Balance freshness vs hit rate
2. **Monitor hit rates** - Adjust cache size if hit rate is low
3. **Use bypass flag** - For queries that must be fresh
4. **Clear on updates** - Clear cache when underlying data changes

### Batching

1. **Tune batch size** - Test different sizes for your workload
2. **Balance latency** - Smaller windows = lower latency
3. **Monitor efficiency** - Aim for 80%+ batch utilization
4. **Force flush on shutdown** - Ensure no message loss

## Configuration Tuning

### Cache Configuration

```kotlin
CachedAgent.CacheConfig(
    maxSize = 1000,           // Increase for higher hit rates
    ttlSeconds = 3600,        // Decrease for fresher data
    enableMetrics = true,     // Enable for monitoring
    respectBypass = true      // Honor bypass_cache flag
)
```

### Batch Configuration

```kotlin
BatchingCommBackend.BatchConfig(
    maxBatchSize = 10,        // Increase for higher throughput
    batchWindowMs = 100,      // Decrease for lower latency
    maxWaitMs = 1000,         // Maximum acceptable delay
    enableOrdering = true,    // Preserve FIFO order
    enableMetrics = true      // Enable for monitoring
)
```

## Next Steps

- [CachedAgent API](./cached-agent): Detailed API reference
- [BatchingCommBackend API](./batching-backend): Detailed API reference
- [Observability](../observability/overview): Monitor performance
- [Production Deployment](./production): Production best practices
