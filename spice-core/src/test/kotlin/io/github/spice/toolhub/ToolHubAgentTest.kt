package io.github.spice.toolhub

import io.github.spice.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ToolHubAgentTest {
    
    private lateinit var baseAgent: BaseAgent
    private lateinit var toolHub: StaticToolHub
    private lateinit var testTool: Tool
    
    @BeforeEach
    fun setup() {
        // 테스트용 도구 생성
        testTool = object : BaseTool(
            name = "test_formatter",
            description = "Text formatter for testing",
            schema = ToolSchema(
                name = "test_formatter",
                description = "Text formatter",
                parameters = mapOf(
                    "text" to ParameterSchema("string", "Text to format", required = true),
                    "format" to ParameterSchema("string", "Format type", required = false)
                )
            )
        ) {
            override suspend fun execute(parameters: Map<String, Any>): io.github.spice.ToolResult {
                val text = parameters["text"] as? String ?: ""
                val format = parameters["format"] as? String ?: "uppercase"
                
                val result = when (format) {
                    "uppercase" -> text.uppercase()
                    "lowercase" -> text.lowercase()
                    "reverse" -> text.reversed()
                    "length" -> text.length.toString()
                    else -> text
                }
                
                return io.github.spice.ToolResult.success(
                    result = result,
                    metadata = mapOf(
                        "original_text" to text,
                        "format" to format
                    )
                )
            }
        }
        
        // 기본 Agent 생성
        baseAgent = object : BaseAgent(
            id = "test-agent",
            name = "Test Agent",
            description = "Agent for testing ToolHub integration",
            capabilities = listOf("text-processing")
        ) {
            override suspend fun processMessage(message: Message): Message {
                return message.createReply(
                    content = "Base agent processed: ${message.content}",
                    sender = id,
                    type = MessageType.TEXT
                )
            }
        }
        
        // ToolHub 생성
        toolHub = StaticToolHub(listOf(testTool))
    }
    
    @Test
    fun `ToolHubAgent 기본 기능 테스트`() = runBlocking {
        val toolHubAgent = baseAgent.withToolHub(toolHub)
        
        // Agent 기본 정보 확인
        assertEquals("test-agent", toolHubAgent.id)
        assertEquals("Test Agent", toolHubAgent.name)
        assertEquals("Agent for testing ToolHub integration", toolHubAgent.description)
        assertEquals(listOf("text-processing"), toolHubAgent.capabilities)
        
        // 도구 목록 확인 (기본 Agent 도구 + ToolHub 도구)
        val tools = toolHubAgent.getTools()
        assertEquals(1, tools.size) // ToolHub의 도구 1개
        assertEquals("test_formatter", tools[0].name)
        
        // ToolHub 접근 확인
        assertTrue(toolHubAgent.getToolHub() is StaticToolHub)
        assertEquals(1, toolHubAgent.getToolHub().listTools().size)
    }
    
    @Test
    fun `ToolHubAgent 도구 호출 테스트`() = runBlocking {
        val toolHubAgent = baseAgent.withToolHub(toolHub)
        
        // 도구 호출 메시지 생성
        val toolCallMessage = Message(
            id = "tool-call-1",
            type = MessageType.TOOL_CALL,
            content = "Format text",
            sender = "user",
            metadata = mapOf(
                "toolName" to "test_formatter",
                "param_text" to "Hello World",
                "param_format" to "uppercase"
            )
        )
        
        // 도구 호출 처리
        val response = toolHubAgent.processMessage(toolCallMessage)
        
        assertEquals(MessageType.TOOL_RESULT, response.type)
        assertEquals("HELLO WORLD", response.content)
        assertEquals("test-agent", response.sender)
        assertEquals("tool-call-1", response.parentId)
        
        // 메타데이터 확인
        assertEquals("test_formatter", response.metadata["toolName"])
        assertEquals("true", response.metadata["toolSuccess"])
    }
    
    @Test
    fun `ToolHubAgent 일반 메시지 처리 테스트`() = runBlocking {
        val toolHubAgent = baseAgent.withToolHub(toolHub)
        
        // 일반 텍스트 메시지
        val textMessage = Message(
            id = "text-1",
            type = MessageType.TEXT,
            content = "Hello",
            sender = "user"
        )
        
        // 기본 Agent의 processMessage가 호출되어야 함
        val response = toolHubAgent.processMessage(textMessage)
        
        assertEquals(MessageType.TEXT, response.type)
        assertEquals("Base agent processed: Hello", response.content)
        assertEquals("test-agent", response.sender)
    }
    
    @Test
    fun `ToolHubAgent canHandle 테스트`() = runBlocking {
        val toolHubAgent = baseAgent.withToolHub(toolHub)
        
        // 기본 Agent가 처리할 수 있는 메시지
        val textMessage = Message(
            id = "text-1",
            type = MessageType.TEXT,
            content = "Hello",
            sender = "user"
        )
        assertTrue(toolHubAgent.canHandle(textMessage))
        
        // ToolHub 도구 호출 메시지
        val toolCallMessage = Message(
            id = "tool-call-1",
            type = MessageType.TOOL_CALL,
            content = "Format text",
            sender = "user",
            metadata = mapOf("toolName" to "test_formatter")
        )
        assertTrue(toolHubAgent.canHandle(toolCallMessage))
        
        // 존재하지 않는 도구 호출 메시지
        val unknownToolMessage = Message(
            id = "tool-call-2",
            type = MessageType.TOOL_CALL,
            content = "Unknown tool",
            sender = "user",
            metadata = mapOf("toolName" to "unknown_tool")
        )
        assertFalse(toolHubAgent.canHandle(unknownToolMessage))
    }
    
    @Test
    fun `ToolHubAgent 도구 실행 통계 테스트`() = runBlocking {
        val toolHubAgent = baseAgent.withToolHub(toolHub)
        
        // 여러 번 도구 실행
        repeat(3) { i ->
            val toolCallMessage = Message(
                id = "tool-call-$i",
                type = MessageType.TOOL_CALL,
                content = "Format text",
                sender = "user",
                metadata = mapOf(
                    "toolName" to "test_formatter",
                    "param_text" to "test$i",
                    "param_format" to "uppercase"
                )
            )
            
            toolHubAgent.processMessage(toolCallMessage)
        }
        
        // 실행 통계 확인
        val stats = toolHubAgent.getToolExecutionStats()
        assertEquals(3, stats["total_executions"])
        
        val toolCounts = stats["tool_execution_counts"] as Map<String, Int>
        assertEquals(3, toolCounts["test_formatter"])
        
        val successRate = stats["success_rate"] as Double
        assertEquals(100.0, successRate)
    }
    
    @Test
    fun `ToolHubAgent 도구 실행 실패 테스트`() = runBlocking {
        val toolHubAgent = baseAgent.withToolHub(toolHub)
        
        // 존재하지 않는 도구 호출
        val unknownToolMessage = Message(
            id = "tool-call-unknown",
            type = MessageType.TOOL_CALL,
            content = "Unknown tool",
            sender = "user",
            metadata = mapOf("toolName" to "unknown_tool")
        )
        
        val response = toolHubAgent.processMessage(unknownToolMessage)
        
        assertEquals(MessageType.ERROR, response.type)
        assertTrue(response.content.contains("not found"))
        assertEquals("unknown_tool", response.metadata["toolName"])
        assertEquals("toolhub_error", response.metadata["errorType"])
    }
    
    @Test
    fun `createToolHubAgent 함수 테스트`() = runBlocking {
        val toolHubAgent = createToolHubAgent(
            id = "hub-agent",
            name = "Hub Agent",
            description = "Agent with ToolHub",
            toolHub = toolHub,
            capabilities = listOf("formatting"),
            messageHandler = { message ->
                message.createReply(
                    content = "Custom handler: ${message.content}",
                    sender = "hub-agent",
                    type = MessageType.TEXT
                )
            }
        )
        
        assertEquals("hub-agent", toolHubAgent.id)
        assertEquals("Hub Agent", toolHubAgent.name)
        assertEquals(listOf("formatting"), toolHubAgent.capabilities)
        
        // 커스텀 메시지 핸들러 테스트
        val message = Message(
            id = "test-1",
            type = MessageType.TEXT,
            content = "Hello",
            sender = "user"
        )
        
        val response = toolHubAgent.processMessage(message)
        assertEquals("Custom handler: Hello", response.content)
    }
    
    @Test
    fun `toolHubAgent DSL 테스트`() = runBlocking {
        val toolHubAgent = toolHubAgent(
            id = "dsl-agent",
            name = "DSL Agent",
            description = "Agent created with DSL",
            toolHub = toolHub
        ) {
            capabilities("formatting", "text-processing")
            messageHandler { message ->
                message.createReply(
                    content = "DSL handler: ${message.content}",
                    sender = "dsl-agent",
                    type = MessageType.TEXT
                )
            }
        }
        
        assertEquals("dsl-agent", toolHubAgent.id)
        assertEquals("DSL Agent", toolHubAgent.name)
        assertEquals(listOf("formatting", "text-processing"), toolHubAgent.capabilities)
        
        // DSL 메시지 핸들러 테스트
        val message = Message(
            id = "test-1",
            type = MessageType.TEXT,
            content = "Hello DSL",
            sender = "user"
        )
        
        val response = toolHubAgent.processMessage(message)
        assertEquals("DSL handler: Hello DSL", response.content)
    }
    
    @Test
    fun `ToolContext 접근 테스트`() = runBlocking {
        val toolHubAgent = baseAgent.withToolHub(toolHub)
        
        // 도구 실행으로 컨텍스트에 데이터 추가
        val toolCallMessage = Message(
            id = "tool-call-1",
            type = MessageType.TOOL_CALL,
            content = "Format text",
            sender = "user",
            metadata = mapOf(
                "toolName" to "test_formatter",
                "param_text" to "Hello",
                "param_format" to "uppercase"
            )
        )
        
        toolHubAgent.processMessage(toolCallMessage)
        
        // 컨텍스트 확인
        val context = toolHubAgent.getToolContext()
        assertEquals(1, context.callHistory.size)
        assertEquals("test_formatter", context.callHistory[0].toolName)
        assertTrue(context.callHistory[0].isSuccess)
        
        // 메타데이터 확인
        val lastResult = context.getLastResult()
        assertTrue(lastResult?.success == true)
        assertEquals("HELLO", (lastResult as ToolResult.Success).output)
    }
} 