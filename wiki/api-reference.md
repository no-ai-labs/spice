# API Reference

## Core Classes

### Agent
```kotlin
interface Agent : Identifiable {
    val name: String
    val description: String
    val version: String
    suspend fun process(comm: Comm): Comm
}
```

### Comm (Communication)
```kotlin
data class Comm(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val from: String? = null,
    val to: String? = null,
    val type: CommType = CommType.TEXT,
    val role: CommRole = CommRole.USER,
    val timestamp: Instant = Instant.now(),
    val conversationId: String? = null,
    val thread: String? = null,
    val parentId: String? = null,
    val data: Map<String, Any> = emptyMap(),
    val media: List<MediaItem> = emptyList(),
    val mentions: List<String> = emptyList(),
    val priority: Priority = Priority.NORMAL,
    val encrypted: Boolean = false,
    val ttl: Long? = null,
    val expiresAt: Instant? = null
)
```

### Tool
```kotlin
interface Tool {
    val name: String
    val description: String
    val schema: ToolSchema
    suspend fun execute(params: Map<String, Any>): ToolResult
}

data class ToolResult(
    val success: Boolean,
    val result: String,
    val error: String? = null,
    val metadata: Map<String, Any> = emptyMap()
)

data class ToolSchema(
    val parameters: Map<String, ParameterSchema> = emptyMap()
)

data class ParameterSchema(
    val type: String,
    val description: String,
    val required: Boolean = false,
    val default: Any? = null
)
```

## DSL Functions

### buildAgent
```kotlin
fun buildAgent(block: AgentBuilder.() -> Unit): Agent

// Usage
val agent = buildAgent {
    id = "my-agent"
    name = "My Agent"
    description = "An example agent"
    version = "1.0.0"
    
    tools {
        add(myTool)
        useGlobal("calculator")
        tool("inline") { params ->
            ToolResult.success("Inline result")
        }
    }
    
    debugMode(true)
    debugPrefix("[DEBUG]")
    
    handle { comm ->
        comm.reply("Processed: ${comm.content}", id)
    }
}
```

### flow
```kotlin
fun flow(block: FlowBuilder.() -> Unit): Flow

// Usage
val workflow = flow {
    name = "My Workflow"
    description = "Example workflow"
    
    step("step1") { comm ->
        agent1.process(comm)
    }
    
    conditional("step2") { result ->
        if (result.data["success"] == true) {
            agent2.process(result)
        } else {
            errorComm("Step 1 failed", "flow")
        }
    }
    
    parallel("step3") { comm ->
        listOf(
            async { agent3.process(comm) },
            async { agent4.process(comm) }
        )
    }
    
    errorHandler { error, step ->
        errorComm("Error in $step: ${error.message}", "flow")
    }
}
```

### comm
```kotlin
fun comm(content: String, block: CommBuilder.() -> Unit = {}): Comm

// Usage
val message = comm("Hello") {
    from("user")
    to("agent")
    type(CommType.TEXT)
    role(CommRole.USER)
    conversation("conv-123")
    thread("thread-456")
    replyTo("parent-789")
    
    data("key", "value")
    data("key2" to "value2", "key3" to "value3")
    
    media(MediaItem("image.png", "https://...", "image/png"))
    mention("user1", "user2")
    
    priority(Priority.HIGH)
    encrypted()
    ttl(60000) // 60 seconds
}
```

### tool
```kotlin
fun tool(
    name: String,
    description: String = "",
    parameters: Map<String, ParameterSchema> = emptyMap(),
    handler: suspend (Map<String, Any>) -> ToolResult
): Tool

// Usage
val myTool = tool(
    name = "my-tool",
    description = "Does something useful",
    parameters = mapOf(
        "input" to ParameterSchema("string", "Input text", true)
    )
) { params ->
    val input = params["input"] as String
    ToolResult.success("Processed: $input")
}
```

