package io.github.spice.dsl

import io.github.spice.*

/**
 * 🌶️ Spice Core DSL
 * 
 * Simple and intuitive DSL for Agent > Flow > Tool structure.
 * Complex features are moved to experimental extensions.
 */

// =====================================
// AGENT DSL
// =====================================

/**
 * Core agent builder
 */
class CoreAgentBuilder {
    var id: String = "agent-${System.currentTimeMillis()}"
    var name: String = ""
    var description: String = ""
    private var handler: (suspend (Message) -> Message)? = null
    private val tools = mutableListOf<String>()
    private var debugEnabled: Boolean = false
    private var debugPrefix: String = "[DEBUG]"
    
    /**
     * Enable debug mode with automatic logging
     */
    fun debugMode(enabled: Boolean = true, prefix: String = "[DEBUG]") {
        this.debugEnabled = enabled
        this.debugPrefix = prefix
    }
    
    /**
     * Set message handler
     */
    fun handle(handler: suspend (Message) -> Message) {
        if (debugEnabled) {
            // Wrap handler with debug logging
            this.handler = { message ->
                println("$debugPrefix Agent '$name' ($id) received message:")
                println("$debugPrefix   From: ${message.sender}")
                println("$debugPrefix   Content: ${message.content}")
                println("$debugPrefix   Metadata: ${message.metadata}")
                
                val startTime = System.currentTimeMillis()
                val result = handler(message)
                val endTime = System.currentTimeMillis()
                
                println("$debugPrefix Agent '$name' ($id) response:")
                println("$debugPrefix   To: ${result.receiver}")
                println("$debugPrefix   Content: ${result.content}")
                println("$debugPrefix   Metadata: ${result.metadata}")
                println("$debugPrefix   Processing time: ${endTime - startTime}ms")
                println("$debugPrefix   ---")
                
                result
            }
        } else {
            this.handler = handler
        }
    }
    
    /**
     * Add tool by name
     */
    fun tool(toolName: String) {
        tools.add(toolName)
        if (debugEnabled) {
            println("$debugPrefix Agent '$name' added tool: $toolName")
        }
    }
    
    /**
     * Add multiple tools
     */
    fun tools(vararg toolNames: String) {
        tools.addAll(toolNames)
        if (debugEnabled) {
            println("$debugPrefix Agent '$name' added tools: ${toolNames.joinToString(", ")}")
        }
    }
    
    internal fun build(): Agent {
        require(name.isNotEmpty()) { "Agent name is required" }
        require(handler != null) { "Message handler is required" }
        
        if (debugEnabled) {
            println("$debugPrefix Building agent '$name' ($id) with ${tools.size} tools")
        }
        
        return CoreAgent(
            id = id,
            name = name,
            description = description,
            handler = handler!!,
            toolNames = tools.toList(),
            debugInfo = if (debugEnabled) DebugInfo(debugEnabled, debugPrefix) else null
        )
    }
}

/**
 * Debug information for agents
 */
data class DebugInfo(
    val enabled: Boolean,
    val prefix: String
)

/**
 * Simple agent implementation
 */
class CoreAgent(
    override val id: String,
    override val name: String,
    override val description: String,
    private val handler: suspend (Message) -> Message,
    private val toolNames: List<String>,
    private val debugInfo: DebugInfo? = null
) : Agent {
    
    override val capabilities: List<String> = listOf("core-processing")
    
    override suspend fun processMessage(message: Message): Message {
        return handler(message)
    }
    
    override fun canHandle(message: Message): Boolean = true
    
    override fun getTools(): List<Tool> {
        val tools = toolNames.mapNotNull { ToolRegistry.getTool(it) }
        if (debugInfo?.enabled == true) {
            println("${debugInfo.prefix} Agent '$name' retrieved ${tools.size} tools: ${tools.map { it.name }}")
        }
        return tools
    }
    
    override fun isReady(): Boolean = true
    
    /**
     * Check if this agent has debug mode enabled
     */
    fun isDebugEnabled(): Boolean = debugInfo?.enabled ?: false
    
    /**
     * Get debug information
     */
    fun getDebugInfo(): DebugInfo? = debugInfo
}

// =====================================
// FLOW DSL
// =====================================

/**
 * Flow step definition
 */
data class FlowStep(
    val id: String,
    val agentId: String,
    val condition: (suspend (Message) -> Boolean)? = null
)

/**
 * Core flow builder
 */
class CoreFlowBuilder {
    var id: String = "flow-${System.currentTimeMillis()}"
    var name: String = ""
    var description: String = ""
    private val steps = mutableListOf<FlowStep>()
    
    /**
     * Add step with agent
     */
    fun step(stepId: String, agentId: String, condition: (suspend (Message) -> Boolean)? = null) {
        steps.add(FlowStep(stepId, agentId, condition))
    }
    
    /**
     * Add step with agent (simple version)
     */
    fun step(agentId: String) {
        step("step-${steps.size + 1}", agentId)
    }
    
    internal fun build(): CoreFlow {
        require(name.isNotEmpty()) { "Flow name is required" }
        require(steps.isNotEmpty()) { "At least one step is required" }
        
        return CoreFlow(
            id = id,
            name = name,
            description = description,
            steps = steps.toList()
        )
    }
}

/**
 * Simple flow implementation
 */
