# Agent API

The foundation of Spice Framework - intelligent, autonomous agents that process communications and execute tasks.

## Overview

`Agent` is the core abstraction in Spice Framework. Agents are autonomous entities that:

- **Process communications** - Handle incoming messages and generate responses
- **Execute tools** - Perform actions through extensible tool system
- **Manage state** - Track and persist operational state
- **Coordinate** - Work with other agents in complex workflows
- **Monitor** - Track metrics and performance data

Spice provides multiple agent implementations:
- **BaseAgent** - Foundation for custom agents
- **SwarmAgent** - Multi-agent coordinator with 5 execution strategies
- **LLMAgent** - AI-powered agents (via providers)

## Core Structure

```kotlin
interface Agent : Identifiable {
    // Identity
    val id: String
    val name: String
    val description: String
    val capabilities: List<String>

    // Core operations
    suspend fun processComm(comm: Comm): SpiceResult<Comm>
    suspend fun processComm(comm: Comm, runtime: AgentRuntime): SpiceResult<Comm>

    // Agent capabilities
    fun canHandle(comm: Comm): Boolean
    fun getTools(): List<Tool>
    fun isReady(): Boolean

    // Configuration & lifecycle
    fun getConfig(): AgentConfig
    suspend fun initialize(runtime: AgentRuntime)
    suspend fun cleanup()

    // Vector stores (RAG support)
    fun getVectorStore(name: String): VectorStore?
    fun getVectorStores(): Map<String, VectorStore>

    // Monitoring
    fun getMetrics(): AgentMetrics
}
```

## Creating Agents

### Using DSL (Recommended)

The simplest way to create agents with LLM providers:

```kotlin
val agent = buildAgent {
    name = "Research Assistant"
    description = "Helps with research tasks"
    capabilities = listOf("research", "analysis", "summarization")

    // LLM provider
    llm = anthropic(apiKey = "your-api-key") {
        model = "claude-3-5-sonnet-20241022"
        temperature = 0.7
        maxTokens = 2048
    }

    // Add tools
    tools {
        tool("web_search") { /* ... */ }
        tool("summarize") { /* ... */ }
    }

    // Add instructions
    instructions = """
        You are a research assistant that helps users find and analyze information.
        Always cite your sources and provide comprehensive answers.
    """.trimIndent()
}
```

### Custom BaseAgent

For advanced use cases, extend `BaseAgent`:

```kotlin
class CustomAgent(
    id: String,
    name: String,
    description: String,
    capabilities: List<String> = emptyList(),
    config: AgentConfig = AgentConfig()
) : BaseAgent(id, name, description, capabilities, config) {

    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        log(LogLevel.INFO, "Processing comm: ${comm.id}")

        return when (comm.type) {
            CommType.TEXT -> handleTextMessage(comm)
            CommType.TOOL_CALL -> handleToolCall(comm)
            else -> SpiceResult.success(
                comm.reply(
                    content = "Unsupported message type",
                    from = id
                )
            )
        }
    }

    private suspend fun handleTextMessage(comm: Comm): SpiceResult<Comm> {
        // Your custom processing logic
        val response = processText(comm.content)

        return SpiceResult.success(
            comm.reply(
                content = response,
                from = id,
                data = mapOf(
                    "processing_time_ms" to "120",
                    "model" to "custom-v1"
                )
            )
        )
    }

    private suspend fun handleToolCall(comm: Comm): SpiceResult<Comm> {
        val toolName = comm.getToolName() ?: return SpiceResult.success(
            comm.error("Missing tool name", from = id)
        )

        val params = parseToolParams(comm.data["tool_params"] ?: "{}")

        return executeTool(toolName, params).fold(
            onSuccess = { toolResult ->
                SpiceResult.success(
                    comm.toolResult(
                        result = toolResult.result,
                        from = toolName
                    )
                )
            },
            onFailure = { error ->
                SpiceResult.success(
                    comm.error(
                        message = "Tool execution failed: ${error.message}",
                        from = id
                    )
                )
            }
        )
    }

    private fun processText(content: String): String {
        // Your custom logic here
        return "Processed: $content"
    }
}
```

