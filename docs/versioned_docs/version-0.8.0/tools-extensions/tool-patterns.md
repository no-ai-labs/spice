# Advanced Tool Patterns

Master advanced patterns for building production-ready tools.

## Overview

This guide covers advanced patterns for creating robust, scalable, and maintainable tools in Spice Framework. These patterns go beyond basic tool creation to address real-world challenges like state management, composition, and production deployment.

**Prerequisites**: Familiarity with [Creating Custom Tools](./creating-tools) and [Tools DSL](../dsl-guide/tools).

---

## 1. Stateful Tools

Tools that maintain state across invocations, such as sessions, caches, or connections.

### Pattern: Session-Based Tool

Tools that need to maintain context across multiple calls:

```kotlin
class SessionTool : Tool {
    override val name = "session_tool"
    override val description = "Manages user sessions"
    override val schema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "session_id" to ParameterSchema("string", "Session ID", required = true),
            "action" to ParameterSchema("string", "Action (get/set/delete)", required = true),
            "data" to ParameterSchema("object", "Session data", required = false)
        )
    )

    // Thread-safe session storage
    private val sessions = ConcurrentHashMap<String, MutableMap<String, Any>>()

    override suspend fun execute(parameters: Map<String, Any>): SpiceResult<ToolResult> {
        return try {
            val sessionId = parameters["session_id"] as String
            val action = parameters["action"] as String

            val result = when (action) {
                "get" -> getSession(sessionId)
                "set" -> {
                    val data = parameters["data"] as? Map<*, *> ?: emptyMap<String, Any>()
                    setSession(sessionId, data as Map<String, Any>)
                }
                "delete" -> deleteSession(sessionId)
                else -> throw IllegalArgumentException("Unknown action: $action")
            }

            SpiceResult.success(ToolResult.success(result))
        } catch (e: Exception) {
            SpiceResult.success(ToolResult.error(e.message ?: "Session operation failed"))
        }
    }

    private fun getSession(sessionId: String): String {
        val session = sessions[sessionId] ?: emptyMap()
        return Json.encodeToString(session)
    }

    private fun setSession(sessionId: String, data: Map<String, Any>): String {
        sessions.computeIfAbsent(sessionId) { ConcurrentHashMap() }
            .putAll(data)
        return "Session updated"
    }

    private fun deleteSession(sessionId: String): String {
        sessions.remove(sessionId)
        return "Session deleted"
    }
}
```

**Best Practices**:
- ✅ Use `ConcurrentHashMap` for thread safety
- ✅ Implement cleanup mechanisms (TTL, LRU)
- ✅ Consider using external state stores (Redis, etc.) for production
- ✅ Add session expiration

### Pattern: Cached Computation Tool

Tools that cache expensive computations:

```kotlin
class CachedComputationTool : Tool {
    override val name = "cached_compute"
    override val description = "Performs expensive computation with caching"
    override val schema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "input" to ParameterSchema("string", "Input data", required = true),
            "bypass_cache" to ParameterSchema("boolean", "Bypass cache", required = false)
        )
    )

    data class CacheEntry(
        val result: String,
        val timestamp: Long,
        val ttlMs: Long = 3600000 // 1 hour
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > ttlMs
    }

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    override suspend fun execute(parameters: Map<String, Any>): SpiceResult<ToolResult> {
        return try {
            val input = parameters["input"] as String
            val bypassCache = parameters["bypass_cache"] as? Boolean ?: false

            // Check cache
            if (!bypassCache) {
                val cached = cache[input]
                if (cached != null && !cached.isExpired()) {
                    return SpiceResult.success(ToolResult.success(
                        "CACHED: ${cached.result}"
                    ))
                }
            }

            // Perform expensive computation
            val result = performExpensiveComputation(input)

            // Store in cache
            cache[input] = CacheEntry(
                result = result,
                timestamp = System.currentTimeMillis()
            )

            // Cleanup expired entries
            cleanupExpiredEntries()

            SpiceResult.success(ToolResult.success(result))
        } catch (e: Exception) {
            SpiceResult.success(ToolResult.error(e.message ?: "Computation failed"))
        }
    }

    private suspend fun performExpensiveComputation(input: String): String {
        // Simulate expensive operation
        delay(1000)
        return "Computed result for: $input"
    }

    private fun cleanupExpiredEntries() {
        cache.entries.removeIf { it.value.isExpired() }
    }
}
```

**Key Features**:
- Cache with TTL
- Bypass mechanism
- Automatic cleanup
- Thread-safe operations

---

## 2. Tool Composition

Combine multiple tools to create more powerful composite tools.

### Pattern: Sequential Tool Chain

Execute tools in sequence, passing results forward:

```kotlin
class ToolChain(
    private val tools: List<Tool>,
    override val name: String = "tool_chain",
    override val description: String = "Executes tools in sequence"
) : Tool {

    override val schema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "input" to ParameterSchema("object", "Initial input", required = true)
        )
    )

    override suspend fun execute(parameters: Map<String, Any>): SpiceResult<ToolResult> {
        return try {
            var currentInput = parameters["input"] as Map<String, Any>
            val results = mutableListOf<String>()

            for ((index, tool) in tools.withIndex()) {
                println("Executing tool ${index + 1}/${tools.size}: ${tool.name}")

                val result = tool.execute(currentInput)

                when (result) {
                    is SpiceResult.Success -> {
                        val toolResult = result.value
                        if (!toolResult.success) {
                            return SpiceResult.success(ToolResult.error(
                                "Tool ${tool.name} failed: ${toolResult.error}"
                            ))
                        }

                        results.add(toolResult.result ?: "")

                        // Prepare input for next tool
                        currentInput = mapOf(
                            "previous_result" to (toolResult.result ?: ""),
                            "all_results" to results
                        )
                    }
                    is SpiceResult.Failure -> {
                        return SpiceResult.failure(result.error)
                    }
                }
            }

            SpiceResult.success(ToolResult.success(
                "Chain completed: ${results.joinToString(" → ")}"
            ))
        } catch (e: Exception) {
            SpiceResult.success(ToolResult.error(
                "Chain execution failed: ${e.message}"
            ))
        }
    }
}

// Usage
val chain = ToolChain(
    tools = listOf(
        DataValidatorTool(),
        DataTransformerTool(),
        DataPersistenceTool()
    ),
    name = "data_processing_chain"
)
```

