package io.github.spice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

/**
 * 🌶️ Core AgentEngine of Spice Framework
 * Central orchestrator responsible for message routing, agent selection, and execution context management
 */
class AgentEngine {
    
    private val agents = ConcurrentHashMap<String, Agent>()
    private val executionContexts = ConcurrentHashMap<String, ExecutionContext>()
    private val messageRouter = MessageRouter.createSpiceFlowRules()
    private val toolRunner = ToolRunner()
    
    /**
     * 🧠 Register Agent
     */
    fun registerAgent(agent: Agent) {
        agents[agent.id] = agent
        
        // Register Agent's Tools to ToolRunner
        agent.getTools().forEach { tool ->
            toolRunner.registerTool(tool)
        }
        
        println("🌶️ Agent registered: ${agent.id} (${agent.name})")
    }
    
    /**
     * 💬 Message reception and processing - Core method
     */
    suspend fun receive(message: Message): AgentMessage {
        return try {
            // 1. Message validation
            validateMessage(message)
            
            // 2. Create/retrieve execution context
            val context = getOrCreateExecutionContext(message)
            
            // 3. Agent selection
            val targetAgent = selectAgent(message, context)
            
            // 4. Apply message routing
            val routedMessages = messageRouter.route(message)
            val processedMessage = routedMessages.firstOrNull() ?: message
            
            // 5. Agent execution
            val agentResponse = executeAgent(targetAgent, processedMessage, context)
            
            // 6. Tool request handling
            val finalResponse = if (agentResponse.type == MessageType.TOOL_CALL) {
                handleToolRequest(agentResponse, targetAgent, context)
            } else {
                agentResponse
            }
            
            // 7. Update execution context
            updateExecutionContext(context, processedMessage, finalResponse)
            
            // 8. Convert to AgentMessage and return
            AgentMessage(
                success = true,
                response = finalResponse,
                agentId = targetAgent.id,
                agentName = targetAgent.name,
                executionTime = System.currentTimeMillis() - context.startTime,
                metadata = mapOf<String, String>(
                    "messageId" to finalResponse.id,
                    "conversationId" to (finalResponse.conversationId ?: ""),
                    "routingApplied" to (routedMessages.size > 1).toString()
                )
            )
            
        } catch (e: Exception) {
            AgentMessage(
                success = false,
                response = Message(
                    content = "AgentEngine processing failed: ${e.message}",
                    type = MessageType.ERROR,
                    sender = "agent-engine"
                ),
                agentId = "agent-engine",
                agentName = "AgentEngine",
                executionTime = 0,
                error = e.message,
                metadata = mapOf<String, String>("errorType" to "engine_error")
            )
        }
    }
    
    /**
     * 🔁 Streaming message processing (for workflow execution)
     */
    fun processWorkflow(initialMessage: Message): Flow<AgentMessage> = flow {
        val context = getOrCreateExecutionContext(initialMessage)
        var currentMessage = initialMessage
        var iterationCount = 0
        val maxIterations = 50 // Prevent infinite loops
        
        while (iterationCount < maxIterations && !context.isTerminated) {
            val agentMessage = receive(currentMessage)
            emit(agentMessage)
            
            // Check termination conditions
            if (shouldTerminate(agentMessage, context)) {
                context.isTerminated = true
                break
            }
            
            // Generate next message (cycle support)
            currentMessage = generateNextMessage(agentMessage, context)
            iterationCount++
        }
        
        if (iterationCount >= maxIterations) {
            emit(AgentMessage(
                success = false,
                response = Message(
                    content = "Maximum iterations exceeded (${maxIterations})",
                    type = MessageType.ERROR,
                    sender = "agent-engine"
                ),
                agentId = "agent-engine",
                agentName = "AgentEngine",
                executionTime = 0,
                error = "Max iterations exceeded",
                metadata = mapOf<String, String>("terminationReason" to "max_iterations")
            ))
        }
    }
    
    /**
     * 🚦 Message validation
     */
    private fun validateMessage(message: Message) {
        if (message.content.isBlank()) {
            throw IllegalArgumentException("Message content is empty")
        }
        
        if (message.sender.isBlank()) {
            throw IllegalArgumentException("Message sender is not specified")
        }
    }
    
