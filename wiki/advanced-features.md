# Advanced Features

## Swarm Intelligence

Swarm Intelligence enables multiple agents to work together as a collective, making decisions through consensus and emergent behaviors.

### Basic Swarm Setup
```kotlin
val swarm = SwarmAgent(
    id = "analysis-swarm",
    name = "Analysis Swarm",
    description = "Collective analysis system"
).apply {
    // Add worker agents
    addWorker(sentimentAnalyzer)
    addWorker(keywordExtractor)
    addWorker(summaryGenerator)
    addWorker(factChecker)
    
    // Configure swarm behavior
    config.consensusThreshold = 0.7
    config.minWorkers = 3
    config.timeout = 5000
}

// Execute swarm task
val result = swarm.execute(
    SwarmTask(
        type = TaskType.CONSENSUS,
        input = comm("Analyze this article about AI"),
        strategy = ExecutionStrategy.PARALLEL
    )
)
```

### Swarm Consensus Mechanisms
```kotlin
// Weighted consensus based on agent expertise
val expertSwarm = SwarmAgent("expert-swarm").apply {
    addWorker(aiExpert, weight = 0.4)
    addWorker(dataScientist, weight = 0.3)
    addWorker(businessAnalyst, weight = 0.2)
    addWorker(ethicsExpert, weight = 0.1)
    
    // Custom scoring function
    setScoringFunction { results ->
        WeightedConsensus.calculate(
            results,
            weights = getWorkerWeights(),
            threshold = 0.75
        )
    }
}
```

### Emergent Behavior Patterns
```kotlin
val emergentSwarm = SwarmAgent("emergent").apply {
    // Enable emergence detection
    enableEmergence(EmergentBehaviorSystem().apply {
        registerPattern("convergence") { results ->
            // Detect when agents converge on similar conclusions
            val similarity = calculateSimilarity(results)
            similarity > 0.85
        }
        
        registerPattern("divergence") { results ->
            // Detect when agents have very different outputs
            val diversity = calculateDiversity(results)
            diversity > 0.7
        }
    })
    
    // React to emergent patterns
    onEmergence("convergence") { pattern ->
        // Strong agreement - fast track the result
        accelerateProcessing()
    }
    
    onEmergence("divergence") { pattern ->
        // Disagreement - add more workers or iterate
        addSpecialistWorkers()
        reprocess()
    }
}
```

## MCP (Model Context Protocol) Integration

MCP allows integration with external AI models and services through a standardized protocol.

### Basic MCP Setup
```kotlin
val mcpIntegration = MCPIntegration {
    // Configure MCP connection
    endpoint = "https://api.example.com/mcp"
    apiKey = System.getenv("MCP_API_KEY")
    timeout = 30_000
    
    // Register capabilities
    capabilities {
        register("text-generation")
        register("code-completion")
        register("image-analysis")
    }
}

// Create MCP-enabled agent
val mcpAgent = buildAgent {
    id = "mcp-agent"
    name = "MCP Agent"
    
    // Attach MCP integration
    mcp(mcpIntegration)
    
    handle { comm ->
        // Use MCP for processing
        val result = mcp.process(
            capability = "text-generation",
            input = comm.content,
            parameters = mapOf(
                "max_tokens" to 1000,
                "temperature" to 0.7
            )
        )
        
        comm.reply(result.content, id)
    }
}
```

### MCP Tool Bridge
```kotlin
// Convert MCP capabilities to Spice tools
class MCPToolBridge(private val mcp: MCPIntegration) {
    fun createTool(capability: String): Tool {
        return SimpleTool(
            name = "mcp-$capability",
            description = "MCP capability: $capability",
            parameterSchemas = mcp.getParameterSchema(capability)
        ) { params ->
            try {
                val result = mcp.process(capability, params)
                ToolResult.success(
                    result.content,
                    mapOf("mcp_metadata" to result.metadata)
                )
            } catch (e: Exception) {
                ToolResult.error("MCP error: ${e.message}")
            }
        }
    }
}

// Use in agent
val agent = buildAgent {
    tools {
        // Add all MCP capabilities as tools
        mcp.capabilities.forEach { capability ->
            add(MCPToolBridge(mcp).createTool(capability))
        }
    }
}
```

