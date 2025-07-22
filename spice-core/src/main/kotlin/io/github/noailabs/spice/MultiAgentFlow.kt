package io.github.noailabs.spice

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
 * 🌊 Multi-Agent Flow - Orchestrates agent execution with various strategies
 */
class MultiAgentFlow(
    private val flowId: String = "flow-${System.currentTimeMillis()}",
    private val defaultStrategy: FlowStrategy = FlowStrategy.SEQUENTIAL,
    private val coroutineContext: CoroutineContext = Dispatchers.Default,
    private val maxRetries: Int = 3
) {
    
    private val agentPool = CopyOnWriteArrayList<Agent>()
    private var strategyResolver: ((Comm, List<Agent>) -> FlowStrategy)? = null
    
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
    fun setStrategyResolver(resolver: (Comm, List<Agent>) -> FlowStrategy): MultiAgentFlow {
        this.strategyResolver = resolver
        return this
    }
    
    /**
     * Process comm through agent flow
     */
    suspend fun process(comm: Comm): Comm {
        if (agentPool.isEmpty()) {
            return comm.reply(
                content = "No agents available for processing",
                from = "multi-agent-flow",
                type = CommType.ERROR
            ).withData("error", "empty_agent_pool")
        }
        
        val activeAgents = agentPool.filter { it.isReady() }
        if (activeAgents.isEmpty()) {
            return comm.reply(
                content = "No ready agents available",
                from = "multi-agent-flow", 
                type = CommType.ERROR
            ).withData("error", "no_ready_agents")
        }
        
        val executionStrategy = strategyResolver?.invoke(comm, activeAgents) ?: defaultStrategy
        val startTime = System.currentTimeMillis()
        
        val result = try {
            withTimeout(30_000L) { // Keep original timeoutMs
                when (executionStrategy) {
                    FlowStrategy.SEQUENTIAL -> processSequential(comm, activeAgents)
                    FlowStrategy.PARALLEL -> processParallel(comm, activeAgents)
                    FlowStrategy.COMPETITION -> processCompetition(comm, activeAgents)
                    FlowStrategy.PIPELINE -> processPipeline(comm, activeAgents)
                }
            }
        } catch (e: TimeoutCancellationException) {
            comm.reply(
                content = "Flow execution timed out after 30000ms",
                from = "multi-agent-flow",
                type = CommType.ERROR,
                data = mapOf(
                    "error" to "timeout",
                    "timeoutMs" to "30000",
                    "strategy" to executionStrategy.name
                )
            )
        } catch (e: Exception) {
            comm.reply(
                content = "Flow execution failed: ${e.message}",
                from = "multi-agent-flow",
                type = CommType.ERROR,
                data = mapOf(
                    "error" to "execution_failed",
                    "cause" to (e::class.simpleName ?: "Unknown"),
                    "strategy" to executionStrategy.name
                )
            )
        }
        
        val executionTime = System.currentTimeMillis() - startTime
        
        return result.copy(
            data = result.data + mapOf(
                "flow_strategy" to executionStrategy.name,
                "execution_time_ms" to executionTime.toString(),
                "agent_count" to activeAgents.size.toString()
            )
        )
    }
    
    /**
     * Sequential processing: A → B → C
     */
    private suspend fun processSequential(comm: Comm, agents: List<Agent>): Comm {
        var currentComm = comm
        val processingSteps = mutableListOf<String>()
        
        for ((index, agent) in agents.withIndex()) {
            try {
                val stepComm = currentComm.withData("step_index", index.toString())
                val result = agent.processComm(stepComm)
                
                processingSteps.add("Step ${index + 1} (${agent.name}): Processed")
                currentComm = result
                
            } catch (e: Exception) {
                processingSteps.add("Step ${index + 1} (${agent.name}): Error - ${e.message}")
                // Continue with original message if agent fails
            }
        }
        
        return currentComm.copy(
            data = currentComm.data + mapOf(
                "sequential_steps" to processingSteps.joinToString("; "),
                "completed_steps" to processingSteps.size.toString()
            )
        )
    }
    
    /**
     * Parallel processing: A, B, C simultaneously
     */
    private suspend fun processParallel(comm: Comm, agents: List<Agent>): Comm = coroutineScope {
        val results = agents.mapIndexed { index, agent ->
            async {
                try {
                    val indexedComm = comm.withData("parallel_index", index.toString())
                    val result = agent.processComm(indexedComm)
                    "${agent.name}: ${result.content}"
                } catch (e: Exception) {
                    "${agent.name}: Error - ${e.message}"
                }
            }
        }.awaitAll()
        
        return@coroutineScope comm.reply(
            content = "Parallel processing results:\n${results.joinToString("\n")}",
            from = "multi-agent-flow",
            type = CommType.RESULT,
            data = mapOf(
                "parallel_results" to results.joinToString("; "),
                "successful_agents" to results.count { !it.contains("Error") }.toString()
            )
        )
    }
    
    /**
     * Competition processing: Fastest agent wins
     */
    private suspend fun processCompetition(comm: Comm, agents: List<Agent>): Comm = coroutineScope {
        val competitionResults = agents.map { agent ->
            async {
                val startTime = System.currentTimeMillis()
                try {
                    val result = agent.processComm(comm)
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
        
        return@coroutineScope comm.reply(
            content = "Competition winner: ${winner.agentName} (${winner.processingTime}ms)\n${winner.content}",
            from = "multi-agent-flow",
            type = CommType.RESULT,
            data = mapOf(
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
    private suspend fun processPipeline(comm: Comm, agents: List<Agent>): Comm {
        var currentComm = comm
        val pipelineSteps = mutableListOf<String>()
        
        for ((index, agent) in agents.withIndex()) {
            try {
                val stepComm = currentComm.withData("pipeline_step", index.toString())
                val result = agent.processComm(stepComm)
                
                pipelineSteps.add("Step ${index + 1} (${agent.name}): Processed")
                
                // Next agent receives previous agent's output
                currentComm = result.copy(
                    data = currentComm.data + mapOf(
                        "previous_agent" to agent.id,
                        "pipeline_step" to index.toString()
                    )
                )
                
            } catch (e: Exception) {
                pipelineSteps.add("Step ${index + 1} (${agent.name}): Failed - ${e.message}")
                break // Stop pipeline on error
            }
        }
        
        return currentComm.copy(
            data = currentComm.data + mapOf(
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
 * 🐝 Swarm Agent - Dynamically coordinates multiple agents based on context
 */
class SwarmAgent(
    override val id: String = "swarm-agent",
    override val name: String = "Swarm Agent",
    override val description: String = "Intelligent swarm of coordinated agents",
    override val capabilities: List<String> = listOf("coordination", "multi_agent", "adaptive")
) : Agent {
    
    private val agentPool = CopyOnWriteArrayList<Agent>()
    private var strategyResolver: ((Comm, List<Agent>) -> FlowStrategy)? = null
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
    fun setStrategyResolver(resolver: (Comm, List<Agent>) -> FlowStrategy): SwarmAgent {
        this.strategyResolver = resolver
        return this
    }
    
    override suspend fun processComm(comm: Comm): Comm {
        if (agentPool.isEmpty()) {
            return comm.reply(
                content = "No agents in swarm pool",
                from = id,
                type = CommType.ERROR
            )
        }
        
        // Select optimal agents for this comm
        val selectedAgents = selectOptimalAgents(comm)
        
        // Create new flow with selected agents
        val swarmFlow = MultiAgentFlow().apply {
            selectedAgents.forEach { addAgent(it) }
            strategyResolver?.let { setStrategyResolver(it) }
        }
        
        // Determine execution strategy
        val strategy = strategyResolver?.invoke(comm, selectedAgents) 
            ?: determineDefaultStrategy(comm, selectedAgents)
        
        // Process through flow
        val flowComm = comm.copy(
            data = comm.data + mapOf(
                "swarm_id" to id,
                "swarm_agents" to selectedAgents.map { it.id }.joinToString(","),
                "swarm_strategy" to strategy.name
            )
        )
        
        return swarmFlow.process(flowComm)
    }
    
    /**
     * Select optimal agents based on comm content and capabilities
     */
    private fun selectOptimalAgents(comm: Comm): List<Agent> {
        val requiredCapabilities = extractRequiredCapabilities(comm)
        
        return agentPool.filter { agent ->
            // Check if agent has any required capability
            requiredCapabilities.any { required ->
                agent.capabilities.any { capability ->
                    capability.equals(required, ignoreCase = true) ||
                    capability.contains(required, ignoreCase = true)
                }
            } || agent.canHandle(comm)
        }.ifEmpty {
            // If no agents match capabilities, use all available agents
            agentPool.toList()
        }
    }
    
    /**
     * Extract required capabilities from comm
     */
    private fun extractRequiredCapabilities(comm: Comm): List<String> {
        val capabilities = mutableListOf<String>()
        
        // Check data for explicit capabilities
        comm.data["required_capabilities"]?.let { caps ->
            capabilities.addAll(caps.split(",").map { it.trim() })
        }
        
        // Analyze content for implicit capabilities
        val content = comm.content.lowercase()
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

    /**
     * Determine default strategy based on message and selected agents
     */
    private fun determineDefaultStrategy(comm: Comm, selectedAgents: List<Agent>): FlowStrategy {
        // Simple strategy: if all agents can handle, use PARALLEL. Otherwise, SEQUENTIAL.
        if (selectedAgents.all { it.canHandle(comm) }) {
            return FlowStrategy.PARALLEL
        }
        return FlowStrategy.SEQUENTIAL
    }
    
    override fun canHandle(comm: Comm): Boolean = agentPool.isNotEmpty()
    
    override fun getTools(): List<Tool> = agentPool.flatMap { it.getTools() }
    
    override fun isReady(): Boolean = agentPool.any { it.isReady() }
}

/**
 * 🔄 Flow Executor Interface
 */
interface FlowExecutor {
    suspend fun execute(input: Comm): Comm
}

/**
 * 📊 Agent Execution Result
 */
data class AgentExecutionResult(
    val agentId: String,
    val agentName: String,
    val success: Boolean,
    val processingTime: Long,
    val errorMessage: String? = null
)

/**
 * 🌐 Sequential Flow: Execute agents one by one
 */
class SequentialFlow(
    private val agents: List<Agent>,
    coroutineContext: CoroutineContext = Dispatchers.Default
) : FlowExecutor {
    
    private val scope = CoroutineScope(coroutineContext + SupervisorJob())
    
    override suspend fun execute(input: Comm): Comm {
        if (agents.isEmpty()) {
            return input.reply(
                content = "No agents available for sequential flow",
                from = "sequential-flow",
                type = CommType.ERROR
            )
        }
        
        var current = input
        val executionResults = mutableListOf<AgentExecutionResult>()
        
        for ((index, agent) in agents.withIndex()) {
            try {
                val startTime = System.currentTimeMillis()
                
                if (!agent.canHandle(current)) {
                    executionResults.add(
                        AgentExecutionResult(
                            agentId = agent.id,
                            agentName = agent.name,
                            success = false,
                            processingTime = 0,
                            errorMessage = "Agent cannot handle message"
                        )
                    )
                    continue
                }
                
                val result = withContext(scope.coroutineContext) {
                    agent.processComm(current)
                }
                
                val processingTime = System.currentTimeMillis() - startTime
                
                executionResults.add(
                    AgentExecutionResult(
                        agentId = agent.id,
                        agentName = agent.name,
                        success = true,
                        processingTime = processingTime
                    )
                )
                
                // Use result as input for next agent
                current = result
                
            } catch (e: Exception) {
                executionResults.add(
                    AgentExecutionResult(
                        agentId = agent.id,
                        agentName = agent.name,
                        success = false,
                        processingTime = 0,
                        errorMessage = e.message
                    )
                )
                
                // Continue with original message if error
                // Could also stop here depending on requirements
            }
        }
        
        // Add execution metadata  
        return current.copy(
            data = current.data + mapOf(
                "flow_type" to "sequential",
                "agents_executed" to executionResults.size.toString(),
                "execution_results" to executionResults.joinToString { it.agentName }
            )
        )
    }
    
    fun close() {
        scope.cancel()
    }
}

/**
 * 🏗️ Extension Functions for Agent List Operations
 */

/**
 * Convert list of agents to MultiAgentFlow
 */
fun List<Agent>.toMultiAgentFlow(strategy: FlowStrategy = FlowStrategy.SEQUENTIAL): MultiAgentFlow {
    val flow = MultiAgentFlow(defaultStrategy = strategy)
    forEach { flow.addAgent(it) }
    return flow
}

/**
 * Convert list of agents to SwarmAgent
 */
fun List<Agent>.toSwarmAgent(
    id: String = "swarm-${System.currentTimeMillis()}",
    name: String = "Agent Swarm",
    description: String = "Coordinated agent swarm"
): SwarmAgent {
    val swarm = SwarmAgent(id, name, description)
    forEach { swarm.addToPool(it) }
    return swarm
}

/**
 * Sequential agent chain: A → B → C
 */
operator fun Agent.plus(other: Agent): MultiAgentFlow {
    return MultiAgentFlow(defaultStrategy = FlowStrategy.SEQUENTIAL).apply {
        addAgent(this@plus)
        addAgent(other)
    }
}

/**
 * Parallel agent execution: A || B
 */
infix fun Agent.parallelWith(other: Agent): MultiAgentFlow {
    return MultiAgentFlow(defaultStrategy = FlowStrategy.PARALLEL).apply {
        addAgent(this@parallelWith)
        addAgent(other)
    }
}

/**
 * Competition between agents: A vs B
 */
infix fun Agent.competesWith(other: Agent): MultiAgentFlow {
    return MultiAgentFlow(defaultStrategy = FlowStrategy.COMPETITION).apply {
        addAgent(this@competesWith)
        addAgent(other)
    }
}

/**
 * 🎯 Convenience Functions for Common Flow Patterns
 */

/**
 * Create a sequential flow
 */
fun sequentialFlow(vararg agents: Agent): MultiAgentFlow = 
    MultiAgentFlow(defaultStrategy = FlowStrategy.SEQUENTIAL).addAgents(*agents)

/**
 * Create a parallel flow
 */
fun parallelFlow(vararg agents: Agent): MultiAgentFlow = 
    MultiAgentFlow(defaultStrategy = FlowStrategy.PARALLEL).addAgents(*agents)

/**
 * Create a competition flow
 */
fun competitionFlow(vararg agents: Agent): MultiAgentFlow = 
    MultiAgentFlow(defaultStrategy = FlowStrategy.COMPETITION).addAgents(*agents)

/**
 * Create a pipeline flow
 */
fun pipelineFlow(vararg agents: Agent): MultiAgentFlow = 
    MultiAgentFlow(defaultStrategy = FlowStrategy.PIPELINE).addAgents(*agents) 