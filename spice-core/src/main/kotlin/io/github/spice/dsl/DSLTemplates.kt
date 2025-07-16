package io.github.spice.dsl

import io.github.spice.*

/**
 * 🚀 DSL Templates & Scaffolding
 * 
 * 사용자가 빠르게 DSL을 시작할 수 있도록 도와주는 템플릿 함수들.
 * 일반적인 패턴과 모범 사례를 포함한 미리 정의된 구성 요소들을 제공합니다.
 */

// =====================================
// AGENT TEMPLATES
// =====================================

/**
 * 기본 에이전트 템플릿 - 간단한 응답 핸들러 포함
 */
fun defaultAgent(
    name: String,
    description: String = "A default agent created from template",
    responsePrefix: String = "Agent Response",
    alias: String? = null
): Agent {
    return buildAgent {
        id = alias ?: name.lowercase().replace(" ", "-")
        this.name = name
        this.description = description
        
        handle { message ->
            Message(
                content = "$responsePrefix: ${message.content}",
                sender = id,
                receiver = message.sender,
                metadata = mapOf(
                    "template_used" to "defaultAgent",
                    "template_alias" to (alias ?: "auto-generated"),
                    "processed_at" to System.currentTimeMillis().toString()
                )
            )
        }
    }
}

/**
 * 에코 에이전트 템플릿 - 받은 메시지를 그대로 반환
 */
fun echoAgent(
    name: String,
    formatMessage: Boolean = true,
    alias: String? = null
): Agent {
    return buildAgent {
        id = alias ?: name.lowercase().replace(" ", "-")
        this.name = name
        description = "Echoes back received messages"
        
        handle { message ->
            val content = if (formatMessage) {
                "Echo: ${message.content}"
            } else {
                message.content
            }
            
            Message(
                content = content,
                sender = id,
                receiver = message.sender,
                metadata = mapOf(
                    "template_used" to "echoAgent",
                    "template_alias" to (alias ?: "auto-generated"),
                    "original_content" to message.content
                )
            )
        }
    }
}

/**
 * 로깅 에이전트 템플릿 - 메시지를 로깅하고 전달
 */
fun loggingAgent(
    name: String,
    logPrefix: String = "[LOG]",
    alias: String? = null
): Agent {
    return buildAgent {
        id = alias ?: name.lowercase().replace(" ", "-")
        this.name = name
        description = "Logs messages and passes them through"
        
        handle { message ->
            // 로깅
            println("$logPrefix Agent '$name' received: ${message.content}")
            
            // 메시지 전달
            Message(
                content = message.content,
                sender = id,
                receiver = message.sender,
                metadata = message.metadata + mapOf(
                    "logged_by" to name,
                    "template_alias" to (alias ?: "auto-generated"),
                    "logged_at" to System.currentTimeMillis().toString()
                )
            )
        }
    }
}

/**
 * 변환 에이전트 템플릿 - 메시지를 변환하는 에이전트
 */
fun transformAgent(
    name: String,
    transformer: (String) -> String,
    alias: String? = null
): Agent {
    return buildAgent {
        id = alias ?: name.lowercase().replace(" ", "-")
        this.name = name
        description = "Transforms message content using provided function"
        
        handle { message ->
            val transformedContent = try {
                transformer(message.content)
            } catch (e: Exception) {
                "Transformation error: ${e.message}"
            }
            
            Message(
                content = transformedContent,
                sender = id,
                receiver = message.sender,
                metadata = message.metadata + mapOf(
                    "template_used" to "transformAgent",
                    "template_alias" to (alias ?: "auto-generated"),
                    "original_content" to message.content
                )
            )
        }
    }
}

// =====================================
// TOOL TEMPLATES
// =====================================

/**
 * 스트리밍 도구 템플릿 - 단일 파라미터 + 기본 executor
 */
