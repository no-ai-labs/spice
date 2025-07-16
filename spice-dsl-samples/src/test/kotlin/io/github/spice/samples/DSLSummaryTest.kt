package io.github.spice.samples

import io.github.spice.Message
import io.github.spice.ToolResult
import io.github.spice.dsl.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * DSL Summary 기능 테스트 및 사용법 예제
 */
class DSLSummaryTest {
    
    @BeforeEach
    fun setup() {
        // 테스트 전에 레지스트리 초기화
        clearRegistries()
    }
    
    @Test
    fun `test agent describe functionality`() = runBlocking {
        // Given: 테스트용 에이전트와 도구 생성
        val greetingTool = tool("greeting-tool") {
            description = "Generates personalized greetings for users"
            param("name", "string")
            param("language", "string") 
            
            execute { params ->
                val name = params["name"] as String
                val language = params["language"] as? String ?: "en"
                val greeting = when (language) {
                    "ko" -> "안녕하세요, ${name}님!"
                    "es" -> "¡Hola, $name!"
                    else -> "Hello, $name!"
                }
                ToolResult.success(greeting)
            }
        }
        ToolRegistry.register(greetingTool)
        
        val greetingAgent = buildAgent {
            id = "greeting-agent"
            name = "Greeting Agent"
            description = "A friendly agent that greets users in multiple languages"
            tools("greeting-tool")
            
            handle { message ->
                val result = greetingTool.execute(mapOf(
                    "name" to message.content,
                    "language" to (message.metadata["language"] ?: "en")
                ))
                Message(
                    content = result.result,
                    sender = id,
                    receiver = message.sender,
                    metadata = mapOf("greeting_generated" to "true")
                )
            }
        }
        AgentRegistry.register(greetingAgent)
        
        // When: describe 함수 호출
        val agentDescription = greetingAgent.describe()
        val toolDescription = greetingTool.describe()
        val agentSummary = greetingAgent.summarize()
        val toolSummary = greetingTool.summarize()
        
        // Then: 결과 검증 및 출력
        println("=== Agent Description ===")
        println(agentDescription)
        
        println("\n=== Tool Description ===")
        println(toolDescription)
        
        println("\n=== Summaries ===")
        println("Agent: $agentSummary")
        println("Tool: $toolSummary")
        
        // 기본 검증
        assertTrue(agentDescription.contains("Greeting Agent"))
        assertTrue(agentDescription.contains("greeting-tool"))
        assertTrue(toolDescription.contains("name"))
        assertTrue(toolDescription.contains("language"))
    }
    
    @Test
    fun `test flow describe functionality`() = runBlocking {
        // Given: 플로우 테스트를 위한 에이전트들 생성
        setupFlowTestAgents()
        
        val customerServiceFlow = flow {
            id = "customer-service-flow"
            name = "Customer Service Flow"
            description = "Handles customer inquiries with sentiment analysis and appropriate response"
            
            step("sentiment-agent")
            step("step2", "response-agent") { message ->
                message.metadata["sentiment"] == "negative"
            }
            step("step3", "escalation-agent") { message ->
                message.metadata["sentiment"] == "positive"
            }
        }
        
        // When: Flow describe 호출
        val flowDescription = customerServiceFlow.describe()
        val flowSummary = customerServiceFlow.summarize()
        
        // Then: 결과 출력
        println("=== Flow Description ===")
        println(flowDescription)
        
        println("\n=== Flow Summary ===")
        println(flowSummary)
        
        // 검증
        assertTrue(flowDescription.contains("Customer Service Flow"))
        assertTrue(flowDescription.contains("sentiment-agent"))
        assertTrue(flowDescription.contains("Conditional Steps: 2"))
    }
    
    @Test
    fun `test complete DSL environment summary`() = runBlocking {
        // Given: 완전한 DSL 환경 구성
        setupCompleteEnvironment()
        
        // When: 전체 환경 요약 생성
        val allAgentsSummary = describeAllAgents()
        val allToolsSummary = describeAllTools()
        val completeEnvironmentSummary = describeAllComponents()
        val healthCheck = checkDSLHealth()
        
        // Then: 결과 출력
        println("=== All Agents Summary ===")
        println(allAgentsSummary)
        
        println("\n=== All Tools Summary ===")
        println(allToolsSummary)
        
        println("\n=== Complete Environment Summary ===")
        println(completeEnvironmentSummary)
        
        println("\n=== Health Check ===")
        if (healthCheck.isEmpty()) {
            println("✅ All systems healthy!")
        } else {
            println("⚠️ Issues found:")
            healthCheck.forEach { issue ->
                println("  - $issue")
            }
        }
        
        // 콘솔 출력용 변환 테스트
        println("\n=== Console Output Format ===")
        println(completeEnvironmentSummary.toConsoleOutput())
    }
    
    @Test
    fun `test describe functionality with real usage scenario`() = runBlocking {
        // Given: 실제 사용 시나리오 구성
        setupRealWorldScenario()
        
        // When: 실제 메시지 처리 및 문서화
        val weatherAgent = AgentRegistry.getAgent("weather-agent")!!
        val analysisAgent = AgentRegistry.getAgent("analysis-agent")!!
        
        // 실제 메시지 처리
        val weatherMessage = Message(content = "Seoul", sender = "user")
        val weatherResponse = weatherAgent.processMessage(weatherMessage)
        
        val analysisMessage = weatherResponse.copy(receiver = "analysis-agent")
        val analysisResponse = analysisAgent.processMessage(analysisMessage)
        
        // Then: 결과와 함께 문서화 출력
        println("=== Real Usage Scenario ===")
        println("User Input: ${weatherMessage.content}")
        println("Weather Response: ${weatherResponse.content}")
        println("Analysis Response: ${analysisResponse.content}")
        
        println("\n=== Agent Documentation ===")
        println(weatherAgent.describe())
        
        println("\n=== Analysis Agent Documentation ===")
        println(analysisAgent.describe())
        
        println("\n=== Environment Health ===")
        val health = checkDSLHealth()
        println("Health Issues: ${health.size}")
        health.forEach { println("- $it") }
    }
    
