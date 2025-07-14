package io.github.spice.model

import io.github.spice.model.clients.OpenAIClient
import io.github.spice.model.clients.ClaudeClient
import io.github.spice.agents.model.createGPTAgent
import io.github.spice.agents.model.createClaudeAgent
import io.github.spice.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

/**
 * 🧪 ModelClient system test
 * 
 * ModelMessage, ModelContext, ModelClient, and 새로운 Agent들의
 * 통합 test를 수행합니다.
 */
class ModelClientTest {
    
    private lateinit var mockConfig: ModelClientConfig
    
    @BeforeEach
    fun setUp() {
        mockConfig = ModelClientConfig(
            apiKey = "test-key",
            defaultModel = "gpt-3.5-turbo"
        )
    }
    
    @Test
    fun `ModelMessage 생성 및 속성 테스트`() {
        // Given
        val userMessage = ModelMessage.user("Hello, world!")
        val systemMessage = ModelMessage.system("You are a helpful assistant.")
        val assistantMessage = ModelMessage.assistant("Hello! How can I help you?")
        
        // Then
        assertTrue(userMessage.isUser)
        assertFalse(userMessage.isSystem)
        assertEquals("user", userMessage.role)
        assertEquals("Hello, world!", userMessage.content)
        
        assertTrue(systemMessage.isSystem)
        assertFalse(systemMessage.isUser)
        assertEquals("system", systemMessage.role)
        
        assertTrue(assistantMessage.isAssistant)
        assertEquals("assistant", assistantMessage.role)
    }
    
    @Test
    fun `ModelContext 버퍼링 테스트`() = runBlocking {
        // Given
        val context = ModelContext(bufferSize = 3)
        
        // When
        context.addUser("Message 1")
        context.addAssistant("Response 1")
        context.addUser("Message 2")
        context.addAssistant("Response 2")
        
        // Then
        assertEquals(4, context.size())
        
        // When - 버퍼 size 초과
        context.addUser("Message 3")
        
        // Then - 가장 오래된 message 제거됨
        assertEquals(3, context.size())
        val messages = context.getMessages()
        assertEquals("Response 1", messages[0].content) // 첫 번째 메시지 제거됨
    }
    
    @Test
    fun `ModelContext 시스템 프롬프트 처리 테스트`() = runBlocking {
        // Given
        val systemPrompt = "You are a helpful assistant."
        val context = ModelContext(bufferSize = 5, systemPrompt = systemPrompt)
        
        // When
        context.addUser("Hello")
        val fullMessages = context.getFullMessages()
        
        // Then
        assertEquals(2, fullMessages.size)
        assertTrue(fullMessages[0].isSystem)
        assertEquals(systemPrompt, fullMessages[0].content)
        assertTrue(fullMessages[1].isUser)
        assertEquals("Hello", fullMessages[1].content)
    }
    
    @Test
    fun `ModelContext 요약 정보 테스트`() = runBlocking {
        // Given
        val context = ModelContext(bufferSize = 10)
        
        // When
        context.addUser("User message 1")
        context.addAssistant("Assistant response 1")
        context.addUser("User message 2")
        
        val summary = context.getSummary()
        
        // Then
        assertEquals(3, summary.messageCount)
        assertEquals(10, summary.bufferSize)
        assertEquals(30.0, summary.bufferUsage)
        assertEquals(2, summary.roleDistribution["user"])
        assertEquals(1, summary.roleDistribution["assistant"])
        
        val summaryText = summary.getSummaryText()
        assertTrue(summaryText.contains("Messages: 3/10"))
        assertTrue(summaryText.contains("user:2"))
        assertTrue(summaryText.contains("assistant:1"))
    }
    
    @Test
    fun `MockModelClient 테스트`() = runBlocking {
        // Given
        val mockClient = MockModelClient()
        
        // When
        val response = mockClient.chat(
            messages = listOf(ModelMessage.user("Hello")),
            systemPrompt = "Be helpful"
        )
        
        // Then
        assertTrue(response.success)
        assertTrue(response.content.contains("Mock response"))
        assertEquals("mock-model", mockClient.modelName)
        assertTrue(mockClient.isReady())
        
        val status = mockClient.getStatus()
        assertEquals("READY", status.status)
        assertEquals(1, status.totalRequests)
        assertEquals(1, status.successfulRequests)
    }
    
