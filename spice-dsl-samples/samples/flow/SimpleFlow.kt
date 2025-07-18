package io.github.spice.samples.flow

import io.github.spice.comm.Comm
import io.github.spice.comm.CommType
import io.github.spice.ToolResult
import io.github.spice.dsl.*
import kotlinx.coroutines.runBlocking

/**
 * 기본적인 Flow 생성 예제
 * 
 * 이 예제는 Core DSL의 flow {} 함수를 사용하여
 * Agent들을 연결하는 워크플로우를 만드는 방법을 보여줍니다.
 */
fun main() = runBlocking {
    println("=== Basic Flow Example ===")
    
    // 1. 도구들 먼저 생성 및 등록
    val greetingTool = tool("greeting-tool") {
        description = "Generates greetings"
        param("name", "string")
        
        execute { params ->
            val name = params["name"] as? String ?: "User"
            ToolResult.success("Hello, $name!")
        }
    }
    ToolRegistry.register(greetingTool)
    
    val analysisTool = tool("analysis-tool") {
        description = "Analyzes text sentiment"
        param("text", "string")
        
        execute { params ->
            val text = params["text"] as String
            val sentiment = when {
                text.contains("hello", ignoreCase = true) -> "positive"
                text.contains("goodbye", ignoreCase = true) -> "neutral"
                else -> "unknown"
            }
            ToolResult.success("Sentiment: $sentiment for text: '$text'")
        }
    }
    ToolRegistry.register(analysisTool)
    
    // 2. Agent들 생성 및 등록
    val greetingAgent = buildAgent {
        id = "greeting-agent"
        name = "Greeting Agent"
        description = "Generates personalized greetings"
        tools("greeting-tool")
        
        handle { comm ->
            val greetingResult = greetingTool.execute(mapOf("name" to comm.content))
            Comm(
                content = greetingResult.result,
                from = id,
                to = comm.sender,
                metadata = mapOf("agent_type" to "greeting")
            )
        }
    }
    AgentRegistry.register(greetingAgent)
    
    val analysisAgent = buildAgent {
        id = "analysis-agent"
        name = "Analysis Agent" 
        description = "Analyzes message sentiment"
        tools("analysis-tool")
        
        handle { comm ->
            val analysisResult = analysisTool.execute(mapOf("text" to comm.content))
            Comm(
                content = "${comm.content} | ${analysisResult.result}",
                from = id,
                to = comm.sender,
                metadata = mapOf("agent_type" to "analysis")
            )
        }
    }
    AgentRegistry.register(analysisAgent)
    
    // 3. 단순한 순차 Flow 생성
    val sequentialFlow = flow {
        id = "greeting-analysis-flow"
        name = "Greeting and Analysis Flow"
        description = "First greets, then analyzes the greeting"
        
        step("greeting-agent")
        step("analysis-agent")
    }
    
    // 4. Flow 실행
    val userMessage = Comm(
        content = "Alice",
        from = "user",
        to = "greeting-analysis-flow"
    )
    
    println("\n=== Sequential Flow Execution ===")
    println("Input: ${userMessage.content}")
    
    val sequentialResult = sequentialFlow.execute(userMessage)
    println("Sequential Flow Result: ${sequentialResult.content}")
    
    // 5. 조건부 Flow 생성
    val conditionalFlow = flow {
        id = "conditional-flow"
        name = "Conditional Processing Flow"
        description = "Chooses different processing based on input"
        
        // Alice면 greeting agent만, 다른 이름이면 analysis agent로
        step("step1", "greeting-agent") { message ->
            comm.content.equals("Alice", ignoreCase = true)
        }
        step("step2", "analysis-agent") { message ->
            !comm.content.equals("Alice", ignoreCase = true)
        }
    }
    
    // 6. 조건부 Flow 테스트
    println("\n=== Conditional Flow Testing ===")
    
    val testNames = listOf("Alice", "Bob", "Charlie")
    testNames.forEach { name ->
        val testMessage = Comm(
            content = name,
            from = "user",
            to = "conditional-flow"
        )
        
        val result = conditionalFlow.execute(testMessage)
        println("Input: $name -> ${result.content}")
    }
    
    // 7. 등록된 Agent와 Tool 정보 출력
    println("\n=== Registered Components ===")
    println("Agents:")
    AgentRegistry.getAllAgents().forEach { agent ->
        println("  - ${agent.id}: ${agent.name}")
    }
    println("Tools:")
    ToolRegistry.getAllTools().forEach { tool ->
        println("  - ${tool.name}: ${tool.description}")
    }
} 