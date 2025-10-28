---
sidebar_position: 7
---

# Graph API Reference

Complete API reference for Spice 0.5.0 Graph System - Microsoft Agent Framework inspired orchestration.

## Core Types

### Graph

```kotlin
data class Graph(
    val id: String,
    val nodes: Map<String, Node>,
    val edges: List<Edge>,
    val entryPoint: String,
    val middleware: List<Middleware> = emptyList()
)
```

**Properties:**
- `id` - Unique identifier for the graph
- `nodes` - Map of node ID to Node instance
- `edges` - List of edges connecting nodes
- `entryPoint` - ID of the starting node
- `middleware` - List of middleware to intercept execution

**Example:**
```kotlin
val graph = Graph(
    id = "my-workflow",
    nodes = mapOf(
        "start" to AgentNode("start", myAgent),
        "process" to ToolNode("process", myTool)
    ),
    edges = listOf(
        Edge("start", "process")
    ),
    entryPoint = "start"
)
```

### Node

```kotlin
interface Node {
    val id: String
    suspend fun run(ctx: NodeContext): SpiceResult<NodeResult>
}
```

**Built-in Node Types:**

#### AgentNode
```kotlin
class AgentNode(
    override val id: String,
    val agent: Agent
) : Node
```

Executes a Spice Agent within a graph.

#### ToolNode
```kotlin
class ToolNode(
    override val id: String,
    val tool: Tool,
    val paramMapper: (NodeContext) -> Map<String, Any?>
) : Node
```

Executes a Spice Tool within a graph.

#### OutputNode
```kotlin
class OutputNode(
    override val id: String,
    val selector: (NodeContext) -> Any?
) : Node
```

Selects and returns final output from graph state.

#### HumanNode (HITL)
```kotlin
class HumanNode(
    override val id: String,
    val prompt: String,
    val options: List<HumanOption> = emptyList(),
    val timeout: Duration? = null,
    val validator: ((HumanResponse) -> Boolean)? = null,
    val allowFreeText: Boolean = options.isEmpty()
) : Node
```

Pauses graph execution for human input.

### Edge

```kotlin
data class Edge(
    val from: String,
    val to: String,
    val condition: (NodeResult) -> Boolean = { true }
)
```

**Properties:**
- `from` - Source node ID
- `to` - Destination node ID
- `condition` - Predicate to determine if edge should be followed

**Example:**
```kotlin
// Unconditional edge
Edge(from = "agent1", to = "agent2")

// Conditional edge
Edge(
    from = "decision",
    to = "approved"
) { result ->
    (result.data as? HumanResponse)?.selectedOption == "approve"
}
```

### NodeContext

**Added in:** 0.5.0  
**Breaking Change in:** 0.6.0

```kotlin
data class NodeContext(
    val graphId: String,
    val state: PersistentMap<String, Any?>,  // Immutable!
    val context: ExecutionContext             // Unified context
)
```

**Properties:**
- `graphId` - ID of the graph being executed
- `state` - Immutable state (use `withState` to modify)
- `context` - Unified execution context (tenant, user, custom metadata)

**Usage:**
```kotlin
class MyNode : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        // Read from state
        val previousResult = ctx.state["previous-node"]

        // Read context (type-safe accessors)
        val tenantId = ctx.context.tenantId
        val userId = ctx.context.userId
        val customValue = ctx.context.get("customKey")

        // Return result with metadata (state updates via metadata)
        return SpiceResult.success(
            NodeResult.fromContext(
                ctx,
                data = "result",
                additional = mapOf("myKey" to "value")
            )
        )
    }
}
```

**Factory & Builders:**
```kotlin
// Create NodeContext
val ctx = NodeContext.create(
    graphId = "graph-id",
    state = mapOf("input" to "data"),
    context = ExecutionContext.of(mapOf("tenantId" to "tenant-123"))
)

// Update state (returns new NodeContext)
val updated = ctx.withState("key", "value")

// Update context
val enriched = ctx.withContext(newExecutionContext)
```

### NodeResult

**Breaking Change in:** 0.6.0

```kotlin
// Constructor is now private - use factories!
data class NodeResult private constructor(
    val data: Any?,
    val metadata: Map<String, Any>,
    val nextEdges: List<String> = emptyList()
)
```

