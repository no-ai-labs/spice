package io.github.spice

/**
 * 🌶️ Spice Agent Engine
 * 
 * Central orchestrator responsible for message routing, agent selection, and execution context management
 */
class AgentEngine(
    private val agents: kotlin.collections.List<Agent>,
    private val messageRouter: MessageRouter = MessageRouter()
) {
    
    private val executionContexts = java.util.concurrent.ConcurrentHashMap<kotlin.String, ExecutionContext>()
    
    /**
     * Process a message through the agent engine
     */
    fun processMessage(message: Message): AgentEngineResult {
        val context = getOrCreateExecutionContext(message)
        
        try {
            // Route message through MessageRouter
            val routedMessages = messageRouter.route(message)
            val routedMessage = if (routedMessages.isNotEmpty()) routedMessages[0] else message
            
            // Select appropriate Agent
            val selectedAgent = selectAgent(routedMessage, context)
                ?: return AgentEngineResult.error("No suitable agent found for message: ${message.type}")
            
            // Process message with selected Agent
            val response = selectedAgent.receive(routedMessage)
            
            // Update execution context
            context.addMessage(message)
            context.addMessage(response)
            
            return AgentEngineResult.success(response)
            
        } catch (e: Exception) {
            return AgentEngineResult.error("Agent processing failed: ${e.message}")
        }
    }
    
    /**
     * Select the most appropriate agent for a message
     */
    private fun selectAgent(message: Message, context: ExecutionContext): Agent? {
        // Simple agent selection based on message type
        for (agent in agents) {
            when (message.type) {
                MessageType.TEXT -> return agent
                MessageType.SYSTEM -> return agent
                MessageType.ERROR -> return agent
                MessageType.TOOL_CALL -> if (agent.id.contains("tool")) return agent
                MessageType.TOOL_RESULT -> if (agent.id.contains("tool")) return agent
                else -> return agent
            }
        }
        return null
    }
    
    /**
     * Get or create execution context for a message
     */
    private fun getOrCreateExecutionContext(message: Message): ExecutionContext {
        val contextKey = message.sender
        return executionContexts.computeIfAbsent(contextKey) { 
            ExecutionContext(contextKey)
        }
    }
    
    /**
     * Get engine statistics
     */
    fun getStatistics(): kotlin.collections.Map<kotlin.String, kotlin.Any> {
        return mapOf(
            "total_agents" to agents.size,
            "active_contexts" to executionContexts.size,
            "routing_rules" to messageRouter.rules.size
        )
    }
    
    /**
     * Clear execution contexts
     */
    fun clearContexts() {
        executionContexts.clear()
    }
    
    /**
     * Get active agents
     */
    fun getActiveAgents(): kotlin.collections.List<Agent> = agents
}

/**
 * 📊 Execution Context
 */
data class ExecutionContext(
    val id: kotlin.String
) {
    private val messages = java.util.ArrayList<Message>()
    
    fun addMessage(message: Message) {
        messages.add(message)
    }
    
    fun getMessages(): kotlin.collections.List<Message> = messages.toList()
    
    fun getMessageCount(): kotlin.Int = messages.size
    
    fun getLastMessage(): Message? = if (messages.isEmpty()) null else messages[messages.size - 1]
}

/**
 * 📊 Agent Engine Result
 */
sealed class AgentEngineResult {
    data class Success(val message: Message) : AgentEngineResult()
    data class Error(val error: kotlin.String) : AgentEngineResult()
    
    companion object {
        fun success(message: Message) = Success(message)
        fun error(error: kotlin.String) = Error(error)
    }
    
    fun isSuccess(): kotlin.Boolean = this is Success
    fun isError(): kotlin.Boolean = this is Error
    
    fun getMessageOrNull(): Message? = if (this is Success) message else null
    fun getErrorOrNull(): kotlin.String? = if (this is Error) error else null
} 