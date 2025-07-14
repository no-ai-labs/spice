package io.github.spice.agents

import io.github.spice.*
import io.github.spice.model.*
import io.github.spice.model.clients.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

/**
 * 🧪 VertexAgent Test Suite
 * 
 * Comprehensive tests for Google Vertex AI powered agent
 */
class VertexAgentTest {
    
    private lateinit var mockVertexClient: MockVertexClient
    private lateinit var vertexAgent: VertexAgent
    
    @BeforeEach
    fun setUp() {
        mockVertexClient = MockVertexClient()
        
        vertexAgent = VertexAgent(
            client = mockVertexClient,
            context = ModelContext(bufferSize = 5),
            id = "test-vertex-agent",
            name = "Test Vertex Agent",
            description = "Test Vertex Agent for unit testing"
        )
    }
    
    @Test
    fun `basic properties test`() {
        assertEquals("test-vertex-agent", vertexAgent.id)
        assertEquals("Test Vertex Agent", vertexAgent.name)
        assertTrue(vertexAgent.capabilities.contains("gemini_pro"))
        assertTrue(vertexAgent.capabilities.contains("context_aware"))
        assertTrue(vertexAgent.isReady())
    }
    
    @Test
    fun `message processing test`() = runBlocking {
        // Given
        val inputMessage = Message(
            id = "test-msg-1",
            content = "Hello Vertex AI!",
            sender = "user"
        )
        
        // When
        val response = vertexAgent.processMessage(inputMessage)
        
        // Then
        assertEquals("Test Vertex Agent", response.sender)
        assertTrue(response.content.contains("Mock Vertex response"))
        assertEquals("vertex", response.metadata["agent_type"])
        assertEquals("gemini-1.5-flash-002", response.metadata["model"])
        assertNotNull(response.metadata["token_usage"])
        assertNotNull(response.metadata["response_time_ms"])
    }
    
    @Test
    fun `error handling test`() = runBlocking {
        // Given
        mockVertexClient.shouldFail = true
        val inputMessage = Message(
            id = "error-test",
            content = "This should fail",
            sender = "user"
        )
        
        // When
        val response = vertexAgent.processMessage(inputMessage)
        
        // Then
        assertTrue(response.content.contains("Sorry, an error occurred during processing") || 
                  response.content.contains("죄송합니다. 처리 중 오류가 발생했습니다"))
        assertEquals("true", response.metadata["error"])
    }
    
    @Test
    fun `context management test`() = runBlocking {
        // Given
        val messages = listOf(
            "First message",
            "Second message", 
            "Third message"
        )
        
        // When
        messages.forEach { content ->
            val message = Message(id = "msg-${System.currentTimeMillis()}", content = content, sender = "user")
            vertexAgent.processMessage(message)
        }
        
        // Then
        val summary = vertexAgent.getContextSummary()
        assertEquals(6, summary.messageCount) // 3 user + 3 assistant
        
        val history = vertexAgent.getConversationHistory()
        assertEquals(6, history.size)
        assertTrue(history.any { it.content == "First message" })
        assertTrue(history.any { it.content == "Third message" })
    }
    
    @Test
    fun `context clearing test`() = runBlocking {
        // Given
        val message = Message(id = "clear-test", content = "Test message", sender = "user")
        vertexAgent.processMessage(message)
        
        // When
        vertexAgent.clearContext()
        
        // Then
        val summary = vertexAgent.getContextSummary()
        assertEquals(0, summary.messageCount)
    }
    
    @Test
    fun `system prompt setting test`() = runBlocking {
        // Given
        val systemPrompt = "You are a specialized Vertex AI assistant."
        
        // When
        vertexAgent.setSystemPrompt(systemPrompt)
        
        // Then
        val history = vertexAgent.getConversationHistory()
        assertTrue(history.any { it.isSystem && it.content == systemPrompt })
    }
    
    @Test
    fun `client status test`() = runBlocking {
        // Given & When
        val status = vertexAgent.getClientStatus()
        
        // Then
        assertEquals("mock-vertex-client", status.clientId)
        assertEquals("READY", status.status)
        assertTrue(status.isAvailable)
    }
    
    @Test
    fun `status summary test`() = runBlocking {
        // Given
        val message = Message(id = "status-test", content = "Test", sender = "user")
        vertexAgent.processMessage(message)
        
        // When
        val statusSummary = vertexAgent.getStatusSummary()
        
        // Then
        assertTrue(statusSummary.contains("🤖 Vertex Agent Status"))
        assertTrue(statusSummary.contains("Model: gemini-1.5-flash-002"))
        assertTrue(statusSummary.contains("Context:"))
    }
    
    @Test
    fun `multimodal message processing test`() = runBlocking {
        // Given
        val text = "Analyze this image"
        val imageData = ByteArray(100) { it.toByte() }
        
        // When
        val response = vertexAgent.processMultimodalMessage(text, imageData, "image/jpeg")
        
        // Then
        assertTrue(response.content.contains("Mock Vertex response"))
    }
    
    @Test
    fun `batch processing test`() = runBlocking {
        // Given
        val messages = listOf("Message 1", "Message 2", "Message 3")
        
        // When
        val responses = vertexAgent.processBatch(messages)
        
        // Then
        assertEquals(3, responses.size)
        responses.forEach { response ->
            assertTrue(response.content.contains("Mock Vertex response"))
        }
    }
    
    @Test
    fun `context buffer limit test`() = runBlocking {
        // Given - buffer size is 5, so max 5 messages
        repeat(10) { i ->
            val message = Message(id = "buffer-$i", content = "Message $i", sender = "user")
            vertexAgent.processMessage(message)
        }
        
        // When
        val summary = vertexAgent.getContextSummary()
        
        // Then
        assertEquals(5, summary.messageCount) // Limited by buffer size
    }
}

/**
 * Mock VertexClient for testing
 */
class MockVertexClient : VertexClient(
    projectId = "test-project",
    location = "us-central1",
    model = "gemini-1.5-flash-002"
) {
    var shouldFail = false
    private var requestCount = 0
    
    override val id: String = "mock-vertex-client"
    override val modelName: String = "gemini-1.5-flash-002"
    override val description: String = "Mock Vertex client for testing"
    
    override suspend fun chat(
        messages: List<ModelMessage>,
        systemPrompt: String?,
        metadata: Map<String, String>
    ): ModelResponse {
        requestCount++
        
        if (shouldFail) {
            return ModelResponse.error("Mock error for testing")
        }
        
        val responseContent = "Mock Vertex response to: ${messages.lastOrNull()?.content ?: "no content"}"
        
        return ModelResponse.success(
            content = responseContent,
            usage = TokenUsage(
                inputTokens = messages.sumOf { it.content.length },
                outputTokens = responseContent.length
            ),
            responseTimeMs = 150
        )
    }
    
    override fun isReady(): Boolean = !shouldFail
    
    override suspend fun getStatus(): ClientStatus {
        return ClientStatus(
            clientId = id,
            status = if (isReady()) "READY" else "ERROR",
            totalRequests = requestCount,
            successfulRequests = if (shouldFail) 0 else requestCount,
            averageResponseTimeMs = 150.0
        )
    }
} 