**Factory Methods:**
```kotlin
// Preferred: preserve context metadata
NodeResult.fromContext(
    ctx = ctx,
    data = result,
    additional = mapOf("key" to "value")
)

// Explicit metadata
NodeResult.create(
    data = result,
    metadata = mapOf("key" to "value")
)
```

**Properties:**
- `data` - Result data from node execution
- `metadata` - Execution metadata (propagated to next node)
- `nextEdges` - Optional edge IDs to follow

**Size Policies:**
```kotlin
// Default: warn at 5KB, no hard limit
NodeResult.METADATA_WARN_THRESHOLD  // 5000
NodeResult.HARD_LIMIT = 10_000      // Optional hard limit
NodeResult.onOverflow = NodeResult.OverflowPolicy.WARN  // WARN | FAIL | IGNORE
```

## Graph Execution

### GraphRunner

```kotlin
interface GraphRunner {
    suspend fun run(
        graph: Graph,
        input: Map<String, Any?>
    ): SpiceResult<RunReport>

    suspend fun runWithCheckpoint(
        graph: Graph,
        input: Map<String, Any?>,
        store: CheckpointStore,
        config: CheckpointConfig = CheckpointConfig()
    ): SpiceResult<RunReport>

    suspend fun resume(
        graph: Graph,
        checkpointId: String,
        store: CheckpointStore,
        config: CheckpointConfig = CheckpointConfig()
    ): SpiceResult<RunReport>

    suspend fun resumeWithHumanResponse(
        graph: Graph,
        checkpointId: String,
        response: HumanResponse,
        store: CheckpointStore
    ): SpiceResult<RunReport>

    suspend fun getPendingInteractions(
        checkpointId: String,
        store: CheckpointStore
    ): SpiceResult<List<HumanInteraction>>
}
```

**Default Implementation:** `DefaultGraphRunner`

**Example:**
```kotlin
val runner = DefaultGraphRunner()

// Basic execution
val result = runner.run(
    graph = myGraph,
    input = mapOf("key" to "value")
).getOrThrow()

// With checkpointing
val checkpointStore = InMemoryCheckpointStore()
val result = runner.runWithCheckpoint(
    graph = myGraph,
    input = mapOf("key" to "value"),
    store = checkpointStore,
    config = CheckpointConfig(saveEveryNNodes = 5)
).getOrThrow()

// Resume from checkpoint
val resumed = runner.resume(
    graph = myGraph,
    checkpointId = "checkpoint-id",
    store = checkpointStore
).getOrThrow()
```

### RunReport

```kotlin
data class RunReport(
    val graphId: String,
    val status: RunStatus,
    val result: Any?,
    val duration: Duration,
    val nodeReports: List<NodeReport>,
    val error: Throwable? = null,
    val checkpointId: String? = null
)
```

**Properties:**
- `graphId` - ID of executed graph
- `status` - Execution status (SUCCESS, FAILED, CANCELLED, PAUSED)
- `result` - Final result from graph
- `duration` - Total execution time
- `nodeReports` - List of individual node execution reports
- `error` - Exception if failed
- `checkpointId` - Checkpoint ID if paused (HITL)

### RunStatus

```kotlin
enum class RunStatus {
    SUCCESS,    // Completed successfully
    FAILED,     // Failed with error
    CANCELLED,  // Cancelled by user
    PAUSED      // Paused for human input (HITL)
}
```

### NodeReport

```kotlin
data class NodeReport(
    val nodeId: String,
    val startTime: Instant,
    val duration: Duration,
    val status: NodeStatus,
    val output: Any?
)
```

**NodeStatus:**
```kotlin
enum class NodeStatus {
    SUCCESS,  // Executed successfully
    FAILED,   // Failed with error
    SKIPPED   // Skipped due to middleware
}
```

## Checkpointing

### Checkpoint

```kotlin
data class Checkpoint(
    val id: String,
    val runId: String,
    val graphId: String,
    val currentNodeId: String,
    val state: Map<String, Any?>,
    val agentContext: AgentContext? = null,
    val timestamp: Instant = Instant.now(),
    val metadata: Map<String, Any> = emptyMap(),
    val executionState: GraphExecutionState = GraphExecutionState.RUNNING,
    val pendingInteraction: HumanInteraction? = null,
    val humanResponse: HumanResponse? = null
)
```

