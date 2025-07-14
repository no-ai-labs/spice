package io.github.spice

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * improvement된 AgentBuilder와 ToolBuilder feature test
 */
class AgentBuilderEnhancedTest {
    
    @Test
    fun `ToolBuilder parameter() 함수 테스트`() = runBlocking {
        val agent = agent {
            id = "enhanced-tool-agent"
            name = "Enhanced Tool Agent"
            description = "Agent with enhanced tool builder"
            
            tool("advanced_calculator") {
                description = "Advanced calculator with parameter validation"
                
                // 새로운 parameter() function 사용
                parameter("operation", "string", "Mathematical operation", required = true)
                parameter("a", "number", "First operand", required = true)
                parameter("b", "number", "Second operand", required = true)
                parameter("precision", "number", "Decimal precision", required = false)
                
                // canExecute validation function
                canExecute { params ->
                    val operation = params["operation"] as? String
                    val validOperations = setOf("add", "subtract", "multiply", "divide", "power")
                    operation in validOperations
                }
                
                execute { params ->
                    val operation = params["operation"] as? String ?: "add"
                    val a = (params["a"] as? Number)?.toDouble() ?: 0.0
                    val b = (params["b"] as? Number)?.toDouble() ?: 0.0
                    val precision = (params["precision"] as? Number)?.toInt() ?: 2
                    
                    val result = when (operation) {
                        "add" -> a + b
                        "subtract" -> a - b
                        "multiply" -> a * b
                        "divide" -> {
                            if (b == 0.0) {
                                return@execute ToolResult(success = false, error = "Division by zero")
                            }
                            a / b
                        }
                        "power" -> kotlin.math.pow(a, b)
                        else -> return@execute ToolResult(success = false, error = "Unknown operation")
                    }
                    
                    val formattedResult = "%.${precision}f".format(result)
                    ToolResult(
                        success = true,
                        result = formattedResult,
                        metadata = mapOf(
                            "operation" to operation,
                            "operands" to "$a, $b",
                            "precision" to precision.toString()
                        )
                    )
                }
            }
            
            messageHandler { message ->
                message.createReply(
                    content = "Enhanced agent processed: ${message.content}",
                    sender = id,
                    type = MessageType.TEXT
                )
            }
        }
        
        // Tool 스키마 check
        val tool = agent.getTools().first()
        assertEquals("advanced_calculator", tool.name)
        assertEquals("Advanced calculator with parameter validation", tool.description)
        
        val schema = tool.schema
        assertEquals(4, schema.parameters.size)
        
        // 파라미터 스키마 check
        val operationParam = schema.parameters["operation"]!!
        assertEquals("string", operationParam.type)
        assertEquals("Mathematical operation", operationParam.description)
        assertTrue(operationParam.required)
        
        val precisionParam = schema.parameters["precision"]!!
        assertEquals("number", precisionParam.type)
        assertEquals("Decimal precision", precisionParam.description)
        assertFalse(precisionParam.required)
        
        // canExecute test
        assertTrue(tool.canExecute(mapOf("operation" to "add", "a" to 1, "b" to 2)))
        assertFalse(tool.canExecute(mapOf("operation" to "invalid", "a" to 1, "b" to 2)))
        
        // execution test
        val validResult = tool.execute(mapOf(
            "operation" to "multiply",
            "a" to 3.14159,
            "b" to 2,
            "precision" to 3
        ))
        assertTrue(validResult.success)
        assertEquals("6.283", validResult.result)
        
        // 잘못된 연산 test
        val invalidResult = tool.execute(mapOf(
            "operation" to "invalid",
            "a" to 1,
            "b" to 2
        ))
        assertFalse(invalidResult.success)
        assertEquals("Tool cannot execute with given parameters", invalidResult.error)
    }
    
