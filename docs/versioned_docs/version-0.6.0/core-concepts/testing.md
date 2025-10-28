# Testing

Comprehensive guide to testing agents, tools, and context-aware code in Spice Framework.

## Overview

Spice Framework provides first-class support for testing all components including agents, tools, flows, and context-aware operations. The framework is designed with testability in mind using Kotlin's coroutine test utilities.

**What You Can Test**:
- ✅ Context-aware tools
- ✅ Agent behavior
- ✅ Tool execution
- ✅ Output validation
- ✅ Cache performance
- ✅ Error handling
- ✅ Multi-tenant isolation

## Testing Setup

### Dependencies

```kotlin
// build.gradle.kts
dependencies {
    // Spice Framework
    implementation("io.github.noailabs:spice-core:0.4.1")

    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}
```

### Test Structure

```kotlin
import io.github.noailabs.spice.*
import io.github.noailabs.spice.dsl.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.*

class MyAgentTest {

    @Test
    fun `test agent behavior`() = runTest {
        // Test using runTest for suspend functions
    }
}
```

## Testing Tools

### Basic Tool Testing

```kotlin
@Test
fun `test simple tool execution`() = runTest {
    // Given: A simple tool
    val tool = SimpleTool(
        name = "calculator",
        description = "Add two numbers",
        parameterSchemas = mapOf(
            "a" to ParameterSchema("number", "First number", true),
            "b" to ParameterSchema("number", "Second number", true)
        )
    ) { params ->
        val a = (params["a"] as Number).toInt()
        val b = (params["b"] as Number).toInt()
        ToolResult.success("Result: ${a + b}")
    }

    // When: Execute tool
    val result = tool.execute(mapOf(
        "a" to 10,
        "b" to 20
    ))

    // Then: Verify result
    assertTrue(result.isSuccess)
    val toolResult = result.getOrNull()!!
    assertTrue(toolResult.success)
    assertEquals("Result: 30", toolResult.result)
}
```

### Context-Aware Tool Testing

```kotlin
@Test
fun `test context-aware tool with context`() = runTest {
    // Given: Context-aware tool
    val tool = contextAwareTool("tenant_lookup") {
        description = "Look up tenant data"
        param("dataKey", "string", "Data key")

        execute { params, context ->
            val tenantId = context.tenantId ?: "default"
            val dataKey = params["dataKey"] as String
            "Tenant $tenantId: Data for $dataKey"
        }
    }

    // When: Execute with context
    val result = withAgentContext(
        "tenantId" to "ACME",
        "userId" to "user-123"
    ) {
        tool.execute(mapOf("dataKey" to "settings"))
    }

    // Then: Verify context was used
    assertTrue(result.isSuccess)
    val output = result.getOrNull()!!.result as String
    assertTrue(output.contains("Tenant ACME"))
}

@Test
fun `test context-aware tool without context fails`() = runTest {
    // Given: Tool that requires context
    val tool = contextAwareTool("requires_context") {
        execute { params, context ->
            context.tenantId!!  // Requires tenantId
        }
    }

    // When: Execute without context
    val result = tool.execute(emptyMap())

    // Then: Should fail gracefully
    assertTrue(result.isSuccess)
    val toolResult = result.getOrNull()!!
    assertFalse(toolResult.success)
    assertTrue(toolResult.error!!.contains("No AgentContext"))
}
```

## Testing Agents

### Basic Agent Testing

```kotlin
@Test
fun `test agent processes comm`() = runTest {
    // Given: Agent with tools
    val agent = buildAgent {
        id = "test-agent"
        name = "Test Agent"

        tool("echo") {
            param("message", "string", "Message to echo")
            execute { params ->
                params["message"] as String
            }
        }
    }

    // When: Process comm
    val comm = Comm(
        id = "comm-1",
        content = "Test message",
        direction = CommDirection.IN
    )

    val result = withAgentContext("tenantId" to "TEST") {
        agent.processComm(comm)
    }

    // Then: Verify processing
    assertTrue(result.isSuccess)
}
```

### Multi-Tool Agent Testing

```kotlin
@Test
fun `test agent with multiple tools`() = runTest {
    val callLog = mutableListOf<String>()

    val agent = buildAgent {
        id = "multi-tool-agent"

        tool("tool1") {
            execute { params ->
                callLog.add("tool1")
                "tool1 result"
            }
        }

        tool("tool2") {
            execute { params ->
                callLog.add("tool2")
                "tool2 result"
            }
        }
    }

    // Verify tools are registered
    val tools = agent.getTools()
    assertEquals(2, tools.size)
    assertTrue(tools.any { it.name == "tool1" })
    assertTrue(tools.any { it.name == "tool2" })
}
```

