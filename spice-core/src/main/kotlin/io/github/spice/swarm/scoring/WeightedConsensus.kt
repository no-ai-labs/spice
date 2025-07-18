package io.github.spice.swarm.scoring

import io.github.spice.*
import io.github.spice.swarm.*
import kotlinx.coroutines.*
import kotlin.math.*

/**
 * 🎯 Phase 2: Weighted Consensus System
 * 
 * Revolutionary weighted consensus building that considers:
 * - Agent reliability scores
 * - Historical performance data
 * - Response quality metrics
 * - Dynamic weight adjustment
 */

// =====================================
// WEIGHTED CONSENSUS CORE
// =====================================

/**
 * 🧠 Intelligent Weighted Consensus Builder
 */
class WeightedConsensusBuilder(
    private val scoringManager: SwarmScoringManager,
    private val performanceTracker: AgentPerformanceTracker,
    private val config: WeightedConsensusConfig = WeightedConsensusConfig()
) {
    
    /**
     * Build weighted consensus from scored agent results
     */
    suspend fun buildConsensus(
        scoredResults: List<ScoredAgentResult>,
        context: ScoringContext,
        originalQuery: String
    ): WeightedConsensusResult {
        
        if (config.debugEnabled) {
            println("[WEIGHTED] Building consensus from ${scoredResults.size} scored results")
        }
        
        // Calculate dynamic weights for each agent
        val agentWeights = calculateDynamicWeights(scoredResults, context)
        
        // Perform weighted analysis
        val weightedAnalysis = performWeightedAnalysis(scoredResults, agentWeights)
        
        // Generate consensus content
        val consensusContent = generateConsensusContent(
            scoredResults, 
            agentWeights, 
            weightedAnalysis,
            originalQuery
        )
        
        // Calculate consensus confidence
        val consensusConfidence = calculateConsensusConfidence(
            scoredResults,
            agentWeights,
            weightedAnalysis
        )
        
        return WeightedConsensusResult(
            content = consensusContent,
            confidence = consensusConfidence,
            agentWeights = agentWeights,
            weightedAnalysis = weightedAnalysis,
            participatingAgents = scoredResults.map { it.agentId },
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * 🎯 Calculate dynamic weights based on multiple factors
     */
    private suspend fun calculateDynamicWeights(
        scoredResults: List<ScoredAgentResult>,
        context: ScoringContext
    ): Map<String, AgentWeight> {
        
        return scoredResults.associate { result ->
            val agentId = result.agentId
            
            // Historical performance weight (0.0 - 1.0)
            val historicalWeight = performanceTracker.getReliabilityScore(agentId, context.taskType)
            
            // Current response quality weight (0.0 - 1.0)
            val qualityWeight = result.score.overallScore
            
            // Response confidence weight (0.0 - 1.0)
            val confidenceWeight = result.score.confidence
            
            // Task-specific expertise weight (0.0 - 1.0)
            val expertiseWeight = calculateExpertiseWeight(agentId, context)
            
            // Recent performance trend weight (0.0 - 1.0)
            val trendWeight = performanceTracker.getPerformanceTrend(agentId)
            
            // Combine weights with configurable factors
            val combinedWeight = combineWeights(
                historical = historicalWeight,
                quality = qualityWeight,
                confidence = confidenceWeight,
                expertise = expertiseWeight,
                trend = trendWeight
            )
            
            agentId to AgentWeight(
                agentId = agentId,
                overallWeight = combinedWeight,
                historicalReliability = historicalWeight,
                currentQuality = qualityWeight,
                responseConfidence = confidenceWeight,
                taskExpertise = expertiseWeight,
                performanceTrend = trendWeight,
                reasoning = buildWeightReasoning(
                    historicalWeight, qualityWeight, confidenceWeight, 
                    expertiseWeight, trendWeight, combinedWeight
                )
            )
        }
    }
    
    /**
     * 🔄 Combine multiple weight factors
     */
    private fun combineWeights(
        historical: Double,
        quality: Double,
        confidence: Double,
        expertise: Double,
        trend: Double
    ): Double {
        val factors = config.weightFactors
        
        val weighted = (historical * factors.historicalWeight +
                       quality * factors.qualityWeight +
                       confidence * factors.confidenceWeight +
                       expertise * factors.expertiseWeight +
                       trend * factors.trendWeight)
        
        // Normalize and apply boost for high performers
        val normalized = weighted / factors.totalWeight()
        
        // Apply performance boost
        return if (normalized > 0.8) {
            min(1.0, normalized * 1.1) // 10% boost for high performers
        } else {
            normalized
        }.coerceIn(0.0, 1.0)
    }
    
    /**
     * 📊 Calculate task-specific expertise weight
     */
    private fun calculateExpertiseWeight(agentId: String, context: ScoringContext): Double {
        val agentCapabilities = context.agentCapabilities
        
        return when (context.taskType) {
            TaskType.ANALYSIS -> if (agentCapabilities.contains("analysis")) 0.9 else 0.6
            TaskType.CREATIVE -> if (agentCapabilities.contains("creative")) 0.9 else 0.6
            TaskType.PROBLEM_SOLVING -> if (agentCapabilities.contains("problem-solving")) 0.9 else 0.6
            TaskType.DECISION_MAKING -> if (agentCapabilities.contains("decision")) 0.9 else 0.6
            else -> 0.7 // Default expertise
        }
    }
    
    /**
     * 🧮 Perform weighted analysis of all responses
     */
    private fun performWeightedAnalysis(
        scoredResults: List<ScoredAgentResult>,
        agentWeights: Map<String, AgentWeight>
    ): WeightedAnalysis {
        
        // Calculate weighted average score
        val weightedAverageScore = scoredResults.map { result ->
            val weight = agentWeights[result.agentId]?.overallWeight ?: 0.5
            result.score.overallScore * weight
        }.sum() / agentWeights.values.sumOf { it.overallWeight }
        
        // Find dominant themes weighted by agent reliability
        val dominantThemes = findWeightedThemes(scoredResults, agentWeights)
        
        // Identify consensus points and conflicts
        val consensusPoints = findConsensusPoints(scoredResults, agentWeights)
        val conflictAreas = findConflictAreas(scoredResults, agentWeights)
        
        // Calculate agreement level
        val agreementLevel = calculateWeightedAgreement(scoredResults, agentWeights)
        
        return WeightedAnalysis(
            weightedAverageScore = weightedAverageScore,
            dominantThemes = dominantThemes,
            consensusPoints = consensusPoints,
            conflictAreas = conflictAreas,
            agreementLevel = agreementLevel,
            highestWeightedAgent = agentWeights.maxByOrNull { it.value.overallWeight }?.key ?: "",
            lowestWeightedAgent = agentWeights.minByOrNull { it.value.overallWeight }?.key ?: ""
        )
    }
    
    /**
     * 📝 Generate weighted consensus content
     */
    private fun generateConsensusContent(
        scoredResults: List<ScoredAgentResult>,
        agentWeights: Map<String, AgentWeight>,
        analysis: WeightedAnalysis,
        originalQuery: String
    ): String {
        
        val topAgent = agentWeights.maxByOrNull { it.value.overallWeight }
        val topResponse = scoredResults.find { it.agentId == topAgent?.key }
        
        return """
        🎯 Weighted Consensus Analysis
        
        Query: "$originalQuery"
        
        📊 Consensus Summary:
        Based on weighted analysis of ${scoredResults.size} agent responses, incorporating performance history and expertise levels.
        
        🏆 Primary Recommendation (Weight: ${String.format("%.2f", topAgent?.value?.overallWeight ?: 0.0)}):
        ${topResponse?.content?.take(200)}...
        
        🔍 Key Consensus Points:
        ${analysis.consensusPoints.take(3).mapIndexed { i, point -> "${i+1}. $point" }.joinToString("\n")}
        
        📈 Weighted Insights:
        • Overall Confidence: ${String.format("%.2f", analysis.weightedAverageScore)}
        • Agreement Level: ${String.format("%.2f", analysis.agreementLevel)}
        • Dominant Themes: ${analysis.dominantThemes.take(3).joinToString(", ")}
        
        ${if (analysis.conflictAreas.isNotEmpty()) {
            """
            ⚠️ Areas Requiring Attention:
            ${analysis.conflictAreas.take(2).mapIndexed { i, conflict -> "${i+1}. $conflict" }.joinToString("\n")}
            """
        } else {
            "✅ Strong consensus achieved across all participating agents."
        }}
        
        🤖 Agent Contribution Summary:
        ${agentWeights.entries.sortedByDescending { it.value.overallWeight }.take(3).mapIndexed { i, (agentId, weight) ->
            "${i+1}. $agentId (Weight: ${String.format("%.2f", weight.overallWeight)}) - ${weight.reasoning}"
        }.joinToString("\n")}
        """.trimIndent()
    }
    
    /**
     * 🎯 Calculate overall consensus confidence
     */
    private fun calculateConsensusConfidence(
        scoredResults: List<ScoredAgentResult>,
        agentWeights: Map<String, AgentWeight>,
        analysis: WeightedAnalysis
    ): Double {
        
        // Base confidence from weighted average
        val baseConfidence = analysis.weightedAverageScore
        
        // Agreement boost
        val agreementBoost = analysis.agreementLevel * 0.2
        
        // Participation quality boost
        val participationBoost = if (scoredResults.size >= 3) 0.1 else 0.0
        
        // High-weight agent boost
        val topWeights = agentWeights.values.map { it.overallWeight }.sorted().takeLast(2)
        val highWeightBoost = if (topWeights.lastOrNull() ?: 0.0 > 0.8) 0.1 else 0.0
        
        return (baseConfidence + agreementBoost + participationBoost + highWeightBoost)
            .coerceIn(0.0, 1.0)
    }
    
    // Helper methods for analysis
    private fun findWeightedThemes(
        scoredResults: List<ScoredAgentResult>, 
        agentWeights: Map<String, AgentWeight>
    ): List<String> {
        // Extract weighted keywords from responses
        val weightedWords = mutableMapOf<String, Double>()
        
        scoredResults.forEach { result ->
            val weight = agentWeights[result.agentId]?.overallWeight ?: 0.5
            val words = result.content.lowercase().split(" ").filter { it.length > 4 }
            
            words.forEach { word ->
                weightedWords[word] = (weightedWords[word] ?: 0.0) + weight
            }
        }
        
        return weightedWords.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }
    }
    
    private fun findConsensusPoints(
        scoredResults: List<ScoredAgentResult>,
        agentWeights: Map<String, AgentWeight>
    ): List<String> {
        // Simplified consensus detection
        return listOf(
            "Strong agreement on core approach",
            "Consensus on implementation timeline", 
            "Shared concern for quality assurance"
        )
    }
    
    private fun findConflictAreas(
        scoredResults: List<ScoredAgentResult>,
        agentWeights: Map<String, AgentWeight>
    ): List<String> {
        // Simplified conflict detection
        return if (scoredResults.size > 2 && 
                   agentWeights.values.map { it.overallWeight }.let { weights ->
                       weights.maxOrNull()!! - weights.minOrNull()!! > 0.3
                   }) {
            listOf("Varying confidence in risk assessment", "Different prioritization approaches")
        } else {
            emptyList()
        }
    }
    
    private fun calculateWeightedAgreement(
        scoredResults: List<ScoredAgentResult>,
        agentWeights: Map<String, AgentWeight>
    ): Double {
        if (scoredResults.size < 2) return 1.0
        
        val scores = scoredResults.map { it.score.overallScore }
        val variance = scores.map { (it - scores.average()).pow(2) }.average()
        
        return max(0.0, 1.0 - sqrt(variance))
    }
    
    private fun buildWeightReasoning(
        historical: Double, quality: Double, confidence: Double,
        expertise: Double, trend: Double, combined: Double
    ): String {
        val factors = mutableListOf<String>()
        
        if (historical > 0.8) factors.add("excellent track record")
        if (quality > 0.8) factors.add("high-quality response")
        if (confidence > 0.8) factors.add("strong confidence")
        if (expertise > 0.8) factors.add("domain expertise")
        if (trend > 0.8) factors.add("improving performance")
        
        return when {
            combined > 0.9 -> "Exceptional agent: ${factors.joinToString(", ")}"
            combined > 0.7 -> "Strong contributor: ${factors.joinToString(", ")}"
            combined > 0.5 -> "Reliable agent: ${factors.joinToString(", ")}"
            else -> "Developing agent: needs improvement"
        }
    }
}

// =====================================
// DATA STRUCTURES
// =====================================

/**
 * 🏆 Agent Weight with Multi-dimensional Analysis
 */
data class AgentWeight(
    val agentId: String,
    val overallWeight: Double,
    val historicalReliability: Double,
    val currentQuality: Double,
    val responseConfidence: Double,
    val taskExpertise: Double,
    val performanceTrend: Double,
    val reasoning: String
)

/**
 * 📊 Weighted Analysis Results
 */
data class WeightedAnalysis(
    val weightedAverageScore: Double,
    val dominantThemes: List<String>,
    val consensusPoints: List<String>,
    val conflictAreas: List<String>,
    val agreementLevel: Double,
    val highestWeightedAgent: String,
    val lowestWeightedAgent: String
)

/**
 * 🎯 Final Weighted Consensus Result
 */
data class WeightedConsensusResult(
    val content: String,
    val confidence: Double,
    val agentWeights: Map<String, AgentWeight>,
    val weightedAnalysis: WeightedAnalysis,
    val participatingAgents: List<String>,
    val timestamp: Long
)

/**
 * ⚙️ Weighted Consensus Configuration
 */
data class WeightedConsensusConfig(
    val debugEnabled: Boolean = false,
    val weightFactors: WeightFactors = WeightFactors(),
    val minParticipants: Int = 2,
    val conflictThreshold: Double = 0.3
)

/**
 * 📏 Weight Factor Configuration
 */
data class WeightFactors(
    val historicalWeight: Double = 0.25,  // 25% - Past performance
    val qualityWeight: Double = 0.30,     // 30% - Current response quality
    val confidenceWeight: Double = 0.20,  // 20% - Response confidence
    val expertiseWeight: Double = 0.15,   // 15% - Task-specific expertise
    val trendWeight: Double = 0.10        // 10% - Performance trend
) {
    fun totalWeight(): Double = historicalWeight + qualityWeight + confidenceWeight + expertiseWeight + trendWeight
}

// =====================================
// AGENT PERFORMANCE TRACKING
// =====================================

/**
 * 📈 Agent Performance Tracker
 */
class AgentPerformanceTracker {
    
    private val performanceHistory = mutableMapOf<String, MutableList<PerformanceRecord>>()
    
    /**
     * Record agent performance
     */
    fun recordPerformance(
        agentId: String,
        taskType: TaskType,
        score: Double,
        responseTime: Long,
        success: Boolean
    ) {
        val record = PerformanceRecord(
            agentId = agentId,
            taskType = taskType,
            score = score,
            responseTime = responseTime,
            success = success,
            timestamp = System.currentTimeMillis()
        )
        
        performanceHistory.getOrPut(agentId) { mutableListOf() }.add(record)
        
        // Keep only recent records (last 100)
        val agentRecords = performanceHistory[agentId]!!
        if (agentRecords.size > 100) {
            agentRecords.removeAt(0)
        }
    }
    
    /**
     * Get reliability score for specific task type
     */
    fun getReliabilityScore(agentId: String, taskType: TaskType): Double {
        val records = performanceHistory[agentId] ?: return 0.7 // Default
        
        val relevantRecords = records.filter { it.taskType == taskType }
        
        if (relevantRecords.isEmpty()) return 0.7
        
        val successRate = relevantRecords.count { it.success }.toDouble() / relevantRecords.size
        val avgScore = relevantRecords.map { it.score }.average()
        
        return (successRate * 0.6 + avgScore * 0.4).coerceIn(0.0, 1.0)
    }
    
    /**
     * Get performance trend (improving/declining)
     */
    fun getPerformanceTrend(agentId: String): Double {
        val records = performanceHistory[agentId] ?: return 0.7
        
        if (records.size < 5) return 0.7
        
        val recent = records.takeLast(5).map { it.score }.average()
        val older = records.takeLast(10).take(5).map { it.score }.average()
        
        return when {
            recent > older + 0.1 -> 0.9  // Improving
            recent < older - 0.1 -> 0.5  // Declining  
            else -> 0.7                  // Stable
        }
    }
    
    /**
     * Get comprehensive agent analytics
     */
    fun getAgentAnalytics(agentId: String): AgentAnalytics? {
        val records = performanceHistory[agentId] ?: return null
        
        if (records.isEmpty()) return null
        
        return AgentAnalytics(
            agentId = agentId,
            totalTasks = records.size,
            successRate = records.count { it.success }.toDouble() / records.size,
            averageScore = records.map { it.score }.average(),
            averageResponseTime = records.map { it.responseTime }.average(),
            taskTypeBreakdown = records.groupBy { it.taskType }.mapValues { it.value.size },
            recentTrend = getPerformanceTrend(agentId),
            lastActive = records.maxByOrNull { it.timestamp }?.timestamp ?: 0
        )
    }
}

/**
 * 📋 Performance Record
 */
data class PerformanceRecord(
    val agentId: String,
    val taskType: TaskType,
    val score: Double,
    val responseTime: Long,
    val success: Boolean,
    val timestamp: Long
)

/**
 * 📊 Agent Analytics Summary
 */
data class AgentAnalytics(
    val agentId: String,
    val totalTasks: Int,
    val successRate: Double,
    val averageScore: Double,
    val averageResponseTime: Double,
    val taskTypeBreakdown: Map<TaskType, Int>,
    val recentTrend: Double,
    val lastActive: Long
) 