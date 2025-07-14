package io.github.spice.agents

import io.github.spice.*
import io.github.spice.model.*
import io.github.spice.model.clients.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

/**
 * 🧪 OpenRouterAgent Test Suite
 * 
 * Comprehensive tests for OpenRouter multi-model agent
 */
class OpenRouterAgentTest {
    
    private lateinit var mockOpenRouterClient: MockOpenRouterClient
    private lateinit var openRouterAgent: OpenRouterAgent
    
    @BeforeEach
    fun setUp() {
        mockOpenRouterClient = MockOpenRouterClient()
        
        openRouterAgent = OpenRouterAgent(
            client = mockOpenRouterClient,
            context = ModelContext(bufferSize = 10),
            id = "test-openrouter-agent",
            name = "Test OpenRouter Agent",
            description = "Test OpenRouter Agent for unit testing"
        )
    }
    
    @Test
    fun `basic properties test`() {
        assertEquals("test-openrouter-agent", openRouterAgent.id)
        assertEquals("Test OpenRouter Agent", openRouterAgent.name)
        assertTrue(openRouterAgent.capabilities.contains("multi_model_access"))
        assertTrue(openRouterAgent.capabilities.contains("claude_models"))
        assertTrue(openRouterAgent.capabilities.contains("gpt_models"))
        assertTrue(openRouterAgent.capabilities.contains("unified_api"))
        assertTrue(openRouterAgent.isReady())
    }
    
    @Test
    fun `message processing test`() = runBlocking {
        // Given
        val inputMessage = Message(
            id = "test-msg-1",
            content = "Hello OpenRouter!",
            sender = "user"
        )
        
        // When
        val response = openRouterAgent.processMessage(inputMessage)
        
        // Then
        assertEquals("Test OpenRouter Agent", response.sender)
        assertTrue(response.content.contains("Mock OpenRouter response"))
        assertEquals("openrouter", response.metadata["agent_type"])
        assertEquals("anthropic/claude-3.5-sonnet", response.metadata["model"])
        assertNotNull(response.metadata["model_used"])
        assertNotNull(response.metadata["generation_id"])
        assertNotNull(response.metadata["token_usage"])
        assertNotNull(response.metadata["response_time_ms"])
    }
    
    @Test
    fun `error handling test`() = runBlocking {
        // Given
        mockOpenRouterClient.shouldFail = true
        val inputMessage = Message(
            id = "error-test",
            content = "This should fail",
            sender = "user"
        )
        
        // When
        val response = openRouterAgent.processMessage(inputMessage)
        
        // Then
        assertTrue(response.content.contains("Sorry, OpenRouter connection error occurred") || 
                  response.content.contains("죄송합니다. OpenRouter 연결 오류가 발생했습니다"))
        assertEquals("true", response.metadata["error"])
    }
    
    @Test
    fun `available models test`() = runBlocking {
        // Given & When
        val models = openRouterAgent.getAvailableModels()
        
        // Then
        assertFalse(models.isEmpty())
        assertTrue(models.any { it.id == "anthropic/claude-3.5-sonnet" })
        assertTrue(models.any { it.id == "openai/gpt-4o" })
        assertTrue(models.any { it.id == "meta-llama/llama-3.1-8b-instruct" })
    }
    
    @Test
    fun `current model info test`() = runBlocking {
        // Given & When
        val modelInfo = openRouterAgent.getCurrentModelInfo()
        
        // Then
        assertNotNull(modelInfo)
        assertEquals("anthropic/claude-3.5-sonnet", modelInfo?.id)
        assertEquals("Claude 3.5 Sonnet", modelInfo?.name)
    }
    
    @Test
    fun `status summary test`() = runBlocking {
        // Given
        val message = Message(id = "status-test", content = "Test", sender = "user")
        openRouterAgent.processMessage(message)
        
        // When
        val statusSummary = openRouterAgent.getStatusSummary()
        
        // Then
        assertTrue(statusSummary.contains("🌐 OpenRouter Agent Status"))
        assertTrue(statusSummary.contains("Current Model: anthropic/claude-3.5-sonnet"))
        assertTrue(statusSummary.contains("Model Name: Claude 3.5 Sonnet"))
        assertTrue(statusSummary.contains("Context Length: 200000 tokens"))
    }
    
    @Test
    fun `creative task processing test`() = runBlocking {
        // Given
        val prompt = "Write a creative story about AI"
        val creativity = "creative"
        
        // When
        val response = openRouterAgent.processCreativeTask(prompt, creativity)
        
        // Then
        assertTrue(response.content.contains("Mock OpenRouter response"))
    }
    
    @Test
    fun `analytical task processing test`() = runBlocking {
        // Given
        val data = "Sales data: Q1=100, Q2=150, Q3=200, Q4=180"
        val analysisType = "business"
        
        // When
        val response = openRouterAgent.processAnalyticalTask(data, analysisType)
        
        // Then
        assertTrue(response.content.contains("Mock OpenRouter response"))
    }
    
    @Test
    fun `learning session test`() = runBlocking {
        // Given
        val topic = "Machine Learning"
        
        // When
        val sessionResponse = openRouterAgent.startLearningSession(topic)
        
        // Then
        assertTrue(sessionResponse.content.contains("OpenRouter learning session has started") || 
                  sessionResponse.content.contains("OpenRouter 학습 세션이 시작되었습니다"))
        assertTrue(sessionResponse.content.contains(topic))
    }
    
