# Creating Custom Tools

Build powerful custom tools for your agents.

## Quick Start: Inline Tools

The fastest way to create tools is using the inline DSL:

```kotlin
val myAgent = buildAgent {
    name = "Tool-Powered Agent"

    tool("calculator", "Simple calculator") {
        parameter("a", "number", "First number", required = true)
        parameter("b", "number", "Second number", required = true)
        parameter("operation", "string", "Operation", required = true)

        execute(fun(params: Map<String, Any>): String {
            val a = (params["a"] as Number).toDouble()
            val b = (params["b"] as Number).toDouble()
            val op = params["operation"] as String

            return when (op) {
                "+" -> (a + b).toString()
                "-" -> (a - b).toString()
                "*" -> (a * b).toString()
                "/" -> (a / b).toString()
                else -> "Unknown operation"
            }
        })
    }

    handle { comm ->
        // Agent implementation
        SpiceResult.success(comm.reply("Done"))
    }
}
```

**Benefits:**
- ✅ No class creation needed
- ✅ Automatic parameter validation
- ✅ Exception handling built-in
- ✅ Clean, readable syntax

## Custom Tool Class

For reusable tools, create a custom class:

```kotlin
class CalculatorTool : Tool {
    override val name = "calculator"
    override val description = "Performs basic arithmetic"
    override val schema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "a" to ParameterSchema("number", "First number", required = true),
            "b" to ParameterSchema("number", "Second number", required = true),
            "operation" to ParameterSchema("string", "Operation (+,-,*,/)", required = true)
        )
    )

    override suspend fun execute(parameters: Map<String, Any>): SpiceResult<ToolResult> {
        return try {
            val a = (parameters["a"] as Number).toDouble()
            val b = (parameters["b"] as Number).toDouble()
            val op = parameters["operation"] as String

            val result = when (op) {
                "+" -> a + b
                "-" -> a - b
                "*" -> a * b
                "/" -> if (b != 0.0) a / b else throw ArithmeticException("Division by zero")
                else -> throw IllegalArgumentException("Unknown operation: $op")
            }

            SpiceResult.success(ToolResult.success(result.toString()))
        } catch (e: Exception) {
            SpiceResult.success(ToolResult.error(e.message ?: "Execution failed"))
        }
    }
}

// Use the tool
val agent = buildAgent {
    name = "Calculator Agent"
    tools(ToolRegistry.register(CalculatorTool()))
}
```

## Tool Patterns

### Stateless Tool

Simple, pure function tool:

```kotlin
tool("uppercase") {
    parameter("text", "string", "Text to uppercase", required = true)

    execute(fun(params: Map<String, Any>): String {
        val text = params["text"] as String
        return text.uppercase()
    })
}
```

### Tool with External API

```kotlin
tool("weather") {
    parameter("city", "string", "City name", required = true)

    execute(fun(params: Map<String, Any>): String {
        val city = params["city"] as String

        // Call external API
        val response = weatherApi.getWeather(city)

        return "Weather in $city: ${response.temperature}°C, ${response.condition}"
    })
}
```

### Tool with Database Access

```kotlin
tool("lookup_user") {
    parameter("user_id", "string", "User ID", required = true)

    execute(fun(params: Map<String, Any>): String {
        val userId = params["user_id"] as String

        // Query database
        val user = database.findUser(userId)
            ?: throw IllegalArgumentException("User not found")

        return "User: ${user.name}, Email: ${user.email}"
    })
}
```

### Complex Tool with Validation

```kotlin
class UserManagementTool : Tool {
    override val name = "manage_user"
    override val description = "Create, update, or delete users"
    override val schema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "action" to ParameterSchema("string", "Action (create/update/delete)", required = true),
            "user_id" to ParameterSchema("string", "User ID", required = false),
            "data" to ParameterSchema("object", "User data", required = false)
        )
    )

    override suspend fun execute(parameters: Map<String, Any>): SpiceResult<ToolResult> {
        return try {
            val action = parameters["action"] as String

            when (action) {
                "create" -> createUser(parameters["data"] as? Map<*, *>)
                "update" -> updateUser(
                    parameters["user_id"] as String,
                    parameters["data"] as? Map<*, *>
                )
                "delete" -> deleteUser(parameters["user_id"] as String)
                else -> throw IllegalArgumentException("Unknown action: $action")
            }

            SpiceResult.success(ToolResult.success("User $action successful"))
        } catch (e: Exception) {
            SpiceResult.success(ToolResult.error(e.message ?: "Operation failed"))
        }
    }

    private fun createUser(data: Map<*, *>?): Unit { /* ... */ }
    private fun updateUser(id: String, data: Map<*, *>?): Unit { /* ... */ }
    private fun deleteUser(id: String): Unit { /* ... */ }
}
```

