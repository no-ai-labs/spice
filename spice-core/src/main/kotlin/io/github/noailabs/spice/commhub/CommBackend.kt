package io.github.noailabs.spice.commhub

import io.github.noailabs.spice.Comm
import io.github.noailabs.spice.CommResult
import kotlinx.coroutines.flow.Flow

/**
 * Backend interface for CommHub
 * Allows pluggable implementations (in-memory, Redis, Kafka, etc.)
 */
interface CommBackend {
    /**
     * Send a message to a specific recipient
     */
    suspend fun send(comm: Comm): CommResult
    
    /**
     * Send a message to multiple recipients
     */
    suspend fun sendBatch(comms: List<Comm>): List<CommResult>
    
    /**
     * Receive messages for a specific agent
     */
    suspend fun receive(agentId: String): Comm?
    
    /**
     * Receive with timeout
     */
    suspend fun receiveTimeout(agentId: String, timeoutMs: Long): Comm?
    
    /**
     * Subscribe to messages for an agent
     */
    fun subscribe(agentId: String): Flow<Comm>
    
    /**
     * Subscribe to messages matching a pattern
     */
    fun subscribePattern(pattern: String): Flow<Comm>
    
    /**
     * Get pending message count for an agent
     */
    suspend fun getPendingCount(agentId: String): Int
    
    /**
     * Clear all messages for an agent
     */
    suspend fun clear(agentId: String)
    
    /**
     * Store message in history
     */
    suspend fun storeHistory(comm: Comm)
    
    /**
     * Retrieve history
     */
    suspend fun getHistory(
        agentId: String? = null,
        limit: Int = 100,
        offset: Int = 0
    ): List<Comm>
    
    /**
     * Get backend health status
     */
    suspend fun health(): BackendHealth
    
    /**
     * Close the backend
     */
    suspend fun close()
}

/**
 * Backend health status
 */
data class BackendHealth(
    val healthy: Boolean,
    val latencyMs: Long? = null,
    val pendingMessages: Int = 0,
    val details: Map<String, Any> = emptyMap()
)

/**
 * Configuration for backends
 */
interface BackendConfig {
    val name: String
    val properties: Map<String, Any>
}

/**
 * Factory for creating backends
 */
interface CommBackendFactory {
    /**
     * Create a backend instance
     */
    fun create(config: BackendConfig): CommBackend
    
    /**
     * Check if this factory supports the given backend type
     */
    fun supports(type: String): Boolean
}