### Multi-Agent Swarms

Create sophisticated multi-agent systems:

```kotlin
val swarm = buildSwarmAgent {
    name = "Research Team"
    description = "Multi-agent research team"

    // Shared tools for all members
    swarmTools {
        tool("web_search", "Search the web") {
            parameter("query", "string", "Search query", required = true)
            execute(fun(params: Map<String, Any>): String {
                val query = params["query"] as String
                return searchWeb(query)
            })
        }

        // Built-in coordination tools
        aiConsensus(scoringAgent = anthropic(...))
        resultAggregator()
    }

    // Define team members
    quickSwarm {
        specialist("researcher", "Researcher", "information gathering")
        specialist("analyst", "Analyst", "data analysis")
        specialist("writer", "Writer", "report writing")
    }

    // Or use existing agents
    members {
        agent(researchAgent)
        agent(analysisAgent)
        agent(writingAgent)
    }
}

// Execute with automatic coordination
val result = swarm.processComm(Comm(
    content = "Research the impact of AI on healthcare",
    from = "user"
))
```

## Agent Methods

### processComm()

Process incoming communication and generate response:

```kotlin
// Basic processing
suspend fun processComm(comm: Comm): SpiceResult<Comm>

// With runtime context
suspend fun processComm(comm: Comm, runtime: AgentRuntime): SpiceResult<Comm>
```

**Usage:**

```kotlin
val agent = buildAgent { /* ... */ }

// Simple processing
val result = agent.processComm(Comm(
    content = "Hello, agent!",
    from = "user"
))

result.fold(
    onSuccess = { response ->
        println("Agent response: ${response.content}")
    },
    onFailure = { error ->
        println("Error: ${error.message}")
    }
)

// With runtime context
val runtime = DefaultAgentRuntime(
    context = AgentContext.of(
        "userId" to "user-123",
        "sessionId" to "session-456"
    )
)

val result = agent.processComm(comm, runtime)
```

**Return Value:**
- `SpiceResult.success(Comm)` - Successful response
- `SpiceResult.failure(SpiceError)` - Processing error

### canHandle()

Check if agent can handle a specific comm:

```kotlin
fun canHandle(comm: Comm): Boolean
```

**Usage:**

```kotlin
val comm = Comm(content = "Analyze this data", from = "user")

if (agent.canHandle(comm)) {
    val result = agent.processComm(comm)
    // Process result
} else {
    // Route to different agent
    val alternateResult = otherAgent.processComm(comm)
}
```

**Default Implementation (BaseAgent):**
- Returns `true` for TEXT, PROMPT, SYSTEM, WORKFLOW_START, WORKFLOW_END
- Returns `true` for TOOL_CALL if agent has the requested tool
- Returns `false` for other types

**Override for custom logic:**

```kotlin
override fun canHandle(comm: Comm): Boolean {
    return when {
        comm.type == CommType.TEXT && comm.content.contains("research") -> true
        comm.from == "trusted-system" -> true
        comm.priority == Priority.CRITICAL -> true
        else -> super.canHandle(comm)
    }
}
```

### getTools()

Retrieve tools available to the agent:

```kotlin
fun getTools(): List<Tool>
```

**Usage:**

```kotlin
val agent = buildAgent {
    name = "Assistant"
    tools {
        tool("calculator") { /* ... */ }
        tool("weather") { /* ... */ }
    }
}

// List all tools
val tools = agent.getTools()
tools.forEach { tool ->
    println("Tool: ${tool.name} - ${tool.description}")
}

// Find specific tool
val calculator = tools.find { it.name == "calculator" }
if (calculator != null) {
    val result = calculator.execute(mapOf("a" to 10, "b" to 20, "op" to "+"))
}

// Check tool availability
val hasWeatherTool = tools.any { it.name == "weather" }
```

### isReady()

Check if agent is ready for operation:

```kotlin
fun isReady(): Boolean
```

**Usage:**