## Registry APIs

### AgentRegistry
```kotlin
object AgentRegistry : SearchableRegistry<Agent>("agents") {
    fun register(agent: Agent)
    fun get(id: String): Agent?
    fun list(): List<Agent>
    fun unregister(id: String): Boolean
    fun clear()
    fun size(): Int
    
    // SearchableRegistry methods
    fun findBy(predicate: (Agent) -> Boolean): List<Agent>
    fun findFirstBy(predicate: (Agent) -> Boolean): Agent?
}
```

### ToolRegistry
```kotlin
object ToolRegistry {
    fun register(tool: Tool, namespace: String = "default")
    fun register(wrapper: ToolWrapper, namespace: String = "default")
    fun getTool(name: String, namespace: String = "default"): Tool?
    fun list(): List<Tool>
    fun getByNamespace(namespace: String): List<Tool>
    fun unregister(name: String, namespace: String = "default"): Boolean
    fun clear()
}
```

### CommHub
```kotlin
object CommHub {
    suspend fun send(comm: Comm, agentId: String): Comm
    suspend fun broadcast(comm: Comm, filter: List<String>? = null): List<CommResult>
    fun register(agent: Agent)
    fun unregister(agentId: String): Boolean
    fun agents(): List<Agent>
    fun history(limit: Int = 100): List<Comm>
    fun getAnalytics(): CommHubAnalytics
    fun reset()
}

data class CommHubAnalytics(
    val totalComms: Long,
    val activeAgents: Int,
    val messageRate: Double,
    val queueSize: Int,
    val errorRate: Double
)
```

## Enums and Constants

### CommType
```kotlin
enum class CommType {
    TEXT,
    COMMAND,
    DATA,
    MEDIA,
    EVENT,
    SYSTEM,
    ERROR,
    STREAM
}
```

### CommRole
```kotlin
enum class CommRole {
    USER,
    ASSISTANT,
    SYSTEM,
    FUNCTION,
    TOOL
}
```

### Priority
```kotlin
enum class Priority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL
}
```

### TaskType (Swarm)
```kotlin
enum class TaskType {
    CONSENSUS,
    DISTRIBUTED,
    COMPETITIVE,
    COLLABORATIVE
}
```

### ExecutionStrategy (Swarm)
```kotlin
enum class ExecutionStrategy {
    SEQUENTIAL,
    PARALLEL,
    ADAPTIVE,
    ROUND_ROBIN
}
```

## Extension Functions

### Comm Extensions
```kotlin
// Reply to a message
fun Comm.reply(content: String, from: String): Comm

// Modify data
fun Comm.withData(key: String, value: Any): Comm
fun Comm.withData(vararg pairs: Pair<String, Any>): Comm

// Priority shortcuts
fun Comm.critical(): Comm
fun Comm.highPriority(): Comm
fun Comm.lowPriority(): Comm

// Encryption
fun Comm.encrypt(): Comm

// TTL
fun Comm.expires(ttlMillis: Long): Comm

// Check expiration
fun Comm.isExpired(): Boolean
```

### Quick Comm Creation
```kotlin
fun quickComm(
    content: String,
    from: String? = null,
    to: String? = null,
    type: CommType = CommType.TEXT,
    role: CommRole = CommRole.USER
): Comm

fun systemComm(content: String, to: String? = null): Comm

fun errorComm(error: String, to: String? = null): Comm
```

## Built-in Tools

### calculatorTool()
```kotlin
fun calculatorTool(): Tool
// Evaluates mathematical expressions
// Parameters:
//   - expression: String (required)
// Returns: Calculated result as string
```

### textProcessorTool()
```kotlin
fun textProcessorTool(): Tool
// Processes text with various operations
// Parameters:
//   - text: String (required)
//   - operation: String (required)
//     Options: uppercase, lowercase, reverse, wordcount, sentiment
// Returns: Processed text or analysis result
```