    @Test
    fun `AgentBuilder 기본 ID 생성 테스트`() = runBlocking {
        val agent1 = agent {
            name = "Test Agent 1"
            description = "First test agent"
        }
        
        val agent2 = agent {
            name = "Test Agent 2"
            description = "Second test agent"
        }
        
        // ID가 자동 generation되었는지 check
        assertTrue(agent1.id.isNotBlank())
        assertTrue(agent2.id.isNotBlank())
        assertTrue(agent1.id.startsWith("agent-"))
        assertTrue(agent2.id.startsWith("agent-"))
        
        // 두 Agent의 ID가 다른지 check
        assertTrue(agent1.id != agent2.id)
        
        println("Agent 1 ID: ${agent1.id}")
        println("Agent 2 ID: ${agent2.id}")
    }
    
    @Test
    fun `messageProcessor deprecated 테스트`() = runBlocking {
        val agent = agent {
            id = "deprecated-test-agent"
            name = "Deprecated Test Agent"
            description = "Testing deprecated messageProcessor"
            
            // deprecated function 사용 (warning가 나와야 함)
            @Suppress("DEPRECATION")
            messageProcessor { message ->
                message.createReply(
                    content = "Processed by deprecated function: ${message.content}",
                    sender = id,
                    type = MessageType.TEXT
                )
            }
        }
        
        val testMessage = Message(
            id = "test-1",
            type = MessageType.TEXT,
            content = "Hello",
            sender = "user"
        )
        
        val response = agent.processMessage(testMessage)
        assertEquals("Processed by deprecated function: Hello", response.content)
        assertEquals("deprecated-test-agent", response.sender)
    }
    
    @Test
    fun `복합 도구 빌더 테스트`() = runBlocking {
        val agent = agent {
            id = "complex-tool-agent"
            name = "Complex Tool Agent"
            description = "Agent with multiple complex tools"
            
            // 첫 번째 도구: 문자열 processing기
            tool("string_processor") {
                description = "Advanced string processing tool"
                
                parameter("text", "string", "Input text to process", required = true)
                parameter("operation", "string", "Processing operation", required = true)
                parameter("case_sensitive", "boolean", "Case sensitive processing", required = false)
                parameter("max_length", "number", "Maximum output length", required = false)
                
                canExecute { params ->
                    val text = params["text"] as? String
                    val operation = params["operation"] as? String
                    val validOps = setOf("uppercase", "lowercase", "reverse", "length", "words")
                    
                    !text.isNullOrBlank() && operation in validOps
                }
                
                execute { params ->
                    val text = params["text"] as String
                    val operation = params["operation"] as String
                    val caseSensitive = params["case_sensitive"] as? Boolean ?: true
                    val maxLength = (params["max_length"] as? Number)?.toInt() ?: Int.MAX_VALUE
                    
                    val processedText = if (!caseSensitive) text.lowercase() else text
                    
                    val result = when (operation) {
                        "uppercase" -> processedText.uppercase()
                        "lowercase" -> processedText.lowercase()
                        "reverse" -> processedText.reversed()
                        "length" -> processedText.length.toString()
                        "words" -> processedText.split("\\s+".toRegex()).size.toString()
                        else -> processedText
                    }
                    
                    val finalResult = if (result.length > maxLength) {
                        result.take(maxLength) + "..."
                    } else {
                        result
                    }
                    
                    ToolResult(
                        success = true,
                        result = finalResult,
                        metadata = mapOf(
                            "original_text" to text,
                            "operation" to operation,
                            "case_sensitive" to caseSensitive.toString(),
                            "truncated" to (result.length > maxLength).toString()
                        )
                    )
                }
            }
            
            // 두 번째 도구: data validation기
            tool("data_validator") {
                description = "Data validation tool"
                
                parameter("data", "any", "Data to validate", required = true)
                parameter("rules", "array", "Validation rules", required = true)
                parameter("strict", "boolean", "Strict validation mode", required = false)
                
                canExecute { params ->
                    params.containsKey("data") && params.containsKey("rules")
                }
                
                execute { params ->
                    val data = params["data"]
                    val rules = params["rules"] as? List<*> ?: emptyList<String>()
                    val strict = params["strict"] as? Boolean ?: false
                    
                    val validationResults = mutableListOf<String>()
                    var isValid = true
                    
                    rules.forEach { rule ->
                        when (rule) {
                            "not_null" -> {
                                if (data == null) {
                                    validationResults.add("Data is null")
                                    isValid = false
                                }
                            }
                            "not_empty" -> {
                                if (data.toString().isEmpty()) {
                                    validationResults.add("Data is empty")
                                    isValid = false
                                }
                            }
                            "is_number" -> {
                                if (data !is Number && data.toString().toDoubleOrNull() == null) {
                                    validationResults.add("Data is not a number")
                                    if (strict) isValid = false
                                }
                            }
                            "is_string" -> {
                                if (data !is String) {
                                    validationResults.add("Data is not a string")
                                    if (strict) isValid = false
                                }
                            }
                        }
                    }
                    
                    if (validationResults.isEmpty()) {
                        validationResults.add("All validations passed")
                    }
                    
                    ToolResult(
                        success = isValid,
                        result = validationResults.joinToString("; "),
                        metadata = mapOf(
                            "rules_checked" to rules.size.toString(),
                            "strict_mode" to strict.toString(),
                            "validation_count" to validationResults.size.toString()
                        )
                    )
                }
            }
            
            messageHandler { message ->
                message.createReply(
                    content = "Complex tool agent processed: ${message.content}",
                    sender = id,
                    type = MessageType.TEXT
                )
            }
        }
        
        // 도구 개수 check
        assertEquals(2, agent.getTools().size)
        
        // 문자열 processing기 test
        val stringProcessor = agent.getTools().find { it.name == "string_processor" }!!
        
        val stringResult = stringProcessor.execute(mapOf(
            "text" to "Hello World",
            "operation" to "reverse",
            "max_length" to 8
        ))
        
        assertTrue(stringResult.success)
        assertEquals("dlroW ol...", stringResult.result)
        assertEquals("true", stringResult.metadata["truncated"])
        
        // data validation기 test
        val dataValidator = agent.getTools().find { it.name == "data_validator" }!!
        
        val validationResult = dataValidator.execute(mapOf(
            "data" to "123",
            "rules" to listOf("not_null", "not_empty", "is_number"),
            "strict" to true
        ))
        
        assertTrue(validationResult.success)
        assertTrue(validationResult.result.contains("All validations passed"))
        
        // validation 실패 test
        val failedValidation = dataValidator.execute(mapOf(
            "data" to "not_a_number",
            "rules" to listOf("is_number"),
            "strict" to true
        ))
        
        assertFalse(failedValidation.success)
        assertTrue(failedValidation.result.contains("Data is not a number"))
    }
    
