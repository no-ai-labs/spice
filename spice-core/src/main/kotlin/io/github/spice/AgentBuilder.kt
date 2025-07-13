package io.github.spice

/**
 * Agent Builder DSL
 * A Kotlin DSL for declaratively creating Agents.
 */

class AgentBuilder {
    var id: String = "agent-${System.currentTimeMillis()}"
    var name: String = ""
    var description: String = ""
    var capabilities: Set<String> = emptySet()
    var supportedMessageTypes: Set<MessageType> = setOf(MessageType.TEXT)
    var tools: MutableList<Tool> = mutableListOf()
    var persona: AgentPersona? = null
    
    private var messageHandler: (suspend (Message) -> Message)? = null
    private var canHandleCondition: (suspend (Message) -> Boolean)? = null
    
    /**
     * Message handler configuration
     */
    fun messageHandler(handler: suspend (Message) -> Message) {
        this.messageHandler = handler
    }
    
    /**
     * Tool configuration
     */
    fun tool(name: String, config: ToolBuilder.() -> Unit) {
        val toolBuilder = ToolBuilder(name)
        toolBuilder.config()
        tools.add(toolBuilder.build())
    }
    
    /**
     * Capabilities configuration
     */
    fun capabilities(vararg caps: String) {
        capabilities = caps.toSet()
    }
    
    /**
     * Tools configuration
     */
    fun tools(vararg toolInstances: Tool) {
        tools.addAll(toolInstances)
    }
    
    /**
     * Message processing handler configuration
     */
    @Deprecated("Use messageHandler instead", ReplaceWith("messageHandler(processor)"))
    fun messageProcessor(processor: suspend (Message) -> Message) {
        messageHandler(processor)
    }
    
    /**
     * canHandle condition configuration
     */
    fun canHandle(condition: suspend (Message) -> Boolean) {
        this.canHandleCondition = condition
    }
    
    /**
     * Persona configuration
     */
    fun persona(persona: AgentPersona) {
        this.persona = persona
    }
    
    /**
     * Persona configuration with DSL
     */
    fun persona(name: String, config: PersonaBuilder.() -> Unit) {
        this.persona = buildPersona(name, config)
    }
    
    fun build(): Agent {
        val baseAgent = DSLAgent(
            id = id,
            name = name,
            description = description,
            capabilities = capabilities.toList(),
            supportedMessageTypes = supportedMessageTypes,
            tools = tools,
            messageHandler = messageHandler,
            canHandleCondition = canHandleCondition
        )
        
        return if (persona != null) {
            baseAgent.withPersona(persona!!)
        } else {
            baseAgent
        }
    }
}

/**
 * Tool builder for DSL
 */
class ToolBuilder(private val name: String) {
    var description: String = ""
    private val parametersMap: MutableMap<String, ParameterSchema> = mutableMapOf()
    private var executeFunction: (suspend (Map<String, Any>) -> ToolResult)? = null
    private var canExecuteFunction: ((Map<String, Any>) -> Boolean)? = null
    
    /**
     * Set parameters using a map (legacy style)
     */
    fun parameters(params: Map<String, String>) {
        parametersMap.clear()
        params.forEach { (paramName, type) ->
            parametersMap[paramName] = ParameterSchema(type = type, description = "", required = true)
        }
    }
    
    /**
     * Add a single parameter with detailed configuration
     */
    fun parameter(name: String, type: String, description: String = "", required: Boolean = true) {
        parametersMap[name] = ParameterSchema(type = type, description = description, required = required)
    }
    
    /**
     * Set the execution function
     */
    fun execute(executor: suspend (Map<String, Any>) -> ToolResult) {
        executeFunction = executor
    }
    
    /**
     * Set the canExecute validation function
     */
    fun canExecute(checker: (Map<String, Any>) -> Boolean) {
        canExecuteFunction = checker
    }
    
    fun build(): Tool {
        return DSLTool(
            name = name,
            description = description,
            schema = ToolSchema(
                name = name,
                description = description,
                parameters = parametersMap.toMap()
            ),
            executeFunction = executeFunction ?: { ToolResult(success = false, error = "No execution function defined") },
            canExecuteFunction = canExecuteFunction
        )
    }
}

