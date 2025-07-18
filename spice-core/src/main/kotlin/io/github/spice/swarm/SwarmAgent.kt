package io.github.spice.swarm

import io.github.spice.*
import io.github.spice.dsl.*
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

/**
 * 🤖 SwarmAgent 2.0 - Revolutionary Multi-Agent Coordination System
 * 
 * A sophisticated coordinator that manages multiple specialized agents
 * to solve complex problems through collaboration and consensus.
 */
class SwarmAgent(
    override val id: String,
    override val name: String,
    override val description: String,
    private val memberAgents: Map<String, Agent>,
    private val coordinator: SwarmCoordinator,
    private val config: SwarmConfig = SwarmConfig()
) : Agent {
    
    override val capabilities: List<String> = 
        listOf("swarm-coordination", "multi-agent-collaboration", "consensus-building") +
        memberAgents.values.flatMap { it.capabilities }.distinct()
    
    // Swarm state tracking
    private val activeOperations = ConcurrentHashMap<String, SwarmOperation>()
    private val results = ConcurrentHashMap<String, SwarmResult>()
    
    override suspend fun processComm(comm: Comm): Comm {
        val operationId = "swarm-${System.currentTimeMillis()}"
        
        return try {
            if (config.debugEnabled) {
                println("[SWARM] ${this.name} handling comm: ${comm.content}")
            }
            
            // Analyze task and determine strategy
            val strategy = coordinator.analyzeTask(comm.content)
            
            // Execute swarm operation
            val operation = SwarmOperation(
                id = operationId,
                originalComm = comm,
                strategy = strategy,
                startTime = System.currentTimeMillis()
            )
            
            activeOperations[operationId] = operation
            
            val result = when (strategy.type) {
                SwarmStrategyType.PARALLEL -> executeParallel(strategy, comm)
                SwarmStrategyType.SEQUENTIAL -> executeSequential(strategy, comm)
                SwarmStrategyType.CONSENSUS -> executeConsensus(strategy, comm)
                SwarmStrategyType.COMPETITION -> executeCompetition(strategy, comm)
                SwarmStrategyType.HIERARCHICAL -> executeHierarchical(strategy, comm)
            }
            
            results[operationId] = result
            activeOperations.remove(operationId)
            
            comm.reply(
                content = result.content,
                from = id,
                data = mapOf(
                    "swarm_operation_id" to operationId,
                    "strategy_type" to strategy.type.name,
                    "participating_agents" to strategy.selectedAgents.joinToString(","),
                    "execution_time_ms" to (System.currentTimeMillis() - operation.startTime).toString(),
                    "success_rate" to result.successRate.toString()
                )
            )
            
        } catch (e: Exception) {
            if (config.debugEnabled) {
                println("[SWARM] Error in operation $operationId: ${e.message}")
            }
            
            activeOperations.remove(operationId)
            
            comm.reply(
                content = "🤖 Swarm operation failed: ${e.message}",
                from = id,
                data = mapOf("error" to "swarm_execution_failed")
            )
        }
    }
    
    /**
     * Execute agents in parallel and aggregate results
     */
    private suspend fun executeParallel(strategy: SwarmStrategy, comm: Comm): SwarmResult {
        if (config.debugEnabled) {
            println("[SWARM] Executing PARALLEL strategy with ${strategy.selectedAgents.size} agents")
        }
        
        val jobs = strategy.selectedAgents.map { agentId ->
            coroutineScope {
                async {
                    val agent = memberAgents[agentId]
                        ?: return@async AgentResult(agentId, false, "Agent not found")
                    
                    try {
                        val result = agent.processComm(comm)
                        AgentResult(agentId, true, result.content, result.data)
                    } catch (e: Exception) {
                        AgentResult(agentId, false, "Error: ${e.message}")
                    }
                }
            }
        }
        
        val agentResults = jobs.map { it.await() }
        return coordinator.aggregateResults(agentResults, strategy)
    }
    
    /**
     * Execute agents sequentially, passing results forward
     */
    private suspend fun executeSequential(strategy: SwarmStrategy, comm: Comm): SwarmResult {
        if (config.debugEnabled) {
            println("[SWARM] Executing SEQUENTIAL strategy with ${strategy.selectedAgents.size} agents")
        }
        
        var currentComm = comm
        val agentResults = mutableListOf<AgentResult>()
        
        for (agentId in strategy.selectedAgents) {
            val agent = memberAgents[agentId]
                ?: return SwarmResult("Agent $agentId not found", 0.0, agentResults)
            
            try {
                val result = agent.processComm(currentComm)
                val agentResult = AgentResult(agentId, true, result.content, result.data)
                agentResults.add(agentResult)
                
                // Create new comm with accumulated context
                currentComm = Comm(
                    id = "sequential-${System.currentTimeMillis()}",
                    content = result.content,
                    from = agentId,
                    to = result.to,
                    type = CommType.TEXT,
                    data = result.data
                )
                
            } catch (e: Exception) {
                agentResults.add(AgentResult(agentId, false, "Error: ${e.message}"))
                break // Stop on first failure
            }
        }
        
        return coordinator.aggregateResults(agentResults, strategy)
    }
    
    /**
     * Execute consensus-building among agents
     */
    private suspend fun executeConsensus(strategy: SwarmStrategy, comm: Comm): SwarmResult {
        if (config.debugEnabled) {
            println("[SWARM] Executing CONSENSUS strategy with ${strategy.selectedAgents.size} agents")
        }
        
        // Phase 1: Initial responses
        val initialResults = executeParallel(strategy, comm)
        
        // Phase 2: Cross-evaluation and refinement
        val refinedResults = mutableListOf<AgentResult>()
        
        for (agentId in strategy.selectedAgents) {
            val agent = memberAgents[agentId] ?: continue
            
            val consensusComm = Comm(
                id = "consensus-${System.currentTimeMillis()}",
                content = "Based on these perspectives: ${initialResults.agentResults.joinToString { it.content }}, what is your refined view on: ${comm.content}",
                from = "swarm-coordinator",
                to = agentId,
                type = CommType.TEXT
            )
            
            try {
                val refinedResult = agent.processComm(consensusComm)
                refinedResults.add(AgentResult(agentId, true, refinedResult.content))
            } catch (e: Exception) {
                refinedResults.add(AgentResult(agentId, false, "Consensus error: ${e.message}"))
            }
        }
        
        return coordinator.buildConsensus(refinedResults, strategy)
    }
    
    /**
     * Execute competition between agents (best result wins)
     */
    private suspend fun executeCompetition(strategy: SwarmStrategy, comm: Comm): SwarmResult {
        if (config.debugEnabled) {
            println("[SWARM] Executing COMPETITION strategy with ${strategy.selectedAgents.size} agents")
        }
        
        val results = executeParallel(strategy, comm)
        return coordinator.selectBestResult(results, strategy)
    }
    
    /**
     * Execute hierarchical delegation (coordinator → specialists → experts)
     */
    private suspend fun executeHierarchical(strategy: SwarmStrategy, comm: Comm): SwarmResult {
        if (config.debugEnabled) {
            println("[SWARM] Executing HIERARCHICAL strategy with ${strategy.selectedAgents.size} agents")
        }
        
        val hierarchy = strategy.agentHierarchy ?: return executeParallel(strategy, comm)
        
        var currentResults = listOf(AgentResult("coordinator", true, comm.content))
        
        for (level in hierarchy) {
            val levelResults = mutableListOf<AgentResult>()
            
            for (agentId in level) {
                val agent = memberAgents[agentId] ?: continue
                
                // Create context from previous level
                val contextComm = Comm(
                    id = "hierarchical-${System.currentTimeMillis()}",
                    content = "Context: ${currentResults.joinToString { it.content }}\n\nTask: ${comm.content}",
                    from = "swarm-hierarchy",
                    to = agentId,
                    type = CommType.TEXT
                )
                
                try {
                    val result = agent.processComm(contextComm)
                    levelResults.add(AgentResult(agentId, true, result.content))
                } catch (e: Exception) {
                    levelResults.add(AgentResult(agentId, false, "Hierarchy error: ${e.message}"))
                }
            }
            
            currentResults = levelResults
        }
        
        return coordinator.aggregateResults(currentResults, strategy)
    }
    
    override fun getTools(): List<Tool> {
        // Aggregate all tools from member agents
        return memberAgents.values.flatMap { it.getTools() }.distinctBy { it.name }
    }
    
    override fun canHandle(comm: Comm): Boolean = true // Swarm can handle any comm
    
    override fun isReady(): Boolean {
        return memberAgents.values.all { it.isReady() }
    }
    
    /**
     * Get swarm status and statistics
     */
    fun getSwarmStatus(): SwarmStatus {
        return SwarmStatus(
            totalAgents = memberAgents.size,
            readyAgents = memberAgents.values.count { it.isReady() },
            activeOperations = activeOperations.size,
            completedOperations = results.size,
            averageSuccessRate = if (results.isEmpty()) 0.0 else results.values.map { it.successRate }.average()
        )
    }
    
    /**
     * Get detailed operation history
     */
    fun getOperationHistory(): List<SwarmResult> {
        return results.values.toList().sortedByDescending { it.timestamp }
    }
}

