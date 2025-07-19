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
 * 🔧 Enhanced Core Agent Builder with 4-Level Management System
 */
class CoreAgentBuilder {
    var id: String = "agent-${System.currentTimeMillis()}"
    var name: String = ""
    var description: String = ""
    private var handler: (suspend (Comm) -> Comm)? = null
    
    // 4-Level Management System
    private val agentTools = mutableListOf<String>()        // Level 1: Agent-only tools
    private val globalToolRefs = mutableListOf<String>()    // Level 2: Global tool references  
    private val inlineTools = mutableListOf<Tool>()         // Level 3: Inline tool definitions
    private val vectorStores = mutableMapOf<String, VectorStoreConfig>() // Level 4: VectorStore management
    private val vectorStoreInstances = mutableMapOf<String, VectorStore>() // Actual VectorStore instances
    
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
     * Level 1: Add agent-specific tools (isolated to this agent)
     */
    fun tools(vararg toolNames: String) {
        agentTools.addAll(toolNames)
    }

    /**
     * Level 2: Reference global tools (register if not exists)
     */
    fun globalTools(vararg toolNames: String) {
        globalToolRefs.addAll(toolNames)
        
        // Ensure tools are registered globally
        toolNames.forEach { name ->
            if (!ToolRegistry.ensureRegistered(name)) {
                println("⚠️ Warning: Global tool '$name' not found in registry")
            }
        }
    }

    /**
     * Level 3: Inline tool definition with optional global registration
     */
    fun tool(name: String, autoRegister: Boolean = false, config: InlineToolBuilder.() -> Unit) {
        val builder = InlineToolBuilder(name)
        builder.config()
        val tool = builder.build()
        
        inlineTools.add(tool)
        
        if (autoRegister) {
            ToolRegistry.register(tool, "global")
            if (debugEnabled) {
                println("$debugPrefix Registered inline tool '$name' globally")
            }
        }
    }
    
    /**
     * Level 4: VectorStore configuration with DSL
     */
    fun vectorStore(name: String, config: VectorStoreBuilder.() -> Unit) {
        val builder = VectorStoreBuilder(name)
        builder.config()
        val storeConfig = builder.build()
        
        vectorStores[name] = storeConfig
        
        // Create and register VectorStore instance
        val vectorStore = VectorStoreRegistry.getOrCreate(name, storeConfig, id)
        vectorStoreInstances[name] = vectorStore
        
        if (debugEnabled) {
            println("$debugPrefix Configured vector store '$name' with provider '${storeConfig.provider}'")
            println("$debugPrefix Created and registered VectorStore instance for '$name'")
        }
        
        // Auto-register vector search tool for this store
        val vectorSearchTool = createVectorSearchTool(name, storeConfig)
        inlineTools.add(vectorSearchTool)
        
        if (debugEnabled) {
            println("$debugPrefix Auto-registered 'search-$name' tool for vector store '$name'")
        }
    }
    
    /**
     * Level 4: Quick VectorStore setup with connection string
     */
    fun vectorStore(name: String, connectionString: String) {
        val config = parseConnectionString(connectionString)
        vectorStores[name] = config
        
        // Create and register VectorStore instance
        val vectorStore = VectorStoreRegistry.getOrCreate(name, config, id)
        vectorStoreInstances[name] = vectorStore
        
        if (debugEnabled) {
            println("$debugPrefix Quick-configured vector store '$name' from connection string")
            println("$debugPrefix Created and registered VectorStore instance for '$name'")
        }
        
        // Auto-register vector search tool
        val vectorSearchTool = createVectorSearchTool(name, config)
        inlineTools.add(vectorSearchTool)
    }

    /**
     * Get all tools for this agent (combined from all levels)
     */
    fun getAllAgentTools(): List<Tool> {
        val allTools = mutableListOf<Tool>()
        
        // Add tools from registry (agent + global)
        (agentTools + globalToolRefs).forEach { toolName ->
            ToolRegistry.getTool(toolName)?.let { allTools.add(it) }
        }
        
        // Add inline tools (including auto-generated vector search tools)
        allTools.addAll(inlineTools)
        
        return allTools
    }
    
    /**
     * Get all vector stores for this agent
     */
    fun getAllVectorStores(): Map<String, VectorStoreConfig> = vectorStores.toMap()
    
