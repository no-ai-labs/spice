# Tool API

Extensible capabilities system for Spice Framework - define actions that agents can perform.

## Overview

`Tool` is the capability abstraction in Spice Framework. Tools enable agents to:

- **Perform actions** - Execute specific tasks like web search, file I/O, calculations
- **Access external systems** - Call APIs, query databases, read sensors
- **Process data** - Transform, validate, analyze information
- **Coordinate** - Facilitate multi-agent interactions

Spice provides multiple ways to create tools:
- **Inline DSL** - Quick tool definitions with automatic validation (recommended)
- **BaseTool** - Abstract base class for custom tools
- **Tool Interface** - Full control for advanced use cases
- **Built-in Tools** - Ready-to-use tools (WebSearch, FileRead, FileWrite)

## Core Structure

```kotlin
interface Tool {
    // Identity
    val name: String
    val description: String
    val schema: ToolSchema

    // Execution
    suspend fun execute(parameters: Map<String, Any>): SpiceResult<ToolResult>
    suspend fun execute(parameters: Map<String, Any>, context: ToolContext): SpiceResult<ToolResult>

    // Validation
    fun canExecute(parameters: Map<String, Any>): Boolean
    fun validateParameters(parameters: Map<String, Any>): ValidationResult
}
```

**Supporting Classes:**

```kotlin
// Tool schema definition
data class ToolSchema(
    val name: String,
    val description: String,
    val parameters: Map<String, ParameterSchema>
)

// Parameter schema
data class ParameterSchema(
    val type: String,              // "string", "number", "boolean", "array", "object"
    val description: String,
    val required: Boolean = false,
    val default: JsonElement? = null
)

// Execution result
data class ToolResult(
    val success: Boolean,
    val result: String = "",
    val error: String = "",
    val metadata: Map<String, String> = emptyMap()
)

// Execution context
data class ToolContext(
    val agentId: String,
    val userId: String? = null,
    val tenantId: String? = null,
    val correlationId: String? = null,
    val metadata: Map<String, Any> = emptyMap()
)
```

## Creating Tools

### Inline DSL (Recommended)

The simplest and most powerful way to create tools:

```kotlin
val agent = buildAgent {
    name = "Calculator Agent"

    tools {
        // Simple calculator tool with automatic validation
        tool("calculate", "Performs basic arithmetic") {
            parameter("a", "number", "First number", required = true)
            parameter("b", "number", "Second number", required = true)
            parameter("operation", "string", "Operation (+, -, *, /)", required = true)

            // Simple execute - automatic result wrapping and error handling
            execute(fun(params: Map<String, Any>): String {
                val a = (params["a"] as Number).toDouble()
                val b = (params["b"] as Number).toDouble()
                val op = params["operation"] as String

                return when (op) {
                    "+" -> (a + b).toString()
                    "-" -> (a - b).toString()
                    "*" -> (a * b).toString()
                    "/" -> if (b != 0.0) (a / b).toString()
                           else throw ArithmeticException("Division by zero")
                    else -> throw IllegalArgumentException("Unknown operation: $op")
                }
            })
        }
    }
}
```

**Key Benefits of Inline DSL:**
- ✅ Automatic parameter validation
- ✅ Automatic error handling
- ✅ Clean, concise syntax
- ✅ Type-safe parameter access after validation

### Advanced Inline DSL

For complex scenarios requiring full control:

```kotlin
tool("weather_search", "Search weather data") {
    parameter("location", "string", "City name", required = true)
    parameter("units", "string", "Temperature units", required = false)

    // Advanced execute with full SpiceResult control
    execute { params: Map<String, Any> ->
        val location = params["location"] as String
        val units = params["units"] as? String ?: "celsius"

        try {
            val weatherData = weatherApi.fetch(location, units)

            SpiceResult.success(ToolResult.success(
                result = "Temperature: ${weatherData.temp}°${units.first().uppercase()}, Condition: ${weatherData.condition}",
                metadata = mapOf(
                    "location" to location,
                    "units" to units,
                    "timestamp" to System.currentTimeMillis().toString()
                )
            ))
        } catch (e: WeatherApiException) {
            SpiceResult.success(ToolResult.error(
                error = "Weather lookup failed: ${e.message}",
                metadata = mapOf("location" to location)
            ))
        } catch (e: Exception) {
            SpiceResult.failure(SpiceError.from(e))
        }
    }

    // Optional: Custom validation logic
    canExecute { params ->
        val location = params["location"] as? String
        location != null && location.length >= 2
    }
}
```

### Context-Aware Tools with Caching and Validation

**New in 0.4.1:** Use `contextAwareTool()` for advanced features like caching and output validation:

```kotlin
val productSearchTool = contextAwareTool("product-search") {
    description = "Search products with caching and validation"

    // Parameters using DSL block (recommended)
    parameters {
        string("query", "Search query", required = true)
        number("limit", "Max results", required = false)
    }

    // Alternative: Individual param() calls
    // param("query", "string", "Search query", required = true)
    // param("limit", "number", "Max results", required = false)

    // Tool-level caching with context-aware keys
    cache {
        ttl = 1800 // 30 minutes
        maxSize = 1000
        enableMetrics = true

        // Custom cache key builder (includes tenant context)
        keyBuilder = { params, context ->
            val query = params["query"] as? String ?: ""
            val limit = params["limit"]?.toString() ?: "10"
            val tenantId = context?.get("tenantId") as? String ?: "default"
            "$tenantId:search:$query:$limit"
        }
    }

    // Output validation ensures data quality
    validate {
        requireField("products", "Products array is required")
        fieldType("products", FieldType.ARRAY)

        rule("non-empty results") { output, _ ->
            val products = (output as? Map<*, *>)?.get("products") as? List<*>
            products?.isNotEmpty() == true
        }
    }

    execute { params, context ->
        val query = params["query"] as String
        val limit = (params["limit"] as? Number)?.toInt() ?: 10

        // Execute search
        val products = searchEngine.search(query, limit)

        // Result automatically validated and cached
        ToolResult.success(mapOf(
            "products" to products,
            "count" to products.size
        ))
    }
}

// Usage in agent
val agent = buildAgent {
    tools {
        +productSearchTool
    }
}
```

#### Cache DSL Block

Configure intelligent caching for expensive operations:

```kotlin
contextAwareTool("api-call") {
    cache {
        ttl = 300 // Time-to-live in seconds (5 minutes)
        maxSize = 500 // Maximum cache entries
        enableMetrics = true // Track hit rate, evictions

        // Optional: Custom cache key builder
        keyBuilder = { params, context ->
            val endpoint = params["endpoint"] as? String ?: ""
            val tenantId = context?.get("tenantId") as? String ?: "default"
            "$tenantId:api:$endpoint"
        }
    }

    execute { params, context ->
        // Implementation
    }
}
```

**Cache Key Builder Options:**

```kotlin
// Default builder (uses all parameters)
keyBuilder = null // Auto-generates from params

// Tenant-aware builder (multi-tenant isolation)
keyBuilder = { params, context ->
    val tenantId = context?.get("tenantId") as? String ?: "default"
    "$tenantId:${params.hashCode()}"
}

// User-specific builder
keyBuilder = { params, context ->
    val userId = context?.get("userId") as? String ?: "anonymous"
    "user:$userId:${params["query"]}"
}

// Custom logic builder
keyBuilder = { params, context ->
    val query = params["query"] as? String ?: ""
    val hash = query.hashCode()
    "search:$hash"
}
```

**Cache Metrics:**

```kotlin
val tool = contextAwareTool("cached-tool") {
    cache { ttl = 600 }
    execute { /* ... */ }
}

// Access metrics using property (recommended)
val metrics = (tool as CachedTool).metrics
println("Hit rate: ${metrics.hitRate * 100}%")
println("Hits: ${metrics.hits}")
println("Misses: ${metrics.misses}")
println("Current size: ${metrics.size}")

// Alternative: Using getCacheStats() method
val stats = (tool as CachedTool).getCacheStats()
println("Max size: ${stats.maxSize}")
println("TTL: ${stats.ttl}s")
```

#### Validate DSL Block

Enforce output schema and business rules:

```kotlin
contextAwareTool("user-lookup") {
    validate {
        // Required fields
        requireField("userId", "User ID is required")
        requireField("email", "Email is required")

        // Type validation
        fieldType("userId", FieldType.STRING)
        fieldType("email", FieldType.STRING)
        fieldType("age", FieldType.NUMBER)

        // Pattern validation
        pattern("email", Regex("^[^@]+@[^@]+\\.[^@]+$"), "Invalid email format")

        // Range validation
        range("age", min = 0.0, max = 150.0, "Invalid age")

        // Custom rules
        rule("email domain whitelist") { output, context ->
            val email = (output as? Map<*, *>)?.get("email") as? String
            email?.endsWith("@company.com") == true
        }

        // Context-aware validation
        rule("tenant match") { output, context ->
            val outputTenant = (output as? Map<*, *>)?.get("tenantId") as? String
            val contextTenant = context?.get("tenantId") as? String
            outputTenant == contextTenant
        }
    }

    execute { params, context ->
        // Implementation
    }
}
```

**Validation Rules:**

| Rule | Purpose | Example |
|------|---------|---------|
| `requireField(field, message?)` | Ensure field exists | `requireField("userId")` |
| `fieldType(field, type, message?)` | Validate field type | `fieldType("age", FieldType.NUMBER)` |
| `range(field, min, max, message?)` | Validate numeric range | `range("age", 0.0, 150.0)` |
| `pattern(field, regex, message?)` | Validate string format | `pattern("email", Regex("..."))` |
| `rule(description, validator)` | Custom validation logic | `rule("custom") { output, ctx -> ... }` |
| `custom(description, validator)` | Alias for rule() | `custom("custom") { output -> ... }` |

**Field Types:**

- `FieldType.STRING` - String values
- `FieldType.NUMBER` - Numeric values (Int, Long, Double, Float, etc.)
- `FieldType.INTEGER` - Integer values only (Int, Long)
- `FieldType.BOOLEAN` - Boolean values
- `FieldType.ARRAY` - List/Array values
- `FieldType.OBJECT` - Map/Object values
- `FieldType.ANY` - Any type (no type checking)

**Combining Cache and Validation:**

