package io.github.spice

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.select

/**
 * 🧠 Multi-Agent Conversation Flow System
 * 
 * 하나의 메시지가 여러 Agent로 전달되어 순차/병렬로 처리되는 시스템
 */

/**
 * 다중 Agent 처리 전략
 */
enum class FlowStrategy {
    SEQUENTIAL,    // 순차 처리 (A → B → C)
    PARALLEL,      // 병렬 처리 (A, B, C 동시)
    BROADCAST,     // 브로드캐스트 (모든 Agent에 동일 메시지)
    PIPELINE,      // 파이프라인 (A의 출력 → B의 입력 → C의 입력)
    COMPETITION    // 경쟁 처리 (가장 빠른 응답 선택)
}

/**
 * 그룹 메시지 메타데이터
 */
data class GroupMetadata(
    val groupId: String,
    val totalAgents: Int,
    val currentIndex: Int,
    val strategy: FlowStrategy,
    val coordinatorId: String? = null
)

/**
 * Agent 그룹을 관리하는 추상 클래스
 */
abstract class GroupAgent(
    override val id: String,
    override val name: String,
    override val description: String,
    override val capabilities: List<String> = emptyList(),
    protected val strategy: FlowStrategy = FlowStrategy.SEQUENTIAL
) : Agent {
    
    protected val memberAgents = mutableListOf<Agent>()
    protected val processHistory = mutableListOf<AgentMessage>()
    
    /**
     * 그룹에 Agent 추가
     */
    fun addAgent(agent: Agent): GroupAgent {
        memberAgents.add(agent)
        return this
    }
    
    /**
     * 여러 Agent를 한번에 추가
     */
    fun addAgents(vararg agents: Agent): GroupAgent {
        memberAgents.addAll(agents)
        return this
    }
    
    /**
     * 그룹 메시지 처리
     */
    override suspend fun processMessage(message: Message): Message {
        val groupId = "group-${System.currentTimeMillis()}"
        val groupMetadata = GroupMetadata(
            groupId = groupId,
            totalAgents = memberAgents.size,
            currentIndex = 0,
            strategy = strategy,
            coordinatorId = id
        )
        
        val enrichedMessage = message.withMetadata("groupId", groupId)
            .withMetadata("groupStrategy", strategy.toString())
        
        return when (strategy) {
            FlowStrategy.SEQUENTIAL -> processSequential(enrichedMessage, groupMetadata)
            FlowStrategy.PARALLEL -> processParallel(enrichedMessage, groupMetadata)
            FlowStrategy.BROADCAST -> processBroadcast(enrichedMessage, groupMetadata)
            FlowStrategy.PIPELINE -> processPipeline(enrichedMessage, groupMetadata)
            FlowStrategy.COMPETITION -> processCompetition(enrichedMessage, groupMetadata)
        }
    }
    
    /**
     * 순차 처리: A → B → C
     */
    protected suspend fun processSequential(message: Message, groupMeta: GroupMetadata): Message {
        var currentMessage = message
        val results = mutableListOf<String>()
        
        memberAgents.forEachIndexed { index, agent ->
            val indexedMessage = currentMessage.withMetadata("agentIndex", index.toString())
            
            try {
                val result = agent.processMessage(indexedMessage)
                results.add("${agent.name}: ${result.content}")
                currentMessage = result
                
                // 처리 기록 저장
                processHistory.add(AgentMessage(
                    success = true,
                    agentId = agent.id,
                    agentName = agent.name,
                    response = result,
                    executionTime = 0L,
                    metadata = mapOf("sequenceIndex" to index.toString())
                ))
            } catch (e: Exception) {
                results.add("${agent.name}: Error - ${e.message}")
            }
        }
        
        return message.createReply(
            content = "Sequential processing completed:\n${results.joinToString("\n")}",
            sender = id,
            type = MessageType.RESULT,
            metadata = mapOf(
                "groupId" to groupMeta.groupId,
                "strategy" to "SEQUENTIAL",
                "agentCount" to memberAgents.size.toString(),
                "completedSteps" to results.size.toString()
            )
        )
    }
    
    /**
     * 병렬 처리: A, B, C 동시 실행
     */
    protected suspend fun processParallel(message: Message, groupMeta: GroupMetadata): Message = coroutineScope {
        val deferredResults = memberAgents.mapIndexed { index, agent ->
            async {
                try {
                    val indexedMessage = message.withMetadata("agentIndex", index.toString())
                    val result = agent.processMessage(indexedMessage)
                    "${agent.name}: ${result.content}"
                } catch (e: Exception) {
                    "${agent.name}: Error - ${e.message}"
                }
            }
        }
        
        val results = deferredResults.awaitAll()
        
        message.createReply(
            content = "Parallel processing completed:\n${results.joinToString("\n")}",
            sender = id,
            type = MessageType.RESULT,
            metadata = mapOf(
                "groupId" to groupMeta.groupId,
                "strategy" to "PARALLEL",
                "agentCount" to memberAgents.size.toString()
            )
        )
    }
    
    /**
     * 브로드캐스트: 모든 Agent에 동일 메시지 전송
     */
    protected suspend fun processBroadcast(message: Message, groupMeta: GroupMetadata): Message = coroutineScope {
        val broadcastMessage = message.withMetadata("broadcastId", groupMeta.groupId)
        
        val responses = memberAgents.map { agent ->
            async {
                try {
                    val result = agent.processMessage(broadcastMessage)
                    AgentResponse(agent.id, agent.name, result.content, true)
                } catch (e: Exception) {
                    AgentResponse(agent.id, agent.name, "Error: ${e.message}", false)
                }
            }
        }.awaitAll()
        
        val successCount = responses.count { it.success }
        val summaryContent = responses.joinToString("\n") { 
            "${it.agentName}: ${it.content}" 
        }
        
        message.createReply(
            content = "Broadcast completed ($successCount/${responses.size} successful):\n$summaryContent",
            sender = id,
            type = MessageType.RESULT,
            metadata = mapOf(
                "groupId" to groupMeta.groupId,
                "strategy" to "BROADCAST",
                "successRate" to "${successCount}/${responses.size}"
            )
        )
    }
    
    /**
     * 파이프라인: A의 출력 → B의 입력 → C의 입력
     */
    protected suspend fun processPipeline(message: Message, groupMeta: GroupMetadata): Message {
        var currentMessage = message.withMetadata("pipelineId", groupMeta.groupId)
        val pipelineSteps = mutableListOf<String>()
        
        for ((index, agent) in memberAgents.withIndex()) {
            try {
                val stepMessage = currentMessage.withMetadata("pipelineStep", index.toString())
                val result = agent.processMessage(stepMessage)
                
                pipelineSteps.add("Step ${index + 1} (${agent.name}): ${result.content}")
                
                // 다음 Agent의 입력으로 현재 Agent의 출력 사용
                currentMessage = result.copy(
                    content = result.content,
                    metadata = currentMessage.metadata + ("previousAgent" to agent.id)
                )
            } catch (e: Exception) {
                pipelineSteps.add("Step ${index + 1} (${agent.name}): Pipeline error - ${e.message}")
                break
            }
        }
        
        return message.createReply(
            content = "Pipeline processing completed:\n${pipelineSteps.joinToString("\n")}",
            sender = id,
            type = MessageType.RESULT,
            metadata = mapOf(
                "groupId" to groupMeta.groupId,
                "strategy" to "PIPELINE",
                "finalOutput" to currentMessage.content
            )
        )
    }
    
    /**
     * 경쟁 처리: 가장 빠른 응답 선택
     */
    protected suspend fun processCompetition(message: Message, groupMeta: GroupMetadata): Message = coroutineScope {
        val competitionMessage = message.withMetadata("competitionId", groupMeta.groupId)
        
        val raceResults = memberAgents.map { agent ->
            async {
                val startTime = System.currentTimeMillis()
                try {
                    val result = agent.processMessage(competitionMessage)
                    val processingTime = System.currentTimeMillis() - startTime
                    AgentCompetitionResult(agent.id, agent.name, result.content, processingTime, true)
                } catch (e: Exception) {
                    val processingTime = System.currentTimeMillis() - startTime
                    AgentCompetitionResult(agent.id, agent.name, "Error: ${e.message}", processingTime, false)
                }
            }
        }
        
        // 첫 번째 성공한 결과 선택
        val winner = select<AgentCompetitionResult?> {
            raceResults.forEach { deferred ->
                deferred.onAwait { result ->
                    if (result.success) result else null
                }
            }
        } ?: run {
            // 모든 Agent가 실패한 경우
            val firstResult = raceResults.first().await()
            firstResult
        }
        
        // 나머지 작업들 취소
        raceResults.forEach { it.cancel() }
        
        message.createReply(
            content = "Competition winner: ${winner.agentName} (${winner.processingTime}ms)\nResult: ${winner.content}",
            sender = id,
            type = MessageType.RESULT,
            metadata = mapOf(
                "groupId" to groupMeta.groupId,
                "strategy" to "COMPETITION",
                "winnerId" to winner.agentId,
                "winnerTime" to winner.processingTime.toString()
            )
        )
    }
    
    override fun canHandle(message: Message): Boolean = memberAgents.isNotEmpty()
    override fun getTools(): List<Tool> = memberAgents.flatMap { it.getTools() }
    override fun isReady(): Boolean = memberAgents.isNotEmpty() && memberAgents.all { it.isReady() }
}