    @Test
    fun `GPTAgent 생성 및 기본 기능 테스트`() = runBlocking {
        // Given
        val mockClient = MockModelClient()
        val agent = createGPTAgent(
            client = mockClient,
            systemPrompt = "You are a helpful GPT assistant.",
            bufferSize = 5,
            id = "test-gpt-agent",
            name = "Test GPT Agent"
        )
        
        // When
        val inputMessage = Message(
            id = "test-msg-1",
            content = "Hello, GPT!",
            sender = "user",
            type = MessageType.TEXT
        )
        
        val response = agent.processMessage(inputMessage)
        
        // Then
        assertEquals("test-gpt-agent", agent.id)
        assertEquals("Test GPT Agent", agent.name)
        assertTrue(agent.isReady())
        assertTrue(agent.canHandle(inputMessage))
        
        assertEquals("test-gpt-agent", response.sender)
        assertEquals(MessageType.TEXT, response.type)
        assertTrue(response.content.contains("Mock response"))
        
        // context check
        val contextSummary = agent.getContextSummary()
        assertEquals(2, contextSummary.messageCount) // user + assistant
        
        val history = agent.getConversationHistory()
        assertEquals(2, history.size)
        assertTrue(history[0].isUser)
        assertTrue(history[1].isAssistant)
    }
    
    @Test
    fun `ClaudeAgent 생성 및 특화 기능 테스트`() = runBlocking {
        // Given
        val mockClient = MockModelClient()
        val agent = createClaudeAgent(
            client = mockClient,
            systemPrompt = "You are Claude, a helpful AI assistant.",
            bufferSize = 5,
            id = "test-claude-agent",
            name = "Test Claude Agent"
        )
        
        // When - basic message processing
        val inputMessage = Message(
            id = "test-msg-2",
            content = "Hello, Claude!",
            sender = "user",
            type = MessageType.TEXT
        )
        
        val response = agent.processMessage(inputMessage)
        
        // Then
        assertEquals("test-claude-agent", agent.id)
        assertEquals("Test Claude Agent", agent.name)
        assertTrue(agent.isReady())
        
        // When - analytical feature test
        val analysisResult = agent.analyzeContent("This is test content", "text")
        
        // Then
        assertTrue(analysisResult.contains("Mock response"))
        
        // When - summary feature test
        val summaryResult = agent.summarizeContent("Long content to summarize", "concise")
        
        // Then
        assertTrue(summaryResult.contains("Mock response"))
    }
    
    @Test
    fun `Agent 상태 정보 테스트`() = runBlocking {
        // Given
        val mockClient = MockModelClient()
        val agent = createGPTAgent(
            client = mockClient,
            systemPrompt = "Test system prompt",
            id = "status-test-agent"
        )
        
        // When
        val status = agent.getAgentStatus()
        
        // Then
        assertEquals("status-test-agent", status["agent_id"])
        assertEquals("mock-model", status["model_client"])
        assertEquals(true, status["is_ready"])
        assertTrue(status["system_prompt_length"] as Int > 0)
        assertTrue((status["capabilities"] as List<*>).contains("chat"))
    }
    
    @Test
    fun `컨텍스트 관리 기능 테스트`() = runBlocking {
        // Given
        val mockClient = MockModelClient()
        val agent = createGPTAgent(client = mockClient, bufferSize = 3)
        
        // When - multiple message processing
        repeat(5) { i ->
            val message = Message(
                id = "msg-$i",
                content = "Message $i",
                sender = "user",
                type = MessageType.TEXT
            )
            agent.processMessage(message)
        }
        
        // Then - 버퍼 size 제한 check
        val summary = agent.getContextSummary()
        assertEquals(3, summary.messageCount) // 버퍼 크기로 제한됨
        
        // When - context 초기화
        agent.clearContext()
        
        // Then
        val clearedSummary = agent.getContextSummary()
        assertEquals(0, clearedSummary.messageCount)
    }
    
    @Test
    fun `시스템 프롬프트 업데이트 테스트`() = runBlocking {
        // Given
        val mockClient = MockModelClient()
        val agent = createGPTAgent(client = mockClient)
        
        // When
        val newSystemPrompt = "Updated system prompt"
        agent.updateSystemPrompt(newSystemPrompt)
        
        // Then
        val status = agent.getAgentStatus()
        assertEquals(newSystemPrompt.length, status["system_prompt_length"])
        
        // When - system message add
        agent.addSystemMessage("Additional system instruction")
        
        // Then
        val history = agent.getConversationHistory()
        assertTrue(history.any { it.isSystem && it.content == "Additional system instruction" })
    }
}

/**
 * 🎭 Mock ModelClient for testing
 */
class MockModelClient : ModelClient {
    override val id: String = "mock-client"
    override val modelName: String = "mock-model"
    override val description: String = "Mock client for testing"
    
    private var requestCount = 0
    private var successCount = 0
    
    override suspend fun chat(
        messages: List<ModelMessage>,
        systemPrompt: String?,
        metadata: Map<String, String>
    ): ModelResponse {
        requestCount++
        successCount++
        
        val responseContent = "Mock response to: ${messages.lastOrNull()?.content ?: "no content"}"
        
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