### Pattern: Parallel Tool Aggregation

Execute multiple tools in parallel and aggregate results:

```kotlin
class ParallelToolAggregator(
    private val tools: List<Tool>,
    override val name: String = "parallel_aggregator",
    override val description: String = "Executes tools in parallel"
) : Tool {

    override val schema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "input" to ParameterSchema("object", "Input for all tools", required = true)
        )
    )

    override suspend fun execute(parameters: Map<String, Any>): SpiceResult<ToolResult> {
        return try {
            val input = parameters["input"] as Map<String, Any>

            // Execute all tools in parallel
            val results = coroutineScope {
                tools.map { tool ->
                    async {
                        tool.name to tool.execute(input)
                    }
                }.awaitAll()
            }

            // Aggregate results
            val aggregated = results.associate { (name, result) ->
                name to when (result) {
                    is SpiceResult.Success -> result.value.result ?: "error"
                    is SpiceResult.Failure -> "failed: ${result.error.message}"
                }
            }

            SpiceResult.success(ToolResult.success(
                Json.encodeToString(aggregated)
            ))
        } catch (e: Exception) {
            SpiceResult.success(ToolResult.error(
                "Parallel execution failed: ${e.message}"
            ))
        }
    }
}
```

**Benefits**:
- Faster execution through parallelism
- Independent tool failures don't block others
- Flexible aggregation strategies

---

## 3. Async Tools with Context

Handle asynchronous operations while propagating context.

### Pattern: Context-Aware Async Tool

Propagate tracing and tenant context through async operations:

```kotlin
class ContextAwareAsyncTool : Tool {
    override val name = "async_api_call"
    override val description = "Makes async API calls with context"
    override val schema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "url" to ParameterSchema("string", "API URL", required = true),
            "method" to ParameterSchema("string", "HTTP method", required = true)
        )
    )

    override suspend fun execute(parameters: Map<String, Any>): SpiceResult<ToolResult> {
        return withContext(Dispatchers.IO) {
            // Capture current context
            val tenantId = TenantContext.current()
            val traceId = SpiceTracer.currentSpan()?.context?.traceId

            try {
                val url = parameters["url"] as String
                val method = parameters["method"] as String

                // Create span for API call
                SpiceTracer.traced("api_call") { span ->
                    span.setAttribute("http.url", url)
                    span.setAttribute("http.method", method)
                    span.setAttribute("tenant.id", tenantId ?: "default")

                    // Make API call with context propagation
                    val result = makeApiCall(url, method, traceId)

                    span.setAttribute("http.status", result.statusCode)
                    SpiceMetrics.recordToolExecution(name, result.duration, result.success)

                    if (result.success) {
                        SpiceResult.success(ToolResult.success(result.body))
                    } else {
                        SpiceResult.success(ToolResult.error(
                            "API call failed: ${result.error}"
                        ))
                    }
                }
            } catch (e: Exception) {
                SpiceTracer.currentSpan()?.recordException(e)
                SpiceResult.success(ToolResult.error(e.message ?: "Unknown error"))
            }
        }
    }

    private suspend fun makeApiCall(
        url: String,
        method: String,
        traceId: String?
    ): ApiResult {
        // Implementation with context headers
        val headers = mutableMapOf<String, String>()
        if (traceId != null) {
            headers["X-Trace-Id"] = traceId
        }

        // Make actual HTTP call
        // ...

        return ApiResult(
            statusCode = 200,
            body = "Response",
            duration = 100,
            success = true
        )
    }

    data class ApiResult(
        val statusCode: Int,
        val body: String,
        val duration: Long,
        val success: Boolean,
        val error: String? = null
    )
}
```

**Key Points**:
- Context propagation through async boundaries
- Distributed tracing integration
- Metrics collection
- Error tracking

---

## 4. Tool Versioning

Manage tool versions and handle deprecation gracefully.

### Pattern: Versioned Tool

Support multiple tool versions simultaneously:

```kotlin
interface VersionedTool : Tool {
    val version: String

    companion object {
        const val V1 = "1.0.0"
        const val V2 = "2.0.0"
    }
}

class DataProcessorV1 : VersionedTool {
    override val name = "data_processor"
    override val version = VersionedTool.V1
    override val description = "Data processor v1 (DEPRECATED)"

    override val schema = ToolSchema(
        name = "${name}_v1",
        description = description,
        parameters = mapOf(
            "data" to ParameterSchema("string", "Data to process", required = true)
        ),
        metadata = mapOf(
            "version" to version,
            "deprecated" to true,
            "deprecation_notice" to "Use v2 for better performance",
            "sunset_date" to "2025-12-31"
        )
    )

    override suspend fun execute(parameters: Map<String, Any>): SpiceResult<ToolResult> {
        // Log deprecation warning
        println("WARNING: data_processor v1 is deprecated. Migrate to v2 by 2025-12-31")

        return try {
            val data = parameters["data"] as String
            val result = processDataV1(data)

            SpiceResult.success(ToolResult.success(result))
        } catch (e: Exception) {
            SpiceResult.success(ToolResult.error(e.message ?: "Processing failed"))
        }
    }

    private fun processDataV1(data: String): String {
        // Old implementation
        return data.uppercase()
    }
}

class DataProcessorV2 : VersionedTool {
    override val name = "data_processor"
    override val version = VersionedTool.V2
    override val description = "Data processor v2 (CURRENT)"

    override val schema = ToolSchema(
        name = "${name}_v2",
        description = description,
        parameters = mapOf(
            "data" to ParameterSchema("string", "Data to process", required = true),
            "format" to ParameterSchema("string", "Output format", required = false)
        ),
        metadata = mapOf(
            "version" to version,
            "recommended" to true
        )
    )

    override suspend fun execute(parameters: Map<String, Any>): SpiceResult<ToolResult> {
        return try {
            val data = parameters["data"] as String
            val format = parameters["format"] as? String ?: "json"

            val result = processDataV2(data, format)

            SpiceResult.success(ToolResult.success(result))
        } catch (e: Exception) {
            SpiceResult.success(ToolResult.error(e.message ?: "Processing failed"))
        }
    }

    private fun processDataV2(data: String, format: String): String {
        // New implementation with format support
        return when (format) {
            "json" -> """{"data": "${data.uppercase()}"}"""
            "xml" -> "<data>${data.uppercase()}</data>"
            else -> data.uppercase()
        }
    }
}
```

