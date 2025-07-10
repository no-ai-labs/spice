package io.github.spice

/**
 * 🧱 Spice Agent Builder DSL
 * 
 * Agent를 선언적으로 생성할 수 있는 Kotlin DSL입니다.
 * 
 * 사용 예제:
 * ```kotlin
 * val myAgent = buildAgent {
 *     id = "my-custom-agent"
 *     name = "My Custom Agent"
 *     description = "This agent does amazing things"
 *     
 *     capabilities {
 *         add("text_processing")
 *         add("data_analysis")
 *         add("file_handling")
 *     }
 *     
 *     tools {
 *         add(WebSearchTool())
 *         add(FileReadTool())
 *         custom("calculator") {
 *             description = "Simple calculator tool"
 *             parameter("operation", "string", "Math operation", required = true)
 *             parameter("a", "number", "First number", required = true)
 *             parameter("b", "number", "Second number", required = true)
 *             
 *             execute { params ->
 *                 val op = params["operation"] as String
 *                 val a = (params["a"] as Number).toDouble()
 *                 val b = (params["b"] as Number).toDouble()
 *                 
 *                 val result = when (op) {
 *                     "add" -> a + b
 *                     "subtract" -> a - b
 *                     "multiply" -> a * b
 *                     "divide" -> if (b != 0.0) a / b else throw IllegalArgumentException("Division by zero")
 *                     else -> throw IllegalArgumentException("Unknown operation: $op")
 *                 }
 *                 
 *                 ToolResult.success("Result: $result")
 *             }
 *         }
 *     }
 *     
 *     messageHandler { message ->
 *         when (message.type) {
 *             MessageType.TEXT -> {
 *                 val processed = "Custom processing: ${message.content}"
 *                 message.createReply(processed, this@buildAgent.id)
 *             }
 *             MessageType.DATA -> {
 *                 val analyzed = "Data analysis complete: ${message.content}"
 *                 message.createReply(analyzed, this@buildAgent.id, MessageType.RESULT)
 *             }
 *             else -> {
 *                 message.createReply("Processed: ${message.content}", this@buildAgent.id)
 *             }
 *         }
 *     }
 * }
 * ```
 */

/**
 * Agent Builder DSL의 메인 함수
 */
fun buildAgent(init: AgentBuilder.() -> Unit): Agent {
    val builder = AgentBuilder()
    builder.init()
    return builder.build()
}

/**
 * Agent Builder 클래스
 */
class AgentBuilder {
    var id: String = "agent-${System.currentTimeMillis()}"
    var name: String = "Custom Agent"
    var description: String = "Agent created with DSL"
    
    private val capabilities = mutableListOf<String>()
    private val tools = mutableListOf<Tool>()
    private var messageProcessor: (suspend (Message) -> Message)? = null
    private var canHandleChecker: ((Message) -> Boolean)? = null
    
    /**
     * Capabilities 설정
     */
    fun capabilities(init: MutableList<String>.() -> Unit) {
        capabilities.init()
    }
    
    /**
     * Tools 설정
     */
    fun tools(init: ToolsBuilder.() -> Unit) {
        val toolsBuilder = ToolsBuilder()
        toolsBuilder.init()
        tools.addAll(toolsBuilder.getTools())
    }
    
    /**
     * 메시지 처리 핸들러 설정
     */
    fun messageHandler(handler: suspend (Message) -> Message) {
        messageProcessor = handler
    }
    
    /**
     * canHandle 조건 설정
     */
    fun canHandle(checker: (Message) -> Boolean) {
        canHandleChecker = checker
    }
    
    /**
     * Agent 빌드
     */
    internal fun build(): Agent {
        return DSLAgent(
            id = id,
            name = name,
            description = description,
            capabilities = capabilities.toList(),
            tools = tools.toList(),
            messageProcessor = messageProcessor,
            canHandleChecker = canHandleChecker
        )
    }
}

/**
 * Tools 빌더
 */
class ToolsBuilder {
    private val tools = mutableListOf<Tool>()
    
    /**
     * 기존 Tool 추가
     */
    fun add(tool: Tool) {
        tools.add(tool)
    }
    
    /**
     * 커스텀 Tool 생성
     */
    fun custom(name: String, init: CustomToolBuilder.() -> Unit) {
        val toolBuilder = CustomToolBuilder(name)
        toolBuilder.init()
        tools.add(toolBuilder.build())
    }
    
    internal fun getTools(): List<Tool> = tools.toList()
}

/**
 * 커스텀 Tool 빌더
 */
class CustomToolBuilder(private val name: String) {
    var description: String = "Custom tool created with DSL"
    private val parameters = mutableMapOf<String, ParameterSchema>()
    private var executor: (suspend (Map<String, Any>) -> ToolResult)? = null
    private var canExecuteChecker: ((Map<String, Any>) -> Boolean)? = null
    
    /**
     * 파라미터 추가
     */
    fun parameter(
        name: String,
        type: String,
        description: String,
        required: Boolean = false,
        default: String? = null
    ) {
        parameters[name] = ParameterSchema(
            type = type,
            description = description,
            required = required
        )
    }
    
    /**
     * 실행 로직 정의
     */
    fun execute(executor: suspend (Map<String, Any>) -> ToolResult) {
        this.executor = executor
    }
    
    /**
     * canExecute 조건 설정
     */
    fun canExecute(checker: (Map<String, Any>) -> Boolean) {
        canExecuteChecker = checker
    }
    