/**
 * Execution builder for complex tool execution
 */
class ExecutionBuilder {
    private var executeFunction: (suspend (Map<String, Any>) -> ToolResult)? = null
    
    fun execute(executor: suspend (Map<String, Any>) -> ToolResult) {
        executeFunction = executor
    }
    
    /**
     * canExecute condition configuration
     */
    fun canExecute(condition: (Map<String, Any>) -> Boolean) {
        // Implementation for execution conditions
    }
    
    fun build(): (suspend (Map<String, Any>) -> ToolResult) {
        return executeFunction ?: { ToolResult(success = false, error = "No execution function defined") }
    }
}

// DSL entry point
fun agent(config: AgentBuilder.() -> Unit): Agent {
    val builder = AgentBuilder()
    builder.config()
    return builder.build()
}

/**
 * DSL-created Agent implementation
 */
private class DSLAgent(
    override val id: String,
    override val name: String,
    override val description: String,
    override val capabilities: List<String>,
    private val supportedMessageTypes: Set<MessageType>,
    private val tools: List<Tool>,
    private val messageHandler: (suspend (Message) -> Message)?,
    private val canHandleCondition: (suspend (Message) -> Boolean)?
) : Agent {
    
    override fun canHandle(message: Message): Boolean {
        return supportedMessageTypes.contains(message.type)
    }
    
    override suspend fun processMessage(message: Message): Message {
        return messageHandler?.invoke(message) ?: Message(
            id = "default-${message.id}",
            type = MessageType.TEXT,
            content = "Default processing: ${message.content}",
            sender = id,
            parentId = message.id
        )
    }
    
    override fun getTools(): List<Tool> = tools
    
    override fun isReady(): Boolean = true
}

/**
 * DSL-created Tool implementation
 */
private class DSLTool(
    override val name: String,
    override val description: String,
    override val schema: ToolSchema,
    private val executeFunction: suspend (Map<String, Any>) -> ToolResult,
    private val canExecuteFunction: ((Map<String, Any>) -> Boolean)? = null
) : Tool {
    
    override fun canExecute(parameters: Map<String, Any>): Boolean {
        return canExecuteFunction?.invoke(parameters) ?: super.canExecute(parameters)
    }
    
    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
        return if (!canExecute(parameters)) {
            ToolResult(success = false, error = "Tool cannot execute with given parameters")
        } else try {
            executeFunction(parameters)
        } catch (e: Exception) {
            ToolResult(success = false, error = "Tool execution failed: ${e.message}")
        }
    }
}

// Convenience functions for common Agent types

/**
 * Text processing Agent
 */
fun textProcessingAgent(
    id: String,
    name: String,
    description: String = "Text processing agent",
    processor: suspend (Message) -> Message
): Agent = agent {
    this.id = id
    this.name = name
    this.description = description
    this.capabilities = setOf("text-processing")
    this.supportedMessageTypes = setOf(MessageType.TEXT)
    messageHandler(processor)
}

/**
 * API Agent
 */
fun apiAgent(
    id: String,
    name: String,
    description: String = "API integration agent",
    apiHandler: suspend (Message) -> Message
): Agent = agent {
    this.id = id
    this.name = name
    this.description = description
    this.capabilities = setOf("api-integration")
    this.supportedMessageTypes = setOf(MessageType.TEXT, MessageType.DATA)
    messageHandler(apiHandler)
}

/**
 * Data processing Agent
 */
fun dataProcessingAgent(
    id: String,
    name: String,
    description: String = "Data processing agent",
    dataProcessor: suspend (Message) -> Message
): Agent = agent {
    this.id = id
    this.name = name
    this.description = description
    this.capabilities = setOf("data-processing")
    this.supportedMessageTypes = setOf(MessageType.DATA, MessageType.TEXT)
    messageHandler(dataProcessor)
}

/**
 * Conditional routing Agent
 */
fun routingAgent(
    id: String,
    name: String,
    description: String = "Message routing agent",
    router: suspend (Message) -> Message
): Agent = agent {
    this.id = id
    this.name = name
    this.description = description
    this.capabilities = setOf("message-routing")
    this.supportedMessageTypes = setOf(MessageType.TEXT, MessageType.DATA)
    messageHandler(router)
} 