package io.github.noailabs.spice.swarm.intelligence

import io.github.noailabs.spice.*
import io.github.noailabs.spice.swarm.*
import io.github.noailabs.spice.swarm.scoring.*
import kotlinx.coroutines.*
import kotlin.math.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 🧠 Phase 3: Collective Intelligence Engine
 * 
 * Revolutionary collective intelligence system where agents:
 * - Share knowledge and insights with each other
 * - Learn from collective experiences
 * - Develop emergent behaviors beyond individual capabilities
 * - Form dynamic knowledge networks
 * - Evolve collectively through swarm interactions
 */

// =====================================
// COLLECTIVE INTELLIGENCE CORE
// =====================================

/**
 * 🌐 Collective Intelligence Engine
 */
class CollectiveIntelligenceEngine(
    private val config: CollectiveIntelligenceConfig = CollectiveIntelligenceConfig()
) {
    
    private val knowledgeGraph = SwarmKnowledgeGraph()
    private val sharedMemory = SwarmSharedMemory()
    private val learningNetwork = CrossAgentLearningNetwork()
    private val emergentBehaviorTracker = EmergentBehaviorTracker()
    
    /**
     * 🎯 Enable collective intelligence for swarm
     */
    suspend fun enableCollectiveIntelligence(
        swarmId: String,
        agents: List<Agent>
    ): CollectiveIntelligenceSession {
        
        if (config.debugEnabled) {
            println("[COLLECTIVE] Enabling collective intelligence for $swarmId with ${agents.size} agents")
        }
        
        // Initialize knowledge network
        val knowledgeNetwork = initializeKnowledgeNetwork(agents)
        
        // Create shared context
        val sharedContext = createSharedContext(swarmId, agents)
        
        // Start cross-agent learning
        val learningSession = learningNetwork.startLearningSession(agents)
        
        // Initialize emergent behavior detection
        emergentBehaviorTracker.startTracking(swarmId, agents)
        
        return CollectiveIntelligenceSession(
            swarmId = swarmId,
            participants = agents.map { it.id },
            knowledgeNetwork = knowledgeNetwork,
            sharedContext = sharedContext,
            learningSession = learningSession,
            startTime = System.currentTimeMillis()
        )
    }
    
    /**
     * 🔗 Process collective intelligence interaction
     */
    suspend fun processCollectiveInteraction(
        session: CollectiveIntelligenceSession,
        task: SwarmTask,
        agentResponses: List<AgentResponse>
    ): CollectiveIntelligenceResult {
        
        // Share knowledge between agents
        val knowledgeSharing = shareKnowledge(session, agentResponses)
        
        // Generate collective insights
        val collectiveInsights = generateCollectiveInsights(session, agentResponses, knowledgeSharing)
        
        // Update shared memory
        updateSharedMemory(session, task, agentResponses, collectiveInsights)
        
        // Detect emergent behaviors
        val emergentBehaviors = detectEmergentBehaviors(session, agentResponses, collectiveInsights)
        
        // Cross-agent learning
        val learningOutcomes = facilitateCrossAgentLearning(session, agentResponses, collectiveInsights)
        
        // Generate collective response
        val collectiveResponse = synthesizeCollectiveResponse(
            task, agentResponses, collectiveInsights, emergentBehaviors
        )
        
        return CollectiveIntelligenceResult(
            sessionId = session.swarmId,
            task = task,
            individualResponses = agentResponses,
            knowledgeSharing = knowledgeSharing,
            collectiveInsights = collectiveInsights,
            emergentBehaviors = emergentBehaviors,
            learningOutcomes = learningOutcomes,
            collectiveResponse = collectiveResponse,
            intelligenceMetrics = calculateIntelligenceMetrics(session, agentResponses, collectiveInsights)
        )
    }
    
    /**
     * 🔄 Share knowledge between agents
     */
    private suspend fun shareKnowledge(
        session: CollectiveIntelligenceSession,
        responses: List<AgentResponse>
    ): KnowledgeSharing {
        
        val sharedInsights = mutableListOf<SharedInsight>()
        val knowledgeConnections = mutableListOf<KnowledgeConnection>()
        
        // Extract insights from each response
        responses.forEach { response ->
            val insights = extractInsights(response)
            insights.forEach { insight ->
                // Share with other agents who can benefit
                val beneficiaries = findKnowledgeBeneficiaries(insight, responses)
                beneficiaries.forEach { beneficiary ->
                    sharedInsights.add(SharedInsight(
                        sourceAgent = response.agentId,
                        targetAgent = beneficiary,
                        insight = insight,
                        relevanceScore = calculateRelevance(insight, responses.find { it.agentId == beneficiary }),
                        timestamp = System.currentTimeMillis()
                    ))
                    
                    knowledgeConnections.add(KnowledgeConnection(
                        fromAgent = response.agentId,
                        toAgent = beneficiary,
                        connectionType = insight.type,
                        strength = insight.confidence
                    ))
                }
            }
        }
        
        // Update knowledge graph
        knowledgeGraph.addConnections(knowledgeConnections)
        
        return KnowledgeSharing(
            sharedInsights = sharedInsights,
            knowledgeConnections = knowledgeConnections,
            networkDensity = calculateNetworkDensity(knowledgeConnections),
            sharingEfficiency = calculateSharingEfficiency(sharedInsights)
        )
    }
    
    /**
     * 💡 Generate collective insights
     */
    private suspend fun generateCollectiveInsights(
        session: CollectiveIntelligenceSession,
        responses: List<AgentResponse>,
        knowledgeSharing: KnowledgeSharing
    ): CollectiveInsights {
        
        // Combine individual insights into collective understanding
        val combinedInsights = combineInsights(responses.flatMap { extractInsights(it) })
        
        // Identify emergent patterns
        val emergentPatterns = identifyEmergentPatterns(responses, knowledgeSharing)
        
        // Generate synthesis
        val synthesis = generateSynthesis(combinedInsights, emergentPatterns)
        
        // Calculate collective confidence
        val collectiveConfidence = calculateCollectiveConfidence(responses, combinedInsights)
        
        return CollectiveInsights(
            combinedInsights = combinedInsights,
            emergentPatterns = emergentPatterns,
            synthesis = synthesis,
            collectiveConfidence = collectiveConfidence,
            noveltyScore = calculateNoveltyScore(combinedInsights, session.sharedContext),
            coherenceScore = calculateCoherenceScore(combinedInsights)
        )
    }
    
    /**
     * 🧮 Detect emergent behaviors
     */
    private suspend fun detectEmergentBehaviors(
        session: CollectiveIntelligenceSession,
        responses: List<AgentResponse>,
        insights: CollectiveInsights
    ): List<EmergentBehavior> {
        
        val behaviors = mutableListOf<EmergentBehavior>()
        
        // Detect collective problem-solving patterns
        val problemSolvingPattern = detectCollectiveProblemSolving(responses)
        if (problemSolvingPattern != null) {
            behaviors.add(EmergentBehavior(
                type = EmergentBehaviorType.COLLECTIVE_PROBLEM_SOLVING,
                description = "Agents spontaneously formed complementary problem-solving approach",
                participants = problemSolvingPattern.participants,
                strength = problemSolvingPattern.coherence,
                novelty = assessNovelty(problemSolvingPattern, session)
            ))
        }
        
        // Detect knowledge amplification
        val amplificationPattern = detectKnowledgeAmplification(responses, insights)
        if (amplificationPattern != null) {
            behaviors.add(EmergentBehavior(
                type = EmergentBehaviorType.KNOWLEDGE_AMPLIFICATION,
                description = "Collective understanding exceeded sum of individual knowledge",
                participants = amplificationPattern.contributors,
                strength = amplificationPattern.amplificationFactor,
                novelty = assessAmplificationNovelty(amplificationPattern)
            ))
        }
        
        // Detect creative synthesis
        val creativeSynthesis = detectCreativeSynthesis(responses, insights)
        if (creativeSynthesis != null) {
            behaviors.add(EmergentBehavior(
                type = EmergentBehaviorType.CREATIVE_SYNTHESIS,
                description = "Agents created novel solutions through creative combination",
                participants = creativeSynthesis.collaborators,
                strength = creativeSynthesis.creativityScore,
                novelty = creativeSynthesis.noveltyLevel
            ))
        }
        
        return behaviors
    }
    
    /**
     * 📚 Facilitate cross-agent learning
     */
    private suspend fun facilitateCrossAgentLearning(
        session: CollectiveIntelligenceSession,
        responses: List<AgentResponse>,
        insights: CollectiveInsights
    ): CrossAgentLearningOutcome {
        
        val learningPairs = identifyLearningOpportunities(responses)
        val knowledgeTransfers = mutableListOf<KnowledgeTransfer>()
        
        learningPairs.forEach { (teacher, learner) ->
            val teacherResponse = responses.find { it.agentId == teacher }!!
            val learnerResponse = responses.find { it.agentId == learner }!!
            
            val transfer = facilitateKnowledgeTransfer(teacherResponse, learnerResponse)
            knowledgeTransfers.add(transfer)
        }
        
        return CrossAgentLearningOutcome(
            learningPairs = learningPairs,
            knowledgeTransfers = knowledgeTransfers,
            overallLearningGain = calculateOverallLearningGain(knowledgeTransfers),
            networkEvolution = assessNetworkEvolution(session, knowledgeTransfers)
        )
    }
    
    /**
     * 🎯 Synthesize collective response
     */
    private suspend fun synthesizeCollectiveResponse(
        task: SwarmTask,
        responses: List<AgentResponse>,
        insights: CollectiveInsights,
        emergentBehaviors: List<EmergentBehavior>
    ): CollectiveResponse {
        
        val primarySolution = synthesizePrimarySolution(responses, insights)
        val alternativeSolutions = generateAlternativeSolutions(responses, insights, emergentBehaviors)
        val confidence = calculateSolutionConfidence(primarySolution, responses, insights)
        val reasoning = generateCollectiveReasoning(responses, insights, emergentBehaviors)
        
        return CollectiveResponse(
            primarySolution = primarySolution,
            alternativeSolutions = alternativeSolutions,
            confidence = confidence,
            reasoning = reasoning,
            contributingAgents = responses.map { it.agentId },
            emergentContributions = emergentBehaviors.map { it.description },
            collectiveAdvantage = calculateCollectiveAdvantage(responses, insights)
        )
    }
    
    // === HELPER METHODS ===
    
    private fun initializeKnowledgeNetwork(agents: List<Agent>): KnowledgeNetwork {
        return KnowledgeNetwork(
            nodes = agents.map { KnowledgeNode(it.id, it.capabilities, mutableMapOf()) },
            connections = mutableListOf(),
            centrality = calculateInitialCentrality(agents)
        )
    }
    
    private fun createSharedContext(swarmId: String, agents: List<Agent>): SwarmContext {
        return SwarmContext(
            swarmId = swarmId,
            participantCapabilities = agents.associate { it.id to it.capabilities },
            sharedKnowledge = mutableMapOf(),
            communicationHistory = mutableListOf(),
            emergentPatterns = mutableListOf()
        )
    }
    
    private fun extractInsights(response: AgentResponse): List<Insight> {
        // Simplified insight extraction - in real implementation, use NLP/AI
        val insights = mutableListOf<Insight>()
        
        if (response.content.contains("analysis", ignoreCase = true)) {
            insights.add(Insight(
                type = InsightType.ANALYTICAL,
                content = "Analytical perspective identified",
                confidence = 0.8,
                novelty = 0.6
            ))
        }
        
        if (response.content.contains("creative", ignoreCase = true) || 
            response.content.contains("innovative", ignoreCase = true)) {
            insights.add(Insight(
                type = InsightType.CREATIVE,
                content = "Creative approach suggested",
                confidence = 0.7,
                novelty = 0.8
            ))
        }
        
        if (response.content.contains("solution", ignoreCase = true) || 
            response.content.contains("solve", ignoreCase = true)) {
            insights.add(Insight(
                type = InsightType.SOLUTION_ORIENTED,
                content = "Solution-focused contribution",
                confidence = 0.9,
                novelty = 0.7
            ))
        }
        
        return insights
    }
    
    private fun findKnowledgeBeneficiaries(insight: Insight, responses: List<AgentResponse>): List<String> {
        // Simplified beneficiary identification
        return responses.filter { response ->
            when (insight.type) {
                InsightType.ANALYTICAL -> !response.content.contains("analysis", ignoreCase = true)
                InsightType.CREATIVE -> !response.content.contains("creative", ignoreCase = true)
                InsightType.SOLUTION_ORIENTED -> !response.content.contains("solution", ignoreCase = true)
                else -> true
            }
        }.map { it.agentId }
    }
    
    private fun calculateRelevance(insight: Insight, response: AgentResponse?): Double {
        return if (response != null) {
            // Simple relevance calculation
            when (insight.type) {
                InsightType.ANALYTICAL -> if (response.content.length > 100) 0.8 else 0.5
                InsightType.CREATIVE -> if (response.content.contains("idea")) 0.9 else 0.6
                InsightType.SOLUTION_ORIENTED -> if (response.content.contains("recommend")) 0.8 else 0.6
                else -> 0.7
            }
        } else 0.5
    }
    
    private fun calculateNetworkDensity(connections: List<KnowledgeConnection>): Double {
        val uniqueAgents = (connections.map { it.fromAgent } + connections.map { it.toAgent }).distinct()
        val maxConnections = uniqueAgents.size * (uniqueAgents.size - 1)
        return if (maxConnections > 0) connections.size.toDouble() / maxConnections else 0.0
    }
    
    private fun calculateSharingEfficiency(insights: List<SharedInsight>): Double {
        return insights.map { it.relevanceScore }.average()
    }
    
    private fun combineInsights(insights: List<Insight>): List<CombinedInsight> {
        val grouped = insights.groupBy { it.type }
        return grouped.map { (type, typeInsights) ->
            CombinedInsight(
                type = type,
                content = typeInsights.map { it.content }.joinToString("; "),
                combinedConfidence = typeInsights.map { it.confidence }.average(),
                contributorCount = typeInsights.size,
                synthesis = "Combined ${type.name.lowercase()} insights from ${typeInsights.size} agents"
            )
        }
    }
    
    private fun identifyEmergentPatterns(
        responses: List<AgentResponse>,
        knowledgeSharing: KnowledgeSharing
    ): List<EmergentPattern> {
        val patterns = mutableListOf<EmergentPattern>()
        
        // Pattern 1: Complementary expertise
        if (responses.map { it.content.length }.let { lengths -> 
            lengths.maxOrNull()!! - lengths.minOrNull()!! > 100 }) {
            patterns.add(EmergentPattern(
                type = PatternType.COMPLEMENTARY_EXPERTISE,
                description = "Agents showed complementary expertise patterns",
                strength = 0.8,
                participants = responses.map { it.agentId }
            ))
        }
        
        // Pattern 2: Knowledge convergence
        val sharedTerms = findSharedTerms(responses)
        if (sharedTerms.size > 3) {
            patterns.add(EmergentPattern(
                type = PatternType.KNOWLEDGE_CONVERGENCE,
                description = "Agents converged on shared understanding",
                strength = min(1.0, sharedTerms.size / 10.0),
                participants = responses.map { it.agentId }
            ))
        }
        
        return patterns
    }
    
    private fun findSharedTerms(responses: List<AgentResponse>): Set<String> {
        val allWords = responses.flatMap { it.content.lowercase().split(" ") }
            .filter { it.length > 4 }
        return allWords.groupingBy { it }.eachCount()
            .filterValues { it > 1 }
            .keys
    }
    
    private fun generateSynthesis(
        insights: List<CombinedInsight>,
        patterns: List<EmergentPattern>
    ): String {
        return """
        🧠 Collective Intelligence Synthesis:
        
        Combined Insights:
        ${insights.map { "• ${it.synthesis}" }.joinToString("\n")}
        
        Emergent Patterns:
        ${patterns.map { "• ${it.description} (strength: ${String.format("%.2f", it.strength)})" }.joinToString("\n")}
        
        Collective Understanding: The swarm demonstrated ${insights.size} types of combined insights 
        with ${patterns.size} emergent patterns, indicating ${if (patterns.isNotEmpty()) "strong" else "developing"} collective intelligence.
        """.trimIndent()
    }
    
    private fun calculateCollectiveConfidence(
        responses: List<AgentResponse>,
        insights: List<CombinedInsight>
    ): Double {
        val responseVariability = calculateResponseVariability(responses)
        val insightStrength = insights.map { it.combinedConfidence }.average()
        return (insightStrength * 0.7 + (1.0 - responseVariability) * 0.3).coerceIn(0.0, 1.0)
    }
    
    private fun calculateResponseVariability(responses: List<AgentResponse>): Double {
        val lengths = responses.map { it.content.length.toDouble() }
        val mean = lengths.average()
        val variance = lengths.map { (it - mean).pow(2) }.average()
        return min(1.0, sqrt(variance) / mean)
    }
    
    private fun calculateNoveltyScore(insights: List<CombinedInsight>, context: SwarmContext): Double {
        // Simplified novelty calculation
        return insights.map { insight ->
            val isNovel = !context.sharedKnowledge.containsKey(insight.type.name)
            if (isNovel) 0.8 else 0.4
        }.average()
    }
    
    private fun calculateCoherenceScore(insights: List<CombinedInsight>): Double {
        // Simplified coherence calculation based on consistency
        return if (insights.size > 1) {
            val avgConfidence = insights.map { it.combinedConfidence }.average()
            val variance = insights.map { (it.combinedConfidence - avgConfidence).pow(2) }.average()
            max(0.0, 1.0 - sqrt(variance))
        } else 1.0
    }
    
    private fun calculateIntelligenceMetrics(
        session: CollectiveIntelligenceSession,
        responses: List<AgentResponse>,
        insights: CollectiveInsights
    ): IntelligenceMetrics {
        return IntelligenceMetrics(
            collectiveIQ = calculateCollectiveIQ(responses, insights),
            emergenceIndex = calculateEmergenceIndex(insights),
            synergyScore = calculateSynergyScore(responses),
            learningVelocity = calculateLearningVelocity(session),
            networkEfficiency = calculateNetworkEfficiency(session.knowledgeNetwork)
        )
    }
    
    private fun calculateCollectiveIQ(responses: List<AgentResponse>, insights: CollectiveInsights): Double {
        val responseQuality = responses.map { it.content.length / 100.0 }.average().coerceAtMost(1.0)
        val insightDepth = insights.collectiveConfidence
        val novelty = insights.noveltyScore
        return (responseQuality * 0.4 + insightDepth * 0.4 + novelty * 0.2) * 100
    }
    
    private fun calculateEmergenceIndex(insights: CollectiveInsights): Double {
        return (insights.noveltyScore * 0.5 + insights.coherenceScore * 0.3 + 
                insights.emergentPatterns.size / 5.0 * 0.2).coerceIn(0.0, 1.0)
    }
    
    private fun calculateSynergyScore(responses: List<AgentResponse>): Double {
        val diversity = calculateResponseDiversity(responses)
        val complementarity = calculateComplementarity(responses)
        return (diversity * 0.6 + complementarity * 0.4).coerceIn(0.0, 1.0)
    }
    
    private fun calculateResponseDiversity(responses: List<AgentResponse>): Double {
        val uniqueWords = responses.flatMap { it.content.split(" ") }.distinct().size
        val totalWords = responses.sumOf { it.content.split(" ").size }
        return if (totalWords > 0) uniqueWords.toDouble() / totalWords else 0.0
    }
    
    private fun calculateComplementarity(responses: List<AgentResponse>): Double {
        // Simplified complementarity based on response length variation
        val lengths = responses.map { it.content.length }
        val variation = lengths.maxOrNull()!! - lengths.minOrNull()!!
        return min(1.0, variation / 500.0)
    }
    
    private fun calculateLearningVelocity(session: CollectiveIntelligenceSession): Double {
        val timeElapsed = System.currentTimeMillis() - session.startTime
        val insightGained = session.knowledgeNetwork.connections.size
        return if (timeElapsed > 0) insightGained / (timeElapsed / 1000.0) else 0.0
    }
    
    private fun calculateNetworkEfficiency(network: KnowledgeNetwork): Double {
        val totalNodes = network.nodes.size
        val totalConnections = network.connections.size
        val maxEfficiency = totalNodes * (totalNodes - 1) / 2.0
        return if (maxEfficiency > 0) totalConnections / maxEfficiency else 0.0
    }
    
    // Stub implementations for complex detection methods
    private fun detectCollectiveProblemSolving(responses: List<AgentResponse>): ProblemSolvingPattern? = null
    private fun detectKnowledgeAmplification(responses: List<AgentResponse>, insights: CollectiveInsights): AmplificationPattern? = null
    private fun detectCreativeSynthesis(responses: List<AgentResponse>, insights: CollectiveInsights): CreativeSynthesisPattern? = null
    private fun assessNovelty(pattern: ProblemSolvingPattern, session: CollectiveIntelligenceSession): Double = 0.7
    private fun assessAmplificationNovelty(pattern: AmplificationPattern): Double = 0.8
    private fun identifyLearningOpportunities(responses: List<AgentResponse>): List<Pair<String, String>> = emptyList()
    private fun facilitateKnowledgeTransfer(teacher: AgentResponse, learner: AgentResponse): KnowledgeTransfer = 
        KnowledgeTransfer(teacher.agentId, learner.agentId, "mock transfer", 0.7)
    private fun calculateOverallLearningGain(transfers: List<KnowledgeTransfer>): Double = 0.8
    private fun assessNetworkEvolution(session: CollectiveIntelligenceSession, transfers: List<KnowledgeTransfer>): String = "Network evolved positively"
    private fun synthesizePrimarySolution(responses: List<AgentResponse>, insights: CollectiveInsights): String = "Collective solution synthesized"
    private fun generateAlternativeSolutions(responses: List<AgentResponse>, insights: CollectiveInsights, behaviors: List<EmergentBehavior>): List<String> = emptyList()
    private fun calculateSolutionConfidence(solution: String, responses: List<AgentResponse>, insights: CollectiveInsights): Double = 0.85
    private fun generateCollectiveReasoning(responses: List<AgentResponse>, insights: CollectiveInsights, behaviors: List<EmergentBehavior>): String = "Collective reasoning applied"
    private fun calculateCollectiveAdvantage(responses: List<AgentResponse>, insights: CollectiveInsights): Double = 0.75
    private fun calculateInitialCentrality(agents: List<Agent>): Map<String, Double> = agents.associate { it.id to 0.5 }
    private fun updateSharedMemory(session: CollectiveIntelligenceSession, task: SwarmTask, responses: List<AgentResponse>, insights: CollectiveInsights) {}
}