    @Test
    fun `model recommendation test`() = runBlocking {
        // Given & When
        val creativeModels = openRouterAgent.recommendModel("creative")
        val codeModels = openRouterAgent.recommendModel("code")
        val analysisModels = openRouterAgent.recommendModel("analysis")
        val chatModels = openRouterAgent.recommendModel("chat")
        
        // Then
        assertTrue(creativeModels.contains("anthropic/claude-3.5-sonnet"))
        assertTrue(codeModels.contains("anthropic/claude-3.5-sonnet"))
        assertTrue(analysisModels.contains("openai/gpt-4o"))
        assertTrue(chatModels.contains("openai/gpt-4o-mini"))
    }
    
    @Test
    fun `cost estimation test`() = runBlocking {
        // Given
        val inputTokens = 1000
        val outputTokens = 500
        
        // When
        val costEstimate = openRouterAgent.estimateCost(inputTokens, outputTokens)
        
        // Then
        assertTrue(costEstimate.contains("Estimated cost") || costEstimate.contains("예상 비용"))
    }
    
    @Test
    fun `batch processing test`() = runBlocking {
        // Given
        val messages = listOf(
            "What is AI?",
            "Explain machine learning",
            "What are neural networks?"
        )
        
        // When
        val responses = openRouterAgent.processBatch(messages)
        
        // Then
        assertEquals(3, responses.size)
        responses.forEach { response ->
            assertTrue(response.content.contains("Mock OpenRouter response"))
            assertEquals("openrouter", response.metadata["agent_type"])
        }
    }
    
    @Test
    fun `context management test`() = runBlocking {
        // Given
        val messages = listOf(
            "Hello OpenRouter",
            "How are you?",
            "What can you do?"
        )
        
        // When
        messages.forEach { content ->
            val message = Message(id = "msg-${System.currentTimeMillis()}", content = content, sender = "user")
            openRouterAgent.processMessage(message)
        }
        
        // Then
        val summary = openRouterAgent.getContextSummary()
        assertEquals(6, summary.messageCount) // 3 user + 3 assistant
        
        val history = openRouterAgent.getConversationHistory()
        assertEquals(6, history.size)
        assertTrue(history.any { it.content == "Hello OpenRouter" })
        assertTrue(history.any { it.content == "What can you do?" })
    }
    
    @Test
    fun `system prompt setting test`() = runBlocking {
        // Given
        val systemPrompt = "You are a helpful AI assistant via OpenRouter."
        
        // When
        openRouterAgent.setSystemPrompt(systemPrompt)
        
        // Then
        val history = openRouterAgent.getConversationHistory()
        assertTrue(history.any { it.isSystem && it.content == systemPrompt })
    }
    
    @Test
    fun `model hint processing test`() = runBlocking {
        // Given
        val message = "Analyze this complex data"
        val preferredModel = "anthropic/claude-3-opus"
        
        // When
        val response = openRouterAgent.processWithModelHint(message, preferredModel)
        
        // Then
        assertTrue(response.content.contains("Mock OpenRouter response"))
    }
}

/**
 * Mock OpenRouterClient for testing
 */
class MockOpenRouterClient : OpenRouterClient(
    apiKey = "test-key",
    model = "anthropic/claude-3.5-sonnet"
) {
    var shouldFail = false
    private var requestCount = 0
    
    override val id: String = "mock-openrouter-client"
    override val modelName: String = "anthropic/claude-3.5-sonnet"
    override val description: String = "Mock OpenRouter client for testing"
    
    override suspend fun chat(
        messages: List<ModelMessage>,
        systemPrompt: String?,
        metadata: Map<String, String>
    ): ModelResponse {
        requestCount++
        
        if (shouldFail) {
            return ModelResponse.error("Mock OpenRouter connection error")
        }
        
        val responseContent = "Mock OpenRouter response to: ${messages.lastOrNull()?.content ?: "no content"}"
        
        return ModelResponse.success(
            content = responseContent,
            usage = TokenUsage(
                inputTokens = messages.sumOf { it.content.length },
                outputTokens = responseContent.length
            ),
            responseTimeMs = 1200,
            metadata = mapOf(
                "model_used" to modelName,
                "generation_id" to "gen-${System.currentTimeMillis()}"
            )
        )
    }
    
    override fun isReady(): Boolean = !shouldFail
    
    override suspend fun getStatus(): ClientStatus {
        return ClientStatus(
            clientId = id,
            status = if (isReady()) "READY" else "OFFLINE",
            totalRequests = requestCount,
            successfulRequests = if (shouldFail) 0 else requestCount,
            averageResponseTimeMs = 1200.0
        )
    }
    
    override suspend fun getAvailableModels(): List<OpenRouterModel> {
        return listOf(
            OpenRouterModel(
                id = "anthropic/claude-3.5-sonnet",
                name = "Claude 3.5 Sonnet",
                description = "Anthropic's most capable model",
                pricing = OpenRouterPricing(prompt = "0.000003", completion = "0.000015"),
                context_length = 200000,
                architecture = null,
                top_provider = null
            ),
            OpenRouterModel(
                id = "openai/gpt-4o",
                name = "GPT-4o",
                description = "OpenAI's flagship model",
                pricing = OpenRouterPricing(prompt = "0.000005", completion = "0.000015"),
                context_length = 128000,
                architecture = null,
                top_provider = null
            ),
            OpenRouterModel(
                id = "meta-llama/llama-3.1-8b-instruct",
                name = "Llama 3.1 8B Instruct",
                description = "Meta's open source model",
                pricing = OpenRouterPricing(prompt = "0.0000001", completion = "0.0000001"),
                context_length = 131072,
                architecture = null,
                top_provider = null
            )
        )
    }
    
    override suspend fun getModelInfo(modelId: String): OpenRouterModel? {
        return getAvailableModels().find { it.id == modelId }
    }
} 