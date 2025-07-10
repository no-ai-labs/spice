package io.github.spice

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MessageTypeProcessingTest {
    
    @Test
    fun `모든 MessageType별 Agent 처리 검증`() = runBlocking {
        // Given: 모든 타입을 처리할 수 있는 UniversalAgent 생성
        val universalAgent = UniversalAgent()
        val agentEngine = AgentEngine()
        agentEngine.registerAgent(universalAgent)
        
        // When & Then: 각 MessageType별로 처리 검증
        testMessageTypeProcessing(agentEngine, MessageType.TEXT, "일반 텍스트")
        testMessageTypeProcessing(agentEngine, MessageType.PROMPT, "프롬프트 메시지")
        testMessageTypeProcessing(agentEngine, MessageType.SYSTEM, "시스템 메시지")
        testMessageTypeProcessing(agentEngine, MessageType.DATA, "데이터 메시지")
        testMessageTypeProcessing(agentEngine, MessageType.RESULT, "결과 메시지")
        testMessageTypeProcessing(agentEngine, MessageType.BRANCH, "분기 메시지")
        testMessageTypeProcessing(agentEngine, MessageType.MERGE, "병합 메시지")
        testMessageTypeProcessing(agentEngine, MessageType.WORKFLOW_START, "워크플로우 시작")
        testMessageTypeProcessing(agentEngine, MessageType.WORKFLOW_END, "워크플로우 종료")
        testMessageTypeProcessing(agentEngine, MessageType.ERROR, "에러 메시지")
    }
    
    @Test
    fun `TOOL_CALL 메시지 처리 검증`() = runBlocking {
        // Given: Tool을 가진 ToolAgent 생성
        val toolAgent = ToolAgent()
        val agentEngine = AgentEngine()
        agentEngine.registerAgent(toolAgent)
        
        // When: TOOL_CALL 메시지 전송
        val toolCallMessage = Message(
            content = "test_tool을 실행해줘",
            sender = "user",
            type = MessageType.TOOL_CALL,
            metadata = mapOf(
                "toolName" to "test_tool",
                "param_input" to "test_value"
            )
        )
        
        val result = agentEngine.receive(toolCallMessage)
        
        // Then: Tool 결과 검증
        assertTrue(result.success, "TOOL_CALL 처리가 성공해야 합니다")
        assertEquals(MessageType.TOOL_RESULT, result.response.type, "응답 타입이 TOOL_RESULT여야 합니다")
        assertTrue(result.response.content.contains("Tool executed: test_tool"), "Tool 실행 결과가 포함되어야 합니다")
    }
    
    @Test
    fun `메타데이터 조건 분기 케이스 검증`() = runBlocking {
        // Given: 메타데이터 기반 조건부 Agent
        val conditionalAgent = ConditionalAgent()
        val agentEngine = AgentEngine()
        agentEngine.registerAgent(conditionalAgent)
        
        // Test Case 1: 우선순위 높은 메시지
        val highPriorityMessage = Message(
            content = "중요한 메시지",
            sender = "user",
            type = MessageType.TEXT,
            metadata = mapOf("priority" to "high")
        )
        
        val highResult = agentEngine.receive(highPriorityMessage)
        assertTrue(highResult.response.content.contains("HIGH_PRIORITY"), "높은 우선순위 처리 확인")
        
        // Test Case 2: 일반 메시지
        val normalMessage = Message(
            content = "일반 메시지",
            sender = "user",
            type = MessageType.TEXT,
            metadata = mapOf("priority" to "normal")
        )
        
        val normalResult = agentEngine.receive(normalMessage)
        assertTrue(normalResult.response.content.contains("NORMAL"), "일반 우선순위 처리 확인")
        
        // Test Case 3: 특별한 capability 요구
        val capabilityMessage = Message(
            content = "분석 요청",
            sender = "user",
            type = MessageType.TEXT,
            metadata = mapOf("requiredCapability" to "analysis")
        )
        
        val capabilityResult = agentEngine.receive(capabilityMessage)
        assertTrue(capabilityResult.response.content.contains("ANALYSIS"), "특별 capability 처리 확인")
    }
    
    @Test
    fun `Agent 선택 우선순위 검증`() = runBlocking {
        // Given: 여러 Agent 등록
        val agentEngine = AgentEngine()
        agentEngine.registerAgent(PromptAgent())
        agentEngine.registerAgent(DataAgent())
        agentEngine.registerAgent(ResultAgent())
        agentEngine.registerAgent(BranchAgent())
        agentEngine.registerAgent(MergeAgent())
        
        // When & Then: 각 타입별로 적절한 Agent가 선택되는지 확인
        val promptMessage = Message(content = "프롬프트", sender = "user", type = MessageType.PROMPT)
        val promptResult = agentEngine.receive(promptMessage)
        assertEquals("prompt-agent", promptResult.agentId, "PROMPT 타입은 PromptAgent가 처리해야 함")
        
        val dataMessage = Message(content = "데이터", sender = "user", type = MessageType.DATA)
        val dataResult = agentEngine.receive(dataMessage)
        assertEquals("data-agent", dataResult.agentId, "DATA 타입은 DataAgent가 처리해야 함")
        
        val resultMessage = Message(content = "결과", sender = "user", type = MessageType.RESULT)
        val resultResultAgent = agentEngine.receive(resultMessage)
        assertEquals("result-agent", resultResultAgent.agentId, "RESULT 타입은 ResultAgent가 처리해야 함")
    }
    
    @Test
    fun `Message 라우팅 규칙 검증`() = runBlocking {
        // Given: Agent와 라우팅 규칙이 있는 Engine
        val universalAgent = UniversalAgent()
        val agentEngine = AgentEngine()
        agentEngine.registerAgent(universalAgent)
        
        // When: 라우팅이 적용될 수 있는 메시지 전송
        val routingMessage = Message(
            content = "라우팅 테스트",
            sender = "user",
            type = MessageType.PROMPT,
            metadata = mapOf("isSecondPrompt" to "true")
        )
        
        val result = agentEngine.receive(routingMessage)
        
        // Then: 라우팅 적용 확인
        assertTrue(result.success, "라우팅된 메시지 처리가 성공해야 합니다")
        assertNotNull(result.metadata["routingApplied"], "라우팅 적용 메타데이터가 있어야 합니다")
    }
    
    private suspend fun testMessageTypeProcessing(
        agentEngine: AgentEngine,
        messageType: MessageType,
        content: String
    ) {
        val message = Message(
            content = content,
            sender = "user",
            type = messageType
        )
        
        val result = agentEngine.receive(message)
        
        assertTrue(result.success, "${messageType} 타입 처리가 성공해야 합니다")
        assertNotNull(result.response, "${messageType} 타입에 대한 응답이 있어야 합니다")
        assertTrue(result.response.content.isNotBlank(), "${messageType} 타입 응답 내용이 있어야 합니다")
        assertEquals("universal-agent", result.agentId, "UniversalAgent가 처리해야 합니다")
    }
}