**Version Management Strategy**:
1. Add version to schema metadata
2. Support multiple versions simultaneously
3. Log deprecation warnings
4. Provide migration path
5. Set sunset dates

---

## 5. Tool Testing Strategies

Comprehensive testing approaches for tools.

### Unit Testing

Test individual tool logic:

```kotlin
class CalculatorToolTest {
    private lateinit var tool: CalculatorTool

    @BeforeEach
    fun setup() {
        tool = CalculatorTool()
    }

    @Test
    fun `should add two numbers`() = runTest {
        // Given
        val params = mapOf(
            "a" to 10,
            "b" to 5,
            "operation" to "+"
        )

        // When
        val result = tool.execute(params)

        // Then
        assertTrue(result.isSuccess)
        val toolResult = (result as SpiceResult.Success).value
        assertTrue(toolResult.success)
        assertEquals("15.0", toolResult.result)
    }

    @Test
    fun `should handle division by zero`() = runTest {
        // Given
        val params = mapOf(
            "a" to 10,
            "b" to 0,
            "operation" to "/"
        )

        // When
        val result = tool.execute(params)

        // Then
        assertTrue(result.isSuccess)
        val toolResult = (result as SpiceResult.Success).value
        assertFalse(toolResult.success)
        assertTrue(toolResult.error.contains("division by zero"))
    }

    @Test
    fun `should validate required parameters`() = runTest {
        // Given
        val params = mapOf("a" to 10) // Missing 'b' and 'operation'

        // When
        val result = tool.execute(params)

        // Then
        assertTrue(result.isSuccess)
        val toolResult = (result as SpiceResult.Success).value
        assertFalse(toolResult.success)
        assertTrue(toolResult.error.contains("Missing required parameter"))
    }
}
```

### Integration Testing

Test tool interactions with external systems:

```kotlin
class DatabaseToolIntegrationTest {
    private lateinit var database: TestDatabase
    private lateinit var tool: DatabaseTool

    @BeforeEach
    fun setup() {
        database = TestDatabase.create()
        tool = DatabaseTool(database)
    }

    @AfterEach
    fun teardown() {
        database.cleanup()
    }

    @Test
    fun `should perform CRUD operations`() = runTest {
        // Create
        val createResult = tool.execute(mapOf(
            "operation" to "create",
            "table" to "users",
            "data" to mapOf("name" to "Alice", "email" to "alice@example.com")
        ))

        assertTrue((createResult as SpiceResult.Success).value.success)
        val userId = createResult.value.result

        // Read
        val readResult = tool.execute(mapOf(
            "operation" to "read",
            "table" to "users",
            "id" to userId
        ))

        assertTrue((readResult as SpiceResult.Success).value.success)
        assertTrue(readResult.value.result!!.contains("Alice"))

        // Update
        val updateResult = tool.execute(mapOf(
            "operation" to "update",
            "table" to "users",
            "id" to userId,
            "data" to mapOf("name" to "Alice Updated")
        ))

        assertTrue((updateResult as SpiceResult.Success).value.success)

        // Delete
        val deleteResult = tool.execute(mapOf(
            "operation" to "delete",
            "table" to "users",
            "id" to userId
        ))

        assertTrue((deleteResult as SpiceResult.Success).value.success)
    }
}
```

### Property-Based Testing

Test tool properties with random inputs:

```kotlin
class ToolPropertyTest {
    @Test
    fun `calculator should be commutative for addition`() = runTest {
        val tool = CalculatorTool()

        checkAll(Arb.int(), Arb.int()) { a, b ->
            val result1 = tool.execute(mapOf(
                "a" to a,
                "b" to b,
                "operation" to "+"
            ))

            val result2 = tool.execute(mapOf(
                "a" to b,
                "b" to a,
                "operation" to "+"
            ))

            val value1 = (result1 as SpiceResult.Success).value.result
            val value2 = (result2 as SpiceResult.Success).value.result

            assertEquals(value1, value2, "Addition should be commutative")
        }
    }
}
```

---

## 6. Production Deployment

Prepare tools for production with monitoring and error handling.

### Pattern: Production-Ready Tool

