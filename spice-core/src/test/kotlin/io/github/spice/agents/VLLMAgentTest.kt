package io.github.spice.agents

import io.github.spice.*
import io.github.spice.model.*
import io.github.spice.model.clients.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

/**
 * 🧪 VLLMAgent Test Suite
 * 
 * Comprehensive tests for vLLM high-performance agent
 */
class VLLMAgentTest {
    
    private lateinit var mockVLLMClient: MockVLLMClient
    private lateinit var vllmAgent: VLLMAgent
    
    @BeforeEach
    fun setUp() {
        mockVLLMClient = MockVLLMClient()
        
        vllmAgent = VLLMAgent(
            client = mockVLLMClient,
            context = ModelContext(bufferSize = 8),
            id = "test-vllm-agent",
            name = "Test vLLM Agent",
            description = "Test vLLM Agent for unit testing"
        )
    }
    
    @Test
    fun `basic properties test`() {
        assertEquals("test-vllm-agent", vllmAgent.id)
        assertEquals("Test vLLM Agent", vllmAgent.name)
        assertTrue(vllmAgent.capabilities.contains("high_performance_inference"))
        assertTrue(vllmAgent.capabilities.contains("gpu_acceleration"))
        assertTrue(vllmAgent.capabilities.contains("llama_models"))
        assertTrue(vllmAgent.isReady())
    }
    
    @Test
    fun `message processing test`() = runBlocking {
        // Given
        val inputMessage = Message(
            id = "test-msg-1",
            content = "Hello vLLM!",
            sender = "user"
        )
        
        // When
        val response = vllmAgent.processMessage(inputMessage)
        
        // Then
        assertEquals("Test vLLM Agent", response.sender)
        assertTrue(response.content.contains("Mock vLLM response"))
        assertEquals("vllm", response.metadata["agent_type"])
        assertEquals("meta-llama/Llama-3.1-8B-Instruct", response.metadata["model"])
        assertEquals("true", response.metadata["gpu_accelerated"])
        assertNotNull(response.metadata["token_usage"])
        assertNotNull(response.metadata["response_time_ms"])
    }
    
    @Test
    fun `server connection error handling test`() = runBlocking {
        // Given
        mockVLLMClient.shouldFail = true
        val inputMessage = Message(
            id = "error-test",
            content = "This should fail",
            sender = "user"
        )
        
        // When
        val response = vllmAgent.processMessage(inputMessage)
        
        // Then
        assertTrue(response.content.contains("Sorry, vLLM server connection error occurred") || 
                  response.content.contains("죄송합니다. vLLM 서버 연결 오류가 발생했습니다"))
        assertEquals("true", response.metadata["error"])
    }
    
    @Test
    fun `context management test`() = runBlocking {
        // Given
        val messages = listOf(
            "What is machine learning?",
            "Explain neural networks",
            "How does backpropagation work?"
        )
        
        // When
        messages.forEach { content ->
            val message = Message(id = "msg-${System.currentTimeMillis()}", content = content, sender = "user")
            vllmAgent.processMessage(message)
        }
        
        // Then
        val summary = vllmAgent.getContextSummary()
        assertEquals(6, summary.messageCount) // 3 user + 3 assistant
        
        val history = vllmAgent.getConversationHistory()
        assertEquals(6, history.size)
        assertTrue(history.any { it.content == "What is machine learning?" })
        assertTrue(history.any { it.content == "How does backpropagation work?" })
    }
    
    @Test
    fun `server status test`() = runBlocking {
        // Given
        val message = Message(id = "status-test", content = "Test", sender = "user")
        vllmAgent.processMessage(message)
        
        // When
        val serverStatus = vllmAgent.getServerStatus()
        
        // Then
        assertTrue(serverStatus.contains("🚀 vLLM Agent Status"))
        assertTrue(serverStatus.contains("Model: meta-llama/Llama-3.1-8B-Instruct"))
        assertTrue(serverStatus.contains("Server: ONLINE"))
        assertTrue(serverStatus.contains("GPU Acceleration: Enabled"))
    }
    
    @Test
    fun `coding session test`() = runBlocking {
        // Given
        val language = "python"
        
        // When
        val sessionResponse = vllmAgent.startCodingSession(language)
        
        // Then
        assertTrue(sessionResponse.content.contains("vLLM coding session has started") || 
                  sessionResponse.content.contains("vLLM 코딩 세션이 시작되었습니다"))
        assertTrue(sessionResponse.content.contains(language))
    }
    
