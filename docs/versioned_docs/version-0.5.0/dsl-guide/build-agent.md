# Building Agents

Master the buildAgent DSL for creating powerful agents.

## Basic Agent

```kotlin
val agent = buildAgent {
    id = "basic-agent"
    name = "Basic Agent"
    description = "A simple agent"

    handle { comm ->
        comm.reply("Hello from ${this.name}!", id)
    }
}
```

## With Debug Mode

```kotlin
val agent = buildAgent {
    id = "debug-agent"
    name = "Debug Agent"

    debugMode(enabled = true, prefix = "[🔍 DEBUG]")

    handle { comm ->
        // Automatic debug logging
        comm.reply("Processed", id)
    }
}
```

## Multi-Level Tool Management

### Level 1: Agent Tools

```kotlin
buildAgent {
    tools("tool1", "tool2")  // Agent-specific tools
}
```

### Level 2: Global Tools

```kotlin
buildAgent {
    globalTools("web_search", "calculator")  // From ToolRegistry
}
```

### Level 3: Inline Tools

```kotlin
buildAgent {
    tool("custom") {
        description = "Custom tool"
        execute { ToolResult.success("Done") }
    }
}
```

### Level 4: Vector Stores

```kotlin
buildAgent {
    vectorStore("knowledge") {
        provider("qdrant")
        connection("localhost", 6333)
        collection("docs")
    }
}
```

## Complete Example

```kotlin
val assistant = buildAgent {
    id = "assistant"
    name = "AI Assistant"
    description = "Helpful assistant with tools and knowledge"

    debugMode(enabled = true)

    // Inline tool
    tool("greet") {
        description = "Greet user"
        parameter("name", "string", "User name")
        execute { params ->
            ToolResult.success("Hello, ${params["name"]}!")
        }
    }

    // Vector store
    vectorStore("docs") {
        provider("qdrant")
        connection("localhost", 6333)
    }

    // Message handling
    handle { comm ->
        when {
            comm.content.startsWith("greet") -> {
                val name = comm.content.removePrefix("greet ").trim()
                val result = run("greet", mapOf("name" to name))
                comm.reply(result.result, id)
            }
            comm.content.startsWith("search") -> {
                val query = comm.content.removePrefix("search ").trim()
                val result = run("search-docs", mapOf("query" to query))
                comm.reply(result.result, id)
            }
            else -> comm.reply("Commands: greet [name], search [query]", id)
        }
    }
}
```