```kotlin
class ProductionTool : Tool {
    override val name = "production_tool"
    override val description = "Production-ready tool with full observability"
    override val schema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "input" to ParameterSchema("string", "Input data", required = true)
        )
    )

    private val logger = LoggerFactory.getLogger(javaClass)
    private val executionCounter = AtomicLong(0)
    private val errorCounter = AtomicLong(0)

    override suspend fun execute(parameters: Map<String, Any>): SpiceResult<ToolResult> {
        val executionId = executionCounter.incrementAndGet()
        val startTime = System.currentTimeMillis()

        // Structured logging
        logger.info(
            "Tool execution started",
            mapOf(
                "tool" to name,
                "execution_id" to executionId,
                "parameters" to parameters
            )
        )

        return try {
            // Create span for tracing
            SpiceTracer.traced("tool_execution") { span ->
                span.setAttribute("tool.name", name)
                span.setAttribute("execution.id", executionId.toString())

                // Validate input
                val validationResult = validateInput(parameters)
                if (!validationResult.valid) {
                    span.setAttribute("validation.failed", true)
                    throw IllegalArgumentException(validationResult.error)
                }

                // Execute business logic
                val result = performOperation(parameters)

                // Record metrics
                val duration = System.currentTimeMillis() - startTime
                SpiceMetrics.recordToolExecution(name, duration, success = true)

                // Structured logging
                logger.info(
                    "Tool execution completed",
                    mapOf(
                        "tool" to name,
                        "execution_id" to executionId,
                        "duration_ms" to duration,
                        "success" to true
                    )
                )

                SpiceResult.success(ToolResult.success(result))
            }
        } catch (e: Exception) {
            errorCounter.incrementAndGet()
            val duration = System.currentTimeMillis() - startTime

            // Record error metrics
            SpiceMetrics.recordToolExecution(name, duration, success = false)
            SpiceMetrics.recordError(name, e)

            // Trace exception
            SpiceTracer.currentSpan()?.recordException(e)

            // Structured error logging
            logger.error(
                "Tool execution failed",
                mapOf(
                    "tool" to name,
                    "execution_id" to executionId,
                    "duration_ms" to duration,
                    "error_type" to e::class.simpleName,
                    "error_message" to e.message
                ),
                e
            )

            SpiceResult.success(ToolResult.error(
                "Execution failed: ${e.message} (execution_id: $executionId)"
            ))
        }
    }

    private fun validateInput(parameters: Map<String, Any>): ValidationResult {
        val input = parameters["input"] as? String

        return when {
            input == null -> ValidationResult(false, "Missing required parameter: input")
            input.isBlank() -> ValidationResult(false, "Input cannot be blank")
            input.length > 1000 -> ValidationResult(false, "Input too long (max 1000 chars)")
            else -> ValidationResult(true)
        }
    }

    private suspend fun performOperation(parameters: Map<String, Any>): String {
        val input = parameters["input"] as String

        // Simulate operation
        delay(100)

        return "Processed: $input"
    }

    data class ValidationResult(
        val valid: Boolean,
        val error: String? = null
    )

    fun getMetrics(): Map<String, Any> {
        return mapOf(
            "total_executions" to executionCounter.get(),
            "total_errors" to errorCounter.get(),
            "error_rate" to if (executionCounter.get() > 0) {
                errorCounter.get().toDouble() / executionCounter.get()
            } else 0.0
        )
    }
}
```

**Production Checklist**:
- ✅ Structured logging
- ✅ Distributed tracing
- ✅ Metrics collection
- ✅ Error tracking
- ✅ Input validation
- ✅ Execution IDs for debugging
- ✅ Performance monitoring

---

## 7. Real-World Examples

### Example 1: Database Transaction Tool

Complete database tool with transaction management:

```kotlin
class DatabaseTransactionTool(
    private val dataSource: DataSource
) : Tool {
    override val name = "db_transaction"
    override val description = "Executes database operations in a transaction"
    override val schema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "operations" to ParameterSchema("array", "List of SQL operations", required = true)
        )
    )

    override suspend fun execute(parameters: Map<String, Any>): SpiceResult<ToolResult> {
        return withContext(Dispatchers.IO) {
            val connection = dataSource.connection

            try {
                connection.autoCommit = false

                val operations = parameters["operations"] as List<*>
                val results = mutableListOf<String>()

                for ((index, op) in operations.withIndex()) {
                    val operation = op as Map<*, *>
                    val sql = operation["sql"] as String
                    val params = operation["params"] as? List<*> ?: emptyList<Any>()

                    val statement = connection.prepareStatement(sql)
                    params.forEachIndexed { i, param ->
                        statement.setObject(i + 1, param)
                    }

                    val affected = statement.executeUpdate()
                    results.add("Operation ${index + 1}: $affected rows affected")
                }

                connection.commit()

                SpiceResult.success(ToolResult.success(
                    "Transaction completed: ${results.joinToString(", ")}"
                ))
            } catch (e: Exception) {
                connection.rollback()
                SpiceResult.success(ToolResult.error(
                    "Transaction rolled back: ${e.message}"
                ))
            } finally {
                connection.close()
            }
        }
    }
}
```

### Example 2: Multi-Step API Call Tool

Orchestrate multiple API calls with error handling:

```kotlin
class MultiStepApiTool : Tool {
    override val name = "multi_step_api"
    override val description = "Orchestrates multiple API calls"
    override val schema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "workflow" to ParameterSchema("object", "API workflow definition", required = true)
        )
    )

    private val httpClient = HttpClient {
        install(ContentNegotiation) { json() }
        install(HttpTimeout) { requestTimeoutMillis = 30000 }
    }

    override suspend fun execute(parameters: Map<String, Any>): SpiceResult<ToolResult> {
        return try {
            val workflow = parameters["workflow"] as Map<*, *>
            val steps = workflow["steps"] as List<*>

            val context = mutableMapOf<String, Any>()

            for ((index, step) in steps.withIndex()) {
                val stepMap = step as Map<*, *>
                val result = executeStep(stepMap, context)

                if (!result.success) {
                    return SpiceResult.success(ToolResult.error(
                        "Step ${index + 1} failed: ${result.error}"
                    ))
                }

                // Store result for next steps
                context["step_${index + 1}_result"] = result.data ?: ""
            }

            SpiceResult.success(ToolResult.success(
                Json.encodeToString(context)
            ))
        } catch (e: Exception) {
            SpiceResult.success(ToolResult.error(
                "Workflow failed: ${e.message}"
            ))
        }
    }

    private suspend fun executeStep(
        step: Map<*, *>,
        context: Map<String, Any>
    ): StepResult {
        val url = interpolate(step["url"] as String, context)
        val method = step["method"] as String
        val body = step["body"] as? Map<*, *>

        return try {
            val response = when (method.uppercase()) {
                "GET" -> httpClient.get(url)
                "POST" -> httpClient.post(url) {
                    setBody(body)
                }
                else -> throw IllegalArgumentException("Unsupported method: $method")
            }

            StepResult(
                success = response.status.value in 200..299,
                data = response.body<String>()
            )
        } catch (e: Exception) {
            StepResult(success = false, error = e.message)
        }
    }

    private fun interpolate(template: String, context: Map<String, Any>): String {
        var result = template
        context.forEach { (key, value) ->
            result = result.replace("{{$key}}", value.toString())
        }
        return result
    }

    data class StepResult(
        val success: Boolean,
        val data: String? = null,
        val error: String? = null
    )
}
```

