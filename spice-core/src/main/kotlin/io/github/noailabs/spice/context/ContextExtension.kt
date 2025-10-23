package io.github.noailabs.spice.context

import io.github.noailabs.spice.AgentContext

/**
 * 🔌 Context Extension System
 *
 * Allows runtime enrichment of AgentContext with additional data.
 * Extensions can be registered globally and applied automatically when context is created.
 *
 * @since 0.4.0
 */

/**
 * Context Extension interface
 *
 * Implement this to create custom context enrichment logic
 *
 * Example:
 * ```kotlin
 * class TenantConfigExtension : ContextExtension {
 *     override val key = "tenant-config"
 *
 *     override suspend fun enrich(context: AgentContext): AgentContext {
 *         val tenantId = context.tenantId ?: return context
 *         val config = loadTenantConfig(tenantId)
 *         return context.with("tenant_config", config)
 *     }
 * }
 * ```
 */
interface ContextExtension {
    /**
     * Unique key for this extension
     */
    val key: String

    /**
     * Enrich the given context with additional data
     *
     * @param context The context to enrich
     * @return Enriched context
     */
    suspend fun enrich(context: AgentContext): AgentContext
}

/**
 * Tenant-specific context enrichment
 *
 * Loads tenant configuration and adds it to the context
 *
 * Example:
 * ```kotlin
 * val extension = TenantContextExtension { tenantId ->
 *     mapOf(
 *         "features" to listOf("feature1", "feature2"),
 *         "limits" to mapOf("max_requests" to 1000)
 *     )
 * }
 *
 * ContextExtensionRegistry.register(extension)
 * ```
 */
class TenantContextExtension(
    private val configLoader: suspend (String) -> Map<String, Any>
) : ContextExtension {

    override val key = "tenant"

    override suspend fun enrich(context: AgentContext): AgentContext {
        val tenantId = context.tenantId ?: return context

        return try {
            val config = configLoader(tenantId)
            context.with(
                "tenant_config", config
            ).with(
                "tenant_features", config["features"] ?: emptyList<String>()
            )
        } catch (e: Exception) {
            // Log error and return original context
            println("⚠️ Failed to enrich tenant context: ${e.message}")
            context
        }
    }
}

/**
 * User-specific context enrichment
 *
 * Loads user profile and permissions, adds them to the context
 *
 * Example:
 * ```kotlin
 * val extension = UserContextExtension { userId ->
 *     mapOf(
 *         "email" to "user@example.com",
 *         "permissions" to listOf("read", "write")
 *     )
 * }
 *
 * ContextExtensionRegistry.register(extension)
 * ```
 */
class UserContextExtension(
    private val userLoader: suspend (String) -> Map<String, Any>
) : ContextExtension {

    override val key = "user"

    override suspend fun enrich(context: AgentContext): AgentContext {
        val userId = context.userId ?: return context

        return try {
            val userData = userLoader(userId)
            context.with(
                "user_profile", userData
            ).with(
                "user_permissions", userData["permissions"] ?: emptyList<String>()
            )
        } catch (e: Exception) {
            println("⚠️ Failed to enrich user context: ${e.message}")
            context
        }
    }
}

/**
 * Session-specific context enrichment
 *
 * Loads session data and adds it to the context
 *
 * Example:
 * ```kotlin
 * val extension = SessionContextExtension { sessionId ->
 *     mapOf(
 *         "startedAt" to System.currentTimeMillis(),
 *         "deviceInfo" to mapOf("type" to "mobile")
 *     )
 * }
 *
 * ContextExtensionRegistry.register(extension)
 * ```
 */
class SessionContextExtension(
    private val sessionLoader: suspend (String) -> Map<String, Any>
) : ContextExtension {

    override val key = "session"

    override suspend fun enrich(context: AgentContext): AgentContext {
        val sessionId = context.sessionId ?: return context

        return try {
            val sessionData = sessionLoader(sessionId)
            context.with("session_data", sessionData)
        } catch (e: Exception) {
            println("⚠️ Failed to enrich session context: ${e.message}")
            context
        }
    }
}

/**
 * Extension registry and executor
 *
 * Global registry for context extensions. Extensions are applied in registration order.
 *
 * Example:
 * ```kotlin
 * // Register extensions
 * ContextExtensionRegistry.register(TenantContextExtension { ... })
 * ContextExtensionRegistry.register(UserContextExtension { ... })
 *
 * // Enrich context
 * val enriched = ContextExtensionRegistry.enrichContext(baseContext)
 * ```
 */
object ContextExtensionRegistry {
    private val extensions = mutableListOf<ContextExtension>()

    /**
     * Register a context extension
     */
    fun register(extension: ContextExtension) {
        extensions.add(extension)
    }

    /**
     * Unregister extension by key
     */
    fun unregister(key: String) {
        extensions.removeIf { it.key == key }
    }

    /**
     * Clear all extensions
     */
    fun clear() {
        extensions.clear()
    }

    /**
     * Get all registered extensions
     */
    fun getAll(): List<ContextExtension> = extensions.toList()

    /**
     * Enrich context with all registered extensions
     *
     * Extensions are applied in registration order. If an extension fails,
     * it's skipped and the error is logged.
     *
     * @param context Base context to enrich
     * @return Enriched context
     */
    suspend fun enrichContext(context: AgentContext): AgentContext {
        var enriched = context
        extensions.forEach { extension ->
            try {
                enriched = extension.enrich(enriched)
            } catch (e: Exception) {
                println("⚠️ Extension '${extension.key}' failed: ${e.message}")
            }
        }
        return enriched
    }

    /**
     * Check if extension is registered
     */
    fun has(key: String): Boolean = extensions.any { it.key == key }
}