## Vector Store and RAG

Vector stores enable semantic search and retrieval-augmented generation (RAG).

### Basic Vector Store Usage
```kotlin
// Create vector store
val vectorStore = VectorStore(
    embeddingModel = "text-embedding-ada-002",
    dimension = 1536
)

// Add documents
vectorStore.addDocument(
    id = "doc1",
    content = "Spice Framework is a Kotlin-based agent framework",
    metadata = mapOf("category" to "documentation")
)

// Search
val results = vectorStore.search(
    query = "What is Spice Framework?",
    limit = 5,
    threshold = 0.7
)

// Use in agent
val ragAgent = buildAgent {
    id = "rag-agent"
    
    // Attach vector store
    vectorStore(vectorStore)
    
    handle { comm ->
        // Search relevant documents
        val context = vectorStore.search(comm.content, limit = 3)
            .joinToString("\n") { it.content }
        
        // Generate response with context
        val prompt = """
            Context: $context
            
            Question: ${comm.content}
            
            Answer based on the context:
        """.trimIndent()
        
        // Process with context
        comm.reply(generateAnswer(prompt), id)
    }
}
```

### Advanced Vector Store Features
```kotlin
// Vector store with multiple indices
class MultiIndexVectorStore {
    private val indices = mutableMapOf<String, VectorStore>()
    
    fun createIndex(name: String, config: VectorStoreConfig) {
        indices[name] = VectorStore(config)
    }
    
    fun searchAcrossIndices(
        query: String,
        indices: List<String> = this.indices.keys.toList()
    ): List<SearchResult> {
        return indices.flatMap { indexName ->
            this.indices[indexName]?.search(query) ?: emptyList()
        }.sortedByDescending { it.score }
    }
}

// Hybrid search combining vector and keyword search
class HybridSearchAgent : Agent {
    private val vectorStore = VectorStore()
    private val keywordIndex = KeywordIndex()
    
    override suspend fun process(comm: Comm): Comm {
        // Vector search
        val vectorResults = vectorStore.search(comm.content)
        
        // Keyword search
        val keywordResults = keywordIndex.search(comm.content)
        
        // Combine and rerank
        val combined = combineResults(vectorResults, keywordResults)
        
        return comm.reply(
            "Found ${combined.size} relevant results",
            id
        ).withData("results", combined)
    }
}
```

## LLM Integration

Advanced integration with Large Language Models.

### AbstractLLMAgent
```kotlin
// Custom LLM agent
class CustomLLMAgent(
    id: String,
    private val apiClient: LLMApiClient
) : AbstractLLMAgent(id) {
    
    override suspend fun generateResponse(
        prompt: String,
        context: Map<String, Any>
    ): String {
        return apiClient.complete(
            prompt = prompt,
            model = context["model"] as? String ?: "default",
            temperature = context["temperature"] as? Double ?: 0.7,
            maxTokens = context["maxTokens"] as? Int ?: 1000
        )
    }
    
    override fun buildPrompt(comm: Comm): String {
        return """
            System: You are a helpful assistant.
            
            Previous context: ${getConversationHistory()}
            
            User: ${comm.content}
            
            Assistant:
        """.trimIndent()
    }
}
```

### Modern Claude/GPT Agents
```kotlin
// Claude agent with custom configuration
val claudeAgent = ModernClaudeAgent(
    id = "claude-assistant",
    apiKey = System.getenv("CLAUDE_API_KEY"),
    config = ClaudeConfig(
        model = "claude-3-opus-20240229",
        maxTokens = 4000,
        temperature = 0.7,
        systemPrompt = "You are an expert Kotlin developer"
    )
)

// GPT agent with function calling
val gptAgent = ModernGPTAgent(
    id = "gpt-assistant",
    apiKey = System.getenv("OPENAI_API_KEY"),
    config = GPTConfig(
        model = "gpt-4-turbo-preview",
        functions = listOf(
            FunctionDef(
                name = "search_code",
                description = "Search for code examples",
                parameters = mapOf(
                    "query" to "string",
                    "language" to "string"
                )
            )
        )
    )
)
```

## Performance Optimization

