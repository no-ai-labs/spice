package io.github.spice

/**
 * Routing Policy
 * 
 * Automatically routes messages to specific Agents based on message content, type, and metadata
 */

/**
 * Routing strategy types
 */
enum class RoutingStrategy {
    KEYWORD_BASED,      // Keyword-based routing
    METADATA_BASED,     // Metadata-based routing
    CONFIDENCE_BASED,   // Confidence-based routing
    CAPABILITY_BASED,   // Capability-based routing
    LOAD_BALANCED,      // Load-balanced routing
    PRIORITY_BASED,     // Priority-based routing
    HYBRID             // Hybrid routing
}

/**
 * Routing result
 */
data class RoutingResult(
    val agent: Agent,
    val confidence: Double,
    val reason: String,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Routing rule interface
 */
interface RoutingRule {
    val name: String
    val strategy: RoutingStrategy
    val priority: Int // Higher value = higher priority
    
    /**
     * Check if message matches this rule
     */
    suspend fun matches(message: Message): Boolean
    
    /**
     * Select optimal Agent when matched
     */
    suspend fun selectAgent(message: Message, availableAgents: List<Agent>): RoutingResult?
}

/**
 * Keyword-based routing rule
 */
class KeywordRoutingRule(
    override val name: String,
    override val priority: Int = 100,
    private val keywordAgentMap: Map<String, Agent>
) : RoutingRule {
    
    override val strategy = RoutingStrategy.KEYWORD_BASED
    
    override suspend fun matches(message: Message): Boolean {
        return keywordAgentMap.keys.any { keyword ->
            message.content.contains(keyword, ignoreCase = true)
        }
    }
    
    override suspend fun selectAgent(message: Message, availableAgents: List<Agent>): RoutingResult? {
        val matchingKeywords = keywordAgentMap.keys.filter { keyword ->
            message.content.contains(keyword, ignoreCase = true)
        }
        
        if (matchingKeywords.isEmpty()) return null
        
        // Select Agent with longest matching keyword (more specific)
        val bestKeyword = matchingKeywords.maxByOrNull { it.length } ?: return null
        val selectedAgent = keywordAgentMap[bestKeyword] ?: return null
        
        if (!availableAgents.contains(selectedAgent)) return null
        
        return RoutingResult(
            agent = selectedAgent,
            confidence = calculateKeywordConfidence(message.content, bestKeyword),
            reason = "Keyword match: '$bestKeyword'",
            metadata = mapOf("keyword" to bestKeyword, "allMatches" to matchingKeywords)
        )
    }
    
    private fun calculateKeywordConfidence(content: String, keyword: String): Double {
        val keywordCount = content.split("\\s+".toRegex()).count { 
            it.contains(keyword, ignoreCase = true) 
        }
        val totalWords = content.split("\\s+".toRegex()).size
        
        // Consider keyword density and keyword length
        val density = keywordCount.toDouble() / totalWords
        val lengthFactor = keyword.length.toDouble() / 10.0 // Normalize by length
        
        return (density * 0.7 + lengthFactor * 0.3).coerceIn(0.0, 1.0)
    }
}

/**
 * Metadata-based routing rule
 */
class MetadataRoutingRule(
    override val name: String,
    override val priority: Int = 200,
    private val metadataRules: Map<String, Map<String, Agent>>
) : RoutingRule {
    
    override val strategy = RoutingStrategy.METADATA_BASED
    
    override suspend fun matches(message: Message): Boolean {
        return metadataRules.keys.any { key ->
            message.metadata.containsKey(key)
        }
    }
    
    override suspend fun selectAgent(message: Message, availableAgents: List<Agent>): RoutingResult? {
        // Find matching rules in metadata
        val matchingRules = metadataRules.entries.filter { (key, _) ->
            message.metadata.containsKey(key)
        }
        
        if (matchingRules.isEmpty()) return null
        
        // Select rule with highest priority
        val bestRule = matchingRules.maxByOrNull { it.value.size } ?: return null
        val metadataKey = bestRule.key
        val metadataValue = message.metadata[metadataKey]?.toString() ?: return null
        
        val selectedAgent = bestRule.value[metadataValue] ?: return null
        
        if (!availableAgents.contains(selectedAgent)) return null
        
        return RoutingResult(
            agent = selectedAgent,
            confidence = 0.9, // Metadata is explicit, so high confidence
            reason = "Metadata match: $metadataKey=$metadataValue",
            metadata = mapOf(
                "metadataKey" to metadataKey,
                "metadataValue" to metadataValue
            )
        )
    }
}

/**
 * Capability-based routing rule
 */
class CapabilityRoutingRule(
    override val name: String,
    override val priority: Int = 150
) : RoutingRule {
    
    override val strategy = RoutingStrategy.CAPABILITY_BASED
    
    override suspend fun matches(message: Message): Boolean {
        // Apply if requiredCapability metadata exists or capability can be inferred from content
        return message.metadata.containsKey("requiredCapability") ||
               inferCapabilityFromContent(message.content).isNotEmpty()
    }
    
    override suspend fun selectAgent(message: Message, availableAgents: List<Agent>): RoutingResult? {
        val requiredCapabilities = mutableSetOf<String>()
        
        // Add from metadata
        message.metadata["requiredCapability"]?.let { capability ->
            requiredCapabilities.add(capability.toString())
        }
        
        // Add inferred from content
        requiredCapabilities.addAll(inferCapabilityFromContent(message.content))
        
        if (requiredCapabilities.isEmpty()) return null
        
        // Select Agent with highest capability score
        val agentScores = availableAgents.map { agent ->
            val score = requiredCapabilities.sumOf { capability ->
                if (agent.capabilities.contains(capability)) 1.0 else 0.0
            } / requiredCapabilities.size
            
            agent to score
        }.filter { it.second > 0 }
        
        if (agentScores.isEmpty()) return null
        
        val (bestAgent, score) = agentScores.maxByOrNull { it.second } ?: return null
        
        return RoutingResult(
            agent = bestAgent,
            confidence = score,
            reason = "Capability match: ${requiredCapabilities.joinToString(", ")}",
            metadata = mapOf("requiredCapabilities" to requiredCapabilities)
        )
    }
    
    private fun inferCapabilityFromContent(content: String): Set<String> {
        val capabilities = mutableSetOf<String>()
        
        // Extract from metadata
        if (content.contains("calculate", ignoreCase = true) || 
            content.contains("math", ignoreCase = true)) {
            capabilities.add("calculation")
        }
        
        if (content.contains("search", ignoreCase = true) || 
            content.contains("find", ignoreCase = true)) {
            capabilities.add("search")
        }
        
        if (content.contains("analyze", ignoreCase = true) || 
            content.contains("analysis", ignoreCase = true)) {
            capabilities.add("analysis")
        }
        
        if (content.contains("translate", ignoreCase = true) || 
            content.contains("translation", ignoreCase = true)) {
            capabilities.add("translation")
        }
        
        if (content.contains("generate", ignoreCase = true) || 
            content.contains("create", ignoreCase = true)) {
            capabilities.add("generation")
        }
        
        return capabilities
    }
}

/**
 * Confidence-based routing rule
 */
class ConfidenceRoutingRule(
    override val name: String,
    override val priority: Int = 50,
    private val performanceTracker: MutableMap<String, AgentPerformance> = mutableMapOf()
) : RoutingRule {
    
    override val strategy = RoutingStrategy.CONFIDENCE_BASED
    
    override suspend fun matches(message: Message): Boolean {
        // Apply only when there are Agents with performance records
        return performanceTracker.isNotEmpty()
    }
    
    override suspend fun selectAgent(message: Message, availableAgents: List<Agent>): RoutingResult? {
        val agentsWithPerformance = availableAgents.filter { agent ->
            performanceTracker.containsKey(agent.id)
        }
        
        if (agentsWithPerformance.isEmpty()) return null
        
        // Select Agent with best performance record
        val bestAgent = agentsWithPerformance.maxByOrNull { agent ->
            val performance = performanceTracker[agent.id]!!
            calculateConfidenceScore(performance)
        } ?: return null
        
        val performance = performanceTracker[bestAgent.id]!!
        val confidence = calculateConfidenceScore(performance)
        
        return RoutingResult(
            agent = bestAgent,
            confidence = confidence,
            reason = "Performance-based selection (success rate: ${performance.successRate})",
            metadata = mapOf("performance" to performance)
        )
    }
    
    private fun calculateConfidenceScore(performance: AgentPerformance): Double {
        val successRate = performance.successRate
        val responseTime = kotlin.math.max(1.0, performance.averageResponseTime / 1000.0) // Convert to seconds
        val recentScore = kotlin.math.min(performance.recentRequests / 10.0, 1.0) // Based on recent 10 requests
        
        // Weighted calculation: success rate (60%) + response time (20%) + recent activity (20%)
        return (successRate * 0.6) + ((1.0 / responseTime) * 0.2) + (recentScore * 0.2)
    }
    
    /**
     * Update Agent performance record
     */
    fun updatePerformance(agentId: String, success: Boolean, responseTime: Long) {
        val performance = performanceTracker.getOrPut(agentId) { AgentPerformance(agentId) }
        performance.recordResult(success, responseTime)
    }
}

/**
 * Agent performance record
 */
data class AgentPerformance(
    val agentId: String,
    var totalRequests: Int = 0,
    var successfulRequests: Int = 0,
    var totalResponseTime: Long = 0,
    var recentRequests: Int = 0,
    private val recentResults: MutableList<Boolean> = mutableListOf()
) {
    val successRate: Double
        get() = if (totalRequests > 0) successfulRequests.toDouble() / totalRequests else 0.0
    
    val averageResponseTime: Double
        get() = if (totalRequests > 0) totalResponseTime.toDouble() / totalRequests else 0.0
    
    fun recordResult(success: Boolean, responseTime: Long) {
        totalRequests++
        if (success) successfulRequests++
        totalResponseTime += responseTime
        
        recentResults.add(success)
        // Keep only recent 20 request records
        if (recentResults.size > 20) {
            recentResults.removeAt(0)
        }
        
        recentRequests = recentResults.size
    }
}

/**
 * Load-balanced routing rule
 */
class LoadBalancedRoutingRule(
    override val name: String,
    override val priority: Int = 75,
    private val loadTracker: MutableMap<String, AgentLoad> = mutableMapOf()
) : RoutingRule {
    
    override val strategy = RoutingStrategy.LOAD_BALANCED
    
    override suspend fun matches(message: Message): Boolean {
        return true // Always applicable
    }
    
    override suspend fun selectAgent(message: Message, availableAgents: List<Agent>): RoutingResult? {
        if (availableAgents.isEmpty()) return null
        
        // Check current load of each Agent
        val agentLoads = availableAgents.map { agent ->
            val load = loadTracker.getOrPut(agent.id) { AgentLoad(agent.id) }
            agent to load
        }
        
        // Select Agent with lowest load
        val (bestAgent, load) = agentLoads.minByOrNull { it.second.currentLoad } ?: return null
        
        // Increase load
        load.incrementLoad()
        
        val confidence = 1.0 - (load.currentLoad / 100.0).coerceIn(0.0, 0.8) // Adjust confidence based on load
        
        return RoutingResult(
            agent = bestAgent,
            confidence = confidence,
            reason = "Load-balanced selection (current load: ${load.currentLoad})",
            metadata = mapOf("currentLoad" to load.currentLoad)
        )
    }
    
    /**
     * Decrease load when Agent task completes
     */
    fun completeTask(agentId: String) {
        loadTracker[agentId]?.decrementLoad()
    }
}

/**
 * Agent load status
 */
data class AgentLoad(
    val agentId: String,
    var currentLoad: Int = 0,
    var totalProcessed: Int = 0,
    var lastActivity: Long = System.currentTimeMillis()
) {
    fun incrementLoad() {
        currentLoad++
        totalProcessed++
        lastActivity = System.currentTimeMillis()
    }
    
    fun decrementLoad() {
        if (currentLoad > 0) {
            currentLoad--
        }
        lastActivity = System.currentTimeMillis()
    }
}

/**
 * Hybrid routing policy manager
 */
class HybridRoutingPolicy {
    private val rules = mutableListOf<RoutingRule>()
    private val routingHistory = mutableListOf<RoutingRecord>()
    
    /**
     * Add routing rule
     */
    fun addRule(rule: RoutingRule) {
        rules.add(rule)
        // Sort by priority
        rules.sortByDescending { it.priority }
    }
    
    /**
     * Select optimal Agent for message
     */
    suspend fun selectAgent(message: Message, availableAgents: List<Agent>): RoutingResult? {
        val candidates = mutableListOf<RoutingResult>()
        
        // Apply all rules in priority order
        for (rule in rules) {
            if (rule.matches(message)) {
                rule.selectAgent(message, availableAgents)?.let { result ->
                    candidates.add(result)
                }
            }
        }
        
        if (candidates.isEmpty()) {
            // Default routing: first available Agent
            val defaultAgent = availableAgents.firstOrNull() ?: return null
            return RoutingResult(
                agent = defaultAgent,
                confidence = 0.5,
                reason = "Default routing (no rules matched)",
                metadata = mapOf("fallback" to true)
            )
        }
        
        // Select result with highest confidence
        val bestResult = candidates.maxByOrNull { it.confidence } ?: return null
        
        // Save routing record
        routingHistory.add(
            RoutingRecord(
                message = message,
                selectedAgent = bestResult.agent,
                confidence = bestResult.confidence,
                reason = bestResult.reason,
                timestamp = System.currentTimeMillis()
            )
        )
        
        return bestResult
    }
    
    /**
     * Query routing statistics
     */
    fun getRoutingStats(): RoutingStats {
        val totalRoutes = routingHistory.size
        val agentCounts = routingHistory.groupingBy { it.selectedAgent.id }.eachCount()
        val averageConfidence = routingHistory.map { it.confidence }.average()
        val recentRoutes = routingHistory.filter { 
            System.currentTimeMillis() - it.timestamp < 3600000 // Last hour
        }.size
        
        return RoutingStats(
            totalRoutes = totalRoutes,
            agentDistribution = agentCounts,
            averageConfidence = averageConfidence,
            recentRoutes = recentRoutes
        )
    }
}

/**
 * Routing record
 */
data class RoutingRecord(
    val message: Message,
    val selectedAgent: Agent,
    val confidence: Double,
    val reason: String,
    val timestamp: Long
)

/**
 * Routing statistics
 */
data class RoutingStats(
    val totalRoutes: Int,
    val agentDistribution: Map<String, Int>,
    val averageConfidence: Double,
    val recentRoutes: Int
)

/**
 * Extension function: Add routing policy to AgentEngine
 */
fun AgentEngine.addRoutingPolicy(policy: HybridRoutingPolicy) {
    // Implementation depends on AgentEngine structure
    // This would integrate with AgentEngine's message routing system
} 