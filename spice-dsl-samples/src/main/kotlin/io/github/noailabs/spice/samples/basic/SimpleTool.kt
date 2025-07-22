package io.github.noailabs.spice.samples.basic

import io.github.noailabs.spice.*
import io.github.noailabs.spice.dsl.*
import kotlinx.coroutines.runBlocking

/**
 * 기본적인 Tool 생성 예제
 * 
 * 이 예제는 Built-in Tools와 Agent DSL을 사용하는 방법을 보여줍니다.
 */
fun main() = runBlocking {
    println("=== Basic Tool Example ===\n")
    
    // 0. Built-in tools를 레지스트리에 등록
    println("0. Registering Built-in Tools")
    println("-" * 30)
    ToolRegistry.register(ToolWrapper("calculator", calculatorTool()))
    ToolRegistry.register(ToolWrapper("textProcessor", textProcessorTool()))
    ToolRegistry.register(ToolWrapper("datetime", dateTimeTool()))
    ToolRegistry.register(ToolWrapper("random", randomTool()))
    println("Registered ${ToolRegistry.size()} built-in tools\n")
    
    // 1. Built-in Tools 사용하기
    println("1. Built-in Tools Testing")
    println("-" * 30)
    
    // Calculator Tool
    val calc = calculatorTool()
    println("Calculator: 10 + 5 = ${calc.execute(mapOf("expression" to "10 + 5")).result}")
    println("Calculator: 7 * 8 = ${calc.execute(mapOf("expression" to "7 * 8")).result}")
    
    // Text Processor Tool
    val textProc = textProcessorTool()
    println("\nText Processor:")
    println("- Uppercase: ${textProc.execute(mapOf("text" to "hello world", "operation" to "uppercase")).result}")
    println("- Length: ${textProc.execute(mapOf("text" to "Spice Framework", "operation" to "length")).result}")
    
    // DateTime Tool
    val dateTime = dateTimeTool()
    println("\nDateTime:")
    println("- Current time: ${dateTime.execute(mapOf("operation" to "now")).result}")
    
    // Random Tool
    val random = randomTool()
    println("\nRandom:")
    println("- Boolean: ${random.execute(mapOf("type" to "boolean")).result}")
    println("- Number (1-10): ${random.execute(mapOf("type" to "number", "min" to 1, "max" to 10)).result}")
    
    // 2. Agent with Built-in Tools
    println("\n\n2. Agent with Tools")
    println("-" * 30)
    
    val toolAgent = buildAgent {
        id = "tool-user"
        name = "Tool User Agent"
        description = "Agent that uses built-in tools"
        
        // Built-in tools를 global tools로 참조
        globalTools("calculator", "textProcessor")
        
        handle { comm ->
            when {
                comm.content.contains("calculate", ignoreCase = true) -> {
                    // Extract expression from message
                    val expression = comm.content.substringAfter("calculate").trim()
                    val calcTool = calculatorTool()
                    val result = calcTool.execute(mapOf("expression" to expression))
                    
                    comm.reply(
                        content = if (result.success) {
                            "Calculation result: ${result.result}"
                        } else {
                            "Calculation error: ${result.error}"
                        },
                        from = id
                    )
                }
                
                comm.content.contains("uppercase", ignoreCase = true) -> {
                    val text = comm.content.replace("uppercase", "", ignoreCase = true).trim()
                    val textTool = textProcessorTool()
                    val result = textTool.execute(mapOf("text" to text, "operation" to "uppercase"))
                    
                    comm.reply(
                        content = "Uppercase: ${result.result}",
                        from = id
                    )
                }
                
                else -> {
                    comm.reply(
                        content = "I can help you calculate expressions or convert text to uppercase. " +
                                "Try 'calculate 5 + 3' or 'uppercase hello world'",
                        from = id
                    )
                }
            }
        }
    }
    
    // Test the agent
    println("Testing Tool User Agent:")
    
    val testMessages = listOf(
        "calculate 15 + 25",
        "uppercase kotlin is awesome",
        "what can you do?"
    )
    
    testMessages.forEach { message ->
        val response = toolAgent.processComm(Comm(content = message, from = "user"))
        println("User: $message")
        println("Agent: ${response.content}\n")
    }
    
    // 3. SimpleTool 직접 생성
    println("\n3. Creating Simple Custom Tool")
    println("-" * 30)
    
    val greetingTool = SimpleTool(
        name = "greeting",
        description = "Generates personalized greetings",
        parameterSchemas = mapOf(
            "name" to ParameterSchema("string", "Name to greet", true),
            "style" to ParameterSchema("string", "Greeting style (formal/casual)", false)
        )
    ) { params ->
        val name = params["name"] as? String ?: "Friend"
        val style = params["style"] as? String ?: "casual"
        
        val greeting = when (style) {
            "formal" -> "Good day, $name. How may I assist you?"
            "casual" -> "Hey $name! What's up?"
            else -> "Hello, $name!"
        }
        
        ToolResult.success(greeting)
    }
    
    // Test custom tool
    println("Custom Greeting Tool:")
    println("- Casual: ${greetingTool.execute(mapOf("name" to "Alice", "style" to "casual")).result}")
    println("- Formal: ${greetingTool.execute(mapOf("name" to "Dr. Smith", "style" to "formal")).result}")
}

// Extension function for string multiplication
operator fun String.times(n: Int) = repeat(n)