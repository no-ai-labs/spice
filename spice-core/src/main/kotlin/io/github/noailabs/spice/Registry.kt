package io.github.noailabs.spice

import io.github.noailabs.spice.chains.ModernToolChain
import io.github.noailabs.spice.dsl.CoreFlow
import io.github.noailabs.spice.model.AgentTool
import java.util.concurrent.ConcurrentHashMap

/**
 * 📋 Modern Registry System for Spice Framework
 * 
 * A unified, type-safe registry system using generics
 * to reduce code duplication and ensure consistency.
 */

/**
 * Base interface for all registrable items
 */
interface Identifiable {
    val id: String
}

/**
 * Generic thread-safe registry implementation
 */
open class Registry<T : Identifiable>(
    val name: String
) {
    private val items = ConcurrentHashMap<String, T>()
    
    /**
     * Register an item
     */
    open fun register(item: T): T {
        items[item.id] = item
        return item
    }
    
    /**
     * Register with override option
     */
    fun register(item: T, override: Boolean): T {
        if (override || !items.containsKey(item.id)) {
            items[item.id] = item
        }
        return item
    }
    
    /**
     * Get item by ID
     */
    fun get(id: String): T? = items[id]
    
    /**
     * Get all items
     */
    fun getAll(): List<T> = items.values.toList()
    
    /**
     * Check if item exists
     */
    fun has(id: String): Boolean = items.containsKey(id)
    
    /**
     * Remove item
     */
    fun unregister(id: String): Boolean = items.remove(id) != null
    
    /**
     * Clear all items
     */
    fun clear() = items.clear()
    
    /**
     * Get item count
     */
    fun size(): Int = items.size
    
    /**
     * Get or register item with factory
     */
    fun getOrRegister(id: String, factory: () -> T): T {
        return items.computeIfAbsent(id) { factory() }
    }
}

/**
 * Extended registry with search capabilities
 */
abstract class SearchableRegistry<T : Identifiable>(
    name: String
) : Registry<T>(name) {
    
    /**
     * Find items by predicate
     */
    fun findBy(predicate: (T) -> Boolean): List<T> {
        return getAll().filter(predicate)
    }
    
    /**
     * Find first item matching predicate
     */
    fun findFirstBy(predicate: (T) -> Boolean): T? {
        return getAll().firstOrNull(predicate)
    }
}

/**
 * 🤖 Agent Registry with advanced search
 */
object AgentRegistry : SearchableRegistry<Agent>("agents") {
    
    /**
     * Find agents by capability
     */
    fun findByCapability(capability: String): List<Agent> {
        return findBy { agent ->
            agent.capabilities.contains(capability)
        }
    }
    
    /**
     * Find agents by tag
     * Note: This requires Agent implementation to have tags property
     */
    fun findByTag(tag: String): List<Agent> {
        return findBy { agent ->
            // Check if agent has tags property (for now, return empty)
            // TODO: Add tags support to Agent interface
            false
        }
    }
    
    /**
     * Find agents by provider  
     * Note: This requires Agent implementation to have provider property
     */
    fun findByProvider(provider: String): List<Agent> {
        return findBy { agent ->
            // Check if agent has provider property (for now, return empty)
            // TODO: Add provider support to Agent interface
            false
        }
    }
}

/**
 * Tool wrapper to make Tool implement Identifiable
 */
open class ToolWrapper(
    override val id: String,
    open val tool: Tool
) : Tool by tool, Identifiable

/**
 * Extended tool wrapper with metadata support
 */
data class ToolWrapperEx(
    override val id: String,
    override val tool: Tool,
    val source: String = "direct",
    val tags: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
) : ToolWrapper(id, tool)

/**
 * 🔧 Tool Registry with namespace support
 */
object ToolRegistry : Registry<ToolWrapper>("tools") {
    private val namespaceIndex = ConcurrentHashMap<String, MutableSet<String>>()
    
    /**
     * Register tool with namespace
     */
    fun register(tool: Tool, namespace: String = "global"): Tool {
        val qualifiedId = if (namespace == "global") {
            tool.name
        } else {
            "$namespace::${tool.name}"
        }
        
        // Create a wrapper tool with qualified ID
        // Use extended wrapper for consistency
        val wrapper = ToolWrapperEx(
            id = qualifiedId, 
            tool = tool,
            source = "direct"
        )
        super.register(wrapper)
        
        // Update namespace index
        namespaceIndex.computeIfAbsent(namespace) { ConcurrentHashMap.newKeySet() }
            .add(tool.name)
        
        return tool
    }
    
    /**
     * Get tool by name with namespace support
     */
    fun getTool(name: String, namespace: String = "global"): Tool? {
        val qualifiedName = if (namespace == "global") name else "$namespace::$name"
        val wrapper = get(qualifiedName) ?: get(name) // fallback to global
        return wrapper?.tool
    }
    
    /**
     * Get all tools in namespace
     */
    fun getByNamespace(namespace: String): List<Tool> {
        val toolNames = namespaceIndex[namespace] ?: return emptyList()
        return toolNames.mapNotNull { getTool(it, namespace) }
    }
    
    /**
     * Check if tool exists
     */
    fun hasTool(name: String, namespace: String = "global"): Boolean {
        val qualifiedName = if (namespace == "global") name else "$namespace::$name"
        return has(qualifiedName) || has(name)
    }
    
    /**
     * Ensure tool is registered (for globalTools DSL)
     */
    fun ensureRegistered(name: String): Boolean = hasTool(name)
    
    /**
     * Register AgentTool with metadata preservation
     */
    fun register(agentTool: AgentTool, namespace: String = "global"): Tool {
        val tool = agentTool.toTool()
        val qualifiedId = if (namespace == "global") {
            agentTool.name
        } else {
            "$namespace::${agentTool.name}"
        }
        
        // Create extended wrapper with metadata
        val wrapper = ToolWrapperEx(
            id = qualifiedId,
            tool = tool,
            source = "agent-tool",
            tags = agentTool.tags,
            metadata = agentTool.metadata + mapOf(
                "implementationType" to agentTool.implementationType
            )
        )
        
        super.register(wrapper)
        
        // Update namespace index
        namespaceIndex.computeIfAbsent(namespace) { ConcurrentHashMap.newKeySet() }
            .add(agentTool.name)
        
        return tool
    }
    
    /**
     * Get tools by tag
     */
    fun getByTag(tag: String): List<Tool> {
        return getAll()
            .filterIsInstance<ToolWrapperEx>()
            .filter { wrapper -> tag in wrapper.tags }
            .map { it.tool }
    }
    
    /**
     * Get tools by source type
     */
    fun getBySource(source: String): List<Tool> {
        return getAll()
            .filterIsInstance<ToolWrapperEx>()
            .filter { wrapper -> wrapper.source == source }
            .map { it.tool }
    }
    
    /**
     * Get all AgentTool-sourced tools with metadata
     */
    fun getAgentTools(): List<Pair<Tool, Map<String, Any>>> {
        return getAll()
            .filterIsInstance<ToolWrapperEx>()
            .filter { wrapper -> wrapper.source == "agent-tool" }
            .map { wrapper ->
                wrapper.tool to mapOf(
                    "tags" to wrapper.tags,
                    "metadata" to wrapper.metadata
                )
            }
    }
}

/**
 * 🔗 ToolChain Registry
 */
object ToolChainRegistry : Registry<ModernToolChain>("toolchains")

/**
 * 🌊 Flow Registry
 */
object FlowRegistry : Registry<CoreFlow>("flows")