fun streamingTool(
    name: String,
    parameterName: String = "input",
    parameterType: String = "string",
    description: String = "A streaming tool created from template",
    processor: (String) -> String = { input -> "Processed: $input" },
    alias: String? = null
): Tool {
    val toolName = alias ?: name
    return tool(toolName) {
        this.description = description
        param(parameterName, parameterType)
        
        execute { params ->
            try {
                val input = params[parameterName] as String
                val result = processor(input)
                ToolResult.success(
                    result = result,
                    metadata = mapOf(
                        "template_used" to "streamingTool",
                        "template_alias" to (alias ?: "auto-generated"),
                        "input_length" to input.length.toString()
                    )
                )
            } catch (e: Exception) {
                ToolResult.error("Tool execution error: ${e.message}")
            }
        }
    }
}

/**
 * 계산기 도구 템플릿 - 기본적인 수학 연산
 */
fun calculatorTool(
    name: String = "calculator",
    alias: String? = null
): Tool {
    val toolName = alias ?: name
    return tool(toolName) {
        description = "Performs basic mathematical operations"
        param("operation", "string")
        param("a", "number")
        param("b", "number")
        
        execute { params ->
            try {
                val operation = params["operation"] as String
                val a = (params["a"] as Number).toDouble()
                val b = (params["b"] as Number).toDouble()
                
                val result = when (operation.lowercase()) {
                    "add", "+" -> a + b
                    "subtract", "-" -> a - b
                    "multiply", "*" -> a * b
                    "divide", "/" -> {
                        if (b == 0.0) {
                            return@execute ToolResult.error("Division by zero")
                        }
                        a / b
                    }
                    "power", "^" -> Math.pow(a, b)
                    else -> return@execute ToolResult.error("Unknown operation: $operation")
                }
                
                ToolResult.success(
                    result = "$a $operation $b = $result",
                    metadata = mapOf(
                        "operation" to operation,
                        "result" to result.toString(),
                        "template_alias" to (alias ?: "auto-generated")
                    )
                )
            } catch (e: Exception) {
                ToolResult.error("Calculation error: ${e.message}")
            }
        }
    }
}

/**
 * 텍스트 처리 도구 템플릿
 */
fun textProcessorTool(
    name: String = "text-processor",
    alias: String? = null
): Tool {
    val toolName = alias ?: name
    return tool(toolName) {
        description = "Processes text with various transformations"
        param("text", "string")
        param("operation", "string")
        
        execute { params ->
            try {
                val text = params["text"] as String
                val operation = params["operation"] as String
                
                val result = when (operation.lowercase()) {
                    "uppercase" -> text.uppercase()
                    "lowercase" -> text.lowercase()
                    "reverse" -> text.reversed()
                    "length" -> "Length: ${text.length}"
                    "words" -> "Word count: ${text.split("\\s+".toRegex()).size}"
                    "capitalize" -> text.split(" ").joinToString(" ") { 
                        it.lowercase().replaceFirstChar { c -> c.uppercase() } 
                    }
                    else -> return@execute ToolResult.error("Unknown operation: $operation")
                }
                
                ToolResult.success(
                    result = result,
                    metadata = mapOf(
                        "operation" to operation,
                        "original_text" to text,
                        "template_alias" to (alias ?: "auto-generated")
                    )
                )
            } catch (e: Exception) {
                ToolResult.error("Text processing error: ${e.message}")
            }
        }
    }
}

// =====================================
// FLOW TEMPLATES
// =====================================

/**
 * 로깅 플로우 템플릿 - 메시지를 로깅하면서 전달
 */
fun loggingFlow(
    name: String,
    agentIds: List<String>,
    logLevel: String = "INFO"
): CoreFlow {
    return flow {
        id = name.lowercase().replace(" ", "-")
        this.name = name
        description = "Logs message flow through specified agents"
        
        agentIds.forEach { agentId ->
            step(agentId)
        }
    }
}

/**
 * 파이프라인 플로우 템플릿 - 순차적으로 에이전트들을 실행
 */
