package io.github.spice

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*

/**
 * 🧪 Resume/Pause feature 종합 test
 * 
 * 실제 시나리오through Resume/Pause feature의 동작을 validation합니다.
 */
class AgentEngineResumePauseTest {
    
    private lateinit var agentEngine: AgentEngine
    private lateinit var interruptAgent: TestInterruptAgent
    private lateinit var normalAgent: TestNormalAgent
    
    @BeforeEach
    fun setup() {
        agentEngine = AgentEngine()
        interruptAgent = TestInterruptAgent()
        normalAgent = TestNormalAgent()
        
        agentEngine.registerAgent(interruptAgent)
        agentEngine.registerAgent(normalAgent)
    }
    
    @Test
    fun `test basic pause and resume flow`() = runBlocking {
        println("🧪 Testing basic pause and resume flow")
        
        // 1. Send message that triggers interrupt
        val initialMessage = Message(
            content = "Start long task",
            sender = "user",
            receiver = "interrupt-agent",
            conversationId = "test-context-1"
        )
        
        // 2. Process message - should trigger pause
        val pauseResponse = agentEngine.receive(initialMessage)
        
        // 3. Verify pause state
        assertTrue(pauseResponse.success, "Pause should be successful")
        assertEquals(MessageType.INTERRUPT, pauseResponse.response.type)
        assertEquals("true", pauseResponse.metadata["interrupted"])
        
        // 4. Check context status
        val contextStatus = agentEngine.getContextStatus("test-context-1")
        assertNotNull(contextStatus)
        assertTrue(contextStatus!!.isSuspended)
        assertEquals("interrupt-agent", contextStatus.suspendedAgentId)
        assertNotNull(contextStatus.suspendedAt)
        
        // 5. Try to send another message - should be blocked
        val blockedMessage = Message(
            content = "This should be blocked",
            sender = "user",
            conversationId = "test-context-1"
        )
        
        val blockedResponse = agentEngine.receive(blockedMessage)
        assertFalse(blockedResponse.success, "Message should be blocked during suspension")
        assertEquals("Context suspended", blockedResponse.error)
        
        // 6. Resume with reply
        val resumeMessage = Message(
            content = "Continue with option A",
            sender = "user"
        )
        
        val resumeResponse = agentEngine.resumeAgent("test-context-1", resumeMessage)
        
        // 7. Verify resume success
        assertTrue(resumeResponse.success, "Resume should be successful")
        assertEquals(MessageType.RESUME, resumeResponse.response.type)
        
        // 8. Check context status after resume
        val resumedStatus = agentEngine.getContextStatus("test-context-1")
        assertNotNull(resumedStatus)
        assertFalse(resumedStatus!!.isSuspended)
        assertNull(resumedStatus.suspendedAgentId)
        assertNotNull(resumedStatus.resumedAt)
        
        println("✅ Basic pause and resume flow test passed")
    }
    
