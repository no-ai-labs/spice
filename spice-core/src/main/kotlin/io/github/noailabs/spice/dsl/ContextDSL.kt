package io.github.noailabs.spice.dsl

import io.github.noailabs.spice.AgentContext
import io.github.noailabs.spice.ExecutionContext
import io.github.noailabs.spice.toExecutionContext
import kotlinx.coroutines.withContext

/**
 * 🎯 Agent Context DSL for automatic context propagation
 *
 * Provides convenient functions to work with AgentContext in coroutine scopes.
 * AgentContext is automatically propagated to all child coroutines.
 *
 * @since 0.4.0
 */

/**
 * Execute block with AgentContext automatically propagated to all child coroutines
 *
 * Example:
 * ```kotlin
 * withAgentContext(
 *     "tenantId" to "CHIC",
 *     "userId" to "user-123",
 *     "sessionId" to "session-456"
 * ) {
 *     // All code here has access to AgentContext
 *     agent.processComm(comm)
 *
 *     // Tools can access context via coroutineContext[AgentContext]
 *     val tenantId = currentAgentContext()?.tenantId
 * }
 * ```
 */
suspend fun <T> withAgentContext(
    vararg pairs: Pair<String, Any>,
    block: suspend () -> T
): T {
    val agentContext = AgentContext.of(*pairs)
    val execContext = agentContext.toExecutionContext()
    return withContext(agentContext + execContext) { block() }
}

/**
 * Execute block with existing AgentContext
 *
 * Example:
 * ```kotlin
 * val context = AgentContext.of("tenantId" to "CHIC", "userId" to "user-123")
 *
 * withAgentContext(context) {
 *     agent.processComm(comm)
 * }
 * ```
 */
suspend fun <T> withAgentContext(
    context: AgentContext,
    block: suspend () -> T
): T {
    val execContext = context.toExecutionContext()
    return withContext(context + execContext) { block() }
}

/**
 * Execute block with enriched AgentContext (merges with existing context if available)
 *
 * Example:
 * ```kotlin
 * withAgentContext("tenantId" to "CHIC") {
 *     // Parent context has tenantId
 *
 *     withEnrichedContext("sessionId" to "session-456") {
 *         // Now has both tenantId and sessionId
 *         val tenant = currentAgentContext()?.tenantId  // "CHIC"
 *         val session = currentAgentContext()?.sessionId  // "session-456"
 *     }
 * }
 * ```
 */
suspend fun <T> withEnrichedContext(
    vararg pairs: Pair<String, Any>,
    block: suspend () -> T
): T {
    val current = currentAgentContext()
    val enriched = current?.withAll(*pairs) ?: AgentContext.of(*pairs)
    val execContext = enriched.toExecutionContext()
    return withContext(enriched + execContext) { block() }
}

/**
 * Get current AgentContext from coroutine context, or null if not available
 *
 * Example:
 * ```kotlin
 * suspend fun myFunction() {
 *     val context = currentAgentContext()
 *     if (context != null) {
 *         println("Tenant: ${context.tenantId}")
 *         println("User: ${context.userId}")
 *     }
 * }
 * ```
 */
suspend fun currentAgentContext(): AgentContext? {
    return kotlin.coroutines.coroutineContext[AgentContext]
}

/**
 * Get current AgentContext or throw exception if not available
 *
 * Example:
 * ```kotlin
 * suspend fun myFunction() {
 *     val context = requireAgentContext()
 *     println("Tenant: ${context.tenantId}") // Safe to use
 * }
 * ```
 *
 * @throws IllegalStateException if no AgentContext found in coroutine scope
 */
suspend fun requireAgentContext(): AgentContext {
    return kotlin.coroutines.coroutineContext[AgentContext]
        ?: throw IllegalStateException(
            "No AgentContext found in coroutine scope. " +
            "Use withAgentContext { } block to provide context."
        )
}

/**
 * Get current tenant ID from AgentContext, or null if not available
 */
suspend fun currentTenantId(): String? {
    return currentAgentContext()?.tenantId
}

/**
 * Get current user ID from AgentContext, or null if not available
 */
suspend fun currentUserId(): String? {
    return currentAgentContext()?.userId
}

/**
 * Get current session ID from AgentContext, or null if not available
 */
suspend fun currentSessionId(): String? {
    return currentAgentContext()?.sessionId
}

/**
 * Get current correlation ID from AgentContext, or null if not available
 */
suspend fun currentCorrelationId(): String? {
    return currentAgentContext()?.correlationId
}

/**
 * Execute block only if AgentContext is available
 *
 * Example:
 * ```kotlin
 * withContextIfAvailable { context ->
 *     println("Running with tenant: ${context.tenantId}")
 * }
 * ```
 */
suspend fun <T> withContextIfAvailable(block: suspend (AgentContext) -> T): T? {
    val context = currentAgentContext()
    return context?.let { block(it) }
}

/**
 * Execute block only if specific context key is present
 *
 * Example:
 * ```kotlin
 * withContextKey("tenantId") { tenantId ->
 *     println("Tenant: $tenantId")
 * }
 * ```
 */
suspend fun <T> withContextKey(key: String, block: suspend (Any) -> T): T? {
    val context = currentAgentContext()
    val value = context?.get(key)
    return value?.let { block(it) }
}
