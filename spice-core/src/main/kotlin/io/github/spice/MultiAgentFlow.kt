package io.github.spice

import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.CoroutineContext

/**
 * 🌊 Multi-Agent Flow Execution Strategies
 * 
 * Defines how multiple agents coordinate to process messages
 */
enum class FlowStrategy {
    /**
     * Agents execute sequentially: A → B → C
     */
    SEQUENTIAL,
    
    /**
     * Agents execute in parallel: A, B, C simultaneously
     */
    PARALLEL,
    
    /**
     * Agents compete for fastest response
     */
    COMPETITION,
    
    /**
     * Messages flow through agents as pipeline: A output → B input → C input
     */
    PIPELINE
}

/**
 * 🏁 Agent Competition Result
 */
data class AgentCompetitionResult(
    val agentId: String,
    val agentName: String,
    val content: String,
    val processingTime: Long,
    val success: Boolean
)

/**
 * 📊 Group Metadata for tracking execution
 */
data class GroupMetadata(
    val groupId: String,
    val totalAgents: Int,
    val currentIndex: Int,
    val strategy: FlowStrategy,
    val coordinatorId: String
) {
    /**
     * Convert to metadata map for Message
     */
    fun toMetadataMap(): Map<String, String> = mapOf(
        "groupId" to groupId,
        "totalAgents" to totalAgents.toString(),
        "currentIndex" to currentIndex.toString(),
        "strategy" to strategy.name,
        "coordinatorId" to coordinatorId
    )
}

/**
 * 🔀 Multi-Agent Flow - High-Performance Agent Orchestration
 * 
 * Manages multiple agents with different execution strategies:
 * - SEQUENTIAL: Agents execute one after another
 * - PARALLEL: All agents execute simultaneously
 * - COMPETITION: Fastest agent wins
 * - PIPELINE: Output of one agent becomes input of next
 */
