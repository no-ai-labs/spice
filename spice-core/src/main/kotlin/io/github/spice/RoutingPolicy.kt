package io.github.spice

/**
 * 🔀 Spice Routing Policy System
 * 
 * 메시지의 내용, 타입, 메타데이터에 따라 자동으로 특정 Agent에게 라우팅하는 전략
 */

/**
 * 라우팅 전략 타입
 */
enum class RoutingStrategy {
    KEYWORD_BASED,      // 키워드 기반 라우팅
    METADATA_BASED,     // 메타데이터 기반 라우팅  
    CONFIDENCE_BASED,   // 신뢰도 기반 라우팅
    CAPABILITY_BASED,   // 능력 기반 라우팅
    LOAD_BALANCED,      // 부하 분산 라우팅
    PRIORITY_BASED,     // 우선순위 기반 라우팅
    HYBRID             // 복합 라우팅
}

/**
 * 라우팅 결과
 */
data class RoutingResult(
    val agentId: String,
    val agentName: String,
    val confidence: Double,
    val reason: String,
    val strategy: RoutingStrategy,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * 라우팅 규칙 인터페이스
 */
interface RoutingRule {
    val strategy: RoutingStrategy
    val priority: Int // 높을수록 우선순위 높음
    
    /**
     * 메시지가 이 규칙에 매칭되는지 확인
     */
    fun matches(message: Message, agents: List<Agent>): Boolean
    
    /**
     * 매칭된 경우 최적 Agent 선택
     */
    fun selectAgent(message: Message, agents: List<Agent>): RoutingResult?
}

/**
 * 키워드 기반 라우팅 규칙
 */
class KeywordRoutingRule(
    private val keywords: Map<String, String>, // keyword -> agentId
    override val priority: Int = 100
) : RoutingRule {
    override val strategy = RoutingStrategy.KEYWORD_BASED
    
    override fun matches(message: Message, agents: List<Agent>): Boolean {
        val content = message.content.lowercase()
        return keywords.keys.any { keyword -> content.contains(keyword.lowercase()) }
    }
    
    override fun selectAgent(message: Message, agents: List<Agent>): RoutingResult? {
        val content = message.content.lowercase()
        
        // 매칭되는 키워드 찾기
        val matchedKeywords = keywords.filter { (keyword, _) ->
            content.contains(keyword.lowercase())
        }
        
        if (matchedKeywords.isEmpty()) return null
        
        // 가장 긴 키워드가 매칭된 Agent 선택 (더 구체적)
        val bestMatch = matchedKeywords.maxByOrNull { it.key.length }!!
        val targetAgent = agents.find { it.id == bestMatch.value }
            ?: return null
        
        val confidence = calculateKeywordConfidence(content, bestMatch.key)
        
        return RoutingResult(
            agentId = targetAgent.id,
            agentName = targetAgent.name,
            confidence = confidence,
            reason = "Keyword match: '${bestMatch.key}'",
            strategy = strategy,
            metadata = mapOf(
                "matchedKeyword" to bestMatch.key,
                "keywordLength" to bestMatch.key.length.toString()
            )
        )
    }
    
    private fun calculateKeywordConfidence(content: String, keyword: String): Double {
        val keywordCount = content.lowercase().split(keyword.lowercase()).size - 1
        val wordCount = content.split(" ").size
        
        // 키워드 밀도와 키워드 길이를 고려한 신뢰도
        val density = keywordCount.toDouble() / wordCount
        val lengthBonus = kotlin.math.min(keyword.length / 10.0, 0.3)
        
        return kotlin.math.min(0.5 + density * 2 + lengthBonus, 1.0)
    }
}

/**
 * 메타데이터 기반 라우팅 규칙
 */
class MetadataRoutingRule(
    private val metadataRules: Map<String, String>, // metadataKey -> agentId
    override val priority: Int = 150
) : RoutingRule {
    override val strategy = RoutingStrategy.METADATA_BASED
    
    override fun matches(message: Message, agents: List<Agent>): Boolean {
        return metadataRules.keys.any { key -> message.metadata.containsKey(key) }
    }
    
    override fun selectAgent(message: Message, agents: List<Agent>): RoutingResult? {
        // 메타데이터에서 매칭되는 규칙 찾기
        val matchedRules = metadataRules.filter { (key, _) ->
            message.metadata.containsKey(key)
        }
        
        if (matchedRules.isEmpty()) return null
        
        // 우선순위가 높은 메타데이터 선택
        val bestRule = matchedRules.entries.first()
        val targetAgent = agents.find { it.id == bestRule.value }
            ?: return null
        
        return RoutingResult(
            agentId = targetAgent.id,
            agentName = targetAgent.name,
            confidence = 0.9, // 메타데이터는 명시적이므로 높은 신뢰도
            reason = "Metadata match: ${bestRule.key}=${message.metadata[bestRule.key]}",
            strategy = strategy,
            metadata = mapOf(
                "matchedMetadataKey" to bestRule.key,
                "matchedMetadataValue" to (message.metadata[bestRule.key] ?: "")
            )
        )
    }
}

/**
 * 능력 기반 라우팅 규칙
 */
class CapabilityRoutingRule(
    override val priority: Int = 120
) : RoutingRule {
    override val strategy = RoutingStrategy.CAPABILITY_BASED
    
    override fun matches(message: Message, agents: List<Agent>): Boolean {
        // requiredCapability 메타데이터가 있거나, 내용에서 능력 추론 가능한 경우
        return message.metadata.containsKey("requiredCapability") ||
                message.metadata.containsKey("capabilities") ||
                inferRequiredCapabilities(message.content).isNotEmpty()
    }
    
    override fun selectAgent(message: Message, agents: List<Agent>): RoutingResult? {
        val requiredCapabilities = extractRequiredCapabilities(message)
        if (requiredCapabilities.isEmpty()) return null
        
        // 각 Agent의 매칭 점수 계산
        val agentScores = agents.mapNotNull { agent ->
            val matchingCapabilities = agent.capabilities.intersect(requiredCapabilities.toSet())
            if (matchingCapabilities.isNotEmpty()) {
                val score = matchingCapabilities.size.toDouble() / requiredCapabilities.size
                AgentCapabilityScore(agent, score, matchingCapabilities.toList())
            } else null
        }
        
        if (agentScores.isEmpty()) return null
        
        // 가장 높은 점수의 Agent 선택
        val bestAgent = agentScores.maxByOrNull { it.score }!!
        val confidence = kotlin.math.min(bestAgent.score * 0.8 + 0.2, 1.0)
        
        return RoutingResult(
            agentId = bestAgent.agent.id,
            agentName = bestAgent.agent.name,
            confidence = confidence,
            reason = "Capability match: ${bestAgent.matchingCapabilities.joinToString(", ")}",
            strategy = strategy,
            metadata = mapOf(
                "matchingCapabilities" to bestAgent.matchingCapabilities.joinToString(","),
                "capabilityScore" to bestAgent.score.toString()
            )
        )
    }
    
    private fun extractRequiredCapabilities(message: Message): List<String> {
        val capabilities = mutableListOf<String>()
        
        // 메타데이터에서 추출
        message.metadata["requiredCapability"]?.let { capabilities.add(it) }
        message.metadata["capabilities"]?.split(",")?.let { capabilities.addAll(it) }
        
        // 내용에서 추론
        capabilities.addAll(inferRequiredCapabilities(message.content))
        
        return capabilities.distinct()
    }
    
    private fun inferRequiredCapabilities(content: String): List<String> {
        val capabilities = mutableListOf<String>()
        val lowercaseContent = content.lowercase()
        
        when {
            lowercaseContent.contains("search") || lowercaseContent.contains("find") -> capabilities.add("search")
            lowercaseContent.contains("analyze") || lowercaseContent.contains("analysis") -> capabilities.add("data_analysis")
            lowercaseContent.contains("api") || lowercaseContent.contains("call") -> capabilities.add("api_calls")
            lowercaseContent.contains("file") || lowercaseContent.contains("document") -> capabilities.add("file_handling")
            lowercaseContent.contains("calculate") || lowercaseContent.contains("math") -> capabilities.add("calculations")
            lowercaseContent.contains("translate") || lowercaseContent.contains("language") -> capabilities.add("translation")
            lowercaseContent.contains("image") || lowercaseContent.contains("photo") -> capabilities.add("image_processing")
            lowercaseContent.contains("text") || lowercaseContent.contains("write") -> capabilities.add("text_processing")
        }
        
        return capabilities
    }
}

/**
 * Agent 능력 점수
 */
data class AgentCapabilityScore(
    val agent: Agent,
    val score: Double,
    val matchingCapabilities: List<String>
)

/**
 * 신뢰도 기반 라우팅 규칙
 */
class ConfidenceRoutingRule(
    private val agentPerformanceHistory: MutableMap<String, AgentPerformance> = mutableMapOf(),
    override val priority: Int = 80
) : RoutingRule {
    override val strategy = RoutingStrategy.CONFIDENCE_BASED
    
    override fun matches(message: Message, agents: List<Agent>): Boolean {
        // 성능 기록이 있는 Agent가 있는 경우에만 적용
        return agents.any { agentPerformanceHistory.containsKey(it.id) }
    }
    
    override fun selectAgent(message: Message, agents: List<Agent>): RoutingResult? {
        val eligibleAgents = agents.filter { agentPerformanceHistory.containsKey(it.id) }
        if (eligibleAgents.isEmpty()) return null
        
        // 성능 기록 기반으로 최적 Agent 선택
        val bestAgent = eligibleAgents.maxByOrNull { agent ->
            val performance = agentPerformanceHistory[agent.id]!!
            calculateConfidenceScore(performance, message)
        } ?: return null
        
        val performance = agentPerformanceHistory[bestAgent.id]!!
        val confidence = calculateConfidenceScore(performance, message)
        
        return RoutingResult(
            agentId = bestAgent.id,
            agentName = bestAgent.name,
            confidence = confidence,
            reason = "Performance-based selection (${performance.successRate}% success rate)",
            strategy = strategy,
            metadata = mapOf(
                "successRate" to performance.successRate.toString(),
                "avgResponseTime" to performance.averageResponseTime.toString(),
                "totalRequests" to performance.totalRequests.toString()
            )
        )
    }
    
    private fun calculateConfidenceScore(performance: AgentPerformance, message: Message): Double {
        val successRateWeight = 0.6
        val responseTimeWeight = 0.3
        val recentRequestsWeight = 0.1
        
        val successScore = performance.successRate / 100.0
        val speedScore = 1.0 - kotlin.math.min(performance.averageResponseTime / 5000.0, 1.0) // 5초 기준
        val recentScore = kotlin.math.min(performance.recentRequests / 10.0, 1.0) // 최근 10개 요청 기준
        
        return successScore * successRateWeight +
                speedScore * responseTimeWeight +
                recentScore * recentRequestsWeight
    }
    
    /**
     * Agent 성능 기록 업데이트
     */
    fun updatePerformance(agentId: String, success: Boolean, responseTime: Long) {
        val performance = agentPerformanceHistory.getOrPut(agentId) { AgentPerformance() }
        performance.addRecord(success, responseTime)
    }
}

/**
 * Agent 성능 기록
 */
data class AgentPerformance(
    var totalRequests: Int = 0,
    var successfulRequests: Int = 0,
    var totalResponseTime: Long = 0,
    var recentRequests: Int = 0
) {
    val successRate: Double
        get() = if (totalRequests > 0) (successfulRequests.toDouble() / totalRequests) * 100 else 0.0
    
    val averageResponseTime: Double
        get() = if (totalRequests > 0) totalResponseTime.toDouble() / totalRequests else 0.0
    
    fun addRecord(success: Boolean, responseTime: Long) {
        totalRequests++
        if (success) successfulRequests++
        totalResponseTime += responseTime
        recentRequests++
        
        // 최근 요청 기록은 최대 20개까지만 유지
        if (recentRequests > 20) recentRequests = 20
    }
}

/**
 * 부하 분산 라우팅 규칙
 */
class LoadBalancedRoutingRule(
    private val agentLoadTracker: MutableMap<String, AgentLoad> = mutableMapOf(),
    override val priority: Int = 60
) : RoutingRule {
    override val strategy = RoutingStrategy.LOAD_BALANCED
    
    override fun matches(message: Message, agents: List<Agent>): Boolean {
        // 모든 Agent에 적용 가능
        return agents.isNotEmpty()
    }
    
    override fun selectAgent(message: Message, agents: List<Agent>): RoutingResult? {
        if (agents.isEmpty()) return null
        
        // 각 Agent의 현재 부하 확인
        val agentLoads = agents.map { agent ->
            val load = agentLoadTracker.getOrPut(agent.id) { AgentLoad() }
            Pair(agent, load)
        }
        
        // 가장 부하가 적은 Agent 선택
        val (bestAgent, load) = agentLoads.minByOrNull { it.second.currentLoad }!!
        
        // 부하 증가
        load.incrementLoad()
        
        val confidence = 1.0 - (load.currentLoad / 100.0).coerceIn(0.0, 0.8) // 부하에 따라 신뢰도 조정
        
        return RoutingResult(
            agentId = bestAgent.id,
            agentName = bestAgent.name,
            confidence = confidence,
            reason = "Load-balanced selection (current load: ${load.currentLoad})",
            strategy = strategy,
            metadata = mapOf(
                "currentLoad" to load.currentLoad.toString(),
                "maxLoad" to load.maxLoad.toString()
            )
        )
    }
    
    /**
     * Agent 작업 완료 시 부하 감소
     */
    fun decrementLoad(agentId: String) {
        agentLoadTracker[agentId]?.decrementLoad()
    }
}

/**
 * Agent 부하 상태
 */
data class AgentLoad(
    var currentLoad: Int = 0,
    val maxLoad: Int = 10
) {
    fun incrementLoad() {
        if (currentLoad < maxLoad) currentLoad++
    }
    
    fun decrementLoad() {
        if (currentLoad > 0) currentLoad--
    }
    
    val loadPercentage: Double
        get() = (currentLoad.toDouble() / maxLoad) * 100
}

/**
 * 복합 라우팅 정책 관리자
 */
class RoutingPolicyManager {
    private val rules = mutableListOf<RoutingRule>()
    private val routingHistory = mutableListOf<RoutingHistoryEntry>()
    
    /**
     * 라우팅 규칙 추가
     */
    fun addRule(rule: RoutingRule): RoutingPolicyManager {
        rules.add(rule)
        // 우선순위 순으로 정렬
        rules.sortByDescending { it.priority }
        return this
    }
    
    /**
     * 메시지에 대한 최적 Agent 선택
     */
    fun selectAgent(message: Message, agents: List<Agent>): RoutingResult? {
        val candidateResults = mutableListOf<RoutingResult>()
        
        // 모든 규칙을 우선순위 순으로 적용
        for (rule in rules) {
            if (rule.matches(message, agents)) {
                rule.selectAgent(message, agents)?.let { result ->
                    candidateResults.add(result)
                }
            }
        }
        
        if (candidateResults.isEmpty()) {
            // 기본 라우팅: 첫 번째 사용 가능한 Agent
            val defaultAgent = agents.firstOrNull { it.canHandle(message) }
            return defaultAgent?.let {
                RoutingResult(
                    agentId = it.id,
                    agentName = it.name,
                    confidence = 0.5,
                    reason = "Default routing (no specific rules matched)",
                    strategy = RoutingStrategy.HYBRID
                )
            }
        }
        
        // 가장 높은 신뢰도의 결과 선택
        val bestResult = candidateResults.maxByOrNull { it.confidence }!!
        
        // 라우팅 기록 저장
        routingHistory.add(RoutingHistoryEntry(
            messageId = message.id,
            selectedAgentId = bestResult.agentId,
            strategy = bestResult.strategy,
            confidence = bestResult.confidence,
            reason = bestResult.reason,
            timestamp = System.currentTimeMillis()
        ))
        
        return bestResult
    }
    
    /**
     * 라우팅 통계 조회
     */
    fun getRoutingStatistics(): RoutingStatistics {
        val strategyUsage = routingHistory.groupBy { it.strategy }
            .mapValues { it.value.size }
        
        val averageConfidence = if (routingHistory.isNotEmpty()) {
            routingHistory.map { it.confidence }.average()
        } else 0.0
        
        val agentUsage = routingHistory.groupBy { it.selectedAgentId }
            .mapValues { it.value.size }
        
        return RoutingStatistics(
            totalRoutings = routingHistory.size,
            strategyUsage = strategyUsage,
            averageConfidence = averageConfidence,
            agentUsage = agentUsage,
            recentRoutings = routingHistory.takeLast(10)
        )
    }
}

/**
 * 라우팅 기록
 */
data class RoutingHistoryEntry(
    val messageId: String,
    val selectedAgentId: String,
    val strategy: RoutingStrategy,
    val confidence: Double,
    val reason: String,
    val timestamp: Long
)

/**
 * 라우팅 통계
 */
data class RoutingStatistics(
    val totalRoutings: Int,
    val strategyUsage: Map<RoutingStrategy, Int>,
    val averageConfidence: Double,
    val agentUsage: Map<String, Int>,
    val recentRoutings: List<RoutingHistoryEntry>
)

/**
 * 확장 함수: AgentEngine에 라우팅 정책 추가
 */
fun AgentEngine.withRoutingPolicy(configure: RoutingPolicyManager.() -> Unit): RoutingPolicyManager {
    val policyManager = RoutingPolicyManager()
    policyManager.configure()
    return policyManager
} 