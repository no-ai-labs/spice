package io.github.noailabs.spice.springboot.ai.registry

import io.github.noailabs.spice.Agent
import java.util.concurrent.ConcurrentHashMap

/**
 * 📚 Agent Registry
 *
 * Manages named agents for runtime selection.
 * Supports thread-safe registration and retrieval of agents.
 *
 * **Usage:**
 * ```kotlin
 * @Configuration
 * class AgentConfig(private val factory: SpringAIAgentFactory) {
 *     @Bean
 *     fun agentRegistry(): AgentRegistry {
 *         return DefaultAgentRegistry().apply {
 *             register("fast", factory.openai("gpt-3.5-turbo"))
 *             register("smart", factory.openai("gpt-4"))
 *             register("reasoning", factory.openai("o1"))
 *             register("claude", factory.anthropic("claude-3-5-sonnet-20241022"))
 *             register("local", factory.ollama("llama3"))
 *         }
 *     }
 * }
 *
 * @Service
 * class ChatService(private val registry: AgentRegistry) {
 *     suspend fun chat(agentName: String, message: String): String {
 *         val agent = registry.get(agentName)
 *         val response = agent.processMessage(SpiceMessage.create(message, "user"))
 *         return response.getOrThrow().content
 *     }
 * }
 * ```
 *
 * @since 1.0.0
 * @author Spice Framework
 */
interface AgentRegistry {

    /**
     * Register an agent with a name
     *
     * @param name Agent name (unique identifier)
     * @param agent Agent instance
     * @throws IllegalArgumentException if name already registered
     */
    fun register(name: String, agent: Agent)

    /**
     * Register an agent, replacing existing if present
     *
     * @param name Agent name
     * @param agent Agent instance
     */
    fun registerOrReplace(name: String, agent: Agent)

    /**
     * Get agent by name
     *
     * @param name Agent name
     * @return Agent instance
     * @throws NoSuchElementException if agent not found
     */
    fun get(name: String): Agent

    /**
     * Get agent by name, or null if not found
     *
     * @param name Agent name
     * @return Agent instance or null
     */
    fun getOrNull(name: String): Agent?

    /**
     * Check if agent is registered
     *
     * @param name Agent name
     * @return True if registered
     */
    fun has(name: String): Boolean

    /**
     * List all registered agent names
     *
     * @return List of agent names
     */
    fun list(): List<String>

    /**
     * Remove agent from registry
     *
     * @param name Agent name
     * @return True if agent was removed, false if not found
     */
    fun remove(name: String): Boolean

    /**
     * Clear all registered agents
     */
    fun clear()

    /**
     * Get number of registered agents
     *
     * @return Count
     */
    fun size(): Int
}

/**
 * 📚 Default Agent Registry Implementation
 *
 * Thread-safe implementation using ConcurrentHashMap.
 *
 * @property threadSafe Enable thread-safe operations (default: true)
 *
 * @since 1.0.0
 * @author Spice Framework
 */
class DefaultAgentRegistry(
    private val threadSafe: Boolean = true
) : AgentRegistry {

    private val agents = if (threadSafe) {
        ConcurrentHashMap<String, Agent>()
    } else {
        mutableMapOf<String, Agent>()
    }

    override fun register(name: String, agent: Agent) {
        if (agents.containsKey(name)) {
            throw IllegalArgumentException("Agent already registered with name: $name")
        }
        agents[name] = agent
    }

    override fun registerOrReplace(name: String, agent: Agent) {
        agents[name] = agent
    }

    override fun get(name: String): Agent {
        return agents[name]
            ?: throw NoSuchElementException("No agent registered with name: $name")
    }

    override fun getOrNull(name: String): Agent? {
        return agents[name]
    }

    override fun has(name: String): Boolean {
        return agents.containsKey(name)
    }

    override fun list(): List<String> {
        return agents.keys.toList()
    }

    override fun remove(name: String): Boolean {
        return agents.remove(name) != null
    }

    override fun clear() {
        agents.clear()
    }

    override fun size(): Int {
        return agents.size
    }

    /**
     * Get agent by name with fallback
     *
     * @param name Primary agent name
     * @param fallback Fallback agent name
     * @return Agent instance
     */
    fun getOrFallback(name: String, fallback: String): Agent {
        return agents[name] ?: agents[fallback]
            ?: throw NoSuchElementException("No agent registered with name: $name or $fallback")
    }

    /**
     * Get agent by name with default
     *
     * @param name Agent name
     * @param default Default agent
     * @return Agent instance
     */
    fun getOrDefault(name: String, default: Agent): Agent {
        return agents[name] ?: default
    }

    /**
     * Get all agents
     *
     * @return Map of agent name to agent
     */
    fun getAll(): Map<String, Agent> {
        return agents.toMap()
    }

    /**
     * Filter agents by predicate
     *
     * @param predicate Filter predicate
     * @return Map of matching agents
     */
    fun filter(predicate: (Map.Entry<String, Agent>) -> Boolean): Map<String, Agent> {
        return agents.filter(predicate)
    }

    /**
     * Find agents by capability
     *
     * @param capability Capability to search for
     * @return List of agent names with the capability
     */
    fun findByCapability(capability: String): List<String> {
        return agents.filter { (_, agent) ->
            agent.capabilities.contains(capability)
        }.keys.toList()
    }
}