    /**
     * 🧠 Agent selection logic
     */
    private fun selectAgent(message: Message, context: ExecutionContext): Agent {
        // 1. When receiver is explicitly specified
        message.receiver?.let { receiverId ->
            agents[receiverId]?.let { return it }
        }
        
        // 2. Default Agent selection by message type
        val candidateAgents = agents.values.filter { agent ->
            agent.canHandle(message)
        }
        
        if (candidateAgents.isEmpty()) {
            throw IllegalStateException("No Agent available to handle this message: ${message.type}")
        }
        
        // 3. Select most suitable Agent (priority: capability matching)
        return candidateAgents.maxByOrNull { agent ->
            calculateAgentScore(agent, message, context)
        } ?: candidateAgents.first()
    }
    
    /**
     * 🔢 Agent score calculation (selection priority)
     */
    private fun calculateAgentScore(agent: Agent, message: Message, context: ExecutionContext): Int {
        var score = 0
        
        // Message type matching
        when (message.type) {
            MessageType.PROMPT -> if (agent is PromptAgent) score += 10
            MessageType.DATA -> if (agent is DataAgent) score += 10
            MessageType.RESULT -> if (agent is ResultAgent) score += 10
            MessageType.BRANCH -> if (agent is BranchAgent) score += 10
            MessageType.MERGE -> if (agent is MergeAgent) score += 10
            MessageType.TOOL_CALL -> if (agent.getTools().isNotEmpty()) score += 8
            else -> score += 1
        }
        
        // Capability matching
        message.metadata["requiredCapability"]?.let { capability ->
            if (agent.capabilities.contains(capability)) score += 5
        }
        
        // Recently used Agent priority (context continuity)
        if (context.lastAgentId == agent.id) score += 3
        
        return score
    }
    
    /**
     * 🏃 Agent execution
     */
    private suspend fun executeAgent(agent: Agent, message: Message, context: ExecutionContext): Message {
        context.lastAgentId = agent.id
        context.messageHistory.add(message)
        
        return agent.processMessage(message)
    }
    
    /**
     * 🛠 Tool request handling
     */
    private suspend fun handleToolRequest(toolMessage: Message, agent: Agent, context: ExecutionContext): Message {
        val toolName = toolMessage.metadata["toolName"]
        if (toolName.isNullOrBlank()) {
            return toolMessage.createReply(
                content = "Tool name is not specified",
                sender = "agent-engine",
                type = MessageType.ERROR
            )
        }
        
        val parameters = extractToolParameters(toolMessage)
        
        return try {
            val toolResult = toolRunner.executeTool(toolName, parameters)
            
            toolMessage.createReply(
                content = if (toolResult.success) toolResult.result else toolResult.error,
                sender = "tool-runner",
                type = if (toolResult.success) MessageType.TOOL_RESULT else MessageType.ERROR,
                metadata = mapOf<String, String>(
                    "toolName" to toolName,
                    "toolSuccess" to toolResult.success.toString()
                ) + toolResult.metadata
            )
        } catch (e: Exception) {
            toolMessage.createReply(
                content = "Tool execution failed: ${e.message}",
                sender = "agent-engine",
                type = MessageType.ERROR,
                metadata = mapOf<String, String>(
                    "toolName" to toolName,
                    "errorType" to "tool_execution_error"
                )
            )
        }
    }
    
    /**
     * 📤 Tool parameter extraction
     */
    private fun extractToolParameters(message: Message): Map<String, Any> {
        return message.metadata.filterKeys { key ->
            key.startsWith("param_")
        }.mapKeys { (key, _) ->
            key.removePrefix("param_")
        }.mapValues { (_, value) -> value as Any }
    }
    
    /**
     * 🏁 Termination condition check
     */
    private fun shouldTerminate(agentMessage: AgentMessage, context: ExecutionContext): Boolean {
        val response = agentMessage.response
        
        // 1. Explicit termination message
        if (response.type == MessageType.WORKFLOW_END) return true
        
        // 2. Terminate on error
        if (!agentMessage.success) return true
        
        // 3. Contains "TERMINATE" keyword
        if (response.content.contains("TERMINATE", ignoreCase = true)) return true
        
        // 4. Final result from ResultAgent
        if (response.type == MessageType.RESULT && 
            response.metadata["resultType"] == "final") return true
        
        // 5. Maximum message count reached
        if (context.messageHistory.size >= 20) return true
        
        return false
    }
    
