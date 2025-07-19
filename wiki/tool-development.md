# Tool Development Guide

## Understanding Tools

Tools are reusable units of functionality that agents can leverage. They encapsulate specific capabilities and provide a consistent interface for agents to use.

## Tool Interface

### Core Tool Interface
```kotlin
interface Tool {
    val name: String
    val description: String
    val schema: ToolSchema
    suspend fun execute(params: Map<String, Any>): ToolResult
}
```

### ToolResult Structure
```kotlin
data class ToolResult(
    val success: Boolean,
    val result: String,
    val error: String? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    companion object {
        fun success(result: String, metadata: Map<String, Any> = emptyMap()) = 
            ToolResult(true, result, null, metadata)
            
        fun error(error: String) = 
            ToolResult(false, "", error)
    }
}
```

## Creating Tools

### 1. SimpleTool (Recommended)
```kotlin
val wordCountTool = SimpleTool(
    name = "word-counter",
    description = "Counts words in provided text",
    parameterSchemas = mapOf(
        "text" to ParameterSchema(
            type = "string",
            description = "Text to count words in",
            required = true
        ),
        "ignoreCase" to ParameterSchema(
            type = "boolean", 
            description = "Whether to ignore case",
            required = false
        )
    )
) { params ->
    val text = params["text"] as String
    val ignoreCase = params["ignoreCase"] as? Boolean ?: false
    
    val words = if (ignoreCase) {
        text.lowercase().split("\\s+".toRegex())
    } else {
        text.split("\\s+".toRegex())
    }
    
    ToolResult.success(
        "Word count: ${words.size}",
        mapOf("words" to words, "count" to words.size)
    )
}
```

### 2. Custom Tool Implementation
```kotlin
class DatabaseQueryTool(
    private val connectionString: String
) : Tool {
    override val name = "db-query"
    override val description = "Executes database queries"
    override val schema = ToolSchema(
        parameters = mapOf(
            "query" to ParameterSchema("string", "SQL query", true),
            "limit" to ParameterSchema("number", "Result limit", false)
        )
    )
    
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        return try {
            val query = params["query"] as String
            val limit = params["limit"] as? Int ?: 100
            
            // Execute query (pseudo-code)
            val results = executeQuery(query, limit)
            
            ToolResult.success(
                "Query executed successfully",
                mapOf("rowCount" to results.size, "data" to results)
            )
        } catch (e: Exception) {
            ToolResult.error("Database error: ${e.message}")
        }
    }
    
    private fun executeQuery(query: String, limit: Int): List<Map<String, Any>> {
        // Implementation here
        return emptyList()
    }
}
```

### 3. Async Tool with Coroutines
```kotlin
val weatherTool = SimpleTool(
    name = "weather",
    description = "Gets current weather for a location",
    parameterSchemas = mapOf(
        "location" to ParameterSchema("string", "City name", true)
    )
) { params ->
    coroutineScope {
        try {
            val location = params["location"] as String
            
            // Simulated async API call
            delay(1000)
            val temperature = (15..30).random()
            val conditions = listOf("Sunny", "Cloudy", "Rainy").random()
            
            ToolResult.success(
                "Weather in $location: $temperature°C, $conditions",
                mapOf(
                    "temperature" to temperature,
                    "conditions" to conditions,
                    "location" to location
                )
            )
        } catch (e: Exception) {
            ToolResult.error("Failed to fetch weather: ${e.message}")
        }
    }
}
```

## Tool Registration

### Global Registration
```kotlin
// Register tools globally
ToolRegistry.register(wordCountTool)
ToolRegistry.register(weatherTool, namespace = "external")

// Retrieve tools
val tool = ToolRegistry.getTool("word-counter")
val weatherFromNamespace = ToolRegistry.getTool("weather", "external")

// List all tools
val allTools = ToolRegistry.list()
val externalTools = ToolRegistry.getByNamespace("external")
```

### Agent-Specific Tools
```kotlin
val agent = buildAgent {
    id = "tool-user"
    
    tools {
        // Add specific tool instance
        add(wordCountTool)
        
        // Reference global tools
        useGlobal("calculator", "datetime")
        
        // Inline tool definition
        tool("uppercase") { params ->
            val text = params["text"] as String
            ToolResult.success(text.uppercase())
        }
    }
    
    handle { comm ->
        val result = useTool("uppercase", mapOf("text" to comm.content))
        comm.reply(result.result, id)
    }
}
```

## Built-in Tools Reference

### Calculator Tool
```kotlin
val calculatorTool = calculatorTool()
// Parameters: expression (string)
// Example: "2 + 2 * 3"
// Returns: Calculated result as string
```

