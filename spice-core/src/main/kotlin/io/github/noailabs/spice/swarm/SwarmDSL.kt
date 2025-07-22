package io.github.noailabs.spice.swarm

import io.github.noailabs.spice.*
import io.github.noailabs.spice.dsl.*

/**
 * Mock agent registry function (replace with actual AgentRegistry when available)
 */
private fun getAgentFromRegistry(agentId: String): Agent? {
    // For now, return null - in real implementation, use AgentRegistry.get(agentId)
    return null
}

/**
 * 🤖 Swarm Agent DSL - Revolutionary Multi-Agent Coordination
 * 
 * Ultimate DSL for creating sophisticated swarm agents that coordinate
 * multiple specialized agents to solve complex problems.
 */

/**
 * 🚀 Build SwarmAgent with Ultimate DSL
 */
fun buildSwarmAgent(config: SwarmAgentBuilder.() -> Unit): SwarmAgent {
    val builder = SwarmAgentBuilder()
    builder.config()
    return builder.build()
}

/**
 * 🔧 SwarmAgent Builder with Ultimate DSL
 */
class SwarmAgentBuilder {
    var id: String = "swarm-${System.currentTimeMillis()}"
    var name: String = ""
    var description: String = ""
    
    // Swarm-specific configurations
    private val memberAgents = mutableMapOf<String, Agent>()
    private var coordinatorType: CoordinatorType = CoordinatorType.SMART
    private var swarmConfig = SwarmConfig()
    private var defaultStrategy: SwarmStrategyType = SwarmStrategyType.PARALLEL
    
    /**
     * Configure swarm behavior
     */
    fun config(block: SwarmConfigBuilder.() -> Unit) {
        val configBuilder = SwarmConfigBuilder()
        configBuilder.block()
        swarmConfig = configBuilder.build()
    }
    
    /**
     * Add member agents to the swarm
     */
    fun agents(vararg agentIds: String) {
        agentIds.forEach { agentId ->
            // Try to get from global agent registry (mock for now)
            val agent = getAgentFromRegistry(agentId)
            if (agent != null) {
                memberAgents[agentId] = agent
            } else {
                println("⚠️ Warning: Agent '$agentId' not found in registry")
            }
        }
    }
    
    /**
     * Add agents with direct references
     */
    fun addAgent(agentId: String, agent: Agent) {
        memberAgents[agentId] = agent
    }
    
    /**
     * Add multiple agents with map
     */
    fun addAgents(agents: Map<String, Agent>) {
        memberAgents.putAll(agents)
    }
    
    /**
     * Configure swarm strategy
     */
    fun defaultStrategy(strategy: SwarmStrategyType) {
        defaultStrategy = strategy
    }
    
    /**
     * Set coordinator type
     */
    fun coordinator(type: CoordinatorType) {
        coordinatorType = type
    }
    
    /**
     * 🔧 Configure specialized swarm tools for coordination
     */
    fun swarmTools(block: SwarmToolBuilder.() -> Unit) {
        val toolBuilder = SwarmToolBuilder()
        toolBuilder.block()
        // TODO: Integrate tools with swarm agent
        // This will be added to SwarmAgent constructor in the future
    }
    
    /**
     * Quick swarm setup with member agent creation
     */
    fun quickSwarm(block: QuickSwarmBuilder.() -> Unit) {
        val quickBuilder = QuickSwarmBuilder()
        quickBuilder.block()
        memberAgents.putAll(quickBuilder.getAgents())
    }
    
    internal fun build(): SwarmAgent {
        require(name.isNotEmpty()) { "Swarm agent name is required" }
        require(memberAgents.isNotEmpty()) { "At least one member agent is required" }
        
        val coordinator = createCoordinator()
        
        return SwarmAgent(
            id = id,
            name = name,
            description = description,
            memberAgents = memberAgents.toMap(),
            coordinator = coordinator,
            config = swarmConfig
        )
    }
    
    private fun createCoordinator(): SwarmCoordinator {
        return when (coordinatorType) {
            CoordinatorType.SMART -> SmartSwarmCoordinator(memberAgents, swarmConfig)
            CoordinatorType.SIMPLE -> SimpleSwarmCoordinator(memberAgents, swarmConfig)
            CoordinatorType.AI_POWERED -> AISwarmCoordinator(memberAgents, swarmConfig)
        }
    }
}

/**
 * 🛠️ Swarm Configuration Builder
 */
class SwarmConfigBuilder {
    var debugEnabled: Boolean = false
    var maxConcurrentOperations: Int = 10
    var timeoutMs: Long = 30000
    var retryAttempts: Int = 3
    
    fun debug(enabled: Boolean = true) {
        debugEnabled = enabled
    }
    
    fun timeout(milliseconds: Long) {
        timeoutMs = milliseconds
    }
    
    fun maxOperations(count: Int) {
        maxConcurrentOperations = count
    }
    