    @Test
    fun `test error handling during resume`() = runBlocking {
        println("🧪 Testing error handling during resume")
        
        // 1. Try to resume non-existent context
        val resumeMessage = Message(content = "Resume", sender = "user")
        
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                agentEngine.resumeAgent("non-existent-context", resumeMessage)
            }
        }
        
        // 2. Try to resume non-suspended context
        val normalMessage = Message(
            content = "Normal message",
            sender = "user",
            receiver = "normal-agent",
            conversationId = "normal-context"
        )
        
        agentEngine.receive(normalMessage)
        
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                agentEngine.resumeAgent("normal-context", resumeMessage)
            }
        }
        
        println("✅ Error handling during resume test passed")
    }
    
    @Test
    fun `test force resume functionality`() = runBlocking {
        println("🧪 Testing force resume functionality")
        
        // 1. Create suspended context
        val message = Message(
            content = "Trigger interrupt",
            sender = "user",
            receiver = "interrupt-agent",
            conversationId = "force-context"
        )
        
        agentEngine.receive(message)
        
        // 2. Verify suspension
        val status = agentEngine.getContextStatus("force-context")
        assertTrue(status!!.isSuspended)
        
        // 3. Force resume
        val forceResumeResult = agentEngine.forceResume("force-context")
        assertTrue(forceResumeResult, "Force resume should succeed")
        
        // 4. Verify context is no longer suspended
        val resumedStatus = agentEngine.getContextStatus("force-context")
        assertFalse(resumedStatus!!.isSuspended)
        
        // 5. Should be able to process messages normally now
        val normalMessage = Message(
            content = "Normal processing",
            sender = "user",
            conversationId = "force-context"
        )
        
        val response = agentEngine.receive(normalMessage)
        assertTrue(response.success, "Should process normally after force resume")
        
        println("✅ Force resume functionality test passed")
    }
    
    @Test
    fun `test workflow suspension and resume`() = runBlocking {
        println("🧪 Testing workflow suspension and resume")
        
        val initialMessage = Message(
            content = "Start workflow with interrupt",
            sender = "user",
            receiver = "interrupt-agent",
            conversationId = "workflow-context"
        )
        
        // 1. Start workflow
        val workflowFlow = agentEngine.processWorkflow(initialMessage)
        
        var suspendedDetected = false
        var messageCount = 0
        
        // 2. Collect workflow messages
        workflowFlow.collect { agentMessage ->
            messageCount++
            println("Workflow message ${messageCount}: ${agentMessage.response.type}")
            
            if (agentMessage.response.type == MessageType.INTERRUPT) {
                suspendedDetected = true
                println("Workflow suspended as expected")
            }
        }
        
        // 3. Verify workflow was suspended
        assertTrue(suspendedDetected, "Workflow should have been suspended")
        
        val status = agentEngine.getContextStatus("workflow-context")
        assertTrue(status!!.isSuspended, "Context should be suspended after workflow")
        
        println("✅ Workflow suspension and resume test passed")
    }
    
    @Test
    fun `test context cleanup with suspended contexts`() = runBlocking {
        println("🧪 Testing context cleanup with suspended contexts")
        
        // 1. Create multiple suspended contexts
        repeat(3) { index ->
            val message = Message(
                content = "Create suspended context $index",
                sender = "user",
                receiver = "interrupt-agent",
                conversationId = "cleanup-context-$index"
            )
            agentEngine.receive(message)
        }
        
        // 2. Verify contexts exist and are suspended
        repeat(3) { index ->
            val status = agentEngine.getContextStatus("cleanup-context-$index")
            assertNotNull(status)
            assertTrue(status!!.isSuspended)
        }
        
        // 3. Cleanup with short timeout (should not clean up recent contexts)
        agentEngine.cleanupExpiredContexts(maxAgeMs = 100)
        
        // 4. Verify contexts still exist
        repeat(3) { index ->
            val status = agentEngine.getContextStatus("cleanup-context-$index")
            assertNotNull(status, "Context should still exist after cleanup")
        }
        
        println("✅ Context cleanup with suspended contexts test passed")
    }
    
    /**
     * 🧪 Test agent that triggers interrupts
     */
    class TestInterruptAgent : BaseAgent(
        id = "interrupt-agent",
        name = "Test Interrupt Agent",
        description = "Agent that triggers interrupts for testing"
    ) {
        override suspend fun processMessage(message: Message): Message {
            return when {
                message.content.contains("interrupt", ignoreCase = true) ||
                message.content.contains("long task", ignoreCase = true) -> {
                    // Trigger interrupt
                    message.createReply(
                        content = "Need user input to continue",
                        sender = id,
                        type = MessageType.INTERRUPT,
                        metadata = mapOf("reason" to "user_input_required")
                    )
                }
                message.type == MessageType.RESUME -> {
                    // Handle resume
                    message.createReply(
                        content = "Resumed successfully with: ${message.content}",
                        sender = id,
                        type = MessageType.TEXT,
                        metadata = mapOf("resumed" to "true")
                    )
                }
                else -> {
                    // Normal processing
                    message.createReply(
                        content = "Processed: ${message.content}",
                        sender = id,
                        type = MessageType.TEXT
                    )
                }
            }
        }
    }
    
    /**
     * 🧪 Test agent for normal processing
     */
    class TestNormalAgent : BaseAgent(
        id = "normal-agent",
        name = "Test Normal Agent",
        description = "Agent for normal processing testing"
    ) {
        override suspend fun processMessage(message: Message): Message {
            return message.createReply(
                content = "Normal processing: ${message.content}",
                sender = id,
                type = MessageType.TEXT
            )
        }
    }
}

/**
 * 🎯 Resume/Pause 사용 예시
 */
object ResumePauseUsageExample {
    
    fun demonstrateUsage() = runBlocking {
        println("🌶️ Resume/Pause Usage Example")
        println("=" * 50)
        
        val agentEngine = AgentEngine()
        
        // 1. Register an agent that can trigger interrupts
        val interruptAgent = object : BaseAgent(
            id = "worker-agent",
            name = "Worker Agent",
            description = "Agent that may need user input"
        ) {
            override suspend fun processMessage(message: Message): Message {
                return when {
                    message.content.contains("complex task") -> {
                        // Simulate need for user input
                        message.createReply(
                            content = "I need to know: Option A or Option B?",
                            sender = id,
                            type = MessageType.INTERRUPT,
                            metadata = mapOf("reason" to "user_choice_required")
                        )
                    }
                    message.type == MessageType.RESUME -> {
                        message.createReply(
                            content = "Continuing with your choice: ${message.content}",
                            sender = id,
                            type = MessageType.TEXT
                        )
                    }
                    else -> {
                        message.createReply(
                            content = "Completed: ${message.content}",
                            sender = id,
                            type = MessageType.TEXT
                        )
                    }
                }
            }
        }
        
        agentEngine.registerAgent(interruptAgent)
        
        // 2. Send message that triggers interrupt
        val taskMessage = Message(
            content = "Please handle this complex task",
            sender = "user",
            receiver = "worker-agent",
            conversationId = "demo-session"
        )
        
        println("Sending task message...")
        val response = agentEngine.receive(taskMessage)
        
        if (response.response.type == MessageType.INTERRUPT) {
            println("✋ Agent paused: ${response.response.content}")
            
            // 3. Check context status
            val status = agentEngine.getContextStatus("demo-session")
            println("Context status: suspended=${status?.isSuspended}, agent=${status?.suspendedAgentId}")
            
            // 4. Resume with user input
            val userChoice = Message(
                content = "Option A",
                sender = "user"
            )
            
            println("Resuming with user choice...")
            val resumeResponse = agentEngine.resumeAgent("demo-session", userChoice)
            println("✅ Resumed: ${resumeResponse.response.content}")
        }
        
        println("Demo completed successfully!")
    }
} 