    /**
     * Get all vector store instances for this agent
     */
    fun getAllVectorStoreInstances(): Map<String, VectorStore> = vectorStoreInstances.toMap()

    /**
     * Set message handler
     */
    fun handle(handler: suspend (Comm) -> Comm) {
        this.handler = handler
    }

    internal fun build(): Agent {
        require(name.isNotEmpty()) { "Agent name is required" }
        require(handler != null) { "Message handler is required" }

        val allTools = getAllAgentTools()
        val allStores = getAllVectorStores()
        val allStoreInstances = getAllVectorStoreInstances()

        if (debugEnabled) {
            println("$debugPrefix Building agent '$name' ($id) with ${allTools.size} tools and ${allStores.size} vector stores")
            println("$debugPrefix - Agent tools: $agentTools")
            println("$debugPrefix - Global tools: $globalToolRefs") 
            println("$debugPrefix - Inline tools: ${inlineTools.map { it.name }}")
            println("$debugPrefix - Vector stores: ${allStores.keys}")
        }

        return CoreAgent(
            id = id,
            name = name,
            description = description,
            handler = handler!!,
            tools = allTools,
            vectorStores = allStores,
            vectorStoreInstances = allStoreInstances,
            debugInfo = if (debugEnabled) DebugInfo(debugEnabled, debugPrefix) else null
        )
    }
    