### Text Processor Tool
```kotlin
val textProcessor = textProcessorTool()
// Parameters: 
//   - text (string): Input text
//   - operation (string): uppercase|lowercase|reverse|wordcount|sentiment
// Returns: Processed text or analysis result
```

### DateTime Tool
```kotlin
val dateTime = dateTimeTool()
// Parameters:
//   - operation (string): current|now|date|time|timestamp|format
//   - format (string, optional): Custom date format
// Returns: Formatted date/time string
```

### Random Tool
```kotlin
val random = randomTool()
// Parameters:
//   - type (string): number|string|uuid|choice
//   - min/max (number): For number type
//   - length (number): For string type
//   - choices (string): Comma-separated choices
// Returns: Random value based on type
```

## Advanced Tool Patterns

### 1. Composable Tools
```kotlin
class CompositeAnalysisTool : Tool {
    private val textProcessor = textProcessorTool()
    private val calculator = calculatorTool()
    
    override val name = "analyze"
    override val description = "Comprehensive text analysis"
    
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val text = params["text"] as String
        
        // Use multiple tools
        val wordCount = textProcessor.execute(mapOf(
            "text" to text,
            "operation" to "wordcount"
        ))
        
        val sentiment = textProcessor.execute(mapOf(
            "text" to text,
            "operation" to "sentiment"
        ))
        
        // Calculate reading time (words / 200 wpm)
        val wordsMatch = wordCount.result.split(":").last().trim()
        val readingTime = calculator.execute(mapOf(
            "expression" to "$wordsMatch / 200"
        ))
        
        return ToolResult.success(
            """
            Analysis Complete:
            - ${wordCount.result}
            - Sentiment: ${sentiment.result}
            - Reading time: ${readingTime.result} minutes
            """.trimIndent()
        )
    }
}
```

### 2. Cached Tool Results
```kotlin
class CachedWeatherTool : Tool {
    private val cache = mutableMapOf<String, Pair<Instant, ToolResult>>()
    private val cacheDuration = Duration.ofMinutes(5)
    
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val location = params["location"] as String
        
        // Check cache
        val cached = cache[location]
        if (cached != null && cached.first.plus(cacheDuration).isAfter(Instant.now())) {
            return cached.second.copy(
                metadata = cached.second.metadata + ("cached" to true)
            )
        }
        
        // Fetch fresh data
        val result = fetchWeather(location)
        cache[location] = Instant.now() to result
        return result
    }
}
```

### 3. Tool with Validation
```kotlin
val emailSenderTool = SimpleTool(
    name = "email-sender",
    description = "Sends emails",
    parameterSchemas = mapOf(
        "to" to ParameterSchema("string", "Recipient email", true),
        "subject" to ParameterSchema("string", "Email subject", true),
        "body" to ParameterSchema("string", "Email body", true)
    )
) { params ->
    // Validate parameters
    val to = params["to"] as String
    val subject = params["subject"] as String
    val body = params["body"] as String
    
    if (!isValidEmail(to)) {
        return@SimpleTool ToolResult.error("Invalid email address: $to")
    }
    
    if (subject.isBlank()) {
        return@SimpleTool ToolResult.error("Subject cannot be empty")
    }
    
    if (body.length > 10000) {
        return@SimpleTool ToolResult.error("Body too long (max 10000 chars)")
    }
    
    // Send email
    try {
        sendEmail(to, subject, body)
        ToolResult.success("Email sent successfully to $to")
    } catch (e: Exception) {
        ToolResult.error("Failed to send email: ${e.message}")
    }
}
```

## Testing Tools

### Unit Testing
```kotlin
class ToolTest {
    @Test
    fun `test word counter tool`() = runBlocking {
        val tool = wordCountTool
        
        val result = tool.execute(mapOf(
            "text" to "Hello world from Spice"
        ))
        
        assertTrue(result.success)
        assertTrue(result.result.contains("4"))
        assertEquals(4, result.metadata["count"])
    }
    
    @Test
    fun `test tool error handling`() = runBlocking {
        val tool = SimpleTool("test", "Test", emptyMap()) { 
            throw RuntimeException("Test error")
        }
        
        val result = tool.execute(emptyMap())
        
        assertFalse(result.success)
        assertNotNull(result.error)
        assertTrue(result.error!!.contains("Test error"))
    }
}
```

## Best Practices

1. **Always validate input parameters**
2. **Return meaningful error messages**
3. **Include useful metadata in results**
4. **Handle exceptions gracefully**
5. **Document parameter schemas clearly**
6. **Use appropriate types in ParameterSchema**
7. **Cache expensive operations when appropriate**
8. **Write comprehensive tests for tools**
9. **Keep tools focused on single responsibility**
10. **Make tools reusable across agents**

## Next: [Flow Orchestration](flow-orchestration.md)