    /**
     * ➡️ Next message generation (cycle support)
     */
    private fun generateNextMessage(agentMessage: AgentMessage, context: ExecutionContext): Message {
        val response = agentMessage.response
        
        // Cycle detection and handling
        val cycleTarget = detectCycle(response, context)
        if (cycleTarget != null) {
            return response.copy(
                receiver = cycleTarget,
                metadata = response.metadata + ("cycleDetected" to "true")
            )
        }
        
        // Default next message generation
        return response.copy(
            sender = response.receiver ?: "system",
            receiver = null // Auto-selection
        )
    }
    
    /**
     * 🔄 Cycle detection
     */
    private fun detectCycle(message: Message, context: ExecutionContext): String? {
        // Simple cycle detection logic
        val recentAgents = context.messageHistory.takeLast(5).map { it.sender }
        
        // Feedback loop pattern detection
        if (message.metadata["feedbackRequired"] == "true") {
            return recentAgents.firstOrNull()
        }
        
        return null
    }
    
    /**
     * 📝 Create/retrieve execution context
     */
    private fun getOrCreateExecutionContext(message: Message): ExecutionContext {
        val contextId = message.conversationId ?: message.id
        
        return executionContexts.getOrPut(contextId) {
            ExecutionContext(
                id = contextId,
                startTime = System.currentTimeMillis(),
                messageHistory = mutableListOf(),
                isTerminated = false,
                lastAgentId = null
            )
        }
    }
    
    /**
     * 🔄 Update execution context
     */
    private fun updateExecutionContext(context: ExecutionContext, request: Message, response: Message) {
        context.messageHistory.add(response)
        
        // Memory management: maintain maximum 50 messages
        if (context.messageHistory.size > 50) {
            context.messageHistory.removeAt(0)
        }
    }
    
    /**
     * 📊 Get engine status
     */
    fun getEngineStatus(): EngineStatus {
        return EngineStatus(
            registeredAgents = agents.size,
            activeContexts = executionContexts.size,
            registeredTools = toolRunner.getRegisteredToolCount(),
            agentList = agents.values.map { agent ->
                AgentInfo(
                    id = agent.id,
                    name = agent.name,
                    type = agent::class.simpleName ?: "Unknown",
                    capabilities = agent.capabilities,
                    toolCount = agent.getTools().size,
                    isReady = agent.isReady()
                )
            }
        )
    }
    
    /**
     * 🧹 Cleanup expired contexts
     */
    fun cleanupExpiredContexts(maxAgeMs: Long = 3600000) { // 1 hour
        val currentTime = System.currentTimeMillis()
        val expiredContexts = executionContexts.filterValues { context ->
            currentTime - context.startTime > maxAgeMs
        }
        
        expiredContexts.keys.forEach { contextId ->
            executionContexts.remove(contextId)
        }
        
        if (expiredContexts.isNotEmpty()) {
            println("🌶️ Cleaned up ${expiredContexts.size} expired execution contexts")
        }
    }
}

/**
 * 🏃 Execution context
 */
data class ExecutionContext(
    val id: String,
    val startTime: Long,
    val messageHistory: MutableList<Message>,
    var isTerminated: Boolean,
    var lastAgentId: String?
)

/**
 * 📤 Agent response message
 */
data class AgentMessage(
    val success: Boolean,
    val response: Message,
    val agentId: String,
    val agentName: String,
    val executionTime: Long,
    val error: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * 📊 Engine status
 */
data class EngineStatus(
    val registeredAgents: Int,
    val activeContexts: Int,
    val registeredTools: Int,
    val agentList: List<AgentInfo>
)

/**
 * 🤖 Agent information
 */
data class AgentInfo(
    val id: String,
    val name: String,
    val type: String,
    val capabilities: List<String>,
    val toolCount: Int,
    val isReady: Boolean
)

/**
 * 🛠 Tool runner
 */
class ToolRunner {
    private val tools = ConcurrentHashMap<String, Tool>()
    
    fun registerTool(tool: Tool) {
        tools[tool.name] = tool
        println("🔧 Tool registered: ${tool.name}")
    }
    
    suspend fun executeTool(toolName: String, parameters: Map<String, Any>): ToolResult {
        val tool = tools[toolName]
            ?: return ToolResult.error("Tool not found: $toolName")
        
        return if (tool.canExecute(parameters)) {
            tool.execute(parameters)
        } else {
            ToolResult.error("Tool execution conditions not met: $toolName")
        }
    }
    
    fun getRegisteredToolCount(): Int = tools.size
    
    fun getRegisteredTools(): List<String> = tools.keys.toList()
} 