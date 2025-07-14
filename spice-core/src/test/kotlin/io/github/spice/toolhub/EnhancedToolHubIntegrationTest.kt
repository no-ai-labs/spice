package io.github.spice.toolhub

import io.github.spice.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * improvement된 AgentBuilder와 ToolHub 통합 test
 */
class EnhancedToolHubIntegrationTest {
    
    private lateinit var enhancedTools: List<Tool>
    private lateinit var toolHub: StaticToolHub
    
    @BeforeEach
    fun setup() {
        // improvement된 ToolBuilder로 generation한 도구들
        enhancedTools = listOf(
            createEnhancedCalculatorTool(),
            createEnhancedTextProcessorTool(),
            createEnhancedDataValidatorTool()
        )
        
        toolHub = StaticToolHub(enhancedTools)
    }
    
    private fun createEnhancedCalculatorTool(): Tool {
        // DSL을 사용하지 않고 직접 Tool generation (ToolBuilder 스타일)
        return object : BaseTool(
            name = "enhanced_calculator",
            description = "Enhanced calculator with validation",
            schema = ToolSchema(
                name = "enhanced_calculator",
                description = "Enhanced calculator with validation",
                parameters = mapOf(
                    "operation" to ParameterSchema("string", "Mathematical operation", required = true),
                    "a" to ParameterSchema("number", "First number", required = true),
                    "b" to ParameterSchema("number", "Second number", required = true),
                    "precision" to ParameterSchema("number", "Decimal precision", required = false)
                )
            )
        ) {
            override fun canExecute(parameters: Map<String, Any>): Boolean {
                val operation = parameters["operation"] as? String
                val validOps = setOf("add", "subtract", "multiply", "divide", "power", "sqrt")
                return operation in validOps && super.canExecute(parameters)
            }
            
            override suspend fun execute(parameters: Map<String, Any>): io.github.spice.ToolResult {
                val operation = parameters["operation"] as? String ?: "add"
                val a = (parameters["a"] as? Number)?.toDouble() ?: 0.0
                val b = (parameters["b"] as? Number)?.toDouble() ?: 0.0
                val precision = (parameters["precision"] as? Number)?.toInt() ?: 2
                
                val result = when (operation) {
                    "add" -> a + b
                    "subtract" -> a - b
                    "multiply" -> a * b
                    "divide" -> {
                        if (b == 0.0) {
                            return io.github.spice.ToolResult.error("Cannot divide by zero")
                        }
                        a / b
                    }
                    "power" -> kotlin.math.pow(a, b)
                    "sqrt" -> kotlin.math.sqrt(a)
                    else -> return io.github.spice.ToolResult.error("Unknown operation: $operation")
                }
                
                val formattedResult = "%.${precision}f".format(result)
                return io.github.spice.ToolResult.success(
                    result = formattedResult,
                    metadata = mapOf(
                        "operation" to operation,
                        "operands" to "$a, $b",
                        "precision" to precision.toString()
                    )
                )
            }
        }
    }
    
    private fun createEnhancedTextProcessorTool(): Tool {
        return object : BaseTool(
            name = "enhanced_text_processor",
            description = "Enhanced text processing with multiple operations",
            schema = ToolSchema(
                name = "enhanced_text_processor",
                description = "Enhanced text processing with multiple operations",
                parameters = mapOf(
                    "text" to ParameterSchema("string", "Input text", required = true),
                    "operations" to ParameterSchema("array", "List of operations to apply", required = true),
                    "delimiter" to ParameterSchema("string", "Delimiter for operations", required = false)
                )
            )
        ) {
            override fun canExecute(parameters: Map<String, Any>): Boolean {
                val text = parameters["text"] as? String
                val operations = parameters["operations"] as? List<*>
                return !text.isNullOrBlank() && !operations.isNullOrEmpty()
            }
            
            override suspend fun execute(parameters: Map<String, Any>): io.github.spice.ToolResult {
                val text = parameters["text"] as String
                val operations = parameters["operations"] as List<String>
                val delimiter = parameters["delimiter"] as? String ?: " | "
                
                val results = mutableListOf<String>()
                var currentText = text
                
                operations.forEach { operation ->
                    currentText = when (operation) {
                        "uppercase" -> currentText.uppercase()
                        "lowercase" -> currentText.lowercase()
                        "reverse" -> currentText.reversed()
                        "trim" -> currentText.trim()
                        "capitalize" -> currentText.replaceFirstChar { it.uppercase() }
                        "length" -> currentText.length.toString()
                        "words" -> currentText.split("\\s+".toRegex()).size.toString()
                        else -> currentText
                    }
                    results.add("$operation: $currentText")
                }
                
                return io.github.spice.ToolResult.success(
                    result = results.joinToString(delimiter),
                    metadata = mapOf(
                        "original_text" to text,
                        "operations_count" to operations.size.toString(),
                        "final_text" to currentText
                    )
                )
            }
        }
    }
    
