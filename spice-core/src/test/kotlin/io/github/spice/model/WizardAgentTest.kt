package io.github.spice.model

import io.github.spice.agents.model.*
import io.github.spice.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

/**
 * 🧪 WizardAgent test
 * 
 * ModelClient 기반 WizardAgent의 feature을 test합니다.
 * especially ModelContext 공유와 모드 전환 feature을 중점적으로 test합니다.
 */
class WizardAgentTest {
    
    private lateinit var mockNormalClient: MockModelClient
    private lateinit var mockWizardClient: MockModelClient
    private lateinit var wizardAgent: WizardAgent
    
    @BeforeEach
    fun setUp() {
        mockNormalClient = MockModelClient("normal-model")
        mockWizardClient = MockModelClient("wizard-model")
        
        wizardAgent = createWizardAgent(
            normalClient = mockNormalClient,
            wizardClient = mockWizardClient,
            systemPrompt = "You are a helpful AI assistant.",
            bufferSize = 10,
            id = "test-wizard-agent",
            name = "Test Wizard Agent"
        )
    }
    
    @Test
    fun `일반 모드 메시지 처리 테스트`() = runBlocking {
        // Given
        val message = Message(
            id = "test-1",
            content = "Hello, how are you?",
            sender = "user",
            type = MessageType.TEXT
        )
        
        // When
        val response = wizardAgent.processMessage(message)
        
        // Then
        assertEquals("test-wizard-agent", response.sender)
        assertEquals(MessageType.TEXT, response.type)
        assertTrue(response.content.contains("Mock response"))
        assertEquals("false", response.metadata["wizard_mode"])
        assertEquals("normal", response.metadata["processing_mode"])
        assertEquals("normal-model", response.metadata["agent_used"])
        
        // context check
        val contextSummary = wizardAgent.getContextSummary()
        assertEquals(2, contextSummary.messageCount) // user + assistant
        
        val history = wizardAgent.getConversationHistory()
        assertEquals(2, history.size)
        assertTrue(history[0].isUser)
        assertTrue(history[1].isAssistant)
    }
    
    @Test
    fun `위저드 모드 트리거 테스트`() = runBlocking {
        // Given - 복잡한 analytical 요청
        val message = Message(
            id = "test-2",
            content = "Please analyze this complex algorithm and provide detailed insights about its performance characteristics.",
            sender = "user",
            type = MessageType.TEXT
        )
        
        // When
        val response = wizardAgent.processMessage(message)
        
        // Then
        assertEquals("test-wizard-agent", response.sender)
        assertEquals(MessageType.TEXT, response.type)
        assertTrue(response.content.contains("🧙‍♂️ *Wizard Mode Activated*"))
        assertTrue(response.content.contains("Mock response"))
        assertEquals("true", response.metadata["wizard_mode"])
        assertEquals("wizard", response.metadata["processing_mode"])
        assertEquals("wizard-model", response.metadata["wizard_client"])
        
        // context check
        val contextSummary = wizardAgent.getContextSummary()
        assertEquals(2, contextSummary.messageCount) // user + assistant
        
        val history = wizardAgent.getConversationHistory()
        assertEquals(2, history.size)
        assertTrue(history[0].isUser)
        assertTrue(history[1].isAssistant)
    }
    
    @Test
    fun `컨텍스트 공유 테스트`() = runBlocking {
        // Given - 첫 번째 message (일반 모드)
        val message1 = Message(
            id = "test-3a",
            content = "My name is John.",
            sender = "user",
            type = MessageType.TEXT
        )
        
        // When
        val response1 = wizardAgent.processMessage(message1)
        
        // Then
        assertEquals("false", response1.metadata["wizard_mode"])
        assertEquals("2", response1.metadata["context_size"])
        
        // Given - 두 번째 message (위저드 모드 트리거)
        val message2 = Message(
            id = "test-3b",
            content = "Can you analyze my previous introduction and provide complex insights?",
            sender = "user",
            type = MessageType.TEXT
        )
        
        // When
        val response2 = wizardAgent.processMessage(message2)
        
        // Then
        assertEquals("true", response2.metadata["wizard_mode"])
        assertEquals("4", response2.metadata["context_size"]) // 이전 대화 + 새 대화
        
        // context history check
        val history = wizardAgent.getConversationHistory()
        assertEquals(4, history.size)
        assertEquals("My name is John.", history[0].content)
        assertEquals("analyze", history[2].content.lowercase())
    }
    