```kotlin
contextAwareTool("evidence-search") {
    description = "Search with caching and citation validation"

    cache {
        ttl = 1800
        maxSize = 500
        keyBuilder = { params, context ->
            val query = params["query"] as? String ?: ""
            "evidence:${query.hashCode()}"
        }
    }

    validate {
        requireField("claim")
        requireField("sources")
        fieldType("sources", FieldType.ARRAY)

        rule("sources must have citations") { output, _ ->
            val sources = (output as? Map<*, *>)?.get("sources") as? List<*>
            sources?.all { source ->
                val s = source as? Map<*, *>
                s?.containsKey("url") == true && s.containsKey("title") == true
            } == true
        }
    }

    execute { params, context ->
        // Search implementation
        val results = searchEngine.search(params["query"] as String)

        ToolResult.success(mapOf(
            "claim" to params["query"],
            "sources" to results
        ))
    }
}
```

**See Also:**
- [Tool-Level Caching Guide](../performance/tool-caching) - Complete caching documentation
- [Output Validation Guide](../dsl-guide/output-validation) - Complete validation documentation

### Swarm Tools

Share tools across all swarm members:

```kotlin
val swarm = buildSwarmAgent {
    name = "Data Processing Swarm"

    swarmTools {
        // Shared validation tool
        tool("validate_json", "Validates JSON data") {
            parameter("json_string", "string", "JSON to validate", required = true)

            execute(fun(params: Map<String, Any>): String {
                val jsonString = params["json_string"] as String

                return try {
                    JsonParser.parse(jsonString)
                    "valid"
                } catch (e: JsonParseException) {
                    throw IllegalArgumentException("Invalid JSON: ${e.message}")
                }
            })
        }

        // Shared transformation tool
        tool("transform_data", "Transforms data format") {
            parameter("data", "string", "Data to transform", required = true)
            parameter("format", "string", "Target format (json, xml, csv)", required = true)

            execute(fun(params: Map<String, Any>): String {
                val data = params["data"] as String
                val format = params["format"] as String

                return when (format) {
                    "json" -> toJson(data)
                    "xml" -> toXml(data)
                    "csv" -> toCsv(data)
                    else -> throw IllegalArgumentException("Unsupported format: $format")
                }
            })
        }
    }

    quickSwarm {
        specialist("validator", "Validator", "data validation")
        specialist("transformer", "Transformer", "data transformation")
    }
}
```

### BaseTool Implementation

For reusable tool classes:

```kotlin
class DatabaseQueryTool(
    private val dataSource: DataSource
) : BaseTool() {
    override val name = "database_query"
    override val description = "Execute SQL queries"
    override val schema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "query" to ParameterSchema(
                type = "string",
                description = "SQL query to execute",
                required = true
            ),
            "limit" to ParameterSchema(
                type = "number",
                description = "Maximum rows to return",
                required = false
            )
        )
    )

    override suspend fun execute(parameters: Map<String, Any>): SpiceResult<ToolResult> {
        val query = parameters["query"] as? String
            ?: return SpiceResult.success(ToolResult.error("Query parameter required"))

        val limit = (parameters["limit"] as? Number)?.toInt() ?: 100

        return try {
            val connection = dataSource.connection
            val statement = connection.prepareStatement("$query LIMIT ?")
            statement.setInt(1, limit)

            val resultSet = statement.executeQuery()
            val results = mutableListOf<Map<String, Any>>()

            while (resultSet.next()) {
                val row = mutableMapOf<String, Any>()
                val metadata = resultSet.metaData
                for (i in 1..metadata.columnCount) {
                    row[metadata.getColumnName(i)] = resultSet.getObject(i)
                }
                results.add(row)
            }

            resultSet.close()
            statement.close()
            connection.close()

            SpiceResult.success(ToolResult.success(
                result = results.joinToString("\n") { it.toString() },
                metadata = mapOf(
                    "row_count" to results.size.toString(),
                    "query" to query
                )
            ))

        } catch (e: SQLException) {
            SpiceResult.success(ToolResult.error(
                error = "Query failed: ${e.message}",
                metadata = mapOf("sql_state" to (e.sqlState ?: ""))
            ))
        }
    }

    override fun canExecute(parameters: Map<String, Any>): Boolean {
        val query = parameters["query"] as? String ?: return false

        // Basic SQL injection prevention
        val forbidden = listOf("DROP", "DELETE", "TRUNCATE", "ALTER")
        return forbidden.none { query.uppercase().contains(it) }
    }
}

// Usage
val agent = buildAgent {
    name = "Database Agent"

    tools {
        tool(DatabaseQueryTool(dataSource))
    }
}
```

### Full Tool Implementation

Maximum control for complex tools:

```kotlin
class VideoProcessingTool : Tool {
    override val name = "process_video"
    override val description = "Process and analyze video files"
    override val schema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "video_url" to ParameterSchema("string", "Video URL", required = true),
            "operations" to ParameterSchema("array", "Processing operations", required = true),
            "quality" to ParameterSchema("string", "Output quality", required = false)
        )
    )

    override suspend fun execute(
        parameters: Map<String, Any>
    ): SpiceResult<ToolResult> {
        return execute(parameters, ToolContext(agentId = "default"))
    }

    override suspend fun execute(
        parameters: Map<String, Any>,
        context: ToolContext
    ): SpiceResult<ToolResult> {
        // Validate first
        val validation = validateParameters(parameters)
        if (!validation.valid) {
            return SpiceResult.success(ToolResult.error(
                error = validation.errors.joinToString(", ")
            ))
        }

        val videoUrl = parameters["video_url"] as String
        val operations = parameters["operations"] as List<*>
        val quality = parameters["quality"] as? String ?: "medium"

        return withContext(Dispatchers.IO) {
            try {
                // Download video
                val videoFile = downloadVideo(videoUrl)

                // Process operations
                val results = operations.map { op ->
                    processOperation(videoFile, op as String, quality)
                }

                // Upload processed video
                val outputUrl = uploadProcessedVideo(videoFile)

                SpiceResult.success(ToolResult.success(
                    result = outputUrl,
                    metadata = mapOf(
                        "operations_count" to operations.size.toString(),
                        "quality" to quality,
                        "duration_ms" to videoFile.duration.toString(),
                        "agent_id" to context.agentId,
                        "correlation_id" to (context.correlationId ?: "")
                    )
                ))

            } catch (e: Exception) {
                SpiceResult.success(ToolResult.error(
                    error = "Video processing failed: ${e.message}"
                ))
            }
        }
    }

    override fun canExecute(parameters: Map<String, Any>): Boolean {
        val videoUrl = parameters["video_url"] as? String ?: return false
        return videoUrl.startsWith("http") &&
               (videoUrl.endsWith(".mp4") || videoUrl.endsWith(".mov"))
    }

    private suspend fun downloadVideo(url: String): VideoFile {
        // Implementation
        TODO()
    }

    private suspend fun processOperation(
        video: VideoFile,
        operation: String,
        quality: String
    ): ProcessResult {
        // Implementation
        TODO()
    }

    private suspend fun uploadProcessedVideo(video: VideoFile): String {
        // Implementation
        TODO()
    }
}
```

## Tool Schema & Parameters

### ToolSchema

Defines the structure and contract of a tool:

```kotlin
val schema = ToolSchema(
    name = "user_lookup",
    description = "Look up user information by ID",
    parameters = mapOf(
        "user_id" to ParameterSchema(
            type = "string",
            description = "User ID to look up",
            required = true
        ),
        "include_history" to ParameterSchema(
            type = "boolean",
            description = "Include interaction history",
            required = false
        ),
        "fields" to ParameterSchema(
            type = "array",
            description = "Specific fields to return",
            required = false
        )
    )
)
```

### Parameter Types

Supported parameter types:

```kotlin
// String parameter
parameter("name", "string", "User name", required = true)

// Number parameter (integers and floats)
parameter("age", "number", "User age", required = true)

// Boolean parameter
parameter("active", "boolean", "Is user active", required = false)

// Array parameter
parameter("tags", "array", "User tags", required = false)

// Object parameter
parameter("metadata", "object", "Additional metadata", required = false)
```

**Usage Example:**

```kotlin
tool("create_user", "Creates a new user") {
    parameter("name", "string", "User's full name", required = true)
    parameter("email", "string", "Email address", required = true)
    parameter("age", "number", "Age in years", required = true)
    parameter("active", "boolean", "Account active status", required = false)
    parameter("roles", "array", "User roles", required = false)
    parameter("settings", "object", "User preferences", required = false)

    execute(fun(params: Map<String, Any>): String {
        val name = params["name"] as String
        val email = params["email"] as String
        val age = (params["age"] as Number).toInt()
        val active = params["active"] as? Boolean ?: true
        val roles = params["roles"] as? List<*> ?: emptyList<String>()
        val settings = params["settings"] as? Map<*, *> ?: emptyMap<String, Any>()

        val user = User(
            name = name,
            email = email,
            age = age,
            active = active,
            roles = roles.map { it.toString() },
            settings = settings.mapKeys { it.key.toString() }
        )

        database.save(user)
        return "User created: ${user.id}"
    })
}
```

### Default Values

Specify default values for optional parameters:

```kotlin
import kotlinx.serialization.json.JsonPrimitive

tool("search", "Search documents") {
    parameter("query", "string", "Search query", required = true)
    parameter("limit", "number", "Max results", required = false)

    // Access defaults through schema
    val limitDefault = JsonPrimitive(10)

    execute(fun(params: Map<String, Any>): String {
        val query = params["query"] as String
        val limit = (params["limit"] as? Number)?.toInt() ?: 10

        val results = search(query, limit)
        return results.joinToString("\n")
    })
}
```

## Execution & Results

### Simple Execute

Automatic success wrapping and error handling:

```kotlin
tool("greet", "Greets a user") {
    parameter("name", "string", "User name", required = true)

    // Return value automatically wrapped in ToolResult.success()
    // Exceptions automatically wrapped in ToolResult.error()
    execute(fun(params: Map<String, Any>): String {
        val name = params["name"] as String
        return "Hello, $name!"
    })
}

// Usage
val result = tool.execute(mapOf("name" to "Alice"))
// result = SpiceResult.success(ToolResult(success = true, result = "Hello, Alice!"))
```

### Advanced Execute

Full control over result and error handling:

```kotlin
tool("api_call", "Calls external API") {
    parameter("endpoint", "string", "API endpoint", required = true)

    execute { params: Map<String, Any> ->
        val endpoint = params["endpoint"] as String

        try {
            val response = httpClient.get(endpoint)

            when (response.status) {
                200 -> SpiceResult.success(ToolResult.success(
                    result = response.body,
                    metadata = mapOf(
                        "status_code" to "200",
                        "content_type" to response.contentType
                    )
                ))
                404 -> SpiceResult.success(ToolResult.error(
                    error = "Endpoint not found: $endpoint",
                    metadata = mapOf("status_code" to "404")
                ))
                500 -> SpiceResult.failure(SpiceError(
                    message = "Server error",
                    code = "API_ERROR",
                    details = mapOf("endpoint" to endpoint)
                ))
                else -> SpiceResult.success(ToolResult.error(
                    error = "Unexpected status: ${response.status}"
                ))
            }
        } catch (e: IOException) {
            SpiceResult.failure(SpiceError.from(e))
        }
    }
}
```

### ToolResult

Result structure for tool execution:

```kotlin
// Success result
val success = ToolResult.success(
    result = "Operation completed successfully",
    metadata = mapOf(
        "duration_ms" to "150",
        "records_processed" to "42"
    )
)

// Error result
val error = ToolResult.error(
    error = "Operation failed: Invalid input",
    metadata = mapOf(
        "error_code" to "INVALID_INPUT",
        "attempted_value" to "xyz"
    )
)

// Check result
if (toolResult.success) {
    println("Result: ${toolResult.result}")
    println("Metadata: ${toolResult.metadata}")
} else {
    println("Error: ${toolResult.error}")
}
```

### Execution with Context

Access runtime context during execution:

```kotlin
class AuditedTool : BaseTool() {
    override val name = "audited_action"
    override val description = "Action with full audit trail"
    override val schema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "action" to ParameterSchema("string", "Action to perform", required = true)
        )
    )

    override suspend fun execute(
        parameters: Map<String, Any>,
        context: ToolContext
    ): SpiceResult<ToolResult> {
        val action = parameters["action"] as String

        // Log with context
        auditLog.record(AuditEntry(
            action = action,
            agentId = context.agentId,
            userId = context.userId,
            tenantId = context.tenantId,
            correlationId = context.correlationId,
            timestamp = System.currentTimeMillis()
        ))

        // Perform action
        val result = performAction(action)

        return SpiceResult.success(ToolResult.success(
            result = result,
            metadata = mapOf(
                "agent_id" to context.agentId,
                "user_id" to (context.userId ?: "unknown"),
                "tenant_id" to (context.tenantId ?: "default")
            )
        ))
    }
}
```

## Validation

### Automatic Validation

Inline tools automatically validate required parameters:

```kotlin
tool("divide", "Divides two numbers") {
    parameter("dividend", "number", "Number to divide", required = true)
    parameter("divisor", "number", "Number to divide by", required = true)

    execute(fun(params: Map<String, Any>): String {
        // At this point, both parameters are guaranteed to exist
        val dividend = (params["dividend"] as Number).toDouble()
        val divisor = (params["divisor"] as Number).toDouble()

        if (divisor == 0.0) {
            throw ArithmeticException("Cannot divide by zero")
        }

        return (dividend / divisor).toString()
    })
}

// Missing required parameter
val result = tool.execute(mapOf("dividend" to 10))
// result = SpiceResult.success(ToolResult(
//     success = false,
//     error = "Parameter validation failed: Missing required parameter: divisor"
// ))
```

### Manual Validation

Validate parameters explicitly:

```kotlin
val tool = DatabaseQueryTool(dataSource)

val params = mapOf("query" to "SELECT * FROM users")
val validation = tool.validateParameters(params)

if (!validation.valid) {
    println("Validation errors:")
    validation.errors.forEach { error ->
        println("  - $error")
    }
} else {
    val result = tool.execute(params)
}
```

### Custom Validation

Implement custom validation logic:

```kotlin
tool("create_order", "Creates a new order") {
    parameter("product_id", "string", "Product ID", required = true)
    parameter("quantity", "number", "Order quantity", required = true)
    parameter("price", "number", "Unit price", required = true)

    // Custom validation beyond required/type checks
    canExecute { params ->
        val quantity = (params["quantity"] as? Number)?.toInt() ?: return@canExecute false
        val price = (params["price"] as? Number)?.toDouble() ?: return@canExecute false

        // Business rules
        quantity > 0 && quantity <= 1000 && price > 0.0 && price <= 10000.0
    }

    execute(fun(params: Map<String, Any>): String {
        val productId = params["product_id"] as String
        val quantity = (params["quantity"] as Number).toInt()
        val price = (params["price"] as Number).toDouble()

        val order = Order(productId, quantity, price)
        orderService.create(order)

        return "Order created: ${order.id}"
    })
}

// Invalid quantity
val result = tool.execute(mapOf(
    "product_id" to "PROD-123",
    "quantity" to 5000,  // Exceeds limit
    "price" to 99.99
))
// canExecute returns false, execution prevented
```

### ValidationResult

Structure returned by validateParameters():

