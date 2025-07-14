package io.github.spice

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentEngineInterruptTest {
    
    @Test
    fun `Agent 인터럽트 및 재개 테스트`() = runBlocking {
        // Given: FakeAgent generation - 특정 입력에 대해 INTERRUPT message 반환
        val fakeAgent = FakeAgent()
        
        // Given: AgentEngine generation 및 Agent 등록
        val agentEngine = AgentEngine()
        agentEngine.registerAgent(fakeAgent)
        
        // When: 인터럽트를 발생시키는 message 전송
        val interruptMessage = Message(
            content = "interrupt_me",
            sender = "user",
            type = MessageType.TEXT
        )
        
        val interruptResult = agentEngine.receive(interruptMessage)
        
        // Then: 인터럽트 response validation
        assertTrue(interruptResult.success, "인터럽트 처리가 성공해야 합니다")
        assertEquals(MessageType.INTERRUPT, interruptResult.response.type, "응답 타입이 INTERRUPT여야 합니다")
        assertEquals("true", interruptResult.metadata["interrupted"], "메타데이터에 interrupted=true가 포함되어야 합니다")
        assertEquals(fakeAgent.id, interruptResult.agentId, "Agent ID가 일치해야 합니다")
        
        // When: resumeAgent를 호출하여 정상적인 response 요청
        val resumeMessage = Message(
            content = "resume_normal",
            sender = "user",
            type = MessageType.TEXT
        )
        
        val resumeResult = agentEngine.resumeAgent(
            contextId = interruptMessage.conversationId ?: interruptMessage.id,
            reply = resumeMessage
        )
        
        // Then: 재개 후 정상 response validation
        assertTrue(resumeResult.success, "재개 후 처리가 성공해야 합니다")
        assertEquals(MessageType.TEXT, resumeResult.response.type, "재개 후 응답 타입이 TEXT여야 합니다")
        assertEquals("Processed: resume_normal", resumeResult.response.content, "정상적인 응답 내용이 반환되어야 합니다")
        assertEquals(fakeAgent.id, resumeResult.agentId, "Agent ID가 일치해야 합니다")
    }
    
    @Test
    fun `다양한 인터럽트 케이스 테스트`() = runBlocking {
        // Given: FakeAgent generation 및 등록
        val fakeAgent = FakeAgent()
        val agentEngine = AgentEngine()
        agentEngine.registerAgent(fakeAgent)
        
        // Test Case 1: "help_needed" 입력about 인터럽트
        val helpMessage = Message(
            content = "help_needed",
            sender = "user",
            type = MessageType.TEXT
        )
        
        val helpResult = agentEngine.receive(helpMessage)
        
        assertTrue(helpResult.success, "help_needed 인터럽트 처리가 성공해야 합니다")
        assertEquals(MessageType.INTERRUPT, helpResult.response.type, "응답 타입이 INTERRUPT여야 합니다")
        assertEquals("true", helpResult.metadata["interrupted"], "메타데이터에 interrupted=true가 포함되어야 합니다")
        assertEquals("I need help with this task", helpResult.response.content, "인터럽트 메시지가 정확해야 합니다")
        
        // Test Case 2: 정상 message는 인터럽트되지 않음
        val normalMessage = Message(
            content = "normal_message",
            sender = "user",
            type = MessageType.TEXT
        )
        
        val normalResult = agentEngine.receive(normalMessage)
        
        assertTrue(normalResult.success, "정상 메시지 처리가 성공해야 합니다")
        assertEquals(MessageType.TEXT, normalResult.response.type, "응답 타입이 TEXT여야 합니다")
        assertEquals("false", normalResult.metadata["interrupted"] ?: "false", "메타데이터에 interrupted=false여야 합니다")
        assertEquals("Processed: normal_message", normalResult.response.content, "정상적인 응답 내용이 반환되어야 합니다")
    }
    
    @Test
    fun `인터럽트된 Agent 상태 확인 테스트`() = runBlocking {
        // Given: FakeAgent generation 및 등록
        val fakeAgent = FakeAgent()
        val agentEngine = AgentEngine()
        agentEngine.registerAgent(fakeAgent)
        
        // When: 인터럽트 message 전송
        val interruptMessage = Message(
            content = "interrupt_me",
            sender = "user",
            type = MessageType.TEXT
        )
        
        val interruptResult = agentEngine.receive(interruptMessage)
        
        // Then: 인터럽트 status check
        assertNotNull(interruptResult, "인터럽트 결과가 null이 아니어야 합니다")
        assertTrue(interruptResult.success, "인터럽트 처리가 성공해야 합니다")
        assertEquals("true", interruptResult.metadata["interrupted"], "인터럽트 메타데이터가 설정되어야 합니다")
        
        // When: 같은 contextin 다시 message 전송 (재개 전)
        val contextId = interruptMessage.conversationId ?: interruptMessage.id
        val resumeMessage = Message(
            content = "continue_work",
            sender = "user",
            type = MessageType.TEXT,
            conversationId = contextId
        )
        
        val resumeResult = agentEngine.resumeAgent(contextId, resumeMessage)
        
        // Then: 재개 후 정상 동작 check
        assertTrue(resumeResult.success, "재개 후 처리가 성공해야 합니다")
        assertEquals(MessageType.TEXT, resumeResult.response.type, "재개 후 응답 타입이 TEXT여야 합니다")
        assertEquals("Processed: continue_work", resumeResult.response.content, "재개 후 정상적인 응답이 반환되어야 합니다")
    }
}

/**
 * test용 FakeAgent class
 * 특정 입력에 대해 INTERRUPT message를 반환하도록 setting
 */
class FakeAgent(
    id: String = "fake-agent",
    name: String = "Fake Test Agent",
    description: String = "Agent for testing interrupt functionality"
) : BaseAgent(id, name, description, listOf("testing", "interrupt")) {
    
    override suspend fun processMessage(message: Message): Message {
        return when (message.content) {
            "interrupt_me" -> {
                // 인터럽트 message 반환
                message.createReply(
                    content = "Need user confirmation to proceed",
                    sender = id,
                    type = MessageType.INTERRUPT,
                    metadata = mapOf(
                        "reason" to "user_confirmation",
                        "required_action" to "confirm_proceed"
                    )
                )
            }
            "help_needed" -> {
                // 도움 요청 인터럽트
                message.createReply(
                    content = "I need help with this task",
                    sender = id,
                    type = MessageType.INTERRUPT,
                    metadata = mapOf(
                        "reason" to "clarification_needed",
                        "help_type" to "task_clarification"
                    )
                )
            }
            else -> {
                // 정상적인 message processing
                message.createReply(
                    content = "Processed: ${message.content}",
                    sender = id,
                    type = MessageType.TEXT,
                    metadata = mapOf(
                        "status" to "success",
                        "processed_at" to System.currentTimeMillis().toString()
                    )
                )
            }
        }
    }
    
    override fun canHandle(message: Message): Boolean {
        return message.type in listOf(
            MessageType.TEXT, 
            MessageType.PROMPT, 
            MessageType.SYSTEM
        )
    }
    
    override fun isReady(): Boolean = true
} 