```kotlin
if (agent.isReady()) {
    val result = agent.processComm(comm)
} else {
    // Initialize agent first
    agent.initialize(runtime)
    // Or wait and retry
}
```

**Common reasons for not ready:**
- Dependencies not initialized
- Required resources unavailable
- Configuration incomplete
- Network connectivity issues

**Custom readiness checks:**

```kotlin
override fun isReady(): Boolean {
    return databaseConnected &&
           cacheInitialized &&
           externalApiAvailable &&
           super.isReady()
}
```

### initialize() / cleanup()

Lifecycle management:

```kotlin
suspend fun initialize(runtime: AgentRuntime)
suspend fun cleanup()
```

**Usage:**

```kotlin
class DatabaseAgent : BaseAgent(...) {
    private lateinit var dbConnection: Connection

    override suspend fun initialize(runtime: AgentRuntime) {
        super.initialize(runtime)

        log(LogLevel.INFO, "Connecting to database")
        dbConnection = connectToDatabase()

        log(LogLevel.INFO, "Loading cache")
        loadCache()
    }

    override suspend fun cleanup() {
        log(LogLevel.INFO, "Closing database connection")
        dbConnection.close()

        super.cleanup()
    }
}

// Usage
val agent = DatabaseAgent(...)
try {
    agent.initialize(runtime)

    // Use agent
    agent.processComm(comm)

} finally {
    agent.cleanup()
}
```

### getConfig()

Access agent configuration:

```kotlin
fun getConfig(): AgentConfig
```

**Usage:**

```kotlin
val config = agent.getConfig()

println("Max concurrent requests: ${config.maxConcurrentRequests}")
println("Request timeout: ${config.requestTimeoutMs}ms")
println("Retry policy: ${config.retryPolicy.maxRetries} retries")

if (config.monitoring.enableMetrics) {
    collectMetrics(agent)
}
```

### getVectorStore() / getVectorStores()

Access vector stores for RAG (Retrieval-Augmented Generation):

```kotlin
fun getVectorStore(name: String): VectorStore?
fun getVectorStores(): Map<String, VectorStore>
```

**Usage:**

```kotlin
class RAGAgent : BaseAgent(...) {

    override suspend fun initialize(runtime: AgentRuntime) {
        super.initialize(runtime)

        // Add vector stores
        val docsStore = createVectorStore("documentation")
        addVectorStore("docs", docsStore)

        val knowledgeStore = createVectorStore("knowledge_base")
        addVectorStore("knowledge", knowledgeStore)
    }

    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        // Retrieve relevant context
        val docsStore = getVectorStore("docs")
        val relevantDocs = docsStore?.search(comm.content, limit = 5)

        // Use context in response
        val contextualResponse = generateResponse(comm.content, relevantDocs)

        return SpiceResult.success(comm.reply(
            content = contextualResponse,
            from = id
        ))
    }
}

// Check available stores
val agent = RAGAgent(...)
agent.getVectorStores().forEach { (name, store) ->
    println("Vector store: $name (${store.size()} vectors)")
}
```

### getMetrics()

Access performance metrics:

```kotlin
fun getMetrics(): AgentMetrics
```

**Usage:**

```kotlin
val metrics = agent.getMetrics()

println("=== Agent Performance ===")
println("Total requests: ${metrics.totalRequests}")
println("Successful: ${metrics.successfulRequests}")
println("Failed: ${metrics.failedRequests}")
println("Success rate: ${metrics.getSuccessRate() * 100}%")
println("Avg response time: ${metrics.getAverageResponseTimeMs()}ms")

// Tool-specific metrics
metrics.toolMetrics.forEach { (toolName, toolMetrics) ->
    println("Tool '$toolName':")
    println("  Total calls: ${toolMetrics.totalCalls}")
    println("  Success: ${toolMetrics.successfulCalls}")
    println("  Failed: ${toolMetrics.failedCalls}")
}
```

## BaseAgent Protected Methods

When extending `BaseAgent`, these protected methods are available:

### executeTool()

Execute a tool by name:

```kotlin
protected suspend fun executeTool(
    toolName: String,
    parameters: Map<String, Any>
): SpiceResult<ToolResult>
```

