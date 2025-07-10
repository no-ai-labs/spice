package io.github.spice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect

/**
 * JVM-Autogen의 Orchestrator 인터페이스
 * 여러 Agent들 간의 메시지 흐름을 조율합니다.
 */
interface Orchestrator {
    /**
     * 메시지 기반 워크플로우 실행
     */
    suspend fun orchestrate(
        initialMessage: Message,
        agents: List<Agent>,
        router: MessageRouter
    ): Flow<Message>
    
    /**
     * 워크플로우 상태 확인
     */
    fun getStatus(): OrchestratorStatus
}

/**
 * Orchestrator 상태
 */
enum class OrchestratorStatus {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED,
    ERROR
}

/**
 * 기본 Orchestrator 구현체
 */
class DefaultOrchestrator : Orchestrator {
    private var status = OrchestratorStatus.IDLE
    private val messageHistory = mutableListOf<Message>()
    
    override suspend fun orchestrate(
        initialMessage: Message,
        agents: List<Agent>,
        router: MessageRouter
    ): Flow<Message> = flow {
        status = OrchestratorStatus.RUNNING
        
        try {
            var currentMessages = listOf(initialMessage)
            val processedMessages = mutableSetOf<String>()
            
            while (currentMessages.isNotEmpty() && status == OrchestratorStatus.RUNNING) {
                val nextMessages = mutableListOf<Message>()
                
                for (message in currentMessages) {
                    // 중복 처리 방지
                    if (message.id in processedMessages) continue
                    processedMessages.add(message.id)
                    
                    // 메시지 히스토리에 추가
                    messageHistory.add(message)
                    emit(message)
                    
                    // 메시지 라우팅
                    val routedMessages = router.route(message)
                    
                    for (routedMessage in routedMessages) {
                        // 적절한 Agent 찾기
                        val agent = findAgentForMessage(routedMessage, agents)
                        
                        if (agent != null) {
                            // Agent에서 메시지 처리
                            val response = agent.processMessage(routedMessage)
                            nextMessages.add(response)
                        } else {
                            // 처리할 Agent가 없으면 에러 메시지 생성
                            val errorMessage = routedMessage.createReply(
                                content = "처리할 수 있는 Agent를 찾을 수 없습니다",
                                sender = "orchestrator",
                                type = MessageType.ERROR
                            )
                            nextMessages.add(errorMessage)
                        }
                    }
                }
                
                currentMessages = nextMessages
                
                // 무한 루프 방지 (최대 10번 반복)
                if (processedMessages.size > 10) {
                    break
                }
            }
            
            status = OrchestratorStatus.COMPLETED
            
        } catch (e: Exception) {
            status = OrchestratorStatus.ERROR
            
            val errorMessage = initialMessage.createReply(
                content = "Orchestrator 실행 중 오류 발생: ${e.message}",
                sender = "orchestrator",
                type = MessageType.ERROR
            )
            emit(errorMessage)
        }
    }
    
    override fun getStatus(): OrchestratorStatus = status
    
    private fun findAgentForMessage(message: Message, agents: List<Agent>): Agent? {
        // 특정 Agent 지정된 경우
        message.receiver?.let { receiverId ->
            return agents.find { it.id == receiverId }
        }
        
        // 메시지 타입에 따라 적절한 Agent 찾기
        return when (message.type) {
            MessageType.PROMPT -> agents.find { it.capabilities.contains("prompt_processing") }
            MessageType.DATA -> agents.find { it.capabilities.contains("data_provision") }
            MessageType.RESULT -> agents.find { it.capabilities.contains("result_processing") }
            MessageType.BRANCH -> agents.find { it.capabilities.contains("conditional_routing") }
            MessageType.MERGE -> agents.find { it.capabilities.contains("message_merging") }
            MessageType.TOOL_CALL -> agents.find { agent ->
                agent.getTools().any { tool -> tool.name == message.metadata["toolName"] }
            }
            else -> agents.find { it.canHandle(message) }
        }
    }
    
    fun getMessageHistory(): List<Message> = messageHistory.toList()
}

/**
 * Spice 특화 Orchestrator
 */
class SpiceOrchestrator : Orchestrator {
    private val defaultOrchestrator = DefaultOrchestrator()
    
    override suspend fun orchestrate(
        initialMessage: Message,
        agents: List<Agent>,
        router: MessageRouter
    ): Flow<Message> {
        // Spice Flow Graph 특화 라우터 사용
        val spiceRouter = MessageRouter.createSpiceFlowRules()
        
        return defaultOrchestrator.orchestrate(initialMessage, agents, spiceRouter)
    }
    
    override fun getStatus(): OrchestratorStatus = defaultOrchestrator.getStatus()
}

/**
 * 워크플로우 빌더 - 멘타트 Spice Flow Graph를 Spice 워크플로우로 변환
 */
class WorkflowBuilder {
    private val agents = mutableListOf<Agent>()
    private val router = MessageRouter()
    
    fun addAgent(agent: Agent): WorkflowBuilder {
        agents.add(agent)
        return this
    }
    
    fun addRoutingRule(rule: MessageRoutingRule): WorkflowBuilder {
        router.addRule(rule)
        return this
    }
    
    fun build(): SpiceWorkflow {
        return SpiceWorkflow(agents.toList(), router)
    }
    
    companion object {
        /**
         * 멘타트 Spice Flow Graph JSON을 Spice 워크플로우로 변환
         */
        fun fromMentatSpiceFlow(spiceFlowJson: String): WorkflowBuilder {
            val builder = WorkflowBuilder()
            
            // TODO: Spice Flow Graph JSON 파싱 및 Agent 생성 로직 구현
            // 현재는 예시 구현
            
            return builder
        }
    }
}

/**
 * Spice 워크플로우 실행 단위
 */
data class SpiceWorkflow(
    val agents: List<Agent>,
    val router: MessageRouter
) {
    suspend fun execute(initialMessage: Message): Flow<Message> {
        val orchestrator = SpiceOrchestrator()
        return orchestrator.orchestrate(initialMessage, agents, router)
    }
} 