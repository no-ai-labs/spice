package io.github.spice.agents

import io.github.spice.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PlanningAgentTest {
    
    @Test
    fun `test basic planning with default configuration`() = runBlocking {
        // Given
        val mockBaseAgent = MockBaseAgent()
        val planningAgent = PlanningAgent(
            baseAgent = mockBaseAgent,
            config = PlanningConfig(
                maxSteps = 5,
                outputFormat = OutputFormat.STRUCTURED_PLAN
            )
        )
        
        val message = Message(
            content = "Create a mobile app for food delivery",
            sender = "user"
        )
        
        // When
        val response = planningAgent.processMessage(message)
        
        // Then
        assertTrue(response.metadata["structured"] == "true")
        assertTrue(response.metadata["planner_id"] == "mock-agent")
        assertTrue(response.content.contains("📋"))
        assertFalse(response.content.isBlank())
    }
    
    @Test
    fun `test custom prompt builder`() = runBlocking {
        // Given
        val customPromptBuilder = object : PromptBuilder {
            override fun buildPrompt(goal: String, context: PlanningContext, config: PlanningConfig): String {
                return "CUSTOM PROMPT: $goal"
            }
        }
        
        val mockBaseAgent = MockBaseAgent()
        val planningAgent = PlanningAgent(
            baseAgent = mockBaseAgent,
            promptBuilder = customPromptBuilder
        )
        
        val message = Message(
            content = "Test goal",
            sender = "user"
        )
        
        // When
        planningAgent.processMessage(message)
        
        // Then
        assertEquals("CUSTOM PROMPT: Test goal", mockBaseAgent.lastReceivedMessage?.content)
    }
    
    @Test
    fun `test context extraction from message metadata`() = runBlocking {
        // Given
        val mockBaseAgent = MockBaseAgent()
        val planningAgent = PlanningAgent(baseAgent = mockBaseAgent)
        
        val message = Message(
            content = "Build a website",
            sender = "user",
            metadata = mapOf(
                "user_profile" to "Developer",
                "company_profile" to "Tech Startup",
                "domain" to "Web Development",
                "priority" to "High",
                "deadline" to "2 weeks"
            )
        )
        
        // When
        planningAgent.processMessage(message)
        
        // Then
        val receivedContent = mockBaseAgent.lastReceivedMessage?.content ?: ""
        assertTrue(receivedContent.contains("User Profile: Developer"))
        assertTrue(receivedContent.contains("Company Profile: Tech Startup"))
        assertTrue(receivedContent.contains("Domain: Web Development"))
        assertTrue(receivedContent.contains("Priority: High"))
        assertTrue(receivedContent.contains("Deadline: 2 weeks"))
    }
    
    @Test
    fun `test JSON output format`() = runBlocking {
        // Given
        val mockBaseAgent = MockBaseAgent()
        mockBaseAgent.mockResponse = """["Step 1", "Step 2", "Step 3"]"""
        
        val planningAgent = PlanningAgent(
            baseAgent = mockBaseAgent,
            config = PlanningConfig(outputFormat = OutputFormat.JSON)
        )
        
        val message = Message(
            content = "Create a plan",
            sender = "user"
        )
        
        // When
        val response = planningAgent.processMessage(message)
        
        // Then
        assertTrue(response.content.contains("\"title\""))
        assertTrue(response.content.contains("\"steps\""))
        assertTrue(response.metadata["output_format"] == "JSON")
    }
    
    @Test
    fun `test company prompt builder`() = runBlocking {
        // Given
        val companyTemplate = "This is a company-specific template for planning."
        val domainRules = mapOf(
            "Web Development" to "Follow web development best practices"
        )
        
        val companyPromptBuilder = CompanyPromptBuilder(
            companyTemplate = companyTemplate,
            domainSpecificRules = domainRules
        )
        
        val mockBaseAgent = MockBaseAgent()
        val planningAgent = PlanningAgent(
            baseAgent = mockBaseAgent,
            promptBuilder = companyPromptBuilder
        )
        
        val message = Message(
            content = "Build a web app",
            sender = "user",
            metadata = mapOf("domain" to "Web Development")
        )
        
        // When
        planningAgent.processMessage(message)
        
        // Then
        val receivedContent = mockBaseAgent.lastReceivedMessage?.content ?: ""
        assertTrue(receivedContent.contains(companyTemplate))
        assertTrue(receivedContent.contains("Follow web development best practices"))
    }
    
    @Test
    fun `test error handling`() = runBlocking {
        // Given
        val errorBaseAgent = object : BaseAgent(
            id = "error-agent",
            name = "Error Agent",
            description = "Agent that throws errors"
        ) {
            override suspend fun processMessage(message: Message): Message {
                throw RuntimeException("Test error")
            }
        }
        
        val planningAgent = PlanningAgent(baseAgent = errorBaseAgent)
        
        val message = Message(
            content = "Test goal",
            sender = "user"
        )
        
        // When
        val response = planningAgent.processMessage(message)
        
        // Then
        assertEquals(MessageType.ERROR, response.type)
        assertTrue(response.content.contains("Planning failed"))
        assertEquals("planning_failed", response.metadata["error"])
    }
    
    /**
     * Mock BaseAgent for testing
     */
    private class MockBaseAgent : BaseAgent(
        id = "mock-agent",
        name = "Mock Agent",
        description = "Mock agent for testing"
    ) {
        var lastReceivedMessage: Message? = null
        var mockResponse: String = "1. Step 1\n2. Step 2\n3. Step 3"
        
        override suspend fun processMessage(message: Message): Message {
            lastReceivedMessage = message
            return message.createReply(
                content = mockResponse,
                sender = id
            )
        }
    }
} 