### Agent Performance Dashboard
```kotlin
val dashboard = PerformanceDashboard()

// Monitor agent performance
val monitoredAgent = dashboard.monitor(agent) {
    trackMetrics = true
    trackLatency = true
    trackErrors = true
    trackThroughput = true
}

// Get performance report
val report = dashboard.generateReport(monitoredAgent.id)
println("""
    Performance Report for ${report.agentId}:
    - Average latency: ${report.avgLatency}ms
    - Throughput: ${report.throughput} msg/sec
    - Error rate: ${report.errorRate}%
    - 95th percentile: ${report.p95Latency}ms
""".trimIndent())
```

### Dynamic Strategy Optimization
```kotlin
val optimizer = DynamicStrategyOptimizer()

// Optimize swarm execution strategy
val optimizedSwarm = optimizer.optimize(swarm) {
    // Define optimization goals
    goals {
        minimizeLatency(weight = 0.4)
        maximizeAccuracy(weight = 0.3)
        minimizeCost(weight = 0.3)
    }
    
    // Set constraints
    constraints {
        maxLatency = 1000 // ms
        minAccuracy = 0.85
        maxCostPerRequest = 0.10
    }
}

// The optimizer will dynamically adjust:
// - Worker allocation
// - Parallelization strategy
// - Caching policies
// - Retry mechanisms
```

## Chains and Pipelines

### Modern Tool Chains
```kotlin
val analysisChain = ModernToolChain(
    name = "analysis-chain",
    description = "Complete analysis pipeline"
).apply {
    // Add tools in sequence
    addTool(extractorTool)
    addTool(cleanerTool)
    addTool(analyzerTool)
    addTool(summarizerTool)
    
    // Configure chain behavior
    config {
        continueOnError = false
        timeout = 30_000
        retryFailedTools = true
        cacheResults = true
    }
    
    // Add transformers between tools
    addTransformer("cleaner", "analyzer") { result ->
        // Transform cleaner output for analyzer input
        mapOf("text" to result.result.lowercase())
    }
}

// Execute chain
val chainResult = analysisChain.execute(
    mapOf("url" to "https://example.com/article")
)
```

## Experimental Features

### Reactive Streams
```kotlin
experimental {
    val reactiveAgent = reactiveAgent {
        id = "stream-processor"
        
        // Define stream processing
        stream<String> { flow ->
            flow.filter { it.isNotBlank() }
                .map { it.uppercase() }
                .buffer(10)
                .collect { batch ->
                    processBatch(batch)
                }
        }
    }
}
```

### Agent Composition
```kotlin
experimental {
    val composedAgent = compose {
        // Combine multiple agents
        agent(preprocessor) handles { msg -> 
            msg.content.contains("raw") 
        }
        
        agent(analyzer) handles { msg ->
            msg.content.contains("analyze")
        }
        
        fallback(defaultAgent)
    }
}
```

## JSON Serialization and Export

Spice provides comprehensive JSON serialization for all components, enabling easy integration with external systems, UIs, and APIs.

### Unified Serialization System

```kotlin
import io.github.noailabs.spice.serialization.SpiceSerializer.toJson
import io.github.noailabs.spice.serialization.SpiceSerializer.toJsonSchema

// Serialize any Spice component
val agentJson = myAgent.toJson()
val toolJson = myTool.toJson()
val vectorStoreJson = myVectorStore.toJson()

// Export tool as JSON Schema (GUI/API integration)
val schema = myAgentTool.toJsonSchema()
```

### AgentTool and JSON Schema

AgentTool provides a serializable representation of tools:

```kotlin
val mlTool = agentTool("ml-predictor") {
    description("Machine learning prediction tool")
    
    parameters {
        array("features", "Input features")
        string("model", "Model name")
        number("threshold", "Confidence threshold")
    }
    
    tags("ml", "prediction", "ai")
    metadata("version", "2.0")
    metadata("accuracy", "0.95")
    
    implementationType("python-script", mapOf(
        "runtime" to "python3.9",
        "requirements" to "numpy,scikit-learn"
    ))
}

// Export as JSON Schema
val schema = mlTool.toJsonSchema()
// Result: Standard JSON Schema draft-07 format

// Save to file
mlTool.saveToFile("tools/ml-predictor.json")

// Load from file
val loadedTool = AgentToolSerializer.loadFromFile("tools/ml-predictor.json")
```