### Example 3: State Machine Tool

Implement complex workflows with state transitions:

```kotlin
class StateMachineTool : Tool {
    override val name = "state_machine"
    override val description = "Manages state transitions"
    override val schema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "state_id" to ParameterSchema("string", "State machine ID", required = true),
            "event" to ParameterSchema("string", "Event to process", required = true),
            "data" to ParameterSchema("object", "Event data", required = false)
        )
    )

    private val machines = ConcurrentHashMap<String, StateMachine>()

    override suspend fun execute(parameters: Map<String, Any>): SpiceResult<ToolResult> {
        return try {
            val stateId = parameters["state_id"] as String
            val event = parameters["event"] as String
            val data = parameters["data"] as? Map<*, *> ?: emptyMap<String, Any>()

            val machine = machines.getOrPut(stateId) { createStateMachine() }
            val result = machine.process(event, data as Map<String, Any>)

            SpiceResult.success(ToolResult.success(
                """
                State: ${machine.currentState}
                Result: $result
                History: ${machine.history.joinToString(" → ")}
                """.trimIndent()
            ))
        } catch (e: Exception) {
            SpiceResult.success(ToolResult.error(e.message ?: "State transition failed"))
        }
    }

    private fun createStateMachine(): StateMachine {
        return StateMachine(
            initialState = "idle",
            transitions = mapOf(
                "idle" to mapOf(
                    "start" to Transition("processing") { println("Starting...") }
                ),
                "processing" to mapOf(
                    "complete" to Transition("completed") { println("Completed!") },
                    "error" to Transition("failed") { println("Failed!") }
                ),
                "completed" to mapOf(
                    "reset" to Transition("idle") { println("Resetting...") }
                ),
                "failed" to mapOf(
                    "retry" to Transition("processing") { println("Retrying...") },
                    "reset" to Transition("idle") { println("Resetting...") }
                )
            )
        )
    }

    data class Transition(
        val nextState: String,
        val action: () -> Unit = {}
    )

    class StateMachine(
        initialState: String,
        private val transitions: Map<String, Map<String, Transition>>
    ) {
        var currentState: String = initialState
            private set

        val history = mutableListOf(initialState)

        fun process(event: String, data: Map<String, Any>): String {
            val stateTransitions = transitions[currentState]
                ?: throw IllegalStateException("Invalid state: $currentState")

            val transition = stateTransitions[event]
                ?: throw IllegalArgumentException(
                    "No transition for event '$event' in state '$currentState'"
                )

            // Execute transition action
            transition.action()

            // Update state
            currentState = transition.nextState
            history.add(currentState)

            return "Transitioned to $currentState"
        }
    }
}
```

### Example 4: Cached Computation Tool

Advanced caching with metrics:

```kotlin
class SmartCacheTool : Tool {
    override val name = "smart_cache"
    override val description = "Intelligent caching with adaptive TTL"
    override val schema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "key" to ParameterSchema("string", "Cache key", required = true),
            "computation" to ParameterSchema("string", "Computation type", required = true),
            "params" to ParameterSchema("object", "Computation parameters", required = false)
        )
    )

    data class CacheEntry(
        val value: String,
        val createdAt: Long,
        val lastAccessedAt: Long,
        val accessCount: Int,
        val computationTimeMs: Long
    ) {
        fun withAccess(): CacheEntry = copy(
            lastAccessedAt = System.currentTimeMillis(),
            accessCount = accessCount + 1
        )

        fun calculateTTL(): Long {
            // Adaptive TTL based on access patterns and computation time
            val baseT TL = 3600000L // 1 hour
            val accessBonus = minOf(accessCount * 300000L, 7200000L) // Max 2 hours bonus
            val computeBonus = minOf(computationTimeMs * 10, 3600000L) // Max 1 hour bonus

            return baseTTL + accessBonus + computeBonus
        }

        fun isExpired(): Boolean {
            val ttl = calculateTTL()
            return System.currentTimeMillis() - createdAt > ttl
        }
    }

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private var hits = AtomicLong(0)
    private var misses = AtomicLong(0)

    override suspend fun execute(parameters: Map<String, Any>): SpiceResult<ToolResult> {
        return try {
            val key = parameters["key"] as String
            val computationType = parameters["computation"] as String
            val params = parameters["params"] as? Map<*, *> ?: emptyMap<String, Any>()

            // Check cache
            val cached = cache[key]
            if (cached != null && !cached.isExpired()) {
                hits.incrementAndGet()
                cache[key] = cached.withAccess()

                return SpiceResult.success(ToolResult.success(
                    """
                    CACHE HIT
                    Value: ${cached.value}
                    Age: ${(System.currentTimeMillis() - cached.createdAt) / 1000}s
                    Accesses: ${cached.accessCount + 1}
                    TTL: ${cached.calculateTTL() / 1000}s
                    Hit Rate: ${getHitRate()}%
                    """.trimIndent()
                ))
            }

            // Cache miss - compute
            misses.incrementAndGet()
            val startTime = System.currentTimeMillis()
            val result = performComputation(computationType, params as Map<String, Any>)
            val computeTime = System.currentTimeMillis() - startTime

            // Store with metadata
            cache[key] = CacheEntry(
                value = result,
                createdAt = System.currentTimeMillis(),
                lastAccessedAt = System.currentTimeMillis(),
                accessCount = 1,
                computationTimeMs = computeTime
            )

            // Cleanup old entries
            cleanupCache()

            SpiceResult.success(ToolResult.success(
                """
                CACHE MISS
                Value: $result
                Compute Time: ${computeTime}ms
                Hit Rate: ${getHitRate()}%
                """.trimIndent()
            ))
        } catch (e: Exception) {
            SpiceResult.success(ToolResult.error(e.message ?: "Cache operation failed"))
        }
    }

    private suspend fun performComputation(type: String, params: Map<String, Any>): String {
        // Simulate expensive computation
        delay(1000)
        return "Result of $type with $params"
    }

    private fun cleanupCache() {
        cache.entries.removeIf { it.value.isExpired() }

        // If still too large, remove least accessed
        if (cache.size > 1000) {
            val sorted = cache.entries.sortedBy { it.value.accessCount }
            sorted.take(cache.size - 1000).forEach {
                cache.remove(it.key)
            }
        }
    }

    private fun getHitRate(): Double {
        val total = hits.get() + misses.get()
        return if (total > 0) {
            (hits.get().toDouble() / total * 100)
        } else 0.0
    }

    fun getCacheStats(): Map<String, Any> {
        return mapOf(
            "size" to cache.size,
            "hits" to hits.get(),
            "misses" to misses.get(),
            "hit_rate" to getHitRate(),
            "entries" to cache.map { (key, entry) ->
                mapOf(
                    "key" to key,
                    "age_seconds" to (System.currentTimeMillis() - entry.createdAt) / 1000,
                    "accesses" to entry.accessCount,
                    "ttl_seconds" to entry.calculateTTL() / 1000
                )
            }
        )
    }
}
```