fun pipelineFlow(
    name: String,
    agentIds: List<String>
): CoreFlow {
    return flow {
        id = name.lowercase().replace(" ", "-")
        this.name = name
        description = "Sequential pipeline processing through ${agentIds.size} agents"
        
        agentIds.forEachIndexed { index, agentId ->
            step("step-${index + 1}", agentId)
        }
    }
}

/**
 * 조건부 라우팅 플로우 템플릿
 */
fun conditionalRoutingFlow(
    name: String,
    conditions: List<Pair<(Message) -> Boolean, String>>,
    defaultAgentId: String
): CoreFlow {
    return flow {
        id = name.lowercase().replace(" ", "-")
        this.name = name
        description = "Routes messages based on conditions"
        
        conditions.forEachIndexed { index, (condition, agentId) ->
            step("condition-${index + 1}", agentId, condition)
        }
        
        step("default", defaultAgentId)
    }
}

// =====================================
// COMPLETE SCENARIO TEMPLATES
// =====================================

/**
 * 고객 서비스 템플릿 - 완전한 고객 서비스 시나리오
 */
fun createCustomerServiceTemplate(): CustomerServiceTemplate {
    // 감정 분석 도구
    val sentimentTool = tool("sentiment-analysis") {
        description = "Analyzes customer message sentiment"
        param("text", "string")
        
        execute { params ->
            val text = params["text"] as String
            val sentiment = when {
                text.contains("angry", ignoreCase = true) || 
                text.contains("frustrated", ignoreCase = true) ||
                text.contains("terrible", ignoreCase = true) -> "negative"
                text.contains("happy", ignoreCase = true) ||
                text.contains("great", ignoreCase = true) ||
                text.contains("excellent", ignoreCase = true) -> "positive"
                else -> "neutral"
            }
            
            ToolResult.success(
                result = sentiment,
                metadata = mapOf(
                    "confidence" to "0.8",
                    "analyzed_text_length" to text.length.toString()
                )
            )
        }
    }
    
    // 티켓 생성 도구
    val ticketTool = tool("ticket-creator") {
        description = "Creates support tickets for customer issues"
        param("issue", "string")
        param("priority", "string")
        param("customer", "string")
        
        execute { params ->
            val issue = params["issue"] as String
            val priority = params["priority"] as String
            val customer = params["customer"] as String
            val ticketId = "TICKET-${System.currentTimeMillis()}"
            
            ToolResult.success(
                result = "Created ticket $ticketId for $customer with priority $priority",
                metadata = mapOf(
                    "ticket_id" to ticketId,
                    "priority" to priority,
                    "customer" to customer
                )
            )
        }
    }
    
    // 감정 분석 에이전트
    val sentimentAgent = buildAgent {
        id = "sentiment-agent"
        name = "Sentiment Analysis Agent"
        description = "Analyzes customer sentiment"
        tools("sentiment-analysis")
        
        handle { message ->
            val sentimentResult = sentimentTool.execute(mapOf("text" to message.content))
            val sentiment = sentimentResult.result
            
            Message(
                content = message.content,
                sender = id,
                receiver = message.sender,
                metadata = message.metadata + mapOf(
                    "sentiment" to sentiment,
                    "sentiment_confidence" to (sentimentResult.metadata["confidence"] ?: "0.5")
                )
            )
        }
    }
    
    // 응답 생성 에이전트
    val responseAgent = buildAgent {
        id = "response-agent"
        name = "Customer Response Agent"
        description = "Generates appropriate customer responses"
        
        handle { message ->
            val sentiment = message.metadata["sentiment"] ?: "neutral"
            val response = when (sentiment) {
                "negative" -> "I understand your frustration. Let me help you resolve this issue."
                "positive" -> "Thank you for your positive feedback! How else can I assist you?"
                else -> "Thank you for contacting us. How can I help you today?"
            }
            
            Message(
                content = response,
                sender = id,
                receiver = message.sender,
                metadata = message.metadata + mapOf(
                    "response_type" to sentiment,
                    "auto_generated" to "true"
                )
            )
        }
    }
    
    // 에스컬레이션 에이전트
    val escalationAgent = buildAgent {
        id = "escalation-agent"
        name = "Escalation Agent"
        description = "Handles case escalation for negative sentiment"
        tools("ticket-creator")
        
        handle { message ->
            val sentiment = message.metadata["sentiment"] ?: "neutral"
            
            if (sentiment == "negative") {
                val ticketResult = ticketTool.execute(mapOf(
                    "issue" to message.content,
                    "priority" to "high",
                    "customer" to (message.metadata["customer"] ?: "anonymous")
                ))
                
                Message(
                    content = "I've escalated your issue to our support team. ${ticketResult.result}",
                    sender = id,
                    receiver = message.sender,
                    metadata = message.metadata + ticketResult.metadata
                )
            } else {
                message
            }
        }
    }
    
    // 고객 서비스 플로우
    val customerServiceFlow = flow {
        id = "customer-service-flow"
        name = "Customer Service Flow"
        description = "Complete customer service workflow with sentiment analysis and escalation"
        
        step("sentiment-agent")
        step("response-agent")
        step("escalation-step", "escalation-agent") { message ->
            message.metadata["sentiment"] == "negative"
        }
    }
    
    return CustomerServiceTemplate(
        sentimentTool = sentimentTool,
        ticketTool = ticketTool,
        sentimentAgent = sentimentAgent,
        responseAgent = responseAgent,
        escalationAgent = escalationAgent,
        flow = customerServiceFlow
    )
}

