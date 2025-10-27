# DSL Guide Overview

Spice Framework provides an intuitive, type-safe DSL for building AI applications.

## What is the Spice DSL?

The Spice DSL (Domain-Specific Language) is a Kotlin-based builder pattern that makes creating agents, tools, and workflows feel natural and readable.

## Core DSL Functions

### buildAgent

Create agents with a fluent API:

```kotlin
val agent = buildAgent {
    id = "my-agent"
    name = "My Agent"
    description = "Does amazing things"

    handle { comm ->
        comm.reply("Response", id)
    }
}
```

### buildFlow

Orchestrate multi-agent workflows:

```kotlin
val flow = buildFlow {
    id = "my-flow"
    name = "Processing Flow"

    step("analyze", "analyzer-agent")
    step("process", "processor-agent")
    step("respond", "responder-agent")
}
```

### tool { }

Add tools inline:

```kotlin
buildAgent {
    tool("calculate") {
        description = "Perform calculations"
        parameter("a", "number", "First number")
        parameter("b", "number", "Second number")

        execute { params ->
            val result = (params["a"] as Number).toInt() +
                        (params["b"] as Number).toInt()
            ToolResult.success("Result: $result")
        }
    }
}
```

## DSL Features

### 1. Type Safety

```kotlin
// Compile-time type checking
buildAgent {
    id = "agent"  // String required
    name = "Agent"  // String required

    handle { comm: Comm ->  // Type inferred
        comm.reply("Response", id)  // Type-safe
    }
}
```

### 2. Progressive Disclosure

Start simple, add complexity as needed:

```kotlin
// Simple
val agent = buildAgent {
    id = "simple"
    name = "Simple Agent"
    handle { it.reply("OK", id) }
}

// Complex
val agent = buildAgent {
    id = "complex"
    debugMode(enabled = true, prefix = "[DEBUG]")
    tools("tool1", "tool2")
    globalTools("global1")
    vectorStore("knowledge") { /* ... */ }
    handle { /* complex logic */ }
}
```

### 3. Fluent Chaining

```kotlin
val comm = comm("Message") {
    from("user")
    to("agent")
    type(CommType.TEXT)
    urgent()
    data("key", "value")
}
```

## DSL Patterns

### Builder Pattern

```kotlin
class CoreAgentBuilder {
    var id: String = ""
    var name: String = ""

    fun handle(handler: suspend (Comm) -> Comm) {
        this.handler = handler
    }

    fun build(): Agent { /* ... */ }
}
```

### Extension Functions

```kotlin
// Extend existing types
fun Comm.urgent() = copy(priority = Priority.URGENT)
fun Comm.withData(key: String, value: String) = copy(data = data + (key to value))
```

### Inline Functions

```kotlin
inline fun buildAgent(config: CoreAgentBuilder.() -> Unit): Agent {
    val builder = CoreAgentBuilder()
    builder.config()
    return builder.build()
}
```

## Quick Reference

| DSL Function | Purpose | Example |
|--------------|---------|---------|
| `buildAgent` | Create agent | `buildAgent { }` |
| `buildFlow` | Create workflow | `buildFlow { }` |
| `tool` | Add inline tool | `tool("name") { }` |
| `vectorStore` | Add vector store | `vectorStore("name") { }` |
| `handle` | Set message handler | `handle { comm -> }` |
| `comm` | Create communication | `comm("text") { }` |

## Next Steps

- [Building Agents](./build-agent)
- [Creating Flows](./build-flow)
- [Tool Development](./tools)
- [Vector Stores](./vector-store)