### Complex Metadata Handling

SpiceSerializer properly handles nested structures:

```kotlin
// Before (toString() approach):
// List -> "[a, b, c]"
// Map -> "{key=value}"

// After (proper JSON):
val metadata = mapOf(
    "stats" to mapOf(
        "total" to 1000,
        "success_rate" to 0.95
    ),
    "tags" to listOf("production", "v2"),
    "config" to mapOf(
        "features" to listOf(
            mapOf("name" to "parallel", "enabled" to true),
            mapOf("name" to "cache", "enabled" to false)
        )
    )
)

val json = SpiceSerializer.toJsonMetadata(metadata)
// Preserves complete structure!
```

### Security Features

```kotlin
val vectorConfig = VectorStoreConfig(
    provider = "pinecone",
    apiKey = "sk-secret-key-12345",
    // ... other config
)

val json = vectorConfig.toJson()
// API key is automatically redacted: "apiKey": "[REDACTED]"
```

## PSI (Program Structure Interface)

PSI provides a tree-based representation of Spice DSL constructs, making them easier to analyze, transform, and persist:

### Converting DSL to PSI

```kotlin
// Agent to PSI
val agent = buildAgent {
    id = "analyzer"
    name = "Data Analyzer"
    
    tool("sentiment-analysis")
    
    vectorStore {
        provider = "qdrant"
        collection = "knowledge"
    }
}

// Convert to PSI using builder
val builder = CoreAgentBuilder().apply {
    id = "analyzer" 
    name = "Data Analyzer"
    // ... configuration
}
val psi = SpicePsiBuilder.run { builder.toPsi() }

// Explore the PSI tree
val tools = psi.findByType(PsiTypes.TOOL)
println("Agent has ${tools.size} tools")
```

### PSI Serialization for Storage

```kotlin
// Convert to JSON for mnemo storage
val json = PsiSerializer.run { psi.toMnemoFormat() }
mnemo.remember("agent-structure-${agent.id}", json)

// Later, retrieve and analyze
val saved = mnemo.recall("agent-structure-analyzer")
val restoredPsi = PsiSerializer.fromMnemoFormat(saved)
```

### Visualization with PSI

```kotlin
// Generate Mermaid diagram
val diagram = PsiSerializer.run { psi.toMermaid() }
println(diagram)
// Output:
// graph TD
//     root_Agent[Agent<br/>id=analyzer, name=Data Analyzer]
//     root_Agent --> tools_Tools[Tools]
//     tools_Tools --> tool1[Tool<br/>name=sentiment-analysis]

// Generate GraphML for advanced visualization
val graphml = PsiSerializer.run { psi.toGraphML() }
```

### LLM-Friendly Format

```kotlin
// Convert to LLM-optimized format
val llmFormat = PsiSerializer.run { psi.toLLMFormat() }

// Use with AI for analysis
val prompt = """
Analyze this agent structure and suggest optimizations:

$llmFormat

Consider tool redundancy and missing capabilities.
"""

val suggestions = llm.complete(prompt)
```

### Complete Application PSI

```kotlin
// Build PSI for entire application
val appPsi = SpicePsiBuilder.buildCompletePsi {
    agent(researchAgent)
    agent(summaryAgent)
    
    flow(documentFlow)
    
    tool(searchTool)
    tool(extractorTool)
    
    config("app.version", "1.0.0")
}

// Analyze application structure
val allAgents = appPsi.findByType(PsiTypes.AGENT)
val allFlows = appPsi.findByType(PsiTypes.FLOW)
```

## Best Practices for Advanced Features

1. **Monitor performance** when using swarms - they can be resource intensive
2. **Cache vector embeddings** to reduce API calls and improve performance
3. **Use appropriate consensus mechanisms** for your swarm use case
4. **Implement circuit breakers** for external service integrations
5. **Version your MCP integrations** to handle API changes
6. **Test emergent behaviors** thoroughly before production
7. **Profile memory usage** with large vector stores
8. **Use connection pooling** for LLM integrations
9. **Implement proper error boundaries** for experimental features
10. **Document complex configurations** for future maintenance
11. **Use PSI for agent structure analysis** and optimization
12. **Store PSI representations** for version control and auditing

## Next: [Spring Boot Integration](spring-boot.md)