# Agent Development Guide

## Creating Agents

### Basic Agent Structure
```kotlin
val agent = buildAgent {
    // Required fields
    id = "unique-id"
    name = "Agent Name"
    
    // Optional fields
    description = "What this agent does"
    version = "1.0.0"
    
    // Handler function
    handle { comm: Comm ->
        // Process the message and return response
        comm.reply("Processed: ${comm.content}", id)
    }
}
```

### Agent Handler Pattern
The handler is the core of an agent. It receives a `Comm` and returns a `Comm`:

```kotlin
handle { comm ->
    when (comm.type) {
        CommType.TEXT -> handleText(comm)
        CommType.COMMAND -> handleCommand(comm)
        CommType.DATA -> handleData(comm)
        else -> comm.reply("Unsupported type", id)
    }
}
```

## Advanced Agent Features

### 1. Multi-Tool Agent
```kotlin
val assistantAgent = buildAgent {
    id = "assistant"
    name = "AI Assistant"
    
    tools {
        // Add multiple tools
        useGlobal("calculator", "textProcessor", "datetime")
        
        // Custom inline tool
        tool("translator") { params ->
            val text = params["text"] as String
            val lang = params["lang"] as String
            // Translation logic here
            ToolResult.success("Translated: $text to $lang")
        }
    }
    
    handle { comm ->
        when {
            comm.content.startsWith("calc") -> {
                val expr = comm.content.substringAfter("calc").trim()
                val result = useTool("calculator", mapOf("expression" to expr))
                comm.reply(result.result, id)
            }
            comm.content.startsWith("translate") -> {
                // Parse and use translator tool
                val result = useTool("translator", mapOf(
                    "text" to "Hello",
                    "lang" to "es"
                ))
                comm.reply(result.result, id)
            }
            else -> comm.reply("I can calculate or translate!", id)
        }
    }
}
```

### 2. Stateful Agent
```kotlin
class ConversationAgent(id: String) {
    private val history = mutableListOf<Comm>()
    
    val agent = buildAgent {
        this.id = id
        name = "Conversation Agent"
        
        handle { comm ->
            // Add to history
            history.add(comm)
            
            // Context-aware response
            val previousTopics = history
                .takeLast(5)
                .map { it.content }
                .joinToString(", ")
            
            comm.reply(
                "I remember we talked about: $previousTopics. Now you said: ${comm.content}",
                id
            )
        }
    }
    
    fun clearHistory() = history.clear()
}
```

### 3. Debug Mode Agent
```kotlin
val debugAgent = buildAgent {
    id = "debug-agent"
    name = "Debug Agent"
    
    // Enable debug mode
    debugMode(true)
    debugPrefix("[DEBUG]")
    
    handle { comm ->
        // Debug output will be automatically logged
        println("Processing: ${comm.content}")
        
        // Simulate processing
        Thread.sleep(100)
        
        comm.reply("Processed with debug", id)
    }
}
```

## Agent Communication Patterns

### 1. Request-Response Pattern
```kotlin
val echoAgent = buildAgent {
    id = "echo"
    handle { comm ->
        comm.reply("Echo: ${comm.content}", id)
    }
}

// Usage
val request = comm("Hello")
val response = echoAgent.process(request)
```

### 2. Pipeline Pattern
```kotlin
val preprocessor = buildAgent {
    id = "preprocessor"
    handle { comm ->
        val cleaned = comm.content.trim().lowercase()
        comm.copy(content = cleaned)
    }
}

val processor = buildAgent {
    id = "processor"
    handle { comm ->
        comm.reply("Processed: ${comm.content}", id)
    }
}

// Chain agents
val input = comm("  HELLO WORLD  ")
val cleaned = preprocessor.process(input)
val result = processor.process(cleaned)
```

### 3. Broadcast Pattern
```kotlin
// Register multiple agents
val agents = listOf(agent1, agent2, agent3)
agents.forEach { CommHub.register(it) }

// Broadcast message
val responses = CommHub.broadcast(comm("Announcement"))
```

## Error Handling in Agents

### Graceful Error Handling
```kotlin
val robustAgent = buildAgent {
    id = "robust"
    name = "Robust Agent"
    
    handle { comm ->
        try {
            // Validation
            require(comm.content.isNotBlank()) { 
                "Content cannot be empty" 
            }
            
            // Processing
            val result = processContent(comm.content)
            comm.reply(result, id)
            
        } catch (e: IllegalArgumentException) {
            errorComm("Invalid input: ${e.message}", comm.from ?: "unknown")
        } catch (e: Exception) {
            errorComm("Processing failed: ${e.message}", comm.from ?: "unknown")
        }
    }
}
```

### Timeout Handling
```kotlin
val timeoutAgent = buildAgent {
    id = "timeout"
    
    handle { comm ->
        withTimeout(5000) { // 5 second timeout
            // Long running operation
            val result = performLongOperation(comm)
            comm.reply(result, id)
        } ?: errorComm("Operation timed out", comm.from ?: "unknown")
    }
}
```

## Agent Lifecycle

### Registration and Management
```kotlin
// Register globally
AgentRegistry.register(myAgent)

// Retrieve
val agent = AgentRegistry.get("agent-id")

// List all agents
val allAgents = AgentRegistry.list()

// Unregister
AgentRegistry.unregister("agent-id")

// Clear all
AgentRegistry.clear()
```

### Agent Metadata
```kotlin
val metadataAgent = buildAgent {
    id = "metadata"
    name = "Metadata Agent"
    
    // Add custom metadata
    data("author", "DevHub")
    data("category", "utility")
    data("tags", listOf("helper", "general"))
    
    handle { comm ->
        val meta = buildString {
            appendLine("Agent: $name")
            appendLine("Version: $version")
            appendLine("Category: ${data["category"]}")
        }
        comm.reply(meta, id)
    }
}
```

## Testing Agents

### Unit Testing
```kotlin
class AgentTest {
    @Test
    fun `test agent response`() {
        val agent = buildAgent {
            id = "test"
            handle { comm ->
                comm.reply("Test: ${comm.content}", id)
            }
        }
        
        val response = agent.process(comm("Hello"))
        assertEquals("Test: Hello", response.content)
        assertEquals("test", response.from)
    }
    
    @Test
    fun `test agent with tools`() {
        val agent = buildAgent {
            id = "calc"
            tools {
                useGlobal("calculator")
            }
            handle { comm ->
                val result = useTool("calculator", 
                    mapOf("expression" to "2+2"))
                comm.reply(result.result, id)
            }
        }
        
        val response = agent.process(comm("calculate"))
        assertEquals("4.0", response.content)
    }
}
```

## Best Practices

1. **Always handle errors gracefully**
2. **Use meaningful agent IDs and names**
3. **Document complex handler logic**
4. **Validate input in handlers**
5. **Use tools for reusable functionality**
6. **Enable debug mode during development**
7. **Clean up resources in stateful agents**
8. **Test edge cases and error scenarios**

## Next: [Tool Development](tool-development.md)