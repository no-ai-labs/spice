package io.github.spice

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentBuilderTest {
    
    @Test
    fun `DSL로 기본 Agent 생성 테스트`() = runBlocking {
        // When: DSL로 Agent 생성
        val agent = buildAgent {
            id = "test-agent"
            name = "Test Agent"
            description = "Agent created for testing"
            
            capabilities {
                add("testing")
                add("validation")
            }
        }
        
        // Then: Agent 속성 검증
        assertEquals("test-agent", agent.id)
        assertEquals("Test Agent", agent.name)
        assertEquals("Agent created for testing", agent.description)
        assertEquals(listOf("testing", "validation"), agent.capabilities)
        assertTrue(agent.isReady())
    }
    
    @Test
    fun `DSL로 커스텀 메시지 핸들러가 있는 Agent 생성 테스트`() = runBlocking {
        // When: 커스텀 메시지 핸들러가 있는 Agent 생성
        val agent = buildAgent {
            id = "custom-handler-agent"
            name = "Custom Handler Agent"
            
            messageHandler { message ->
                when (message.type) {
                    MessageType.TEXT -> {
                        message.createReply(
                            "CUSTOM: ${message.content.uppercase()}",
                            this@buildAgent.id,
                            MessageType.TEXT
                        )
                    }
                    else -> {
                        message.createReply("DEFAULT: ${message.content}", this@buildAgent.id)
                    }
                }
            }
        }
        
        // Then: 메시지 처리 테스트
        val testMessage = Message(
            content = "hello world",
            sender = "user",
            type = MessageType.TEXT
        )
        
        val result = agent.processMessage(testMessage)
        assertEquals("CUSTOM: HELLO WORLD", result.content)
        assertEquals("custom-handler-agent", result.sender)
    }
    
    @Test
    fun `DSL로 커스텀 Tool이 있는 Agent 생성 테스트`() = runBlocking {
        // When: 커스텀 Tool이 있는 Agent 생성
        val agent = buildAgent {
            id = "tool-agent"
            name = "Tool Agent"
            
            tools {
                add(WebSearchTool())
                
                custom("string_manipulator") {
                    description = "Manipulates strings"
                    parameter("text", "string", "Text to manipulate", required = true)
                    parameter("operation", "string", "Operation to perform", required = true)
                    
                    execute { params ->
                        val text = params["text"] as String
                        val operation = params["operation"] as String
                        
                        val result = when (operation) {
                            "uppercase" -> text.uppercase()
                            "lowercase" -> text.lowercase()
                            "reverse" -> text.reversed()
                            else -> "Unknown operation: $operation"
                        }
                        
                        ToolResult.success("Result: $result")
                    }
                }
            }
        }
        
        // Then: Tool 검증
        val tools = agent.getTools()
        assertEquals(2, tools.size)
        
        val customTool = tools.find { it.name == "string_manipulator" }
        assertNotNull(customTool, "Custom tool should exist")
        
        // Tool 실행 테스트
        val toolResult = customTool.execute(mapOf(
            "text" to "Hello World",
            "operation" to "uppercase"
        ))
        
        assertTrue(toolResult.success)
        assertEquals("Result: HELLO WORLD", toolResult.result)
    }
    
    @Test
    fun `편의 함수로 textProcessingAgent 생성 테스트`() = runBlocking {
        // When: 편의 함수로 Agent 생성
        val agent = textProcessingAgent(
            id = "text-processor",
            name = "Text Processor"
        ) { text ->
            "PROCESSED: ${text.reversed()}"
        }
        
        // Then: Agent 검증
        assertEquals("text-processor", agent.id)
        assertEquals("Text Processor", agent.name)
        assertTrue(agent.capabilities.contains("text_processing"))
        
        // 메시지 처리 테스트
        val message = Message(
            content = "hello",
            sender = "user",
            type = MessageType.TEXT
        )
        
        val result = agent.processMessage(message)
        assertEquals("PROCESSED: olleh", result.content)
    }
    
    @Test
    fun `편의 함수로 apiAgent 생성 테스트`() = runBlocking {
        // When: API Agent 생성
        val agent = apiAgent(
            id = "api-client",
            name = "API Client",
            baseUrl = "https://api.example.com"
        )
        
        // Then: Agent 검증
        assertEquals("api-client", agent.id)
        assertEquals("API Client", agent.name)
        assertTrue(agent.capabilities.contains("api_calls"))
        assertTrue(agent.capabilities.contains("http_requests"))
        
        // Tool 확인
        val apiTool = agent.getTools().find { it.name == "api_call" }
        assertNotNull(apiTool, "API call tool should exist")
        
        // Tool 실행 테스트
        val toolResult = apiTool.execute(mapOf(
            "endpoint" to "/users",
            "method" to "GET"
        ))
        
        assertTrue(toolResult.success)
        assertTrue(toolResult.result.contains("https://api.example.com/users"))
    }
    
    @Test
    fun `편의 함수로 routingAgent 생성 테스트`() = runBlocking {
        // When: 라우팅 Agent 생성
        val agent = routingAgent(
            id = "router",
            name = "Message Router",
            routes = mapOf(
                "weather" to "weather-service",
                "news" to "news-service",
                "search" to "search-service"
            )
        )
        
        // Then: Agent 검증
        assertEquals("router", agent.id)
        assertEquals("Message Router", agent.name)
        assertTrue(agent.capabilities.contains("message_routing"))
        
        // 라우팅 테스트
        val weatherMessage = Message(
            content = "What's the weather like today?",
            sender = "user",
            type = MessageType.TEXT
        )
        
        val weatherResult = agent.processMessage(weatherMessage)
        assertTrue(weatherResult.content.contains("weather-service"))
        assertEquals("weather-service", weatherResult.metadata["route"])
        
        // 매칭되지 않는 메시지 테스트
        val unknownMessage = Message(
            content = "Random question",
            sender = "user",
            type = MessageType.TEXT
        )
        
        val unknownResult = agent.processMessage(unknownMessage)
        assertTrue(unknownResult.content.contains("No route found"))
    }
    
    @Test
    fun `복잡한 DSL Agent 생성 및 AgentEngine 통합 테스트`() = runBlocking {
        // When: 복잡한 Agent 생성
        val complexAgent = buildAgent {
            id = "complex-agent"
            name = "Complex Multi-Tool Agent"
            description = "Agent with multiple capabilities and tools"
            
            capabilities {
                add("text_processing")
                add("calculations")
                add("data_transformation")
            }
            
            tools {
                custom("calculator") {
                    description = "Simple calculator"
                    parameter("a", "number", "First number", required = true)
                    parameter("b", "number", "Second number", required = true)
                    parameter("operation", "string", "Math operation", required = true)
                    
                    execute { params ->
                        val a = (params["a"] as Number).toDouble()
                        val b = (params["b"] as Number).toDouble()
                        val op = params["operation"] as String
                        
                        val result = when (op) {
                            "add" -> a + b
                            "subtract" -> a - b
                            "multiply" -> a * b
                            "divide" -> if (b != 0.0) a / b else throw IllegalArgumentException("Division by zero")
                            else -> throw IllegalArgumentException("Unknown operation: $op")
                        }
                        
                        ToolResult.success("$a $op $b = $result")
                    }
                }
                
                custom("data_transformer") {
                    description = "Transforms data"
                    parameter("data", "string", "Data to transform", required = true)
                    parameter("format", "string", "Target format", required = true)
                    
                    execute { params ->
                        val data = params["data"] as String
                        val format = params["format"] as String
                        
                        val transformed = when (format) {
                            "uppercase" -> data.uppercase()
                            "lowercase" -> data.lowercase()
                            "json" -> """{"data": "$data"}"""
                            else -> "Unsupported format: $format"
                        }
                        
                        ToolResult.success("Transformed to $format: $transformed")
                    }
                }
            }
            
            canHandle { message ->
                message.type in listOf(MessageType.TEXT, MessageType.DATA, MessageType.TOOL_CALL)
            }
            
            messageHandler { message ->
                when (message.type) {
                    MessageType.TEXT -> {
                        if (message.content.contains("calculate", ignoreCase = true)) {
                            message.createReply(
                                "Use calculator tool for calculations",
                                this@buildAgent.id,
                                MessageType.TOOL_CALL,
                                mapOf("toolName" to "calculator")
                            )
                        } else if (message.content.contains("transform", ignoreCase = true)) {
                            message.createReply(
                                "Use data_transformer tool for data transformation",
                                this@buildAgent.id,
                                MessageType.TOOL_CALL,
                                mapOf("toolName" to "data_transformer")
                            )
                        } else {
                            message.createReply(
                                "Complex processing: ${message.content}",
                                this@buildAgent.id
                            )
                        }
                    }
                    MessageType.DATA -> {
                        message.createReply(
                            "Data received and analyzed: ${message.content}",
                            this@buildAgent.id,
                            MessageType.RESULT
                        )
                    }
                    else -> {
                        message.createReply(
                            "Default processing: ${message.content}",
                            this@buildAgent.id
                        )
                    }
                }
            }
        }
        
        // AgentEngine에 등록
        val agentEngine = AgentEngine()
        agentEngine.registerAgent(complexAgent)
        
        // Then: 통합 테스트
        
        // 1. 일반 텍스트 메시지
        val textMessage = Message(
            content = "Hello complex agent",
            sender = "user",
            type = MessageType.TEXT
        )
        val textResult = agentEngine.receive(textMessage)
        assertTrue(textResult.success)
        assertTrue(textResult.response.content.contains("Complex processing"))
        
        // 2. 계산 요청 메시지
        val calcMessage = Message(
            content = "Can you calculate something for me?",
            sender = "user",
            type = MessageType.TEXT
        )
        val calcResult = agentEngine.receive(calcMessage)
        assertTrue(calcResult.success, "Calculation message should be processed successfully: ${calcResult.error}")
        // AgentEngine에서 처리된 응답 확인 (실제 응답 내용 출력해서 디버깅)
        println("Calc Response: ${calcResult.response.content}")
        assertTrue(calcResult.response.content.isNotEmpty(), "Response should not be empty")
        
        // 3. 데이터 메시지
        val dataMessage = Message(
            content = "Sample data to process",
            sender = "user",
            type = MessageType.DATA
        )
        val dataResult = agentEngine.receive(dataMessage)
        assertTrue(dataResult.success)
        assertEquals(MessageType.RESULT, dataResult.response.type)
        assertTrue(dataResult.response.content.contains("Data received and analyzed"))
        
        // 4. Tool 직접 실행
        val calculator = complexAgent.getTools().find { it.name == "calculator" }!!
        val mathResult = calculator.execute(mapOf(
            "a" to 10,
            "b" to 5,
            "operation" to "add"
        ))
        assertTrue(mathResult.success)
        assertEquals("10.0 add 5.0 = 15.0", mathResult.result)
    }
} 