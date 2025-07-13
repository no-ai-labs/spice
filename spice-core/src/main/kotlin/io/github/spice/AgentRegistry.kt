package io.github.spice

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 🌶️ Agent Registry Interface
 * Central registry for dynamic agent management and discovery
 */
interface AgentRegistry {
    fun register(agent: Agent)
    fun register(agent: Agent, override: Boolean = false)
    fun get(id: String): Agent?
    fun getAll(): List<Agent>
    fun findByCapability(capability: String): List<Agent>
    fun findByTag(tag: String): List<Agent>
    fun findByProvider(provider: String): List<Agent>
    fun unregister(id: String): Boolean
    fun getOrRegister(id: String, factory: () -> Agent): Agent
    fun freeze(): AgentRegistry
    fun toJSON(): String
    fun getDescriptor(id: String): AgentDescriptor?
    fun getAllDescriptors(): List<AgentDescriptor>
}

/**
 * 🧠 Agent Descriptor
 * Metadata information about registered agents
 */
data class AgentDescriptor(
    val id: String,
    val name: String,
    val description: String,
    val capabilities: List<String>,
    val tags: List<String>,
    val provider: String,
    val createdAt: Instant,
    val isReady: Boolean
)

/**
 * 🔧 In-Memory Agent Registry Implementation
 * Thread-safe registry with advanced querying capabilities
 */
class InMemoryAgentRegistry : AgentRegistry {
    
    private val agents = ConcurrentHashMap<String, Agent>()
    private val descriptors = ConcurrentHashMap<String, AgentDescriptor>()
    private var frozen = false
    
    override fun register(agent: Agent) {
        register(agent, override = false)
    }
    
    override fun register(agent: Agent, override: Boolean) {
        if (frozen) {
            throw IllegalStateException("Registry is frozen. Cannot register new agents.")
        }
        
        if (!override && agents.containsKey(agent.id)) {
            throw IllegalArgumentException("Agent with id '${agent.id}' already exists. Use override=true to replace.")
        }
        
        agents[agent.id] = agent
        descriptors[agent.id] = createDescriptor(agent)
        
        println("🌶️ Agent registered: ${agent.id} (${agent.name})")
    }
    
    override fun get(id: String): Agent? = agents[id]
    
    override fun getAll(): List<Agent> = agents.values.toList()
    
    override fun findByCapability(capability: String): List<Agent> =
        agents.values.filter { it.capabilities.contains(capability) }
    
    override fun findByTag(tag: String): List<Agent> =
        agents.values.filter { agent ->
            val descriptor = descriptors[agent.id]
            descriptor?.tags?.contains(tag) == true
        }
    
    override fun findByProvider(provider: String): List<Agent> =
        agents.values.filter { agent ->
            val descriptor = descriptors[agent.id]
            descriptor?.provider == provider
        }
    
    override fun unregister(id: String): Boolean {
        if (frozen) {
            throw IllegalStateException("Registry is frozen. Cannot unregister agents.")
        }
        
        val removed = agents.remove(id) != null
        descriptors.remove(id)
        
        if (removed) {
            println("🌶️ Agent unregistered: $id")
        }
        
        return removed
    }
    
    override fun getOrRegister(id: String, factory: () -> Agent): Agent {
        return agents[id] ?: run {
            val newAgent = factory()
            register(newAgent)
            newAgent
        }
    }
    
    override fun freeze(): AgentRegistry {
        frozen = true
        println("🌶️ Agent Registry frozen. No more registrations allowed.")
        return this
    }
    
    override fun toJSON(): String {
        val agentSummaries = agents.values.map { agent ->
            mapOf(
                "id" to agent.id,
                "name" to agent.name,
                "description" to agent.description,
                "capabilities" to agent.capabilities,
                "isReady" to agent.isReady()
            )
        }
        
        return """
        {
            "registry": {
                "totalAgents": ${agents.size},
                "frozen": $frozen,
                "agents": $agentSummaries
            }
        }
        """.trimIndent()
    }
    
    override fun getDescriptor(id: String): AgentDescriptor? = descriptors[id]
    
    override fun getAllDescriptors(): List<AgentDescriptor> = descriptors.values.toList()
    
    /**
     * Create descriptor from agent
     */
    private fun createDescriptor(agent: Agent): AgentDescriptor {
        val provider = when {
            agent.javaClass.simpleName.contains("OpenAI") -> "OpenAI"
            agent.javaClass.simpleName.contains("Anthropic") -> "Anthropic"
            agent.javaClass.simpleName.contains("Vertex") -> "Google Vertex"
            agent.javaClass.simpleName.contains("OpenRouter") -> "OpenRouter"
            agent.javaClass.simpleName.contains("VLLM") -> "vLLM"
            else -> "Unknown"
        }
        
        val tags = extractTags(agent)
        
        return AgentDescriptor(
            id = agent.id,
            name = agent.name,
            description = agent.description,
            capabilities = agent.capabilities,
            tags = tags,
            provider = provider,
            createdAt = Instant.now(),
            isReady = agent.isReady()
        )
    }
    
    /**
     * Extract tags from agent (can be extended with custom logic)
     */
    private fun extractTags(agent: Agent): List<String> {
        val tags = mutableListOf<String>()
        
        // Add capability-based tags
        if (agent.capabilities.contains("vision")) tags.add("vision")
        if (agent.capabilities.contains("text")) tags.add("text")
        if (agent.capabilities.contains("code")) tags.add("code")
        if (agent.capabilities.contains("multimodal")) tags.add("multimodal")
        
        // Add type-based tags
        when {
            agent.javaClass.simpleName.contains("Wizard") -> tags.add("wizard")
            agent.javaClass.simpleName.contains("Swarm") -> tags.add("swarm")
            agent.javaClass.simpleName.contains("Tool") -> tags.add("tool-enabled")
        }
        
        return tags
    }
}

/**
 * 🌶️ Registry Extensions
 * Utility functions for common registry operations
 */
object AgentRegistryExtensions {
    
    /**
     * Find agents that match all specified capabilities
     */
    fun AgentRegistry.findByCapabilities(vararg capabilities: String): List<Agent> {
        return getAll().filter { agent ->
            capabilities.all { capability -> agent.capabilities.contains(capability) }
        }
    }
    
    /**
     * Find agents that match any of the specified tags
     */
    fun AgentRegistry.findByAnyTag(vararg tags: String): List<Agent> {
        return tags.flatMap { tag -> findByTag(tag) }.distinct()
    }
    
    /**
     * Get ready agents only
     */
    fun AgentRegistry.getReadyAgents(): List<Agent> {
        return getAll().filter { it.isReady() }
    }
    
    /**
     * Get statistics about the registry
     */
    fun AgentRegistry.getStats(): Map<String, Any> {
        val all = getAll()
        val ready = all.filter { it.isReady() }
        val capabilities = all.flatMap { it.capabilities }.distinct()
        val providers = getAllDescriptors().map { it.provider }.distinct()
        
        return mapOf(
            "totalAgents" to all.size,
            "readyAgents" to ready.size,
            "uniqueCapabilities" to capabilities.size,
            "providers" to providers,
            "capabilities" to capabilities
        )
    }
} 