    private fun createEnhancedDataValidatorTool(): Tool {
        return object : BaseTool(
            name = "enhanced_data_validator",
            description = "Enhanced data validation with custom rules",
            schema = ToolSchema(
                name = "enhanced_data_validator",
                description = "Enhanced data validation with custom rules",
                parameters = mapOf(
                    "data" to ParameterSchema("any", "Data to validate", required = true),
                    "rules" to ParameterSchema("array", "Validation rules", required = true),
                    "fail_fast" to ParameterSchema("boolean", "Stop on first failure", required = false)
                )
            )
        ) {
            override suspend fun execute(parameters: Map<String, Any>): io.github.spice.ToolResult {
                val data = parameters["data"]
                val rules = parameters["rules"] as List<String>
                val failFast = parameters["fail_fast"] as? Boolean ?: false
                
                val validationResults = mutableListOf<String>()
                var isValid = true
                
                for (rule in rules) {
                    val ruleResult = when (rule) {
                        "not_null" -> {
                            if (data == null) {
                                "FAIL: Data is null"
                            } else {
                                "PASS: Data is not null"
                            }
                        }
                        "not_empty" -> {
                            if (data.toString().isEmpty()) {
                                "FAIL: Data is empty"
                            } else {
                                "PASS: Data is not empty"
                            }
                        }
                        "is_number" -> {
                            if (data is Number || data.toString().toDoubleOrNull() != null) {
                                "PASS: Data is a number"
                            } else {
                                "FAIL: Data is not a number"
                            }
                        }
                        "is_string" -> {
                            if (data is String) {
                                "PASS: Data is a string"
                            } else {
                                "FAIL: Data is not a string"
                            }
                        }
                        "min_length_3" -> {
                            if (data.toString().length >= 3) {
                                "PASS: Data length >= 3"
                            } else {
                                "FAIL: Data length < 3"
                            }
                        }
                        else -> "SKIP: Unknown rule: $rule"
                    }
                    
                    validationResults.add(ruleResult)
                    
                    if (ruleResult.startsWith("FAIL")) {
                        isValid = false
                        if (failFast) break
                    }
                }
                
                return io.github.spice.ToolResult(
                    success = isValid,
                    result = validationResults.joinToString("\n"),
                    metadata = mapOf(
                        "total_rules" to rules.size.toString(),
                        "checked_rules" to validationResults.size.toString(),
                        "fail_fast" to failFast.toString()
                    )
                )
            }
        }
    }
    
    @Test
    fun `개선된 ToolBuilder와 ToolHub 통합 테스트`() = runBlocking {
        toolHub.start()
        
        // 1. 계산기 도구 test
        val context = ToolContext()
        
        val calcResult = toolHub.callTool(
            name = "enhanced_calculator",
            parameters = mapOf(
                "operation" to "power",
                "a" to 2,
                "b" to 8,
                "precision" to 0
            ),
            context = context
        )
        
        assertTrue(calcResult.success)
        assertEquals("256", (calcResult as ToolResult.Success).output)
        
        // 2. 텍스트 processing기 test
        val textResult = toolHub.callTool(
            name = "enhanced_text_processor",
            parameters = mapOf(
                "text" to "  hello world  ",
                "operations" to listOf("trim", "capitalize", "uppercase"),
                "delimiter" to " -> "
            ),
            context = context
        )
        
        assertTrue(textResult.success)
        val textOutput = (textResult as ToolResult.Success).output.toString()
        assertTrue(textOutput.contains("trim: hello world"))
        assertTrue(textOutput.contains("capitalize: Hello world"))
        assertTrue(textOutput.contains("uppercase: HELLO WORLD"))
        
        // 3. data validation기 test
        val validationResult = toolHub.callTool(
            name = "enhanced_data_validator",
            parameters = mapOf(
                "data" to "12345",
                "rules" to listOf("not_null", "not_empty", "is_number", "min_length_3"),
                "fail_fast" to false
            ),
            context = context
        )
        
        assertTrue(validationResult.success)
        val validationOutput = (validationResult as ToolResult.Success).output.toString()
        assertTrue(validationOutput.contains("PASS: Data is not null"))
        assertTrue(validationOutput.contains("PASS: Data is a number"))
        
        // execution statistics check
        assertEquals(3, context.callHistory.size)
        assertTrue(context.callHistory.all { it.isSuccess })
        
        toolHub.stop()
    }
    
