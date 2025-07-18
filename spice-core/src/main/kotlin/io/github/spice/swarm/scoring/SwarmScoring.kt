package io.github.spice.swarm.scoring

import io.github.spice.*
import io.github.spice.swarm.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 🎯 AI-Powered Scoring System for SwarmAgent 3.0
 * 
 * Revolutionary multi-dimensional scoring with AI-based evaluation
 * and intelligent fallback mechanisms.
 */

// =====================================
// SCORING INTERFACES
// =====================================

/**
 * 🧠 Result Scorer Interface
 */
interface ResultScorer {
    suspend fun scoreResult(
        content: String,
        context: ScoringContext,
        criteria: ScoringCriteria
    ): ScoringResult
    
    fun isAvailable(): Boolean
    val name: String
}

/**
 * 📊 Enhanced AgentResult with Scoring
 */
data class ScoredAgentResult(
    val agentId: String,
    val success: Boolean,
    val content: String,
    val score: ScoringResult,
    val metadata: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 🎯 Scoring Result with Multi-dimensional Analysis
 */
data class ScoringResult(
    val overallScore: Double,           // 0.0 - 1.0
    val dimensions: Map<String, Double>, // relevance, accuracy, creativity, etc.
    val confidence: Double,             // 신뢰도
    val reasoning: String,              // AI의 평가 근거
    val scorerUsed: String             // 사용된 스코어러
) {
    companion object {
        fun simple(score: Double, scorer: String = "fallback"): ScoringResult {
            return ScoringResult(
                overallScore = score,
                dimensions = mapOf("overall" to score),
                confidence = 0.5,
                reasoning = "Simple scoring applied",
                scorerUsed = scorer
            )
        }
        
        fun failed(reason: String): ScoringResult {
            return ScoringResult(
                overallScore = 0.0,
                dimensions = emptyMap(),
                confidence = 0.0,
                reasoning = "Scoring failed: $reason",
                scorerUsed = "error"
            )
        }
    }
}

/**
 * ⚙️ Scoring Context
 */
data class ScoringContext(
    val taskType: TaskType,
    val originalQuery: String,
    val agentCapabilities: List<String>,
    val expectedOutputType: String = "text"
)

/**
 * 📏 Scoring Criteria Configuration
 */
data class ScoringCriteria(
    val dimensions: Map<String, Double> = mapOf(
        "relevance" to 0.3,
        "accuracy" to 0.3,
        "creativity" to 0.2,
        "clarity" to 0.2
    ),
    val customInstructions: String = "",
    val minConfidenceThreshold: Double = 0.7
)

// =====================================
// AI-BASED SCORER IMPLEMENTATIONS
// =====================================

/**
 * 🤖 AI-Powered Result Scorer
 */
class AIResultScorer(
    private val scoringAgent: Agent,
    private val config: AIScoringConfig = AIScoringConfig()
) : ResultScorer {
    
    override val name: String = "AI-Scorer(${scoringAgent.name})"
    
    override suspend fun scoreResult(
        content: String,
        context: ScoringContext,
        criteria: ScoringCriteria
    ): ScoringResult {
        return try {
            val prompt = buildScoringPrompt(content, context, criteria)
            val comm = Comm(
                id = "scoring-${System.currentTimeMillis()}",
                content = prompt,
                from = "swarm-scorer",
                to = scoringAgent.id,
                type = CommType.TEXT
            )
            
            val response = scoringAgent.processComm(comm)
            parseScoringResponse(response.content)
            
        } catch (e: Exception) {
            ScoringResult.failed("AI scoring error: ${e.message}")
        }
    }
    
    override fun isAvailable(): Boolean {
        return scoringAgent.isReady()
    }
    
    private fun buildScoringPrompt(
        content: String,
        context: ScoringContext,
        criteria: ScoringCriteria
    ): String {
        return """
        🎯 RESPONSE EVALUATION TASK
        
        Original Query: "${context.originalQuery}"
        Task Type: ${context.taskType}
        
        Response to Evaluate:
        "${content}"
        
        Evaluation Criteria (weights):
        ${criteria.dimensions.map { (dim, weight) -> "- $dim: ${(weight * 100).toInt()}%" }.joinToString("\n")}
        
        Additional Instructions: ${criteria.customInstructions}
        
        Please evaluate this response and provide scores in the following JSON format:
        {
          "overall_score": 0.85,
          "dimensions": {
            "relevance": 0.9,
            "accuracy": 0.8,
            "creativity": 0.7,
            "clarity": 0.9
          },
          "confidence": 0.88,
          "reasoning": "Detailed explanation of the scoring rationale..."
        }
        
        Focus on objective analysis and provide constructive feedback.
        """.trimIndent()
    }
    
    private fun parseScoringResponse(response: String): ScoringResult {
        return try {
            // Simple JSON-like parsing (in real implementation, use proper JSON parser)
            val overallScore = extractScore(response, "overall_score") ?: 0.5
            val confidence = extractScore(response, "confidence") ?: 0.7
            val reasoning = extractReasoning(response) ?: "AI evaluation completed"
            
            val dimensions = mapOf(
                "relevance" to (extractScore(response, "relevance") ?: 0.5),
                "accuracy" to (extractScore(response, "accuracy") ?: 0.5),
                "creativity" to (extractScore(response, "creativity") ?: 0.5),
                "clarity" to (extractScore(response, "clarity") ?: 0.5)
            )
            
            ScoringResult(
                overallScore = overallScore,
                dimensions = dimensions,
                confidence = confidence,
                reasoning = reasoning,
                scorerUsed = name
            )
        } catch (e: Exception) {
            ScoringResult.simple(0.5, "ai-parser-fallback")
        }
    }
    
    private fun extractScore(text: String, field: String): Double? {
        val pattern = "\"$field\"\\s*:\\s*([0-9.]+)".toRegex()
        return pattern.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
    }
    
    private fun extractReasoning(text: String): String? {
        val pattern = "\"reasoning\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        return pattern.find(text)?.groupValues?.get(1)
    }
}

/**
 * 📊 Multi-dimensional Fallback Scorer
 */
class MultidimensionalScorer : ResultScorer {
    
    override val name: String = "Multidimensional-Fallback"
    
    override suspend fun scoreResult(
        content: String,
        context: ScoringContext,
        criteria: ScoringCriteria
    ): ScoringResult {
        
        // Rule-based scoring heuristics
        val relevanceScore = calculateRelevance(content, context.originalQuery)
        val accuracyScore = calculateAccuracy(content, context.taskType)
        val creativityScore = calculateCreativity(content)
        val clarityScore = calculateClarity(content)
        
        val dimensions = mapOf(
            "relevance" to relevanceScore,
            "accuracy" to accuracyScore,
            "creativity" to creativityScore,
            "clarity" to clarityScore
        )
        
        // Weighted overall score
        val overallScore = criteria.dimensions.map { (dim, weight) ->
            (dimensions[dim] ?: 0.5) * weight
        }.sum()
        
        return ScoringResult(
            overallScore = overallScore,
            dimensions = dimensions,
            confidence = 0.6, // Lower confidence for rule-based
            reasoning = "Multidimensional heuristic analysis applied",
            scorerUsed = name
        )
    }
    
    override fun isAvailable(): Boolean = true
    
    private fun calculateRelevance(content: String, query: String): Double {
        val queryWords = query.lowercase().split(" ").filter { it.length > 3 }
        val contentWords = content.lowercase().split(" ")
        val matches = queryWords.count { word -> contentWords.any { it.contains(word) } }
        return (matches.toDouble() / queryWords.size.coerceAtLeast(1)).coerceIn(0.0, 1.0)
    }
    
    private fun calculateAccuracy(content: String, taskType: TaskType): Double {
        return when (taskType) {
            TaskType.ANALYSIS -> if (content.contains("analysis") || content.contains("data")) 0.8 else 0.6
            TaskType.CREATIVE -> if (content.contains("creative") || content.contains("innovative")) 0.8 else 0.6
            TaskType.PROBLEM_SOLVING -> if (content.contains("solution") || content.contains("solve")) 0.8 else 0.6
            else -> 0.7
        }
    }
    
    private fun calculateCreativity(content: String): Double {
        val creativityIndicators = listOf("innovative", "unique", "creative", "novel", "original")
        val score = creativityIndicators.count { content.lowercase().contains(it) }
        return (score.toDouble() / creativityIndicators.size).coerceIn(0.3, 1.0)
    }
    
    private fun calculateClarity(content: String): Double {
        val sentences = content.split(".").filter { it.trim().isNotEmpty() }
        val avgSentenceLength = sentences.map { it.split(" ").size }.average()
        // Prefer moderate sentence length (10-20 words)
        return when {
            avgSentenceLength in 10.0..20.0 -> 0.9
            avgSentenceLength in 5.0..30.0 -> 0.7
            else -> 0.5
        }
    }
}

/**
 * ⚡ Simple Scorer (emergency fallback)
 */
class SimpleScorer : ResultScorer {
    override val name: String = "Simple-Emergency"
    
    override suspend fun scoreResult(
        content: String,
        context: ScoringContext,
        criteria: ScoringCriteria
    ): ScoringResult {
        // Ultra-simple: score based on content length and basic checks
        val score = when {
            content.length < 10 -> 0.2
            content.length < 50 -> 0.5
            content.length < 200 -> 0.7
            else -> 0.8
        }
        
        return ScoringResult.simple(score, name)
    }
    
    override fun isAvailable(): Boolean = true
}

// =====================================
// SCORING MANAGER
// =====================================

/**
 * 🎯 Intelligent Scoring Manager with Fallback Chain
 */
class SwarmScoringManager(
    private val scorers: List<ResultScorer>,
    private val config: ScoringManagerConfig = ScoringManagerConfig()
) {
    
    suspend fun scoreResults(
        results: List<AgentResult>,
        context: ScoringContext,
        criteria: ScoringCriteria
    ): List<ScoredAgentResult> {
        
        return coroutineScope {
            results.map { result ->
                async {
                    val score = scoreWithFallback(result.content, context, criteria)
                    ScoredAgentResult(
                        agentId = result.agentId,
                        success = result.success,
                        content = result.content,
                        score = score,
                        metadata = result.data
                    )
                }
            }.map { it.await() }
        }
    }
    
    private suspend fun scoreWithFallback(
        content: String,
        context: ScoringContext,
        criteria: ScoringCriteria
    ): ScoringResult {
        
        for (scorer in scorers) {
            if (!scorer.isAvailable()) continue
            
            try {
                val result = scorer.scoreResult(content, context, criteria)
                
                // Check if result meets minimum confidence threshold
                if (result.confidence >= criteria.minConfidenceThreshold) {
                    if (config.debugEnabled) {
                        println("[SCORING] Used ${scorer.name}, confidence: ${result.confidence}")
                    }
                    return result
                }
            } catch (e: Exception) {
                if (config.debugEnabled) {
                    println("[SCORING] ${scorer.name} failed: ${e.message}")
                }
                continue
            }
        }
        
        // Ultimate fallback
        return ScoringResult.simple(0.5, "ultimate-fallback")
    }
}

// =====================================
// CONFIGURATION CLASSES
// =====================================

/**
 * ⚙️ AI Scoring Configuration
 */
data class AIScoringConfig(
    val maxRetries: Int = 3,
    val timeoutMs: Long = 10000,
    val enableCaching: Boolean = true
)

/**
 * 🛠️ Scoring Manager Configuration
 */
data class ScoringManagerConfig(
    val debugEnabled: Boolean = false,
    val parallelScoring: Boolean = true,
    val cacheResults: Boolean = true
)

// =====================================
// FACTORY FUNCTIONS
// =====================================

/**
 * 🏭 Create AI-powered scoring manager
 */
fun createAIScoringManager(
    scoringAgent: Agent? = null,
    debugEnabled: Boolean = false
): SwarmScoringManager {
    
    val scorers = mutableListOf<ResultScorer>()
    
    // Add AI scorer if agent is provided
    scoringAgent?.let { agent ->
        scorers.add(AIResultScorer(agent))
    }
    
    // Add fallback scorers
    scorers.add(MultidimensionalScorer())
    scorers.add(SimpleScorer())
    
    return SwarmScoringManager(
        scorers = scorers,
        config = ScoringManagerConfig(debugEnabled = debugEnabled)
    )
}

/**
 * 🎯 Create scoring criteria for different task types
 */
fun createScoringCriteria(taskType: TaskType): ScoringCriteria {
    return when (taskType) {
        TaskType.ANALYSIS -> ScoringCriteria(
            dimensions = mapOf(
                "accuracy" to 0.4,
                "relevance" to 0.3,
                "clarity" to 0.2,
                "creativity" to 0.1
            )
        )
        TaskType.CREATIVE -> ScoringCriteria(
            dimensions = mapOf(
                "creativity" to 0.4,
                "relevance" to 0.3,
                "clarity" to 0.2,
                "accuracy" to 0.1
            )
        )
        TaskType.PROBLEM_SOLVING -> ScoringCriteria(
            dimensions = mapOf(
                "accuracy" to 0.35,
                "relevance" to 0.35,
                "creativity" to 0.2,
                "clarity" to 0.1
            )
        )
        else -> ScoringCriteria() // Default balanced criteria
    }
} 