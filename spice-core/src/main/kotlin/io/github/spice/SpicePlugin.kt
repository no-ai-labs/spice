package io.github.spice

import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap
import java.util.ServiceLoader

/**
 * Spice Plugin System
 * 
 * Provides an extensible modular architecture.
 * Allows extending Spice functionality through plugins.
 */

/**
 * Plugin metadata
 */
@Serializable
data class PluginMetadata(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val dependencies: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
    val category: PluginCategory = PluginCategory.GENERAL,
    val spiceVersion: String = "1.0.0"
)

/**
 * Plugin category
 */
enum class PluginCategory {
    AGENT,          // Agent extensions
    TOOL,           // Tool extensions
    MESSAGE_ROUTER, // MessageRouter extensions
    ORCHESTRATOR,   // Orchestrator extensions
    GENERAL,        // General extensions
    INTEGRATION,    // External service integration
    CUSTOM          // Custom extensions
}

/**
 * Plugin status
 */
enum class PluginStatus {
    ACTIVATED,      // Activated
    DEACTIVATED,    // Deactivated
    ERROR,          // Error state
    UPDATING        // Updating
}

/**
 * Plugin interface
 */
interface SpicePlugin {
    
    /**
     * Plugin metadata
     */
    val metadata: PluginMetadata
    
    /**
     * Plugin context
     */
    var context: PluginContext?
    
    /**
     * Plugin activation
     */
    suspend fun activate()
    
    /**
     * Plugin deactivation
     */
    suspend fun deactivate()
    
    /**
     * Plugin cleanup
     */
    suspend fun cleanup()
    
    /**
     * Plugin status check
     */
    fun isReady(): Boolean
    
    /**
     * Plugin configuration
     */
    fun configure(config: Map<String, Any>)
}

/**
 * Plugin context
 */
interface PluginContext {
    val pluginId: String
    val logger: PluginLogger
    val configManager: ConfigManager
    
    /**
     * Plugin configuration
     */
    fun getConfig(): Map<String, Any>
    fun setConfig(key: String, value: Any)
    
    /**
     * Access other plugins
     */
    fun getPlugin(id: String): SpicePlugin?
    fun getAllPlugins(): List<SpicePlugin>
}

/**
 * Simple plugin logger implementation
 */
interface PluginLogger {
    fun info(message: String)
    fun warn(message: String)
    fun error(message: String, throwable: Throwable? = null)
    fun debug(message: String)
}

/**
 * Simple plugin logger implementation
 */
class SimplePluginLogger(private val pluginId: String) : PluginLogger {
    override fun info(message: String) = println("🌶️ [INFO] [$pluginId] $message")
    override fun warn(message: String) = println("🌶️ [WARN] [$pluginId] $message")
    override fun error(message: String, exception: Throwable?) = println("🌶️ [ERROR] [$pluginId] $message ${exception?.message ?: ""}")
    override fun debug(message: String) = println("🌶️ [DEBUG] [$pluginId] $message")
}

/**
 * Configuration manager
 */
interface ConfigManager {
    fun getString(key: String, defaultValue: String = ""): String
    fun getInt(key: String, defaultValue: Int = 0): Int
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean
    fun setProperty(key: String, value: Any)
}

/**
 * Simple configuration manager implementation
 */
class SimpleConfigManager : ConfigManager {
    private val properties = ConcurrentHashMap<String, Any>()
    
    override fun getString(key: String, defaultValue: String): String {
        return properties[key]?.toString() ?: defaultValue
    }
    
    override fun getInt(key: String, defaultValue: Int): Int {
        return properties[key]?.toString()?.toIntOrNull() ?: defaultValue
    }
    
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return properties[key]?.toString()?.toBooleanStrictOrNull() ?: defaultValue
    }
    
    override fun setProperty(key: String, value: Any) {
        properties[key] = value
    }
}

/**
 * Plugin information
 */
data class PluginInfo(
    val metadata: PluginMetadata,
    val status: PluginStatus,
    val loadTime: Long,
    val activationTime: Long? = null,
    val errorMessage: String? = null
)

/**
 * Plugin manager
 */