class CoreFlow(
    val id: String,
    val name: String,
    val description: String,
    val steps: List<FlowStep>
) {
    
    /**
     * Execute flow with message
     */
    suspend fun execute(message: Message): Message {
        var currentMessage = message.copy(
            metadata = message.metadata + ("flow_id" to id)
        )
        
        for (step in steps) {
            // Check condition if exists
            if (step.condition != null && !step.condition.invoke(currentMessage)) {
                continue
            }
            
            // Get agent from registry
            val agent = AgentRegistry.getAgent(step.agentId)
                ?: return currentMessage.createReply(
                    content = "Agent not found: ${step.agentId}",
                    sender = "flow-executor",
                    type = MessageType.ERROR
                )
            
            // Process message
            currentMessage = agent.processMessage(currentMessage)
            
            // Stop on error
            if (currentMessage.type == MessageType.ERROR) {
                break
            }
        }
        
        return currentMessage
    }
}

// =====================================
// TOOL DSL
// =====================================

/**
 * Core tool builder
 */
class CoreToolBuilder(private val name: String) {
    var description: String = ""
    private val parameters = mutableMapOf<String, String>()
    private var executor: (suspend (Map<String, Any>) -> ToolResult)? = null
    
    /**
     * Add parameter
     */
    fun param(name: String, type: String = "string") {
        parameters[name] = type
    }
    
    /**
     * Set executor function
     */
    fun execute(executor: suspend (Map<String, Any>) -> ToolResult) {
        this.executor = executor
    }
    
    internal fun build(): Tool {
        require(executor != null) { "Tool executor is required" }
        
        return CoreTool(
            name = name,
            description = description,
            parameters = parameters.toMap(),
            executor = executor!!
        )
    }
}

/**
 * Simple tool implementation
 */
class CoreTool(
    override val name: String,
    override val description: String,
    private val parameters: Map<String, String>,
    private val executor: suspend (Map<String, Any>) -> ToolResult
) : Tool {
    
    override val schema: ToolSchema = ToolSchema(
        name = name,
        description = description,
        parameters = parameters.mapValues { (_, type) ->
            ParameterSchema(type = type, description = "", required = true)
        }
    )
    
    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
        return executor(parameters)
    }
}

// =====================================
// REGISTRIES
// =====================================

/**
 * Simple agent registry
 */
object AgentRegistry {
    private val agents = mutableMapOf<String, Agent>()
    
    fun register(agent: Agent) {
        agents[agent.id] = agent
    }
    
    fun getAgent(id: String): Agent? = agents[id]
    
    fun getAllAgents(): List<Agent> = agents.values.toList()
    
    fun clear() = agents.clear()
}

/**
 * Simple tool registry
 */
object ToolRegistry {
    private val tools = mutableMapOf<String, Tool>()
    
    fun register(tool: Tool) {
        tools[tool.name] = tool
    }
    
    fun getTool(name: String): Tool? = tools[name]
    
    fun getAllTools(): List<Tool> = tools.values.toList()
    
    fun clear() = tools.clear()
}

/**
 * Simple flow registry
 */
object FlowRegistry {
    private val flows = mutableMapOf<String, CoreFlow>()
    
    fun register(flow: CoreFlow) {
        flows[flow.id] = flow
    }
    
    fun getFlow(id: String): CoreFlow? = flows[id]
    
    fun getAllFlows(): List<CoreFlow> = flows.values.toList()
    
    fun clear() = flows.clear()
}

// =====================================
// DSL ENTRY POINTS
// =====================================

/**
 * Build agent with DSL
 */
fun buildAgent(init: CoreAgentBuilder.() -> Unit): Agent {
    val builder = CoreAgentBuilder()
    builder.init()
    val agent = builder.build()
    AgentRegistry.register(agent)
    return agent
}

/**
 * Build flow with DSL
 */
fun flow(init: CoreFlowBuilder.() -> Unit): CoreFlow {
    val builder = CoreFlowBuilder()
    builder.init()
    val flow = builder.build()
    FlowRegistry.register(flow)
    return flow
}

/**
 * Build tool with DSL
 */
fun tool(name: String, init: CoreToolBuilder.() -> Unit): Tool {
    val builder = CoreToolBuilder(name)
    builder.init()
    val tool = builder.build()
    ToolRegistry.register(tool)
    return tool
}

// =====================================
// CONVENIENCE FUNCTIONS
// =====================================

/**
 * Execute flow by ID
 */
suspend fun executeFlow(flowId: String, message: Message): Message {
    val flow = FlowRegistry.getFlow(flowId)
        ?: return message.createReply(
            content = "Flow not found: $flowId",
            sender = "flow-executor",
            type = MessageType.ERROR
        )
    
    return flow.execute(message)
}

/**
 * Execute agent by ID
 */
suspend fun executeAgent(agentId: String, message: Message): Message {
    val agent = AgentRegistry.getAgent(agentId)
        ?: return message.createReply(
            content = "Agent not found: $agentId",
            sender = "agent-executor",
            type = MessageType.ERROR
        )
    
    return agent.processMessage(message)
}

/**
 * Execute tool by name
 */
suspend fun executeTool(toolName: String, parameters: Map<String, Any>): ToolResult {
    val tool = ToolRegistry.getTool(toolName)
        ?: return ToolResult.error("Tool not found: $toolName")
    
    return tool.execute(parameters)
} 