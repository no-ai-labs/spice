package io.github.spice.toolhub

import io.github.spice.*

/**
 * 🤖 ToolHubAgent - ToolHub를 사용하는 Agent 확장
 * 
 * 기존 Agent에 ToolHub 기능을 추가하는 데코레이터 패턴 구현체입니다.
 * 
 * 사용 예시:
 * ```kotlin
 * val toolHub = staticToolHub {
 *     addTool(WebSearchTool())
 *     addTool(FileReadTool())
 * }
 * 
 * val agent = VertexAgent(...)
 * val toolHubAgent = agent.withToolHub(toolHub)
 * ```
 */
class ToolHubAgent(
    private val baseAgent: Agent,
    private val toolHub: ToolHub
) : Agent by baseAgent {
    
    private val toolContext = ToolContext()
    
    /**
     * 🔧 ToolHub의 도구들을 Agent의 도구 목록에 추가
     */
    override fun getTools(): List<Tool> {
        return baseAgent.getTools() + runCatching {
            kotlinx.coroutines.runBlocking { toolHub.listTools() }
        }.getOrElse { emptyList() }
    }
    
    /**
     * 💬 메시지 처리 시 ToolHub를 통한 도구 호출 지원
     */
    override suspend fun processMessage(message: Message): Message {
        return when (message.type) {
            MessageType.TOOL_CALL -> {
                val toolName = message.metadata["toolName"] as? String
                if (toolName != null) {
                    handleToolCall(toolName, message)
                } else {
                    baseAgent.processMessage(message)
                }
            }
            else -> baseAgent.processMessage(message)
        }
    }
    
    /**
     * 🛠️ ToolHub를 통한 도구 호출 처리
     */
    private suspend fun handleToolCall(toolName: String, message: Message): Message {
        val parameters = extractToolParameters(message)
        
        return try {
            val result = toolHub.callTool(toolName, parameters, toolContext)
            
            message.createReply(
                content = if (result.success) {
                    when (result) {
                        is ToolResult.Success -> result.output.toString()
                        else -> result.toString()
                    }
                } else {
                    when (result) {
                        is ToolResult.Error -> result.message
                        is ToolResult.Retry -> "Retry requested: ${result.reason}"
                        else -> "Tool execution failed"
                    }
                },
                sender = id,
                type = if (result.success) MessageType.TOOL_RESULT else MessageType.ERROR,
                metadata = mapOf<String, String>(
                    "toolName" to toolName,
                    "toolSuccess" to result.success.toString(),
                    "executionTime" to (toolContext.getLastResult()?.metadata?.get("executionTime")?.toString() ?: "0")
                ) + result.metadata.mapValues { it.value.toString() }
            )
            
        } catch (e: Exception) {
            message.createReply(
                content = "ToolHub execution failed: ${e.message}",
                sender = id,
                type = MessageType.ERROR,
                metadata = mapOf<String, String>(
                    "toolName" to toolName,
                    "errorType" to "toolhub_error"
                )
            )
        }
    }
    
    /**
     * 📤 메시지에서 도구 파라미터 추출
     */
    private fun extractToolParameters(message: Message): Map<String, Any> {
        return message.metadata.filterKeys { key ->
            key.startsWith("param_")
        }.mapKeys { (key, _) ->
            key.removePrefix("param_")
        }.mapValues { (_, value) -> value as Any }
    }
    
    /**
     * 🔍 ToolHub 도구 포함 여부 확인
     */
    override fun canHandle(message: Message): Boolean {
        if (baseAgent.canHandle(message)) return true
        
        // ToolHub의 도구 호출 가능 여부 확인
        if (message.type == MessageType.TOOL_CALL) {
            val toolName = message.metadata["toolName"] as? String
            if (toolName != null) {
                return runCatching {
                    kotlinx.coroutines.runBlocking { 
                        toolHub.listTools().any { it.name == toolName }
                    }
                }.getOrElse { false }
            }
        }
        
        return false
    }
    
    /**
     * 📊 도구 실행 통계 조회
     */
    fun getToolExecutionStats(): Map<String, Any> {
        return if (toolHub is StaticToolHub) {
            toolHub.getExecutionStats(toolContext)
        } else {
            mapOf(
                "total_executions" to toolContext.callHistory.size,
                "execution_history" to toolContext.callHistory.map { it.getSummary() }
            )
        }
    }
    
    /**
     * 🗂️ 도구 컨텍스트 조회
     */
    fun getToolContext(): ToolContext = toolContext
    
    /**
     * 🧰 ToolHub 조회
     */
    fun getToolHub(): ToolHub = toolHub
}

/**
 * 🎯 Agent 확장 함수 - ToolHub 통합
 */
suspend fun Agent.withToolHub(toolHub: ToolHub): ToolHubAgent {
    // ToolHub 시작 (아직 시작되지 않았다면)
    if (toolHub is StaticToolHub && !toolHub.isStarted()) {
        toolHub.start()
    }
    
    return ToolHubAgent(this, toolHub)
}

/**
 * 🔧 BaseAgent 확장 함수 - ToolHub 통합 (더 편리한 사용)
 */
suspend fun BaseAgent.withToolHub(toolHub: ToolHub): ToolHubAgent {
    return (this as Agent).withToolHub(toolHub)
}

/**
 * 🎯 ToolHub 전용 Agent 생성 함수
 */
suspend fun createToolHubAgent(
    id: String,
    name: String,
    description: String,
    toolHub: ToolHub,
    capabilities: List<String> = emptyList(),
    messageHandler: (suspend (Message) -> Message)? = null
): ToolHubAgent {
    val baseAgent = object : BaseAgent(id, name, description, capabilities) {
        override suspend fun processMessage(message: Message): Message {
            return messageHandler?.invoke(message) ?: message.createReply(
                content = "Processed by ToolHub Agent: ${message.content}",
                sender = id,
                type = MessageType.TEXT
            )
        }
    }
    
    return baseAgent.withToolHub(toolHub)
}

/**
 * 🔧 ToolHub 전용 Agent DSL
 */
suspend fun toolHubAgent(
    id: String,
    name: String,
    description: String,
    toolHub: ToolHub,
    init: ToolHubAgentBuilder.() -> Unit = {}
): ToolHubAgent {
    val builder = ToolHubAgentBuilder(id, name, description, toolHub)
    builder.init()
    return builder.build()
}

/**
 * 🏗️ ToolHub Agent 빌더
 */
class ToolHubAgentBuilder(
    private val id: String,
    private val name: String,
    private val description: String,
    private val toolHub: ToolHub
) {
    private var capabilities: List<String> = emptyList()
    private var messageHandler: (suspend (Message) -> Message)? = null
    
    fun capabilities(vararg caps: String) {
        capabilities = caps.toList()
    }
    
    fun messageHandler(handler: suspend (Message) -> Message) {
        messageHandler = handler
    }
    
    suspend fun build(): ToolHubAgent {
        return createToolHubAgent(id, name, description, toolHub, capabilities, messageHandler)
    }
} 