/**
 * Agent 응답 데이터 클래스
 */
data class AgentResponse(
    val agentId: String,
    val agentName: String,
    val content: String,
    val success: Boolean
)

/**
 * Agent 경쟁 결과 데이터 클래스
 */
data class AgentCompetitionResult(
    val agentId: String,
    val agentName: String,
    val content: String,
    val processingTime: Long,
    val success: Boolean
)

/**
 * 순차 처리 그룹 Agent
 */
class SequentialGroupAgent(
    id: String = "sequential-group",
    name: String = "Sequential Group Agent",
    description: String = "Processes messages through agents sequentially"
) : GroupAgent(id, name, description, listOf("sequential_processing"), FlowStrategy.SEQUENTIAL)

/**
 * 병렬 처리 그룹 Agent
 */
class ParallelGroupAgent(
    id: String = "parallel-group", 
    name: String = "Parallel Group Agent",
    description: String = "Processes messages through agents in parallel"
) : GroupAgent(id, name, description, listOf("parallel_processing"), FlowStrategy.PARALLEL)

/**
 * Swarm Agent: 동적으로 Agent를 추가/제거하면서 처리
 */
class SwarmAgent(
    id: String = "swarm",
    name: String = "Swarm Agent",
    description: String = "Dynamic swarm of agents with adaptive processing",
    private val maxSwarmSize: Int = 10,
    private val defaultStrategy: FlowStrategy = FlowStrategy.PARALLEL
) : GroupAgent(id, name, description, listOf("swarm_intelligence", "adaptive_processing"), defaultStrategy) {
    
    private val agentPool = mutableListOf<Agent>()
    private var currentStrategy = defaultStrategy
    
    /**
     * Agent 풀에 Agent 추가
     */
    fun addToPool(agent: Agent): SwarmAgent {
        agentPool.add(agent)
        return this
    }
    
    /**
     * 메시지 특성에 따라 동적 Swarm 구성
     */
    override suspend fun processMessage(message: Message): Message {
        // 메시지 분석하여 적절한 Agent 선택
        val selectedAgents = selectOptimalSwarm(message)
        
        // 동적으로 전략 변경
        currentStrategy = determineOptimalStrategy(message, selectedAgents)
        
        // 선택된 Agent들로 임시 그룹 구성
        memberAgents.clear()
        memberAgents.addAll(selectedAgents)
        
        val swarmMetadata = GroupMetadata(
            groupId = "swarm-${System.currentTimeMillis()}",
            totalAgents = selectedAgents.size,
            currentIndex = 0,
            strategy = currentStrategy,
            coordinatorId = id
        )
        
        val swarmMessage = message.withMetadata("swarmSize", selectedAgents.size.toString())
            .withMetadata("swarmStrategy", currentStrategy.toString())
        
        return when (currentStrategy) {
            FlowStrategy.SEQUENTIAL -> processSequential(swarmMessage, swarmMetadata)
            FlowStrategy.PARALLEL -> processParallel(swarmMessage, swarmMetadata)
            FlowStrategy.BROADCAST -> processBroadcast(swarmMessage, swarmMetadata)
            FlowStrategy.PIPELINE -> processPipeline(swarmMessage, swarmMetadata)
            FlowStrategy.COMPETITION -> processCompetition(swarmMessage, swarmMetadata)
        }
    }
    
    /**
     * 메시지에 최적화된 Agent 선택
     */
    private fun selectOptimalSwarm(message: Message): List<Agent> {
        val messageComplexity = analyzeComplexity(message)
        val requiredCapabilities = extractRequiredCapabilities(message)
        
        return agentPool.filter { agent ->
            // 필요한 능력을 가진 Agent 우선 선택
            requiredCapabilities.any { capability -> 
                agent.capabilities.contains(capability) 
            } || agent.canHandle(message)
        }.take(maxSwarmSize.coerceAtMost(messageComplexity))
    }
    
    /**
     * 메시지 복잡도 분석
     */
    private fun analyzeComplexity(message: Message): Int {
        val baseComplexity = when (message.type) {
            MessageType.TOOL_CALL -> 3
            MessageType.DATA -> 2
            MessageType.PROMPT -> 2
            else -> 1
        }
        
        val metadataComplexity = message.metadata.size / 3
        val contentComplexity = message.content.length / 100
        
        return (baseComplexity + metadataComplexity + contentComplexity).coerceIn(1, maxSwarmSize)
    }
    
    /**
     * 필요한 능력 추출
     */
    private fun extractRequiredCapabilities(message: Message): List<String> {
        val capabilities = mutableListOf<String>()
        
        // 메타데이터에서 요구 능력 추출
        message.metadata["requiredCapability"]?.let { capabilities.add(it) }
        message.metadata["capabilities"]?.split(",")?.let { capabilities.addAll(it) }
        
        // 메시지 내용에서 키워드 기반 능력 추출
        val content = message.content.lowercase()
        when {
            content.contains("search") || content.contains("find") -> capabilities.add("search")
            content.contains("analyze") || content.contains("analysis") -> capabilities.add("data_analysis")
            content.contains("api") || content.contains("call") -> capabilities.add("api_calls")
            content.contains("file") || content.contains("document") -> capabilities.add("file_handling")
            content.contains("calculate") || content.contains("math") -> capabilities.add("calculations")
        }
        
        return capabilities.distinct()
    }
    
    /**
     * 최적 전략 결정
     */
    private fun determineOptimalStrategy(message: Message, agents: List<Agent>): FlowStrategy {
        return when {
            message.metadata["strategy"] == "sequential" -> FlowStrategy.SEQUENTIAL
            message.metadata["strategy"] == "parallel" -> FlowStrategy.PARALLEL
            message.metadata["strategy"] == "competition" -> FlowStrategy.COMPETITION
            agents.size == 1 -> FlowStrategy.SEQUENTIAL
            agents.size <= 3 -> FlowStrategy.PARALLEL
            message.type == MessageType.TOOL_CALL -> FlowStrategy.COMPETITION
            message.content.contains("pipeline") -> FlowStrategy.PIPELINE
            else -> defaultStrategy
        }
    }
}

/**
 * 확장 함수: Agent 리스트를 그룹으로 변환
 */
fun List<Agent>.asSequentialGroup(
    id: String = "auto-sequential-group",
    name: String = "Auto Sequential Group"
): SequentialGroupAgent {
    val group = SequentialGroupAgent(id, name)
    this.forEach { group.addAgent(it) }
    return group
}

fun List<Agent>.asParallelGroup(
    id: String = "auto-parallel-group", 
    name: String = "Auto Parallel Group"
): ParallelGroupAgent {
    val group = ParallelGroupAgent(id, name)
    this.forEach { group.addAgent(it) }
    return group
}

fun List<Agent>.asSwarm(
    id: String = "auto-swarm",
    name: String = "Auto Swarm",
    maxSize: Int = 10
): SwarmAgent {
    val swarm = SwarmAgent(id, name, maxSwarmSize = maxSize)
    this.forEach { swarm.addToPool(it) }
    return swarm
} 