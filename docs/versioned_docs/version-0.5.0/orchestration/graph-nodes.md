# Graph Nodes

**Added in:** 0.5.0

Nodes are the building blocks of graphs. Each node represents a unit of work that can be executed within a graph workflow.

## Node Interface

All nodes implement the `Node` interface:

```kotlin
interface Node {
    val id: String
    suspend fun run(ctx: NodeContext): SpiceResult<NodeResult>
}
```

### NodeContext

The context passed to each node during execution:

```kotlin
data class NodeContext(
    val graphId: String,
    val state: MutableMap<String, Any?>,
    val metadata: MutableMap<String, Any> = mutableMapOf(),
    val agentContext: AgentContext? = null  // Auto-propagated from coroutine context
)
```

- **graphId**: Unique identifier for the graph
- **state**: Shared state across all nodes (mutable)
- **metadata**: Additional metadata for the execution
- **agentContext**: Context for multi-tenant/distributed scenarios

### NodeResult

The result returned by node execution:

```kotlin
data class NodeResult(
    val data: Any?,
    val metadata: Map<String, Any> = emptyMap(),
    val nextEdges: List<String> = emptyList()
)
```

## Built-in Node Types

### AgentNode

Executes a Spice `Agent` within the graph.

```kotlin
class AgentNode(
    override val id: String,
    val agent: Agent,
    val inputKey: String? = null  // Which key from state to use as input
) : Node
```

**Usage:**

```kotlin
// Using DSL
val graph = graph("my-graph") {
    agent("analyzer", analysisAgent)  // Uses "_previous" or "input" from state
    agent("processor", processorAgent, inputKey = "analyzer")  // Uses specific key
}

// Manually
val node = AgentNode(
    id = "my-agent",
    agent = myAgent,
    inputKey = "custom-input"
)
```

**How it works:**
1. Retrieves input from `state[inputKey]` (or `_previous`/`input` if not specified)
2. Creates a `Comm` with the input content
3. Passes `AgentContext` from node context to the agent
4. Returns agent's response in `NodeResult`

**Example:**

```kotlin
val greetingAgent = object : Agent {
    override val id = "greeter"
    // ... implementation
    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        return SpiceResult.success(
            comm.reply("Hello, ${comm.content}!", id)
        )
    }
}

val graph = graph("greeting") {
    agent("greeter", greetingAgent)
    output("result") { it.state["greeter"] }
}

// Input "Alice" -> Output "Hello, Alice!"
```

### ToolNode

Executes a Spice `Tool` within the graph.

```kotlin
class ToolNode(
    override val id: String,
    val tool: Tool,
    val paramMapper: (NodeContext) -> Map<String, Any?> = { it.state }
) : Node
```

**Usage:**

```kotlin
// Using DSL
val graph = graph("my-graph") {
    tool("processor", processorTool) { ctx ->
        mapOf(
            "input" to ctx.state["data"],
            "format" to "json"
        )
    }
}

// Manually
val node = ToolNode(
    id = "my-tool",
    tool = myTool,
    paramMapper = { ctx ->
        mapOf("param" to ctx.state["value"])
    }
)
```

**How it works:**
1. Maps node context to tool parameters using `paramMapper`
2. Filters out null values
3. Passes `AgentContext` to tool (if available)
4. Returns tool result in `NodeResult`

**Example:**

```kotlin
val calculatorTool = object : Tool {
    override val name = "calculator"
    // ... implementation
    override suspend fun execute(
        parameters: Map<String, Any>,
        context: ToolContext
    ): SpiceResult<ToolResult> {
        val a = parameters["a"] as Int
        val b = parameters["b"] as Int
        return SpiceResult.success(
            ToolResult(success = true, result = a + b)
        )
    }
}

val graph = graph("calculation") {
    tool("add", calculatorTool) { ctx ->
        mapOf("a" to 5, "b" to 3)
    }
    output("result") { it.state["add"] }
}

// Output: 8
```

### OutputNode

Transforms and outputs the final result from the graph.

```kotlin
class OutputNode(
    override val id: String,
    val transformer: (NodeContext) -> Any? = { it.state["_previous"] }
) : Node
```

**Is OutputNode Required?**

**No!** OutputNode is completely optional. Here's how it works:

```kotlin
// ✅ Without OutputNode - returns last node's result
val simpleGraph = graph("simple") {
    agent("processor", processorAgent)
    // No output() needed
}

val report = runner.run(simpleGraph, input).getOrThrow()
// report.result = processor's NodeResult.data

// ✅ With OutputNode - for transformation/selection
val advancedGraph = graph("advanced") {
    agent("step1", agent1)
    agent("step2", agent2)

    output("custom") { ctx ->
        // Return step1 instead of step2
        ctx.state["step1"]
    }
}

val report = runner.run(advancedGraph, input).getOrThrow()
// report.result = step1's NodeResult.data (from output selector)
```

**When to use OutputNode:**
- Need to select specific node results (not the last one)
- Want to combine multiple node results
- Need to transform the final output
- Want explicit control over return value

**When to skip OutputNode:**
- Simple linear workflows
- Last node's result is exactly what you need
- No transformation required

**Usage:**

