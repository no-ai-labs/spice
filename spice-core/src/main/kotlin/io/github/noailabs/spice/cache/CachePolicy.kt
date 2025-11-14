package io.github.noailabs.spice.cache

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.idempotency.IdempotencyKey
import io.github.noailabs.spice.idempotency.IdempotencyStore
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

enum class CacheKind {
    TOOL_CALL,
    STEP,
    INTENT
}

data class CachePolicy(
    val toolCallTtl: Duration = 1.hours,
    val stepTtl: Duration = 6.hours,
    val intentTtl: Duration = 1.days
) {
    fun ttlFor(kind: CacheKind): Duration = when (kind) {
        CacheKind.TOOL_CALL -> toolCallTtl
        CacheKind.STEP -> stepTtl
        CacheKind.INTENT -> intentTtl
    }
}

/**
 * Thin wrapper around IdempotencyStore to make key strategy reusable.
 *
 * **Important**: If store is null, all lookup methods return null (caching disabled)
 * and all store methods do nothing (no-op). This is intentional for optional caching.
 */
class IdempotencyManager(
    private val store: IdempotencyStore?,
    private val policy: CachePolicy = CachePolicy()
) {
    /**
     * Lookup tool call result from cache.
     * Returns null if store not configured, key not found, or entry expired.
     */
    suspend fun lookupToolCall(toolName: String, arguments: Map<String, Any>): SpiceMessage? {
        if (store == null) return null  // Caching disabled
        val key = IdempotencyKey.fromToolCall(toolName, arguments)
        return store.get(key)
    }

    /**
     * Store tool call result in cache.
     * No-op if store not configured.
     */
    suspend fun storeToolCall(
        toolName: String,
        arguments: Map<String, Any>,
        result: SpiceMessage
    ) {
        if (store == null) return  // Caching disabled
        val key = IdempotencyKey.fromToolCall(toolName, arguments)
        store.save(key, result, policy.ttlFor(CacheKind.TOOL_CALL))
    }

    /**
     * Lookup step result from cache.
     * Returns null if store not configured, key not found, or entry expired.
     */
    suspend fun lookupStep(stepId: String, intent: String): SpiceMessage? {
        if (store == null) return null  // Caching disabled
        val key = IdempotencyKey.fromProperties(mapOf("step" to stepId, "intent" to intent))
        return store.get(key)
    }

    /**
     * Store step result in cache.
     * No-op if store not configured.
     */
    suspend fun storeStep(stepId: String, intent: String, result: SpiceMessage) {
        if (store == null) return  // Caching disabled
        val key = IdempotencyKey.fromProperties(mapOf("step" to stepId, "intent" to intent))
        store.save(key, result, policy.ttlFor(CacheKind.STEP))
    }

    /**
     * Lookup intent result from cache.
     * Returns null if store not configured, key not found, or entry expired.
     */
    suspend fun lookupIntent(intent: String): SpiceMessage? {
        if (store == null) return null  // Caching disabled
        val key = IdempotencyKey.fromContent(intent, "intent-cache")
        return store.get(key)
    }

    /**
     * Store intent result in cache.
     * No-op if store not configured.
     */
    suspend fun storeIntent(intent: String, result: SpiceMessage) {
        if (store == null) return  // Caching disabled
        val key = IdempotencyKey.fromContent(intent, "intent-cache")
        store.save(key, result, policy.ttlFor(CacheKind.INTENT))
    }
}
