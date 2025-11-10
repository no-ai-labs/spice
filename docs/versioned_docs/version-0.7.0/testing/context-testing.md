# Context Testing Guide

Advanced patterns for testing context-aware tools, services, and agents in multi-tenant scenarios.

## Overview

Testing context-aware code requires special attention to ensure proper context propagation, tenant isolation, and error handling. This guide covers advanced testing patterns specific to Spice Framework's context system.

## Testing Context Propagation

### Basic Context Flow

```kotlin
@Test
fun `context flows through tool execution`() = runTest {
    val capturedContext = mutableListOf<AgentContext?>()

    val tool = contextAwareTool("context_capture") {
        execute { params, context ->
            capturedContext.add(context)
            "Captured: ${context.tenantId}"
        }
    }

    withAgentContext(
        "tenantId" to "ACME",
        "userId" to "user-123"
    ) {
        tool.execute(emptyMap())
    }

    assertEquals(1, capturedContext.size)
    assertEquals("ACME", capturedContext[0]?.tenantId)
    assertEquals("user-123", capturedContext[0]?.userId)
}
```

### Nested Context

```kotlin
@Test
fun `context enrichment preserves parent values`() = runTest {
    val contexts = mutableListOf<AgentContext>()

    val tool = contextAwareTool("capture") {
        execute { params, context ->
            contexts.add(context)
            "OK"
        }
    }

    withAgentContext("tenantId" to "ACME") {
        tool.execute(emptyMap())  // Has tenantId

        withEnrichedContext("userId" to "user-123") {
            tool.execute(emptyMap())  // Has tenantId + userId
        }
    }

    assertEquals(2, contexts.size)

    // First execution
    assertEquals("ACME", contexts[0].tenantId)
    assertNull(contexts[0].userId)

    // Second execution (enriched)
    assertEquals("ACME", contexts[1].tenantId)
    assertEquals("user-123", contexts[1].userId)
}
```

## Testing Multi-Tenancy

### Tenant Isolation

```kotlin
@Test
fun `tenants cannot access each others data`() = runTest {
    val dataStore = mutableMapOf<String, MutableList<String>>()

    val storeTool = contextAwareTool("store") {
        param("value", "string", "Value")
        execute { params, context ->
            val tenantId = context.tenantId!!
            dataStore.getOrPut(tenantId) { mutableListOf() }
                .add(params["value"] as String)
            "Stored"
        }
    }

    val retrieveTool = contextAwareTool("retrieve") {
        execute { params, context ->
            val tenantId = context.tenantId!!
            dataStore[tenantId] ?: emptyList()
        }
    }

    // Tenant A stores data
    withAgentContext("tenantId" to "TENANT-A") {
        storeTool.execute(mapOf("value" to "secret-a"))
    }

    // Tenant B stores data
    withAgentContext("tenantId" to "TENANT-B") {
        storeTool.execute(mapOf("value" to "secret-b"))
    }

    // Tenant A retrieves - should only see their data
    val resultA = withAgentContext("tenantId" to "TENANT-A") {
        retrieveTool.execute(emptyMap())
    }

    val dataA = resultA.getOrNull()!!.result as List<*>
    assertEquals(listOf("secret-a"), dataA)
    assertFalse(dataA.contains("secret-b"))

    // Tenant B retrieves - should only see their data
    val resultB = withAgentContext("tenantId" to "TENANT-B") {
        retrieveTool.execute(emptyMap())
    }

    val dataB = resultB.getOrNull()!!.result as List<*>
    assertEquals(listOf("secret-b"), dataB)
    assertFalse(dataB.contains("secret-a"))
}
```

### Cross-Tenant Validation

```kotlin
@Test
fun `validation prevents cross-tenant access`() = runTest {
    val tool = contextAwareTool("secure_access") {
        param("tenantId", "string", "Tenant ID")

        validate {
            custom("tenant must match context") { output, context ->
                val outputTenant = (output as? Map<*, *>)?.get("tenantId") as? String
                outputTenant == context?.tenantId
            }
        }

        execute { params, context ->
            mapOf("tenantId" to params["tenantId"])
        }
    }

    // Matching tenant - should succeed
    val validResult = withAgentContext("tenantId" to "ACME") {
        tool.execute(mapOf("tenantId" to "ACME"))
    }
    assertTrue(validResult.getOrNull()!!.success)

    // Mismatched tenant - should fail validation
    val invalidResult = withAgentContext("tenantId" to "ACME") {
        tool.execute(mapOf("tenantId" to "EVIL"))
    }
    assertFalse(invalidResult.getOrNull()!!.success)
}
```

## Testing Async Operations