    @Test
    fun `code generation test`() = runBlocking {
        // Given
        val prompt = "Create a function to calculate fibonacci numbers"
        val language = "python"
        
        // When
        val response = vllmAgent.generateCode(prompt, language, includeExplanation = true)
        
        // Then
        assertTrue(response.content.contains("Mock vLLM response"))
    }
    
    @Test
    fun `batch processing test`() = runBlocking {
        // Given
        val messages = listOf(
            "What is AI?",
            "Explain deep learning",
            "What are transformers?"
        )
        
        // When
        val responses = vllmAgent.processBatch(messages)
        
        // Then
        assertEquals(3, responses.size)
        responses.forEach { response ->
            assertTrue(response.content.contains("Mock vLLM response"))
            assertEquals("true", response.metadata["gpu_accelerated"])
        }
    }
    
    @Test
    fun `connection test`() = runBlocking {
        // Given & When
        val connectionResult = vllmAgent.testConnection()
        
        // Then
        assertTrue(connectionResult) // Mock client should always be connected
    }
    
    @Test
    fun `temperature processing test`() = runBlocking {
        // Given
        val message = "Generate creative content"
        val temperature = 0.9
        
        // When
        val response = vllmAgent.processWithTemperature(message, temperature)
        
        // Then
        assertTrue(response.content.contains("Mock vLLM response"))
    }
    
    @Test
    fun `client status test`() = runBlocking {
        // Given & When
        val status = vllmAgent.getClientStatus()
        
        // Then
        assertEquals("mock-vllm-client", status.clientId)
        assertEquals("READY", status.status)
        assertTrue(status.isAvailable)
    }
    
    @Test
    fun `system prompt setting test`() = runBlocking {
        // Given
        val systemPrompt = "You are a specialized coding assistant powered by vLLM."
        
        // When
        vllmAgent.setSystemPrompt(systemPrompt)
        
        // Then
        val history = vllmAgent.getConversationHistory()
        assertTrue(history.any { it.isSystem && it.content == systemPrompt })
    }
    
    @Test
    fun `context buffer limit test`() = runBlocking {
        // Given - buffer size is 8, so max 8 messages
        repeat(15) { i ->
            val message = Message(id = "buffer-$i", content = "Message $i", sender = "user")
            vllmAgent.processMessage(message)
        }
        
        // When
        val summary = vllmAgent.getContextSummary()
        
        // Then
        assertEquals(8, summary.messageCount) // Limited by buffer size
    }
}

/**
 * Mock VLLMClient for testing
 */
class MockVLLMClient : VLLMClient(
    baseUrl = "http://localhost:8000",
    model = "meta-llama/Llama-3.1-8B-Instruct"
) {
    var shouldFail = false
    private var requestCount = 0
    
    override val id: String = "mock-vllm-client"
    override val modelName: String = "meta-llama/Llama-3.1-8B-Instruct"
    override val description: String = "Mock vLLM client for testing"
    
    override suspend fun chat(
        messages: List<ModelMessage>,
        systemPrompt: String?,
        metadata: Map<String, String>
    ): ModelResponse {
        requestCount++
        
        if (shouldFail) {
            return ModelResponse.error("Mock vLLM server connection error")
        }
        
        val responseContent = "Mock vLLM response to: ${messages.lastOrNull()?.content ?: "no content"}"
        
        return ModelResponse.success(
            content = responseContent,
            usage = TokenUsage(
                inputTokens = messages.sumOf { it.content.length },
                outputTokens = responseContent.length
            ),
            responseTimeMs = 80 // vLLM is fast!
        )
    }
    
    override fun isReady(): Boolean = !shouldFail
    
    override suspend fun getStatus(): ClientStatus {
        return ClientStatus(
            clientId = id,
            status = if (isReady()) "READY" else "OFFLINE",
            totalRequests = requestCount,
            successfulRequests = if (shouldFail) 0 else requestCount,
            averageResponseTimeMs = 80.0
        )
    }
    
    override suspend fun testConnection(): VLLMConnectionTest {
        return VLLMConnectionTest(
            success = !shouldFail,
            responseTimeMs = 50,
            statusCode = if (shouldFail) -1 else 200,
            message = if (shouldFail) "Connection failed" else "Connection successful"
        )
    }
    
    override suspend fun getServerInfo(): VLLMServerInfo? {
        return if (shouldFail) {
            VLLMServerInfo(
                baseUrl = "http://localhost:8000",
                availableModels = emptyList(),
                isOnline = false,
                error = "Server offline"
            )
        } else {
            VLLMServerInfo(
                baseUrl = "http://localhost:8000",
                availableModels = listOf("meta-llama/Llama-3.1-8B-Instruct", "meta-llama/Llama-3.1-70B-Instruct"),
                isOnline = true
            )
        }
    }
} 