    private suspend fun setupFlowTestAgents() {
        val sentimentAgent = buildAgent {
            id = "sentiment-agent"
            name = "Sentiment Analysis Agent"
            description = "Analyzes sentiment of customer messages"
            
            handle { message ->
                val sentiment = when {
                    message.content.contains("angry", ignoreCase = true) || 
                    message.content.contains("frustrated", ignoreCase = true) -> "negative"
                    message.content.contains("happy", ignoreCase = true) || 
                    message.content.contains("great", ignoreCase = true) -> "positive"
                    else -> "neutral"
                }
                message.copy(
                    content = "Sentiment analyzed: $sentiment",
                    metadata = message.metadata + ("sentiment" to sentiment)
                )
            }
        }
        AgentRegistry.register(sentimentAgent)
        
        val responseAgent = buildAgent {
            id = "response-agent"
            name = "Response Agent"
            description = "Generates appropriate responses"
            
            handle { message ->
                message.copy(content = "Standard response generated")
            }
        }
        AgentRegistry.register(responseAgent)
        
        val escalationAgent = buildAgent {
            id = "escalation-agent"
            name = "Escalation Agent"
            description = "Handles escalated cases"
            
            handle { message ->
                message.copy(content = "Case escalated to human agent")
            }
        }
        AgentRegistry.register(escalationAgent)
    }
    
    private suspend fun setupCompleteEnvironment() {
        // 도구들 생성
        val calculatorTool = tool("calculator") {
            description = "Performs basic mathematical calculations"
            param("operation", "string")
            param("a", "number")
            param("b", "number")
            
            execute { params ->
                val op = params["operation"] as String
                val a = (params["a"] as Number).toDouble()
                val b = (params["b"] as Number).toDouble()
                
                val result = when (op) {
                    "add" -> a + b
                    "subtract" -> a - b
                    "multiply" -> a * b
                    "divide" -> if (b != 0.0) a / b else error("Division by zero")
                    else -> error("Unknown operation")
                }
                
                ToolResult.success("$a $op $b = $result")
            }
        }
        ToolRegistry.register(calculatorTool)
        
        val textTool = tool("text-processor") {
            description = "Processes text with various transformations"
            param("text", "string")
            param("operation", "string")
            
            execute { params ->
                val text = params["text"] as String
                val operation = params["operation"] as String
                
                val result = when (operation) {
                    "uppercase" -> text.uppercase()
                    "lowercase" -> text.lowercase()
                    "reverse" -> text.reversed()
                    "count" -> "Character count: ${text.length}"
                    else -> "Unknown operation"
                }
                
                ToolResult.success(result)
            }
        }
        ToolRegistry.register(textTool)
        
        // 에이전트들 생성
        val mathAgent = buildAgent {
            id = "math-agent"
            name = "Mathematics Agent"
            description = "Handles mathematical calculations and operations"
            tools("calculator")
            
            handle { message ->
                Message(content = "Math processing: ${message.content}", sender = id)
            }
        }
        AgentRegistry.register(mathAgent)
        
        val textAgent = buildAgent {
            id = "text-agent"
            name = "Text Processing Agent"  
            description = "Handles text manipulation and analysis"
            tools("text-processor")
            
            handle { message ->
                Message(content = "Text processing: ${message.content}", sender = id)
            }
        }
        AgentRegistry.register(textAgent)
    }
    
    private suspend fun setupRealWorldScenario() {
        val weatherTool = tool("weather-api") {
            description = "Fetches weather information for specified locations"
            param("location", "string")
            param("units", "string")
            
            execute { params ->
                val location = params["location"] as String
                val units = params["units"] as? String ?: "celsius"
                ToolResult.success("Weather in $location: 22°$units, sunny")
            }
        }
        ToolRegistry.register(weatherTool)
        
        val weatherAgent = buildAgent {
            id = "weather-agent"
            name = "Weather Information Agent"
            description = "Provides current weather information for any location"
            tools("weather-api")
            
            handle { message ->
                val result = weatherTool.execute(mapOf("location" to message.content))
                Message(
                    content = result.result,
                    sender = id,
                    receiver = message.sender,
                    metadata = mapOf("weather_fetched" to "true")
                )
            }
        }
        AgentRegistry.register(weatherAgent)
        
        val analysisAgent = buildAgent {
            id = "analysis-agent"
            name = "Data Analysis Agent"
            description = "Analyzes and provides insights on provided data"
            
            handle { message ->
                val analysis = "Analysis: The data shows favorable conditions with good visibility."
                Message(
                    content = analysis,
                    sender = id,
                    receiver = message.sender,
                    metadata = mapOf("analysis_complete" to "true")
                )
            }
        }
        AgentRegistry.register(analysisAgent)
    }
    
    private fun clearRegistries() {
        // 실제 구현에서는 registry clear 메서드가 필요할 수 있음
        // 지금은 테스트를 위한 mock
    }
} 