**Usage:**

```kotlin
class MyAgent : BaseAgent(...) {
    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        // Execute calculator tool
        val result = executeTool("calculator", mapOf(
            "a" to 10,
            "b" to 20,
            "operation" to "+"
        ))

        return result.fold(
            onSuccess = { toolResult ->
                if (toolResult.success) {
                    SpiceResult.success(comm.reply(
                        content = "Result: ${toolResult.result}",
                        from = id
                    ))
                } else {
                    SpiceResult.success(comm.error(
                        message = toolResult.error,
                        from = id
                    ))
                }
            },
            onFailure = { error ->
                SpiceResult.failure(error)
            }
        )
    }
}
```

### log()

Log messages through runtime:

```kotlin
protected fun log(
    level: LogLevel,
    message: String,
    data: Map<String, Any> = emptyMap()
)
```

**Usage:**

```kotlin
class MyAgent : BaseAgent(...) {
    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        log(LogLevel.INFO, "Processing comm", mapOf(
            "commId" to comm.id,
            "from" to comm.from,
            "type" to comm.type.name
        ))

        try {
            val result = processMessage(comm)

            log(LogLevel.INFO, "Processing complete", mapOf(
                "duration_ms" to 120
            ))

            return SpiceResult.success(result)

        } catch (e: Exception) {
            log(LogLevel.ERROR, "Processing failed", mapOf(
                "error" to e.message.orEmpty(),
                "stack_trace" to e.stackTraceToString()
            ))

            return SpiceResult.failure(SpiceError(
                message = "Processing failed: ${e.message}",
                code = "PROCESSING_ERROR"
            ))
        }
    }
}
```

### callAgent()

Call another agent:

```kotlin
protected suspend fun callAgent(
    agentId: String,
    comm: Comm
): SpiceResult<Comm>?
```

**Usage:**

```kotlin
class CoordinatorAgent : BaseAgent(...) {
    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        log(LogLevel.INFO, "Delegating to specialist agent")

        // Call specialist agent
        val specialistResult = callAgent("specialist-agent", comm)

        return if (specialistResult != null) {
            specialistResult.fold(
                onSuccess = { response ->
                    // Process and augment response
                    SpiceResult.success(response.copy(
                        content = "Coordinated result: ${response.content}"
                    ))
                },
                onFailure = { error ->
                    SpiceResult.failure(error)
                }
            )
        } else {
            SpiceResult.success(comm.error(
                message = "Specialist agent not available",
                from = id
            ))
        }
    }
}
```

### publishEvent()

Publish events to the system:

```kotlin
protected suspend fun publishEvent(
    type: String,
    data: Map<String, Any> = emptyMap()
)
```

**Usage:**

```kotlin
class MonitoringAgent : BaseAgent(...) {
    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        // Publish start event
        publishEvent("processing.started", mapOf(
            "commId" to comm.id,
            "timestamp" to System.currentTimeMillis()
        ))

        val result = process(comm)

        // Publish completion event
        publishEvent("processing.completed", mapOf(
            "commId" to comm.id,
            "success" to result.isSuccess,
            "timestamp" to System.currentTimeMillis()
        ))

        return result
    }
}
```

### saveState() / getState()

Persist and retrieve agent state:

```kotlin
protected suspend fun saveState(key: String, value: Any)
protected suspend fun getState(key: String): Any?
```

**Usage:**

```kotlin
class StatefulAgent : BaseAgent(...) {
    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        // Load conversation history
        val history = getState("conversation_history") as? List<Comm> ?: emptyList()

        // Process with context
        val response = processWithHistory(comm, history)

        // Save updated history
        val updatedHistory = history + comm + response
        saveState("conversation_history", updatedHistory)

        return SpiceResult.success(response)
    }

    suspend fun clearHistory() {
        saveState("conversation_history", emptyList<Comm>())
    }
}
```

## Configuration

### AgentConfig

Configure agent behavior:

