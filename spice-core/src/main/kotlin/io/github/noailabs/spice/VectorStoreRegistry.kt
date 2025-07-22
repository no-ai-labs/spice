package io.github.noailabs.spice

import java.util.concurrent.ConcurrentHashMap

/**
 * 🗂️ VectorStore Registry
 * 
 * Centralized registry for managing VectorStore instances
 * Allows agents to access vector stores by name
 */
object VectorStoreRegistry {
    
    private val stores = ConcurrentHashMap<String, VectorStoreEntry>()
    
    /**
     * VectorStore entry with metadata
     */
    data class VectorStoreEntry(
        val store: VectorStore,
        val config: VectorStoreConfig,
        val createdAt: Long = System.currentTimeMillis(),
        val agentId: String? = null
    )
    
    /**
     * Register a vector store
     */
    fun register(name: String, store: VectorStore, config: VectorStoreConfig, agentId: String? = null): Boolean {
        val entry = VectorStoreEntry(store, config, agentId = agentId)
        stores[name] = entry
        return true
    }
    
    /**
     * Get a vector store by name
     */
    fun get(name: String): VectorStore? {
        return stores[name]?.store
    }
    
    /**
     * Get vector store with config
     */
    fun getWithConfig(name: String): Pair<VectorStore, VectorStoreConfig>? {
        val entry = stores[name] ?: return null
        return entry.store to entry.config
    }
    
    /**
     * Check if a vector store exists
     */
    fun exists(name: String): Boolean {
        return stores.containsKey(name)
    }
    
    /**
     * Remove a vector store
     */
    fun unregister(name: String): Boolean {
        return stores.remove(name) != null
    }
    
    /**
     * List all registered vector stores
     */
    fun list(): List<String> {
        return stores.keys.toList()
    }
    
    /**
     * List vector stores for a specific agent
     */
    fun listByAgent(agentId: String): List<String> {
        return stores.entries
            .filter { it.value.agentId == agentId }
            .map { it.key }
    }
    
    /**
     * Clear all vector stores
     */
    fun clear() {
        stores.clear()
    }
    
    /**
     * Get or create a vector store
     */
    fun getOrCreate(name: String, config: VectorStoreConfig, agentId: String? = null): VectorStore {
        return get(name) ?: run {
            val store = createVectorStoreInstance(config)
            register(name, store, config, agentId)
            store
        }
    }
    
    /**
     * Create vector store instance from config
     */
    private fun createVectorStoreInstance(config: VectorStoreConfig): VectorStore {
        return when (config.provider.lowercase()) {
            "qdrant" -> QdrantVectorStore(
                host = config.host,
                port = config.port,
                apiKey = config.apiKey
            )
            // Add other providers as needed
            else -> throw IllegalArgumentException("Unsupported vector store provider: ${config.provider}")
        }
    }
}

/**
 * VectorStore configuration (moved from CoreDSL to be shared)
 */
data class VectorStoreConfig(
    val provider: String,
    val host: String = "localhost",
    val port: Int = 6333,
    val apiKey: String? = null,
    val collection: String = "default",
    val vectorSize: Int = 384,
    val config: Map<String, String> = emptyMap()
)