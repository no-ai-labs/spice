package io.github.noailabs.spice.tenant

import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.CoroutineContext

/**
 * 🏢 Tenant Context for Multi-Tenant Support
 * 
 * Provides thread-safe tenant isolation across the application.
 * Works seamlessly with coroutines using ThreadContextElement.
 */
class TenantContext(
    val tenantId: String,
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * Get metadata value with type safety
     */
    fun <T> getAs(key: String): T? = metadata[key] as? T
    
    /**
     * Check if metadata contains key
     */
    fun has(key: String): Boolean = metadata.containsKey(key)
    
    /**
     * Create new context with additional metadata
     */
    fun with(key: String, value: Any): TenantContext {
        return TenantContext(tenantId, metadata + (key to value))
    }
    
    /**
     * Create new context with multiple metadata entries
     */
    fun withAll(vararg pairs: Pair<String, Any>): TenantContext {
        return TenantContext(tenantId, metadata + pairs.toMap())
    }
    
    override fun toString(): String = "TenantContext(tenantId=$tenantId, metadata=$metadata)"
    
    companion object {
        private val threadLocal = ThreadLocal<TenantContext>()
        
        /**
         * Get current tenant context
         */
        fun current(): TenantContext? = threadLocal.get()
        
        /**
         * Get current tenant ID
         */
        fun currentTenantId(): String? = current()?.tenantId
        
        /**
         * Require current tenant context (throws if not set)
         */
        fun require(): TenantContext = current()
            ?: throw IllegalStateException("No tenant context available")
        
        /**
         * Set tenant context for current thread
         */
        fun set(context: TenantContext) {
            threadLocal.set(context)
        }
        
        /**
         * Set tenant context by ID
         */
        fun set(tenantId: String, metadata: Map<String, Any> = emptyMap()) {
            set(TenantContext(tenantId, metadata))
        }
        
        /**
         * Clear tenant context from current thread
         */
        fun clear() {
            threadLocal.remove()
        }
        
        /**
         * Execute block with tenant context
         */
        inline fun <T> withTenant(tenantId: String, block: () -> T): T {
            return withTenant(TenantContext(tenantId), block)
        }
        
        /**
         * Execute block with tenant context
         */
        inline fun <T> withTenant(context: TenantContext, block: () -> T): T {
            val previous = current()
            return try {
                set(context)
                block()
            } finally {
                if (previous != null) {
                    set(previous)
                } else {
                    clear()
                }
            }
        }
        
        /**
         * Create tenant context
         */
        fun of(tenantId: String, vararg metadata: Pair<String, Any>): TenantContext {
            return TenantContext(tenantId, metadata.toMap())
        }
    }
}

/**
 * 🔄 Coroutine Context Element for Tenant Propagation
 * 
 * Ensures tenant context is properly propagated across coroutine boundaries.
 */
class TenantContextElement(
    private val context: TenantContext
) : ThreadContextElement<TenantContext?> {
    
    companion object Key : CoroutineContext.Key<TenantContextElement>
    
    override val key: CoroutineContext.Key<*> = Key
    
    override fun updateThreadContext(context: CoroutineContext): TenantContext? {
        val oldState = TenantContext.current()
        TenantContext.set(this.context)
        return oldState
    }
    
    override fun restoreThreadContext(context: CoroutineContext, oldState: TenantContext?) {
        if (oldState != null) {
            TenantContext.set(oldState)
        } else {
            TenantContext.clear()
        }
    }
}

/**
 * Extension function to add tenant context to coroutine context
 */
fun CoroutineContext.withTenant(tenantId: String): CoroutineContext {
    return this + TenantContextElement(TenantContext.of(tenantId))
}

/**
 * Extension function to add tenant context to coroutine context
 */
fun CoroutineContext.withTenant(context: TenantContext): CoroutineContext {
    return this + TenantContextElement(context)
}

/**
 * 🛡️ Tenant Isolation Helper
 * 
 * Utilities for ensuring tenant data isolation
 */
object TenantIsolation {
    /**
     * Validate that operation is allowed for current tenant
     */
    fun validateAccess(resourceTenantId: String) {
        val currentTenantId = TenantContext.currentTenantId()
            ?: throw SecurityException("No tenant context for access validation")
        
        if (currentTenantId != resourceTenantId) {
            throw SecurityException(
                "Tenant $currentTenantId cannot access resource of tenant $resourceTenantId"
            )
        }
    }
    
    /**
     * Filter collection by current tenant
     */
    fun <T> filterByTenant(
        items: Collection<T>,
        tenantIdExtractor: (T) -> String
    ): List<T> {
        val currentTenantId = TenantContext.currentTenantId() ?: return emptyList()
        return items.filter { tenantIdExtractor(it) == currentTenantId }
    }
    
    /**
     * Create tenant-scoped key
     */
    fun scopedKey(key: String): String {
        val tenantId = TenantContext.currentTenantId()
            ?: throw IllegalStateException("No tenant context for scoped key")
        return "$tenantId:$key"
    }
}