/**
 * 데이터 처리 파이프라인 템플릿
 */
fun createDataProcessingTemplate(): DataProcessingTemplate {
    val validatorTool = streamingTool(
        name = "data-validator",
        description = "Validates input data format",
        processor = { input ->
            if (input.isNotBlank()) "Valid: $input" else "Invalid: empty input"
        }
    )
    
    val processorTool = streamingTool(
        name = "data-processor",
        description = "Processes validated data",
        processor = { input ->
            "Processed: ${input.uppercase()}"
        }
    )
    
    val validatorAgent = buildAgent {
        id = "validator-agent"
        name = "Data Validator"
        description = "Validates incoming data"
        tools("data-validator")
        
        handle { message ->
            val validationResult = validatorTool.execute(mapOf("input" to message.content))
            Message(
                content = if (validationResult.success) message.content else "INVALID_DATA",
                sender = id,
                receiver = message.sender,
                metadata = message.metadata + mapOf(
                    "validation_result" to validationResult.success.toString(),
                    "validation_message" to validationResult.result
                )
            )
        }
    }
    
    val processorAgent = buildAgent {
        id = "processor-agent"
        name = "Data Processor"
        description = "Processes validated data"
        tools("data-processor")
        
        handle { message ->
            if (message.content == "INVALID_DATA") {
                Message(
                    content = "Error: Cannot process invalid data",
                    sender = id,
                    receiver = message.sender,
                    metadata = message.metadata + mapOf("processing_error" to "invalid_input")
                )
            } else {
                val result = processorTool.execute(mapOf("input" to message.content))
                Message(
                    content = result.result,
                    sender = id,
                    receiver = message.sender,
                    metadata = message.metadata + mapOf("processed" to "true")
                )
            }
        }
    }
    
    val dataProcessingFlow = pipelineFlow(
        "data-processing-pipeline",
        listOf("validator-agent", "processor-agent")
    )
    
    return DataProcessingTemplate(
        validatorTool = validatorTool,
        processorTool = processorTool,
        validatorAgent = validatorAgent,
        processorAgent = processorAgent,
        flow = dataProcessingFlow
    )
}

// =====================================
// TEMPLATE DATA CLASSES
// =====================================

/**
 * 고객 서비스 템플릿 구성 요소
 */
