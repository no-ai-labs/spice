package io.github.noailabs.spice.samples.basic

import io.github.noailabs.spice.comm.Comm
import io.github.noailabs.spice.comm.CommType
import io.github.noailabs.spice.dsl.buildAgent
import io.github.noailabs.spice.dsl.AgentRegistry
import kotlinx.coroutines.runBlocking

/**
 * 가장 기본적인 Agent 생성 예제
 * 
 * 이 예제는 Core DSL의 buildAgent {} 함수를 사용하여
 * 간단한 인사 에이전트를 만드는 방법을 보여줍니다.
 * 
 * 리팩터링: 핸들러 함수들을 별도로 분리하여 재사용성과 가독성을 향상시켰습니다.
 */

// =====================================
// HANDLER FUNCTIONS
// =====================================

/**
 * 간단한 인사 응답 핸들러
 */
fun createGreetingHandler(agentId: String): suspend (Comm) -> Comm = { comm ->
    Comm(
        content = "Hello! You said: ${comm.content}",
        from = agentId,
        to = comm.sender,
        metadata = mapOf(
            "handler_type" to "greeting",
            "processed_at" to System.currentTimeMillis().toString()
        )
    )
}

/**
 * 에코 핸들러 - 메시지 타입을 분석하여 포맷팅
 */
fun createEchoHandler(agentId: String): suspend (Comm) -> Comm = { comm ->
    val formattedContent = when {
        comm.content.contains("?") -> "You asked: ${comm.content}"
        comm.content.contains("!") -> "You exclaimed: ${comm.content}"
        else -> "You said: ${comm.content}"
    }
    
    val messageType = when {
        comm.content.contains("?") -> "question"
        comm.content.contains("!") -> "exclamation"
        else -> "statement"
    }
    
    Comm(
        content = formattedContent,
        from = agentId,
        to = comm.sender,
        metadata = mapOf(
            "handler_type" to "echo",
            "processed_at" to System.currentTimeMillis().toString(),
            "message_type" to messageType,
            "original_length" to comm.content.length.toString()
        )
    )
}

/**
 * 분석 핸들러 - 메시지를 분석하고 인사이트 제공
 */
fun createAnalysisHandler(agentId: String): suspend (Comm) -> Comm = { comm ->
    val text = comm.content
    val wordCount = text.split("\\s+".toRegex()).size
    val charCount = text.length
    val sentiment = when {
        text.contains("good", ignoreCase = true) || 
        text.contains("great", ignoreCase = true) || 
        text.contains("awesome", ignoreCase = true) -> "positive"
        text.contains("bad", ignoreCase = true) || 
        text.contains("terrible", ignoreCase = true) || 
        text.contains("awful", ignoreCase = true) -> "negative"
        else -> "neutral"
    }
    
    val analysis = """
        |Text Analysis Results:
        |• Words: $wordCount
        |• Characters: $charCount
        |• Sentiment: $sentiment
        |• Contains question: ${text.contains("?")}
        |• Language hints: ${detectLanguageHints(text)}
    """.trimMargin()
    
    Comm(
        content = analysis,
        from = agentId,
        to = comm.sender,
        metadata = mapOf(
            "handler_type" to "analysis",
            "word_count" to wordCount.toString(),
            "char_count" to charCount.toString(),
            "sentiment" to sentiment,
            "processed_at" to System.currentTimeMillis().toString()
        )
    )
}

/**
 * 변환 핸들러 팩토리 - 커스텀 변환 함수를 받아서 핸들러 생성
 */
fun createTransformHandler(
    agentId: String,
    transformer: (String) -> String,
    transformType: String = "custom"
): suspend (Comm) -> Comm = { comm ->
    val transformedContent = try {
        transformer(comm.content)
    } catch (e: Exception) {
        "Transformation error: ${e.message}"
    }
    
    Comm(
        content = transformedContent,
        from = agentId,
        to = comm.sender,
        metadata = mapOf(
            "handler_type" to "transform",
            "transform_type" to transformType,
            "original_content" to comm.content,
            "processed_at" to System.currentTimeMillis().toString()
        )
    )
}

/**
 * 조건부 라우팅 핸들러 - 메시지 내용에 따라 다른 응답
 */
fun createConditionalHandler(agentId: String): suspend (Comm) -> Comm = { comm ->
    val content = comm.content.lowercase()
    
    val response = when {
        content.contains("hello") || content.contains("hi") -> 
            "👋 Hello there! How can I help you today?"
        content.contains("help") -> 
            "🆘 I'm here to help! What do you need assistance with?"
        content.contains("bye") || content.contains("goodbye") -> 
            "👋 Goodbye! Have a great day!"
        content.contains("time") -> 
            "🕐 Current timestamp: ${System.currentTimeMillis()}"
        content.contains("weather") -> 
            "🌤️ I don't have access to weather data, but it's always sunny in the digital world!"
        content.length > 100 -> 
            "📝 That's quite a long message! Here's a summary: '${content.take(50)}...'"
        content.isBlank() -> 
            "🤔 You sent an empty comm. Did you mean to say something?"
        else -> 
            "🤖 I received your message: '$content'. I'm a simple agent, so I don't have a specific response for that."
    }
    
    Comm(
        content = response,
        from = agentId,
        to = comm.sender,
        metadata = mapOf(
            "handler_type" to "conditional",
            "condition_matched" to getMatchedCondition(content),
            "processed_at" to System.currentTimeMillis().toString()
        )
    )
}

