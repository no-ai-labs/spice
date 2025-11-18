# Registry API

Centralized registry system for managing agents, tools, and resources in Spice Framework.

## Overview

The Registry system provides **thread-safe**, **type-safe** registration and discovery for all framework components:

- **AgentRegistry** - Register and discover agents by capability, tag, or provider
- **ToolRegistry** - Manage tools with namespace support and metadata
- **VectorStoreRegistry** - Centralized vector store management
- **ToolChainRegistry** - Register tool chains for complex workflows
- **FlowRegistry** - Register and reuse agent flows

**Key Features:**
- ✅ Thread-safe concurrent access
- ✅ Type-safe generic implementation
- ✅ Advanced search capabilities
- ✅ Namespace isolation
- ✅ Metadata support
- ✅ Automatic deduplication

## Core Registry

### Registry&lt;T&gt;

Generic base registry for any identifiable type:

```kotlin
open class Registry<T : Identifiable>(
    val name: String
) {
    fun register(item: T): T
    fun register(item: T, override: Boolean): T
    fun get(id: String): T?
    fun getAll(): List<T>
    fun has(id: String): Boolean
    fun unregister(id: String): Boolean
    fun clear()
    fun size(): Int
    fun getOrRegister(id: String, factory: () -> T): T
}
```

**Usage:**

```kotlin
// Define custom identifiable type
// v0.9.0: Config supports native types and null
data class MyResource(
    override val id: String,
    val name: String,
    @Serializable(with = AnyValueMapSerializer::class)
    val config: Map<String, Any?>
) : Identifiable

// Create registry
val resourceRegistry = Registry<MyResource>("resources")

// Register items (v0.9.0: native types in config)
val resource1 = MyResource(
    "res-1",
    "Database",
    mapOf(
        "host" to "localhost",
        "port" to 5432,           // v0.9.0: Int natively supported
        "ssl" to true,            // v0.9.0: Boolean natively supported
        "timeout_ms" to 30000     // v0.9.0: Int natively supported
    )
)
resourceRegistry.register(resource1)

// Get by ID
val retrieved = resourceRegistry.get("res-1")
println(retrieved?.name) // "Database"

// Check existence
if (resourceRegistry.has("res-1")) {
    println("Resource exists")
}

// Get all
val allResources = resourceRegistry.getAll()
println("Total resources: ${resourceRegistry.size()}")

// Get or create (v0.9.0: native types in config)
val resource2 = resourceRegistry.getOrRegister("res-2") {
    MyResource(
        "res-2",
        "Cache",
        mapOf(
            "ttl" to 3600,        // v0.9.0: Int natively supported
            "max_size" to 1000,   // v0.9.0: Int natively supported
            "enabled" to true     // v0.9.0: Boolean natively supported
        )
    )
}

// Unregister
resourceRegistry.unregister("res-1")

// Clear all
resourceRegistry.clear()
```

### Identifiable

Base interface for registrable items:

```kotlin
interface Identifiable {
    val id: String
}
```

**Example Implementation:**

```kotlin
data class CustomAgent(
    override val id: String,
    val name: String,
    val type: String
) : Identifiable, Agent {
    // Agent implementation
}
```

### SearchableRegistry&lt;T&gt;

Extended registry with filtering capabilities:

```kotlin
abstract class SearchableRegistry<T : Identifiable>(
    name: String
) : Registry<T>(name) {
    fun findBy(predicate: (T) -> Boolean): List<T>
    fun findFirstBy(predicate: (T) -> Boolean): T?
}
```

**Usage:**

```kotlin
class UserRegistry : SearchableRegistry<User>("users")

val userRegistry = UserRegistry()

// Register users
userRegistry.register(User("user-1", "Alice", role = "admin"))
userRegistry.register(User("user-2", "Bob", role = "user"))
userRegistry.register(User("user-3", "Charlie", role = "admin"))

// Find by predicate
val admins = userRegistry.findBy { user -> user.role == "admin" }
println("Admins: ${admins.size}") // 2

// Find first matching
val firstUser = userRegistry.findFirstBy { user -> user.name.startsWith("B") }
println(firstUser?.name) // "Bob"
```

