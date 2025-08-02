# 🌲 Spice PSI (Program Structure Interface)

PSI provides a tree-based representation of Spice DSL constructs, making them easier to analyze, transform, and persist.

## Why PSI?

- **LLM-Friendly**: Tree structures are easier for AI to understand and manipulate
- **Serializable**: Can be saved to mnemo or other storage systems  
- **Transformable**: Easy to analyze, optimize, or convert between formats
- **Inspectable**: Provides insight into agent/tool/flow structures

## Core Components

### PsiNode
The basic building block of PSI trees:

```kotlin
data class PsiNode(
    val type: String,                              // Node type (Agent, Tool, etc.)
    val props: MutableMap<String, Any?>,          // Properties
    val children: MutableList<PsiNode>,           // Child nodes
    val metadata: MutableMap<String, Any?>        // Additional metadata
)
```

### SpicePsiBuilder
Converts DSL objects to PSI trees:

```kotlin
// Agent to PSI
val agent = buildAgent {
    id = "analyzer"
    tool("sentiment-analysis")
}
val psi = agent.toPsi()

// Tool to PSI  
val tool = tool {
    name = "calculator"
    description = "Basic math operations"
}
val toolPsi = tool.toPsi()
```

### PsiSerializer
Handles serialization for storage and visualization:

```kotlin
// To JSON for storage
val json = psi.toJson()

// To mnemo format
val mnemoData = psi.toMnemoFormat()
mnemo.remember("agent-structure", mnemoData)

// To Mermaid for visualization
val diagram = psi.toMermaid()

// To LLM-friendly format
val llmFormat = psi.toLLMFormat()
```

## Usage Examples

### 1. Analyzing Agent Structure

```kotlin
val agent = buildAgent {
    id = "researcher"
    name = "Research Agent"
    
    tool("web-search")
    tool("summarizer")
    
    vectorStore {
        provider = "qdrant"
        collection = "knowledge"
    }
}

// Convert to PSI
val psi = SpicePsiBuilder.run { 
    agent.builder.toPsi() 
}

// Find all tools
val tools = psi.findByType(PsiTypes.TOOL)
println("Agent has ${tools.size} tools")

// Check vector stores
val vectorStores = psi.findByType("VectorStore")
println("Uses ${vectorStores.size} vector stores")
```

### 2. Storing Agent Configuration

```kotlin
// Save agent structure to mnemo
val psi = agent.toPsi()
val serialized = PsiSerializer.run { psi.toMnemoFormat() }

mnemo.remember("agent-config-${agent.id}", serialized)

// Later, retrieve and analyze
val saved = mnemo.recall("agent-config-researcher")
val restoredPsi = PsiSerializer.fromMnemoFormat(saved)

// Analyze structure
val analysis = PsiAnalyzer.analyzeIntent(restoredPsi)
```

### 3. Visualizing Agent Network

```kotlin
// Create a complete application PSI
val appPsi = SpicePsiBuilder.buildCompletePsi {
    agent(researchAgent)
    agent(summaryAgent)
    agent(translatorAgent)
    
    flow(documentFlow)
    
    tool(searchTool)
    tool(extractorTool)
}

// Generate Mermaid diagram
val diagram = PsiSerializer.run { appPsi.toMermaid() }
println(diagram)
// Output: Mermaid diagram showing agent/tool relationships
```

### 4. LLM Analysis

```kotlin
// Convert to LLM-friendly format
val llmFormat = psi.toLLMFormat()

// Use with LLM for analysis
val prompt = """
Analyze this agent structure and suggest optimizations:

$llmFormat

Consider:
1. Tool redundancy
2. Missing capabilities  
3. Performance improvements
"""

val suggestions = llm.complete(prompt)
```

## PSI Node Types

Common node types defined in `PsiTypes`:

- `AGENT` - Agent definition
- `TOOL` - Tool definition
- `FLOW` - Flow definition
- `SWARM` - Swarm agent
- `TOOLS` - Tool container
- `VECTOR_STORES` - Vector store configurations
- `SCHEMA` - Parameter schemas
- `HANDLER` - Handler functions
- `CONFIG` - Configuration nodes

## Advanced Features

### PSI Queries

```kotlin
// Find nodes by property
val namedAgents = psi.findByProp("name", "Research Agent")

// Path-based queries (coming soon)
val tool = psi.path("Agent/Tools/Tool[name=calculator]")
```

### PSI Transformations

```kotlin
// Optimize PSI tree (coming soon)
val optimized = PsiTransformer.optimize(psi)

// Merge multiple PSI trees
val merged = PsiTransformer.merge(listOf(psi1, psi2))
```

### PSI Diffing

```kotlin
// Compare two agent structures (coming soon)
val diff = PsiDiffer.diff(oldPsi, newPsi)
println("Changes: ${diff.changes.size}")
```

## Integration with mnemo

PSI is designed to work seamlessly with mnemo for persistence:

```kotlin
// Save agent template
val templatePsi = buildAgent {
    id = "template-analyzer"
    // ... configuration
}.toPsi()

mnemo.remember(
    key = "agent-template-analyzer",
    content = PsiSerializer.run { templatePsi.toMnemoFormat() },
    memory_type = "code_pattern",
    tags = ["agent", "template", "analyzer"]
)

// Find similar agent structures
val similar = mnemo.find_pattern("analyzer agent structure")
```

## Best Practices

1. **Use PSI for Complex Analysis**: When you need to understand relationships between components
2. **Store Templates**: Save successful agent configurations as PSI templates
3. **Version Control**: Track PSI changes over time for agent evolution
4. **LLM Integration**: Use PSI's LLM format for AI-assisted optimization
5. **Visualization**: Generate diagrams for documentation and debugging