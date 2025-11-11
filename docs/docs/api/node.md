# Node API

Graph execution system for orchestrating multi-step workflows with state management.

## Overview

The **Node API** is the core abstraction for Spice's graph-based execution system. Nodes represent units of work that can be:
- Executed sequentially or in parallel
- Connected through edges to form workflows
- Paused and resumed with checkpointing
- Integrated with human-in-the-loop patterns

## Core Interfaces

### Node

The fundamental building block of graph execution:

```kotlin
interface Node {
    val id: String
    suspend fun run(ctx: NodeContext): SpiceResult<NodeResult>
}
```

**Every node must:**
- Have a unique `id` within the graph
- Implement `run()` to perform its work
- Return `SpiceResult<NodeResult>` for error handling

### NodeContext

Execution context passed to each node:

```kotlin
data class NodeContext(
    val graphId: String,
    val state: Map<String, Any?>,  // ✅ v0.9.0: Standard Map interface
    val context: ExecutionContext
)
```

**Fields:**
- `graphId`: Unique identifier for the current graph execution
- `state`: Immutable state map shared across nodes (read-only access)
- `context`: Execution metadata (tenantId, userId, correlationId, etc.)

**Helper Methods:**

```kotlin
// Create new context with updated state
fun withState(key: String, value: Any?): NodeContext
fun withState(updates: Map<String, Any?>): NodeContext

// Update execution context
fun withContext(newContext: ExecutionContext): NodeContext

// Preserve metadata for NodeResult
fun preserveMetadata(additional: Map<String, Any> = emptyMap()): Map<String, Any>
```

### NodeResult

Result of node execution:

```kotlin
data class NodeResult private constructor(
    val data: Any?,
    val metadata: Map<String, Any>,  // Non-nullable (nulls filtered)
    val nextEdges: List<String> = emptyList()
)
```

**Factory Methods:**

```kotlin
// Explicit metadata
NodeResult.create(
    data = myData,
    metadata = mapOf("status" to "complete"),
    nextEdges = listOf("next-node")
)

// Preserve context metadata
NodeResult.fromContext(
    ctx = ctx,
    data = result,
    additional = mapOf("phase" to "processing")
)

// From HumanResponse (auto-propagates metadata)
NodeResult.fromHumanResponse(
    ctx = ctx,
    response = humanResponse
)
```

## Built-in Node Types

### AgentNode

Execute an agent with automatic Comm conversion:

```kotlin
AgentNode(
    id = "analyzer",
    agent = myAgent,
    inputExtractor = { ctx ->
        ctx.state["user_input"]?.toString() ?: "Default query"
    }
)
```

**Features:**
- Automatically converts state to Comm for agent
- Extracts result from agent response
- Propagates ExecutionContext

### ToolNode

Execute a tool with parameters from state:

```kotlin
ToolNode(
    id = "search",
    tool = searchTool,
    parameterExtractor = { ctx ->
        mapOf(
            "query" to ctx.state["search_query"],
            "limit" to 10
        )
    }
)
```

**Features:**
- Type-safe parameter extraction
- Automatic error handling
- Metadata propagation

### HumanNode

Pause execution for human input:

```kotlin
HumanNode(
    id = "approval",
    question = "Approve this action?",
    options = listOf(
        HumanOption("approve", "Approve"),
        HumanOption("reject", "Reject")
    ),
    metadata = mapOf("required" to true)
)
```

**Features:**
- Checkpoint creation
- Resume with human response
- Metadata propagation to next node

### OutputNode

Format final output:

```kotlin
OutputNode(
    id = "result",
    formatter = { ctx ->
        val result = ctx.state["analysis_result"]
        "Analysis complete: $result"
    }
)
```

**Features:**
- Access to full state
- Custom formatting logic
- Final result extraction

### ResponseNode

Structured response generation:

```kotlin
ResponseNode(
    id = "response",
    extractors = mapOf(
        "workflow_message" to { ctx -> ctx.state["message"]?.toString() },
        "reasoning" to { ctx -> ctx.state["reasoning"]?.toString() },
        "data" to { ctx -> ctx.state["result_data"] }
    )
)
```

**Features:**
- Multiple field extraction
- Type-safe access to state
- Structured JSON output

## State Management

### Immutability Pattern

State is **immutable** - modifications create new NodeContext:

```kotlin
override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
    // ❌ Cannot modify state directly
    // ctx.state["key"] = value  // Compile error

    // ✅ Create result with new state
    return SpiceResult.success(
        NodeResult.fromContext(
            ctx = ctx,
            data = "processed",
            additional = mapOf(
                "processed_at" to System.currentTimeMillis(),
                "status" to "complete"
            )
        )
    )
}
```

### State Access Patterns

```kotlin
// Read from state
val input = ctx.state["user_input"]?.toString() ?: "default"
val count = (ctx.state["count"] as? Number)?.toInt() ?: 0
val enabled = ctx.state["enabled"] as? Boolean ?: false

// Pattern matching
when (ctx.state["action"]) {
    "approve" -> handleApproval(ctx)
    "reject" -> handleRejection(ctx)
    else -> handleUnknown(ctx)
}

// Check existence
if (ctx.state.containsKey("optional_field")) {
    // Handle optional data
}
```

### State Propagation

State flows through the graph automatically:

```kotlin
graph {
    node(AgentNode(
        id = "analyzer",
        agent = analyzer,
        inputExtractor = { it.state["input"]?.toString() ?: "" }
    ))

    node(ToolNode(
        id = "enrich",
        tool = enrichTool,
        parameterExtractor = { ctx ->
            // Access previous node's result
            mapOf("data" to ctx.state["_previous"])
        }
    ))

    edge("analyzer", "enrich")
}
```