```kotlin
data class AgentConfig(
    val maxConcurrentRequests: Int = 10,
    val requestTimeoutMs: Long = 30_000,
    val retryPolicy: RetryPolicy = RetryPolicy.default(),
    val rateLimiting: RateLimiting? = null,
    val monitoring: MonitoringConfig = MonitoringConfig()
)
```

**Usage:**

```kotlin
val config = AgentConfig(
    maxConcurrentRequests = 5,
    requestTimeoutMs = 60_000,
    retryPolicy = RetryPolicy(
        maxRetries = 3,
        initialDelayMs = 1000,
        maxDelayMs = 10000,
        backoffMultiplier = 2.0
    ),
    rateLimiting = RateLimiting(
        maxRequestsPerMinute = 100,
        maxBurstSize = 20
    ),
    monitoring = MonitoringConfig(
        enableMetrics = true,
        enableTracing = true,
        enableLogging = true,
        logSlowRequests = true,
        slowRequestThresholdMs = 5000
    )
)

val agent = MyAgent(
    id = "agent-1",
    name = "Configured Agent",
    description = "Agent with custom config",
    config = config
)
```

### RetryPolicy

Configure retry behavior:

```kotlin
data class RetryPolicy(
    val maxRetries: Int = 3,
    val initialDelayMs: Long = 1000,
    val maxDelayMs: Long = 10000,
    val backoffMultiplier: Double = 2.0
)
```

**Examples:**

```kotlin
// No retries (fail fast)
val noRetry = RetryPolicy.noRetry()

// Aggressive retries
val aggressive = RetryPolicy(
    maxRetries = 10,
    initialDelayMs = 100,
    maxDelayMs = 5000,
    backoffMultiplier = 1.5
)

// Default (balanced)
val balanced = RetryPolicy.default()
```

### RateLimiting

Control request rate:

```kotlin
data class RateLimiting(
    val maxRequestsPerMinute: Int,
    val maxBurstSize: Int = maxRequestsPerMinute
)
```

**Usage:**

```kotlin
// 60 requests per minute, 10 burst
val rateLimiting = RateLimiting(
    maxRequestsPerMinute = 60,
    maxBurstSize = 10
)

val config = AgentConfig(
    rateLimiting = rateLimiting
)
```

### MonitoringConfig

Configure monitoring:

```kotlin
data class MonitoringConfig(
    val enableMetrics: Boolean = true,
    val enableTracing: Boolean = true,
    val enableLogging: Boolean = true,
    val logSlowRequests: Boolean = true,
    val slowRequestThresholdMs: Long = 5000
)
```

## Runtime & Context

### AgentRuntime

Execution environment for agents:

```kotlin
interface AgentRuntime {
    val context: AgentContext
    val scope: CoroutineScope

    suspend fun callAgent(agentId: String, comm: Comm): SpiceResult<Comm>
    suspend fun publishEvent(event: AgentEvent)
    suspend fun saveState(key: String, value: Any)
    suspend fun getState(key: String): Any?
    fun log(level: LogLevel, message: String, data: Map<String, Any> = emptyMap())
}
```

**Usage:**

```kotlin
val runtime = DefaultAgentRuntime(
    context = AgentContext.of(
        "userId" to "user-123",
        "sessionId" to "session-456",
        "locale" to "en-US"
    )
)

// Initialize agent with runtime
agent.initialize(runtime)

// Process with runtime context
val result = agent.processComm(comm, runtime)
```

### AgentContext

Flexible context storage:

```kotlin
val context = AgentContext.of(
    "userId" to "user-123",
    "tenantId" to "tenant-456",
    "permissions" to listOf("read", "write")
)

// Add values
context["requestId"] = "req-789"

// Get values
val userId = context.getAs<String>("userId")

// Check existence
if (context.has("permissions")) {
    val perms = context.getAs<List<String>>("permissions")
}

// Builder style
val newContext = context
    .with("traceId", "trace-123")
    .with("correlationId", "corr-456")

// DSL
val context = agentContext {
    this["userId"] = "user-123"
    this["sessionId"] = "session-456"
}
```

**Standard Context Keys:**

