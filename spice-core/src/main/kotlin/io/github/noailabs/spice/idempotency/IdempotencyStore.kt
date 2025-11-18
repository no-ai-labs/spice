package io.github.noailabs.spice.idempotency

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult
import kotlin.time.Duration

/**
 * 🔒 Idempotency Store for Spice Framework 1.0.0
 *
 * Provides duplicate detection and caching for idempotent message execution.
 * Prevents re-execution of messages with the same causationId (idempotency key).
 *
 * **Architecture Pattern:**
 * ```
 * Client sends message M1 (causationId = "order-123")
 *   ↓
 * GraphRunner checks idempotency store
 *   ↓
 * Cache miss → Execute graph → Store result
 *   ↓
 * Client retries M2 (same causationId = "order-123")
 *   ↓
 * GraphRunner checks idempotency store
 *   ↓
 * Cache hit → Return cached result (no re-execution)
 * ```
 *
 * **Use Cases:**
 * 1. **Network retry safety:** Duplicate HTTP requests don't re-execute
 * 2. **Event deduplication:** Same event from message queue processed once
 * 3. **Resume protection:** Graph resume doesn't re-execute completed nodes
 * 4. **Tool call caching:** Expensive API calls cached by arguments
 *
 * **Key Concepts:**
 * - **Idempotency Key:** Typically message.causationId or custom key
 * - **TTL:** Cache duration to balance safety vs. memory usage
 * - **Conflict Resolution:** Latest write wins (LWW) for concurrent writes
 *
 * **Implementations:**
 * - `InMemoryIdempotencyStore`: Fast, ephemeral (testing, dev)
 * - `RedisIdempotencyStore`: Distributed, persistent (production)
 * - `HybridIdempotencyStore`: L1 (memory) + L2 (Redis) cache
 *
 * **Example Usage:**
 * ```kotlin
 * // In GraphRunner
 * suspend fun execute(graph: Graph, message: SpiceMessage): SpiceResult<SpiceMessage> {
 *     // Check idempotency
 *     message.causationId?.let { key ->
 *         graph.idempotencyStore?.get(key)?.let { cached ->
 *             logger.info { "Idempotency hit: $key" }
 *             return SpiceResult.success(cached)
 *         }
 *     }
 *
 *     // Execute
 *     val result = runGraph(graph, message)
 *
 *     // Cache result
 *     message.causationId?.let { key ->
 *         graph.idempotencyStore?.save(key, result, ttl = Duration.hours(24))
 *     }
 *
 *     return result
 * }
 * ```
 *
 * **Thread Safety:**
 * All implementations must be thread-safe for concurrent access.
 *
 * **Serialization:**
 * Messages are serialized using kotlinx.serialization (JSON format).
 *
 * @author Spice Framework
 * @since 1.0.0
 */
interface IdempotencyStore {
    /**
     * Retrieve cached message by idempotency key
     *
     * @param key Idempotency key (typically message.causationId)
     * @return Cached SpiceMessage if exists and not expired, null otherwise
     */
    suspend fun get(key: String): SpiceMessage?

    /**
     * Store message result with TTL
     *
     * @param key Idempotency key
     * @param message Message to cache
     * @param ttl Time-to-live for cache entry
     * @return SpiceResult indicating success or failure
     */
    suspend fun save(key: String, message: SpiceMessage, ttl: Duration): SpiceResult<Unit>

    /**
     * Delete cached message by key
     *
     * @param key Idempotency key
     * @return SpiceResult indicating success or failure
     */
    suspend fun delete(key: String): SpiceResult<Unit>

    /**
     * Check if key exists in cache
     *
     * @param key Idempotency key
     * @return true if key exists and not expired
     */
    suspend fun exists(key: String): Boolean

    /**
     * Clear all cached messages
     * Use with caution - only for testing or maintenance
     *
     * @return SpiceResult indicating success or failure
     */
    suspend fun clear(): SpiceResult<Unit>

    /**
     * Get cache statistics
     *
     * @return IdempotencyStats with hit/miss counts
     */
    suspend fun getStats(): IdempotencyStats
}

