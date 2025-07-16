package io.github.spice.samples.templates

import io.github.spice.Message
import io.github.spice.dsl.*
import kotlinx.coroutines.runBlocking

/**
 * DSL 템플릿 사용법 예제
 * 
 * 이 파일은 Spice DSL의 다양한 템플릿 함수들을 사용하는 방법을 보여줍니다.
 * 빠른 프로토타이핑과 일반적인 패턴 구현에 유용합니다.
 */
fun main() = runBlocking {
    println("🚀 DSL Templates Usage Examples")
    println("================================")
    
    // 사용 가능한 템플릿 목록 출력
    printAvailableTemplates()
    
    // 1. 기본 Agent 템플릿들
    println("\n=== 1. Agent Templates ===")
    agentTemplateExamples()
    
    // 2. Tool 템플릿들
    println("\n=== 2. Tool Templates ===")
    toolTemplateExamples()
    
    // 3. Flow 템플릿들  
    println("\n=== 3. Flow Templates ===")
    flowTemplateExamples()
    
    // 4. 완전한 시나리오 템플릿들
    println("\n=== 4. Complete Scenario Templates ===")
    completeScenarioExamples()
    
    println("\n✅ All template examples completed!")
}

/**
 * Agent 템플릿 사용 예제
 */
private suspend fun agentTemplateExamples() {
    println("Creating various agent templates...")
    
    // 1. 기본 에이전트
    val myAgent = defaultAgent("My First Agent")
    AgentRegistry.register(myAgent)
    
    val message1 = Message(content = "Hello World", sender = "user")
    val response1 = myAgent.processMessage(message1)
    println("Default Agent: ${response1.content}")
    
    // 2. 에코 에이전트
    val echoBot = echoAgent("Echo Bot", formatMessage = true)
    AgentRegistry.register(echoBot)
    
    val message2 = Message(content = "Test message", sender = "user")
    val response2 = echoBot.processMessage(message2)
    println("Echo Agent: ${response2.content}")
    
    // 3. 로깅 에이전트
    val logger = loggingAgent("Logger Agent", "[INFO]")
    AgentRegistry.register(logger)
    
    val message3 = Message(content = "This will be logged", sender = "user")
    val response3 = logger.processMessage(message3)
    println("Logging Agent: ${response3.content}")
    
    // 4. 변환 에이전트
    val transformer = transformAgent("Uppercase Transformer") { input ->
        input.uppercase()
    }
    AgentRegistry.register(transformer)
    
    val message4 = Message(content = "transform this text", sender = "user")
    val response4 = transformer.processMessage(message4)
    println("Transform Agent: ${response4.content}")
}

/**
 * Tool 템플릿 사용 예제
 */
private suspend fun toolTemplateExamples() {
    println("Creating various tool templates...")
    
    // 1. 스트리밍 도구
    val processor = streamingTool(
        name = "word-counter",
        description = "Counts words in input text"
    ) { input ->
        val wordCount = input.split("\\s+".toRegex()).size
        "Word count: $wordCount"
    }
    ToolRegistry.register(processor)
    
    val result1 = processor.execute(mapOf("input" to "Hello world from Spice DSL"))
    println("Streaming Tool: ${result1.result}")
    
    // 2. 계산기 도구
    val calculator = calculatorTool("my-calculator")
    ToolRegistry.register(calculator)
    
    val result2 = calculator.execute(mapOf(
        "operation" to "multiply",
        "a" to 15,
        "b" to 3
    ))
    println("Calculator Tool: ${result2.result}")
    
    // 3. 텍스트 처리 도구
    val textProcessor = textProcessorTool("text-utils")
    ToolRegistry.register(textProcessor)
    
    val result3 = textProcessor.execute(mapOf(
        "text" to "hello world",
        "operation" to "capitalize"
    ))
    println("Text Processor Tool: ${result3.result}")
    
    // 도구를 사용하는 에이전트 생성
    val toolAgent = buildAgent {
        id = "tool-user-agent"
        name = "Tool User Agent"
        description = "Uses various tools"
        tools("word-counter", "my-calculator", "text-utils")
        
        handle { message ->
            val words = processor.execute(mapOf("input" to message.content))
            Message(
                content = "Analysis: ${words.result}",
                sender = id,
                receiver = message.sender
            )
        }
    }
    AgentRegistry.register(toolAgent)
    
    val toolMessage = Message(content = "The quick brown fox jumps", sender = "user")
    val toolResponse = toolAgent.processMessage(toolMessage)
    println("Tool User Agent: ${toolResponse.content}")
}

/**
 * Flow 템플릿 사용 예제
 */
