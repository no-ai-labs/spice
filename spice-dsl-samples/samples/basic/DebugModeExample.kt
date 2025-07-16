package io.github.spice.samples.basic

import io.github.spice.Message
import io.github.spice.ToolResult
import io.github.spice.dsl.*
import kotlinx.coroutines.runBlocking

/**
 * Debug Mode 사용 예제
 * 
 * 이 예제는 AgentBuilder의 debugMode() 기능을 사용하여
 * 자동 로깅과 디버깅 정보를 출력하는 방법을 보여줍니다.
 */
fun main() = runBlocking {
    println("🐛 Debug Mode Example")
    println("====================")
    
    // 1. 일반 모드 에이전트 (로깅 없음)
    println("\n=== 1. Normal Mode Agent ===")
    val normalAgent = buildAgent {
        id = "normal-agent"
        name = "Normal Agent"
        description = "Regular agent without debug logging"
        
        handle { message ->
            Message(
                content = "Normal response: ${message.content}",
                sender = id,
                receiver = message.sender
            )
        }
    }
    AgentRegistry.register(normalAgent)
    
    val message1 = Message(content = "Hello normal agent", sender = "user")
    val response1 = normalAgent.processMessage(message1)
    println("Response: ${response1.content}")
    
    // 2. 디버그 모드 에이전트 (자동 로깅 포함)
    println("\n=== 2. Debug Mode Agent ===")
    val debugAgent = buildAgent {
        id = "debug-agent"
        name = "Debug Agent"
        description = "Agent with automatic debug logging"
        
        // 디버그 모드 활성화
        debugMode(enabled = true, prefix = "[🔍 DEBUG]")
        
        handle { message ->
            // 일부러 처리 시간을 늘려서 디버그 정보 확인
            kotlinx.coroutines.delay(100)
            
            Message(
                content = "Debug response: ${message.content.uppercase()}",
                sender = id,
                receiver = message.sender,
                metadata = mapOf("debug_processed" to "true")
            )
        }
    }
    AgentRegistry.register(debugAgent)
    
    val message2 = Message(
        content = "Hello debug agent",
        sender = "user",
        metadata = mapOf("test_mode" to "active")
    )
    val response2 = debugAgent.processMessage(message2)
    
    // 3. 도구와 함께 사용하는 디버그 모드
    println("\n=== 3. Debug Mode with Tools ===")
    
    val debugTool = tool("debug-calculator") {
        description = "Calculator with debug output"
        param("a", "number")
        param("b", "number")
        param("operation", "string")
        
        execute { params ->
            val a = (params["a"] as Number).toDouble()
            val b = (params["b"] as Number).toDouble()
            val op = params["operation"] as String
            
            val result = when (op) {
                "add" -> a + b
                "multiply" -> a * b
                else -> 0.0
            }
            
            ToolResult.success(
                result = "$a $op $b = $result",
                metadata = mapOf("calculation_time" to System.currentTimeMillis().toString())
            )
        }
    }
    ToolRegistry.register(debugTool)
    
    val toolDebugAgent = buildAgent {
        id = "tool-debug-agent"
        name = "Tool Debug Agent"
        description = "Agent with tools and debug mode"
        
        debugMode(enabled = true, prefix = "[🛠️ TOOL-DEBUG]")
        tools("debug-calculator")
        
        handle { message ->
            // 메시지에서 계산 추출: "5 + 3"
            val parts = message.content.split(" ")
            if (parts.size == 3) {
                val a = parts[0].toDoubleOrNull() ?: 0.0
                val operation = when (parts[1]) {
                    "+" -> "add"
                    "*" -> "multiply"
                    else -> "add"
                }
                val b = parts[2].toDoubleOrNull() ?: 0.0
                
                val toolResult = debugTool.execute(mapOf(
                    "a" to a,
                    "b" to b,
                    "operation" to operation
                ))
                
                Message(
                    content = if (toolResult.success) toolResult.result else "Calculation error",
                    sender = id,
                    receiver = message.sender,
                    metadata = mapOf("tool_used" to "debug-calculator")
                )
            } else {
                Message(
                    content = "Please use format: '5 + 3' or '10 * 2'",
                    sender = id,
                    receiver = message.sender
                )
            }
        }
    }
    AgentRegistry.register(toolDebugAgent)
    
    val mathMessage = Message(content = "15 * 4", sender = "user")
    val mathResponse = toolDebugAgent.processMessage(mathMessage)
    
    // 4. 커스텀 디버그 접두사 사용
    println("\n=== 4. Custom Debug Prefix ===")
    val customDebugAgent = buildAgent {
        id = "custom-debug-agent"
        name = "Custom Debug Agent"
        description = "Agent with custom debug prefix"
        
        debugMode(enabled = true, prefix = "[⚡ CUSTOM]")
        
        handle { message ->
            Message(
                content = "Custom debug: ${message.content}",
                sender = id,
                receiver = message.sender
            )
        }
    }
    AgentRegistry.register(customDebugAgent)
    
    val customMessage = Message(content = "Test custom prefix", sender = "user")
    val customResponse = customDebugAgent.processMessage(customMessage)
    
    // 5. 디버그 정보 확인
    println("\n=== 5. Debug Information ===")
    if (debugAgent is io.github.spice.dsl.CoreAgent) {
        println("Debug enabled: ${debugAgent.isDebugEnabled()}")
        println("Debug info: ${debugAgent.getDebugInfo()}")
    }
    
    // 6. 디버그 모드 비교
    println("\n=== 6. Performance Comparison ===")
    println("Testing response times...")
    
    val testMessage = Message(content = "Performance test", sender = "user")
    
    // Normal mode timing
    val normalStart = System.currentTimeMillis()
    repeat(5) {
        normalAgent.processMessage(testMessage)
    }
    val normalEnd = System.currentTimeMillis()
    
    // Debug mode timing (로그는 이미 출력됨)
    val debugStart = System.currentTimeMillis()
    repeat(5) {
        debugAgent.processMessage(testMessage)
    }
    val debugEnd = System.currentTimeMillis()
    
    println("\nPerformance Results:")
    println("Normal mode (5 messages): ${normalEnd - normalStart}ms")
    println("Debug mode (5 messages): ${debugEnd - debugStart}ms")
    println("Debug overhead: ${(debugEnd - debugStart) - (normalEnd - normalStart)}ms")
    
    // 7. 프로덕션 팁
    println("\n=== 7. Production Tips ===")
    println("""
    💡 Debug Mode Best Practices:
    
    1. Development: debugMode(true) for detailed logging
    2. Testing: debugMode(true, "[TEST]") with custom prefix
    3. Production: debugMode(false) or omit for performance
    4. Conditional: Use environment variables
       debugMode(System.getenv("DEBUG") == "true")
    
    5. Performance: Debug mode adds ~5-10ms overhead per message
    6. Logging: Debug output goes to stdout (redirect as needed)
    """.trimIndent())
} 