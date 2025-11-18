package io.github.noailabs.spice.dsl

import io.github.noailabs.spice.Agent
import io.github.noailabs.spice.AgentRuntime
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.Tool
import io.github.noailabs.spice.error.SpiceResult

/**
 * 🏗️ Agent Builder DSL
 *
 * Fluent API for building custom agents with tool integration, vector stores,
 * and custom message handlers.
 *
 * **Usage:**
 * ```kotlin
 * val agent = buildAgent {
 *     id = "custom-agent"
 *     name = "My Custom Agent"
 *     description = "A custom agent with tools"
 *
 *     // Tool integration
 *     tools("web_search", "calculator")
 *
 *     // Global tools (from registry)
 *     globalTools("knowledge_base")
 *
 *     // Optional: Vector store
 *     vectorStore("docs") {
 *         provider("qdrant")
 *         connection("localhost", 6333)
 *         collection("documents")
 *     }
 *
 *     // Custom handler
 *     handle { message ->
 *         // Custom logic
 *         message.reply("Custom response", id)
 *     }
 * }
 * ```
 *
 * @since 1.0.0
 * @author Spice Framework
 */
class AgentBuilder {
    var id: String = "custom-agent-${System.currentTimeMillis()}"
    var name: String = "Custom Agent"
    var description: String = "Custom agent built with DSL"
    var capabilities: MutableList<String> = mutableListOf("chat", "custom")

    private val tools = mutableListOf<Tool>()
    private val globalToolNames = mutableListOf<String>()
    private var vectorStoreConfig: VectorStoreConfig? = null
    private var messageHandler: (suspend (SpiceMessage) -> SpiceResult<SpiceMessage>)? = null
    private var debugMode: Boolean = false

    /**
     * Add tools to the agent
     *
     * @param toolNames Tool names or IDs
     */
    fun tools(vararg toolNames: String) {
        // Tools will be resolved from ToolRegistry at build time
        globalToolNames.addAll(toolNames)
    }

    /**
     * Add tools to the agent
     *
     * @param tools Tool instances
     */
    fun tools(vararg tools: Tool) {
        this.tools.addAll(tools)
    }

    /**
     * Add global tools from registry
     *
     * @param toolNames Tool names to load from global registry
     */
    fun globalTools(vararg toolNames: String) {
        globalToolNames.addAll(toolNames)
    }

    /**
     * Configure vector store for the agent
     *
     * @param name Vector store name
     * @param block Configuration block
     */
    fun vectorStore(name: String, block: VectorStoreConfig.() -> Unit) {
        vectorStoreConfig = VectorStoreConfig(name).apply(block)
    }

    /**
     * Set custom message handler
     *
     * @param handler Message processing function
     */
    fun handle(handler: suspend (SpiceMessage) -> SpiceResult<SpiceMessage>) {
        messageHandler = handler
    }

    /**
     * Enable debug mode
     *
     * @param enabled Debug mode flag
     */
    fun debugMode(enabled: Boolean = true) {
        debugMode = enabled
    }

    /**
     * Build the agent
     *
     * @return Custom agent instance
     */
    fun build(): Agent {
        return CustomAgent(
            id = id,
            name = name,
            description = description,
            capabilities = capabilities.toList(),
            tools = tools.toList(),
            globalToolNames = globalToolNames.toList(),
            vectorStoreConfig = vectorStoreConfig,
            messageHandler = messageHandler,
            debugMode = debugMode
        )
    }
}

/**
 * Vector Store Configuration
 */
class VectorStoreConfig(val name: String) {
    var provider: String = "qdrant"
    var host: String = "localhost"
    var port: Int = 6333
    var collection: String = "documents"
    var embeddingModel: String = "text-embedding-ada-002"
    var apiKey: String? = null

    fun provider(name: String) {
        provider = name
    }

    fun connection(host: String, port: Int) {
        this.host = host
        this.port = port
    }

    fun collection(name: String) {
        collection = name
    }

    fun embeddingModel(model: String) {
        embeddingModel = model
    }
}

/**
 * Custom Agent Implementation
 */
private class CustomAgent(
    override val id: String,
    override val name: String,
    override val description: String,
    override val capabilities: List<String>,
    private val tools: List<Tool>,
    private val globalToolNames: List<String>,
    private val vectorStoreConfig: VectorStoreConfig?,
    private val messageHandler: (suspend (SpiceMessage) -> SpiceResult<SpiceMessage>)?,
    private val debugMode: Boolean
) : Agent {

    override suspend fun processMessage(message: SpiceMessage): SpiceResult<SpiceMessage> {
        return if (messageHandler != null) {
            // Use custom handler
            messageHandler.invoke(message)
        } else {
            // Default: echo back
            SpiceResult.success(
                message.reply(
                    content = "[Custom Agent] Received: ${message.content}",
                    from = id
                )
            )
        }
    }

    override suspend fun processMessage(
        message: SpiceMessage,
        runtime: AgentRuntime
    ): SpiceResult<SpiceMessage> {
        return processMessage(message)
    }

    override fun getTools(): List<Tool> = tools

    override fun canHandle(message: SpiceMessage): Boolean = true

    override fun isReady(): Boolean = true
}

/**
 * Build a custom agent using DSL
 *
 * **Example:**
 * ```kotlin
 * val agent = buildAgent {
 *     id = "my-agent"
 *     name = "My Agent"
 *
 *     tools("web_search", "calculator")
 *
 *     handle { message ->
 *         SpiceResult.success(message.reply("Response", id))
 *     }
 * }
 * ```
 *
 * @param block Builder configuration block
 * @return Custom agent
 */
fun buildAgent(block: AgentBuilder.() -> Unit): Agent {
    return AgentBuilder().apply(block).build()
}