## AgentRegistry

Specialized registry for agents with advanced search:

```kotlin
object AgentRegistry : SearchableRegistry<Agent>("agents") {
    fun findByCapability(capability: String): List<Agent>
    fun findByTag(tag: String): List<Agent>
    fun findByProvider(provider: String): List<Agent>
}
```

### Register Agents

```kotlin
import io.github.noailabs.spice.springboot.ai.factory.SpringAIAgentFactory
import io.github.noailabs.spice.springboot.ai.factory.AnthropicConfig

// Create agent with Spring AI factory
val factory: SpringAIAgentFactory = ... // Inject or create

val agent = factory.anthropic(
    model = "claude-3-5-sonnet-20241022",
    config = AnthropicConfig(
        agentId = "research-assistant",
        agentName = "Research Assistant",
        agentDescription = "AI-powered research assistant",
        temperature = 0.7
    )
)

AgentRegistry.register(agent)

// Register multiple agents
listOf(agent1, agent2, agent3).forEach { agent ->
    AgentRegistry.register(agent)
}

// Check if registered
if (AgentRegistry.has("research-assistant")) {
    println("Agent registered")
}
```

### Find by Capability

Find agents with specific capabilities:

```kotlin
import io.github.noailabs.spice.springboot.ai.factory.AnthropicConfig

// Register agents with capabilities
val dataAnalyst = factory.anthropic(
    model = "claude-3-5-sonnet-20241022",
    config = AnthropicConfig(
        agentId = "data-analyst",
        agentName = "Data Analyst",
        agentDescription = "Data analysis specialist #data-analysis #visualization #statistics"
    )
)
AgentRegistry.register(dataAnalyst)

val codeReviewer = factory.anthropic(
    model = "claude-3-5-sonnet-20241022",
    config = AnthropicConfig(
        agentId = "code-reviewer",
        agentName = "Code Reviewer",
        agentDescription = "Code review specialist #code-review #security #testing"
    )
)
AgentRegistry.register(codeReviewer)

// Find agents by capability (searches in description)
val dataAgents = AgentRegistry.findByCapability("data-analysis")
println("Data agents: ${dataAgents.size}") // 1

val reviewers = AgentRegistry.findByCapability("code-review")
println("Code reviewers: ${reviewers.size}") // 1
```

**Dynamic Agent Discovery:**

```kotlin
import io.github.noailabs.spice.SpiceMessage

suspend fun getAgentForTask(task: String): Agent? {
    return when {
        task.contains("data") -> AgentRegistry.findByCapability("data-analysis").firstOrNull()
        task.contains("code") -> AgentRegistry.findByCapability("code-review").firstOrNull()
        task.contains("research") -> AgentRegistry.findByCapability("research").firstOrNull()
        else -> null
    }
}

// Use discovered agent
val agent = getAgentForTask("Analyze this dataset")
if (agent != null) {
    val result = agent.processMessage(
        SpiceMessage.create("Analyze data", "user")
    )
}
```

### Find by Tag

Find agents by tags (checks name, description, metadata):

```kotlin
// Register agents with tags in description
val claudeAgent = factory.anthropic(
    model = "claude-3-5-sonnet-20241022",
    config = AnthropicConfig(
        agentId = "claude-assistant",
        agentName = "Claude Assistant",
        agentDescription = "Anthropic Claude-powered assistant #anthropic #llm"
    )
)
AgentRegistry.register(claudeAgent)

// Find by tag
val anthropicAgents = AgentRegistry.findByTag("anthropic")
println("Anthropic agents: ${anthropicAgents.size}")

val llmAgents = AgentRegistry.findByTag("llm")
println("LLM agents: ${llmAgents.size}")
```

### Find by Provider

Find agents by LLM provider:

```kotlin
import io.github.noailabs.spice.springboot.ai.factory.OpenAIConfig

// Register agents with different providers
val claudeAgent = factory.anthropic(
    model = "claude-3-5-sonnet-20241022",
    config = AnthropicConfig(
        agentId = "claude-agent",
        agentName = "Claude Agent"
    )
)
AgentRegistry.register(claudeAgent)

val gptAgent = factory.openai(
    model = "gpt-4",
    config = OpenAIConfig(
        agentId = "gpt-agent",
        agentName = "GPT Agent"
    )
)
AgentRegistry.register(gptAgent)

// Find by provider (searches in name/description)
val claudeAgents = AgentRegistry.findByProvider("claude")
val openaiAgents = AgentRegistry.findByProvider("gpt")

println("Claude agents: ${claudeAgents.size}")
println("OpenAI agents: ${openaiAgents.size}")
```

### List All Agents

```kotlin
// Get all registered agents
val allAgents = AgentRegistry.getAll()

println("=== Registered Agents ===")
allAgents.forEach { agent ->
    println("${agent.name}: ${agent.description}")
    println("  Capabilities: ${agent.capabilities.joinToString(", ")}")
    println("  Tools: ${agent.getTools().size}")
}

// Get specific agent
val agent = AgentRegistry.get("research-assistant")
if (agent != null) {
    println("Found: ${agent.name}")
}
```

## ToolRegistry

Specialized registry for tools with namespace support:

```kotlin
object ToolRegistry : Registry<ToolWrapper>("tools") {
    // Registration
    fun register(tool: Tool, namespace: String = "global"): Tool
    fun register(agentTool: AgentTool, namespace: String = "global"): Tool

    // Retrieval
    fun getTool(name: String, namespace: String = "global"): Tool?
    fun getByNamespace(namespace: String): List<Tool>
    fun hasTool(name: String, namespace: String = "global"): Boolean
    fun ensureRegistered(name: String): Boolean

    // Search
    fun getByTag(tag: String): List<Tool>
    fun getBySource(source: String): List<Tool>
    fun getAgentTools(): List<Pair<Tool, Map<String, Any>>>
}
```

### Register Tools

```kotlin
// Register tool in global namespace
val calculator = object : Tool {
    override val name = "calculate"
    override val description = "Performs arithmetic"
    override val schema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "a" to ParameterSchema("number", "First number", required = true),
            "b" to ParameterSchema("number", "Second number", required = true),
            "op" to ParameterSchema("string", "Operation", required = true)
        )
    )

    // v0.9.0: Updated to Map<String, Any?>
    override suspend fun execute(parameters: Map<String, Any?>): SpiceResult<ToolResult> {
        val a = (parameters["a"] as? Number)?.toDouble()
            ?: throw IllegalArgumentException("Missing 'a'")
        val b = (parameters["b"] as? Number)?.toDouble()
            ?: throw IllegalArgumentException("Missing 'b'")
        val op = parameters["op"]?.toString()
            ?: throw IllegalArgumentException("Missing 'op'")

        val result = when (op) {
            "+" -> a + b
            "-" -> a - b
            "*" -> a * b
            "/" -> a / b
            else -> throw IllegalArgumentException("Unknown operation")
        }

        return SpiceResult.success(ToolResult.success(result.toString()))
    }
}

ToolRegistry.register(calculator)

// Register in specific namespace
ToolRegistry.register(calculator, namespace = "math")
```

### Namespace Isolation

Organize tools by namespace to avoid conflicts:

```kotlin
// Math namespace
ToolRegistry.register(addTool, namespace = "math")
ToolRegistry.register(subtractTool, namespace = "math")
ToolRegistry.register(multiplyTool, namespace = "math")

// String namespace
ToolRegistry.register(uppercaseTool, namespace = "string")
ToolRegistry.register(lowercaseTool, namespace = "string")
ToolRegistry.register(trimTool, namespace = "string")

// Get tools by namespace
val mathTools = ToolRegistry.getByNamespace("math")
println("Math tools: ${mathTools.size}") // 3

val stringTools = ToolRegistry.getByNamespace("string")
println("String tools: ${stringTools.size}") // 3

// Get specific tool from namespace
val addTool = ToolRegistry.getTool("add", namespace = "math")
if (addTool != null) {
    val result = addTool.execute(mapOf("a" to 10, "b" to 20))
}
```