/**
 * 모든 MessageType을 처리할 수 있는 테스트용 Agent
 */
class UniversalAgent(
    id: String = "universal-agent",
    name: String = "Universal Test Agent",
    description: String = "Agent that can handle all message types"
) : BaseAgent(id, name, description, listOf("universal", "testing", "all_types")) {
    
    override suspend fun processMessage(message: Message): Message {
        val responseContent = when (message.type) {
            MessageType.TEXT -> "Text processed: ${message.content}"
            MessageType.PROMPT -> "Prompt processed: ${message.content}"
            MessageType.SYSTEM -> "System message handled: ${message.content}"
            MessageType.DATA -> "Data analyzed: ${message.content}"
            MessageType.RESULT -> "Result formatted: ${message.content}"
            MessageType.BRANCH -> "Branch logic applied: ${message.content}"
            MessageType.MERGE -> "Merge operation completed: ${message.content}"
            MessageType.WORKFLOW_START -> "Workflow started: ${message.content}"
            MessageType.WORKFLOW_END -> "Workflow ended: ${message.content}"
            MessageType.ERROR -> "Error handled: ${message.content}"
            MessageType.TOOL_CALL -> "Tool call processed: ${message.content}"
            MessageType.TOOL_RESULT -> "Tool result received: ${message.content}"
            MessageType.INTERRUPT -> "Interrupt handled: ${message.content}"
            MessageType.RESUME -> "Resume processed: ${message.content}"
        }
        
        return message.createReply(
            content = responseContent,
            sender = id,
            type = MessageType.TEXT,
            metadata = mapOf(
                "originalType" to message.type.toString(),
                "processedAt" to System.currentTimeMillis().toString()
            )
        )
    }
    
    override fun canHandle(message: Message): Boolean = true
}

