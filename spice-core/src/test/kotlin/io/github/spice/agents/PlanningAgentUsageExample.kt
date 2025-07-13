package io.github.spice.agents

import io.github.spice.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

/**
 * 🌶️ PlanningAgent Usage Examples
 * 
 * Shows how to use PlanningAgent with different configurations and scenarios
 */
class PlanningAgentUsageExample {
    
    @Test
    fun `example 1 - Basic planning with default settings`() = runBlocking {
        println("🔥 Example 1: Basic Planning with Default Settings")
        println("=" * 60)
        
        // Create a mock base agent
        val baseAgent = MockVertexAgent()
        
        // Create planning agent with default settings
        val planningAgent = PlanningAgent(
            baseAgent = baseAgent
        )
        
        // Create a planning request
        val message = Message(
            content = "Create a mobile app for food delivery",
            sender = "user"
        )
        
        // Get the plan
        val response = planningAgent.processMessage(message)
        
        println("📋 Generated Plan:")
        println(response.content)
        println()
        println("📊 Metadata:")
        response.metadata.forEach { (key, value) ->
            println("  $key: $value")
        }
        println()
    }
    
    @Test
    fun `example 2 - Custom prompt builder for startup environment`() = runBlocking {
        println("🔥 Example 2: Custom Prompt Builder for Startup")
        println("=" * 60)
        
        // Create custom prompt builder for startup
        val startupPromptBuilder = CompanyPromptBuilder(
            companyTemplate = """
                🚀 STARTUP PLANNING MODE
                Focus on rapid iteration, MVP development, and market validation.
                Keep solutions lean and scalable.
            """.trimIndent(),
            domainSpecificRules = mapOf(
                "mobile_app" to "Prioritize cross-platform development and user feedback loops"
            )
        )
        
        val baseAgent = MockVertexAgent()
        val planningAgent = PlanningAgent(
            baseAgent = baseAgent,
            promptBuilder = startupPromptBuilder
        )
        
        val message = Message(
            content = "Build a social media app for Gen Z",
            sender = "user",
            metadata = mapOf(
                "company_profile" to "startup",
                "domain" to "mobile_app",
                "priority" to "high",
                "deadline" to "3 months"
            )
        )
        
        val response = planningAgent.processMessage(message)
        
        println("📋 Startup-Optimized Plan:")
        println(response.content)
        println()
    }
    
    @Test
    fun `example 3 - JSON output format with detailed configuration`() = runBlocking {
        println("🔥 Example 3: JSON Output with Detailed Configuration")
        println("=" * 60)
        
        val baseAgent = MockVertexAgent()
        val planningAgent = PlanningAgent(
            baseAgent = baseAgent,
            config = PlanningConfig(
                maxSteps = 8,
                outputFormat = OutputFormat.JSON,
                planningStrategy = PlanningStrategy.PARALLEL,
                includeTimeEstimates = true,
                includeDependencies = true,
                allowParallelSteps = true
            )
        )
        
        val message = Message(
            content = "Implement a data analytics dashboard",
            sender = "user",
            metadata = mapOf(
                "domain" to "data_analysis",
                "resources" to "Python, React, PostgreSQL",
                "constraints" to "Security compliance, Performance requirements"
            )
        )
        
        val response = planningAgent.processMessage(message)
        
        println("📋 JSON Plan:")
        println(response.content)
        println()
    }
    
    @Test
    fun `example 4 - Markdown output for documentation`() = runBlocking {
        println("🔥 Example 4: Markdown Output for Documentation")
        println("=" * 60)
        
        val baseAgent = MockVertexAgent()
        val planningAgent = PlanningAgent(
            baseAgent = baseAgent,
            config = PlanningConfig(
                outputFormat = OutputFormat.MARKDOWN,
                planningStrategy = PlanningStrategy.MILESTONE_BASED
            )
        )
        
        val message = Message(
            content = "Launch an e-commerce website",
            sender = "user",
            metadata = mapOf(
                "company_profile" to "enterprise",
                "domain" to "web_development",
                "priority" to "critical",
                "deadline" to "6 months"
            )
        )
        
        val response = planningAgent.processMessage(message)
        
        println("📋 Markdown Plan:")
        println(response.content)
        println()
    }
    