/**
 * 📊 Idempotency Store Statistics
 *
 * Metrics for monitoring cache performance and effectiveness.
 *
 * **Key Metrics:**
 * - **Hit Rate:** % of requests served from cache (higher is better)
 * - **Miss Rate:** % of requests requiring execution
 * - **Eviction Rate:** % of entries evicted due to TTL or size limits
 *
 * **Example:**
 * ```kotlin
 * val stats = store.getStats()
 * println("Hit rate: ${stats.hitRate()}%")
 * println("Total entries: ${stats.totalEntries}")
 * println("Memory usage: ${stats.memoryUsageBytes / 1024 / 1024}MB")
 * ```
 *
 * @property hits Number of cache hits
 * @property misses Number of cache misses
 * @property evictions Number of evicted entries
 * @property totalEntries Current number of cached entries
 * @property memoryUsageBytes Estimated memory usage in bytes
 *
 * @author Spice Framework
 * @since 1.0.0
 */
data class IdempotencyStats(
    val hits: Long,
    val misses: Long,
    val evictions: Long,
    val totalEntries: Long,
    val memoryUsageBytes: Long
) {
    /**
     * Calculate cache hit rate percentage
     * @return Hit rate (0-100)
     */
    fun hitRate(): Double {
        val total = hits + misses
        return if (total > 0) (hits.toDouble() / total) * 100 else 0.0
    }

    /**
     * Calculate cache miss rate percentage
     * @return Miss rate (0-100)
     */
    fun missRate(): Double {
        return 100.0 - hitRate()
    }

    /**
     * Check if cache is performing well
     * Threshold: Hit rate > 70%
     */
    fun isEffective(): Boolean {
        return hitRate() > 70.0
    }
}

/**
 * 🔑 Idempotency Key Generator
 *
 * Utility for generating deterministic idempotency keys from message properties.
 * Used when causationId is not available or custom key logic is needed.
 *
 * **Key Generation Strategies:**
 * 1. **Content-based:** Hash of message content + metadata
 * 2. **Tool-based:** Hash of tool name + arguments
 * 3. **Node-based:** Hash of nodeId + input data
 *
 * **Example:**
 * ```kotlin
 * // Content-based key
 * val key1 = IdempotencyKey.fromContent(message.content, message.from)
 *
 * // Tool-based key
 * val key2 = IdempotencyKey.fromToolCall(toolCall.function.name, toolCall.function.arguments)
 *
 * // Node-based key
 * val key3 = IdempotencyKey.fromNode(nodeId, message.data)
 * ```
 *
 * @author Spice Framework
 * @since 1.0.0
 */
object IdempotencyKey {
    /**
     * Generate key from message content
     * Format: idem_content_{hash}
     *
     * @param content Message content
     * @param from Message sender
     * @return Deterministic idempotency key
     */
    fun fromContent(content: String, from: String): String {
        val combined = "$from:$content"
        val hash = combined.hashCode().toString(16)
        return "idem_content_$hash"
    }

    /**
     * Generate key from tool call
     * Format: idem_tool_{toolName}_{argsHash}
     *
     * @param toolName Tool function name
     * @param arguments Tool arguments
     * @return Deterministic idempotency key
     */
    fun fromToolCall(toolName: String, arguments: Map<String, Any>): String {
        val argsHash = arguments.toString().hashCode().toString(16)
        return "idem_tool_${toolName}_$argsHash"
    }

    /**
     * Generate key from node execution
     * Format: idem_node_{nodeId}_{dataHash}
     *
     * @param nodeId Node identifier
     * @param data Input data
     * @return Deterministic idempotency key
     */
    fun fromNode(nodeId: String, data: Map<String, Any>): String {
        val dataHash = data.toString().hashCode().toString(16)
        return "idem_node_${nodeId}_$dataHash"
    }

    /**
     * Generate key from arbitrary properties
     * Format: idem_custom_{hash}
     *
     * @param properties Key-value pairs to hash
     * @return Deterministic idempotency key
     */
    fun fromProperties(properties: Map<String, Any>): String {
        val hash = properties.toString().hashCode().toString(16)
        return "idem_custom_$hash"
    }
}
