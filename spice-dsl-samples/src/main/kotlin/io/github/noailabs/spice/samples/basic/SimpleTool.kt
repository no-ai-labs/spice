package io.github.noailabs.spice.samples.basic

import io.github.noailabs.spice.*
import io.github.noailabs.spice.dsl.*
import io.github.noailabs.spice.error.SpiceResult
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
    ToolRegistry.register(
        calculatorTool(),
        tags = setOf("builtin", "math"),
        metadata = mapOf("category" to "calculator")
    )
    ToolRegistry.register(
        textProcessorTool(),
        tags = setOf("builtin", "text"),
        metadata = mapOf("category" to "nlp")
    )
    ToolRegistry.register(
        dateTimeTool(),
        tags = setOf("builtin", "time"),
        metadata = mapOf("category" to "datetime")
    )
    ToolRegistry.register(
        randomTool(),
        tags = setOf("builtin", "utility"),
        metadata = mapOf("category" to "random")
    )
    println("Registered ${ToolRegistry.size()} built-in tools\n")
    
    val defaultContext = ToolContext(agentId = "sample-agent")

    // 1. Built-in Tools 사용하기
    println("1. Built-in Tools Testing")
    println("-" * 30)
    
    // Calculator Tool
    val calc = calculatorTool()
    println("Calculator: 10 + 5 = ${calc.execute(mapOf("expression" to "10 + 5"), defaultContext).getOrNull()?.result}")
    println("Calculator: 7 * 8 = ${calc.execute(mapOf("expression" to "7 * 8"), defaultContext).getOrNull()?.result}")
    
    // Text Processor Tool
    val textProc = textProcessorTool()
    println("\nText Processor:")
    println("- Uppercase: ${textProc.execute(mapOf("text" to "hello world", "operation" to "uppercase"), defaultContext).getOrNull()?.result}")
    println("- Length: ${textProc.execute(mapOf("text" to "Spice Framework", "operation" to "length"), defaultContext).getOrNull()?.result}")
    
    // DateTime Tool
    val dateTime = dateTimeTool()
    println("\nDateTime:")
    println("- Current time: ${dateTime.execute(mapOf("operation" to "now"), defaultContext).getOrNull()?.result}")
    
    // Random Tool
    val random = randomTool()
    println("\nRandom:")
    println("- Boolean: ${random.execute(mapOf("type" to "boolean"), defaultContext).getOrNull()?.result}")
    println("- Number (1-10): ${random.execute(mapOf("type" to "number", "min" to 1, "max" to 10), defaultContext).getOrNull()?.result}")
    
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
                    val result = calcTool.execute(mapOf("expression" to expression), defaultContext)
                    
                    comm.reply(
                        content = when (result) {
                            is SpiceResult.Success -> "Calculation result: ${result.value.result}"
                            is SpiceResult.Failure -> "Calculation error: ${result.error.message}"
                        },
                        from = id
                    )
                }
                
                comm.content.contains("uppercase", ignoreCase = true) -> {
                    val text = comm.content.replace("uppercase", "", ignoreCase = true).trim()
                    val textTool = textProcessorTool()
                    val result = textTool.execute(mapOf("text" to text, "operation" to "uppercase"), defaultContext)
                    
                    comm.reply(
                        content = when (result) {
                            is SpiceResult.Success -> "Uppercase: ${result.value.result}"
                            is SpiceResult.Failure -> "Text processing error: ${result.error.message}"
                        },
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
    println("- Casual: ${greetingTool.execute(mapOf("name" to "Alice", "style" to "casual"), defaultContext).getOrNull()?.result}")
    println("- Formal: ${greetingTool.execute(mapOf("name" to "Dr. Smith", "style" to "formal"), defaultContext).getOrNull()?.result}")
}

// Extension function for string multiplication
operator fun String.times(n: Int) = repeat(n)

fun calculatorTool(): Tool = SimpleTool(
    name = "calculator",
    description = "Evaluates simple arithmetic expressions",
    parameterSchemas = mapOf(
        "expression" to ParameterSchema("string", "Expression to evaluate", true)
    )
) { params ->
    val expression = params["expression"] as? String ?: return@SimpleTool ToolResult.error("Missing expression")
    val sanitized = expression.replace(Regex("[^0-9+\\-*/(). ]"), "")
    val result = runCatching { evaluateExpression(sanitized) }
        .getOrElse { return@SimpleTool ToolResult.error("Invalid expression: ${it.message}") }
    ToolResult.success(result)
}

fun textProcessorTool(): Tool = SimpleTool(
    name = "textProcessor",
    description = "Performs simple text operations",
    parameterSchemas = mapOf(
        "text" to ParameterSchema("string", "Input text", true),
        "operation" to ParameterSchema("string", "Operation (uppercase|lowercase|length)", true)
    )
) { params ->
    val text = params["text"] as? String ?: return@SimpleTool ToolResult.error("Missing text")
    val operation = params["operation"] as? String ?: "uppercase"
    val output = when (operation.lowercase()) {
        "uppercase" -> text.uppercase()
        "lowercase" -> text.lowercase()
        "length" -> text.length.toString()
        else -> return@SimpleTool ToolResult.error("Unsupported operation: $operation")
    }
    ToolResult.success(output)
}

fun dateTimeTool(): Tool = SimpleTool(
    name = "dateTime",
    description = "Returns current date/time info",
    parameterSchemas = mapOf(
        "operation" to ParameterSchema("string", "Operation (now|date|time)", false)
    )
) { params ->
    val now = java.time.OffsetDateTime.now()
    val op = (params["operation"] as? String)?.lowercase() ?: "now"
    val result = when (op) {
        "now" -> now.toString()
        "date" -> now.toLocalDate().toString()
        "time" -> now.toLocalTime().toString()
        else -> return@SimpleTool ToolResult.error("Unsupported operation: $op")
    }
    ToolResult.success(result)
}

fun randomTool(): Tool = SimpleTool(
    name = "random",
    description = "Generates random values",
    parameterSchemas = mapOf(
        "type" to ParameterSchema("string", "Value type (boolean|number)", true),
        "min" to ParameterSchema("number", "Min value (for number)", false),
        "max" to ParameterSchema("number", "Max value (for number)", false)
    )
) { params ->
    val type = (params["type"] as? String)?.lowercase() ?: "boolean"
    val random = kotlin.random.Random(System.currentTimeMillis())
    val result = when (type) {
        "boolean" -> random.nextBoolean().toString()
        "number" -> {
            val min = (params["min"] as? Number)?.toInt() ?: 0
            val max = (params["max"] as? Number)?.toInt() ?: 100
            random.nextInt(min, max.coerceAtLeast(min + 1)).toString()
        }
        else -> return@SimpleTool ToolResult.error("Unsupported type: $type")
    }
    ToolResult.success(result)
}

private fun evaluateExpression(expression: String): Double {
    val tokens = expression.replace("\\s+".toRegex(), "")
    if (tokens.isBlank()) throw IllegalArgumentException("Expression is blank")

    val values = ArrayDeque<Double>()
    val operators = ArrayDeque<Char>()
    var index = 0

    fun precedence(op: Char): Int = when (op) {
        '+', '-' -> 1
        '*', '/' -> 2
        else -> 0
    }

    fun applyOperator() {
        val op = operators.removeLast()
        val right = values.removeLast()
        val left = values.removeLast()
        val result = when (op) {
            '+' -> left + right
            '-' -> left - right
            '*' -> left * right
            '/' -> {
                if (right == 0.0) throw IllegalArgumentException("Division by zero")
                left / right
            }
            else -> throw IllegalArgumentException("Unsupported operator: $op")
        }
        values.addLast(result)
    }

    while (index < tokens.length) {
        when (val ch = tokens[index]) {
            in '0'..'9', '.' -> {
                val start = index
                while (index < tokens.length && (tokens[index].isDigit() || tokens[index] == '.')) {
                    index++
                }
                val number = tokens.substring(start, index).toDoubleOrNull()
                    ?: throw IllegalArgumentException("Invalid number in expression")
                values.addLast(number)
                continue
            }
            '(' -> operators.addLast(ch)
            ')' -> {
                while (operators.isNotEmpty() && operators.last() != '(') {
                    applyOperator()
                }
                if (operators.isEmpty() || operators.removeLast() != '(') {
                    throw IllegalArgumentException("Mismatched parentheses")
                }
            }
            '+', '-', '*', '/' -> {
                while (
                    operators.isNotEmpty() &&
                    precedence(operators.last()) >= precedence(ch)
                    ) {
                    applyOperator()
                }
                operators.addLast(ch)
            }
            else -> throw IllegalArgumentException("Invalid character '$ch' in expression")
        }
        index++
    }

    while (operators.isNotEmpty()) {
        if (operators.last() == '(' || operators.last() == ')') {
            throw IllegalArgumentException("Mismatched parentheses")
        }
        applyOperator()
    }

    return values.singleOrNull()
        ?: throw IllegalArgumentException("Unable to evaluate expression")
}