```kotlin
data class ValidationResult(
    val valid: Boolean,
    val errors: List<String>
)

// Example usage
val validation = tool.validateParameters(params)

if (validation.valid) {
    println("Parameters valid!")
} else {
    println("Validation failed:")
    validation.errors.forEach { error ->
        println("  - $error")
    }
}
```

## Tool Context

Runtime context for tool execution:

```kotlin
val context = ToolContext(
    agentId = "agent-123",
    userId = "user-456",
    tenantId = "tenant-789",
    correlationId = "corr-abc",
    metadata = mapOf(
        "request_id" to "req-xyz",
        "session_id" to "sess-def"
    )
)

val result = tool.execute(parameters, context)
```

**Common Use Cases:**

```kotlin
// Multi-tenant tool
class TenantAwareTool : BaseTool() {
    override suspend fun execute(
        parameters: Map<String, Any>,
        context: ToolContext
    ): SpiceResult<ToolResult> {
        val tenantId = context.tenantId ?: return SpiceResult.success(
            ToolResult.error("Tenant ID required")
        )

        // Query tenant-specific data
        val data = database.query(tenantId, parameters)

        return SpiceResult.success(ToolResult.success(data))
    }
}

// Audit logging tool
class AuditTool : BaseTool() {
    override suspend fun execute(
        parameters: Map<String, Any>,
        context: ToolContext
    ): SpiceResult<ToolResult> {
        val action = parameters["action"] as String

        // Log with full context
        logger.info(
            "Tool execution: $name, Action: $action, " +
            "Agent: ${context.agentId}, User: ${context.userId}, " +
            "Correlation: ${context.correlationId}"
        )

        val result = performAction(action)

        return SpiceResult.success(ToolResult.success(result))
    }
}

// Distributed tracing tool
class TracedTool : BaseTool() {
    override suspend fun execute(
        parameters: Map<String, Any>,
        context: ToolContext
    ): SpiceResult<ToolResult> {
        val span = tracer.buildSpan(name)
            .withTag("agent.id", context.agentId)
            .withTag("user.id", context.userId ?: "")
            .withTag("correlation.id", context.correlationId ?: "")
            .start()

        return try {
            val result = performAction(parameters)
            span.setTag("result.success", true)
            SpiceResult.success(ToolResult.success(result))
        } catch (e: Exception) {
            span.setTag("result.success", false)
            span.setTag("error.message", e.message ?: "")
            SpiceResult.failure(SpiceError.from(e))
        } finally {
            span.finish()
        }
    }
}
```

## Built-in Tools

Spice provides ready-to-use tools for common tasks:

### WebSearchTool

Search the web:

```kotlin
val tool = WebSearchTool()

val result = tool.execute(mapOf(
    "query" to "Kotlin coroutines tutorial",
    "limit" to 5
))

result.fold(
    onSuccess = { toolResult ->
        if (toolResult.success) {
            println(toolResult.result)
            println("Found: ${toolResult.metadata["resultCount"]} results")
        }
    },
    onFailure = { error ->
        println("Search failed: ${error.message}")
    }
)
```

### FileReadTool

Read files:

```kotlin
val tool = FileReadTool()

val result = tool.execute(mapOf(
    "path" to "/path/to/file.txt"
))

result.fold(
    onSuccess = { toolResult ->
        if (toolResult.success) {
            println("File content: ${toolResult.result}")
            println("File size: ${toolResult.metadata["size"]} bytes")
        } else {
            println("Read failed: ${toolResult.error}")
        }
    },
    onFailure = { error ->
        println("Error: ${error.message}")
    }
)
```

### FileWriteTool

Write files:

```kotlin
val tool = FileWriteTool()

val result = tool.execute(mapOf(
    "path" to "/path/to/output.txt",
    "content" to "Hello, World!"
))

result.fold(
    onSuccess = { toolResult ->
        if (toolResult.success) {
            println("File written successfully")
            println("Bytes written: ${toolResult.metadata["size"]}")
        } else {
            println("Write failed: ${toolResult.error}")
        }
    },
    onFailure = { error ->
        println("Error: ${error.message}")
    }
)
```

## Real-World Examples

### API Integration Tool

```kotlin
class StripePaymentTool(
    private val stripeApiKey: String
) : BaseTool() {
    override val name = "create_payment"
    override val description = "Create a Stripe payment intent"
    override val schema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "amount" to ParameterSchema("number", "Amount in cents", required = true),
            "currency" to ParameterSchema("string", "Currency code", required = true),
            "customer_id" to ParameterSchema("string", "Stripe customer ID", required = false)
        )
    )

    private val stripe = Stripe(stripeApiKey)

    override suspend fun execute(
        parameters: Map<String, Any>
    ): SpiceResult<ToolResult> {
        val amount = (parameters["amount"] as Number).toLong()
        val currency = parameters["currency"] as String
        val customerId = parameters["customer_id"] as? String

        return try {
            val params = PaymentIntentCreateParams.builder()
                .setAmount(amount)
                .setCurrency(currency)
                .apply {
                    customerId?.let { setCustomer(it) }
                }
                .build()

            val intent = PaymentIntent.create(params)

            SpiceResult.success(ToolResult.success(
                result = intent.clientSecret,
                metadata = mapOf(
                    "payment_intent_id" to intent.id,
                    "amount" to amount.toString(),
                    "currency" to currency,
                    "status" to intent.status
                )
            ))

        } catch (e: StripeException) {
            SpiceResult.success(ToolResult.error(
                error = "Payment creation failed: ${e.message}",
                metadata = mapOf(
                    "error_code" to e.code.orEmpty(),
                    "error_type" to e::class.simpleName.orEmpty()
                )
            ))
        }
    }

    override fun canExecute(parameters: Map<String, Any>): Boolean {
        val amount = (parameters["amount"] as? Number)?.toLong() ?: return false
        val currency = parameters["currency"] as? String ?: return false

        return amount > 0 && amount <= 99999999 && // $999,999.99 max
               currency.length == 3 && currency.matches(Regex("[a-z]{3}"))
    }
}

// Usage
val agent = buildAgent {
    name = "Payment Agent"

    tools {
        tool(StripePaymentTool(env["STRIPE_API_KEY"]!!))
    }

    instructions = """
        You process payments using Stripe.
        Always validate amounts and currencies before creating payment intents.
    """.trimIndent()
}
```

