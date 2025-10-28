# Tools DSL

Create and manage tools with the Spice DSL.

## Inline Tool

### Simple Execute (Recommended)

The simplest way to create a tool with automatic result wrapping:

```kotlin
tool("my_tool") {
    description = "My custom tool"

    parameter("input", "string", "Input data", required = true)
    parameter("format", "string", "Output format", required = false)

    // Simple execute - returns String, automatically wrapped in ToolResult
    execute(fun(params: Map<String, Any>): String {
        val input = params["input"] as String
        val format = params["format"] as? String ?: "json"

        return "Processed: $input in $format"
    })
}
```

**Key Features:**
- ✅ Automatic parameter validation (checks required parameters)
- ✅ Auto-wraps return value in `ToolResult.success()`
- ✅ Catches exceptions and returns `ToolResult.error()`
- ✅ Type-safe with explicit function syntax

### Advanced Execute

For full control over the result:

```kotlin
tool("my_tool") {
    description = "My custom tool"

    parameter("input", "string", "Input data", required = true)
    parameter("format", "string", "Output format", required = false)

    execute { params ->
        val input = params["input"] as String
        val format = params["format"] as? String ?: "json"

        SpiceResult.success(ToolResult.success("Processed: $input in $format"))
    }
}
```

## Parameter Validation

Parameters are automatically validated before execution:

```kotlin
tool("validated_tool") {
    parameter("name", "string", "User name", required = true)
    parameter("age", "number", "User age", required = true)

    execute(fun(params: Map<String, Any>): String {
        val name = params["name"] as String
        val age = (params["age"] as Number).toInt()

        return "Hello $name, you are $age years old"
    })
}

// Missing required parameter returns error automatically
tool.execute(emptyMap()) // Error: "Parameter validation failed: Missing required parameter: name"
```

### Custom Validation

Add custom validation logic:

```kotlin
tool("age_validator") {
    parameter("age", "number", "User age")

    canExecute { params ->
        val age = (params["age"] as? Number)?.toInt() ?: 0
        age >= 18  // Must be 18 or older
    }

    execute(fun(params: Map<String, Any>): String {
        return "Access granted"
    })
}
```

## Error Handling

Exceptions are automatically caught and wrapped:

```kotlin
tool("risky_tool") {
    parameter("divisor", "number", "Divisor")

    execute(fun(params: Map<String, Any>): String {
        val divisor = (params["divisor"] as Number).toDouble()

        if (divisor == 0.0) {
            throw ArithmeticException("Division by zero!")
        }

        return "Result: ${100 / divisor}"
    })
}

// Exception caught and returned as ToolResult.error()
tool.execute(mapOf("divisor" to 0))
// Returns: ToolResult(success = false, error = "Division by zero!")
```

## AgentTool DSL

For advanced tools with metadata and tags:

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
        SpiceResult.success(ToolResult.success("Done"))
    }
}
```

## Best Practices

1. **Use Simple Execute** for most tools - it's cleaner and safer
2. **Explicit Function Syntax** - Use `fun(params: Map<String, Any>): String` to avoid overload ambiguity
3. **Validate Required Parameters** - Mark parameters as `required = true`
4. **Throw Exceptions** - They're caught automatically and returned as errors
5. **Return Strings** - Simple string returns are automatically wrapped
6. **Type Safety** - Cast parameters to their expected types early

## Common Patterns

### Calculator Tool

```kotlin
tool("calculate", "Simple calculator") {
    parameter("a", "number", "First number", required = true)
    parameter("b", "number", "Second number", required = true)
    parameter("operation", "string", "Operation (+,-,*,/)", required = true)

    execute(fun(params: Map<String, Any>): String {
        val a = (params["a"] as Number).toDouble()
        val b = (params["b"] as Number).toDouble()
        val op = params["operation"] as String

        val result = when (op) {
            "+" -> a + b
            "-" -> a - b
            "*" -> a * b
            "/" -> if (b != 0.0) a / b else throw ArithmeticException("Division by zero")
            else -> throw IllegalArgumentException("Unknown operation: $op")
        }

        return result.toString()
    })
}
```

### Data Processor Tool

```kotlin
tool("process_data", "Process and transform data") {
    parameter("data", "string", "Input data", required = true)
    parameter("format", "string", "Output format", required = false)

    execute(fun(params: Map<String, Any>): String {
        val data = params["data"] as String
        val format = params["format"] as? String ?: "json"

        return when (format) {
            "json" -> """{"data": "$data"}"""
            "xml" -> "<data>$data</data>"
            "text" -> data
            else -> data
        }
    })
}
```
