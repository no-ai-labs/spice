# Tool

Tools are reusable functions that agents can execute.

## What is a Tool?

A Tool in Spice Framework is a well-defined function with:
- **Name** and **Description**
- **Parameter Schema** with validation
- **Execution Logic** (async-capable)
- **Result Handling**

## Tool Interface

```kotlin
// v0.9.0: Updated to support Map<String, Any?> for native types and null values
interface Tool {
    val name: String
    val description: String
    val schema: ToolSchema

    suspend fun execute(parameters: Map<String, Any?>): ToolResult
    fun canExecute(parameters: Map<String, Any?>): Boolean
    fun validateParameters(parameters: Map<String, Any?>): ValidationResult
}
```

## Creating Tools

### Inline Tool (Recommended)

```kotlin
buildAgent {
    tool("calculator") {
        description = "Perform mathematical operations"
        parameter("operation", "string", "Operation: add, subtract, multiply, divide")
        parameter("a", "number", "First number", required = true)
        parameter("b", "number", "Second number", required = true)

        execute { params ->
            // v0.9.0: Safe nullable handling
            val op = params["operation"]?.toString()
                ?: throw IllegalArgumentException("Missing 'operation'")
            val a = (params["a"] as? Number)?.toDouble()
                ?: throw IllegalArgumentException("Missing 'a'")
            val b = (params["b"] as? Number)?.toDouble()
                ?: throw IllegalArgumentException("Missing 'b'")

            val result = when (op) {
                "add" -> a + b
                "subtract" -> a - b
                "multiply" -> a * b
                "divide" -> if (b != 0.0) a / b else 0.0
                else -> 0.0
            }

            ToolResult.success("Result: $result")
        }
    }
}
```

### Custom Tool Class

```kotlin
class WeatherTool : BaseTool() {
    override val name = "get_weather"
    override val description = "Get weather information"
    override val schema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "city" to ParameterSchema("string", "City name", required = true),
            "units" to ParameterSchema("string", "Temperature units", required = false)
        )
    )

    // v0.9.0: Updated parameter type to Map<String, Any?>
    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        val city = parameters["city"]?.toString()
            ?: return ToolResult.error("City is required")

        val units = parameters["units"]?.toString() ?: "celsius"

        // Fetch weather data
        val weather = fetchWeather(city, units)

        // v0.9.0: Metadata supports native types
        return ToolResult.success(
            result = "Weather in $city: ${weather.temp}°$units, ${weather.condition}",
            metadata = mapOf(
                "city" to city,
                "units" to units,
                "temperature" to weather.temp,  // v0.9.0: Int natively supported
                "cached" to false               // v0.9.0: Boolean natively supported
            )
        )
    }

    private suspend fun fetchWeather(city: String, units: String): Weather {
        // Implementation
        return Weather(temp = 22, condition = "Sunny")
    }
}

data class Weather(val temp: Int, val condition: String)
```

## Tool Result

```kotlin
// v0.9.0: Success result with native types
ToolResult.success(
    result = "Operation successful",
    metadata = mapOf(
        "duration_ms" to 100,     // v0.9.0: Int
        "success" to true         // v0.9.0: Boolean
    )
)

// v0.9.0: Error result with native types
ToolResult.error(
    error = "Invalid input",
    metadata = mapOf(
        "code" to "INVALID_PARAM",
        "retry_count" to 3,       // v0.9.0: Int
        "fatal" to false          // v0.9.0: Boolean
    )
)
```

## Tool Registry

### Register Tools

```kotlin
// Register in global namespace
ToolRegistry.register(weatherTool, namespace = "global")

// Register in specific namespace
ToolRegistry.register(customTool, namespace = "custom")

// Get tool
val tool = ToolRegistry.getTool("get_weather", namespace = "global")
```

### Search Tools

```kotlin
// By namespace
val globalTools = ToolRegistry.getByNamespace("global")

// By tag
val calculators = ToolRegistry.getByTag("math")

// By source
val agentTools = ToolRegistry.getBySource("agent-tool")
```

## Tool Execution in Agents

```kotlin
buildAgent {
    id = "tool-user"

    tool("my_tool") { /* ... */ }

    handle { comm ->
        // Execute tool (v0.9.0: parameters support native types)
        val result = run("my_tool", mapOf(
            "param" to "value",
            "count" to 10,        // v0.9.0: Int
            "enabled" to true     // v0.9.0: Boolean
        ))

        if (result.success) {
            comm.reply(result.result, id)
        } else {
            comm.error(result.error, id)
        }
    }
}
```

## Built-in Tools

Spice provides several built-in tools:

- **WebSearchTool** - Web search capabilities
- **FileReadTool** - Read files
- **FileWriteTool** - Write files

## Next Steps

- [Tool Registry](./registry)
- [Creating Advanced Tools](../tools-extensions/creating-tools)
- [MCP Integration](../tools-extensions/mcp)