**Special state keys:**
- `_previous`: Result of the previous node
- `workflow_message`: Human-readable message
- `_checkpoint_id`: Current checkpoint (if paused)

## Metadata Propagation

Metadata flows through ExecutionContext:

```kotlin
// Initial context
val ctx = ExecutionContext(
    tenantId = "tenant-123",
    userId = "user-456",
    metadata = mapOf("session_id" to "abc-123")
)

// Accessible in all nodes
override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
    val tenantId = ctx.context.tenantId  // "tenant-123"
    val sessionId = ctx.context["session_id"]  // "abc-123"

    // Add more metadata
    return SpiceResult.success(
        NodeResult.fromContext(
            ctx,
            data = result,
            additional = mapOf(
                "node_id" to id,
                "execution_time" to System.currentTimeMillis()
            )
        )
    )
}
```

## Custom Node Implementation

### Basic Custom Node

```kotlin
class ProcessingNode(
    override val id: String,
    private val processor: (String) -> String
) : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        return try {
            val input = ctx.state["input"]?.toString()
                ?: return SpiceResult.failure("Missing input")

            val result = processor(input)

            SpiceResult.success(
                NodeResult.fromContext(
                    ctx = ctx,
                    data = result,
                    additional = mapOf(
                        "processed" to true,
                        "length" to result.length
                    )
                )
            )
        } catch (e: Exception) {
            SpiceResult.failure("Processing failed: ${e.message}")
        }
    }
}
```

### Conditional Node

```kotlin
class ConditionalNode(
    override val id: String,
    private val condition: (NodeContext) -> Boolean,
    private val trueEdge: String,
    private val falseEdge: String
) : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        val nextEdge = if (condition(ctx)) trueEdge else falseEdge

        return SpiceResult.success(
            NodeResult.fromContext(
                ctx = ctx,
                data = "Condition evaluated",
                additional = mapOf("branch" to nextEdge)
            ).copy(nextEdges = listOf(nextEdge))
        )
    }
}
```

### Async Processing Node

```kotlin
class AsyncProcessingNode(
    override val id: String,
    private val service: ExternalService
) : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        return try {
            val data = ctx.state["data"]?.toString()
                ?: return SpiceResult.failure("Missing data")

            // Async call
            val result = withTimeout(30.seconds) {
                service.process(data)
            }

            SpiceResult.success(
                NodeResult.fromContext(
                    ctx = ctx,
                    data = result,
                    additional = mapOf(
                        "async" to true,
                        "service" to service.name
                    )
                )
            )
        } catch (e: TimeoutCancellationException) {
            SpiceResult.failure("Service timeout")
        } catch (e: Exception) {
            SpiceResult.failure("Service error: ${e.message}")
        }
    }
}
```

## Error Handling

### Node-Level Errors

```kotlin
override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
    // Validation errors
    val input = ctx.state["input"]?.toString()
        ?: return SpiceResult.failure("Missing required input")

    // Processing errors
    return try {
        val result = dangerousOperation(input)
        SpiceResult.success(NodeResult.fromContext(ctx, result))
    } catch (e: ValidationException) {
        SpiceResult.failure("Validation failed: ${e.message}")
    } catch (e: Exception) {
        SpiceResult.failure("Processing failed: ${e.message}")
    }
}
```

### Graph-Level Error Actions

Configure error handling at graph level:

```kotlin
graph {
    errorAction = ErrorAction.CONTINUE  // Skip failed nodes
    // or ErrorAction.FAIL (default)
    // or ErrorAction.RETRY(maxAttempts = 3)
}
```

## Best Practices

### 1. State Access

```kotlin
// ✅ GOOD: Safe with defaults
val count = (ctx.state["count"] as? Number)?.toInt() ?: 0

// ✅ GOOD: Explicit error
val id = ctx.state["id"]?.toString()
    ?: return SpiceResult.failure("Missing ID")

// ❌ BAD: Unsafe cast
val count = ctx.state["count"] as Int  // Runtime exception risk
```

### 2. Metadata Size

Keep metadata under 5KB:

```kotlin
// ✅ GOOD: Small, focused metadata
mapOf(
    "status" to "complete",
    "count" to 42,
    "timestamp" to System.currentTimeMillis()
)

// ❌ BAD: Large payload in metadata
mapOf(
    "full_result" to largeJsonString  // Use state instead!
)
```

### 3. Node Composition

```kotlin
// ✅ GOOD: Small, focused nodes
graph {
    node(ValidateNode("validate"))
    node(ProcessNode("process"))
    node(FormatNode("format"))

    edge("validate", "process")
    edge("process", "format")
}

// ❌ BAD: One giant node
graph {
    node(DoEverythingNode("all"))  // Hard to test, reuse, debug
}
```

### 4. Context Propagation

```kotlin
// ✅ GOOD: Preserve and extend
NodeResult.fromContext(
    ctx = ctx,
    data = result,
    additional = mapOf("phase" to "complete")
)

// ❌ BAD: Lose context
NodeResult.create(
    data = result,
    metadata = mapOf("phase" to "complete")  // Lost tenantId, userId, etc!
)
```

## See Also

- [Graph System](/docs/orchestration/graph-system) - Complete graph execution guide
- [Graph Nodes](/docs/orchestration/graph-nodes) - Node types and patterns
- [Execution Context](/docs/api/execution-context) - Context propagation
- [Error Handling](/docs/error-handling/overview) - SpiceResult patterns