**Properties:**
- `id` - Unique checkpoint ID
- `runId` - ID of graph execution run
- `graphId` - ID of graph
- `currentNodeId` - Node where checkpoint was created
- `state` - Snapshot of graph state
- `agentContext` - Multi-tenant context
- `timestamp` - When checkpoint was created
- `executionState` - Graph state (RUNNING, WAITING_FOR_HUMAN, etc.)
- `pendingInteraction` - Human interaction if paused
- `humanResponse` - Human's response if resuming

### CheckpointStore

```kotlin
interface CheckpointStore {
    suspend fun save(checkpoint: Checkpoint): SpiceResult<String>
    suspend fun load(checkpointId: String): SpiceResult<Checkpoint>
    suspend fun delete(checkpointId: String): SpiceResult<Unit>
    suspend fun listByRun(runId: String): SpiceResult<List<Checkpoint>>
    suspend fun deleteByRun(runId: String): SpiceResult<Unit>
}
```

**Built-in Implementations:**
- `InMemoryCheckpointStore` - For development/testing
- Custom stores can be implemented for production

### CheckpointConfig

```kotlin
data class CheckpointConfig(
    val saveEveryNNodes: Int? = null,
    val saveEveryNSeconds: Long? = null,
    val maxCheckpointsPerRun: Int = 10,
    val saveOnError: Boolean = true
)
```

**Example:**
```kotlin
// Save checkpoint every 5 nodes
CheckpointConfig(saveEveryNNodes = 5)

// Save checkpoint every 60 seconds
CheckpointConfig(saveEveryNSeconds = 60)

// Combine both
CheckpointConfig(
    saveEveryNNodes = 10,
    saveEveryNSeconds = 120,
    maxCheckpointsPerRun = 20
)
```

## Middleware

### Middleware

```kotlin
interface Middleware {
    suspend fun onStart(
        ctx: RunContext,
        next: suspend () -> Unit
    ) { next() }

    suspend fun onNode(
        req: NodeRequest,
        next: suspend (NodeRequest) -> SpiceResult<NodeResult>
    ): SpiceResult<NodeResult> = next(req)

    suspend fun onError(
        error: Throwable,
        ctx: RunContext
    ): ErrorAction = ErrorAction.PROPAGATE

    suspend fun onFinish(report: RunReport) { }
}
```

**Lifecycle Hooks:**
- `onStart` - Called before graph execution starts
- `onNode` - Called for each node execution (can modify request/result)
- `onError` - Called when node execution fails
- `onFinish` - Called after graph execution completes

### ErrorAction

```kotlin
sealed class ErrorAction {
    data object PROPAGATE : ErrorAction()  // Throw error, fail graph
    data object RETRY : ErrorAction()       // Retry failed node
    data object SKIP : ErrorAction()        // Skip failed node, continue
    data class CONTINUE(val result: Any?) : ErrorAction()  // Use fallback result
}
```

**Example:**
```kotlin
class RetryMiddleware : Middleware {
    override suspend fun onError(
        error: Throwable,
        ctx: RunContext
    ): ErrorAction {
        return if (error is TemporaryException) {
            ErrorAction.RETRY
        } else {
            ErrorAction.PROPAGATE
        }
    }
}
```

### RunContext

```kotlin
data class RunContext(
    val graphId: String,
    val runId: String,
    val agentContext: AgentContext? = null
)
```

### NodeRequest

```kotlin
data class NodeRequest(
    val nodeId: String,
    val input: Any?,
    val context: RunContext
)
```

## HITL (Human-in-the-Loop)

### HumanResponse

```kotlin
data class HumanResponse(
    val nodeId: String,
    val selectedOption: String? = null,
    val text: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val timestamp: String = Instant.now().toString()
) {
    companion object {
        fun choice(nodeId: String, optionId: String): HumanResponse
        fun text(nodeId: String, text: String): HumanResponse
    }
}
```

**Example:**
```kotlin
// Multiple choice
val response = HumanResponse.choice(
    nodeId = "review",
    optionId = "approve"
)

// Free text
val response = HumanResponse.text(
    nodeId = "feedback",
    text = "Please add more examples"
)
```

### HumanInteraction