```kotlin
object ContextKeys {
    const val USER_ID = "userId"
    const val SESSION_ID = "sessionId"
    const val TENANT_ID = "tenantId"
    const val LOCALE = "locale"
    const val TIMEZONE = "timezone"
    const val CORRELATION_ID = "correlationId"
    const val REQUEST_ID = "requestId"
    const val TRACE_ID = "traceId"
    const val FEATURES = "features"
    const val PERMISSIONS = "permissions"
    const val METADATA = "metadata"
}
```

## SwarmAgent

Multi-agent coordination system with 5 execution strategies:

### Execution Strategies

```kotlin
enum class SwarmStrategyType {
    PARALLEL,       // Execute all agents simultaneously
    SEQUENTIAL,     // Execute in sequence, passing results forward
    CONSENSUS,      // Build consensus through multi-round discussion
    COMPETITION,    // Compete and select best result
    HIERARCHICAL    // Hierarchical delegation with levels
}
```

### Usage

```kotlin
val swarm = buildSwarmAgent {
    name = "Research Team"

    swarmTools {
        tool("search") { /* ... */ }
        aiConsensus(scoringAgent = llm)
    }

    quickSwarm {
        specialist("researcher", "Researcher", "research")
        specialist("analyst", "Analyst", "analysis")
        specialist("writer", "Writer", "writing")
    }
}

// Process automatically selects strategy
val result = swarm.processComm(Comm(
    content = "Research AI safety and write a report",
    from = "user"
))

// Check swarm status
val status = swarm.getSwarmStatus()
println("Active operations: ${status.activeOperations}")
println("Success rate: ${status.averageSuccessRate}")

// Get operation history
val history = swarm.getOperationHistory()
history.take(5).forEach { result ->
    println("${result.timestamp}: ${result.successRate * 100}% success")
}
```

## Real-World Examples

### Research Agent with Tools

```kotlin
val researchAgent = buildAgent {
    name = "Research Assistant"
    description = "AI-powered research assistant"
    capabilities = listOf("research", "analysis", "summarization")

    llm = anthropic(apiKey = env["ANTHROPIC_API_KEY"]!!) {
        model = "claude-3-5-sonnet-20241022"
        temperature = 0.7
    }

    tools {
        tool("web_search", "Search the web") {
            parameter("query", "string", "Search query", required = true)
            parameter("limit", "number", "Max results", required = false)

            execute(fun(params: Map<String, Any>): String {
                val query = params["query"] as String
                val limit = (params["limit"] as? Number)?.toInt() ?: 10

                val results = searchEngine.search(query, limit)
                return results.joinToString("\n") { result ->
                    "${result.title}: ${result.url}"
                }
            })
        }

        tool("summarize", "Summarize text") {
            parameter("text", "string", "Text to summarize", required = true)
            parameter("max_length", "number", "Max summary length", required = false)

            execute(fun(params: Map<String, Any>): String {
                val text = params["text"] as String
                val maxLength = (params["max_length"] as? Number)?.toInt() ?: 500

                return summarizer.summarize(text, maxLength)
            })
        }
    }

    instructions = """
        You are a research assistant that helps users find and analyze information.

        When given a research task:
        1. Use web_search to find relevant sources
        2. Analyze the information critically
        3. Use summarize if content is too long
        4. Provide comprehensive, well-cited answers

        Always cite your sources with URLs.
    """.trimIndent()
}

// Use the agent
val result = researchAgent.processComm(Comm(
    content = "Research the latest developments in quantum computing",
    from = "user"
))
```

### Custom Agent with State Management