data class CustomerServiceTemplate(
    val sentimentTool: Tool,
    val ticketTool: Tool,
    val sentimentAgent: Agent,
    val responseAgent: Agent,
    val escalationAgent: Agent,
    val flow: CoreFlow
) {
    /**
     * 모든 구성 요소를 레지스트리에 등록
     */
    fun registerAll() {
        ToolRegistry.register(sentimentTool)
        ToolRegistry.register(ticketTool)
        AgentRegistry.register(sentimentAgent)
        AgentRegistry.register(responseAgent)
        AgentRegistry.register(escalationAgent)
    }
    
    /**
     * 템플릿 사용 예시 출력
     */
    fun printUsageExample() {
        println("""
        |=== Customer Service Template Usage ===
        |
        |// 1. Register all components
        |val template = createCustomerServiceTemplate()
        |template.registerAll()
        |
        |// 2. Process customer message
        |val customerMessage = Message(
        |    content = "I'm very frustrated with this service!",
        |    sender = "customer",
        |    metadata = mapOf("customer" to "john.doe@email.com")
        |)
        |
        |// 3. Execute the flow
        |val result = template.flow.execute(customerMessage)
        |println("Response: ${result.content}")
        |
        |// Expected: Sentiment analysis -> Negative response -> Ticket creation
        """.trimMargin())
    }
}

/**
 * 데이터 처리 템플릿 구성 요소
 */
data class DataProcessingTemplate(
    val validatorTool: Tool,
    val processorTool: Tool,
    val validatorAgent: Agent,
    val processorAgent: Agent,
    val flow: CoreFlow
) {
    fun registerAll() {
        ToolRegistry.register(validatorTool)
        ToolRegistry.register(processorTool)
        AgentRegistry.register(validatorAgent)
        AgentRegistry.register(processorAgent)
    }
    
    fun printUsageExample() {
        println("""
        |=== Data Processing Template Usage ===
        |
        |// 1. Setup pipeline
        |val template = createDataProcessingTemplate()
        |template.registerAll()
        |
        |// 2. Process data
        |val dataMessage = Message(content = "raw data input", sender = "user")
        |val result = template.flow.execute(dataMessage)
        |println("Processed: ${result.content}")
        """.trimMargin())
    }
}

// =====================================
// TEMPLATE UTILITIES
// =====================================

/**
 * 사용 가능한 모든 템플릿 목록 출력
 */
fun printAvailableTemplates() {
    println("""
    |🚀 Available DSL Templates
    |==========================
    |
    |## Agent Templates:
    |• defaultAgent(name) - Basic agent with simple response
    |• echoAgent(name) - Echoes back messages
    |• loggingAgent(name) - Logs and forwards messages
    |• transformAgent(name, transformer) - Transforms message content
    |
    |## Tool Templates:
    |• streamingTool(name, processor) - Single parameter processing tool
    |• calculatorTool() - Basic mathematical operations
    |• textProcessorTool() - Text transformation operations
    |
    |## Flow Templates:
    |• loggingFlow(name, agentIds) - Logs message flow
    |• pipelineFlow(name, agentIds) - Sequential processing
    |• conditionalRoutingFlow(name, conditions, default) - Conditional routing
    |
    |## Complete Scenarios:
    |• createCustomerServiceTemplate() - Full customer service workflow
    |• createDataProcessingTemplate() - Data validation and processing pipeline
    |
    |## Usage Examples:
    |```kotlin
    |// Quick agent
    |val agent = defaultAgent("MyAgent")
    |AgentRegistry.register(agent)
    |
    |// Quick tool
    |val tool = streamingTool("processor") { input -> "Processed: ${'$'}input" }
    |ToolRegistry.register(tool)
    |
    |// Complete scenario
    |val customerService = createCustomerServiceTemplate()
    |customerService.registerAll()
    |```
    """.trimMargin())
} 

// =====================================
// SAMPLE LOADING DSL
// =====================================

/**
 * 샘플 로더 빌더 클래스
 */