### Parallel Execution

```kotlin
@Test
fun `context propagates through parallel operations`() = runTest {
    val capturedTenants = mutableListOf<String>()

    val tool = contextAwareTool("async_capture") {
        execute { params, context ->
            coroutineScope {
                val jobs = (1..5).map {
                    async {
                        delay(10)
                        synchronized(capturedTenants) {
                            capturedTenants.add(context.tenantId ?: "none")
                        }
                    }
                }
                jobs.awaitAll()
            }
            "Done"
        }
    }

    withAgentContext("tenantId" to "ACME") {
        tool.execute(emptyMap())
    }

    assertEquals(5, capturedTenants.size)
    assertTrue(capturedTenants.all { it == "ACME" })
}
```

## Testing Services

### BaseContextAwareService Testing

```kotlin
class TestRepository : BaseContextAwareService() {
    private val data = mutableMapOf<String, MutableList<String>>()

    suspend fun store(value: String) = withTenant { tenantId ->
        data.getOrPut(tenantId) { mutableListOf() }.add(value)
    }

    suspend fun retrieve() = withTenant { tenantId ->
        data[tenantId] ?: emptyList()
    }
}

@Test
fun `service uses context automatically`() = runTest {
    val repo = TestRepository()

    // Store as TENANT-A
    withAgentContext("tenantId" to "TENANT-A") {
        repo.store("data-a")
    }

    // Store as TENANT-B
    withAgentContext("tenantId" to "TENANT-B") {
        repo.store("data-b")
    }

    // Retrieve as TENANT-A
    val dataA = withAgentContext("tenantId" to "TENANT-A") {
        repo.retrieve()
    }
    assertEquals(listOf("data-a"), dataA)

    // Retrieve as TENANT-B
    val dataB = withAgentContext("tenantId" to "TENANT-B") {
        repo.retrieve()
    }
    assertEquals(listOf("data-b"), dataB)
}
```

## Testing Error Cases

### Missing Context

```kotlin
@Test
fun `service fails gracefully when context is missing`() = runTest {
    class StrictService : BaseContextAwareService() {
        suspend fun doWork() = withTenant { tenantId ->
            "Work done for $tenantId"
        }
    }

    val service = StrictService()

    // Without context - should throw
    assertFailsWith<IllegalStateException> {
        service.doWork()
    }

    // With context - should succeed
    val result = withAgentContext("tenantId" to "ACME") {
        service.doWork()
    }
    assertEquals("Work done for ACME", result)
}
```

## Mock Services

### Creating Test Doubles

```kotlin
class MockCustomerService : BaseContextAwareService() {
    val findCalls = mutableListOf<Pair<String, String>>()

    suspend fun find(customerId: String) = withTenant { tenantId ->
        findCalls.add(tenantId to customerId)
        Customer(id = customerId, tenantId = tenantId, name = "Test Customer")
    }
}

@Test
fun `mock service tracks calls`() = runTest {
    val mock = MockCustomerService()

    withAgentContext("tenantId" to "ACME") {
        mock.find("cust-1")
        mock.find("cust-2")
    }

    assertEquals(2, mock.findCalls.size)
    assertEquals("ACME" to "cust-1", mock.findCalls[0])
    assertEquals("ACME" to "cust-2", mock.findCalls[1])
}
```

## Best Practices

### 1. Always Test With and Without Context

```kotlin
@Test
fun `test with context succeeds`() = runTest {
    withAgentContext("tenantId" to "TEST") {
        // Test happy path
    }
}

@Test
fun `test without context fails appropriately`() = runTest {
    // Test error path
}
```

### 2. Test Tenant Isolation

```kotlin
@Test
fun `test tenant data isolation`() = runTest {
    // Store data for multiple tenants
    // Verify each tenant only sees their data
}
```

### 3. Test Context Enrichment

```kotlin
@Test
fun `test context enrichment preserves parent`() = runTest {
    withAgentContext("key1" to "value1") {
        withEnrichedContext("key2" to "value2") {
            // Verify both key1 and key2 are present
        }
    }
}
```

### 4. Use Descriptive Test Names

```kotlin
@Test
fun `should isolate tenant A data from tenant B`() = runTest { }

@Test
fun `should propagate context through 3 levels of nesting`() = runTest { }
```

## See Also

- [Core Concepts: Testing](../core-concepts/testing.md) - Basic testing guide
- [Context-Aware Tools](../dsl-guide/context-aware-tools.md) - Tool development
- [Multi-Tenancy](../security/multi-tenancy.md) - Multi-tenant architecture
- [Context Propagation](../advanced/context-propagation.md) - Deep dive