```kotlin
// Using DSL
val graph = graph("my-graph") {
    agent("step1", agent1)
    agent("step2", agent2)

    // Simple output (uses specific node's result)
    output("result") { it.state["step2"] }

    // Complex transformation
    output("summary") { ctx ->
        mapOf(
            "step1_result" to ctx.state["step1"],
            "step2_result" to ctx.state["step2"],
            "total_steps" to 2
        )
    }
}
```

**Example:**

```kotlin
val graph = graph("analytics") {
    agent("analyzer", analysisAgent)
    tool("processor", processorTool) { mapOf("data" to it.state["analyzer"]) }

    output("report") { ctx ->
        mapOf(
            "analysis" to ctx.state["analyzer"],
            "processed" to ctx.state["processor"],
            "timestamp" to System.currentTimeMillis(),
            "graph_id" to ctx.graphId
        )
    }
}
```

## Custom Nodes

Create custom nodes by implementing the `Node` interface:

```kotlin
class DelayNode(
    override val id: String,
    val delayMs: Long
) : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        return SpiceResult.catchingSuspend {
            kotlinx.coroutines.delay(delayMs)
            NodeResult(data = "Delayed for ${delayMs}ms")
        }
    }
}

// Usage
val graph = Graph(
    id = "delayed-workflow",
    nodes = mapOf(
        "delay" to DelayNode("delay", delayMs = 1000),
        "output" to OutputNode("output")
    ),
    edges = listOf(Edge("delay", "output")),
    entryPoint = "delay"
)
```

### Advanced Custom Node Example

```kotlin
class ConditionalSplitNode(
    override val id: String,
    val condition: (Any?) -> Boolean
) : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        return SpiceResult.catching {
            val input = ctx.state["_previous"]
            val result = if (condition(input)) "path-a" else "path-b"

            NodeResult(
                data = result,
                metadata = mapOf(
                    "condition_met" to condition(input),
                    "input" to input
                )
            )
        }
    }
}

val graph = graph("conditional") {
    // Would need to use Graph constructor with custom nodes
}

// With manual construction
val graph = Graph(
    id = "split-workflow",
    nodes = mapOf(
        "splitter" to ConditionalSplitNode("splitter") { it is String && it.startsWith("A") },
        "path-a" to OutputNode("path-a"),
        "path-b" to OutputNode("path-b"),
        "result" to OutputNode("result")
    ),
    edges = listOf(
        Edge("splitter", "path-a") { it.data == "path-a" },
        Edge("splitter", "path-b") { it.data == "path-b" },
        Edge("path-a", "result"),
        Edge("path-b", "result")
    ),
    entryPoint = "splitter"
)
```

## Node State Management

### Accessing State

```kotlin
val node = object : Node {
    override val id = "my-node"

    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        // Access previous node's output
        val previousResult = ctx.state["_previous"]

        // Access specific node's output
        val step1Result = ctx.state["step1"]

        // Access initial input
        val input = ctx.state["input"]

        return SpiceResult.success(NodeResult(data = "processed"))
    }
}
```

### Modifying State

```kotlin
val node = object : Node {
    override val id = "counter"

    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        // Read current count
        val count = (ctx.state["count"] as? Int) ?: 0

        // Update state (shared across all nodes)
        ctx.state["count"] = count + 1
        ctx.state["last_updated"] = System.currentTimeMillis()

        return SpiceResult.success(NodeResult(data = count + 1))
    }
}
```

## Context Propagation

`AgentContext` automatically propagates through all nodes:

```kotlin
// Set context in coroutine scope
val agentContext = AgentContext.of(
    "tenantId" to "tenant-123",
    "userId" to "user-456"
)

withContext(agentContext) {
    runner.run(graph, input)
}

// Nodes automatically receive context
class ContextAwareNode(override val id: String) : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        val tenantId = ctx.agentContext?.tenantId
        val userId = ctx.agentContext?.userId

        println("Processing for tenant: $tenantId, user: $userId")

        return SpiceResult.success(NodeResult(data = "processed"))
    }
}
```

## Node Execution Report

After graph execution, you can inspect each node's execution:

```kotlin
val report = runner.run(graph, input).getOrThrow()

report.nodeReports.forEach { nodeReport ->
    println("Node: ${nodeReport.nodeId}")
    println("  Status: ${nodeReport.status}")  // SUCCESS, FAILED, SKIPPED
    println("  Duration: ${nodeReport.duration}")
    println("  Output: ${nodeReport.output}")
}
```

## Best Practices

### ✅ Do's

1. **Keep nodes focused** - Each node should do one thing well
2. **Use meaningful IDs** - Helps with debugging and state access
3. **Handle errors gracefully** - Return `SpiceResult.failure()` instead of throwing
4. **Document custom nodes** - Explain what they do and their expected inputs
5. **Use state wisely** - Only store what's needed for downstream nodes

### ❌ Don'ts

1. **Don't mutate external state** - Nodes should be side-effect free (except via state)
2. **Don't throw exceptions** - Use `SpiceResult` for error handling
3. **Don't create tight coupling** - Nodes shouldn't know about each other's internals
4. **Don't store large objects in state** - Can impact checkpoint performance

## Next Steps

- Explore [Graph Middleware](./graph-middleware.md)
- Learn about [Checkpoint & Resume](./graph-checkpoint.md)
- Understand [Graph Validation](./graph-validation.md)

## Related

- [Graph System Overview](./graph-system.md)
- [Error Handling](../error-handling/overview.md)
- [Advanced Context Propagation](../advanced/context-propagation.md)