class SampleLoaderBuilder {
    var agentId: String? = null
    var toolId: String? = null
    var flowId: String? = null
    var registerComponents: Boolean = true
    var overrideExisting: Boolean = false
    
    internal fun build(): SampleLoadResult {
        return SampleLoadResult(
            agentId = agentId,
            toolId = toolId, 
            flowId = flowId,
            registerComponents = registerComponents,
            overrideExisting = overrideExisting
        )
    }
}

/**
 * 샘플 로드 결과
 */
data class SampleLoadResult(
    val agentId: String?,
    val toolId: String?,
    val flowId: String?,
    val registerComponents: Boolean,
    val overrideExisting: Boolean
)

/**
 * 사전 정의된 샘플들을 로드하는 DSL 함수
 */
fun loadSample(sampleName: String, configure: SampleLoaderBuilder.() -> Unit = {}): LoadedSample {
    val builder = SampleLoaderBuilder()
    builder.configure()
    val config = builder.build()
    
    return when (sampleName.lowercase()) {
        "echo" -> loadEchoSample(config)
        "calculator" -> loadCalculatorSample(config)
        "logger" -> loadLoggerSample(config)
        "customer-service" -> loadCustomerServiceSample(config)
        "data-processing" -> loadDataProcessingSample(config)
        "chatbot" -> loadChatbotSample(config)
        "transformer" -> loadTransformerSample(config)
        else -> throw IllegalArgumentException("Unknown sample: $sampleName. Available: echo, calculator, logger, customer-service, data-processing, chatbot, transformer")
    }
}

/**
 * 로드된 샘플 정보
 */
data class LoadedSample(
    val name: String,
    val agents: List<Agent> = emptyList(),
    val tools: List<Tool> = emptyList(),
    val flows: List<CoreFlow> = emptyList(),
    val description: String = "",
    val usageExample: String = ""
) {
    fun printSummary() {
        println("📦 Loaded Sample: $name")
        println("Description: $description")
        println("Components:")
        println("  - Agents: ${agents.size}")
        println("  - Tools: ${tools.size}")
        println("  - Flows: ${flows.size}")
        if (usageExample.isNotEmpty()) {
            println("\nUsage Example:")
            println(usageExample)
        }
    }
    
    fun registerAll() {
        agents.forEach { AgentRegistry.register(it) }
        tools.forEach { ToolRegistry.register(it) }
        println("✅ Registered ${agents.size} agents and ${tools.size} tools")
    }
}

/**
 * Echo 샘플 로드
 */
private fun loadEchoSample(config: SampleLoadResult): LoadedSample {
    val agentId = config.agentId ?: "echo-sample"
    val agent = echoAgent("Echo Sample Agent", alias = agentId)
    
    val sample = LoadedSample(
        name = "Echo",
        agents = listOf(agent),
        description = "Simple echo agent that repeats back messages",
        usageExample = """
        |val message = Message(content = "Hello", sender = "user")
        |val response = AgentRegistry.getAgent("$agentId")?.processMessage(message)
        |println(response?.content) // "Echo: Hello"
        """.trimMargin()
    )
    
    if (config.registerComponents) {
        sample.registerAll()
    }
    
    return sample
}

/**
 * Calculator 샘플 로드
 */