### Global vs Namespaced

```kotlin
// Register in global namespace (default)
ToolRegistry.register(searchTool) // Accessible as "search"

// Register in namespace
ToolRegistry.register(searchTool, namespace = "web") // Accessible as "web::search"

// Retrieve
val globalSearch = ToolRegistry.getTool("search") // From global
val webSearch = ToolRegistry.getTool("search", namespace = "web") // From web namespace

// Check existence
if (ToolRegistry.hasTool("search")) {
    println("Global search exists")
}

if (ToolRegistry.hasTool("search", namespace = "web")) {
    println("Web search exists")
}
```

### Search by Tag

```kotlin
// Register tools with metadata
val agentTool = AgentTool(
    name = "analyze_code",
    description = "Analyzes code quality",
    implementationType = "kotlin",
    tags = listOf("code", "analysis", "quality"),
    parameters = mapOf(/* ... */),
    implementation = { params -> /* ... */ }
)

ToolRegistry.register(agentTool)

// Find by tag
val codeTools = ToolRegistry.getByTag("code")
println("Code tools: ${codeTools.size}")

val analysisTools = ToolRegistry.getByTag("analysis")
println("Analysis tools: ${analysisTools.size}")
```

### Search by Source

```kotlin
// Tools are automatically tagged by source
// - "direct" - Registered via Tool interface
// - "agent-tool" - Registered via AgentTool

val directTools = ToolRegistry.getBySource("direct")
println("Direct tools: ${directTools.size}")

val agentTools = ToolRegistry.getBySource("agent-tool")
println("Agent tools: ${agentTools.size}")

// Get AgentTool metadata
val agentToolsWithMetadata = ToolRegistry.getAgentTools()
agentToolsWithMetadata.forEach { (tool, metadata) ->
    println("Tool: ${tool.name}")
    println("  Tags: ${metadata["tags"]}")
    println("  Metadata: ${metadata["metadata"]}")
}
```

### Dynamic Tool Discovery

```kotlin
fun findToolForTask(task: String): Tool? {
    return when {
        task.contains("code") -> ToolRegistry.getByTag("code").firstOrNull()
        task.contains("data") -> ToolRegistry.getByTag("data").firstOrNull()
        task.contains("search") -> ToolRegistry.getTool("search")
        else -> null
    }
}

// Use discovered tool
val tool = findToolForTask("Analyze this code")
if (tool != null) {
    val result = tool.execute(mapOf("code" to sourceCode))
}
```

## VectorStoreRegistry

Centralized management for vector stores:

```kotlin
object VectorStoreRegistry {
    fun register(name: String, store: VectorStore, config: VectorStoreConfig, agentId: String? = null): Boolean
    fun get(name: String): VectorStore?
    fun getWithConfig(name: String): Pair<VectorStore, VectorStoreConfig>?
    fun exists(name: String): Boolean
    fun unregister(name: String): Boolean
    fun list(): List<String>
    fun listByAgent(agentId: String): List<String>
    fun clear()
    fun getOrCreate(name: String, config: VectorStoreConfig, agentId: String? = null): VectorStore
}
```

### Register Vector Stores

```kotlin
// Create config
val config = VectorStoreConfig(
    provider = "qdrant",
    host = "localhost",
    port = 6333,
    apiKey = null,
    collection = "documents",
    vectorSize = 384
)

// Create vector store
val vectorStore = QdrantVectorStore(
    host = config.host,
    port = config.port,
    apiKey = config.apiKey
)

// Register
VectorStoreRegistry.register(
    name = "docs",
    store = vectorStore,
    config = config,
    agentId = "research-agent"
)

// Check existence
if (VectorStoreRegistry.exists("docs")) {
    println("Vector store registered")
}
```

### Get or Create

Lazy initialization pattern:

```kotlin
val agent = buildAgent {
    name = "RAG Agent"

    // Get or create vector store
    val vectorStore = VectorStoreRegistry.getOrCreate(
        name = "knowledge-base",
        config = VectorStoreConfig(
            provider = "qdrant",
            host = "localhost",
            port = 6333
        ),
        agentId = id
    )

    // Use vector store
    tools {
        tool("search_knowledge", "Search knowledge base") {
            parameter("query", "string", required = true)

            execute(fun(params: Map<String, Any>): String {
                val query = params["query"] as String
                val results = vectorStore.searchByText(
                    collectionName = "documents",
                    queryText = query,
                    topK = 5
                )
                return results.joinToString("\n") { it.metadata["content"] as String }
            })
        }
    }
}
```

### List Vector Stores

```kotlin
// List all vector stores
val allStores = VectorStoreRegistry.list()
println("Registered vector stores:")
allStores.forEach { name ->
    println("  - $name")
}

// List by agent
val agentStores = VectorStoreRegistry.listByAgent("research-agent")
println("Vector stores for research-agent:")
agentStores.forEach { name ->
    println("  - $name")
}
```

### Get with Config

```kotlin
// Get vector store with its configuration
val (store, config) = VectorStoreRegistry.getWithConfig("docs")
    ?: error("Vector store not found")

println("Provider: ${config.provider}")
println("Host: ${config.host}:${config.port}")
println("Collection: ${config.collection}")

// Use store
val results = store.searchByText(
    collectionName = config.collection,
    queryText = "machine learning",
    topK = 10
)
```

### Cleanup

```kotlin
// Unregister specific store
VectorStoreRegistry.unregister("docs")

// Clear all stores
VectorStoreRegistry.clear()
```

## ToolChainRegistry

Register tool chains for reuse:

```kotlin
object ToolChainRegistry : Registry<ModernToolChain>("toolchains")
```

**Usage:**

```kotlin
// Create tool chain
val dataProcessingChain = ModernToolChain("data-processing")
    .addTool(validateTool)
    .addTool(transformTool)
    .addTool(analyzeTool)

// Register
ToolChainRegistry.register(dataProcessingChain)

// Retrieve and use
val chain = ToolChainRegistry.get("data-processing")
if (chain != null) {
    val result = chain.execute(inputData)
}
```

## FlowRegistry

Register and reuse agent flows:

```kotlin
object FlowRegistry : Registry<CoreFlow>("flows")
```

**Usage:**

```kotlin
// Create flow
val orderFlow = buildFlow {
    name = "Order Processing"
    description = "E-commerce order processing flow"

    step("validate") { /* ... */ }
    step("payment") { /* ... */ }
    step("fulfillment") { /* ... */ }
}

// Register
FlowRegistry.register(orderFlow)

// Retrieve and execute
val flow = FlowRegistry.get("order-processing")
if (flow != null) {
    val result = flow.execute(orderData)
}
```

## Best Practices

### 1. Use Namespaces for Organization

```kotlin
// ✅ GOOD - Organized by namespace
ToolRegistry.register(addTool, namespace = "math")
ToolRegistry.register(searchTool, namespace = "web")
ToolRegistry.register(queryTool, namespace = "database")

// Get tools by context
val mathTools = ToolRegistry.getByNamespace("math")

// ❌ BAD - Everything in global namespace
ToolRegistry.register(addTool)
ToolRegistry.register(searchTool)
ToolRegistry.register(queryTool)
// Name conflicts, hard to organize
```

### 2. Register at Startup

```kotlin
// ✅ GOOD - Register resources at startup
fun initializeApplication() {
    // Register agents
    AgentRegistry.register(researchAgent)
    AgentRegistry.register(analysisAgent)

    // Register tools
    ToolRegistry.register(calculatorTool, namespace = "math")
    ToolRegistry.register(searchTool, namespace = "web")

    // Register vector stores
    VectorStoreRegistry.getOrCreate("knowledge-base", knowledgeConfig)
}

// ❌ BAD - Register on demand (may cause race conditions)
fun getAgent(): Agent {
    if (!AgentRegistry.has("research-agent")) {
        AgentRegistry.register(createAgent()) // Race condition!
    }
    return AgentRegistry.get("research-agent")!!
}
```