    fun retries(attempts: Int) {
        retryAttempts = attempts
    }
    
    internal fun build(): SwarmConfig {
        return SwarmConfig(
            debugEnabled = debugEnabled,
            maxConcurrentOperations = maxConcurrentOperations,
            timeoutMs = timeoutMs,
            retryAttempts = retryAttempts
        )
    }
}

/**
 * ⚡ Quick Swarm Builder for rapid prototyping
 */
class QuickSwarmBuilder {
    private val agents = mutableMapOf<String, Agent>()
    
    /**
     * Add Claude agent
     */
    fun claudeAgent(
        id: String = "claude",
        name: String = "Claude Agent",
        apiKey: String? = null
    ) {
        if (apiKey != null) {
            // Create actual Claude agent - simplified for demo
            val agent = buildAgent {
                this.name = name
                description = "Claude-powered AI agent"
                handle { comm ->
                    comm.reply(
                        content = "🤖 Claude response to: ${comm.content}",
                        from = id
                    )
                }
            }
            agents[id] = agent
        } else {
            // Mock Claude agent for testing
            agents[id] = createMockAgent(id, name, "Claude-style responses")
        }
    }
    
    /**
     * Add GPT agent
     */
    fun gptAgent(
        id: String = "gpt",
        name: String = "GPT Agent", 
        apiKey: String? = null
    ) {
        if (apiKey != null) {
            // Create actual GPT agent - simplified for demo
            val agent = buildAgent {
                this.name = name
                description = "GPT-powered AI agent"
                handle { comm ->
                    comm.reply(
                        content = "🧠 GPT response to: ${comm.content}",
                        from = id
                    )
                }
            }
            agents[id] = agent
        } else {
            // Mock GPT agent for testing
            agents[id] = createMockAgent(id, name, "GPT-style responses")
        }
    }
    
    /**
     * Add specialist agent
     */
    fun specialist(
        id: String,
        name: String,
        speciality: String,
        responsePattern: String = "Specialist analysis"
    ) {
        agents[id] = createMockAgent(id, name, "$speciality: $responsePattern")
    }
    
    /**
     * Add research agent
     */
    fun researchAgent(
        id: String = "researcher",
        name: String = "Research Agent"
    ) {
        agents[id] = createMockAgent(id, name, "📚 Research findings")
    }
    
    /**
     * Add analysis agent
     */
    fun analysisAgent(
        id: String = "analyst", 
        name: String = "Analysis Agent"
    ) {
        agents[id] = createMockAgent(id, name, "📊 Analytical insights")
    }
    
    /**
     * Add creative agent
     */
    fun creativeAgent(
        id: String = "creative",
        name: String = "Creative Agent"
    ) {
        agents[id] = createMockAgent(id, name, "🎨 Creative solutions")
    }
    
    private fun createMockAgent(id: String, name: String, responsePrefix: String): Agent {
        return buildAgent {
            this.name = name
            description = "Mock agent for swarm testing"
            
            handle { comm ->
                comm.reply(
                    content = "$responsePrefix: ${comm.content}",
                    from = id,
                    data = mapOf(
                        "agent_type" to "mock",
                        "speciality" to responsePrefix
                    )
                )
            }
        }
    }
    
    internal fun getAgents(): Map<String, Agent> = agents.toMap()
}

/**
 * 🤔 Simple Coordinator Implementation (for lightweight swarms)
 */
class SimpleSwarmCoordinator(
    private val memberAgents: Map<String, Agent>,
    private val config: SwarmConfig
) : SwarmCoordinator {
    
    override suspend fun analyzeTask(task: String): SwarmStrategy {
        // Simple strategy: always use all agents in parallel
        return SwarmStrategy(
            type = SwarmStrategyType.PARALLEL,
            selectedAgents = memberAgents.keys.toList(),
            confidence = 0.7
        )
    }
    
    override suspend fun aggregateResults(results: List<AgentResult>, strategy: SwarmStrategy): SwarmResult {
        val successfulResults = results.filter { it.success }
        val content = if (successfulResults.isEmpty()) {
            "🤖 No successful results from swarm agents"
        } else {
            "🤖 Simple Swarm Results:\n\n${successfulResults.joinToString("\n") { "• ${it.content}" }}"
        }
        
        return SwarmResult(
            content = content,
            successRate = successfulResults.size.toDouble() / results.size,
            agentResults = results
        )
    }
    
    override suspend fun buildConsensus(results: List<AgentResult>, strategy: SwarmStrategy): SwarmResult {
        return aggregateResults(results, strategy) // Simple consensus = aggregation
    }
    
    override suspend fun selectBestResult(results: SwarmResult, strategy: SwarmStrategy): SwarmResult {
        val best = results.agentResults.filter { it.success }.randomOrNull()
        return SwarmResult(
            content = best?.content ?: "No results",
            successRate = results.successRate,
            agentResults = results.agentResults
        )
    }
}