    @Test
    fun `example 5 - AI ML project with specialized rules`() = runBlocking {
        println("🔥 Example 5: AI/ML Project with Specialized Rules")
        println("=" * 60)
        
        val aiPromptBuilder = object : PromptBuilder {
            override fun buildPrompt(goal: String, context: PlanningContext, config: PlanningConfig): String {
                return """
                    🤖 AI/ML PROJECT PLANNING
                    
                    You are planning an AI/ML project with the following considerations:
                    - Data collection and preprocessing
                    - Model selection and training
                    - Evaluation and validation
                    - Deployment and monitoring
                    - Ethical AI considerations
                    
                    Context: ${context.domain}
                    Resources: ${context.resources?.joinToString(", ") ?: "Standard ML stack"}
                    
                    Goal: "$goal"
                    
                    Create a structured plan with clear phases and deliverables.
                """.trimIndent()
            }
        }
        
        val baseAgent = MockVertexAgent()
        val planningAgent = PlanningAgent(
            baseAgent = baseAgent,
            promptBuilder = aiPromptBuilder,
            config = PlanningConfig(
                planningStrategy = PlanningStrategy.ITERATIVE,
                outputFormat = OutputFormat.STRUCTURED_PLAN
            )
        )
        
        val message = Message(
            content = "Build a recommendation system for an e-commerce platform",
            sender = "user",
            metadata = mapOf(
                "domain" to "ai_ml",
                "resources" to "Python, TensorFlow, Apache Spark, AWS",
                "constraints" to "Real-time performance, Privacy compliance"
            )
        )
        
        val response = planningAgent.processMessage(message)
        
        println("📋 AI/ML Plan:")
        println(response.content)
        println()
    }
    
    @Test
    fun `example 6 - Error handling and fallback`() = runBlocking {
        println("🔥 Example 6: Error Handling and Fallback")
        println("=" * 60)
        
        // Create an agent that throws errors
        val errorAgent = object : BaseAgent(
            id = "error-agent",
            name = "Error Agent",
            description = "Agent that simulates errors"
        ) {
            override suspend fun processMessage(message: Message): Message {
                throw RuntimeException("Simulated planning error")
            }
        }
        
        val planningAgent = PlanningAgent(baseAgent = errorAgent)
        
        val message = Message(
            content = "Create a complex system",
            sender = "user"
        )
        
        val response = planningAgent.processMessage(message)
        
        println("📋 Error Response:")
        println("Type: ${response.type}")
        println("Content: ${response.content}")
        println("Error Metadata: ${response.metadata["error"]}")
        println()
    }
    
    /**
     * Mock Vertex Agent for testing
     */
    private class MockVertexAgent : BaseAgent(
        id = "mock-vertex-agent",
        name = "Mock Vertex Agent",
        description = "Mock agent for testing"
    ) {
        override suspend fun processMessage(message: Message): Message {
            // Simulate different responses based on content
            val response = when {
                message.content.contains("mobile app") -> """
                    {
                      "title": "Mobile Food Delivery App",
                      "description": "Complete mobile application for food delivery service",
                      "steps": [
                        {
                          "id": "step-1",
                          "title": "Market Research and User Analysis",
                          "description": "Analyze target market and user requirements",
                          "type": "ACTION",
                          "estimatedTime": "1w",
                          "dependencies": [],
                          "metadata": {
                            "complexity": "medium",
                            "parallelizable": "false"
                          }
                        },
                        {
                          "id": "step-2",
                          "title": "UI/UX Design",
                          "description": "Create wireframes and user interface designs",
                          "type": "ACTION",
                          "estimatedTime": "2w",
                          "dependencies": ["step-1"],
                          "metadata": {
                            "complexity": "high",
                            "parallelizable": "false"
                          }
                        },
                        {
                          "id": "step-3",
                          "title": "Backend API Development",
                          "description": "Develop REST API for app functionality",
                          "type": "ACTION",
                          "estimatedTime": "3w",
                          "dependencies": ["step-1"],
                          "metadata": {
                            "complexity": "high",
                            "parallelizable": "true"
                          }
                        },
                        {
                          "id": "step-4",
                          "title": "Mobile App Development",
                          "description": "Develop iOS and Android applications",
                          "type": "ACTION",
                          "estimatedTime": "4w",
                          "dependencies": ["step-2", "step-3"],
                          "metadata": {
                            "complexity": "high",
                            "parallelizable": "false"
                          }
                        },
                        {
                          "id": "step-5",
                          "title": "Testing and Quality Assurance",
                          "description": "Comprehensive testing of all components",
                          "type": "REVIEW",
                          "estimatedTime": "1w",
                          "dependencies": ["step-4"],
                          "metadata": {
                            "complexity": "medium",
                            "parallelizable": "false"
                          }
                        }
                      ]
                    }
                """.trimIndent()
                
                message.content.contains("data analytics") -> """
                    1. Data Source Integration
                    2. Data Processing Pipeline
                    3. Analytics Engine Development
                    4. Dashboard Frontend Creation
                    5. Visualization Components
                    6. User Authentication System
                    7. Performance Optimization
                    8. Deployment and Monitoring
                """.trimIndent()
                
                else -> """
                    1. Requirements Analysis
                    2. System Design
                    3. Implementation
                    4. Testing
                    5. Deployment
                """.trimIndent()
            }
            
            return message.createReply(
                content = response,
                sender = id
            )
        }
    }
}

// Helper extension for string repetition
private operator fun String.times(count: Int): String = repeat(count) 