### 3. Use getOrRegister for Lazy Initialization

```kotlin
// ✅ GOOD - Thread-safe lazy initialization
val agent = AgentRegistry.getOrRegister("research-agent") {
    buildAgent {
        name = "Research Agent"
        llm = anthropic(...) { model = "claude-3-5-sonnet-20241022" }
    }
}

// ❌ BAD - Manual check-then-act (not thread-safe)
if (!AgentRegistry.has("research-agent")) {
    val agent = buildAgent { /* ... */ }
    AgentRegistry.register(agent)
}
val agent = AgentRegistry.get("research-agent")
```

### 4. Clean Up Resources

```kotlin
// ✅ GOOD - Clean up when done
fun shutdown() {
    // Unregister agents
    AgentRegistry.clear()

    // Unregister tools
    ToolRegistry.clear()

    // Clean up vector stores
    VectorStoreRegistry.clear()
}

// ❌ BAD - Leave resources registered
// Memory leaks, stale references
```

### 5. Use Tags for Discovery

```kotlin
// ✅ GOOD - Rich metadata with tags
val agentTool = AgentTool(
    name = "analyze_sentiment",
    description = "Sentiment analysis tool",
    tags = listOf("nlp", "analysis", "sentiment", "text"),
    implementationType = "kotlin",
    parameters = mapOf(/* ... */),
    implementation = { params -> /* ... */ }
)

ToolRegistry.register(agentTool)

// Easy discovery
val nlpTools = ToolRegistry.getByTag("nlp")
val analysisTools = ToolRegistry.getByTag("analysis")

// ❌ BAD - No metadata
val tool = SimpleTool("analyze_sentiment")
ToolRegistry.register(tool)
// Hard to discover, no context
```

## Real-World Examples

### Example 1: Dynamic Agent Router

Route requests to appropriate agents based on capabilities:

```kotlin
import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.springboot.ai.factory.SpringAIAgentFactory
import io.github.noailabs.spice.springboot.ai.factory.AnthropicConfig

class AgentRouter(private val factory: SpringAIAgentFactory) {
    init {
        // Register agents with capabilities
        val codeReviewer = factory.anthropic(
            model = "claude-3-5-sonnet-20241022",
            config = AnthropicConfig(
                agentId = "code-reviewer",
                agentName = "Code Reviewer",
                agentDescription = "Code review specialist #code-review #security #testing"
            )
        )
        AgentRegistry.register(codeReviewer)

        val dataAnalyst = factory.anthropic(
            model = "claude-3-5-sonnet-20241022",
            config = AnthropicConfig(
                agentId = "data-analyst",
                agentName = "Data Analyst",
                agentDescription = "Data analysis specialist #data-analysis #visualization #statistics"
            )
        )
        AgentRegistry.register(dataAnalyst)

        val researchAssistant = factory.anthropic(
            model = "claude-3-5-sonnet-20241022",
            config = AnthropicConfig(
                agentId = "research-assistant",
                agentName = "Research Assistant",
                agentDescription = "Research specialist #research #summarization #fact-checking"
            )
        )
        AgentRegistry.register(researchAssistant)
    }

    suspend fun routeRequest(request: String): SpiceResult<SpiceMessage> {
        // Determine required capability
        val capability = when {
            request.contains("code", ignoreCase = true) -> "code-review"
            request.contains("data", ignoreCase = true) -> "data-analysis"
            request.contains("research", ignoreCase = true) -> "research"
            else -> null
        }

        if (capability == null) {
            return SpiceResult.failure(SpiceError.validationError(
                "Could not determine appropriate agent"
            ))
        }

        // Find agent with capability
        val agent = AgentRegistry.findByCapability(capability).firstOrNull()
            ?: return SpiceResult.failure(SpiceError.validationError(
                "No agent found for capability: $capability"
            ))

        // Route to agent
        return agent.processMessage(
            SpiceMessage.create(request, "router")
        )
    }
}

// Usage
val factory: SpringAIAgentFactory = ... // Inject
val router = AgentRouter(factory)

val result = router.routeRequest("Review this code for security issues")
when (result) {
    is SpiceResult.Success -> {
        val response = result.value
        println("Agent: ${response.from}")
        println("Response: ${response.content}")
    }
    is SpiceResult.Failure -> {
        println("Routing failed: ${result.error.message}")
    }
}
```