private fun loadCalculatorSample(config: SampleLoadResult): LoadedSample {
    val toolId = config.toolId ?: "calculator-sample"
    val agentId = config.agentId ?: "calculator-agent"
    
    val calculator = calculatorTool(alias = toolId)
    val agent = buildAgent {
        id = agentId
        name = "Calculator Agent"
        description = "Performs mathematical calculations"
        tools(toolId)
        
        handle { message ->
            // 간단한 파싱: "5 + 3" 형태
            val parts = message.content.split(" ")
            if (parts.size == 3) {
                try {
                    val a = parts[0].toDouble()
                    val operation = parts[1]
                    val b = parts[2].toDouble()
                    
                    val result = calculator.execute(mapOf(
                        "operation" to operation,
                        "a" to a,
                        "b" to b
                    ))
                    
                    Message(
                        content = if (result.success) result.result else "Calculation error: ${result.error}",
                        sender = id,
                        receiver = message.sender
                    )
                } catch (e: Exception) {
                    Message(
                        content = "Invalid format. Use: '5 + 3' or '10 * 2'",
                        sender = id,
                        receiver = message.sender
                    )
                }
            } else {
                Message(
                    content = "Invalid format. Use: 'number operation number'",
                    sender = id,
                    receiver = message.sender
                )
            }
        }
    }
    
    val sample = LoadedSample(
        name = "Calculator",
        agents = listOf(agent),
        tools = listOf(calculator),
        description = "Calculator tool with agent wrapper for natural language math",
        usageExample = """
        |val message = Message(content = "15 * 3", sender = "user")
        |val response = AgentRegistry.getAgent("$agentId")?.processMessage(message)
        |println(response?.content) // "15.0 * 3.0 = 45.0"
        """.trimMargin()
    )
    
    if (config.registerComponents) {
        sample.registerAll()
    }
    
    return sample
}

/**
 * Logger 샘플 로드
 */
private fun loadLoggerSample(config: SampleLoadResult): LoadedSample {
    val agentId = config.agentId ?: "logger-sample"
    val agent = loggingAgent("Logger Sample", "[SAMPLE]", alias = agentId)
    
    val sample = LoadedSample(
        name = "Logger",
        agents = listOf(agent),
        description = "Logging agent that prints and forwards messages",
        usageExample = """
        |val message = Message(content = "Log this message", sender = "user")
        |val response = AgentRegistry.getAgent("$agentId")?.processMessage(message)
        |// Console: [SAMPLE] Agent 'Logger Sample' received: Log this message
        """.trimMargin()
    )
    
    if (config.registerComponents) {
        sample.registerAll()
    }
    
    return sample
}

/**
 * Chatbot 샘플 로드
 */
private fun loadChatbotSample(config: SampleLoadResult): LoadedSample {
    val toolId = config.toolId ?: "faq-sample"
    val agentId = config.agentId ?: "chatbot-sample"
    
    val faqTool = streamingTool(
        name = "faq-sample",
        description = "FAQ responses for common questions",
        alias = toolId
    ) { question ->
        when {
            question.contains("hello", ignoreCase = true) -> "Hello! How can I help you today?"
            question.contains("help", ignoreCase = true) -> "I'm here to assist you! What do you need help with?"
            question.contains("bye", ignoreCase = true) -> "Goodbye! Have a great day!"
            question.contains("weather", ignoreCase = true) -> "I don't have access to weather data, but you can check your local weather service!"
            question.contains("time", ignoreCase = true) -> "I don't have access to the current time, but you can check your device's clock!"
            else -> "That's an interesting question! Could you provide more details?"
        }
    }
    
    val chatbot = buildAgent {
        id = agentId
        name = "FAQ Chatbot"
        description = "Simple chatbot with FAQ responses"
        tools(toolId)
        
        handle { message ->
            val response = faqTool.execute(mapOf("input" to message.content))
            Message(
                content = response.result,
                sender = id,
                receiver = message.sender,
                metadata = mapOf("chatbot_response" to "true")
            )
        }
    }
    
    val sample = LoadedSample(
        name = "Chatbot",
        agents = listOf(chatbot),
        tools = listOf(faqTool),
        description = "Simple FAQ chatbot for common questions",
        usageExample = """
        |val questions = listOf("Hello", "Can you help me?", "Goodbye")
        |questions.forEach { question ->
        |    val message = Message(content = question, sender = "user")
        |    val response = AgentRegistry.getAgent("$agentId")?.processMessage(message)
        |    println("Q: ${"$"}question -> A: ${"$"}{response?.content}")
        |}
        """.trimMargin()
    )
    
    if (config.registerComponents) {
        sample.registerAll()
    }
    
    return sample
}