private suspend fun flowTemplateExamples() {
    println("Creating various flow templates...")
    
    // 기본 에이전트들이 이미 등록되어 있다고 가정
    val agentIds = listOf("my-first-agent", "echo-bot", "logger-agent")
    
    // 1. 로깅 플로우
    val loggingFlow = loggingFlow("Message Logging Flow", agentIds)
    
    println("Created logging flow with ${agentIds.size} agents")
    
    // 2. 파이프라인 플로우
    val pipeline = pipelineFlow("Processing Pipeline", agentIds)
    
    val pipelineMessage = Message(content = "Pipeline test", sender = "user")
    val pipelineResult = pipeline.execute(pipelineMessage)
    println("Pipeline Flow Result: ${pipelineResult.content}")
    
    // 3. 조건부 라우팅 플로우
    val conditions = listOf(
        { message: Message -> message.content.contains("echo") } to "echo-bot",
        { message: Message -> message.content.contains("log") } to "logger-agent"
    )
    
    val conditionalFlow = conditionalRoutingFlow(
        "Smart Router",
        conditions,
        "my-first-agent"
    )
    
    val testMessages = listOf(
        "This should echo back",
        "Please log this message", 
        "Default routing test"
    )
    
    testMessages.forEach { content ->
        val msg = Message(content = content, sender = "user")
        val result = conditionalFlow.execute(msg)
        println("Conditional routing '$content' -> ${result.content}")
    }
}

/**
 * 완전한 시나리오 템플릿 사용 예제
 */
private suspend fun completeScenarioExamples() {
    println("Testing complete scenario templates...")
    
    // 1. 고객 서비스 템플릿
    println("\n--- Customer Service Template ---")
    val customerService = createCustomerServiceTemplate()
    customerService.registerAll()
    customerService.printUsageExample()
    
    // 실제 고객 서비스 테스트
    val customerMessages = listOf(
        Message(
            content = "I'm very frustrated with your service!",
            sender = "customer",
            metadata = mapOf("customer" to "angry.customer@email.com")
        ),
        Message(
            content = "Your product is excellent! Thank you!",
            sender = "customer", 
            metadata = mapOf("customer" to "happy.customer@email.com")
        ),
        Message(
            content = "I have a question about my order",
            sender = "customer",
            metadata = mapOf("customer" to "neutral.customer@email.com")
        )
    )
    
    customerMessages.forEach { message ->
        println("\nCustomer Input: ${message.content}")
        val result = customerService.flow.execute(message)
        println("Service Response: ${result.content}")
        println("Sentiment: ${result.metadata["sentiment"]}")
        if (result.metadata.containsKey("ticket_id")) {
            println("Ticket Created: ${result.metadata["ticket_id"]}")
        }
    }
    
    // 2. 데이터 처리 템플릿
    println("\n--- Data Processing Template ---")
    val dataProcessing = createDataProcessingTemplate()
    dataProcessing.registerAll()
    dataProcessing.printUsageExample()
    
    // 실제 데이터 처리 테스트
    val dataInputs = listOf(
        "valid data input",
        "",  // Invalid data
        "another valid input"
    )
    
    dataInputs.forEach { input ->
        val dataMessage = Message(content = input, sender = "user")
        val result = dataProcessing.flow.execute(dataMessage)
        println("Data Input: '$input' -> ${result.content}")
        println("Validation: ${result.metadata["validation_result"]}")
    }
}

/**
 * 템플릿을 사용한 빠른 프로토타이핑 예제
 */
fun quickPrototypingExample() = runBlocking {
    println("\n🚀 Quick Prototyping with Templates")
    println("====================================")
    
    // 1분 안에 챗봇 만들기
    println("Creating a chatbot in under 1 minute...")
    
    // 단계 1: 기본 응답 에이전트
    val chatbot = defaultAgent(
        "Chatbot",
        "A friendly chatbot",
        "Bot"
    )
    
    // 단계 2: 도구 추가
    val faqTool = streamingTool("faq") { question ->
        when {
            question.contains("hello", ignoreCase = true) -> "Hello! How can I help you?"
            question.contains("help", ignoreCase = true) -> "I'm here to assist you!"
            question.contains("bye", ignoreCase = true) -> "Goodbye! Have a great day!"
            else -> "I understand. Could you be more specific?"
        }
    }
    
    // 단계 3: 스마트 챗봇 에이전트
    val smartChatbot = buildAgent {
        id = "smart-chatbot"
        name = "Smart Chatbot"
        description = "AI chatbot with FAQ capabilities"
        tools("faq")
        
        handle { message ->
            val faqResponse = faqTool.execute(mapOf("input" to message.content))
            Message(
                content = faqResponse.result,
                sender = id,
                receiver = message.sender,
                metadata = mapOf("bot_response" to "true")
            )
        }
    }
    
    // 등록 및 테스트
    ToolRegistry.register(faqTool)
    AgentRegistry.register(smartChatbot)
    
    val testQuestions = listOf(
        "Hello there!",
        "I need help",
        "What can you do?", 
        "Goodbye"
    )
    
    testQuestions.forEach { question ->
        val response = smartChatbot.processMessage(
            Message(content = question, sender = "user")
        )
        println("User: $question")
        println("Bot: ${response.content}\n")
    }
    
    println("✅ Chatbot created and tested successfully!")
} 