// =====================================
// DATA STRUCTURES
// =====================================

/**
 * 🧠 Collective Intelligence Session
 */
data class CollectiveIntelligenceSession(
    val swarmId: String,
    val participants: List<String>,
    val knowledgeNetwork: KnowledgeNetwork,
    val sharedContext: SwarmContext,
    val learningSession: String,
    val startTime: Long
)

/**
 * 🎯 Collective Intelligence Result
 */
data class CollectiveIntelligenceResult(
    val sessionId: String,
    val task: SwarmTask,
    val individualResponses: List<AgentResponse>,
    val knowledgeSharing: KnowledgeSharing,
    val collectiveInsights: CollectiveInsights,
    val emergentBehaviors: List<EmergentBehavior>,
    val learningOutcomes: CrossAgentLearningOutcome,
    val collectiveResponse: CollectiveResponse,
    val intelligenceMetrics: IntelligenceMetrics
)

/**
 * 🔗 Knowledge Sharing
 */
data class KnowledgeSharing(
    val sharedInsights: List<SharedInsight>,
    val knowledgeConnections: List<KnowledgeConnection>,
    val networkDensity: Double,
    val sharingEfficiency: Double
)

/**
 * 💡 Collective Insights
 */
data class CollectiveInsights(
    val combinedInsights: List<CombinedInsight>,
    val emergentPatterns: List<EmergentPattern>,
    val synthesis: String,
    val collectiveConfidence: Double,
    val noveltyScore: Double,
    val coherenceScore: Double
)