    @Test
    fun `레거시 parameters() 함수 호환성 테스트`() = runBlocking {
        val agent = agent {
            id = "legacy-compat-agent"
            name = "Legacy Compatibility Agent"
            description = "Testing legacy parameters function"
            
            tool("legacy_tool") {
                description = "Tool using legacy parameters function"
                
                // 레거시 방식 사용
                parameters(mapOf(
                    "input" to "string",
                    "count" to "number",
                    "flag" to "boolean"
                ))
                
                execute { params ->
                    val input = params["input"] as? String ?: ""
                    val count = (params["count"] as? Number)?.toInt() ?: 1
                    val flag = params["flag"] as? Boolean ?: false
                    
                    val result = if (flag) {
                        input.repeat(count).uppercase()
                    } else {
                        input.repeat(count)
                    }
                    
                    ToolResult(success = true, result = result)
                }
            }
            
            messageHandler { message ->
                message.createReply(
                    content = "Legacy agent processed: ${message.content}",
                    sender = id,
                    type = MessageType.TEXT
                )
            }
        }
        
        val tool = agent.getTools().first()
        assertEquals("legacy_tool", tool.name)
        
        // 스키마 check (레거시 방식으로 generation된 파라미터들)
        val schema = tool.schema
        assertEquals(3, schema.parameters.size)
        assertTrue(schema.parameters.all { it.value.required }) // 레거시 방식은 모두 required=true
        assertTrue(schema.parameters.all { it.value.description.isEmpty() }) // 레거시 방식은 description 없음
        
        // execution test
        val result = tool.execute(mapOf(
            "input" to "Hi",
            "count" to 3,
            "flag" to true
        ))
        
        assertTrue(result.success)
        assertEquals("HIHIHI", result.result)
    }
} 