class MultiAgentFlow(
    private val strategy: FlowStrategy = FlowStrategy.SEQUENTIAL,
    private val timeoutMs: Long = 30_000L,
    private val maxRetries: Int = 3
) {
    
    private val agentPool = CopyOnWriteArrayList<Agent>()
    private var strategyResolver: ((Message, List<Agent>) -> FlowStrategy)? = null
    
    /**
     * Add agent to the flow
     */
    fun addAgent(agent: Agent): MultiAgentFlow {
        agentPool.add(agent)
        return this
    }
    
    /**
     * Add multiple agents at once
     */
    fun addAgents(vararg agents: Agent): MultiAgentFlow {
        agentPool.addAll(agents)
        return this
    }
    
    /**
     * Set custom strategy resolver for dynamic strategy selection
     */
    fun setStrategyResolver(resolver: (Message, List<Agent>) -> FlowStrategy): MultiAgentFlow {
        this.strategyResolver = resolver
        return this
    }
    
    /**
     * Process message through agent flow
     */
    suspend fun process(message: Message): Message {
        if (agentPool.isEmpty()) {
            return message.createReply(
                content = "No agents available for processing",
                sender = "multi-agent-flow",
                type = MessageType.ERROR,
                metadata = mapOf("error" to "empty_agent_pool")
            )
        }
        
        val activeAgents = agentPool.filter { it.isReady() }
        if (activeAgents.isEmpty()) {
            return message.createReply(
                content = "No ready agents available",
                sender = "multi-agent-flow", 
                type = MessageType.ERROR,
                metadata = mapOf("error" to "no_ready_agents")
            )
        }
        
        val executionStrategy = strategyResolver?.invoke(message, activeAgents) ?: strategy
        val startTime = System.currentTimeMillis()
        
        val result = try {
            withTimeout(timeoutMs) {
                when (executionStrategy) {
                    FlowStrategy.SEQUENTIAL -> processSequential(message, activeAgents)
                    FlowStrategy.PARALLEL -> processParallel(message, activeAgents)
                    FlowStrategy.COMPETITION -> processCompetition(message, activeAgents)
                    FlowStrategy.PIPELINE -> processPipeline(message, activeAgents)
                }
            }
        } catch (e: TimeoutCancellationException) {
            message.createReply(
                content = "Flow execution timed out after ${timeoutMs}ms",
                sender = "multi-agent-flow",
                type = MessageType.ERROR,
                metadata = mapOf(
                    "error" to "timeout",
                    "timeoutMs" to timeoutMs.toString(),
                    "strategy" to executionStrategy.name
                )
            )
        } catch (e: Exception) {
            message.createReply(
                content = "Flow execution failed: ${e.message}",
                sender = "multi-agent-flow",
                type = MessageType.ERROR,
                metadata = mapOf(
                    "error" to "execution_failed",
                    "cause" to (e::class.simpleName ?: "Unknown"),
                    "strategy" to executionStrategy.name
                )
            )
        }
        
        val executionTime = System.currentTimeMillis() - startTime
        
        return result.copy(
            metadata = result.metadata + mapOf(
                "flow_strategy" to executionStrategy.name,
                "execution_time_ms" to executionTime.toString(),
                "agent_count" to activeAgents.size.toString()
            )
        )
    }
    
    /**
     * Sequential processing: A → B → C
     */
    private suspend fun processSequential(message: Message, agents: List<Agent>): Message {
        var currentMessage = message
        val processingSteps = mutableListOf<String>()
        
        for ((index, agent) in agents.withIndex()) {
            try {
                val stepMessage = currentMessage.withMetadata("step_index", index.toString())
                val result = agent.processMessage(stepMessage)
                
                processingSteps.add("Step ${index + 1} (${agent.name}): Processed")
                currentMessage = result
                
            } catch (e: Exception) {
                processingSteps.add("Step ${index + 1} (${agent.name}): Error - ${e.message}")
                // Continue with original message if agent fails
            }
        }
        
        return currentMessage.copy(
            metadata = currentMessage.metadata + mapOf(
                "sequential_steps" to processingSteps.joinToString("; "),
                "completed_steps" to processingSteps.size.toString()
            )
        )
    }
    
    /**
     * Parallel processing: A, B, C simultaneously
     */
    private suspend fun processParallel(message: Message, agents: List<Agent>): Message = coroutineScope {
        val results = agents.mapIndexed { index, agent ->
            async {
                try {
                    val indexedMessage = message.withMetadata("parallel_index", index.toString())
                    val result = agent.processMessage(indexedMessage)
                    "${agent.name}: ${result.content}"
                } catch (e: Exception) {
                    "${agent.name}: Error - ${e.message}"
                }
            }
        }.awaitAll()
        
        return@coroutineScope message.createReply(
            content = "Parallel processing results:\n${results.joinToString("\n")}",
            sender = "multi-agent-flow",
            type = MessageType.RESULT,
            metadata = mapOf(
                "parallel_results" to results.joinToString("; "),
                "successful_agents" to results.count { !it.contains("Error") }.toString()
            )
        )
    }
    
    /**
     * Competition processing: Fastest agent wins
     */
    private suspend fun processCompetition(message: Message, agents: List<Agent>): Message = coroutineScope {
        val competitionResults = agents.map { agent ->
            async {
                val startTime = System.currentTimeMillis()
                try {
                    val result = agent.processMessage(message)
                    val processingTime = System.currentTimeMillis() - startTime
                    AgentCompetitionResult(
                        agentId = agent.id,
                        agentName = agent.name,
                        content = result.content,
                        processingTime = processingTime,
                        success = true
                    )
                } catch (e: Exception) {
                    val processingTime = System.currentTimeMillis() - startTime
                    AgentCompetitionResult(
                        agentId = agent.id,
                        agentName = agent.name,
                        content = "Error: ${e.message}",
                        processingTime = processingTime,
                        success = false
                    )
                }
            }
        }
        
        // Wait for first successful result
        val firstCompleted = try {
            select<AgentCompetitionResult?> {
                competitionResults.forEach { deferred ->
                    deferred.onAwait { result ->
                        if (result.success) result else null
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
        
        // If no successful result, get all results and find best one
        val winner = firstCompleted ?: run {
            val allResults = competitionResults.awaitAll()
            allResults.firstOrNull { it.success } ?: allResults.minByOrNull { it.processingTime }
            ?: AgentCompetitionResult("fallback", "System", "All agents failed", 0L, false)
        }
        
        // Cancel remaining tasks if we have a winner
        if (winner.success) {
            competitionResults.forEach { 
                if (!it.isCompleted) it.cancel()
            }
        }
        
        return@coroutineScope message.createReply(
            content = "Competition winner: ${winner.agentName} (${winner.processingTime}ms)\n${winner.content}",
            sender = "multi-agent-flow",
            type = MessageType.RESULT,
            metadata = mapOf(
                "winner_id" to winner.agentId,
                "winner_name" to winner.agentName,
                "winner_time" to winner.processingTime.toString(),
                "winner_success" to winner.success.toString()
            )
        )
    }
    
    /**
     * Pipeline processing: A output → B input → C input
     */
    private suspend fun processPipeline(message: Message, agents: List<Agent>): Message {
        var currentMessage = message
        val pipelineSteps = mutableListOf<String>()
        
        for ((index, agent) in agents.withIndex()) {
            try {
                val stepMessage = currentMessage.withMetadata("pipeline_step", index.toString())
                val result = agent.processMessage(stepMessage)
                
                pipelineSteps.add("Step ${index + 1} (${agent.name}): Processed")
                
                // Next agent receives previous agent's output
                currentMessage = result.copy(
                    metadata = currentMessage.metadata + mapOf(
                        "previous_agent" to agent.id,
                        "pipeline_step" to index.toString()
                    )
                )
                
            } catch (e: Exception) {
                pipelineSteps.add("Step ${index + 1} (${agent.name}): Failed - ${e.message}")
                break // Stop pipeline on error
            }
        }
        
        return currentMessage.copy(
            metadata = currentMessage.metadata + mapOf(
                "pipeline_steps" to pipelineSteps.joinToString("; "),
                "completed_steps" to pipelineSteps.size.toString()
            )
        )
    }
    
    /**
     * Get current agent pool size
     */
    fun getAgentCount(): Int = agentPool.size
    
    /**
     * Get all agents in the pool
     */
    fun getAgents(): List<Agent> = agentPool.toList()
    
    /**
     * Clear all agents from the pool
     */
    fun clearAgents(): MultiAgentFlow {
        agentPool.clear()
        return this
    }
}

/**
 * 🐝 Swarm Agent - Intelligent Agent Coordination
 * 
 * Manages a dynamic pool of agents with intelligent selection and strategy resolution
 */
class SwarmAgent(
    override val id: String = "swarm-agent",
    override val name: String = "Swarm Agent",
    override val description: String = "Intelligent swarm of coordinated agents",
    override val capabilities: List<String> = listOf("coordination", "multi_agent", "adaptive")
) : Agent {
    
    private val agentPool = CopyOnWriteArrayList<Agent>()
    private var strategyResolver: ((Message, List<Agent>) -> FlowStrategy)? = null
    private val flow = MultiAgentFlow()
    
    /**
     * Add agent to swarm pool
     */
    fun addToPool(agent: Agent): SwarmAgent {
        agentPool.add(agent)
        return this
    }
    
    /**
     * Set custom strategy resolver
     */
    fun setStrategyResolver(resolver: (Message, List<Agent>) -> FlowStrategy): SwarmAgent {
        this.strategyResolver = resolver
        return this
    }
    
    override suspend fun processMessage(message: Message): Message {
        if (agentPool.isEmpty()) {
            return message.createReply(
                content = "No agents in swarm pool",
                sender = id,
                type = MessageType.ERROR
            )
        }
        
        // Select optimal agents for this message
        val selectedAgents = selectOptimalAgents(message)
        
        // Configure flow with selected agents
        val swarmFlow = MultiAgentFlow().apply {
            selectedAgents.forEach { addAgent(it) }
            strategyResolver?.let { setStrategyResolver(it) }
        }
        
        // Process with swarm coordination
        val result = swarmFlow.process(message)
        
        return result.copy(
            sender = id,
            metadata = result.metadata + mapOf(
                "swarm_id" to id,
                "swarm_name" to name,
                "selected_agents" to selectedAgents.joinToString(",") { it.id },
                "swarm_size" to selectedAgents.size.toString()
            )
        )
    }
    
    /**
     * Select optimal agents based on message characteristics
     */
    private fun selectOptimalAgents(message: Message): List<Agent> {
        val requiredCapabilities = extractRequiredCapabilities(message)
        
        return agentPool.filter { agent ->
            // Check if agent has required capabilities
            requiredCapabilities.any { capability ->
                agent.capabilities.contains(capability)
            } || agent.canHandle(message)
        }.ifEmpty {
            // If no agents match capabilities, use all available agents
            agentPool.toList()
        }
    }
    
    /**
     * Extract required capabilities from message
     */
    private fun extractRequiredCapabilities(message: Message): List<String> {
        val capabilities = mutableListOf<String>()
        
        // Check metadata for explicit capabilities
        message.metadata["required_capabilities"]?.let { caps ->
            capabilities.addAll(caps.split(",").map { it.trim() })
        }
        
        // Analyze content for implicit capabilities
        val content = message.content.lowercase()
        val capabilityKeywords = mapOf(
            "analysis" to listOf("analyze", "examine", "study", "investigate"),
            "generation" to listOf("create", "generate", "produce", "build"),
            "translation" to listOf("translate", "convert", "transform"),
            "search" to listOf("search", "find", "lookup", "discover"),
            "classification" to listOf("classify", "categorize", "sort", "group"),
            "extraction" to listOf("extract", "pull", "get", "retrieve"),
            "validation" to listOf("validate", "verify", "check", "confirm"),
            "optimization" to listOf("optimize", "improve", "enhance", "refine")
        )
        
        capabilityKeywords.forEach { (capability, keywords) ->
            if (keywords.any { keyword -> content.contains(keyword) }) {
                capabilities.add(capability)
            }
        }
        
        return capabilities.distinct()
    }
    
    override fun canHandle(message: Message): Boolean = agentPool.isNotEmpty()
    
    override fun getTools(): List<Tool> = agentPool.flatMap { it.getTools() }
    
    override fun isReady(): Boolean = agentPool.any { it.isReady() }
}

/**
 * 🏗️ Extension Functions for Agent List Operations
 */

/**
 * Convert list of agents to MultiAgentFlow
 */
fun List<Agent>.toMultiAgentFlow(strategy: FlowStrategy = FlowStrategy.SEQUENTIAL): MultiAgentFlow {
    val flow = MultiAgentFlow(strategy)
    forEach { flow.addAgent(it) }
    return flow
}

/**
 * Convert list of agents to SwarmAgent
 */
fun List<Agent>.toSwarm(
    id: String = "agent-swarm",
    name: String = "Agent Swarm",
    description: String = "Coordinated agent swarm"
): SwarmAgent {
    val swarm = SwarmAgent(id, name, description)
    forEach { swarm.addToPool(it) }
    return swarm
}

/**
 * 🎯 Convenience Functions for Common Flow Patterns
 */

/**
 * Create a sequential flow
 */
fun sequentialFlow(vararg agents: Agent): MultiAgentFlow = 
    MultiAgentFlow(FlowStrategy.SEQUENTIAL).addAgents(*agents)

/**
 * Create a parallel flow
 */
fun parallelFlow(vararg agents: Agent): MultiAgentFlow = 
    MultiAgentFlow(FlowStrategy.PARALLEL).addAgents(*agents)

/**
 * Create a competition flow
 */
fun competitionFlow(vararg agents: Agent): MultiAgentFlow = 
    MultiAgentFlow(FlowStrategy.COMPETITION).addAgents(*agents)

/**
 * Create a pipeline flow
 */
fun pipelineFlow(vararg agents: Agent): MultiAgentFlow = 
    MultiAgentFlow(FlowStrategy.PIPELINE).addAgents(*agents) 