/**
 * 🧠 Swarm Coordinator - Brain of the swarm operation
 */
interface SwarmCoordinator {
    suspend fun analyzeTask(task: String): SwarmStrategy
    suspend fun aggregateResults(results: List<AgentResult>, strategy: SwarmStrategy): SwarmResult
    suspend fun buildConsensus(results: List<AgentResult>, strategy: SwarmStrategy): SwarmResult
    suspend fun selectBestResult(results: SwarmResult, strategy: SwarmStrategy): SwarmResult
}

/**
 * 🎯 Default Smart Coordinator Implementation
 */
class SmartSwarmCoordinator(
    private val memberAgents: Map<String, Agent>,
    private val config: SwarmConfig = SwarmConfig()
) : SwarmCoordinator {
    
    override suspend fun analyzeTask(task: String): SwarmStrategy {
        // Smart task analysis - determine best strategy and agents
        val taskType = classifyTask(task)
        val selectedAgents = selectOptimalAgents(task, taskType)
        
        val strategyType = when {
            task.contains("compare") || task.contains("evaluate") -> SwarmStrategyType.CONSENSUS
            task.contains("best") || task.contains("compete") -> SwarmStrategyType.COMPETITION
            task.contains("step") || task.contains("sequence") -> SwarmStrategyType.SEQUENTIAL
            task.contains("hierarchy") || task.contains("delegate") -> SwarmStrategyType.HIERARCHICAL
            else -> SwarmStrategyType.PARALLEL
        }
        
        return SwarmStrategy(
            type = strategyType,
            selectedAgents = selectedAgents,
            confidence = calculateConfidence(task, selectedAgents),
            agentHierarchy = if (strategyType == SwarmStrategyType.HIERARCHICAL) createHierarchy(selectedAgents) else null
        )
    }
    
    override suspend fun aggregateResults(results: List<AgentResult>, strategy: SwarmStrategy): SwarmResult {
        val successfulResults = results.filter { it.success }
        val successRate = if (results.isEmpty()) 0.0 else successfulResults.size.toDouble() / results.size
        
        val aggregatedContent = when (strategy.type) {
            SwarmStrategyType.PARALLEL -> {
                "🤖 Swarm Analysis (${successfulResults.size}/${results.size} agents):\n\n" +
                successfulResults.mapIndexed { index, result ->
                    "Agent ${result.agentId}: ${result.content}"
                }.joinToString("\n\n")
            }
            SwarmStrategyType.SEQUENTIAL -> {
                "🔄 Sequential Processing Result:\n\n${successfulResults.lastOrNull()?.content ?: "No successful results"}"
            }
            else -> {
                "🎯 Swarm Result: ${successfulResults.joinToString(" | ") { it.content }}"
            }
        }
        
        return SwarmResult(
            content = aggregatedContent,
            successRate = successRate,
            agentResults = results,
            timestamp = System.currentTimeMillis()
        )
    }
    
    override suspend fun buildConsensus(results: List<AgentResult>, strategy: SwarmStrategy): SwarmResult {
        val successfulResults = results.filter { it.success }
        
        // Simple consensus: find common themes and agreements
        val consensusContent = if (successfulResults.isEmpty()) {
            "🤖 No consensus reached - insufficient agent responses"
        } else {
            val commonTerms = findCommonTerms(successfulResults.map { it.content })
            "🤝 Swarm Consensus:\n\nBased on ${successfulResults.size} agent perspectives, the consensus is:\n\n" +
            "Key agreements: ${commonTerms.joinToString(", ")}\n\n" +
            "Detailed perspectives:\n${successfulResults.mapIndexed { i, r -> "${i+1}. ${r.content}" }.joinToString("\n")}"
        }
        
        return SwarmResult(
            content = consensusContent,
            successRate = successfulResults.size.toDouble() / results.size,
            agentResults = results,
            timestamp = System.currentTimeMillis()
        )
    }
    
    override suspend fun selectBestResult(results: SwarmResult, strategy: SwarmStrategy): SwarmResult {
        val best = results.agentResults
            .filter { it.success }
            .maxByOrNull { it.content.length } // Simple metric: longest response
        
        val content = best?.let {
            "🏆 Competition Winner (Agent ${it.agentId}):\n\n${it.content}"
        } ?: "🤖 No winner in competition"
        
        return SwarmResult(
            content = content,
            successRate = results.successRate,
            agentResults = results.agentResults,
            timestamp = System.currentTimeMillis()
        )
    }
    
    private fun classifyTask(task: String): TaskType {
        return when {
            task.contains("analyze") || task.contains("research") -> TaskType.ANALYSIS
            task.contains("create") || task.contains("generate") -> TaskType.CREATIVE
            task.contains("solve") || task.contains("calculate") -> TaskType.PROBLEM_SOLVING
            task.contains("decide") || task.contains("choose") -> TaskType.DECISION_MAKING
            else -> TaskType.GENERAL
        }
    }
    
    private fun selectOptimalAgents(task: String, taskType: TaskType): List<String> {
        // Smart agent selection based on capabilities and task
        return memberAgents.keys.toList() // For now, select all agents
    }
    
    private fun calculateConfidence(task: String, selectedAgents: List<String>): Double {
        // Calculate confidence based on task complexity and agent capabilities
        return if (selectedAgents.isNotEmpty()) 0.8 else 0.0
    }
    
    private fun createHierarchy(agents: List<String>): List<List<String>> {
        // Create simple 2-level hierarchy
        return if (agents.size <= 2) {
            listOf(agents)
        } else {
            listOf(
                agents.take(agents.size / 2),
                agents.drop(agents.size / 2)
            )
        }
    }
    
    private fun findCommonTerms(contents: List<String>): List<String> {
        // Simple common term extraction
        val words = contents.flatMap { it.lowercase().split(" ") }
        return words.groupingBy { it }.eachCount()
            .filterValues { it > 1 }
            .keys.take(5)
            .toList()
    }
}

