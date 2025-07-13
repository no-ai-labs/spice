package io.github.spice

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentBuilderTest {

    @Test
    fun `basic Agent creation with DSL test`() = runBlocking {
        // When: Create Agent with DSL
        val agent = agent {
            id = "test-agent"
            name = "Test Agent"
            description = "Test agent for DSL"
            capabilities = setOf("text-processing")
            supportedMessageTypes = setOf(MessageType.TEXT)
        }

        // Then: Verify Agent properties
        assertEquals("test-agent", agent.id)
        assertEquals("Test Agent", agent.name)
        assertEquals("Test agent for DSL", agent.description)
        assertEquals(setOf("text-processing"), agent.capabilities)
        assertEquals(setOf(MessageType.TEXT), agent.supportedMessageTypes)
        assertTrue(agent.canHandle(Message(id = "test", type = MessageType.TEXT, content = "test", agentId = "test")))
    }

    @Test
    fun `Agent creation with custom message handler using DSL test`() = runBlocking {
        // When: Create Agent with custom message handler
        val agent = agent {
            id = "custom-agent"
            name = "Custom Agent"
            description = "Agent with custom handler"
            capabilities = setOf("custom-processing")
            supportedMessageTypes = setOf(MessageType.TEXT, MessageType.DATA)
            
            messageHandler { message ->
                when (message.type) {
                    MessageType.TEXT -> Message(
                        id = "text-response-${message.id}",
                        type = MessageType.TEXT,
                        content = "Text processed: ${message.content}",
                        agentId = id,
                        parentId = message.id
                    )
                    MessageType.DATA -> Message(
                        id = "data-response-${message.id}",
                        type = MessageType.DATA,
                        content = "Data processed: ${message.content}",
                        agentId = id,
                        parentId = message.id
                    )
                    else -> Message(
                        id = "error-${message.id}",
                        type = MessageType.ERROR,
                        content = "Unsupported message type: ${message.type}",
                        agentId = id,
                        parentId = message.id
                    )
                }
            }
        }

        // Then: Test message processing
        val textMessage = Message(id = "text1", type = MessageType.TEXT, content = "Hello", agentId = "test")
        val textResponse = agent.process(textMessage)
        assertEquals("Text processed: Hello", textResponse.content)
        assertEquals(MessageType.TEXT, textResponse.type)

        val dataMessage = Message(id = "data1", type = MessageType.DATA, content = "Data", agentId = "test")
        val dataResponse = agent.process(dataMessage)
        assertEquals("Data processed: Data", dataResponse.content)
        assertEquals(MessageType.DATA, dataResponse.type)
    }

    @Test
    fun `Agent creation with custom Tool using DSL test`() = runBlocking {
        // When: Create Agent with custom Tool
        val agent = agent {
            id = "tool-agent"
            name = "Tool Agent"
            description = "Agent with custom tools"
            capabilities = setOf("tool-processing")
            supportedMessageTypes = setOf(MessageType.TEXT, MessageType.TOOL_CALL)
            
            tool("calculator") {
                description = "Simple calculator tool"
                parameters = mapOf(
                    "operation" to "string",
                    "a" to "number",
                    "b" to "number"
                )
                
                execute { params ->
                    val operation = params["operation"] as? String ?: "add"
                    val a = (params["a"] as? Number)?.toDouble() ?: 0.0
                    val b = (params["b"] as? Number)?.toDouble() ?: 0.0
                    
                    val result = when (operation) {
                        "add" -> a + b
                        "subtract" -> a - b
                        "multiply" -> a * b
                        "divide" -> if (b != 0.0) a / b else Double.NaN
                        else -> Double.NaN
                    }
                    
                    ToolResult(
                        success = !result.isNaN(),
                        data = mapOf("result" to result),
                        error = if (result.isNaN()) "Invalid operation or division by zero" else null
                    )
                }
            }
            
            messageHandler { message ->
                when (message.type) {
                    MessageType.TOOL_CALL -> {
                        val toolName = message.metadata["tool"] as? String
                        val tool = tools.find { it.name == toolName }
                        if (tool != null) {
                            val params = message.metadata["params"] as? Map<String, Any> ?: emptyMap()
                            val result = tool.execute(params)
                            Message(
                                id = "tool-result-${message.id}",
                                type = MessageType.TOOL_RESULT,
                                content = if (result.success) "Tool executed successfully" else "Tool execution failed",
                                agentId = id,
                                parentId = message.id,
                                metadata = mapOf("toolResult" to result)
                            )
                        } else {
                            Message(
                                id = "error-${message.id}",
                                type = MessageType.ERROR,
                                content = "Tool not found: $toolName",
                                agentId = id,
                                parentId = message.id
                            )
                        }
                    }
                    else -> Message(
                        id = "response-${message.id}",
                        type = MessageType.TEXT,
                        content = "Processed: ${message.content}",
                        agentId = id,
                        parentId = message.id
                    )
                }
            }
        }

        // Then: Test tool functionality
        assertEquals(1, agent.tools.size)
        assertEquals("calculator", agent.tools.first().name)
        
        val toolCall = Message(
            id = "tool1",
            type = MessageType.TOOL_CALL,
            content = "Calculate 5 + 3",
            agentId = "test",
            metadata = mapOf(
                "tool" to "calculator",
                "params" to mapOf("operation" to "add", "a" to 5, "b" to 3)
            )
        )
        
        val response = agent.process(toolCall)
        assertEquals(MessageType.TOOL_RESULT, response.type)
        val toolResult = response.metadata["toolResult"] as ToolResult
        assertTrue(toolResult.success)
        assertEquals(8.0, toolResult.data["result"])
    }

    @Test
    fun `textProcessingAgent creation with convenience function test`() = runBlocking {
        // When: Create Agent with convenience function
        val agent = textProcessingAgent(
            id = "text-processor",
            name = "Text Processor",
            description = "Processes text messages"
        ) { message ->
            Message(
                id = "processed-${message.id}",
                type = MessageType.TEXT,
                content = "Processed: ${message.content.uppercase()}",
                agentId = "text-processor",
                parentId = message.id
            )
        }

        // Then: Test functionality
        assertEquals("text-processor", agent.id)
        assertEquals("Text Processor", agent.name)
        assertEquals(setOf("text-processing"), agent.capabilities)
        assertEquals(setOf(MessageType.TEXT), agent.supportedMessageTypes)
        
        val testMessage = Message(id = "test", type = MessageType.TEXT, content = "hello", agentId = "test")
        val response = agent.process(testMessage)
        assertEquals("Processed: HELLO", response.content)
        assertEquals(MessageType.TEXT, response.type)
    }

    @Test
    fun `apiAgent creation with convenience function test`() = runBlocking {
        // When: Create API Agent
        val agent = apiAgent(
            id = "api-agent",
            name = "API Agent",
            description = "Handles API calls"
        ) { message ->
            // Simulate API call
            val apiResponse = when (message.content) {
                "get_weather" -> "Sunny, 25°C"
                "get_time" -> "2024-01-01 12:00:00"
                else -> "Unknown command"
            }
            
            Message(
                id = "api-response-${message.id}",
                type = MessageType.DATA,
                content = apiResponse,
                agentId = "api-agent",
                parentId = message.id,
                metadata = mapOf("api_call" to true)
            )
        }

        // Then: Test API functionality
        assertEquals("api-agent", agent.id)
        assertEquals("API Agent", agent.name)
        assertEquals(setOf("api-integration"), agent.capabilities)
        assertTrue(agent.supportedMessageTypes.contains(MessageType.TEXT))
        assertTrue(agent.supportedMessageTypes.contains(MessageType.DATA))
        
        val weatherRequest = Message(id = "weather", type = MessageType.TEXT, content = "get_weather", agentId = "test")
        val weatherResponse = agent.process(weatherRequest)
        assertEquals("Sunny, 25°C", weatherResponse.content)
        assertEquals(MessageType.DATA, weatherResponse.type)
        assertEquals(true, weatherResponse.metadata["api_call"])
    }

    @Test
    fun `routingAgent creation with convenience function test`() = runBlocking {
        // When: Create routing Agent
        val agent = routingAgent(
            id = "router",
            name = "Message Router",
            description = "Routes messages based on content"
        ) { message ->
            val targetAgent = when {
                message.content.contains("calculate") -> "calculator-agent"
                message.content.contains("weather") -> "weather-agent"
                else -> "default-agent"
            }
            
            Message(
                id = "route-${message.id}",
                type = MessageType.TEXT,
                content = "Routing to: $targetAgent",
                agentId = "router",
                parentId = message.id,
                metadata = mapOf("target_agent" to targetAgent)
            )
        }

        // Routing test
        val calcMessage = Message(id = "calc", type = MessageType.TEXT, content = "calculate 2+2", agentId = "test")
        val calcResponse = agent.process(calcMessage)
        assertEquals("calculator-agent", calcResponse.metadata["target_agent"])
        
        val weatherMessage = Message(id = "weather", type = MessageType.TEXT, content = "weather today", agentId = "test")
        val weatherResponse = agent.process(weatherMessage)
        assertEquals("weather-agent", weatherResponse.metadata["target_agent"])
        
        val defaultMessage = Message(id = "default", type = MessageType.TEXT, content = "hello", agentId = "test")
        val defaultResponse = agent.process(defaultMessage)
        assertEquals("default-agent", defaultResponse.metadata["target_agent"])
    }

    @Test
    fun `complex DSL Agent creation and AgentEngine integration test`() = runBlocking {
        // When: Create complex Agent
        val complexAgent = agent {
            id = "complex-agent"
            name = "Complex Agent"
            description = "Agent with multiple capabilities"
            capabilities = setOf("text-processing", "data-analysis", "tool-execution")
            supportedMessageTypes = setOf(MessageType.TEXT, MessageType.DATA, MessageType.TOOL_CALL)
            
            tool("text_analyzer") {
                description = "Analyzes text content"
                parameters = mapOf("text" to "string")
                
                execute { params ->
                    val text = params["text"] as? String ?: ""
                    val wordCount = text.split("\\s+".toRegex()).size
                    val charCount = text.length
                    
                    ToolResult(
                        success = true,
                        data = mapOf(
                            "word_count" to wordCount,
                            "char_count" to charCount,
                            "has_numbers" to text.any { it.isDigit() }
                        )
                    )
                }
            }
            
            tool("data_processor") {
                description = "Processes data"
                parameters = mapOf("data" to "any")
                
                execute { params ->
                    val data = params["data"]
                    ToolResult(
                        success = true,
                        data = mapOf("processed" to true, "original" to data)
                    )
                }
            }
            
            canHandle { message ->
                when (message.type) {
                    MessageType.TEXT -> message.content.isNotBlank()
                    MessageType.DATA -> true
                    MessageType.TOOL_CALL -> {
                        val toolName = message.metadata["tool"] as? String
                        tools.any { it.name == toolName }
                    }
                    else -> false
                }
            }
            
            messageHandler { message ->
                when (message.type) {
                    MessageType.TEXT -> {
                        if (message.content.startsWith("analyze:")) {
                            val textToAnalyze = message.content.removePrefix("analyze:")
                            val analyzerTool = tools.find { it.name == "text_analyzer" }
                            val result = analyzerTool?.execute(mapOf("text" to textToAnalyze))
                            
                            Message(
                                id = "analysis-${message.id}",
                                type = MessageType.DATA,
                                content = "Analysis complete",
                                agentId = id,
                                parentId = message.id,
                                metadata = mapOf("analysis_result" to result)
                            )
                        } else {
                            Message(
                                id = "text-response-${message.id}",
                                type = MessageType.TEXT,
                                content = "Text processed: ${message.content}",
                                agentId = id,
                                parentId = message.id
                            )
                        }
                    }
                    MessageType.DATA -> {
                        val processorTool = tools.find { it.name == "data_processor" }
                        val result = processorTool?.execute(mapOf("data" to message.content))
                        
                        Message(
                            id = "data-response-${message.id}",
                            type = MessageType.DATA,
                            content = "Data processed",
                            agentId = id,
                            parentId = message.id,
                            metadata = mapOf("processing_result" to result)
                        )
                    }
                    MessageType.TOOL_CALL -> {
                        val toolName = message.metadata["tool"] as? String
                        val tool = tools.find { it.name == toolName }
                        if (tool != null) {
                            val params = message.metadata["params"] as? Map<String, Any> ?: emptyMap()
                            val result = tool.execute(params)
                            Message(
                                id = "tool-result-${message.id}",
                                type = MessageType.TOOL_RESULT,
                                content = "Tool executed: $toolName",
                                agentId = id,
                                parentId = message.id,
                                metadata = mapOf("tool_result" to result)
                            )
                        } else {
                            Message(
                                id = "error-${message.id}",
                                type = MessageType.ERROR,
                                content = "Tool not found: $toolName",
                                agentId = id,
                                parentId = message.id
                            )
                        }
                    }
                    else -> Message(
                        id = "error-${message.id}",
                        type = MessageType.ERROR,
                        content = "Unsupported message type: ${message.type}",
                        agentId = id,
                        parentId = message.id
                    )
                }
            }
        }

        // Then: Test complex Agent functionality
        assertEquals("complex-agent", complexAgent.id)
        assertEquals(2, complexAgent.tools.size)
        assertTrue(complexAgent.tools.any { it.name == "text_analyzer" })
        assertTrue(complexAgent.tools.any { it.name == "data_processor" })

        // Test AgentEngine integration
        val engine = AgentEngine()
        engine.registerAgent(complexAgent)
        
        // 1. Text analysis
        val analysisMessage = Message(
            id = "analysis",
            type = MessageType.TEXT,
            content = "analyze:Hello world 123",
            agentId = "test"
        )
        val analysisResult = engine.processMessage(analysisMessage)
        
        // Verify response from AgentEngine processing (print actual response content for debugging)
        println("Analysis result: ${analysisResult.response.content}")
        assertEquals(MessageType.DATA, analysisResult.response.type)
        
        // 2. Data message
        val dataMessage = Message(
            id = "data",
            type = MessageType.DATA,
            content = "sample data",
            agentId = "test"
        )
        val dataResult = engine.processMessage(dataMessage)
        assertEquals(MessageType.DATA, dataResult.response.type)
        
        // 3. Tool call
        val toolMessage = Message(
            id = "tool",
            type = MessageType.TOOL_CALL,
            content = "Call text analyzer",
            agentId = "test",
            metadata = mapOf(
                "tool" to "text_analyzer",
                "params" to mapOf("text" to "Test text")
            )
        )
        val toolResult = engine.processMessage(toolMessage)
        assertEquals(MessageType.TOOL_RESULT, toolResult.response.type)
    }
} 