/**
 * Transformer 샘플 로드
 */
private fun loadTransformerSample(config: SampleLoadResult): LoadedSample {
    val agentId = config.agentId ?: "transformer-sample"
    
    val agent = transformAgent(
        "Text Transformer",
        transformer = { input ->
            // 다양한 변환 적용
            val operations = listOf(
                "Uppercase: ${input.uppercase()}",
                "Length: ${input.length} characters",
                "Reversed: ${input.reversed()}",
                "Words: ${input.split("\\s+".toRegex()).size}"
            )
            operations.joinToString("\n")
        },
        alias = agentId
    )
    
    val sample = LoadedSample(
        name = "Transformer",
        agents = listOf(agent),
        description = "Text transformation agent with multiple operations",
        usageExample = """
        |val message = Message(content = "Hello World", sender = "user")
        |val response = AgentRegistry.getAgent("$agentId")?.processMessage(message)
        |println(response?.content)
        |// Output:
        |// Uppercase: HELLO WORLD
        |// Length: 11 characters
        |// Reversed: dlroW olleH
        |// Words: 2
        """.trimMargin()
    )
    
    if (config.registerComponents) {
        sample.registerAll()
    }
    
    return sample
}

/**
 * Customer Service 샘플 로드
 */
private fun loadCustomerServiceSample(config: SampleLoadResult): LoadedSample {
    val template = createCustomerServiceTemplate()
    
    val sample = LoadedSample(
        name = "Customer Service",
        agents = listOf(template.sentimentAgent, template.responseAgent, template.escalationAgent),
        tools = listOf(template.sentimentTool, template.ticketTool),
        flows = listOf(template.flow),
        description = "Complete customer service workflow with sentiment analysis",
        usageExample = """
        |val message = Message(
        |    content = "I'm frustrated with this service!",
        |    sender = "customer",
        |    metadata = mapOf("customer" to "user@email.com")
        |)
        |val result = template.flow.execute(message)
        |println(result.content)
        """.trimMargin()
    )
    
    if (config.registerComponents) {
        sample.registerAll()
    }
    
    return sample
}

/**
 * Data Processing 샘플 로드
 */
private fun loadDataProcessingSample(config: SampleLoadResult): LoadedSample {
    val template = createDataProcessingTemplate()
    
    val sample = LoadedSample(
        name = "Data Processing",
        agents = listOf(template.validatorAgent, template.processorAgent),
        tools = listOf(template.validatorTool, template.processorTool),
        flows = listOf(template.flow),
        description = "Data validation and processing pipeline",
        usageExample = """
        |val message = Message(content = "raw data input", sender = "user")
        |val result = template.flow.execute(message)
        |println(result.content) // "Processed: RAW DATA INPUT"
        """.trimMargin()
    )
    
    if (config.registerComponents) {
        sample.registerAll()
    }
    
    return sample
}

/**
 * 사용 가능한 샘플 목록 출력
 */
fun printAvailableSamples() {
    println("""
    |📦 Available Sample Templates
    |============================
    |
    |## Quick Samples:
    |• echo - Simple echo agent
    |• calculator - Math operations with natural language
    |• logger - Message logging agent
    |• chatbot - FAQ chatbot with common responses
    |• transformer - Text transformation operations
    |
    |## Complete Scenarios:
    |• customer-service - Full customer service workflow
    |• data-processing - Data validation and processing pipeline
    |
    |## Usage:
    |```kotlin
    |// Load and register sample
    |val sample = loadSample("echo") { 
    |    agentId = "my-echo-agent"
    |    registerComponents = true
    |}
    |sample.printSummary()
    |
    |// Load without auto-registration
    |val calculator = loadSample("calculator") {
    |    agentId = "calc-agent"
    |    toolId = "calc-tool"
    |    registerComponents = false
    |}
    |calculator.registerAll() // Manual registration
    |```
    """.trimMargin())
} 