    /**
     * Tool 빌드
     */
    internal fun build(): Tool {
        val finalExecutor = executor ?: { ToolResult.success("Tool executed: $name") }
        val finalCanExecute = canExecuteChecker ?: { true }
        
        return DSLTool(
            name = name,
            description = description,
            schema = ToolSchema(
                name = name,
                description = description,
                parameters = parameters.toMap()
            ),
            executor = finalExecutor,
            canExecuteChecker = finalCanExecute
        )
    }
}

/**
 * DSL로 생성된 Agent 구현체
 */
private class DSLAgent(
    override val id: String,
    override val name: String,
    override val description: String,
    override val capabilities: List<String>,
    private val tools: List<Tool>,
    private val messageProcessor: (suspend (Message) -> Message)?,
    private val canHandleChecker: ((Message) -> Boolean)?
) : Agent {
    
    override suspend fun processMessage(message: Message): Message {
        return if (messageProcessor != null) {
            try {
                messageProcessor.invoke(message)
            } catch (e: Exception) {
                message.createReply(
                    content = "Processing error: ${e.message}",
                    sender = id,
                    type = MessageType.ERROR
                )
            }
        } else {
            // Default message processing
            message.createReply(
                content = "Processed by $name: ${message.content}",
                sender = id,
                type = MessageType.TEXT,
                metadata = mapOf("processedBy" to id)
            )
        }
    }
    
    override fun canHandle(message: Message): Boolean {
        return canHandleChecker?.invoke(message) ?: when (message.type) {
            MessageType.TEXT, MessageType.PROMPT, MessageType.SYSTEM -> true
            MessageType.TOOL_CALL -> tools.any { it.name == message.metadata["toolName"] }
            else -> false
        }
    }
    
    override fun getTools(): List<Tool> = tools
    
    override fun isReady(): Boolean = true
}

/**
 * DSL로 생성된 Tool 구현체
 */
private class DSLTool(
    override val name: String,
    override val description: String,
    override val schema: ToolSchema,
    private val executor: suspend (Map<String, Any>) -> ToolResult,
    private val canExecuteChecker: (Map<String, Any>) -> Boolean
) : Tool {
    
    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
        return try {
            executor(parameters)
        } catch (e: Exception) {
            ToolResult.error("Tool execution failed: ${e.message}")
        }
    }
    
    override fun canExecute(parameters: Map<String, Any>): Boolean {
        return canExecuteChecker(parameters)
    }
}

// ========== 편의 함수들 ==========

/**
 * 간단한 텍스트 처리 Agent
 */
fun textProcessingAgent(
    id: String = "text-processor",
    name: String = "Text Processing Agent",
    processor: (String) -> String = { "Processed: $it" }
): Agent = buildAgent {
    this.id = id
    this.name = name
    description = "Simple text processing agent"
    
    capabilities {
        add("text_processing")
    }
    
    messageHandler { message ->
        val processed = processor(message.content)
        message.createReply(processed, this@buildAgent.id)
    }
}

/**
 * API 호출 Agent
 */
fun apiAgent(
    id: String = "api-agent",
    name: String = "API Agent",
    baseUrl: String
): Agent = buildAgent {
    this.id = id
    this.name = name
    description = "Agent for API calls to $baseUrl"
    
    capabilities {
        add("api_calls")
        add("http_requests")
    }
    
    tools {
        custom("api_call") {
            description = "Make HTTP API calls"
            parameter("endpoint", "string", "API endpoint", required = true)
            parameter("method", "string", "HTTP method", required = false)
            parameter("body", "string", "Request body", required = false)
            
            execute { params ->
                val endpoint = params["endpoint"] as String
                val method = params["method"] as? String ?: "GET"
                val body = params["body"] as? String
                
                // Mock API call
                val response = "API Response from $baseUrl$endpoint (Method: $method)"
                ToolResult.success(response, mapOf("endpoint" to endpoint, "method" to method))
            }
        }
    }
    
    messageHandler { message ->
        when (message.type) {
            MessageType.TEXT -> {
                if (message.content.contains("call api", ignoreCase = true)) {
                    message.createReply(
                        "API call initiated. Use api_call tool for specific requests.",
                        this@buildAgent.id,
                        MessageType.TOOL_CALL,
                        mapOf("toolName" to "api_call")
                    )
                } else {
                    message.createReply("Ready for API calls: ${message.content}", this@buildAgent.id)
                }
            }
            else -> message.createReply("API Agent processed: ${message.content}", this@buildAgent.id)
        }
    }
}

/**
 * 조건부 라우팅 Agent
 */
fun routingAgent(
    id: String = "router",
    name: String = "Routing Agent",
    routes: Map<String, String> = emptyMap()
): Agent = buildAgent {
    this.id = id
    this.name = name
    description = "Agent that routes messages based on content patterns"
    
    capabilities {
        add("message_routing")
        add("pattern_matching")
    }
    
    messageHandler { message ->
        val route = routes.entries.find { (pattern, _) ->
            message.content.contains(pattern, ignoreCase = true)
        }
        
        if (route != null) {
            message.createReply(
                "Routing to: ${route.value}",
                this@buildAgent.id,
                MessageType.SYSTEM,
                mapOf("route" to route.value, "pattern" to route.key)
            )
        } else {
            message.createReply(
                "No route found for: ${message.content}",
                this@buildAgent.id,
                MessageType.TEXT
            )
        }
    }
} 