```kotlin
class ConversationAgent(
    id: String,
    name: String = "Conversation Agent",
    description: String = "Maintains conversation context"
) : BaseAgent(id, name, description) {

    private data class ConversationState(
        val history: List<Comm>,
        val context: Map<String, Any>
    )

    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        // Load state
        val state = loadConversationState(comm.conversationId)

        // Process with context
        val response = processWithContext(comm, state)

        // Update state
        saveConversationState(
            comm.conversationId,
            state.copy(
                history = state.history + comm + response,
                context = extractContext(response)
            )
        )

        return SpiceResult.success(response)
    }

    private suspend fun loadConversationState(conversationId: String?): ConversationState {
        if (conversationId == null) return ConversationState(emptyList(), emptyMap())

        val saved = getState("conv:$conversationId") as? ConversationState
        return saved ?: ConversationState(emptyList(), emptyMap())
    }

    private suspend fun saveConversationState(
        conversationId: String?,
        state: ConversationState
    ) {
        if (conversationId != null) {
            saveState("conv:$conversationId", state)
        }
    }

    private fun processWithContext(comm: Comm, state: ConversationState): Comm {
        val contextSummary = if (state.history.isEmpty()) {
            "New conversation"
        } else {
            "Previous messages: ${state.history.size}, Context: ${state.context.keys}"
        }

        log(LogLevel.INFO, "Processing with context", mapOf(
            "context_summary" to contextSummary
        ))

        // Generate response using context
        val content = generateResponse(comm.content, state)

        return comm.reply(
            content = content,
            from = id,
            data = mapOf(
                "context_used" to state.context.keys.joinToString(",")
            )
        )
    }

    private fun extractContext(comm: Comm): Map<String, Any> {
        // Extract entities, intents, etc. from the response
        return mapOf(
            "last_topic" to extractTopic(comm.content),
            "entities" to extractEntities(comm.content)
        )
    }
}
```

### Multi-Agent Data Pipeline

```kotlin
val dataProcessingSwarm = buildSwarmAgent {
    name = "Data Processing Pipeline"
    description = "Multi-stage data processing system"

    // Shared tools
    swarmTools {
        tool("validate_data", "Validate data format") {
            parameter("data", "string", "Data to validate", required = true)
            execute(fun(params: Map<String, Any>): String {
                val data = params["data"] as String
                return if (isValidJson(data)) "valid" else "invalid"
            })
        }

        tool("transform_data", "Transform data") {
            parameter("data", "string", "Data to transform", required = true)
            parameter("format", "string", "Target format", required = true)
            execute(fun(params: Map<String, Any>): String {
                val data = params["data"] as String
                val format = params["format"] as String
                return transformData(data, format)
            })
        }

        tool("store_data", "Store processed data") {
            parameter("data", "string", "Data to store", required = true)
            execute(fun(params: Map<String, Any>): String {
                val data = params["data"] as String
                database.store(data)
                return "stored"
            })
        }
    }

    // Pipeline stages
    members {
        // Stage 1: Validation
        agent(buildAgent {
            name = "Validator"
            description = "Validates incoming data"
            llm = anthropic(...) { model = "claude-3-5-haiku-20241022" }
        })

        // Stage 2: Transformation
        agent(buildAgent {
            name = "Transformer"
            description = "Transforms data to target format"
            llm = anthropic(...) { model = "claude-3-5-sonnet-20241022" }
        })

        // Stage 3: Storage
        agent(buildAgent {
            name = "Archiver"
            description = "Stores processed data"
            llm = anthropic(...) { model = "claude-3-5-haiku-20241022" }
        })
    }
}

// Process data through pipeline (automatically uses SEQUENTIAL strategy)
val result = dataProcessingSwarm.processComm(Comm(
    content = "Process this data: {\"user\": \"Alice\", \"action\": \"login\"}",
    from = "data-source"
))

result.fold(
    onSuccess = { response ->
        println("Pipeline result: ${response.content}")
        println("Stages completed: ${response.data["participating_agents"]}")
    },
    onFailure = { error ->
        println("Pipeline failed: ${error.message}")
    }
)
```

## Best Practices

### 1. Use Appropriate Agent Types

```kotlin
// ✅ Good - Simple single-agent tasks
val agent = buildAgent {
    name = "Calculator"
    llm = anthropic(...)
    tools { tool("calculate") { /* ... */ } }
}

// ✅ Good - Multi-agent coordination
val swarm = buildSwarmAgent {
    name = "Analysis Team"
    members { /* multiple agents */ }
}

// ❌ Bad - Using swarm for simple tasks
val unnecessarySwarm = buildSwarmAgent {
    name = "Calculator Swarm"
    members { agent(simpleCalculator) } // Overkill!
}
```

