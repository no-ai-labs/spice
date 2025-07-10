package io.github.spice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MessageTest {
    
    @Test
    fun `메시지 생성 테스트`() {
        val message = Message(
            content = "안녕하세요",
            sender = "user",
            type = MessageType.TEXT
        )
        
        assertNotNull(message.id)
        assertEquals("안녕하세요", message.content)
        assertEquals("user", message.sender)
        assertEquals(MessageType.TEXT, message.type)
        assertTrue(message.timestamp > 0)
    }
    
    @Test
    fun `메시지 응답 생성 테스트`() {
        val originalMessage = Message(
            content = "질문입니다",
            sender = "user",
            type = MessageType.TEXT
        )
        
        val reply = originalMessage.createReply(
            content = "답변입니다",
            sender = "assistant"
        )
        
        assertEquals("답변입니다", reply.content)
        assertEquals("assistant", reply.sender)
        assertEquals("user", reply.receiver)
        assertEquals(originalMessage.id, reply.parentId)
        assertEquals(originalMessage.id, reply.conversationId)
    }
    
    @Test
    fun `메시지 전달 테스트`() {
        val message = Message(
            content = "전달할 메시지",
            sender = "agent1",
            receiver = "agent2"
        )
        
        val forwarded = message.forward("agent3")
        
        assertEquals("agent3", forwarded.receiver)
        assertEquals("agent1", forwarded.sender)
        assertEquals("전달할 메시지", forwarded.content)
    }
    
    @Test
    fun `메타데이터 추가 테스트`() {
        val message = Message(
            content = "테스트",
            sender = "test"
        )
        
        val withMetadata = message.withMetadata("key", "value")
        
        assertEquals("value", withMetadata.metadata["key"])
        assertEquals("테스트", withMetadata.content)
    }
    
    @Test
    fun `메시지 타입 변경 테스트`() {
        val message = Message(
            content = "테스트",
            sender = "test",
            type = MessageType.TEXT
        )
        
        val withNewType = message.withType(MessageType.SYSTEM)
        
        assertEquals(MessageType.SYSTEM, withNewType.type)
        assertEquals("테스트", withNewType.content)
    }
} 