// =====================================
// DATA CLASSES
// =====================================

/**
 * 🎯 Swarm Strategy Configuration
 */
data class SwarmStrategy(
    val type: SwarmStrategyType,
    val selectedAgents: List<String>,
    val confidence: Double,
    val agentHierarchy: List<List<String>>? = null
)

/**
 * 🤖 Individual Agent Result
 */
data class AgentResult(
    val agentId: String,
    val success: Boolean,
    val content: String,
    val data: Map<String, String> = emptyMap()
)

/**
 * 🎯 Final Swarm Result
 */
data class SwarmResult(
    val content: String,
    val successRate: Double,
    val agentResults: List<AgentResult>,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 📊 Swarm Status Information
 */
data class SwarmStatus(
    val totalAgents: Int,
    val readyAgents: Int,
    val activeOperations: Int,
    val completedOperations: Int,
    val averageSuccessRate: Double
)

/**
 * ⚙️ Swarm Configuration
 */
data class SwarmConfig(
    val debugEnabled: Boolean = false,
    val maxConcurrentOperations: Int = 10,
    val timeoutMs: Long = 30000,
    val retryAttempts: Int = 3
)

/**
 * 🔄 Swarm Operation Tracking
 */
data class SwarmOperation(
    val id: String,
    val originalComm: Comm,
    val strategy: SwarmStrategy,
    val startTime: Long
)

/**
 * 🎯 Strategy Types
 */
enum class SwarmStrategyType {
    PARALLEL,       // Execute all agents simultaneously
    SEQUENTIAL,     // Execute agents in sequence, passing results forward
    CONSENSUS,      // Build consensus through multi-round discussion
    COMPETITION,    // Compete and select best result
    HIERARCHICAL    // Hierarchical delegation with levels
}

/**
 * 🧠 Task Classification
 */
enum class TaskType {
    ANALYSIS,
    CREATIVE,
    PROBLEM_SOLVING,
    DECISION_MAKING,
    GENERAL
} 