---

## 7. OpenAI Function Calling Integration

**New in 0.8.2:** Convert Spice tools to OpenAI Function Calling specification.

### Pattern: Multi-Provider Tool Architecture

Build tools that work seamlessly across Spice, OpenAI, and other LLM providers:

```kotlin
// Define tools once
val tools = listOf(
    WebSearchTool(),
    DatabaseQueryTool(dataSource),
    EmailSenderTool(smtpConfig),
    CalculatorTool()
)

// Use with Spice agents (native)
val spiceAgent = buildAgent {
    name = "Spice Agent"
    tools {
        tools.forEach { tool(it) }
    }
}

// Use with OpenAI (via conversion)
val openAIFunctions = tools.map { it.toOpenAIFunctionSpec() }

val chatRequest = ChatCompletionRequest(
    model = ModelId("gpt-4"),
    messages = messages,
    functions = openAIFunctions
)
```

### Pattern: Unified Workflow Tool

Create tools optimized for both Spice agents and OpenAI function calling:

```kotlin
class WorkflowTool(
    private val spiceTool: Tool
) {
    // Original Spice tool
    val tool: Tool = spiceTool

    // OpenAI function spec
    val openAISpec: Map<String, Any> by lazy {
        spiceTool.toOpenAIFunctionSpec()
    }

    // Execute via Spice (local)
    suspend fun executeLocal(params: Map<String, Any>): SpiceResult<ToolResult> {
        return spiceTool.execute(params)
    }

    // Execute via OpenAI (remote)
    suspend fun executeWithOpenAI(
        openAI: OpenAI,
        userMessage: String,
        context: Map<String, Any> = emptyMap()
    ): String {
        val request = ChatCompletionRequest(
            model = ModelId("gpt-4"),
            messages = listOf(
                ChatMessage(
                    role = ChatRole.System,
                    content = "You are a helpful assistant with access to tools."
                ),
                ChatMessage(
                    role = ChatRole.User,
                    content = userMessage
                )
            ),
            functions = listOf(openAISpec),
            functionCall = "auto"
        )

        val response = openAI.chatCompletion(request)

        response.choices.firstOrNull()?.message?.functionCall?.let { functionCall ->
            if (functionCall.name == spiceTool.name) {
                val params = Json.decodeFromString<Map<String, Any>>(functionCall.arguments)

                // Execute locally
                val result = spiceTool.execute(params)

                result.fold(
                    onSuccess = { toolResult ->
                        if (toolResult.success) {
                            return toolResult.result
                        } else {
                            return "Error: ${toolResult.error}"
                        }
                    },
                    onFailure = { error ->
                        return "Error: ${error.message}"
                    }
                )
            }
        }

        return response.choices.firstOrNull()?.message?.content ?: "No response"
    }
}

// Usage
val databaseTool = DatabaseQueryTool(dataSource)
val workflowTool = WorkflowTool(databaseTool)

// Execute locally via Spice
val localResult = workflowTool.executeLocal(mapOf("query" to "SELECT * FROM users"))

// Execute remotely via OpenAI
val remoteResult = workflowTool.executeWithOpenAI(
    openAI = openAI,
    userMessage = "Show me all users in the database"
)
```

### Pattern: Adaptive Tool Router

Route tool execution between local Spice and remote OpenAI based on context:

```kotlin
class AdaptiveToolRouter(
    private val tools: List<Tool>,
    private val openAI: OpenAI
) {
    enum class ExecutionMode {
        LOCAL,      // Execute via Spice
        OPENAI,     // Execute via OpenAI function calling
        AUTO        // Decide automatically
    }

    suspend fun execute(
        userMessage: String,
        mode: ExecutionMode = ExecutionMode.AUTO,
        context: ToolContext? = null
    ): String {
        val selectedMode = when (mode) {
            ExecutionMode.AUTO -> selectMode(userMessage, context)
            else -> mode
        }

        return when (selectedMode) {
            ExecutionMode.LOCAL -> executeLocal(userMessage, context)
            ExecutionMode.OPENAI -> executeWithOpenAI(userMessage)
            ExecutionMode.AUTO -> error("Should not reach here")
        }
    }

    private fun selectMode(message: String, context: ToolContext?): ExecutionMode {
        // Decision logic:
        // - Use LOCAL for simple, deterministic tasks
        // - Use OPENAI for complex reasoning + tool use

        val isSimpleQuery = message.length < 50 &&
            (message.contains("calculate") ||
             message.contains("search") ||
             message.contains("query"))

        return if (isSimpleQuery) {
            ExecutionMode.LOCAL
        } else {
            ExecutionMode.OPENAI
        }
    }

    private suspend fun executeLocal(message: String, context: ToolContext?): String {
        // Parse message to extract tool + params (simple rule-based)
        // Execute via Spice
        // Return result

        // Simplified example
        val tool = tools.firstOrNull { message.contains(it.name) }
            ?: return "No matching tool found"

        val params = extractParameters(message, tool.schema)

        val result = if (context != null) {
            tool.execute(params, context)
        } else {
            tool.execute(params)
        }

        return result.fold(
            onSuccess = { it.result },
            onFailure = { "Error: ${it.message}" }
        )
    }

    private suspend fun executeWithOpenAI(message: String): String {
        val openAIFunctions = tools.map { it.toOpenAIFunctionSpec() }

        val request = ChatCompletionRequest(
            model = ModelId("gpt-4"),
            messages = listOf(
                ChatMessage(role = ChatRole.User, content = message)
            ),
            functions = openAIFunctions,
            functionCall = "auto"
        )

        val response = openAI.chatCompletion(request)

        // Handle function calls
        response.choices.firstOrNull()?.message?.functionCall?.let { functionCall ->
            val tool = tools.find { it.name == functionCall.name }

            if (tool != null) {
                val params = Json.decodeFromString<Map<String, Any>>(functionCall.arguments)
                val result = tool.execute(params)

                return result.fold(
                    onSuccess = { if (it.success) it.result else "Error: ${it.error}" },
                    onFailure = { "Error: ${it.message}" }
                )
            }
        }

        return response.choices.firstOrNull()?.message?.content ?: "No response"
    }

    private fun extractParameters(message: String, schema: ToolSchema): Map<String, Any> {
        // Simple rule-based parameter extraction
        // In production, use more sophisticated parsing
        return emptyMap()
    }
}

// Usage
val router = AdaptiveToolRouter(
    tools = listOf(
        WebSearchTool(),
        CalculatorTool(),
        DatabaseQueryTool(dataSource)
    ),
    openAI = openAI
)

// Auto mode: Router decides execution strategy
val result1 = router.execute(
    "Calculate 42 * 1337",
    mode = AdaptiveToolRouter.ExecutionMode.AUTO
)

// Force local execution
val result2 = router.execute(
    "Search for AI news",
    mode = AdaptiveToolRouter.ExecutionMode.LOCAL
)

// Force OpenAI execution with reasoning
val result3 = router.execute(
    "Analyze sales data and suggest optimization strategies",
    mode = AdaptiveToolRouter.ExecutionMode.OPENAI
)
```

### Pattern: Tool Spec Validation

Validate that your tools produce valid OpenAI function specs:

```kotlin
object ToolSpecValidator {
    fun validate(tool: Tool): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Validate name
        if (tool.name.isEmpty()) {
            errors.add("Tool name cannot be empty")
        }
        if (!tool.name.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
            warnings.add("Tool name should only contain alphanumeric, underscore, or hyphen")
        }

        // Validate description
        if (tool.description.isEmpty()) {
            errors.add("Tool description cannot be empty")
        }
        if (tool.description.length < 10) {
            warnings.add("Tool description is too short (< 10 chars). OpenAI models work better with detailed descriptions.")
        }

        // Validate parameters
        tool.schema.parameters.forEach { (paramName, paramSchema) ->
            if (paramName.isEmpty()) {
                errors.add("Parameter name cannot be empty")
            }
            if (paramSchema.description.isEmpty()) {
                warnings.add("Parameter '$paramName' has no description")
            }
            if (paramSchema.type !in listOf("string", "number", "boolean", "array", "object")) {
                errors.add("Parameter '$paramName' has invalid type: ${paramSchema.type}")
            }
        }

        // Validate OpenAI spec generation
        try {
            val spec = tool.toOpenAIFunctionSpec()

            // Check structure
            if (spec["name"] != tool.name) {
                errors.add("OpenAI spec name doesn't match tool name")
            }

            val parameters = spec["parameters"] as? Map<*, *>
            if (parameters == null) {
                errors.add("OpenAI spec missing 'parameters' field")
            } else {
                if (parameters["type"] != "object") {
                    errors.add("OpenAI spec 'parameters.type' must be 'object'")
                }
                if (!parameters.containsKey("properties")) {
                    errors.add("OpenAI spec missing 'parameters.properties'")
                }
            }

        } catch (e: Exception) {
            errors.add("Failed to generate OpenAI spec: ${e.message}")
        }

        return ValidationResult(
            valid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    data class ValidationResult(
        val valid: Boolean,
        val errors: List<String>,
        val warnings: List<String>
    )
}

// Usage
val tool = WebSearchTool()
val validation = ToolSpecValidator.validate(tool)

if (validation.valid) {
    println("✓ Tool is valid")
    if (validation.warnings.isNotEmpty()) {
        println("⚠ Warnings:")
        validation.warnings.forEach { println("  - $it") }
    }
} else {
    println("✗ Tool validation failed:")
    validation.errors.forEach { println("  - $it") }
}
```

### Pattern: Testing OpenAI Integration

Test your tools work correctly with OpenAI function calling:

