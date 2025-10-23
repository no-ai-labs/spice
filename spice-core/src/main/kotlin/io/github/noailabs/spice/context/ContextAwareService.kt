package io.github.noailabs.spice.context

import io.github.noailabs.spice.AgentContext
import kotlin.coroutines.coroutineContext

/**
 * 🏗️ Service Layer Context Support
 *
 * Base interface and utilities for services that need access to AgentContext.
 * Automatically retrieves context from the coroutine scope.
 *
 * @since 0.4.0
 */

/**
 * Context-aware service interface
 *
 * Implement this to access AgentContext from your service layer
 *
 * Example:
 * ```kotlin
 * class PolicyService : ContextAwareService {
 *     suspend fun lookup(policyType: String): Policy {
 *         val tenantId = getContext().tenantId ?: "CHIC"
 *         return repository.find(tenantId, policyType)
 *     }
 * }
 * ```
 */
interface ContextAwareService {
    /**
     * Get current AgentContext from coroutine scope
     *
     * @throws IllegalStateException if no AgentContext found
     */
    suspend fun getContext(): AgentContext {
        return coroutineContext[AgentContext]
            ?: throw IllegalStateException("No AgentContext found in coroutine scope")
    }

    /**
     * Get current AgentContext or null if not available
     */
    suspend fun getContextOrNull(): AgentContext? {
        return coroutineContext[AgentContext]
    }
}

/**
 * Convenience base class for context-aware services
 *
 * Provides helper methods for common context operations
 *
 * Example:
 * ```kotlin
 * class PolicyService : BaseContextAwareService() {
 *     suspend fun lookup(policyType: String): Policy = withTenant { tenantId ->
 *         repository.find(tenantId, policyType)
 *     }
 *
 *     suspend fun getUserPolicies(): List<Policy> = withUser { userId ->
 *         repository.findByUser(userId)
 *     }
 * }
 * ```
 */
abstract class BaseContextAwareService : ContextAwareService {

    /**
     * Execute block with tenant ID from context
     *
     * @throws IllegalStateException if no tenantId in context
     */
    protected suspend fun <T> withTenant(block: suspend (String) -> T): T {
        val tenantId = getContext().tenantId
            ?: throw IllegalStateException("No tenantId in context")
        return block(tenantId)
    }

    /**
     * Execute block with user ID from context
     *
     * @throws IllegalStateException if no userId in context
     */
    protected suspend fun <T> withUser(block: suspend (String) -> T): T {
        val userId = getContext().userId
            ?: throw IllegalStateException("No userId in context")
        return block(userId)
    }

    /**
     * Execute block with session ID from context
     *
     * @throws IllegalStateException if no sessionId in context
     */
    protected suspend fun <T> withSession(block: suspend (String) -> T): T {
        val sessionId = getContext().sessionId
            ?: throw IllegalStateException("No sessionId in context")
        return block(sessionId)
    }

    /**
     * Execute block with correlation ID from context
     *
     * @throws IllegalStateException if no correlationId in context
     */
    protected suspend fun <T> withCorrelation(block: suspend (String) -> T): T {
        val correlationId = getContext().correlationId
            ?: throw IllegalStateException("No correlationId in context")
        return block(correlationId)
    }

    /**
     * Execute block with tenant and user IDs from context
     *
     * @throws IllegalStateException if no tenantId or userId in context
     */
    protected suspend fun <T> withTenantAndUser(block: suspend (String, String) -> T): T {
        val context = getContext()
        val tenantId = context.tenantId
            ?: throw IllegalStateException("No tenantId in context")
        val userId = context.userId
            ?: throw IllegalStateException("No userId in context")
        return block(tenantId, userId)
    }

    /**
     * Execute block with optional tenant ID (default if not present)
     */
    protected suspend fun <T> withTenantOrDefault(
        default: String = "default",
        block: suspend (String) -> T
    ): T {
        val tenantId = getContext().tenantId ?: default
        return block(tenantId)
    }

    /**
     * Execute block with optional user ID (default if not present)
     */
    protected suspend fun <T> withUserOrDefault(
        default: String = "anonymous",
        block: suspend (String) -> T
    ): T {
        val userId = getContext().userId ?: default
        return block(userId)
    }
}

/**
 * Example: Policy Service with automatic tenant context
 *
 * ```kotlin
 * class PolicyService : BaseContextAwareService() {
 *     suspend fun lookup(policyType: String): Policy = withTenant { tenantId ->
 *         // tenantId automatically from context
 *         repository.find(tenantId, policyType)
 *     }
 *
 *     suspend fun create(policy: Policy): Policy = withTenantAndUser { tenantId, userId ->
 *         // Both IDs automatically from context
 *         repository.save(policy.copy(
 *             tenantId = tenantId,
 *             createdBy = userId
 *         ))
 *     }
 * }
 * ```
 */

/**
 * Example: Analytics Service with optional context
 *
 * ```kotlin
 * class AnalyticsService : BaseContextAwareService() {
 *     suspend fun track(event: String) = withTenantOrDefault("global") { tenantId ->
 *         // Falls back to "global" if no tenantId in context
 *         analytics.record(tenantId, event)
 *     }
 * }
 * ```
 */

/**
 * Repository pattern with context support
 *
 * Example:
 * ```kotlin
 * interface ContextAwareRepository<T> : ContextAwareService {
 *     suspend fun findAll(): List<T> = withTenant { tenantId ->
 *         findAllByTenant(tenantId)
 *     }
 *
 *     suspend fun findAllByTenant(tenantId: String): List<T>
 * }
 * ```
 */