## Testing Validation

### Output Validation Testing

```kotlin
@Test
fun `test output validation success`() = runTest {
    // Given: Tool with validation
    val tool = contextAwareTool("validated_tool") {
        validate {
            requireField("result")
            fieldType("result", FieldType.STRING)
        }

        execute { params, context ->
            mapOf("result" to "success")
        }
    }

    // When: Execute
    val result = withAgentContext("tenantId" to "TEST") {
        tool.execute(emptyMap())
    }

    // Then: Validation passes
    assertTrue(result.isSuccess)
    assertTrue(result.getOrNull()!!.success)
}

@Test
fun `test output validation failure`() = runTest {
    // Given: Tool with validation
    val tool = contextAwareTool("validated_tool") {
        validate {
            requireField("result")
        }

        execute { params, context ->
            mapOf("wrongField" to "value")  // Missing "result"
        }
    }

    // When: Execute
    val result = withAgentContext("tenantId" to "TEST") {
        tool.execute(emptyMap())
    }

    // Then: Validation fails
    assertTrue(result.isSuccess)
    val toolResult = result.getOrNull()!!
    assertFalse(toolResult.success)
    assertTrue(toolResult.error!!.contains("result"))
}
```

## Testing Caching

### Cache Hit/Miss Testing

```kotlin
@Test
fun `test cache hit and miss`() = runTest {
    var executionCount = 0

    val tool = SimpleTool(
        name = "cached",
        description = "Cached tool",
        parameterSchemas = emptyMap()
    ) { params ->
        executionCount++
        ToolResult.success("Result $executionCount")
    }

    val cached = tool.cached(ttl = 300, maxSize = 100)

    // First call - cache miss
    cached.execute(mapOf("id" to "1"))
    assertEquals(1, executionCount)

    // Second call - cache hit
    cached.execute(mapOf("id" to "1"))
    assertEquals(1, executionCount)  // Still 1!

    // Different params - cache miss
    cached.execute(mapOf("id" to "2"))
    assertEquals(2, executionCount)

    // Verify metrics
    val metrics = cached.metrics
    assertEquals(1, metrics.hits)
    assertEquals(2, metrics.misses)
}
```

## Testing Multi-Tenancy

### Tenant Isolation Testing

```kotlin
@Test
fun `test tenant isolation`() = runTest {
    val dataStore = mutableMapOf<String, MutableList<String>>()

    val tool = contextAwareTool("store_data") {
        param("value", "string", "Value to store")

        execute { params, context ->
            val tenantId = context.tenantId!!
            val value = params["value"] as String

            dataStore.getOrPut(tenantId) { mutableListOf() }.add(value)
            "Stored"
        }
    }

    // Tenant A stores data
    withAgentContext("tenantId" to "TENANT-A") {
        tool.execute(mapOf("value" to "data-a"))
    }

    // Tenant B stores data
    withAgentContext("tenantId" to "TENANT-B") {
        tool.execute(mapOf("value" to "data-b"))
    }

    // Verify isolation
    assertEquals(listOf("data-a"), dataStore["TENANT-A"])
    assertEquals(listOf("data-b"), dataStore["TENANT-B"])
    assertNull(dataStore["TENANT-A"]?.find { it == "data-b" })
}
```

## Testing Error Handling

### Error Recovery Testing

```kotlin
@Test
fun `test error handling and recovery`() = runTest {
    val tool = contextAwareTool("error_prone") {
        param("shouldFail", "boolean", "Should fail")

        execute { params, context ->
            if (params["shouldFail"] as Boolean) {
                throw IllegalArgumentException("Intentional failure")
            }
            "Success"
        }
    }

    // Test failure
    val failResult = withAgentContext("tenantId" to "TEST") {
        tool.execute(mapOf("shouldFail" to true))
    }

    assertTrue(failResult.isSuccess)
    val failToolResult = failResult.getOrNull()!!
    assertFalse(failToolResult.success)
    assertTrue(failToolResult.error!!.contains("Intentional failure"))

    // Test success
    val successResult = withAgentContext("tenantId" to "TEST") {
        tool.execute(mapOf("shouldFail" to false))
    }

    assertTrue(successResult.isSuccess)
    val successToolResult = successResult.getOrNull()!!
    assertTrue(successToolResult.success)
}
```

## Best Practices

### 1. Use `runTest` for Suspend Functions

```kotlin
// ✅ Good
@Test
fun `test async operation`() = runTest {
    val result = tool.execute(params)
    assertTrue(result.isSuccess)
}

// ❌ Bad
@Test
fun `test async operation`() {
    runBlocking {  // Don't use runBlocking in tests!
        val result = tool.execute(params)
    }
}
```

