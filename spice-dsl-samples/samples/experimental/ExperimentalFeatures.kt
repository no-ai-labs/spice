package io.github.spice.samples.experimental

import io.github.spice.Message
import io.github.spice.ToolResult
import io.github.spice.dsl.*
import io.github.spice.dsl.experimental.*
import kotlinx.coroutines.runBlocking

/**
 * Experimental DSL 기능 예제
 * 
 * 이 예제는 experimental {} 래퍼를 사용하여
 * 고급 DSL 기능들을 사용하는 방법을 보여줍니다.
 */
fun main() = runBlocking {
    println("=== Experimental DSL Features ===")
    
    // 기본 도구와 에이전트 설정
    setupBasicComponents()
    
    // 1. Conditional Flow - 조건부 라우팅
    println("\n=== 1. Conditional Flow Example ===")
    
    experimental {
        val conditionalRouter = conditional {
            whenThen(
                condition = { message -> message.content.contains("weather") },
                action = { message ->
                    val weatherAgent = AgentRegistry.getAgent("weather-agent")!!
                    weatherAgent.processMessage(message)
                }
            )
            whenThen(
                condition = { message -> message.content.contains("help") },
                action = { message ->
                    val supportAgent = AgentRegistry.getAgent("support-agent")!!
                    supportAgent.processMessage(message)
                }
            )
            otherwise { message ->
                val generalAgent = AgentRegistry.getAgent("general-agent")!!
                generalAgent.processMessage(message)
            }
        }
        
        // 조건부 라우팅 테스트
        val testMessages = listOf(
            "What's the weather like today?",
            "I need help with my account",
            "Hello, how are you?"
        )
        
        testMessages.forEach { content ->
            val message = Message(content = content, sender = "user")
            val result = conditionalRouter.execute(message)
            println("'$content' -> ${result.content}")
        }
    }
    
    // 2. Simple Composition - 간단한 합성 (실제 reactive는 복잡하므로 mock)
    println("\n=== 2. Agent Composition Example ===")
    
    experimental {
        val composedWorkflow = composition {
            sequential(listOf("weather-agent", "analysis-agent"))
            merge { responses -> 
                "Sequential result: ${responses.last()}"
            }
        }
        
        val testMessage = Message(
            content = "sunny weather",
            sender = "user"
        )
        
        val composedResult = composedWorkflow.execute(testMessage)
        println("Composed workflow result: ${composedResult.content}")
    }
    
    // 3. Simple Workflow - 간단한 워크플로우 
    println("\n=== 3. Simple Workflow Example ===")
    
    experimental {
        val simpleWorkflow = workflow("weather-analysis") {
            step("weather-processing") { input ->
                "Weather processed: $input"
            }
            step("final-analysis") { processed ->
                "Final analysis: $processed"
            }
        }
        
        val workflowResult = simpleWorkflow.process("sunny day")
        println("Workflow result: $workflowResult")
    }
    
    // 4. Type-safe Messages - 타입 안전 메시징 (간단한 버전)
    println("\n=== 4. Type-safe Messaging Example ===")
    
    experimental {
        // 실제 구현이 복잡하므로 개념만 보여주는 mock
        println("Type-safe messaging concepts:")
        println("- TextContent: structured text data")
        println("- DataContent: key-value data structures")  
        println("- ErrorContent: error information with metadata")
        
        // Mock example
        val textData = mapOf("text" to "Hello Spice Framework")
        val processedData = mapOf(
            "word_count" to textData["text"]?.toString()?.split(" ")?.size,
            "char_count" to textData["text"]?.toString()?.length,
            "processed" to textData["text"]?.toString()?.uppercase()
        )
        
        println("Mock typed processing:")
        println("  Input: ${textData["text"]}")
        println("  Output: $processedData")
    }
    
    println("\n=== Experimental Features Demo Complete ===")
    println("Note: Some features are conceptual examples showing the direction of advanced DSL capabilities.")
}

/**
 * 기본 컴포넌트들 설정
 */
private suspend fun setupBasicComponents() {
    // 날씨 도구
    val weatherTool = tool("weather-tool") {
        description = "Provides weather information"
        param("query", "string")
        
        execute { params ->
            val query = params["query"] as String
            ToolResult.success("Weather for '$query': Sunny, 25°C")
        }
    }
    ToolRegistry.register(weatherTool)
    
    // 분석 도구
    val analysisTool = tool("analysis-tool") {
        description = "Analyzes text content"
        param("text", "string")
        
        execute { params ->
            val text = params["text"] as String
            ToolResult.success("Analysis: ${text.split(" ").size} words, positive sentiment")
        }
    }
    ToolRegistry.register(analysisTool)
    
    // 에이전트들 생성
    val weatherAgent = buildAgent {
        id = "weather-agent"
        name = "Weather Agent"
        description = "Handles weather-related queries"
        tools("weather-tool")
        
        handle { message ->
            val result = weatherTool.execute(mapOf("query" to message.content))
            Message(
                content = result.result,
                sender = id,
                receiver = message.sender
            )
        }
    }
    AgentRegistry.register(weatherAgent)
    
    val supportAgent = buildAgent {
        id = "support-agent"  
        name = "Support Agent"
        description = "Provides customer support"
        
        handle { message ->
            Message(
                content = "Support: How can I help you with '${message.content}'?",
                sender = id,
                receiver = message.sender
            )
        }
    }
    AgentRegistry.register(supportAgent)
    
    val generalAgent = buildAgent {
        id = "general-agent"
        name = "General Agent"
        description = "Handles general queries"
        
        handle { message ->
            Message(
                content = "General response to: ${message.content}",
                sender = id,
                receiver = message.sender
            )
        }
    }
    AgentRegistry.register(generalAgent)
    
    val analysisAgent = buildAgent {
        id = "analysis-agent"
        name = "Analysis Agent" 
        description = "Analyzes content"
        tools("analysis-tool")
        
        handle { message ->
            val result = analysisTool.execute(mapOf("text" to message.content))
            Message(
                content = result.result,
                sender = id,
                receiver = message.sender
            )
        }
    }
    AgentRegistry.register(analysisAgent)
} 