/**
 * Tool을 가진 테스트용 Agent
 */
class ToolAgent(
    id: String = "tool-agent",
    name: String = "Tool Test Agent",
    description: String = "Agent with test tools"
) : BaseAgent(id, name, description, listOf("tool_execution")) {
    
    init {
        addTool(TestTool())
    }
    
    override suspend fun processMessage(message: Message): Message {
        return when (message.type) {
            MessageType.TOOL_CALL -> {
                val toolName = message.metadata["toolName"]
                if (toolName != null) {
                    val parameters = message.metadata
                        .filterKeys { it.startsWith("param_") }
                        .mapKeys { it.key.removePrefix("param_") }
                        .mapValues { it.value as Any }
                    
                    val toolResult = executeTool(toolName, parameters)
                    
                    message.createReply(
                        content = if (toolResult.success) toolResult.result else toolResult.error,
                        sender = id,
                        type = MessageType.TOOL_RESULT,
                        metadata = mapOf("toolExecuted" to toolName) + toolResult.metadata
                    )
                } else {
                    message.createReply(
                        content = "Tool name not specified",
                        sender = id,
                        type = MessageType.ERROR
                    )
                }
            }
            else -> {
                message.createReply(
                    content = "Processed: ${message.content}",
                    sender = id,
                    type = MessageType.TEXT
                )
            }
        }
    }
}

/**
 * 메타데이터 조건부 처리 Agent
 */
class ConditionalAgent(
    id: String = "conditional-agent",
    name: String = "Conditional Test Agent",
    description: String = "Agent that processes based on metadata conditions"
) : BaseAgent(id, name, description, listOf("conditional", "analysis")) {
    
    override suspend fun processMessage(message: Message): Message {
        val responseContent = when {
            message.metadata["priority"] == "high" -> "HIGH_PRIORITY: ${message.content} processed with urgency"
            message.metadata["requiredCapability"] == "analysis" -> "ANALYSIS: Deep analysis of ${message.content}"
            else -> "NORMAL: Standard processing of ${message.content}"
        }
        
        return message.createReply(
            content = responseContent,
            sender = id,
            type = MessageType.TEXT,
            metadata = mapOf(
                "conditionMet" to "true",
                "processingMode" to when {
                    message.metadata["priority"] == "high" -> "high_priority"
                    message.metadata["requiredCapability"] == "analysis" -> "analysis"
                    else -> "normal"
                }
            )
        )
    }
}

/**
 * 테스트용 간단한 Tool
 */
class TestTool : BaseTool(
    name = "test_tool",
    description = "Simple test tool for verification",
    schema = ToolSchema(
        name = "test_tool",
        description = "Test tool",
        parameters = mapOf(
            "input" to ParameterSchema(
                type = "string",
                description = "Test input parameter",
                required = true
            )
        )
    )
) {
    
    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
        val input = parameters["input"] as? String ?: "default"
        return ToolResult.success(
            result = "Tool executed: test_tool with input: $input",
            metadata = mapOf("execution_time" to System.currentTimeMillis().toString())
        )
    }
    
    override fun canExecute(parameters: Map<String, Any>): Boolean = true
} 