// =====================================
// UTILITY FUNCTIONS
// =====================================

/**
 * 언어 힌트 감지 (간단한 버전)
 */
private fun detectLanguageHints(text: String): String {
    return when {
        text.contains("안녕") || text.contains("감사") -> "Korean detected"
        text.contains("hola") || text.contains("gracias") -> "Spanish detected"
        text.contains("bonjour") || text.contains("merci") -> "French detected"
        text.contains("こんにちは") || text.contains("ありがとう") -> "Japanese detected"
        else -> "English (default)"
    }
}

/**
 * 매칭된 조건 식별
 */
private fun getMatchedCondition(content: String): String {
    return when {
        content.contains("hello") || content.contains("hi") -> "greeting"
        content.contains("help") -> "help_request"
        content.contains("bye") || content.contains("goodbye") -> "farewell"
        content.contains("time") -> "time_request"
        content.contains("weather") -> "weather_request"
        content.length > 100 -> "long_message"
        content.isBlank() -> "empty_message"
        else -> "default"
    }
}

// =====================================
// MAIN FUNCTION
// =====================================

fun main() = runBlocking {
    println("=== Basic Agent Example (Refactored) ===")
    
    // 1. 가장 간단한 에이전트 - 분리된 핸들러 사용
    val greetingAgent = buildAgent {
        id = "greeting-agent"
        name = "Greeting Agent"
        description = "A simple agent that greets users using separated handler"
        
        handle(createGreetingHandler(id))
    }
    AgentRegistry.register(greetingAgent)
    
    // 2. 에코 에이전트 - 향상된 분석 기능
    val echoAgent = buildAgent {
        id = "echo-agent"
        name = "Echo Agent"
        description = "Enhanced echo agent with message type analysis"
        
        handle(createEchoHandler(id))
    }
    AgentRegistry.register(echoAgent)
    
    // 3. 분석 에이전트 - 텍스트 분석 전문
    val analysisAgent = buildAgent {
        id = "analysis-agent"
        name = "Analysis Agent"
        description = "Analyzes text content and provides insights"
        
        handle(createAnalysisHandler(id))
    }
    AgentRegistry.register(analysisAgent)
    
    // 4. 변환 에이전트 - 커스텀 변환 함수 사용
    val transformAgent = buildAgent {
        id = "transform-agent"
        name = "Transform Agent"
        description = "Transforms text using custom functions"
        
        handle(createTransformHandler(id, { text ->
            """
            |Original: $text
            |Uppercase: ${text.uppercase()}
            |Reversed: ${text.reversed()}
            |ROT13: ${text.map { c -> 
                when {
                    c.isLetter() -> ((c.lowercaseChar() - 'a' + 13) % 26 + 'a'.code).toChar()
                    else -> c
                }
            }.joinToString("")}
            """.trimMargin()
        }, "multi-transform"))
    }
    AgentRegistry.register(transformAgent)
    
    // 5. 조건부 에이전트 - 스마트 응답
    val conditionalAgent = buildAgent {
        id = "conditional-agent"
        name = "Conditional Agent"
        description = "Provides context-aware responses based on message content"
        
        handle(createConditionalHandler(id))
    }
    AgentRegistry.register(conditionalAgent)
    
    // 테스트 메시지들
    val testComms = listOf(
        "Nice to meet you!" to "greeting-agent",
        "How are you?" to "echo-agent", 
        "This is an awesome framework for building agents!" to "analysis-agent",
        "Hello World" to "transform-agent",
        "Hi there, I need help with something" to "conditional-agent"
    )
    
    println("\n=== Testing All Agents ===")
    testComms.forEach { (content, agentId) ->
        println("\n--- Testing $agentId ---")
        val agent = AgentRegistry.getAgent(agentId)
        val message = Comm(content = content, from = "user", to = agentId)
        val response = agent?.processComm(message)
        
        println("Input: $content")
        println("Output: ${response?.content}")
        println("Metadata: ${response?.metadata}")
    }
    
    // 6. 핸들러 재사용 예제
    println("\n=== Handler Reusability Example ===")
    val anotherGreetingAgent = buildAgent {
        id = "another-greeting-agent"
        name = "Another Greeting Agent"
        description = "Uses the same greeting handler"
        
        // 같은 핸들러를 재사용
        handle(createGreetingHandler(id))
    }
    
    val testComm = Comm(content = "Reusability test", from = "user")
    val response = anotherGreetingAgent.processComm(testComm)
    println("Reused handler response: ${response.content}")
    
    println("\n✅ Handler separation improves:")
    println("  • Code readability and organization")
    println("  • Handler reusability across agents")
    println("  • Easier testing and maintenance")
    println("  • Separation of concerns")
} 