### dateTimeTool()
```kotlin
fun dateTimeTool(): Tool
// Date and time operations
// Parameters:
//   - operation: String (required)
//     Options: current, now, date, time, timestamp, format
//   - format: String (optional, for format operation)
// Returns: Formatted date/time string
```

### randomTool()
```kotlin
fun randomTool(): Tool
// Generates random values
// Parameters:
//   - type: String (required)
//     Options: number, string, uuid, choice
//   - min: Number (optional, for number type)
//   - max: Number (optional, for number type)
//   - length: Number (optional, for string type)
//   - choices: String (optional, comma-separated for choice type)
// Returns: Generated random value
```

## Advanced APIs

### SwarmAgent
```kotlin
class SwarmAgent(
    override val id: String,
    override val name: String,
    override val description: String = ""
) : Agent {
    fun addWorker(agent: Agent, weight: Double = 1.0)
    fun removeWorker(agentId: String)
    fun execute(task: SwarmTask): SwarmResult
    fun setCoordinator(coordinator: SwarmCoordinator)
    fun setScoringFunction(scorer: (List<WorkerResult>) -> Score)
    fun enableEmergence(system: EmergentBehaviorSystem)
    fun onEmergence(pattern: String, handler: (EmergentPattern) -> Unit)
}

data class SwarmTask(
    val type: TaskType,
    val input: Comm,
    val strategy: ExecutionStrategy = ExecutionStrategy.PARALLEL,
    val timeout: Long = 5000
)

data class SwarmResult(
    val consensus: String?,
    val confidence: Double,
    val workerResults: List<WorkerResult>,
    val emergentPatterns: List<EmergentPattern>
)
```

### MCPIntegration
```kotlin
class MCPIntegration(config: MCPConfig) {
    suspend fun process(
        capability: String,
        input: Any,
        parameters: Map<String, Any> = emptyMap()
    ): MCPResult
    
    fun getCapabilities(): List<String>
    fun getParameterSchema(capability: String): Map<String, ParameterSchema>
    fun health(): MCPHealth
}
```

### VectorStore
```kotlin
class VectorStore(
    val embeddingModel: String,
    val dimension: Int
) {
    suspend fun addDocument(
        id: String,
        content: String,
        metadata: Map<String, Any> = emptyMap()
    )
    
    suspend fun search(
        query: String,
        limit: Int = 10,
        threshold: Double = 0.0
    ): List<SearchResult>
    
    suspend fun delete(id: String): Boolean
    fun clear()
    fun size(): Int
}

data class SearchResult(
    val id: String,
    val content: String,
    val score: Double,
    val metadata: Map<String, Any>
)
```

## Error Types

```kotlin
class AgentNotFoundException(agentId: String) : Exception("Agent not found: $agentId")
class ToolNotFoundException(toolName: String) : Exception("Tool not found: $toolName")
class ProcessingException(message: String, cause: Throwable? = null) : Exception(message, cause)
class TimeoutException(message: String) : Exception(message)
class ValidationException(message: String) : Exception(message)
```

## Utility Functions

### Registry Utilities
```kotlin
fun <T : Identifiable> Registry<T>.getOrRegister(
    id: String,
    factory: () -> T
): T

fun <T : Identifiable> SearchableRegistry<T>.findByName(
    name: String
): List<T>
```

### Tool Utilities
```kotlin
fun SimpleTool(
    name: String,
    description: String,
    parameterSchemas: Map<String, ParameterSchema>,
    handler: suspend (Map<String, Any>) -> ToolResult
): Tool

fun ToolWrapper(
    id: String,
    tool: Tool
): Identifiable
```

### Flow Utilities
```kotlin
suspend fun Flow.executeWithTimeout(
    input: Comm,
    timeout: Long
): Comm?

fun Flow.withRetry(
    maxRetries: Int = 3,
    delayMillis: Long = 1000
): Flow
```