# Tools DSL

Create and manage tools with the Spice DSL.

## Inline Tool

```kotlin
tool("my_tool") {
    description = "My custom tool"

    parameter("input", "string", "Input data", required = true)
    parameter("format", "string", "Output format", required = false)

    execute { params ->
        val input = params["input"] as String
        val format = params["format"] as? String ?: "json"

        ToolResult.success("Processed: $input in $format")
    }
}
```

## Tool with Validation

```kotlin
tool("validated_tool") {
    parameter("age", "number", "User age")

    canExecute { params ->
        val age = (params["age"] as? Number)?.toInt() ?: 0
        age >= 18
    }

    execute { params ->
        ToolResult.success("Valid user")
    }
}
```

## AgentTool DSL

```kotlin
val tool = agentTool("advanced") {
    description = "Advanced tool with metadata"
    tags = listOf("production", "v2")

    parameter("data", "object", "Complex data")

    metadata = mapOf(
        "version" to "2.0",
        "author" to "team"
    )

    implementation { params ->
        // Implementation logic
        ToolResult.success("Done")
    }
}
```