/**
 * 🌟 Emergent Behavior
 */
data class EmergentBehavior(
    val type: EmergentBehaviorType,
    val description: String,
    val participants: List<String>,
    val strength: Double,
    val novelty: Double
)

/**
 * 📊 Intelligence Metrics
 */
data class IntelligenceMetrics(
    val collectiveIQ: Double,
    val emergenceIndex: Double,
    val synergyScore: Double,
    val learningVelocity: Double,
    val networkEfficiency: Double
)

// Supporting data classes
data class SwarmTask(val id: String, val description: String, val type: TaskType)
data class AgentResponse(val agentId: String, val content: String, val timestamp: Long = System.currentTimeMillis())
data class SharedInsight(val sourceAgent: String, val targetAgent: String, val insight: Insight, val relevanceScore: Double, val timestamp: Long)
data class KnowledgeConnection(val fromAgent: String, val toAgent: String, val connectionType: InsightType, val strength: Double)
data class Insight(val type: InsightType, val content: String, val confidence: Double, val novelty: Double)
data class CombinedInsight(val type: InsightType, val content: String, val combinedConfidence: Double, val contributorCount: Int, val synthesis: String)
data class EmergentPattern(val type: PatternType, val description: String, val strength: Double, val participants: List<String>)
data class KnowledgeTransfer(val fromAgent: String, val toAgent: String, val knowledge: String, val effectiveness: Double)
data class CrossAgentLearningOutcome(val learningPairs: List<Pair<String, String>>, val knowledgeTransfers: List<KnowledgeTransfer>, val overallLearningGain: Double, val networkEvolution: String)
data class CollectiveResponse(val primarySolution: String, val alternativeSolutions: List<String>, val confidence: Double, val reasoning: String, val contributingAgents: List<String>, val emergentContributions: List<String>, val collectiveAdvantage: Double)
data class KnowledgeNetwork(val nodes: List<KnowledgeNode>, val connections: MutableList<KnowledgeConnection>, val centrality: Map<String, Double>)
data class KnowledgeNode(val agentId: String, val capabilities: List<String>, val knowledgeMap: MutableMap<String, Any>)
data class SwarmContext(val swarmId: String, val participantCapabilities: Map<String, List<String>>, val sharedKnowledge: MutableMap<String, Any>, val communicationHistory: MutableList<String>, val emergentPatterns: MutableList<String>)