    @Test
    fun `Agent DSL과 ToolHub 통합 테스트`() = runBlocking {
        // DSL로 Agent generation (improvement된 feature 사용)
        val agent = agent {
            name = "Enhanced ToolHub Agent"
            description = "Agent using enhanced DSL features with ToolHub"
            capabilities("calculation", "text-processing", "data-validation")
            
            // Agent 자체 도구 정의 (improvement된 parameter() function 사용)
            tool("agent_formatter") {
                description = "Agent's own formatting tool"
                
                parameter("input", "string", "Text to format", required = true)
                parameter("style", "string", "Formatting style", required = false)
                parameter("prefix", "string", "Prefix to add", required = false)
                
                canExecute { params ->
                    val input = params["input"] as? String
                    !input.isNullOrBlank()
                }
                
                execute { params ->
                    val input = params["input"] as String
                    val style = params["style"] as? String ?: "default"
                    val prefix = params["prefix"] as? String ?: ""
                    
                    val formatted = when (style) {
                        "brackets" -> "[$input]"
                        "quotes" -> "\"$input\""
                        "stars" -> "***$input***"
                        else -> input
                    }
                    
                    val result = if (prefix.isNotEmpty()) "$prefix$formatted" else formatted
                    
                    ToolResult(
                        success = true,
                        result = result,
                        metadata = mapOf(
                            "original" to input,
                            "style" to style,
                            "prefix" to prefix
                        )
                    )
                }
            }
            
            messageHandler { message ->
                when (message.type) {
                    MessageType.TEXT -> {
                        // 텍스트 message를 자동으로 포맷팅
                        val formattedMessage = Message(
                            id = "auto-format-${message.id}",
                            type = MessageType.TOOL_CALL,
                            content = "Auto formatting",
                            sender = message.sender,
                            metadata = mapOf(
                                "toolName" to "agent_formatter",
                                "param_input" to message.content,
                                "param_style" to "brackets",
                                "param_prefix" to "Agent: "
                            )
                        )
                        processMessage(formattedMessage)
                    }
                    else -> {
                        message.createReply(
                            content = "Enhanced agent processed: ${message.content}",
                            sender = id,
                            type = MessageType.TEXT
                        )
                    }
                }
            }
        }
        
        // ToolHub와 통합
        val toolHubAgent = agent.withToolHub(toolHub)
        toolHub.start()
        
        // 1. Agent 자체 도구 test
        val agentToolMessage = Message(
            id = "agent-tool-test",
            type = MessageType.TOOL_CALL,
            content = "Agent tool test",
            sender = "user",
            metadata = mapOf(
                "toolName" to "agent_formatter",
                "param_input" to "Hello World",
                "param_style" to "stars",
                "param_prefix" to ">> "
            )
        )
        
        val agentToolResponse = toolHubAgent.processMessage(agentToolMessage)
        assertEquals(MessageType.TOOL_RESULT, agentToolResponse.type)
        assertEquals(">> ***Hello World***", agentToolResponse.content)
        
        // 2. ToolHub 도구 test
        val hubToolMessage = Message(
            id = "hub-tool-test",
            type = MessageType.TOOL_CALL,
            content = "Hub tool test",
            sender = "user",
            metadata = mapOf(
                "toolName" to "enhanced_calculator",
                "param_operation" to "sqrt",
                "param_a" to 16,
                "param_b" to 0,
                "param_precision" to 1
            )
        )
        
        val hubToolResponse = toolHubAgent.processMessage(hubToolMessage)
        assertEquals(MessageType.TOOL_RESULT, hubToolResponse.type)
        assertEquals("4.0", hubToolResponse.content)
        
        // 3. 자동 포맷팅 test (텍스트 message)
        val textMessage = Message(
            id = "text-test",
            type = MessageType.TEXT,
            content = "Hello from user",
            sender = "user"
        )
        
        val textResponse = toolHubAgent.processMessage(textMessage)
        assertEquals(MessageType.TOOL_RESULT, textResponse.type)
        assertEquals("Agent: [Hello from user]", textResponse.content)
        
        // 도구 개수 check (Agent 도구 + ToolHub 도구)
        val allTools = toolHubAgent.getTools()
        assertEquals(4, allTools.size) // 1개 Agent 도구 + 3개 ToolHub 도구
        
        val toolNames = allTools.map { it.name }.toSet()
        assertTrue(toolNames.contains("agent_formatter"))
        assertTrue(toolNames.contains("enhanced_calculator"))
        assertTrue(toolNames.contains("enhanced_text_processor"))
        assertTrue(toolNames.contains("enhanced_data_validator"))
        
        toolHub.stop()
    }
    
