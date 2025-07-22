package io.github.noailabs.spice

import io.github.noailabs.spice.config.*

/**
 * Spice Framework Central Configuration
 * 
 * Provides centralized configuration management for all providers and features.
 * Works in both KMP and Spring Boot environments.
 */
class SpiceConfig private constructor(
    val providers: ProviderRegistry,
    val engine: EngineConfig,
    val vectorStore: VectorStoreSettings,
    val debug: DebugConfig
) {
    /**
     * Builder for SpiceConfig using DSL pattern
     */
    class Builder {
        private val providers = ProviderRegistry()
        private var engine = EngineConfig()
        private var vectorStore = VectorStoreSettings()
        private var debug = DebugConfig()
        
        /**
         * Configure providers (OpenAI, Anthropic, etc.)
         */
        fun providers(config: ProviderRegistry.() -> Unit) {
            providers.config()
        }
        
        /**
         * Configure engine settings
         */
        fun engine(config: EngineConfig.() -> Unit) {
            engine = EngineConfig().apply(config)
        }
        
        /**
         * Configure vector store settings
         */
        fun vectorStore(config: VectorStoreSettings.() -> Unit) {
            vectorStore = VectorStoreSettings().apply(config)
        }
        
        /**
         * Configure debug settings
         */
        fun debug(config: DebugConfig.() -> Unit) {
            debug = DebugConfig().apply(config)
        }
        
        fun build(): SpiceConfig {
            return SpiceConfig(
                providers = providers,
                engine = engine,
                vectorStore = vectorStore,
                debug = debug
            )
        }
    }
    
    companion object {
        @Volatile
        private var instance: SpiceConfig? = null
        
        /**
         * Get current configuration instance
         */
        fun current(): SpiceConfig {
            return instance ?: throw IllegalStateException(
                "SpiceConfig not initialized. Call SpiceConfig.initialize() first."
            )
        }
        
        /**
         * Initialize global configuration
         */
        fun initialize(config: SpiceConfig) {
            instance = config
        }
        
        /**
         * Initialize with DSL
         */
        fun initialize(init: Builder.() -> Unit) {
            val builder = Builder()
            builder.init()
            instance = builder.build()
        }
        
        /**
         * Create new configuration without setting as global
         */
        fun create(init: Builder.() -> Unit): SpiceConfig {
            val builder = Builder()
            builder.init()
            return builder.build()
        }
        
        /**
         * Reset configuration (mainly for testing)
         */
        fun reset() {
            instance = null
        }
        
        /**
         * Check if configuration is initialized
         */
        fun isInitialized(): Boolean = instance != null
    }
}

/**
 * Engine configuration
 */
class EngineConfig {
    var enabled: Boolean = true
    var maxAgents: Int = 100
    var cleanupIntervalMs: Long = 60000
    var maxMessageHistory: Int = 1000
    var enableStreaming: Boolean = true
    var enableCycleDetection: Boolean = true
    var enableHealthCheck: Boolean = true
}

/**
 * Vector store settings
 */
class VectorStoreSettings(
    var enabled: Boolean = true,
    var defaultProvider: String = "memory",
    var qdrant: QdrantConfig? = null,
    var pinecone: PineconeConfig? = null,
    var weaviate: WeaviateConfig? = null
) {
    data class QdrantConfig(
        val host: String = "localhost",
        val port: Int = 6333,
        val apiKey: String? = null,
        val useTls: Boolean = false
    )
    
    data class PineconeConfig(
        val apiKey: String,
        val environment: String,
        val projectName: String? = null,
        val indexName: String = "spice-vectors"
    )
    
    data class WeaviateConfig(
        val host: String = "localhost",
        val port: Int = 8080,
        val scheme: String = "http",
        val apiKey: String? = null
    )
}

/**
 * Debug configuration
 */
class DebugConfig {
    var enabled: Boolean = false
    var prefix: String = "[SPICE]"
    var logLevel: LogLevel = LogLevel.INFO
    var logHttpRequests: Boolean = false
    var logAgentCommunication: Boolean = false
    
    enum class LogLevel {
        DEBUG, INFO, WARN, ERROR
    }
}

/**
 * DSL function to create SpiceConfig
 */
fun spiceConfig(init: SpiceConfig.Builder.() -> Unit): SpiceConfig {
    return SpiceConfig.create(init)
}