// Placeholder classes for complex patterns
class ProblemSolvingPattern(val participants: List<String>, val coherence: Double)
class AmplificationPattern(val contributors: List<String>, val amplificationFactor: Double)
class CreativeSynthesisPattern(val collaborators: List<String>, val creativityScore: Double, val noveltyLevel: Double)

// Supporting classes
class SwarmKnowledgeGraph {
    fun addConnections(connections: List<KnowledgeConnection>) {}
}

class SwarmSharedMemory

class CrossAgentLearningNetwork {
    fun startLearningSession(agents: List<Agent>): String = "learning-session-${System.currentTimeMillis()}"
}

class EmergentBehaviorTracker {
    fun startTracking(swarmId: String, agents: List<Agent>) {}
}

/**
 * ⚙️ Configuration
 */
data class CollectiveIntelligenceConfig(
    val debugEnabled: Boolean = false,
    val knowledgeSharingEnabled: Boolean = true,
    val emergentBehaviorDetection: Boolean = true,
    val crossAgentLearning: Boolean = true,
    val maxSharedInsights: Int = 100,
    val learningThreshold: Double = 0.7
)

// =====================================
// ENUMS
// =====================================

enum class InsightType { ANALYTICAL, CREATIVE, SOLUTION_ORIENTED, STRATEGIC, TECHNICAL }
enum class PatternType { COMPLEMENTARY_EXPERTISE, KNOWLEDGE_CONVERGENCE, CREATIVE_SYNTHESIS, PROBLEM_DECOMPOSITION }
enum class EmergentBehaviorType { COLLECTIVE_PROBLEM_SOLVING, KNOWLEDGE_AMPLIFICATION, CREATIVE_SYNTHESIS, SWARM_COORDINATION } 