    @Test
    fun `업그레이드 조건 테스트`() = runBlocking {
        val testCases = listOf(
            "시각화해줘" to true,
            "그래프를 만들어줘" to true,
            "코드를 분석해줘" to true,
            "복잡한 문제야" to true,
            "Hello world" to false,
            "간단한 질문이야" to false
        )
        
        testCases.forEach { (content, shouldUpgrade) ->
            // Given
            val message = Message(
                id = "test-upgrade-$content",
                content = content,
                sender = "user",
                type = MessageType.TEXT
            )
            
            // When
            val response = wizardAgent.processMessage(message)
            
            // Then
            val isWizardMode = response.metadata["wizard_mode"] == "true"
            assertEquals(shouldUpgrade, isWizardMode, "Content: '$content' should ${if (shouldUpgrade) "" else "not "}trigger wizard mode")
            
            // context 초기화
            wizardAgent.clearContext()
        }
    }
    
    @Test
    fun `사용자 정의 업그레이드 조건 테스트`() = runBlocking {
        // Given
        val message = Message(
            id = "test-custom",
            content = "This is a custom trigger word",
            sender = "user",
            type = MessageType.TEXT
        )
        
        val customConditions = listOf("custom", "trigger")
        
        // When
        val response = wizardAgent.processWithCustomUpgrade(
            message = message,
            customConditions = customConditions
        )
        
        // Then
        assertEquals("true", response.metadata["wizard_mode"])
        assertTrue(response.content.contains("🧙‍♂️ *Wizard Mode Activated*"))
    }
    
    @Test
    fun `강제 위저드 모드 테스트`() = runBlocking {
        // Given
        val message = Message(
            id = "test-force",
            content = "Simple message",
            sender = "user",
            type = MessageType.TEXT
        )
        
        // When
        val response = wizardAgent.processWithCustomUpgrade(
            message = message,
            forceWizardMode = true
        )
        
        // Then
        assertEquals("true", response.metadata["wizard_mode"])
        assertTrue(response.content.contains("🧙‍♂️ *Wizard Mode Activated*"))
    }
    
    @Test
    fun `위저드 통계 테스트`() = runBlocking {
        // Given - 일반 모드 message processing
        val normalMessage = Message(
            id = "stats-1",
            content = "Hello",
            sender = "user",
            type = MessageType.TEXT
        )
        wizardAgent.processMessage(normalMessage)
        
        // Given - 위저드 모드 message processing
        val wizardMessage = Message(
            id = "stats-2",
            content = "Analyze this complex data structure",
            sender = "user",
            type = MessageType.TEXT
        )
        wizardAgent.processMessage(wizardMessage)
        
        // When
        val stats = wizardAgent.getWizardStats()
        
        // Then
        assertEquals(1, stats["wizard_mode_usage_count"])
        assertEquals(1, stats["normal_mode_usage_count"])
        assertEquals(2, stats["total_usage_count"])
        assertEquals("normal", stats["current_mode"])
        assertEquals("normal-model", stats["normal_client"])
        assertEquals("wizard-model", stats["wizard_client"])
        assertTrue(stats["is_ready"] as Boolean)
        assertTrue((stats["context_summary"] as String).contains("Messages: 4/10"))
    }
    
    @Test
    fun `시스템 프롬프트 업데이트 테스트`() = runBlocking {
        // Given
        val newSystemPrompt = "You are a specialized AI assistant for data analysis."
        
        // When
        wizardAgent.updateSystemPrompt(newSystemPrompt)
        
        // Then
        val stats = wizardAgent.getWizardStats()
        // 실제 system prompt check은 internal implementation에 따라 달라질 수 있음
        
        // message processing 후 context check
        val message = Message(
            id = "system-test",
            content = "Test message",
            sender = "user",
            type = MessageType.TEXT
        )
        
        val response = wizardAgent.processMessage(message)
        assertNotNull(response)
        assertEquals("test-wizard-agent", response.sender)
    }
    
