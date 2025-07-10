package io.github.spice

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.*

/**
 * Core Message class for JVM-Autogen
 * All agent-to-agent communication happens via Message.
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
     * Create a reply message based on the current one
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
     * Forward message to another agent (with updated receiver/timestamp)
     */
    fun forward(newReceiver: String): Message {
        return copy(
            receiver = newReceiver,
            timestamp = Instant.now().toEpochMilli()
        )
    }

    /**
     * Add metadata to the message
     */
    fun withMetadata(key: String, value: String): Message {
        return copy(metadata = metadata + (key to value))
    }

    /**
     * Change the message type
     */
    fun withType(newType: MessageType): Message {
        return copy(type = newType)
    }
}

/**
 * Definition of message types
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
    WORKFLOW_END,   // 워크플로우 종료
    INTERRUPT,       // 인터럽트 메시지,
    RESUME
}

/**
 * Reason for an interrupt message (for UI, routing, etc)
 */
enum class InterruptReason {
    CLARIFICATION_NEEDED,
    USER_CONFIRMATION,
    MISSING_DATA
}

/**
 * Message routing rule (used for DAG transformation)
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
 * Message router - handles Mentat DAG node transformation logic
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
         * Convert Spice Flow Graph rules into routing rules
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