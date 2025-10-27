# Registry API

Centralized registry system for managing agents, tools, and resources in Spice Framework.

## Overview

The Registry system provides **thread-safe**, **type-safe** registration and discovery for all framework components:

- **AgentRegistry** - Register and discover agents by capability, tag, or provider
- **ToolRegistry** - Manage tools with namespace support and metadata
- **GraphRegistry** - ✨ NEW in v0.5.0 - Manage DAG-based orchestration graphs
- **VectorStoreRegistry** - Centralized vector store management
- **ToolChainRegistry** - Register tool chains for complex workflows
- **FlowRegistry** - ⚠️ DEPRECATED - Use GraphRegistry instead

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
data class MyResource(
    override val id: String,
    val name: String,
    val config: Map<String, Any>
) : Identifiable

// Create registry
val resourceRegistry = Registry<MyResource>("resources")

// Register items
val resource1 = MyResource("res-1", "Database", mapOf("host" to "localhost"))
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

// Get or create
val resource2 = resourceRegistry.getOrRegister("res-2") {
    MyResource("res-2", "Cache", mapOf("ttl" to 3600))
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
// Register single agent
val agent = buildAgent {
    name = "Research Assistant"
    description = "AI-powered research assistant"
    capabilities = listOf("research", "analysis", "summarization")

    llm = anthropic(apiKey = "...") {
        model = "claude-3-5-sonnet-20241022"
    }
}

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
// Register agents with capabilities
AgentRegistry.register(buildAgent {
    name = "Data Analyst"
    capabilities = listOf("data-analysis", "visualization", "statistics")
    llm = anthropic(...) { model = "claude-3-5-sonnet-20241022" }
})

AgentRegistry.register(buildAgent {
    name = "Code Reviewer"
    capabilities = listOf("code-review", "security-analysis", "testing")
    llm = anthropic(...) { model = "claude-3-5-sonnet-20241022" }
})

// Find agents by capability
val dataAgents = AgentRegistry.findByCapability("data-analysis")
println("Data agents: ${dataAgents.size}") // 1

val reviewers = AgentRegistry.findByCapability("code-review")
println("Code reviewers: ${reviewers.size}") // 1
```

**Dynamic Agent Discovery:**

```kotlin
fun getAgentForTask(task: String): Agent? {
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
    val result = agent.processComm(Comm(content = "Analyze data", from = "user"))
}
```

### Find by Tag

Find agents by tags (checks name, description, metadata):

```kotlin
// Register agents with tags in description
AgentRegistry.register(buildAgent {
    name = "Claude Assistant"
    description = "Anthropic Claude-powered assistant #anthropic #llm"
    llm = anthropic(...) { model = "claude-3-5-sonnet-20241022" }
})

// Find by tag
val anthropicAgents = AgentRegistry.findByTag("anthropic")
println("Anthropic agents: ${anthropicAgents.size}")

val llmAgents = AgentRegistry.findByTag("llm")
println("LLM agents: ${llmAgents.size}")
```

### Find by Provider

Find agents by LLM provider:

```kotlin
// Register agents with different providers
AgentRegistry.register(buildAgent {
    name = "Claude Agent"
    llm = anthropic(...) { model = "claude-3-5-sonnet-20241022" }
})

AgentRegistry.register(buildAgent {
    name = "GPT Agent"
    llm = openai(...) { model = "gpt-4" }
})

// Find by provider
val claudeAgents = AgentRegistry.findByProvider("claude")
val openaiAgents = AgentRegistry.findByProvider("openai")

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

    override suspend fun execute(parameters: Map<String, Any>): SpiceResult<ToolResult> {
        val a = (parameters["a"] as Number).toDouble()
        val b = (parameters["b"] as Number).toDouble()
        val op = parameters["op"] as String

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

## GraphRegistry

✨ **NEW in v0.5.0** - Centralized management for DAG-based orchestration graphs:

```kotlin
object GraphRegistry : Registry<Graph>("graphs")
```

### Register Graphs

```kotlin
import io.github.noailabs.spice.graph.dsl.graph
import io.github.noailabs.spice.GraphRegistry

// Create a graph
val customerWorkflow = graph("customer-support") {
    agent("intake", intakeAgent)
    agent("classify", classifierAgent)
    agent("technical", technicalAgent)
    agent("billing", billingAgent)

    edge("intake", "classify")
    edge("classify", "technical") { result ->
        val comm = result.data as? Comm
        comm?.data?.get("category") == "technical"
    }
    edge("classify", "billing") { result ->
        val comm = result.data as? Comm
        comm?.data?.get("category") == "billing"
    }
}

// Register the graph
GraphRegistry.register(customerWorkflow)

// Check if registered
if (GraphRegistry.has("customer-support")) {
    println("Graph registered successfully")
}
```

### Retrieve and Execute

```kotlin
// Get registered graph
val graph = GraphRegistry.get("customer-support")

if (graph != null) {
    // Execute with GraphRunner
    val runner = DefaultGraphRunner()
    val result = runner.run(
        graph = graph,
        input = mapOf("comm" to initialComm)
    ).getOrThrow()

    println("Status: ${result.status}")
    println("Result: ${result.result}")
}
```

### List All Graphs

```kotlin
// Get all registered graphs
val allGraphs = GraphRegistry.getAll()

println("=== Registered Graphs ===")
allGraphs.forEach { graph ->
    println("ID: ${graph.id}")
    println("  Nodes: ${graph.nodes.size}")
    println("  Edges: ${graph.edges.size}")
    println("  Entry: ${graph.entryPoint}")
    println("  Middleware: ${graph.middleware.size}")
}
```

### Dynamic Graph Selection

```kotlin
class GraphRouter {
    init {
        // Register multiple workflows
        GraphRegistry.register(orderProcessingGraph)
        GraphRegistry.register(customerSupportGraph)
        GraphRegistry.register(dataAnalysisGraph)
    }

    suspend fun routeToGraph(workflowType: String, input: Map<String, Any?>): RunReport {
        val graphId = when (workflowType) {
            "order" -> "order-processing"
            "support" -> "customer-support"
            "analysis" -> "data-analysis"
            else -> throw IllegalArgumentException("Unknown workflow: $workflowType")
        }

        val graph = GraphRegistry.get(graphId)
            ?: throw IllegalStateException("Graph not found: $graphId")

        val runner = DefaultGraphRunner()
        return runner.run(graph, input).getOrThrow()
    }
}

// Usage
val router = GraphRouter()
val result = router.routeToGraph("order", mapOf("orderId" to "12345"))
```

### Context Integration

GraphRegistry fully supports AgentContext for multi-tenancy:

```kotlin
// Register graph with context-aware tools
val multiTenantGraph = graph("order-processing") {
    tool("lookup", contextAwareTool("lookup_orders") {
        execute { params, context ->
            // Context automatically propagated!
            val orders = orderRepo.findOrdersForTenant(context.tenantId)
            "Found ${orders.size} orders"
        }
    })

    agent("process", processingAgent)
}

GraphRegistry.register(multiTenantGraph)

// Execute with context
val result = withAgentContext("tenantId" to "ACME", "userId" to "user-123") {
    val graph = GraphRegistry.get("order-processing")!!
    val runner = DefaultGraphRunner()
    runner.run(graph, inputData)
}.getOrThrow()

// Context is automatically propagated through all nodes!
```

### Unregister and Cleanup

```kotlin
// Unregister specific graph
GraphRegistry.unregister("customer-support")

// Clear all graphs
GraphRegistry.clear()

// Get count
println("Registered graphs: ${GraphRegistry.size()}")
```

### Best Practices

```kotlin
// ✅ GOOD - Register at startup
fun initializeApplication() {
    GraphRegistry.register(customerSupportGraph)
    GraphRegistry.register(orderProcessingGraph)
    GraphRegistry.register(dataAnalysisGraph)
}

// ✅ GOOD - Use getOrRegister for lazy init
val graph = GraphRegistry.getOrRegister("customer-support") {
    buildCustomerSupportGraph()
}

// ✅ GOOD - Clean up when shutting down
fun shutdown() {
    GraphRegistry.clear()
}

// ❌ BAD - Register on every request
suspend fun handleRequest(input: Map<String, Any?>) {
    // Don't do this! Register once at startup
    GraphRegistry.register(graph)
    val result = runner.run(graph, input)
}
```

### Migration from FlowRegistry

If you're migrating from FlowRegistry to GraphRegistry:

```kotlin
// Before (0.4.x with Flow)
val orderFlow = buildFlow {
    name = "Order Processing"
    step("validate") { /* ... */ }
    step("payment") { /* ... */ }
}
FlowRegistry.register(orderFlow)
val flow = FlowRegistry.get("order-processing")

// After (0.5.0 with Graph)
val orderGraph = graph("order-processing") {
    agent("validate", validateAgent)
    agent("payment", paymentAgent)
    edge("validate", "payment")
}
GraphRegistry.register(orderGraph)
val graph = GraphRegistry.get("order-processing")
```

See [Migration Guide](../roadmap/migration-guide) for complete migration steps.

---

## FlowRegistry

⚠️ **DEPRECATED in v0.5.0** - Use `GraphRegistry` instead.

```kotlin
@Deprecated(
    message = "Flow has been replaced by Graph in v0.5.0. Use GraphRegistry instead.",
    replaceWith = ReplaceWith("GraphRegistry", "io.github.noailabs.spice.GraphRegistry"),
    level = DeprecationLevel.WARNING
)
object FlowRegistry : Registry<MultiAgentFlow>("flows")
```

**Deprecation Timeline:**
- ⚠️ v0.5.0 (Oct 2025): Deprecated with WARNING level
- 🔴 v0.6.0 (Apr 2026): Will be removed

**Migration Required:**

FlowRegistry is deprecated in favor of GraphRegistry. Please migrate to the new Graph system:

```kotlin
// ❌ DEPRECATED - FlowRegistry
val flow = buildFlow {
    name = "workflow"
    step("step1") { /* ... */ }
}
FlowRegistry.register(flow)
val retrieved = FlowRegistry.get("workflow")

// ✅ RECOMMENDED - GraphRegistry
val graph = graph("workflow") {
    agent("step1", agent1)
}
GraphRegistry.register(graph)
val retrieved = GraphRegistry.get("workflow")
```

**Why Deprecated?**
- Graph system provides more flexibility (DAG vs linear)
- Better composability and conditional routing
- Support for checkpointing and Human-in-the-Loop
- Industry-aligned with Microsoft Agent Framework

**See Also:**
- [Graph API](./graph) - Complete Graph system documentation
- [Migration Guide](../roadmap/migration-guide) - Step-by-step migration instructions
- [Graph System Overview](../orchestration/graph-system) - Learn about the new system

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
class AgentRouter {
    init {
        // Register agents with capabilities
        AgentRegistry.register(buildAgent {
            name = "Code Reviewer"
            capabilities = listOf("code-review", "security", "testing")
            llm = anthropic(...) { model = "claude-3-5-sonnet-20241022" }
        })

        AgentRegistry.register(buildAgent {
            name = "Data Analyst"
            capabilities = listOf("data-analysis", "visualization", "statistics")
            llm = anthropic(...) { model = "claude-3-5-sonnet-20241022" }
        })

        AgentRegistry.register(buildAgent {
            name = "Research Assistant"
            capabilities = listOf("research", "summarization", "fact-checking")
            llm = anthropic(...) { model = "claude-3-5-sonnet-20241022" }
        })
    }

    suspend fun routeRequest(request: String): SpiceResult<Comm> {
        // Determine required capability
        val capability = when {
            request.contains("code", ignoreCase = true) -> "code-review"
            request.contains("data", ignoreCase = true) -> "data-analysis"
            request.contains("research", ignoreCase = true) -> "research"
            else -> null
        }

        if (capability == null) {
            return SpiceResult.failure(SpiceError(
                message = "Could not determine appropriate agent",
                code = "ROUTING_ERROR"
            ))
        }

        // Find agent with capability
        val agent = AgentRegistry.findByCapability(capability).firstOrNull()
            ?: return SpiceResult.failure(SpiceError(
                message = "No agent found for capability: $capability",
                code = "AGENT_NOT_FOUND"
            ))

        // Route to agent
        return agent.processComm(Comm(
            content = request,
            from = "router"
        ))
    }
}

// Usage
val router = AgentRouter()

val result = router.routeRequest("Review this code for security issues")
result.fold(
    onSuccess = { response ->
        println("Agent: ${response.from}")
        println("Response: ${response.content}")
    },
    onFailure = { error ->
        println("Routing failed: ${error.message}")
    }
)
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