### 2. Handle Errors Gracefully

```kotlin
// ✅ Good - Proper error handling
override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
    return try {
        val result = process(comm)
        SpiceResult.success(result)
    } catch (e: Exception) {
        log(LogLevel.ERROR, "Processing failed", mapOf("error" to e.message.orEmpty()))
        SpiceResult.failure(SpiceError(
            message = "Processing failed: ${e.message}",
            code = "PROCESSING_ERROR",
            cause = e
        ))
    }
}

// ❌ Bad - Swallowing errors
override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
    val result = process(comm) // Can throw!
    return SpiceResult.success(result)
}
```

### 3. Use Metrics for Monitoring

```kotlin
// ✅ Good - Monitor performance
class MonitoredAgent : BaseAgent(...) {
    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        val startTime = System.currentTimeMillis()

        return try {
            val result = process(comm)

            val duration = System.currentTimeMillis() - startTime
            log(LogLevel.INFO, "Processing complete", mapOf(
                "duration_ms" to duration
            ))

            if (duration > 5000) {
                publishEvent("slow_request", mapOf(
                    "duration_ms" to duration,
                    "commId" to comm.id
                ))
            }

            SpiceResult.success(result)
        } catch (e: Exception) {
            log(LogLevel.ERROR, "Processing failed", mapOf(
                "error" to e.message.orEmpty()
            ))
            SpiceResult.failure(SpiceError.from(e))
        }
    }
}

// Check metrics regularly
fun monitorAgent(agent: Agent) {
    val metrics = agent.getMetrics()

    if (metrics.getSuccessRate() < 0.95) {
        alertOps("Agent ${agent.id} success rate below threshold")
    }

    if (metrics.getAverageResponseTimeMs() > 10000) {
        alertOps("Agent ${agent.id} average response time too high")
    }
}
```

### 4. Implement Proper Lifecycle Management

```kotlin
// ✅ Good - Clean lifecycle
class ResourceAgent : BaseAgent(...) {
    private lateinit var connection: Connection

    override suspend fun initialize(runtime: AgentRuntime) {
        super.initialize(runtime)
        connection = createConnection()
        log(LogLevel.INFO, "Agent initialized")
    }

    override suspend fun cleanup() {
        try {
            connection.close()
            log(LogLevel.INFO, "Agent cleaned up")
        } catch (e: Exception) {
            log(LogLevel.ERROR, "Cleanup failed", mapOf("error" to e.message.orEmpty()))
        } finally {
            super.cleanup()
        }
    }
}

// Usage
val agent = ResourceAgent(...)
try {
    agent.initialize(runtime)
    // Use agent
} finally {
    agent.cleanup() // Always cleanup
}
```

### 5. Design for Testability

```kotlin
// ✅ Good - Testable design
class TestableAgent(
    id: String,
    name: String,
    private val processor: MessageProcessor, // Injected dependency
    config: AgentConfig = AgentConfig()
) : BaseAgent(id, name, "Testable agent", config = config) {

    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        val result = processor.process(comm.content)
        return SpiceResult.success(comm.reply(
            content = result,
            from = id
        ))
    }
}

// Easy to test with mock processor
@Test
fun testAgent() = runTest {
    val mockProcessor = MockMessageProcessor()
    val agent = TestableAgent(
        id = "test-agent",
        name = "Test Agent",
        processor = mockProcessor
    )

    val result = agent.processComm(Comm(
        content = "test",
        from = "user"
    ))

    assertTrue(result.isSuccess)
}
```

## Next Steps

- [Tool API](./tool) - Learn about tools and capabilities
- [DSL API](./dsl) - Master the DSL for building agents
- [Comm API](./comm) - Understand communication system
- [Swarm Documentation](../orchestration/swarm) - Multi-agent coordination
- [Creating Custom Tools](../tools-extensions/creating-tools) - Extend agent capabilities
