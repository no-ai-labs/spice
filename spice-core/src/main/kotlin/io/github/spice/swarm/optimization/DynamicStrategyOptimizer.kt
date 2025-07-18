package io.github.spice.swarm.optimization

import io.github.spice.*
import io.github.spice.swarm.*
import io.github.spice.swarm.scoring.*
import io.github.spice.swarm.analytics.*
import kotlinx.coroutines.*
import kotlin.math.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 🚀 Phase 2: Dynamic Strategy Optimization System
 * 
 * Revolutionary real-time strategy optimization that automatically
 * adjusts SwarmAgent coordination strategies based on:
 * - Live performance metrics
 * - Agent availability and load
 * - Task complexity analysis
 * - Historical success patterns
 * - Environmental conditions
 */

// =====================================
// DYNAMIC STRATEGY OPTIMIZER
// =====================================

/**
 * 🧠 Intelligent Dynamic Strategy Optimizer
 */
class DynamicStrategyOptimizer(
    private val performanceTracker: AgentPerformanceTracker,
    private val config: OptimizerConfig = OptimizerConfig()
) {
    
    private val strategyHistory = ConcurrentHashMap<String, MutableList<StrategyExecutionRecord>>()
    private val realTimeMetrics = ConcurrentHashMap<String, RealtimeMetrics>()
    private val optimizationRules = mutableListOf<OptimizationRule>()
    
    init {
        initializeDefaultRules()
    }
    
    /**
     * 🎯 Optimize strategy for current swarm operation
     */
    suspend fun optimizeStrategy(
        currentStrategy: SwarmStrategy,
        context: OptimizationContext,
        agentStates: Map<String, AgentState>
    ): OptimizedStrategy {
        
        if (config.debugEnabled) {
            println("[OPTIMIZER] Analyzing strategy optimization for ${context.taskType} task")
        }
        
        // Analyze current conditions
        val situationAnalysis = analyzeSituation(context, agentStates)
        
        // Calculate strategy effectiveness scores
        val strategyScores = calculateStrategyScores(situationAnalysis, context)
        
        // Apply optimization rules
        val recommendedStrategy = applyOptimizationRules(
            currentStrategy, 
            strategyScores, 
            situationAnalysis,
            context
        )
        
        // Validate and refine recommendation
        val optimizedStrategy = validateAndRefine(recommendedStrategy, agentStates, context)
        
        // Record optimization decision
        recordOptimizationDecision(currentStrategy, optimizedStrategy, situationAnalysis, context)
        
        return optimizedStrategy
    }
    
    /**
     * 📊 Analyze current situation and conditions
     */
    private suspend fun analyzeSituation(
        context: OptimizationContext,
        agentStates: Map<String, AgentState>
    ): SituationAnalysis {
        
        // Agent availability analysis
        val availableAgents = agentStates.filter { it.value.isAvailable }.keys
        val busyAgents = agentStates.filter { it.value.isBusy }.keys
        val overloadedAgents = agentStates.filter { it.value.isOverloaded }.keys
        
        // Performance analysis
        val agentPerformances = agentStates.mapValues { (agentId, state) ->
            AgentPerformanceSnapshot(
                agentId = agentId,
                currentLoad = state.currentLoad,
                averageResponseTime = performanceTracker.getAgentAnalytics(agentId)?.averageResponseTime ?: 1000.0,
                recentSuccessRate = performanceTracker.getReliabilityScore(agentId, context.taskType),
                trend = performanceTracker.getPerformanceTrend(agentId)
            )
        }
        
        // Task complexity analysis
        val taskComplexity = analyzeTaskComplexity(context)
        
        // System load analysis
        val systemLoad = calculateSystemLoad(agentStates.values)
        
        // Historical success patterns
        val historicalPatterns = analyzeHistoricalPatterns(context.taskType)
        
        return SituationAnalysis(
            availableAgents = availableAgents.toList(),
            busyAgents = busyAgents.toList(),
            overloadedAgents = overloadedAgents.toList(),
            agentPerformances = agentPerformances,
            taskComplexity = taskComplexity,
            systemLoad = systemLoad,
            historicalPatterns = historicalPatterns,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * 🎯 Calculate effectiveness scores for different strategies
     */
    private fun calculateStrategyScores(
        analysis: SituationAnalysis,
        context: OptimizationContext
    ): Map<SwarmStrategyType, Double> {
        
        return SwarmStrategyType.values().associateWith { strategyType ->
            calculateStrategyScore(strategyType, analysis, context)
        }
    }
    
    /**
     * 📈 Calculate score for specific strategy
     */
    private fun calculateStrategyScore(
        strategyType: SwarmStrategyType,
        analysis: SituationAnalysis,
        context: OptimizationContext
    ): Double {
        
        var score = 0.0
        
        when (strategyType) {
            SwarmStrategyType.PARALLEL -> {
                // Parallel works best with many available agents and simple tasks
                score += analysis.availableAgents.size * 0.2
                score += (1.0 - analysis.taskComplexity.complexity) * 0.3
                score += (1.0 - analysis.systemLoad) * 0.2
                score += if (context.urgency > 0.7) 0.3 else 0.1
            }
            
            SwarmStrategyType.SEQUENTIAL -> {
                // Sequential works best for complex tasks requiring coordination
                score += analysis.taskComplexity.complexity * 0.4
                score += analysis.taskComplexity.dependencyLevel * 0.3
                score += if (analysis.availableAgents.size >= 2) 0.2 else 0.0
                score += (1.0 - context.urgency) * 0.1
            }
            
            SwarmStrategyType.CONSENSUS -> {
                // Consensus works best for decision-making with expert agents
                score += analysis.agentPerformances.values
                    .filter { it.recentSuccessRate > 0.8 }
                    .size * 0.25
                score += if (context.taskType == TaskType.DECISION_MAKING) 0.3 else 0.1
                score += (1.0 - analysis.systemLoad) * 0.2
                score += if (analysis.availableAgents.size >= 3) 0.25 else 0.0
            }
            
            SwarmStrategyType.COMPETITION -> {
                // Competition works best when speed and quality are both important
                score += context.urgency * 0.3
                score += context.qualityRequirement * 0.3
                score += if (analysis.availableAgents.size >= 2) 0.2 else 0.0
                score += (1.0 - analysis.systemLoad) * 0.2
            }
            
            SwarmStrategyType.HIERARCHICAL -> {
                // Hierarchical works best for complex, multi-stage tasks
                score += analysis.taskComplexity.complexity * 0.3
                score += analysis.taskComplexity.stages * 0.1
                score += if (analysis.availableAgents.size >= 4) 0.3 else 0.1
                score += (1.0 - context.urgency) * 0.3
            }
        }
        
        // Apply historical success factor
        val historicalFactor = analysis.historicalPatterns[strategyType] ?: 0.5
        score = score * 0.8 + historicalFactor * 0.2
        
        return score.coerceIn(0.0, 1.0)
    }
    
    /**
     * 🔧 Apply optimization rules to refine strategy recommendation
     */
    private fun applyOptimizationRules(
        currentStrategy: SwarmStrategy,
        strategyScores: Map<SwarmStrategyType, Double>,
        analysis: SituationAnalysis,
        context: OptimizationContext
    ): SwarmStrategy {
        
        // Find best scoring strategy
        val bestStrategy = strategyScores.maxByOrNull { it.value }?.key ?: currentStrategy.type
        val bestScore = strategyScores[bestStrategy] ?: 0.5
        
        // Apply optimization rules
        val optimizedType = optimizationRules.fold(bestStrategy) { strategy, rule ->
            rule.apply(strategy, analysis, context, strategyScores)
        }
        
        // Select optimal agents for the strategy
        val selectedAgents = selectOptimalAgents(optimizedType, analysis, context)
        
        // Create agent hierarchy if needed
        val agentHierarchy = if (optimizedType == SwarmStrategyType.HIERARCHICAL) {
            createOptimalHierarchy(selectedAgents, analysis)
        } else {
            null
        }
        
        return SwarmStrategy(
            type = optimizedType,
            selectedAgents = selectedAgents,
            confidence = bestScore,
            agentHierarchy = agentHierarchy
        )
    }
    
    /**
     * 🎯 Select optimal agents for the strategy
     */
    private fun selectOptimalAgents(
        strategyType: SwarmStrategyType,
        analysis: SituationAnalysis,
        context: OptimizationContext
    ): List<String> {
        
        val availableAgents = analysis.availableAgents
        val agentPerformances = analysis.agentPerformances
        
        return when (strategyType) {
            SwarmStrategyType.PARALLEL -> {
                // Select all available high-performing agents
                availableAgents.sortedByDescending { 
                    agentPerformances[it]?.recentSuccessRate ?: 0.0 
                }.take(min(availableAgents.size, config.maxAgentsPerStrategy))
            }
            
            SwarmStrategyType.SEQUENTIAL -> {
                // Select 2-3 complementary agents
                selectComplementaryAgents(availableAgents, agentPerformances, 3)
            }
            
            SwarmStrategyType.CONSENSUS -> {
                // Select 3-5 expert agents
                availableAgents.filter { 
                    agentPerformances[it]?.recentSuccessRate ?: 0.0 > 0.7 
                }.take(5)
            }
            
            SwarmStrategyType.COMPETITION -> {
                // Select top 2-3 performers
                availableAgents.sortedByDescending { 
                    agentPerformances[it]?.recentSuccessRate ?: 0.0 
                }.take(3)
            }
            
            SwarmStrategyType.HIERARCHICAL -> {
                // Select diverse agents for different levels
                selectHierarchicalAgents(availableAgents, agentPerformances)
            }
        }
    }
    
    /**
     * 🏗️ Create optimal agent hierarchy
     */
    private fun createOptimalHierarchy(
        selectedAgents: List<String>,
        analysis: SituationAnalysis
    ): List<List<String>> {
        
        if (selectedAgents.size < 2) return listOf(selectedAgents)
        
        val performances = analysis.agentPerformances
        
        // Sort agents by performance
        val sortedAgents = selectedAgents.sortedByDescending { 
            performances[it]?.recentSuccessRate ?: 0.0 
        }
        
        // Create 2-level hierarchy
        val topTierSize = max(1, selectedAgents.size / 3)
        
        return listOf(
            sortedAgents.take(topTierSize), // Top tier - coordinators
            sortedAgents.drop(topTierSize)  // Worker tier
        )
    }
    
    /**
     * ✅ Validate and refine optimization recommendation
     */
    private fun validateAndRefine(
        recommendedStrategy: SwarmStrategy,
        agentStates: Map<String, AgentState>,
        context: OptimizationContext
    ): OptimizedStrategy {
        
        // Validate agent availability
        val validAgents = recommendedStrategy.selectedAgents.filter { 
            agentStates[it]?.isAvailable == true 
        }
        
        // Ensure minimum agents
        val refinedAgents = if (validAgents.size < 2) {
            // Fallback to any available agents
            agentStates.filter { it.value.isAvailable }.keys.take(2).toList()
        } else {
            validAgents
        }
        
        // Calculate optimization confidence
        val optimizationConfidence = calculateOptimizationConfidence(
            recommendedStrategy, 
            refinedAgents.size,
            context
        )
        
        // Generate optimization reasoning
        val reasoning = generateOptimizationReasoning(
            recommendedStrategy,
            refinedAgents,
            optimizationConfidence,
            context
        )
        
        return OptimizedStrategy(
            strategy = recommendedStrategy.copy(selectedAgents = refinedAgents),
            optimizationConfidence = optimizationConfidence,
            reasoning = reasoning,
            originalStrategy = recommendedStrategy.type,
            optimizationGains = calculateOptimizationGains(recommendedStrategy, context),
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * 📝 Record optimization decision for learning
     */
    private fun recordOptimizationDecision(
        originalStrategy: SwarmStrategy,
        optimizedStrategy: OptimizedStrategy,
        analysis: SituationAnalysis,
        context: OptimizationContext
    ) {
        val record = StrategyExecutionRecord(
            originalStrategyType = originalStrategy.type,
            optimizedStrategyType = optimizedStrategy.strategy.type,
            taskType = context.taskType,
            agentCount = optimizedStrategy.strategy.selectedAgents.size,
            systemLoad = analysis.systemLoad,
            taskComplexity = analysis.taskComplexity.complexity,
            optimizationConfidence = optimizedStrategy.optimizationConfidence,
            timestamp = System.currentTimeMillis(),
            executionResult = null // Will be updated later
        )
        
        val contextKey = "${context.taskType}_${analysis.availableAgents.size}"
        strategyHistory.getOrPut(contextKey) { mutableListOf() }.add(record)
        
        // Keep only recent records
        val records = strategyHistory[contextKey]!!
        if (records.size > config.historyLimit) {
            records.removeAt(0)
        }
    }
    
    // === HELPER METHODS ===
    
    private fun analyzeTaskComplexity(context: OptimizationContext): TaskComplexity {
        // Simplified complexity analysis
        val complexity = when (context.taskType) {
            TaskType.ANALYSIS -> 0.7
            TaskType.CREATIVE -> 0.6
            TaskType.PROBLEM_SOLVING -> 0.8
            TaskType.DECISION_MAKING -> 0.9
            else -> 0.5
        }
        
        return TaskComplexity(
            complexity = complexity,
            dependencyLevel = if (context.hasInterdependencies) 0.8 else 0.3,
            stages = if (context.isMultiStage) 3 else 1,
            timeConstraint = context.urgency
        )
    }
    
    private fun calculateSystemLoad(agentStates: Collection<AgentState>): Double {
        if (agentStates.isEmpty()) return 0.0
        
        val totalLoad = agentStates.sumOf { it.currentLoad }
        return totalLoad / agentStates.size
    }
    
    private fun analyzeHistoricalPatterns(taskType: TaskType): Map<SwarmStrategyType, Double> {
        // Simplified historical analysis
        return mapOf(
            SwarmStrategyType.PARALLEL to 0.6,
            SwarmStrategyType.SEQUENTIAL to 0.7,
            SwarmStrategyType.CONSENSUS to 0.8,
            SwarmStrategyType.COMPETITION to 0.6,
            SwarmStrategyType.HIERARCHICAL to 0.5
        )
    }
    
    private fun selectComplementaryAgents(
        availableAgents: List<String>,
        performances: Map<String, AgentPerformanceSnapshot>,
        maxCount: Int
    ): List<String> {
        return availableAgents.sortedByDescending { 
            performances[it]?.recentSuccessRate ?: 0.0 
        }.take(maxCount)
    }
    
    private fun selectHierarchicalAgents(
        availableAgents: List<String>,
        performances: Map<String, AgentPerformanceSnapshot>
    ): List<String> {
        return availableAgents.sortedByDescending { 
            performances[it]?.recentSuccessRate ?: 0.0 
        }.take(6)
    }
    
    private fun calculateOptimizationConfidence(
        strategy: SwarmStrategy,
        agentCount: Int,
        context: OptimizationContext
    ): Double {
        var confidence = strategy.confidence
        
        // Adjust based on agent count
        confidence *= when {
            agentCount >= 3 -> 1.0
            agentCount == 2 -> 0.8
            else -> 0.6
        }
        
        // Adjust based on urgency
        if (context.urgency > 0.8) confidence *= 0.9
        
        return confidence.coerceIn(0.0, 1.0)
    }
    
    private fun generateOptimizationReasoning(
        strategy: SwarmStrategy,
        agents: List<String>,
        confidence: Double,
        context: OptimizationContext
    ): String {
        return """
        Optimized to ${strategy.type} strategy for ${context.taskType} task.
        Selected ${agents.size} agents based on performance history and availability.
        Optimization confidence: ${String.format("%.2f", confidence)}
        """.trimIndent()
    }
    
    private fun calculateOptimizationGains(
        strategy: SwarmStrategy,
        context: OptimizationContext
    ): OptimizationGains {
        return OptimizationGains(
            expectedSpeedImprovement = 0.15,
            expectedQualityImprovement = 0.10,
            expectedReliabilityImprovement = 0.20,
            resourceEfficiencyGain = 0.12
        )
    }
    
    private fun initializeDefaultRules() {
        // Add default optimization rules
        optimizationRules.add(HighLoadRule())
        optimizationRules.add(UrgencyRule())
        optimizationRules.add(QualityRequirementRule())
        optimizationRules.add(AgentAvailabilityRule())
    }
}

// =====================================
// DATA STRUCTURES
// =====================================

/**
 * 🎯 Optimization Context
 */
data class OptimizationContext(
    val taskType: TaskType,
    val urgency: Double,
    val qualityRequirement: Double,
    val hasInterdependencies: Boolean,
    val isMultiStage: Boolean,
    val resourceConstraints: Map<String, Double> = emptyMap()
)

/**
 * 📊 Agent State
 */
data class AgentState(
    val agentId: String,
    val isAvailable: Boolean,
    val isBusy: Boolean,
    val isOverloaded: Boolean,
    val currentLoad: Double,
    val lastActivity: Long
)

/**
 * 📈 Agent Performance Snapshot
 */
data class AgentPerformanceSnapshot(
    val agentId: String,
    val currentLoad: Double,
    val averageResponseTime: Double,
    val recentSuccessRate: Double,
    val trend: Double
)

/**
 * 🔍 Situation Analysis
 */
data class SituationAnalysis(
    val availableAgents: List<String>,
    val busyAgents: List<String>,
    val overloadedAgents: List<String>,
    val agentPerformances: Map<String, AgentPerformanceSnapshot>,
    val taskComplexity: TaskComplexity,
    val systemLoad: Double,
    val historicalPatterns: Map<SwarmStrategyType, Double>,
    val timestamp: Long
)

/**
 * 🧮 Task Complexity Analysis
 */
data class TaskComplexity(
    val complexity: Double,
    val dependencyLevel: Double,
    val stages: Int,
    val timeConstraint: Double
)

/**
 * 🚀 Optimized Strategy Result
 */
data class OptimizedStrategy(
    val strategy: SwarmStrategy,
    val optimizationConfidence: Double,
    val reasoning: String,
    val originalStrategy: SwarmStrategyType,
    val optimizationGains: OptimizationGains,
    val timestamp: Long
)

/**
 * 📈 Optimization Gains
 */
data class OptimizationGains(
    val expectedSpeedImprovement: Double,
    val expectedQualityImprovement: Double,
    val expectedReliabilityImprovement: Double,
    val resourceEfficiencyGain: Double
)

/**
 * 📋 Strategy Execution Record
 */
data class StrategyExecutionRecord(
    val originalStrategyType: SwarmStrategyType,
    val optimizedStrategyType: SwarmStrategyType,
    val taskType: TaskType,
    val agentCount: Int,
    val systemLoad: Double,
    val taskComplexity: Double,
    val optimizationConfidence: Double,
    val timestamp: Long,
    var executionResult: OptimizationResult? = null
)

/**
 * 📊 Optimization Result
 */
data class OptimizationResult(
    val actualSpeedImprovement: Double,
    val actualQualityImprovement: Double,
    val actualReliabilityImprovement: Double,
    val resourceEfficiency: Double,
    val success: Boolean
)

/**
 * ⚙️ Optimizer Configuration
 */
data class OptimizerConfig(
    val debugEnabled: Boolean = false,
    val maxAgentsPerStrategy: Int = 8,
    val historyLimit: Int = 100,
    val optimizationThreshold: Double = 0.1
)

// =====================================
// OPTIMIZATION RULES
// =====================================

/**
 * 🔧 Optimization Rule Interface
 */
interface OptimizationRule {
    fun apply(
        recommendedStrategy: SwarmStrategyType,
        analysis: SituationAnalysis,
        context: OptimizationContext,
        strategyScores: Map<SwarmStrategyType, Double>
    ): SwarmStrategyType
}

/**
 * ⚡ High Load Rule - Switch to less resource-intensive strategies
 */
class HighLoadRule : OptimizationRule {
    override fun apply(
        recommendedStrategy: SwarmStrategyType,
        analysis: SituationAnalysis,
        context: OptimizationContext,
        strategyScores: Map<SwarmStrategyType, Double>
    ): SwarmStrategyType {
        return if (analysis.systemLoad > 0.8) {
            SwarmStrategyType.SEQUENTIAL // Less resource intensive
        } else {
            recommendedStrategy
        }
    }
}

/**
 * 🚨 Urgency Rule - Prioritize speed for urgent tasks
 */
class UrgencyRule : OptimizationRule {
    override fun apply(
        recommendedStrategy: SwarmStrategyType,
        analysis: SituationAnalysis,
        context: OptimizationContext,
        strategyScores: Map<SwarmStrategyType, Double>
    ): SwarmStrategyType {
        return if (context.urgency > 0.8 && analysis.availableAgents.size >= 2) {
            SwarmStrategyType.PARALLEL // Fastest execution
        } else {
            recommendedStrategy
        }
    }
}

/**
 * 🎯 Quality Requirement Rule - Prioritize quality for critical tasks
 */
class QualityRequirementRule : OptimizationRule {
    override fun apply(
        recommendedStrategy: SwarmStrategyType,
        analysis: SituationAnalysis,
        context: OptimizationContext,
        strategyScores: Map<SwarmStrategyType, Double>
    ): SwarmStrategyType {
        return if (context.qualityRequirement > 0.9 && analysis.availableAgents.size >= 3) {
            SwarmStrategyType.CONSENSUS // Highest quality
        } else {
            recommendedStrategy
        }
    }
}

/**
 * 🤖 Agent Availability Rule - Adapt to agent constraints
 */
class AgentAvailabilityRule : OptimizationRule {
    override fun apply(
        recommendedStrategy: SwarmStrategyType,
        analysis: SituationAnalysis,
        context: OptimizationContext,
        strategyScores: Map<SwarmStrategyType, Double>
    ): SwarmStrategyType {
        return when {
            analysis.availableAgents.size <= 1 -> SwarmStrategyType.SEQUENTIAL
            analysis.availableAgents.size == 2 -> SwarmStrategyType.PARALLEL
            else -> recommendedStrategy
        }
    }
} 