## Best Practices

### 1. Use Inline Tools for Simple Cases

```kotlin
// ✅ Good - Simple, inline
tool("greet") {
    parameter("name", "string", "Name", required = true)
    execute(fun(params: Map<String, Any>): String {
        return "Hello, ${params["name"]}!"
    })
}

// ❌ Overkill - Creating class for simple tool
class GreetTool : Tool { /* 50 lines of boilerplate */ }
```

### 2. Validate Parameters Early

```kotlin
execute(fun(params: Map<String, Any>): String {
    // ✅ Good - Validate and cast early
    val age = (params["age"] as? Number)?.toInt()
        ?: throw IllegalArgumentException("Age must be a number")

    if (age < 0 || age > 150) {
        throw IllegalArgumentException("Invalid age: $age")
    }

    return "Age is valid: $age"
})
```

### 3. Use Descriptive Error Messages

```kotlin
execute(fun(params: Map<String, Any>): String {
    val file = params["file"] as? String
        ?: throw IllegalArgumentException("Missing required parameter: file")

    if (!File(file).exists()) {
        // ✅ Good - Clear error message
        throw FileNotFoundException("File not found: $file. Please check the path and try again.")
    }

    return "File loaded successfully"
})
```

### 4. Handle Async Operations Properly

```kotlin
class AsyncTool : Tool {
    override suspend fun execute(parameters: Map<String, Any>): SpiceResult<ToolResult> {
        return try {
            // ✅ Good - Use suspend functions
            val result = withContext(Dispatchers.IO) {
                performLongRunningOperation()
            }

            SpiceResult.success(ToolResult.success(result))
        } catch (e: Exception) {
            SpiceResult.success(ToolResult.error(e.message ?: "Failed"))
        }
    }
}
```

### 5. Return Structured Data

```kotlin
execute(fun(params: Map<String, Any>): String {
    val user = fetchUser(params["id"] as String)

    // ✅ Good - Return structured JSON
    return buildJsonObject {
        put("id", user.id)
        put("name", user.name)
        put("email", user.email)
        put("status", user.status)
    }.toString()
})
```

## Testing Tools

### Unit Test

```kotlin
@Test
fun `calculator tool should add numbers`() = runTest {
    val tool = CalculatorTool()

    val result = tool.execute(mapOf(
        "a" to 5,
        "b" to 3,
        "operation" to "+"
    ))

    assertTrue(result.isSuccess)
    val toolResult = (result as SpiceResult.Success).value
    assertTrue(toolResult.success)
    assertEquals("8.0", toolResult.result)
}
```

### Integration Test

```kotlin
@Test
fun `agent should use tool correctly`() = runTest {
    val agent = buildAgent {
        name = "Test Agent"

        tool("add") {
            parameter("a", "number", required = true)
            parameter("b", "number", required = true)

            execute(fun(params: Map<String, Any>): String {
                val a = (params["a"] as Number).toDouble()
                val b = (params["b"] as Number).toDouble()
                return (a + b).toString()
            })
        }

        handle { comm ->
            // Use the tool
            val tool = getTools().first { it.name == "add" }
            val result = tool.execute(mapOf("a" to 10, "b" to 20))

            SpiceResult.success(comm.reply(
                result.getOrNull()?.result ?: "Error"
            ))
        }
    }

    val response = agent.processComm(Comm(
        content = "Add numbers",
        from = "user",
        type = CommType.TEXT
    ))

    assertTrue(response.isSuccess)
    assertEquals("30.0", response.getOrNull()?.content)
}
```

## Next Steps

- [Tools DSL Reference](../dsl-guide/tools)
- [Swarm Tools](../orchestration/swarm#swarm-tools)
- [Vector Store Tools](./vector-stores)