### Example 2: Tool Marketplace

Dynamic tool discovery and execution:

```kotlin
class ToolMarketplace {
    init {
        // Register tools with rich metadata
        registerMarketplaceTool(
            name = "sentiment_analysis",
            tags = listOf("nlp", "analysis", "sentiment"),
            category = "text-processing",
            implementation = { params ->
                val text = params["text"] as String
                analyzeSentiment(text)
            }
        )

        registerMarketplaceTool(
            name = "image_classification",
            tags = listOf("vision", "classification", "image"),
            category = "computer-vision",
            implementation = { params ->
                val imageUrl = params["image_url"] as String
                classifyImage(imageUrl)
            }
        )

        registerMarketplaceTool(
            name = "translation",
            tags = listOf("nlp", "translation", "language"),
            category = "text-processing",
            implementation = { params ->
                val text = params["text"] as String
                val targetLang = params["target_language"] as String
                translate(text, targetLang)
            }
        )
    }

    private fun registerMarketplaceTool(
        name: String,
        tags: List<String>,
        category: String,
        implementation: (Map<String, Any>) -> String
    ) {
        val agentTool = AgentTool(
            name = name,
            description = "Marketplace tool: $name",
            tags = tags + category,
            implementationType = "kotlin",
            metadata = mapOf("category" to category),
            parameters = mapOf(),
            implementation = implementation
        )

        ToolRegistry.register(agentTool, namespace = "marketplace")
    }

    fun searchTools(query: String): List<Tool> {
        // Search by tag
        val tagResults = ToolRegistry.getByTag(query)

        // Search by namespace
        val marketplaceTools = ToolRegistry.getByNamespace("marketplace")

        // Filter by name match
        val nameMatches = marketplaceTools.filter { tool ->
            tool.name.contains(query, ignoreCase = true) ||
            tool.description.contains(query, ignoreCase = true)
        }

        return (tagResults + nameMatches).distinct()
    }

    suspend fun executeTool(toolName: String, params: Map<String, Any>): SpiceResult<ToolResult> {
        val tool = ToolRegistry.getTool(toolName, namespace = "marketplace")
            ?: return SpiceResult.failure(SpiceError(
                message = "Tool not found: $toolName",
                code = "TOOL_NOT_FOUND"
            ))

        return tool.execute(params)
    }
}

// Usage
val marketplace = ToolMarketplace()

// Search for NLP tools
val nlpTools = marketplace.searchTools("nlp")
println("NLP tools:")
nlpTools.forEach { tool ->
    println("  - ${tool.name}: ${tool.description}")
}

// Execute tool
val result = marketplace.executeTool("sentiment_analysis", mapOf(
    "text" to "This product is amazing!"
))

result.fold(
    onSuccess = { toolResult ->
        if (toolResult.success) {
            println("Result: ${toolResult.result}")
        } else {
            println("Error: ${toolResult.error}")
        }
    },
    onFailure = { error ->
        println("Execution failed: ${error.message}")
    }
)
```

### Example 3: Multi-Tenant Vector Store Management

Manage vector stores per tenant:

```kotlin
class MultiTenantVectorStoreManager {
    fun getVectorStoreForTenant(tenantId: String): VectorStore {
        val storeName = "tenant-$tenantId"

        return VectorStoreRegistry.getOrCreate(
            name = storeName,
            config = VectorStoreConfig(
                provider = "qdrant",
                host = "localhost",
                port = 6333,
                collection = tenantId,
                vectorSize = 384
            ),
            agentId = "tenant-manager"
        )
    }

    fun listTenantsWithStores(): List<String> {
        return VectorStoreRegistry.list()
            .filter { it.startsWith("tenant-") }
            .map { it.removePrefix("tenant-") }
    }

    fun cleanupTenant(tenantId: String) {
        val storeName = "tenant-$tenantId"
        VectorStoreRegistry.unregister(storeName)
    }
}

// Usage
val manager = MultiTenantVectorStoreManager()

// Get tenant-specific store
val store = manager.getVectorStoreForTenant("tenant-123")

// Index documents for tenant
store.add(
    collectionName = "tenant-123",
    vectors = listOf(
        VectorEntry(
            id = "doc-1",
            vector = embedding1,
            metadata = mapOf("content" to "Document 1")
        )
    )
)

// Search within tenant
val results = store.searchByText(
    collectionName = "tenant-123",
    queryText = "search query",
    topK = 5
)

// List tenants
val tenants = manager.listTenantsWithStores()
println("Tenants with vector stores: ${tenants.size}")

// Cleanup
manager.cleanupTenant("tenant-123")
```

## Thread Safety

All registries are **thread-safe** using `ConcurrentHashMap`:

```kotlin
// Safe concurrent registration
launch {
    ToolRegistry.register(tool1, namespace = "math")
}

launch {
    ToolRegistry.register(tool2, namespace = "math")
}

launch {
    ToolRegistry.register(tool3, namespace = "math")
}

// Safe concurrent access
launch {
    val tools = ToolRegistry.getByNamespace("math")
    println("Math tools: ${tools.size}")
}

launch {
    val tool = ToolRegistry.getTool("add", namespace = "math")
    tool?.execute(mapOf("a" to 1, "b" to 2))
}
```

## Performance Considerations

### 1. Use getOrRegister for Lazy Init

```kotlin
// ✅ GOOD - Lazy, thread-safe
val tool = ToolRegistry.getOrRegister("expensive-tool") {
    createExpensiveTool() // Only created once
}

// ❌ BAD - Always creates
val tool = createExpensiveTool()
ToolRegistry.register(tool) // May already exist!
```

### 2. Cache Registry Lookups

```kotlin
// ✅ GOOD - Cache frequently accessed items
class AgentService {
    private val agentCache = mutableMapOf<String, Agent>()

    fun getAgent(capability: String): Agent? {
        return agentCache.getOrPut(capability) {
            AgentRegistry.findByCapability(capability).firstOrNull()
        }
    }
}

// ❌ BAD - Lookup every time
fun getAgent(capability: String): Agent? {
    return AgentRegistry.findByCapability(capability).firstOrNull()
}
```

### 3. Batch Operations

```kotlin
// ✅ GOOD - Batch registration
val tools = listOf(tool1, tool2, tool3, tool4, tool5)
tools.forEach { tool ->
    ToolRegistry.register(tool, namespace = "batch")
}

// ❌ BAD - Individual with checks
tools.forEach { tool ->
    if (!ToolRegistry.hasTool(tool.name, namespace = "batch")) {
        ToolRegistry.register(tool, namespace = "batch")
    }
}
```

## Summary

Registry system provides:

1. **Centralized Management** - Single source of truth for all resources
2. **Type Safety** - Generic implementation with compile-time checks
3. **Thread Safety** - Concurrent access without locks
4. **Advanced Search** - Find resources by capability, tag, namespace
5. **Namespace Isolation** - Organize tools by context
6. **Lazy Initialization** - getOrRegister pattern for efficient loading
7. **Metadata Support** - Rich information for discovery
8. **Cleanup** - Easy resource management

**Use registries for:**
- ✅ Agent discovery and routing
- ✅ Tool organization and reuse
- ✅ Vector store management
- ✅ Dynamic resource loading
- ✅ Multi-tenant isolation

## Next Steps

- [Agent API](./agent) - Learn about agent implementation
- [Tool API](./tool) - Create custom tools
- [DSL API](./dsl) - Build agents with DSL
- [Creating Custom Tools](../tools-extensions/creating-tools) - Tool development guide
