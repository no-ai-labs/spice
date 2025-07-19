# Core Concepts

## Architecture Pattern: Agent > Flow > Tool

Spice Framework follows a hierarchical architecture with three main levels:

### 1. Tools (Foundation Layer)
The smallest unit of functionality. Tools are reusable capabilities that agents can leverage.

```kotlin
val myTool = SimpleTool(
    name = "word-counter",
    description = "Counts words in text",
    parameterSchemas = mapOf(
        "text" to ParameterSchema("string", "Text to analyze", true)
    )
) { params ->
    val text = params["text"] as String
    val wordCount = text.split("\\s+".toRegex()).size
    ToolResult.success("Word count: $wordCount")
}
```

### 2. Agents (Processing Layer)
Autonomous units that process messages and can use tools.

```kotlin
val agent = buildAgent {
    id = "processor"
    name = "Text Processor"
    
    tools {
        add(myTool)
    }
    
    handle { comm ->
        // Agent logic here
    }
}
```

### 3. Flows (Orchestration Layer)
Coordinate multiple agents to accomplish complex tasks.

```kotlin
val workflow = flow {
    name = "Document Analysis"
    
    step("extract") { comm ->
        // First agent extracts text
        extractorAgent.process(comm)
    }
    
    step("analyze") { comm ->
        // Second agent analyzes
        analyzerAgent.process(comm)
    }
    
    step("summarize") { comm ->
        // Third agent summarizes
        summarizerAgent.process(comm)
    }
}
```

## Communication Model

### Comm (Communication Object)
The unified message type that flows through the system.

```kotlin
data class Comm(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val from: String? = null,
    val to: String? = null,
    val type: CommType = CommType.TEXT,
    val role: CommRole = CommRole.USER,
    val timestamp: Instant = Instant.now(),
    val data: Map<String, Any> = emptyMap(),
    // ... more fields
)
```

### CommHub
Central message routing and agent coordination.

```kotlin
// Register agents
CommHub.register(agent1)
CommHub.register(agent2)

// Send message to specific agent
val response = CommHub.send(comm, "agent1")

// Broadcast to all agents
val responses = CommHub.broadcast(comm)
```

## Registry System

### Generic Registry Pattern
```kotlin
class Registry<T : Identifiable>(val name: String) {
    fun register(item: T)
    fun get(id: String): T?
    fun getAll(): List<T>
    fun clear()
}
```

### Built-in Registries
1. **AgentRegistry** - Global agent storage
2. **ToolRegistry** - Global tool storage with namespace support

```kotlin
// Register globally
AgentRegistry.register(myAgent)
ToolRegistry.register(myTool, namespace = "custom")

// Retrieve
val agent = AgentRegistry.get("agent-id")
val tool = ToolRegistry.getTool("tool-name", "custom")
```

## Tool System

### Tool Interface
```kotlin
interface Tool {
    val name: String
    val description: String
    suspend fun execute(params: Map<String, Any>): ToolResult
}
```

### ToolResult
```kotlin
data class ToolResult(
    val success: Boolean,
    val result: String,
    val error: String? = null,
    val metadata: Map<String, Any> = emptyMap()
)
```

### Tool Usage in Agents
```kotlin
val agent = buildAgent {
    tools {
        // Add specific tool
        add(customTool)
        
        // Use global tools
        useGlobal("calculator", "datetime")
        
        // Inline tool definition
        tool("uppercase") { params ->
            val text = params["text"] as String
            ToolResult.success(text.uppercase())
        }
    }
    
    handle { comm ->
        // Use tool in handler
        val result = useTool("uppercase", mapOf("text" to comm.content))
        comm.reply(result.result, id)
    }
}
```

## DSL (Domain Specific Language)

### Core DSL Functions
- `buildAgent { }` - Create agents
- `flow { }` - Define workflows
- `comm { }` - Build messages
- `tool { }` - Define tools

### Builder Pattern
All DSL functions use Kotlin's builder pattern for clean, readable code:

```kotlin
val complexAgent = buildAgent {
    id = "complex"
    name = "Complex Agent"
    description = "An agent with many features"
    
    tools {
        useGlobal("calculator")
        add(customTool)
    }
    
    debugMode(true)
    debugPrefix("[COMPLEX]")
    
    handle { comm ->
        // Processing logic
    }
}
```

## Error Handling

### Tool Errors
```kotlin
val safeTool = SimpleTool("safe", "Safe tool", emptyMap()) { params ->
    try {
        // Tool logic
        ToolResult.success("Result")
    } catch (e: Exception) {
        ToolResult.error(e.message ?: "Unknown error")
    }
}
```

### Agent Error Handling
```kotlin
val resilientAgent = buildAgent {
    handle { comm ->
        try {
            // Process message
            comm.reply("Success", id)
        } catch (e: Exception) {
            errorComm(e.message ?: "Processing failed", comm.from ?: "unknown")
        }
    }
}
```

## Next: [Agent Guide](agent-guide.md)