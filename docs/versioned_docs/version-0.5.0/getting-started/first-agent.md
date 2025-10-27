# Your First Agent

Learn how to create your first Spice agent from scratch.

## Basic Agent

The simplest agent you can create:

```kotlin
import io.github.noailabs.spice.dsl.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val agent = buildAgent {
        id = "my-first-agent"
        name = "My First Agent"
        description = "A simple echo agent"

        handle { comm ->
            comm.reply("You said: ${comm.content}", id)
        }
    }

    val response = agent.processComm(
        Comm(content = "Hello, Spice!", from = "user")
    )

    println(response.content)
    // Output: You said: Hello, Spice!
}
```

## Adding Tools

Agents become powerful when you add tools:

```kotlin
val agent = buildAgent {
    id = "tool-agent"
    name = "Tool Agent"

    tool("calculate") {
        description = "Perform calculations"
        parameter("operation", "string", "Operation to perform", required = true)
        parameter("a", "number", "First number", required = true)
        parameter("b", "number", "Second number", required = true)

        execute { params ->
            val op = params["operation"] as String
            val a = (params["a"] as Number).toDouble()
            val b = (params["b"] as Number).toDouble()

            val result = when (op) {
                "add" -> a + b
                "subtract" -> a - b
                "multiply" -> a * b
                "divide" -> a / b
                else -> 0.0
            }

            ToolResult.success("Result: $result")
        }
    }

    handle { comm ->
        comm.reply("Ready to calculate!", id)
    }
}
```

## Next Steps

- [Quick Start Guide](./quick-start) - Build a complete application
- [Core Concepts](../core-concepts/overview) - Learn about Agent, Comm, and Tools