### Database Tool with Connection Pool

```kotlin
class DatabaseTool(
    private val pool: HikariDataSource
) : BaseTool() {
    override val name = "execute_query"
    override val description = "Execute SQL query with connection pooling"
    override val schema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "query" to ParameterSchema("string", "SQL query", required = true),
            "params" to ParameterSchema("array", "Query parameters", required = false),
            "timeout" to ParameterSchema("number", "Query timeout (seconds)", required = false)
        )
    )

    override suspend fun execute(
        parameters: Map<String, Any>,
        context: ToolContext
    ): SpiceResult<ToolResult> = withContext(Dispatchers.IO) {
        val query = parameters["query"] as String
        val queryParams = parameters["params"] as? List<*> ?: emptyList<Any>()
        val timeout = (parameters["timeout"] as? Number)?.toInt() ?: 30

        pool.connection.use { conn ->
            try {
                conn.prepareStatement(query).use { stmt ->
                    stmt.queryTimeout = timeout

                    // Set parameters
                    queryParams.forEachIndexed { index, param ->
                        stmt.setObject(index + 1, param)
                    }

                    // Execute query
                    val resultSet = stmt.executeQuery()
                    val results = mutableListOf<Map<String, Any?>>()

                    while (resultSet.next()) {
                        val row = mutableMapOf<String, Any?>()
                        val metaData = resultSet.metaData
                        for (i in 1..metaData.columnCount) {
                            row[metaData.getColumnName(i)] = resultSet.getObject(i)
                        }
                        results.add(row)
                    }

                    SpiceResult.success(ToolResult.success(
                        result = Json.encodeToString(results),
                        metadata = mapOf(
                            "row_count" to results.size.toString(),
                            "agent_id" to context.agentId,
                            "tenant_id" to (context.tenantId ?: "default")
                        )
                    ))
                }
            } catch (e: SQLException) {
                SpiceResult.success(ToolResult.error(
                    error = "Query failed: ${e.message}",
                    metadata = mapOf(
                        "sql_state" to (e.sqlState ?: ""),
                        "error_code" to e.errorCode.toString()
                    )
                ))
            } catch (e: SQLTimeoutException) {
                SpiceResult.success(ToolResult.error(
                    error = "Query timeout after ${timeout}s"
                ))
            }
        }
    }

    override fun canExecute(parameters: Map<String, Any>): Boolean {
        val query = parameters["query"] as? String ?: return false

        // Basic SQL injection prevention
        val upperQuery = query.uppercase().trim()
        val forbidden = listOf("DROP ", "DELETE ", "TRUNCATE ", "ALTER ", "CREATE ")

        return forbidden.none { upperQuery.startsWith(it) }
    }
}
```

### Caching Tool

```kotlin
class CachedTool(
    private val delegate: Tool,
    private val ttlMs: Long = 60000
) : Tool by delegate {
    private data class CacheEntry(
        val result: SpiceResult<ToolResult>,
        val timestamp: Long
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    override suspend fun execute(
        parameters: Map<String, Any>
    ): SpiceResult<ToolResult> {
        val cacheKey = generateCacheKey(parameters)
        val cached = cache[cacheKey]

        // Check cache
        if (cached != null) {
            val age = System.currentTimeMillis() - cached.timestamp
            if (age < ttlMs) {
                return cached.result
            } else {
                cache.remove(cacheKey)
            }
        }

        // Execute and cache
        val result = delegate.execute(parameters)
        cache[cacheKey] = CacheEntry(result, System.currentTimeMillis())

        return result
    }

    private fun generateCacheKey(parameters: Map<String, Any>): String {
        return "${delegate.name}:${parameters.entries.sortedBy { it.key }
            .joinToString(":") { "${it.key}=${it.value}" }}"
    }

    fun clearCache() {
        cache.clear()
    }

    fun getCacheStats(): CacheStats {
        val now = System.currentTimeMillis()
        val entries = cache.values

        return CacheStats(
            totalEntries = entries.size,
            validEntries = entries.count { now - it.timestamp < ttlMs },
            expiredEntries = entries.count { now - it.timestamp >= ttlMs }
        )
    }

    data class CacheStats(
        val totalEntries: Int,
        val validEntries: Int,
        val expiredEntries: Int
    )
}

// Usage
val weatherTool = WeatherTool()
val cachedWeatherTool = CachedTool(
    delegate = weatherTool,
    ttlMs = 300000  // 5 minutes
)

val agent = buildAgent {
    name = "Weather Agent"

    tools {
        tool(cachedWeatherTool)
    }
}

// Check cache stats
val stats = cachedWeatherTool.getCacheStats()
println("Cache: ${stats.validEntries} valid, ${stats.expiredEntries} expired")
```