### 2. Test Both Success and Failure Paths

```kotlin
@Test
fun `test success path`() = runTest {
    // Test happy path
}

@Test
fun `test validation failure`() = runTest {
    // Test validation errors
}

@Test
fun `test missing context`() = runTest {
    // Test missing context
}

@Test
fun `test error handling`() = runTest {
    // Test exception handling
}
```

### 3. Verify Context Isolation

```kotlin
@Test
fun `test context does not leak between calls`() = runTest {
    // First context
    withAgentContext("tenantId" to "A") {
        // ...
    }

    // Second context - should be isolated
    withAgentContext("tenantId" to "B") {
        // Verify "A" is not accessible
    }
}
```

### 4. Use Meaningful Test Names

```kotlin
// ✅ Good
@Test
fun `should return error when tenant context is missing`() = runTest { }

@Test
fun `should cache results for 5 minutes with same parameters`() = runTest { }

// ❌ Bad
@Test
fun `test1`() = runTest { }

@Test
fun `testTool`() = runTest { }
```

### 5. Test Edge Cases

```kotlin
@Test
fun `test empty parameters`() = runTest { }

@Test
fun `test null values`() = runTest { }

@Test
fun `test very large inputs`() = runTest { }

@Test
fun `test concurrent execution`() = runTest { }
```

## Performance Testing

### Load Testing

```kotlin
@Test
fun `test tool performance under load`() = runTest {
    val tool = contextAwareTool("performance_test") {
        execute { params, context ->
            // Simulate work
            delay(10)
            "Done"
        }
    }

    val start = System.currentTimeMillis()

    // Execute 100 times
    repeat(100) {
        withAgentContext("tenantId" to "PERF") {
            tool.execute(emptyMap())
        }
    }

    val duration = System.currentTimeMillis() - start

    println("100 executions took ${duration}ms")
    assertTrue(duration < 5000)  // Should complete in 5 seconds
}
```

### Cache Performance Testing

```kotlin
@Test
fun `test cache improves performance`() = runTest {
    var executionTime = 0L

    val tool = SimpleTool("slow", "Slow tool", emptyMap()) {
        val start = System.currentTimeMillis()
        delay(100)  // Simulate slow operation
        executionTime = System.currentTimeMillis() - start
        ToolResult.success("Done")
    }

    val cached = tool.cached(ttl = 300, maxSize = 100)

    // First call - slow
    cached.execute(mapOf("id" to "1"))
    val firstCallTime = executionTime

    // Second call - fast (cached)
    val start = System.currentTimeMillis()
    cached.execute(mapOf("id" to "1"))
    val secondCallTime = System.currentTimeMillis() - start

    assertTrue(secondCallTime < firstCallTime / 10)  // 10x faster!
}
```

## Integration Testing

### Full Agent Integration Test

```kotlin
@Test
fun `test full agent workflow`() = runTest {
    // Setup
    val customerService = MockCustomerService()
    val orderService = MockOrderService()

    val agent = buildAgent {
        id = "order-agent"

        contextAwareTool("lookup_customer") {
            param("customerId", "string", "Customer ID")
            execute { params, context ->
                customerService.find(
                    context.tenantId!!,
                    params["customerId"] as String
                )
            }
        }

        contextAwareTool("create_order") {
            param("customerId", "string", "Customer ID")
            param("items", "array", "Items")
            execute { params, context ->
                orderService.create(
                    tenantId = context.tenantId!!,
                    customerId = params["customerId"] as String,
                    items = params["items"] as List<*>
                )
            }
        }
    }

    // Execute workflow
    withAgentContext(
        "tenantId" to "ACME",
        "userId" to "user-123"
    ) {
        // Step 1: Lookup customer
        val customer = agent.getTools()
            .find { it.name == "lookup_customer" }!!
            .execute(mapOf("customerId" to "cust-1"))

        assertTrue(customer.isSuccess)

        // Step 2: Create order
        val order = agent.getTools()
            .find { it.name == "create_order" }!!
            .execute(mapOf(
                "customerId" to "cust-1",
                "items" to listOf("item1", "item2")
            ))

        assertTrue(order.isSuccess)
    }

    // Verify service interactions
    assertEquals(1, customerService.findCallCount)
    assertEquals(1, orderService.createCallCount)
}
```

## See Also

- [Context Testing Guide](../testing/context-testing.md) - Advanced context testing patterns
- [Best Practices](../error-handling/best-practices.md) - Error handling in tests
- [Examples](../examples/) - Real-world testing examples