```kotlin
data class HumanInteraction(
    val nodeId: String,
    val prompt: String,
    val options: List<HumanOption>,
    val pausedAt: String,
    val expiresAt: String? = null,
    val allowFreeText: Boolean = false
)
```

### HumanOption

```kotlin
data class HumanOption(
    val id: String,
    val label: String,
    val description: String? = null
)
```

### GraphExecutionState

```kotlin
enum class GraphExecutionState {
    RUNNING,           // Normal execution
    WAITING_FOR_HUMAN, // Paused for human input
    COMPLETED,         // Completed successfully
    FAILED,            // Failed with error
    CANCELLED          // Cancelled
}
```

## DSL

### graph()

```kotlin
fun graph(id: String, block: GraphBuilder.() -> Unit): Graph
```

**Example:**
```kotlin
val myGraph = graph("my-workflow") {
    agent("step1", myAgent)
    tool("step2", myTool)
    humanNode("review", "Please review")
    output("final") { ctx -> ctx.state["result"] }
}
```

### GraphBuilder

```kotlin
class GraphBuilder(val id: String) {
    fun agent(id: String, agent: Agent)

    fun tool(
        id: String,
        tool: Tool,
        paramMapper: (NodeContext) -> Map<String, Any?> = { it.state }
    )

    fun humanNode(
        id: String,
        prompt: String,
        options: List<HumanOption> = emptyList(),
        timeout: Duration? = null,
        validator: ((HumanResponse) -> Boolean)? = null
    )

    fun output(
        id: String = "output",
        selector: (NodeContext) -> Any? = { it.state["result"] }
    )

    fun edge(
        from: String,
        to: String,
        condition: (NodeResult) -> Boolean = { true }
    )

    fun middleware(middleware: Middleware)

    fun build(): Graph
}
```

**Example:**
```kotlin
val graph = graph("approval-flow") {
    agent("draft", draftAgent)

    humanNode(
        id = "review",
        prompt = "Approve or reject?",
        options = listOf(
            HumanOption("approve", "Approve"),
            HumanOption("reject", "Reject")
        )
    )

    edge("review", "publish") { result ->
        (result.data as? HumanResponse)?.selectedOption == "approve"
    }

    agent("publish", publishAgent)

    middleware(LoggingMiddleware())
    middleware(MetricsMiddleware())
}
```

## Validation

### GraphValidator

```kotlin
object GraphValidator {
    fun validate(graph: Graph): SpiceResult<Unit>
    fun findCycles(graph: Graph): List<List<String>>
    fun findUnreachableNodes(graph: Graph): Set<String>
    fun findTerminalNodes(graph: Graph): Set<String>
}
```

**Example:**
```kotlin
val graph = graph("my-graph") {
    // ... build graph
}

// Validate before execution
GraphValidator.validate(graph).getOrThrow()

// Or check specific issues
val cycles = GraphValidator.findCycles(graph)
if (cycles.isNotEmpty()) {
    println("Found cycles: $cycles")
}
```

## Built-in Middleware

### LoggingMiddleware

```kotlin
class LoggingMiddleware : Middleware {
    override suspend fun onStart(ctx: RunContext, next: suspend () -> Unit)
    override suspend fun onNode(req: NodeRequest, next: suspend (NodeRequest) -> SpiceResult<NodeResult>): SpiceResult<NodeResult>
    override suspend fun onFinish(report: RunReport)
}
```

Logs graph execution events.

### MetricsMiddleware

```kotlin
class MetricsMiddleware : Middleware {
    fun getNodeMetrics(nodeId: String): NodeMetrics
    fun getGraphMetrics(): GraphMetrics
}

data class NodeMetrics(
    val executionCount: Int,
    val averageExecutionTime: Long,
    val minExecutionTime: Long,
    val maxExecutionTime: Long
)
```

Collects execution metrics.

## See Also

- [Graph System Guide](/docs/orchestration/graph-system)
- [Graph Nodes](/docs/orchestration/graph-nodes)
- [Graph Middleware](/docs/orchestration/graph-middleware)
- [Graph Checkpointing](/docs/orchestration/graph-checkpoint)
- [Graph HITL](/docs/orchestration/graph-hitl)
- [Agent API](/docs/api/agent)
- [Tool API](/docs/api/tool)
