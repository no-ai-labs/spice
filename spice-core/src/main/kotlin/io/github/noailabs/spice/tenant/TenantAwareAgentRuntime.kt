package io.github.noailabs.spice.tenant

import io.github.noailabs.spice.*
import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

/**
 * 🏢 Tenant-Aware Agent Runtime
 * 
 * Enhanced runtime that provides tenant isolation and context propagation
 */
class TenantAwareAgentRuntime(
    context: AgentContext,
    orchestrator: AgentOrchestrator? = null,
    private val tenantId: String? = null
) : DefaultAgentRuntime(context, orchestrator) {
    
    init {
        // Set tenant context if provided
        tenantId?.let { 
            TenantContext.set(it, context.toMap())
        }
    }
    
    override suspend fun callAgent(agentId: String, comm: Comm): SpiceResult<Comm> {
        // Ensure tenant context is propagated
        val currentTenant = TenantContext.current()
        return if (currentTenant != null) {
            withContext(scope.coroutineContext.withTenant(currentTenant)) {
                super.callAgent(agentId, comm)
            }
        } else {
            super.callAgent(agentId, comm)
        }
    }
    
    override suspend fun publishEvent(event: AgentEvent) {
        // Add tenant information to event
        val enrichedEvent = if (TenantContext.current() != null) {
            event.copy(
                data = event.data + ("tenantId" to TenantContext.currentTenantId()!!)
            )
        } else {
            event
        }
        super.publishEvent(enrichedEvent)
    }
    
    override suspend fun saveState(key: String, value: Any) {
        // Use tenant-scoped key
        val scopedKey = if (TenantContext.current() != null) {
            TenantIsolation.scopedKey(key)
        } else {
            key
        }
        super.saveState(scopedKey, value)
    }
    
    override suspend fun getState(key: String): Any? {
        // Use tenant-scoped key
        val scopedKey = if (TenantContext.current() != null) {
            TenantIsolation.scopedKey(key)
        } else {
            key
        }
        return super.getState(scopedKey)
    }
    
    override fun log(level: LogLevel, message: String, data: Map<String, Any>) {
        // Add tenant information to log data
        val enrichedData = if (TenantContext.current() != null) {
            data + ("tenantId" to TenantContext.currentTenantId()!!)
        } else {
            data
        }
        super.log(level, message, enrichedData)
    }
}

/**
 * 🔧 Tenant-Aware Agent Base
 * 
 * Base class for agents that need tenant awareness
 */
abstract class TenantAwareAgent(
    id: String,
    name: String,
    description: String,
    capabilities: List<String> = emptyList(),
    config: AgentConfig = AgentConfig()
) : BaseAgent(id, name, description, capabilities, config) {
    
    /**
     * Get current tenant ID
     */
    protected fun currentTenantId(): String? = TenantContext.currentTenantId()
    
    /**
     * Require current tenant ID (throws if not set)
     */
    protected fun requireTenantId(): String = TenantContext.require().tenantId
    
    /**
     * Execute with tenant validation
     */
    protected suspend fun <T> withTenantValidation(
        resourceTenantId: String,
        block: suspend () -> T
    ): T {
        TenantIsolation.validateAccess(resourceTenantId)
        return block()
    }
    
    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        // Extract tenant from comm if available
        val tenantId = comm.data["tenantId"]?.toString()
        return if (tenantId != null && TenantContext.current() == null) {
            // Set tenant context before processing
            val previous = TenantContext.current()
            try {
                TenantContext.set(tenantId)
                processCommInternal(comm)
            } finally {
                if (previous != null) {
                    TenantContext.set(previous)
                } else {
                    TenantContext.clear()
                }
            }
        } else {
            processCommInternal(comm)
        }
    }

    /**
     * Override this method instead of processComm
     */
    protected abstract suspend fun processCommInternal(comm: Comm): SpiceResult<Comm>
}

/**
 * 🏪 Tenant Store Interface
 * 
 * Storage abstraction with tenant isolation
 */
interface TenantStore<T> {
    suspend fun save(key: String, value: T, tenantId: String? = null)
    suspend fun get(key: String, tenantId: String? = null): T?
    suspend fun delete(key: String, tenantId: String? = null)
    suspend fun list(tenantId: String? = null): List<Pair<String, T>>
}

/**
 * 📦 In-Memory Tenant Store
 * 
 * Simple implementation for testing and development
 */
class InMemoryTenantStore<T> : TenantStore<T> {
    private val store = mutableMapOf<String, T>()
    
    private fun scopedKey(key: String, tenantId: String?): String {
        val effectiveTenantId = tenantId ?: TenantContext.currentTenantId()
        return if (effectiveTenantId != null) {
            "$effectiveTenantId:$key"
        } else {
            key
        }
    }
    
    override suspend fun save(key: String, value: T, tenantId: String?) {
        store[scopedKey(key, tenantId)] = value
    }
    
    override suspend fun get(key: String, tenantId: String?): T? {
        return store[scopedKey(key, tenantId)]
    }
    
    override suspend fun delete(key: String, tenantId: String?) {
        store.remove(scopedKey(key, tenantId))
    }
    
    override suspend fun list(tenantId: String?): List<Pair<String, T>> {
        val effectiveTenantId = tenantId ?: TenantContext.currentTenantId()
        val prefix = if (effectiveTenantId != null) "$effectiveTenantId:" else ""
        
        return store.entries
            .filter { it.key.startsWith(prefix) }
            .map { it.key.removePrefix(prefix) to it.value }
    }
}