    @Test
    fun `ToolChain과 개선된 도구들 통합 테스트`() = runBlocking {
        toolHub.start()
        
        // 복합 data processing 체인 execution
        val result = executeToolChain(
            toolHub = toolHub,
            chainName = "enhanced_data_processing",
            initialParameters = mapOf("raw_input" to "  Hello World 123  ")
        ) {
            // 1단계: data validation
            step(
                name = "validate_input",
                toolName = "enhanced_data_validator",
                parameterMapping = mapOf("raw_input" to "data"),
                parameters = mapOf(
                    "rules" to listOf("not_null", "not_empty", "min_length_3"),
                    "fail_fast" to false
                )
            )
            
            // 2단계: 텍스트 processing
            step(
                name = "process_text",
                toolName = "enhanced_text_processor",
                parameterMapping = mapOf("raw_input" to "text"),
                parameters = mapOf(
                    "operations" to listOf("trim", "lowercase", "length"),
                    "delimiter" to " | "
                )
            )
            
            // 3단계: 길이 계산 (숫자 추출하여 계산)
            step(
                name = "calculate_stats",
                toolName = "enhanced_calculator",
                parameters = mapOf(
                    "operation" to "multiply",
                    "a" to 15, // "Hello World 123".trim().length
                    "b" to 2,
                    "precision" to 0
                ),
                condition = { context ->
                    // 이전 단계가 성공한 경우에만 execution
                    context.executionHistory.isNotEmpty() && 
                    context.executionHistory.all { it.result.success }
                }
            )
        }
        
        assertTrue(result.success)
        assertEquals(3, result.totalSteps)
        assertEquals(3, result.successfulSteps)
        assertEquals(100.0, result.successRate)
        
        // 각 단계 결과 check
        val logs = result.executionLogs
        assertEquals("validate_input", logs[0].stepName)
        assertEquals("process_text", logs[1].stepName)
        assertEquals("calculate_stats", logs[2].stepName)
        
        // 최종 계산 결과 check
        val finalResult = logs[2].result
        assertTrue(finalResult.success)
        assertEquals("30", (finalResult as ToolResult.Success).output)
        
        println("체인 실행 요약:")
        println(result.getSummary())
        println("\n상세 로그:")
        println(result.getDetailedLog())
        
        toolHub.stop()
    }
    
    @Test
    fun `canExecute 검증 실패 테스트`() = runBlocking {
        toolHub.start()
        val context = ToolContext()
        
        // 잘못된 연산으로 계산기 호출
        val invalidCalcResult = toolHub.callTool(
            name = "enhanced_calculator",
            parameters = mapOf(
                "operation" to "invalid_operation",
                "a" to 1,
                "b" to 2
            ),
            context = context
        )
        
        assertFalse(invalidCalcResult.success)
        assertTrue((invalidCalcResult as ToolResult.Error).message.contains("not found"))
        
        // 빈 텍스트로 텍스트 processing기 호출
        val invalidTextResult = toolHub.callTool(
            name = "enhanced_text_processor",
            parameters = mapOf(
                "text" to "",
                "operations" to listOf("uppercase")
            ),
            context = context
        )
        
        assertFalse(invalidTextResult.success)
        assertTrue((invalidTextResult as ToolResult.Error).message.contains("cannot execute"))
        
        // execution statisticsin 실패 check
        val stats = (toolHub as StaticToolHub).getExecutionStats(context)
        assertEquals(2, stats["total_executions"])
        assertEquals(0.0, stats["success_rate"])
        
        toolHub.stop()
    }
} 