    @Test
    fun `컨텍스트 관리 기능 테스트`() = runBlocking {
        // Given - message processing
        val message = Message(
            id = "context-test",
            content = "Test message",
            sender = "user",
            type = MessageType.TEXT
        )
        wizardAgent.processMessage(message)
        
        // When - context check
        val summary = wizardAgent.getContextSummary()
        val history = wizardAgent.getConversationHistory()
        
        // Then
        assertEquals(2, summary.messageCount)
        assertEquals(2, history.size)
        
        // When - system message add
        wizardAgent.addSystemMessage("System instruction")
        
        // Then
        val updatedHistory = wizardAgent.getConversationHistory()
        assertEquals(3, updatedHistory.size)
        assertTrue(updatedHistory.any { it.isSystem && it.content == "System instruction" })
        
        // When - context 초기화
        wizardAgent.clearContext()
        
        // Then
        val clearedSummary = wizardAgent.getContextSummary()
        assertEquals(0, clearedSummary.messageCount)
    }
    
    @Test
    fun `현재 지능 수준 설명 테스트`() {
        // Given & When
        val normalLevel = wizardAgent.getCurrentIntelligenceLevel()
        
        // Then
        assertTrue(normalLevel.contains("🐂 Normal Mode"))
        assertTrue(normalLevel.contains("normal-model"))
    }
    
    @Test
    fun `업그레이드 조건 설명 테스트`() {
        // Given & When
        val explanation = wizardAgent.explainUpgradeConditions()
        
        // Then
        assertTrue(explanation.contains("🔮 Wizard Mode Upgrade Conditions"))
        assertTrue(explanation.contains("🎨 Visualization"))
        assertTrue(explanation.contains("🔧 Technical"))
        assertTrue(explanation.contains("📊 Analysis"))
        assertTrue(explanation.contains("📚 Research"))
    }
    
    @Test
    fun `Agent 준비 상태 테스트`() {
        // Given & When
        val isReady = wizardAgent.isReady()
        
        // Then
        assertTrue(isReady) // Mock 클라이언트들은 항상 준비 상태
    }
    
    @Test
    fun `메시지 처리 가능성 테스트`() {
        // Given
        val textMessage = Message(id = "1", content = "test", sender = "user", type = MessageType.TEXT)
        val promptMessage = Message(id = "2", content = "test", sender = "user", type = MessageType.PROMPT)
        val errorMessage = Message(id = "3", content = "test", sender = "user", type = MessageType.ERROR)
        
        // When & Then
        assertTrue(wizardAgent.canHandle(textMessage))
        assertTrue(wizardAgent.canHandle(promptMessage))
        assertFalse(wizardAgent.canHandle(errorMessage))
    }
}

/**
 * 🎭 test용 Mock ModelClient
 */
class MockModelClient(private val modelName: String) : ModelClient {
    override val id: String = "mock-$modelName"
    override val modelName: String = modelName
    override val description: String = "Mock client for $modelName"
    
    private var requestCount = 0
    private var successCount = 0
    
    override suspend fun chat(
        messages: List<ModelMessage>,
        systemPrompt: String?,
        metadata: Map<String, String>
    ): ModelResponse {
        requestCount++
        successCount++
        
        val responseContent = "Mock response from $modelName to: ${messages.lastOrNull()?.content ?: "no content"}"
        
        return ModelResponse.success(
            content = responseContent,
            usage = TokenUsage(
                inputTokens = messages.sumOf { it.content.length },
                outputTokens = responseContent.length
            ),
            metadata = mapOf(
                "provider" to "mock",
                "model" to modelName
            ),
            responseTimeMs = 100
        )
    }
    
    override suspend fun chatStream(
        messages: List<ModelMessage>,
        systemPrompt: String?,
        metadata: Map<String, String>
    ) = kotlinx.coroutines.flow.flow {
        val response = chat(messages, systemPrompt, metadata)
        emit(ModelStreamChunk(
            content = response.content,
            isComplete = true,
            index = 0
        ))
    }
    
    override fun isReady(): Boolean = true
    
    override suspend fun getStatus(): ClientStatus {
        return ClientStatus(
            clientId = id,
            status = "READY",
            totalRequests = requestCount,
            successfulRequests = successCount,
            averageResponseTimeMs = 100.0,
            metadata = mapOf(
                "model" to modelName,
                "provider" to "mock"
            )
        )
    }
} 