    /**
     * Create vector search tool for a vector store
     */
    private fun createVectorSearchTool(storeName: String, config: VectorStoreConfig): Tool {
        return object : Tool {
            override val name: String = "search-$storeName"
            override val description: String = "Search in vector store '$storeName'"
            override val schema: ToolSchema = ToolSchema(
                name = name,
                description = description,
                parameters = mapOf(
                    "query" to ParameterSchema("string", "Search query", required = true),
                    "topK" to ParameterSchema("number", "Number of results", required = false),
                    "filter" to ParameterSchema("object", "Search filters", required = false)
                )
            )
            
            override suspend fun execute(parameters: Map<String, Any>): ToolResult {
                return try {
                    val query = parameters["query"] as? String
                        ?: return ToolResult.error("Query parameter required")
                    
                    val topK = (parameters["topK"] as? Number)?.toInt() ?: 5
                    
                    // Get vector store instance from registry or create new one
                    val vectorStore = VectorStoreRegistry.get(storeName) 
                        ?: createVectorStoreInstance(config)
                    
                    // Perform search
                    val results = vectorStore.searchByText(
                        collectionName = "default",
                        queryText = query,
                        topK = topK
                    )
                    
                    val resultText = results.joinToString("\n") { result ->
                        "Score: ${result.score}, Content: ${result.metadata["content"] ?: "N/A"}"
                    }
                    
                    ToolResult.success(
                        result = resultText,
                        metadata = mapOf(
                            "store_name" to storeName,
                            "result_count" to results.size.toString(),
                            "provider" to config.provider
                        )
                    )
                } catch (e: Exception) {
                    ToolResult.error("Vector search failed: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Parse connection string to VectorStoreConfig
     */
    private fun parseConnectionString(connectionString: String): VectorStoreConfig {
        // Parse "qdrant://localhost:6333?apiKey=xxx" format
        val uri = java.net.URI.create(connectionString)
        val provider = uri.scheme ?: "qdrant"
        val host = uri.host ?: "localhost"
        val port = if (uri.port != -1) uri.port else 6333
        
        val params = uri.query?.split("&")?.associate { param ->
            val (key, value) = param.split("=", limit = 2)
            key to value
        } ?: emptyMap()
        
        return VectorStoreConfig(
            provider = provider,
            host = host,
            port = port,
            apiKey = params["apiKey"],
            config = params
        )
    }
    
    /**
     * Create vector store instance from config
     */
    private fun createVectorStoreInstance(config: VectorStoreConfig): VectorStore {
        return when (config.provider.lowercase()) {
            "qdrant" -> QdrantVectorStore(
                host = config.host,
                port = config.port,
                apiKey = config.apiKey
            )
            else -> throw IllegalArgumentException("Unsupported vector store provider: ${config.provider}")
        }
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
 * 🤖 Enhanced Core Agent with Tool Context Support
 */
internal class CoreAgent(
    override val id: String,
    override val name: String,
    override val description: String,
    private val handler: suspend (Comm) -> Comm,
    private val tools: List<Tool> = emptyList(),
    private val vectorStores: Map<String, VectorStoreConfig> = emptyMap(),
    private val vectorStoreInstances: Map<String, VectorStore> = emptyMap(),
    private val debugInfo: DebugInfo? = null
) : Agent {
    
    override val capabilities: List<String> = listOf("core-processing", "tool-context")
    
    override suspend fun processComm(comm: Comm): Comm {
        if (debugInfo?.enabled == true) {
            println("${debugInfo.prefix} Agent '$name' ($id) received comm:")
            println("${debugInfo.prefix}   From: ${comm.from}")
            println("${debugInfo.prefix}   Content: ${comm.content}")
            println("${debugInfo.prefix}   Data: ${comm.data}")
        }
        
        val startTime = System.currentTimeMillis()
        
        // Execute handler with tool context if tools are available
        val response = if (tools.isNotEmpty()) {
            // Use withToolContext for tool-aware processing
            io.github.spice.dsl.withToolContext(
                tools = tools,
                namespace = id,
                agentId = id,
                currentComm = comm
            ) {
                handler(comm)
            }
        } else {
            handler(comm)
        }
        
        val duration = System.currentTimeMillis() - startTime
        
        if (debugInfo?.enabled == true) {
            println("${debugInfo.prefix} Agent '$name' ($id) response:")
            println("${debugInfo.prefix}   To: ${response.to}")
            println("${debugInfo.prefix}   Content: ${response.content}")
            println("${debugInfo.prefix}   Data: ${response.data}")
            println("${debugInfo.prefix}   Processing time: ${duration}ms")
            println("${debugInfo.prefix}   ---")
        }
        
        return response
    }
    
    override fun canHandle(comm: Comm): Boolean = true
    
    override fun getTools(): List<Tool> {
        if (debugInfo?.enabled == true) {
            println("${debugInfo.prefix} Agent '$name' retrieved ${tools.size} tools: ${tools.map { it.name }}")
        }
        return tools
    }
    
    override fun isReady(): Boolean = true
    
    override fun getVectorStore(name: String): VectorStore? = vectorStoreInstances[name]
    
    override fun getVectorStores(): Map<String, VectorStore> = vectorStoreInstances
    
    /**
     * Check if this agent has debug mode enabled
     */
    fun isDebugEnabled(): Boolean = debugInfo?.enabled ?: false
    
    /**
     * Get debug information
     */
    fun getDebugInfo(): DebugInfo? = debugInfo
    
    /**
     * 🚀 Direct tool execution sugar - agent.run("toolName", params)
     */
    suspend fun run(toolName: String, parameters: Map<String, Any> = emptyMap()): ToolResult {
        val tool = tools.find { it.name == toolName }
            ?: return ToolResult.error("Tool '$toolName' not found in agent '${this.name}'")
        
        return try {
            if (debugInfo?.enabled == true) {
                println("${debugInfo.prefix} Agent '$name' executing tool '$toolName'")
            }
            
            val result = tool.execute(parameters)
            
            if (debugInfo?.enabled == true) {
                println("${debugInfo.prefix} Tool '$toolName' result: ${if (result.success) "✅ SUCCESS" else "❌ ERROR: ${result.error}"}")
            }
            
            result
        } catch (e: Exception) {
            ToolResult.error("Tool execution failed: ${e.message}")
        }
    }
    
    /**
     * 🔍 Check if agent has specific tool
     */
    fun hasTool(toolName: String): Boolean {
        return tools.any { it.name == toolName }
    }
    
    /**
     * 📋 Get available tool names
     */
    fun getToolNames(): List<String> {
        return tools.map { it.name }
    }
    
    /**
     * 🎯 Execute multiple tools in sequence
     */
    suspend fun runSequence(
        toolsWithParams: List<Pair<String, Map<String, Any>>>
    ): List<ToolResult> {
        val results = mutableListOf<ToolResult>()
        
        for ((toolName, params) in toolsWithParams) {
            val result = run(toolName, params)
            results.add(result)
            
            // Stop on first failure if not explicitly configured otherwise
            if (!result.success) {
                if (debugInfo?.enabled == true) {
                    println("${debugInfo.prefix} Sequence stopped at '$toolName' due to failure")
                }
                break
            }
        }
        
        return results
    }
}

/**
 * 🔧 Tool-aware context for agents
 */
suspend fun <T> withToolContext(
    tools: List<Tool>,
    namespace: String,
    agentId: String,
    currentComm: Comm,
    block: suspend () -> T
): T {
    // Set up tool context (could be more sophisticated in real implementation)
    return block()
}

/**
 * 🔨 Inline Tool Builder for DSL
 */
class InlineToolBuilder(private val name: String) {
    var description: String = ""
    private val parametersMap: MutableMap<String, ParameterSchema> = mutableMapOf()
    private var executeFunction: (suspend (Map<String, Any>) -> ToolResult)? = null
    private var canExecuteFunction: ((Map<String, Any>) -> Boolean)? = null
    
    /**
     * Set tool description
     */
    fun description(desc: String) {
        description = desc
    }
    
    /**
     * Add a parameter
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
     * Set simple execution with auto-success result
     */
    fun execute(executor: (Map<String, Any>) -> Any?) {
        executeFunction = { params ->
            try {
                val result = executor(params)
                ToolResult(success = true, result = result?.toString() ?: "")
            } catch (e: Exception) {
                ToolResult(success = false, error = e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Set validation function
     */
    fun canExecute(checker: (Map<String, Any>) -> Boolean) {
        canExecuteFunction = checker
    }
    
    /**
     * Build the tool
     */
    internal fun build(): Tool {
        require(executeFunction != null) { "Tool execution function is required" }
        
        val schema = ToolSchema(
            name = name,
            description = description,
            parameters = parametersMap
        )
        
        return InlineTool(
            name = name,
            description = description,
            schema = schema,
            executeFunction = executeFunction!!,
            canExecuteFunction = canExecuteFunction
        )
    }
}

// =====================================
// INLINE TOOL CLASS
// =====================================

/**
 * 🔧 Inline Tool Implementation
 * 
 * A Tool implementation specifically designed for inline tool definitions
 * within the DSL. Supports both suspend and non-suspend execution patterns.
 */
class InlineTool(
    override val name: String,
    override val description: String,
    override val schema: ToolSchema,
    private val executeFunction: suspend (Map<String, Any>) -> ToolResult,
    private val canExecuteFunction: ((Map<String, Any>) -> Boolean)? = null
) : Tool {
    
    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
        // Validate parameters if canExecute function is provided
        canExecuteFunction?.let { validator ->
            if (!validator(parameters)) {
                return ToolResult.error("Tool execution validation failed")
            }
        }
        
        return try {
            executeFunction(parameters)
        } catch (e: Exception) {
            ToolResult.error("Tool execution failed: ${e.message}")
        }
    }
    
    override fun canExecute(parameters: Map<String, Any>): Boolean {
        return canExecuteFunction?.invoke(parameters) ?: true
    }
    
    /**
     * Create a copy of this tool with modified properties
     */
    fun copy(
        name: String = this.name,
        description: String = this.description,
        schema: ToolSchema = this.schema,
        executeFunction: suspend (Map<String, Any>) -> ToolResult = this.executeFunction,
        canExecuteFunction: ((Map<String, Any>) -> Boolean)? = this.canExecuteFunction
    ): InlineTool {
        return InlineTool(
            name = name,
            description = description,
            schema = schema,
            executeFunction = executeFunction,
            canExecuteFunction = canExecuteFunction
        )
    }
    
    /**
     * Get execution function for debugging/inspection
     */
    internal fun getExecuteFunction() = executeFunction
    
    override fun toString(): String {
        return "InlineTool(name='$name', description='$description', parameters=${schema.parameters.keys})"
    }
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
    val condition: (suspend (Comm) -> Boolean)? = null
)

/**
 * Simple flow implementation
 */
class CoreFlow(
    override val id: String,
    val name: String,
    val description: String,
    private val steps: List<FlowStep>
) : Identifiable

/**
 * Flow builder
 */
class CoreFlowBuilder {
    var id: String = "flow-${System.currentTimeMillis()}"
    var name: String = ""
    var description: String = ""
    private val steps = mutableListOf<FlowStep>()
    
    fun step(stepId: String, agentId: String, condition: (suspend (Comm) -> Boolean)? = null) {
        steps.add(FlowStep(stepId, agentId, condition))
    }
    
    internal fun build(): CoreFlow {
        require(name.isNotEmpty()) { "Flow name is required" }
        return CoreFlow(id, name, description, steps.toList())
    }
}

// =====================================
// DSL FUNCTIONS
// =====================================

/**
 * Build agent with DSL
 */
fun buildAgent(config: CoreAgentBuilder.() -> Unit): Agent {
    val builder = CoreAgentBuilder()
    builder.config()
    return builder.build()
}

/**
 * Build flow with DSL
 */
fun buildFlow(config: CoreFlowBuilder.() -> Unit): CoreFlow {
    val builder = CoreFlowBuilder()
    builder.config()
    return builder.build()
}

// =====================================
// VECTOR STORE DSL
// =====================================

// VectorStoreConfig is now in VectorStoreRegistry.kt

/**
 * 🔧 Provider Default Settings
 */
data class ProviderDefaults(
    val host: String,
    val port: Int,
    val vectorSize: Int
)

/**
 * 🔨 Vector Store Builder for DSL
 */
class VectorStoreBuilder(private val name: String) {
    var provider: String = "qdrant"
    var host: String = "localhost"
    var port: Int = 6333
    var apiKey: String? = null
    var collection: String = "default"
    var vectorSize: Int = 384
    private val config = mutableMapOf<String, String>()
    
    // Provider-specific defaults
    private val providerDefaults = mapOf(
        "qdrant" to ProviderDefaults("localhost", 6333, 384),
        "pinecone" to ProviderDefaults("api.pinecone.io", 443, 1536),
        "weaviate" to ProviderDefaults("localhost", 8080, 768),
        "chroma" to ProviderDefaults("localhost", 8000, 384),
        "milvus" to ProviderDefaults("localhost", 19530, 768)
    )
    
    /**
     * Set provider with automatic defaults
     */
    fun provider(providerName: String) {
        provider = providerName.lowercase()
        
        // Apply provider-specific defaults
        providerDefaults[provider]?.let { defaults ->
            if (host == "localhost" || host == providerDefaults["qdrant"]?.host) {
                host = defaults.host
            }
            if (port == 6333 || port == providerDefaults["qdrant"]?.port) {
                port = defaults.port
            }
            if (vectorSize == 384 || vectorSize == providerDefaults["qdrant"]?.vectorSize) {
                vectorSize = defaults.vectorSize
            }
        }
    }
    
    /**
     * Set connection details
     */
    fun connection(host: String, port: Int = 6333) {
        this.host = host
        this.port = port
    }
    
    /**
     * Set API key for authentication
     */
    fun apiKey(key: String) {
        apiKey = key
    }
    
    /**
     * Set default collection name
     */
    fun collection(name: String) {
        collection = name
    }
    
    /**
     * Set vector dimension size
     */
    fun vectorSize(size: Int) {
        vectorSize = size
    }
    
    /**
     * Add custom configuration
     */
    fun config(key: String, value: String) {
        config[key] = value
    }
    
    /**
     * Build the configuration
     */
    internal fun build(): VectorStoreConfig {
        return VectorStoreConfig(
            provider = provider,
            host = host,
            port = port,
            apiKey = apiKey,
            collection = collection,
            vectorSize = vectorSize,
            config = config.toMap()
        )
    }
}

// =====================================
// REGISTRIES
// =====================================

/**
 * 🗂️ Enhanced Tool Registry with Namespace Support
 */


// Convenience functions for common Agent types

/**
 * Text processing Agent
 */
fun textProcessingAgent(
    id: String,
    name: String,
    description: String = "Text processing agent",
    processor: suspend (Comm) -> Comm
): Agent = buildAgent {
    this.id = id
    this.name = name
    this.description = description
    handle(processor)
}

/**
 * Echo Agent (for testing)
 */
fun echoAgent(
    id: String,
    name: String = "Echo Agent",
    description: String = "Simple echo agent"
): Agent = buildAgent {
    this.id = id
    this.name = name
    this.description = description
    handle { comm ->
        comm.reply(
            content = "Echo: ${comm.content}",
            from = id
        )
    }
} 