class PluginManager(
    private val agentEngine: AgentEngine,
    private val configManager: ConfigManager = SimpleConfigManager()
) {
    
    private val plugins = ConcurrentHashMap<String, SpicePlugin>()
    private val pluginStates = ConcurrentHashMap<String, PluginStatus>()
    private val pluginInfos = ConcurrentHashMap<String, PluginInfo>()
    private val dependencyGraph = ConcurrentHashMap<String, List<String>>()
    
    /**
     * Register plugin
     */
    fun registerPlugin(plugin: SpicePlugin): Boolean {
        val pluginId = plugin.metadata.id
        
        if (plugins.containsKey(pluginId)) {
            return false // Plugin already registered
        }
        
        // Dependency check
        val missingDeps = plugin.metadata.dependencies.filter { dep ->
            !plugins.containsKey(dep)
        }
        
        if (missingDeps.isNotEmpty()) {
            return false // Missing dependencies
        }
        
        // Store plugin information
        plugins[pluginId] = plugin
        pluginStates[pluginId] = PluginStatus.DEACTIVATED
        pluginInfos[pluginId] = PluginInfo(
            metadata = plugin.metadata,
            status = PluginStatus.DEACTIVATED,
            loadTime = System.currentTimeMillis()
        )
        
        // Update dependency graph
        dependencyGraph[pluginId] = plugin.metadata.dependencies
        
        // Create and initialize plugin context
        val context = createPluginContext(pluginId)
        plugin.context = context
        
        return true
    }
    
    private fun createPluginContext(pluginId: String): PluginContext {
        return object : PluginContext {
            override val pluginId: String = pluginId
            override val logger: PluginLogger = SimplePluginLogger(pluginId)
            override val configManager: ConfigManager = SimpleConfigManager()
            
            private val config = ConcurrentHashMap<String, Any>()
            
            override fun getConfig(): Map<String, Any> = config.toMap()
            
            override fun setConfig(key: String, value: Any) {
                config[key] = value
            }
            
            override fun getPlugin(id: String): SpicePlugin? {
                return plugins[id]
            }
            
            override fun getAllPlugins(): List<SpicePlugin> {
                return plugins.values.toList()
            }
        }
    }
    
    /**
     * Activate plugin
     */
    suspend fun activatePlugin(pluginId: String): Boolean {
        val plugin = plugins[pluginId] ?: return false
        
        // Check if dependency plugins are activated
        val dependencies = dependencyGraph[pluginId] ?: emptyList()
        val inactiveDeps = dependencies.filter { dep ->
            pluginStates[dep] != PluginStatus.ACTIVATED
        }
        
        if (inactiveDeps.isNotEmpty()) {
            return false // Dependencies not activated
        }
        
        return try {
            plugin.activate()
            
            // Update plugin information
            pluginInfos[pluginId]?.let { info ->
                pluginInfos[pluginId] = info.copy(
                    status = PluginStatus.ACTIVATED,
                    activationTime = System.currentTimeMillis()
                )
            }
            
            true
        } catch (e: Exception) {
            pluginInfos[pluginId] = pluginInfos[pluginId]?.copy(
                status = PluginStatus.ERROR,
                errorMessage = e.message
            )
            false
        }
    }
    
    /**
     * Deactivate plugin
     */
    suspend fun deactivatePlugin(pluginId: String): Boolean {
        val plugin = plugins[pluginId] ?: return false
        
        return try {
            plugin.deactivate()
            
            // Update plugin information
            pluginInfos[pluginId] = pluginInfos[pluginId]?.copy(
                status = PluginStatus.DEACTIVATED,
                activationTime = null
            )
            
            true
        } catch (e: Exception) {
            pluginInfos[pluginId] = pluginInfos[pluginId]?.copy(
                status = PluginStatus.ERROR,
                errorMessage = e.message
            )
            false
        }
    }
    
    /**
     * Unregister plugin
     */
    suspend fun unregisterPlugin(pluginId: String): Boolean {
        val plugin = plugins[pluginId] ?: return false
        
        return try {
            // Deactivate first
            if (pluginStates[pluginId] == PluginStatus.ACTIVATED) {
                deactivatePlugin(pluginId)
            }
            
            // Cleanup
            plugin.cleanup()
            
            // Remove from registry
            plugins.remove(pluginId)
            pluginStates.remove(pluginId)
            pluginInfos.remove(pluginId)
            dependencyGraph.remove(pluginId)
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Query plugins
     */
    fun getPlugin(pluginId: String): SpicePlugin? {
        return plugins[pluginId]
    }
    
    /**
     * Query all plugins
     */
    fun getAllPlugins(): Map<String, SpicePlugin> {
        return plugins.toMap()
    }
    
    /**
     * Query plugin information
     */
    fun getPluginInfo(pluginId: String): PluginInfo? {
        return pluginInfos[pluginId]
    }
    
    /**
     * Query all plugin information
     */
    fun getAllPluginInfos(): Map<String, PluginInfo> {
        return pluginInfos.toMap()
    }
    
    /**
     * Query plugins by category
     */
    fun getPluginsByCategory(category: PluginCategory): List<SpicePlugin> {
        return plugins.values.filter { it.metadata.category == category }
    }
    
    /**
     * Dependency check
     */
    private fun checkDependencies(dependencies: List<String>): Boolean {
        return dependencies.all { depId ->
            plugins.containsKey(depId)
        }
    }
    
    /**
     * Sort plugins by dependency order
     */
    fun getPluginActivationOrder(): List<String> {
        val result = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()
        
        fun visit(pluginId: String) {
            if (pluginId in visiting) {
                throw IllegalStateException("Circular dependency detected: $pluginId")
            }
            
            if (pluginId in visited) return
            
            visiting.add(pluginId)
            
            val dependencies = dependencyGraph[pluginId] ?: emptyList()
            dependencies.forEach { dep ->
                if (plugins.containsKey(dep)) {
                    visit(dep)
                }
            }
            
            visiting.remove(pluginId)
            visited.add(pluginId)
            result.add(pluginId)
        }
        
        plugins.keys.forEach { pluginId ->
            if (pluginId !in visited) {
                visit(pluginId)
            }
        }
        
        return result
    }
    
    /**
     * Activate all plugins (in dependency order)
     */
    suspend fun activateAllPlugins(): List<String> {
        val activationOrder = getPluginActivationOrder()
        val failed = mutableListOf<String>()
        
        activationOrder.forEach { pluginId ->
            if (pluginStates[pluginId] != PluginStatus.ACTIVATED) {
                if (!activatePlugin(pluginId)) {
                    failed.add(pluginId)
                }
            }
        }
        
        return failed
    }
    
    /**
     * Deactivate all plugins (in reverse order)
     */
    suspend fun deactivateAllPlugins(): List<String> {
        val deactivationOrder = getPluginActivationOrder().reversed()
        val failed = mutableListOf<String>()
        
        deactivationOrder.forEach { pluginId ->
            if (pluginStates[pluginId] == PluginStatus.ACTIVATED) {
                if (!deactivatePlugin(pluginId)) {
                    failed.add(pluginId)
                }
            }
        }
        
        return failed
    }
    
    /**
     * Plugin statistics
     */
    fun getPluginStats(): PluginStats {
        val byState = pluginStates.values.groupingBy { it }.eachCount()
        val byCategory = plugins.values.groupingBy { it.metadata.category }.eachCount()
        
        return PluginStats(
            totalPlugins = plugins.size,
            stateDistribution = byState,
            categoryDistribution = byCategory,
            averageLoadTime = pluginInfos.values.map { it.loadTime }.average().takeIf { !it.isNaN() } ?: 0.0
        )
    }
}

/**
 * Plugin statistics
 */
data class PluginStats(
    val totalPlugins: Int,
    val stateDistribution: Map<PluginStatus, Int>,
    val categoryDistribution: Map<PluginCategory, Int>,
    val averageLoadTime: Double
)

/**
 * Plugin exception
 */
class PluginException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * === Actual plugin examples ===
 */

/**
 * Base plugin class
 * 
 * Methods to implement in subclasses
 */
abstract class BaseSpicePlugin(
    override val metadata: PluginMetadata
) : SpicePlugin {
    
    override var context: PluginContext? = null
    
    override suspend fun activate() {
        // Default implementation
        context?.logger?.info("Plugin ${metadata.name} activated")
    }
    
    override suspend fun deactivate() {
        // Default implementation
        context?.logger?.info("Plugin ${metadata.name} deactivated")
    }
    
    override suspend fun cleanup() {
        // Default implementation
        context?.logger?.info("Plugin ${metadata.name} cleaned up")
    }
    
    override fun isReady(): Boolean = context != null
    
    override fun configure(config: Map<String, Any>) {
        // Default implementation
    }
    
    /**
     * Methods to implement in subclasses
     */
    protected open fun onInitialize() {}
    protected open fun onActivate() {}
    protected open fun onDeactivate() {}
    protected open fun onCleanup() {}
    protected open fun onConfigure(config: Map<String, Any>) {}
}

/**
 * Webhook notification plugin example
 */
class WebhookNotificationPlugin : BaseSpicePlugin(
    PluginMetadata(
        id = "webhook-notification",
        name = "Webhook Notification",
        version = "1.0.0",
        description = "Sends notifications via webhook",
        author = "Spice Team",
        category = PluginCategory.INTEGRATION,
        permissions = listOf("network.http")
    )
) {
    
    override suspend fun activate() {
        super.activate()
        
        // Get webhook URL from configuration
        val webhookUrl = context?.configManager?.getString("webhook.url", "")
        
        if (webhookUrl.isNullOrEmpty()) {
            context?.logger?.warn("Webhook URL not configured")
            return
        }
        
        context?.logger?.info("Webhook notification activated with URL: $webhookUrl")
        
        // Register event listeners
        // (Implementation would require AgentEngine to have event system)
    }
    
    override suspend fun deactivate() {
        super.deactivate()
        
        // Unregister event listeners
        context?.logger?.info("Webhook notification deactivated")
    }
    
    /**
     * Send webhook
     */
    private suspend fun sendWebhook(event: String, data: Map<String, Any>) {
        val webhookUrl = context?.configManager?.getString("webhook.url", "") ?: return
        
        // Implement actual HTTP request
        // (This example just logs)
        context?.logger?.info("Sending webhook: $event with data: $data")
    }
}

/**
 * Logging plugin example
 */
class FileLoggingPlugin : BaseSpicePlugin(
    PluginMetadata(
        id = "file-logging",
        name = "File Logging",
        version = "1.0.0",
        description = "Logs Agent activities to file",
        author = "Spice Team",
        category = PluginCategory.GENERAL,
        permissions = listOf("filesystem.write")
    )
) {
    
    override suspend fun activate() {
        super.activate()
        
        val logPath = context?.configManager?.getString("log.path", "spice.log")
        context?.logger?.info("File logging activated with path: $logPath")
        
        // Implementation would integrate with AgentEngine's logging system
        // Capture all Agent execution logs and save to file
    }
    
    override suspend fun deactivate() {
        super.deactivate()
        
        // Close file handles
        context?.logger?.info("File logging deactivated")
    }
    
    /**
     * Write to file (implementation needed)
     */
    private fun writeToFile(message: String) {
        // Implement file writing
        // (This example just logs)
    }
}

/**
 * Convenience functions
 */
fun createPluginManager(agentEngine: AgentEngine): PluginManager {
    return PluginManager(agentEngine)
}

/**
 * Plugin builder DSL
 */
fun buildPlugin(id: String, name: String, init: PluginBuilder.() -> Unit): SpicePlugin {
    val builder = PluginBuilder(id, name)
    builder.init()
    return builder.build()
}

/**
 * Plugin builder
 */
class PluginBuilder(private val id: String, private val name: String) {
    var version: String = "1.0.0"
    var description: String = ""
    var author: String = ""
    var category: PluginCategory = PluginCategory.GENERAL
    var dependencies: List<String> = emptyList()
    var permissions: List<String> = emptyList()
    
    private var initHandler: (PluginContext) -> Unit = {}
    private var activateHandler: () -> Unit = {}
    private var deactivateHandler: () -> Unit = {}
    private var cleanupHandler: () -> Unit = {}
    private var configureHandler: (Map<String, Any>) -> Unit = {}
    
    fun onInitialize(handler: (PluginContext) -> Unit) {
        initHandler = handler
    }
    
    fun onActivate(handler: () -> Unit) {
        activateHandler = handler
    }
    
    fun onDeactivate(handler: () -> Unit) {
        deactivateHandler = handler
    }
    
    fun onCleanup(handler: () -> Unit) {
        cleanupHandler = handler
    }
    
    fun onConfigure(handler: (Map<String, Any>) -> Unit) {
        configureHandler = handler
    }
    
    internal fun build(): SpicePlugin {
        val metadata = PluginMetadata(
            id = id,
            name = name,
            version = version,
            description = description,
            author = author,
            dependencies = dependencies,
            permissions = permissions,
            category = category
        )
        
        return object : BaseSpicePlugin(metadata) {
            override fun onInitialize() = initHandler(context!!)
            override fun onActivate() = activateHandler()
            override fun onDeactivate() = deactivateHandler()
            override fun onCleanup() = cleanupHandler()
            override fun onConfigure(config: Map<String, Any>) = configureHandler(config)
        }
    }
} 