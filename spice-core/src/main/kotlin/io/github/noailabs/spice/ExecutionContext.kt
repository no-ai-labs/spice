package io.github.noailabs.spice

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Unified execution context for Spice graphs.
 * Single source of truth for tenant/user/tracing and graph-level metadata.
 *
 * Note: Backed by an immutable Map; mutation returns a new instance.
 */
data class ExecutionContext(
    private val data: Map<String, Any> = emptyMap()
) : AbstractCoroutineContextElement(ExecutionContext) {

    companion object Key : CoroutineContext.Key<ExecutionContext> {
        fun of(map: Map<String, Any>): ExecutionContext = ExecutionContext(map.toMap())
    }

    fun toMap(): Map<String, Any> = data

    fun get(key: String): Any? = data[key]

    @Suppress("UNCHECKED_CAST")
    fun <T> getAs(key: String): T? = data[key] as? T

    fun plus(key: String, value: Any): ExecutionContext = copy(data = data + (key to value))

    fun plusAll(additional: Map<String, Any>): ExecutionContext = copy(data = data + additional)

    val tenantId: String? get() = getAs("tenantId")
    val userId: String? get() = getAs("userId")
    val correlationId: String? get() = getAs("correlationId")
}

/**
 * Bridge: Convert AgentContext to ExecutionContext.
 */
fun AgentContext.toExecutionContext(additional: Map<String, Any> = emptyMap()): ExecutionContext {
    return ExecutionContext.of(this.toMap() + additional)
}

/**
 * Bridge: Convert ExecutionContext back to AgentContext (for legacy APIs).
 */
fun ExecutionContext.toAgentContext(): AgentContext {
    val pairs = this.toMap().entries.map { it.key to it.value }
    return AgentContext.of(*pairs.toTypedArray())
}

/**
 * Get current ExecutionContext from coroutine context.
 * Returns null if not in ExecutionContext scope.
 *
 * Example:
 * ```kotlin
 * suspend fun myService() {
 *     val context = currentExecutionContext()
 *     val tenantId = context?.tenantId
 * }
 * ```
 */
suspend fun currentExecutionContext(): ExecutionContext? {
    return kotlin.coroutines.coroutineContext[ExecutionContext]
}

/**
 * Require ExecutionContext in current coroutine.
 * Throws if not in ExecutionContext scope.
 *
 * Example:
 * ```kotlin
 * suspend fun myService() {
 *     val context = requireExecutionContext()
 *     val tenantId = context.tenantId ?: error("No tenant")
 * }
 * ```
 */
suspend fun requireExecutionContext(): ExecutionContext {
    return currentExecutionContext()
        ?: throw IllegalStateException("No ExecutionContext in coroutine scope")
}

/**
 * Get current tenant ID from ExecutionContext.
 * Returns null if not in ExecutionContext scope or tenant ID not set.
 *
 * Example:
 * ```kotlin
 * suspend fun myService() {
 *     val tenantId = getCurrentTenantId() ?: "default"
 * }
 * ```
 */
suspend fun getCurrentTenantId(): String? {
    return currentExecutionContext()?.tenantId
}

/**
 * Get current user ID from ExecutionContext.
 * Returns null if not in ExecutionContext scope or user ID not set.
 */
suspend fun getCurrentUserId(): String? {
    return currentExecutionContext()?.userId
}

/**
 * Get current correlation ID from ExecutionContext.
 * Returns null if not in ExecutionContext scope or correlation ID not set.
 */
suspend fun getCurrentCorrelationId(): String? {
    return currentExecutionContext()?.correlationId
}