/**
 * 🧠 AI-Powered Coordinator (future enhancement)
 */
class AISwarmCoordinator(
    private val memberAgents: Map<String, Agent>,
    private val config: SwarmConfig
) : SwarmCoordinator {
    
    override suspend fun analyzeTask(task: String): SwarmStrategy {
        // TODO: Use AI to analyze task and determine optimal strategy
        // For now, fallback to smart coordinator behavior
        return SmartSwarmCoordinator(memberAgents, config).analyzeTask(task)
    }
    
    override suspend fun aggregateResults(results: List<AgentResult>, strategy: SwarmStrategy): SwarmResult {
        // TODO: Use AI to intelligently aggregate results
        return SmartSwarmCoordinator(memberAgents, config).aggregateResults(results, strategy)
    }
    
    override suspend fun buildConsensus(results: List<AgentResult>, strategy: SwarmStrategy): SwarmResult {
        // TODO: Use AI to build sophisticated consensus
        return SmartSwarmCoordinator(memberAgents, config).buildConsensus(results, strategy)
    }
    
    override suspend fun selectBestResult(results: SwarmResult, strategy: SwarmStrategy): SwarmResult {
        // TODO: Use AI to select best result
        return SmartSwarmCoordinator(memberAgents, config).selectBestResult(results, strategy)
    }
}

/**
 * 🎯 Coordinator Types
 */
enum class CoordinatorType {
    SIMPLE,      // Basic coordination
    SMART,       // Intelligent coordination (default)
    AI_POWERED   // AI-enhanced coordination (future)
}

// =====================================
// CONVENIENCE FUNCTIONS  
// =====================================

/**
 * 🚀 Quick swarm creation functions
 */

/**
 * Create research swarm
 */
fun researchSwarm(
    name: String = "Research Swarm",
    debugEnabled: Boolean = false
): SwarmAgent {
    return buildSwarmAgent {
        this.name = name
        description = "Multi-agent research and analysis swarm"
        
        config {
            debug(debugEnabled)
            timeout(45000) // Research takes time
        }
        
        quickSwarm {
            researchAgent("researcher", "Lead Researcher")
            analysisAgent("analyst", "Data Analyst") 
            specialist("domain-expert", "Domain Expert", "Expert analysis")
        }
        
        defaultStrategy(SwarmStrategyType.SEQUENTIAL)
    }
}

/**
 * Create creative swarm
 */
fun creativeSwarm(
    name: String = "Creative Swarm",
    debugEnabled: Boolean = false
): SwarmAgent {
    return buildSwarmAgent {
        this.name = name
        description = "Multi-agent creative collaboration swarm"
        
        config {
            debug(debugEnabled)
            maxOperations(5)
        }
        
        quickSwarm {
            creativeAgent("creative1", "Creative Director")
            creativeAgent("creative2", "Design Specialist")
            specialist("critic", "Creative Critic", "Constructive feedback")
        }
        
        defaultStrategy(SwarmStrategyType.CONSENSUS)
    }
}

/**
 * Create decision-making swarm
 */
fun decisionSwarm(
    name: String = "Decision Swarm", 
    debugEnabled: Boolean = false
): SwarmAgent {
    return buildSwarmAgent {
        this.name = name
        description = "Multi-agent decision making swarm"
        
        config {
            debug(debugEnabled)
            retries(2)
        }
        
        quickSwarm {
            analysisAgent("pro-analyst", "Pro Analyst")
            analysisAgent("con-analyst", "Con Analyst")
            specialist("judge", "Decision Judge", "Final judgment")
        }
        
        defaultStrategy(SwarmStrategyType.COMPETITION)
    }
}

/**
 * Create AI powerhouse swarm (when you have real API keys)
 */
fun aiPowerhouseSwarm(
    name: String = "AI Powerhouse",
    claudeApiKey: String? = null,
    gptApiKey: String? = null,
    debugEnabled: Boolean = false
): SwarmAgent {
    return buildSwarmAgent {
        this.name = name
        description = "Premium AI agents collaboration swarm"
        
        config {
            debug(debugEnabled)
            timeout(60000) // AI takes time
            maxOperations(3) // Premium agents
        }
        
        quickSwarm {
            if (claudeApiKey != null) {
                claudeAgent("claude", "Claude-3.5 Sonnet", claudeApiKey)
            }
            if (gptApiKey != null) {
                gptAgent("gpt", "GPT-4o", gptApiKey)
            }
            // Add mock agents if no API keys
            if (claudeApiKey == null && gptApiKey == null) {
                claudeAgent("claude-mock", "Claude (Mock)")
                gptAgent("gpt-mock", "GPT (Mock)")
            }
        }
        
        defaultStrategy(SwarmStrategyType.CONSENSUS)
        coordinator(CoordinatorType.SMART)
    }
} 