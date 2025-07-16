package io.github.spice.samples.basic

import io.github.spice.ToolResult
import io.github.spice.dsl.tool
import io.github.spice.dsl.ToolRegistry
import kotlinx.coroutines.runBlocking

/**
 * 기본적인 Tool 생성 예제
 * 
 * 이 예제는 Core DSL의 tool() 함수를 사용하여
 * 간단한 도구들을 만들고 등록하는 방법을 보여줍니다.
 */
fun main() = runBlocking {
    println("=== Basic Tool Example ===")
    
    // 1. 가장 간단한 도구 생성
    val greetingTool = tool("greeting") {
        description = "Generates personalized greetings"
        param("name", "string")
        
        execute { params ->
            val name = params["name"] as? String ?: "Anonymous"
            ToolResult.success("Hello, $name! Welcome to Spice Framework!")
        }
    }
    
    // 2. 도구 등록
    ToolRegistry.register(greetingTool)
    
    // 3. 도구 사용
    val result1 = greetingTool.execute(mapOf("name" to "Alice"))
    println("Greeting Tool Result: ${result1.result}")
    
    // 4. 계산 도구 예제
    val calculatorTool = tool("calculator") {
        description = "Performs basic arithmetic operations"
        param("operation", "string")
        param("a", "number")
        param("b", "number")
        
        execute { params ->
            try {
                val operation = params["operation"] as String
                val a = (params["a"] as Number).toDouble()
                val b = (params["b"] as Number).toDouble()
                
                val result = when (operation.lowercase()) {
                    "add" -> a + b
                    "subtract" -> a - b
                    "multiply" -> a * b
                    "divide" -> {
                        if (b != 0.0) a / b 
                        else return@execute ToolResult.error("Division by zero")
                    }
                    else -> return@execute ToolResult.error("Unknown operation: $operation")
                }
                
                ToolResult.success(
                    result = "Result of $a $operation $b = $result",
                    metadata = mapOf("operation" to operation, "result" to result.toString())
                )
            } catch (e: Exception) {
                ToolResult.error("Calculation error: ${e.message}")
            }
        }
    }
    
    // 5. 계산 도구 등록 및 사용
    ToolRegistry.register(calculatorTool)
    
    val calculations = listOf(
        mapOf("operation" to "add", "a" to 10, "b" to 5),
        mapOf("operation" to "multiply", "a" to 7, "b" to 8),
        mapOf("operation" to "divide", "a" to 15, "b" to 3)
    )
    
    println("\n=== Calculator Tool Testing ===")
    calculations.forEach { params ->
        val result = calculatorTool.execute(params)
        if (result.success) {
            println(result.result)
        } else {
            println("Error: ${result.error}")
        }
    }
    
    // 6. 텍스트 처리 도구
    val textProcessorTool = tool("text-processor") {
        description = "Processes text with various transformations"
        param("text", "string")
        param("operation", "string")
        
        execute { params ->
            val text = params["text"] as String
            val operation = params["operation"] as String
            
            val result = when (operation.lowercase()) {
                "uppercase" -> text.uppercase()
                "lowercase" -> text.lowercase()
                "reverse" -> text.reversed()
                "word-count" -> "Word count: ${text.split("\\s+".toRegex()).size}"
                else -> return@execute ToolResult.error("Unknown operation: $operation")
            }
            
            ToolResult.success(
                result = result,
                metadata = mapOf("operation" to operation, "original_text" to text)
            )
        }
    }
    
    // 7. 텍스트 처리 도구 테스트
    ToolRegistry.register(textProcessorTool)
    
    println("\n=== Text Processor Tool Testing ===")
    val textOperations = listOf(
        mapOf("text" to "Hello Spice Framework", "operation" to "uppercase"),
        mapOf("text" to "KOTLIN IS AWESOME", "operation" to "lowercase"),
        mapOf("text" to "DSL Design", "operation" to "reverse"),
        mapOf("text" to "This is a sample text for word counting", "operation" to "word-count")
    )
    
    textOperations.forEach { params ->
        val result = textProcessorTool.execute(params)
        if (result.success) {
            println("${params["operation"]}: ${params["text"]} -> ${result.result}")
        } else {
            println("Error: ${result.error}")
        }
    }
    
    // 8. 등록된 도구 목록 확인
    println("\n=== Registered Tools ===")
    ToolRegistry.getAllTools().forEach { tool ->
        println("- ${tool.name}: ${tool.description}")
    }
} 