## Best Practices

### 1. Use Appropriate Creation Method

```kotlin
// ✅ Good - Simple inline tool for basic operations
tool("calculate", "Calculator") {
    parameter("a", "number", required = true)
    parameter("b", "number", required = true)

    execute(fun(params: Map<String, Any>): String {
        val a = (params["a"] as Number).toDouble()
        val b = (params["b"] as Number).toDouble()
        return (a + b).toString()
    })
}

// ✅ Good - BaseTool for reusable, stateful tools
class DatabaseTool(private val dataSource: DataSource) : BaseTool() {
    // Complex implementation with connection management
}

// ❌ Bad - Overengineering simple tools
class AdditionTool : BaseTool() {  // Too complex for simple addition!
    override val name = "add"
    // ... 50 lines of boilerplate for simple addition
}
```

### 2. Validate Inputs Properly

```kotlin
// ✅ Good - Comprehensive validation
tool("create_user", "Creates user") {
    parameter("email", "string", required = true)
    parameter("age", "number", required = true)

    canExecute { params ->
        val email = params["email"] as? String ?: return@canExecute false
        val age = (params["age"] as? Number)?.toInt() ?: return@canExecute false

        email.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) &&
        age in 18..120
    }

    execute(fun(params: Map<String, Any>): String {
        // Safe to use params here
        val email = params["email"] as String
        val age = (params["age"] as Number).toInt()

        createUser(email, age)
        return "User created"
    })
}

// ❌ Bad - No validation
tool("create_user") {
    execute(fun(params: Map<String, Any>): String {
        val email = params["email"] as String  // Could crash!
        createUser(email, 0)
    })
}
```

### 3. Handle Errors Gracefully

```kotlin
// ✅ Good - Proper error handling
tool("api_call", "Call external API") {
    parameter("endpoint", "string", required = true)

    execute(fun(params: Map<String, Any>): String {
        val endpoint = params["endpoint"] as String

        return try {
            val response = apiClient.get(endpoint)
            response.body
        } catch (e: IOException) {
            throw IOException("Network error: ${e.message}")
        } catch (e: TimeoutException) {
            throw TimeoutException("Request timeout for: $endpoint")
        } catch (e: Exception) {
            throw RuntimeException("Unexpected error: ${e.message}")
        }
    })
}

// ❌ Bad - Swallowing errors
tool("api_call") {
    execute(fun(params: Map<String, Any>): String {
        try {
            return apiClient.get(params["endpoint"] as String).body
        } catch (e: Exception) {
            return ""  // Silent failure!
        }
    })
}
```

### 4. Include Useful Metadata

```kotlin
// ✅ Good - Rich metadata
tool("search", "Search documents") {
    parameter("query", "string", required = true)

    execute { params ->
        val query = params["query"] as String
        val startTime = System.currentTimeMillis()

        val results = searchEngine.search(query)
        val duration = System.currentTimeMillis() - startTime

        SpiceResult.success(ToolResult.success(
            result = results.joinToString("\n"),
            metadata = mapOf(
                "query" to query,
                "result_count" to results.size.toString(),
                "duration_ms" to duration.toString(),
                "timestamp" to System.currentTimeMillis().toString(),
                "search_engine" to "elasticsearch",
                "index" to "documents"
            )
        ))
    }
}

// ❌ Bad - No metadata
tool("search") {
    execute(fun(params: Map<String, Any>): String {
        return searchEngine.search(params["query"] as String).joinToString("\n")
    })
}
```

### 5. Write Descriptive Schemas

```kotlin
// ✅ Good - Clear, detailed descriptions
tool("send_email", "Sends an email via SMTP with attachments support") {
    parameter(
        "to",
        "string",
        "Recipient email address (e.g., user@example.com)",
        required = true
    )
    parameter(
        "subject",
        "string",
        "Email subject line (max 200 characters)",
        required = true
    )
    parameter(
        "body",
        "string",
        "Email body content (HTML supported)",
        required = true
    )
    parameter(
        "attachments",
        "array",
        "List of file paths to attach (max 10MB total)",
        required = false
    )

    execute(fun(params: Map<String, Any>): String {
        // Implementation
    })
}

// ❌ Bad - Vague descriptions
tool("send_email", "Sends email") {
    parameter("to", "string", "To", required = true)
    parameter("subject", "string", "Subject", required = true)
    parameter("body", "string", "Body", required = true)
}
```

## Next Steps

- [Agent API](./agent) - Learn about agents that use tools
- [DSL API](./dsl) - Master the DSL for building agents and tools
- [Comm API](./comm) - Understand communication system
- [Creating Custom Tools](../tools-extensions/creating-tools) - Deep dive into tool development
- [Tool Patterns](../tools-extensions/tool-patterns) - Advanced tool patterns
