package io.github.spice

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.*

/**
 * JVM-Autogen의 핵심 Message 인터페이스
 * 모든 Agent 간 통신은 Message를 통해 이루어집니다.
 */
@Serializable
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val type: MessageType = MessageType.TEXT,
    val sender: String,
    val receiver: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val timestamp: Long = Instant.now().toEpochMilli(),
    val parentId: String? = null,
    val conversationId: String? = null
) {
    
    /**
     * 메시지 체인 생성 - 응답 메시지 생성 시 사용
     */
    fun createReply(
        content: String,
        sender: String,
        type: MessageType = MessageType.TEXT,
        metadata: Map<String, String> = emptyMap()
    ): Message {
        return copy(
            id = UUID.randomUUID().toString(),
            content = content,
            sender = sender,
            receiver = this.sender,
            type = type,
            metadata = metadata,
            timestamp = Instant.now().toEpochMilli(),
            parentId = this.id,
            conversationId = this.conversationId ?: this.id
        )
    }
    
    /**
     * 메시지 전달 - 다른 Agent로 라우팅 시 사용
     */
    fun forward(newReceiver: String): Message {
        return copy(
            receiver = newReceiver,
            timestamp = Instant.now().toEpochMilli()
        )
    }
    
    /**
     * 메타데이터 추가
     */
    fun withMetadata(key: String, value: String): Message {
        return copy(metadata = metadata + (key to value))
    }
    
    /**
     * 메시지 타입 변경
     */
    fun withType(newType: MessageType): Message {
        return copy(type = newType)
    }
}

/**
 * 메시지 타입 정의
 */
enum class MessageType {
    TEXT,           // 일반 텍스트
    SYSTEM,         // 시스템 메시지
    TOOL_CALL,      // 도구 호출
    TOOL_RESULT,    // 도구 실행 결과
    ERROR,          // 에러 메시지
    DATA,           // 데이터 전달
    PROMPT,         // 프롬프트 메시지
    RESULT,         // 최종 결과
    BRANCH,         // 분기 메시지
    MERGE,          // 병합 메시지
    WORKFLOW_START, // 워크플로우 시작
    WORKFLOW_END    // 워크플로우 종료
}

/**
 * 메시지 라우팅 규칙
 */
data class MessageRoutingRule(
    val sourceType: MessageType,
    val targetType: MessageType,
    val condition: (Message) -> Boolean = { true },
    val transformer: (Message) -> Message = { it }
) {
    
    fun canRoute(message: Message): Boolean {
        return message.type == sourceType && condition(message)
    }
    
    fun route(message: Message): Message {
        return transformer(message).withType(targetType)
    }
}

/**
 * 메시지 라우터 - 멘타트의 복잡한 노드 연결 제약조건을 처리
 */
class MessageRouter {
    private val rules = mutableListOf<MessageRoutingRule>()
    
    fun addRule(rule: MessageRoutingRule) {
        rules.add(rule)
    }
    
    fun route(message: Message): List<Message> {
        val applicableRules = rules.filter { it.canRoute(message) }
        
        return if (applicableRules.isEmpty()) {
            listOf(message) // 기본 전달
        } else {
            applicableRules.map { it.route(message) }
        }
    }
    
    companion object {
        /**
         * Spice Flow Graph 규칙을 Message 라우팅 규칙으로 변환
         */
        fun createSpiceFlowRules(): MessageRouter {
            val router = MessageRouter()
            
            // 규칙 1: PROMPT → PROMPT는 두 번째를 SYSTEM으로 변환
            router.addRule(MessageRoutingRule(
                sourceType = MessageType.PROMPT,
                targetType = MessageType.SYSTEM,
                condition = { msg -> msg.metadata["isSecondPrompt"] == "true" },
                transformer = { msg -> msg.withMetadata("autoUpgraded", "promptToSystem") }
            ))
            
            // 규칙 2: DATA → RESULT는 중간에 SYSTEM 메시지 삽입
            router.addRule(MessageRoutingRule(
                sourceType = MessageType.DATA,
                targetType = MessageType.SYSTEM,
                condition = { msg -> msg.metadata["targetType"] == "RESULT" },
                transformer = { msg -> 
                    msg.withMetadata("autoInserted", "dataToResult")
                       .withMetadata("nextTarget", "RESULT")
                }
            ))
            
            // 규칙 3: BRANCH → 여러 경로로 분기
            router.addRule(MessageRoutingRule(
                sourceType = MessageType.BRANCH,
                targetType = MessageType.SYSTEM,
                transformer = { msg ->
                    msg.withMetadata("branchExpanded", "true")
                }
            ))
            
            return router
        }
    }
} 