```kotlin
class OpenAIToolIntegrationTest {
    private lateinit var mockOpenAI: OpenAI
    private lateinit var tools: List<Tool>

    @BeforeTest
    fun setup() {
        tools = listOf(
            SimpleTool(
                name = "get_weather",
                description = "Get weather for a city",
                parameterSchemas = mapOf(
                    "city" to ParameterSchema("string", "City name", required = true)
                )
            ) { params ->
                val city = params["city"] as String
                ToolResult.success("""{"city": "$city", "temp": 22, "condition": "sunny"}""")
            }
        )
    }

    @Test
    fun `test tool spec generation`() {
        val weatherTool = tools[0]
        val spec = weatherTool.toOpenAIFunctionSpec()

        // Validate spec structure
        assertEquals("get_weather", spec["name"])
        assertEquals("Get weather for a city", spec["description"])

        val parameters = spec["parameters"] as Map<*, *>
        assertEquals("object", parameters["type"])

        val properties = parameters["properties"] as Map<*, *>
        assertTrue(properties.containsKey("city"))

        val required = parameters["required"] as List<*>
        assertEquals(listOf("city"), required)
    }

    @Test
    fun `test local execution matches spec`() = runTest {
        val weatherTool = tools[0]

        // Verify tool executes correctly
        val result = weatherTool.execute(mapOf("city" to "Seoul"))

        assertTrue(result.isSuccess)
        result.fold(
            onSuccess = { toolResult ->
                assertTrue(toolResult.success)
                assertTrue(toolResult.result.contains("Seoul"))
                assertTrue(toolResult.result.contains("temp"))
            },
            onFailure = { fail("Should not fail") }
        )
    }

    @Test
    fun `test parameter validation`() {
        val weatherTool = tools[0]

        // Missing required parameter
        val validation = weatherTool.validateParameters(emptyMap())

        assertFalse(validation.valid)
        assertTrue(validation.errors.any { it.contains("city") })
    }

    @Test
    fun `test spec validator`() {
        val weatherTool = tools[0]
        val validation = ToolSpecValidator.validate(weatherTool)

        assertTrue(validation.valid)
        assertEquals(0, validation.errors.size)
    }
}
```

### Best Practices for OpenAI Integration

**1. Descriptive Schemas**

OpenAI models use function descriptions to decide when to call functions. Make them clear and detailed:

```kotlin
// ✅ Good
ToolSchema(
    name = "search_customer_database",
    description = "Search the customer database by email, phone, or customer ID. Returns full customer record including contact details, purchase history, and account status.",
    parameters = mapOf(
        "search_term" to ParameterSchema(
            "string",
            "Customer email address, phone number (format: +1-555-555-5555), or customer ID (format: CUST-12345)",
            required = true
        )
    )
)

// ❌ Bad
ToolSchema(
    name = "search",
    description = "Search",
    parameters = mapOf(
        "q" to ParameterSchema("string", "Query", required = true)
    )
)
```

**2. Required vs Optional Parameters**

Only mark parameters as required if they're truly mandatory:

```kotlin
// ✅ Good
tool("send_notification", "Send notification to user") {
    parameter("user_id", "string", "User ID", required = true)
    parameter("message", "string", "Notification message", required = true)
    parameter("channel", "string", "Notification channel (email, sms, push)", required = false) // Defaults to email
    parameter("priority", "string", "Priority level (low, medium, high)", required = false) // Defaults to medium
}
```

**3. Test Both Execution Paths**

Test both Spice local execution and OpenAI remote execution:

```kotlin
@Test
fun `test dual execution`() = runTest {
    val tool = MyTool()

    // Test local Spice execution
    val localResult = tool.execute(params)
    assertTrue(localResult.isSuccess)

    // Test OpenAI spec generation
    val spec = tool.toOpenAIFunctionSpec()
    assertNotNull(spec["name"])
    assertNotNull(spec["parameters"])

    // Validate spec with OpenAI (mock or integration test)
    // ...
}
```

**4. Monitor Conversion Performance**

For large tool sets, cache OpenAI specs:

```kotlin
class CachedToolRegistry(private val tools: List<Tool>) {
    private val specCache = ConcurrentHashMap<String, Map<String, Any>>()

    fun getOpenAISpecs(): List<Map<String, Any>> {
        return tools.map { tool ->
            specCache.getOrPut(tool.name) {
                tool.toOpenAIFunctionSpec()
            }
        }
    }

    fun invalidateCache(toolName: String) {
        specCache.remove(toolName)
    }

    fun clearCache() {
        specCache.clear()
    }
}
```

**5. Use Strict Mode for Production**

Enable strict mode for production APIs requiring exact schema compliance:

```kotlin
// Development: flexible, non-strict
val devSpec = tool.toOpenAIFunctionSpec()

// Production: strict validation
val prodSpec = tool.toOpenAIFunctionSpec(strict = true)
```

**Strict mode ensures:**
- ✅ `"type": "function"` field is always present
- ✅ `"strict": true` enforces schema validation
- ✅ `"additionalProperties": false` prevents unexpected properties
- ✅ OpenAI validates all function calls against exact schema

**Example: Environment-based strict mode**

```kotlin
class ToolRegistry(private val environment: String) {
    fun getOpenAISpecs(tools: List<Tool>): List<Map<String, Any>> {
        val useStrict = environment == "production"

        return tools.map { tool ->
            tool.toOpenAIFunctionSpec(strict = useStrict)
        }
    }
}

// Usage
val registry = ToolRegistry(System.getenv("ENV") ?: "development")
val specs = registry.getOpenAISpecs(myTools)
```

---

## Best Practices Summary

### 1. State Management
- Use thread-safe collections (`ConcurrentHashMap`)
- Implement TTL and cleanup mechanisms
- Consider external state stores for production

### 2. Composition
- Keep individual tools focused and simple
- Build complex behavior through composition
- Support both sequential and parallel execution

### 3. Async Operations
- Propagate context (tracing, tenant, etc.)
- Use appropriate dispatchers
- Handle timeouts gracefully

### 4. Versioning
- Include version in metadata
- Support multiple versions simultaneously
- Provide clear migration paths
- Set deprecation timelines

### 5. Testing
- Write unit tests for business logic
- Integration tests for external systems
- Property-based tests for invariants
- Mock external dependencies

### 6. Production
- Add comprehensive logging
- Integrate distributed tracing
- Collect metrics
- Implement health checks
- Validate all inputs
- Handle errors gracefully

---

## Next Steps

- [Creating Custom Tools](./creating-tools) - Basic tool creation
- [Tools DSL Reference](../dsl-guide/tools) - DSL syntax
- [Swarm Tools](../orchestration/swarm#swarm-tools) - Shared tools in swarms
- [Observability Guide](../observability/overview) - Monitoring and tracing

---

**Last Updated**: 2025-10-22
