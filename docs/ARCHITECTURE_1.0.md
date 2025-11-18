# 🏗️ Spice Framework 1.0.0 - Architecture Overview

Comprehensive architectural guide to Spice Framework 1.0.0's design, patterns, and data flow.

---

## Table of Contents

- [Core Philosophy](#core-philosophy)
- [Architecture Diagrams](#architecture-diagrams)
- [SpiceMessage: The Foundation](#spicemessage-the-foundation)
- [Component Architecture](#component-architecture)
- [Data Flow Patterns](#data-flow-patterns)
- [State Machine](#state-machine)
- [Graph Execution](#graph-execution)
- [Design Patterns](#design-patterns)
- [Comparison with 0.x](#comparison-with-0x)

---

## Core Philosophy

### Single Message Type Architecture

Spice 1.0.0 introduces a **revolutionary unified message system**:

```
❌ Before (0.x):                 ✅ After (1.0.0):
┌──────────────────┐             ┌──────────────────┐
│ Comm             │             │                  │
│   ↓              │             │  SpiceMessage    │
│ Agent            │      →      │      ↓  ↑        │
│   ↓              │             │  Everything      │
│ NodeContext      │             │                  │
│   ↓              │             └──────────────────┘
│ NodeResult       │
└──────────────────┘
```

**Benefits:**
- ✅ Single source of truth for all communication
- ✅ No conversion overhead between message types
- ✅ Consistent API across agents, nodes, and tools
- ✅ Built-in state management
- ✅ Automatic metadata propagation

### Design Principles

1. **Progressive Disclosure** - Simple things simple, complex things possible
2. **Type Safety** - Leverage Kotlin's type system for compile-time guarantees
3. **Coroutine-First** - Async operations as first-class citizens
4. **Immutability** - Data classes with functional transformations
5. **Composability** - Small, focused components that combine elegantly

---

## Architecture Diagrams

### High-Level System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Your Application                          │
│                    (Spring Boot / Standalone)                    │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Graph DSL Layer                             │
│  graph { agent("id", agent); tool("id", tool); edge(...) }      │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Execution Layer                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ GraphRunner  │  │ AgentNode    │  │ ToolNode     │          │
│  │  (Executor)  │  │  (Wrapper)   │  │  (Wrapper)   │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Core Components                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ SpiceMessage │  │ Agent        │  │ Tool         │          │
│  │  (Message)   │  │  (Logic)     │  │  (Function)  │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Infrastructure Layer                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ Checkpoints  │  │ Event Bus    │  │ Observability│          │
│  │  (Redis)     │  │  (Kafka)     │  │  (OpenTel)   │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘
```

### SpiceMessage Flow Through Graph

```
User Input
    │
    ▼
SpiceMessage.create("input", "user")
    │ state = READY
    ▼
GraphRunner.execute(graph, message)
    │
    ├─→ [Node 1: AgentNode]
    │   │ state = RUNNING
    │   ├─→ Agent.processMessage(message)
    │   │   └─→ Returns SpiceMessage (processed)
    │   └─→ AgentNode.run(message)
    │       └─→ Returns SpiceMessage
    │
    ├─→ [Node 2: ToolNode]
    │   │ state = RUNNING
    │   ├─→ Tool.execute(params, context)
    │   │   └─→ Returns ToolResult
    │   └─→ ToolNode.run(message)
    │       └─→ SpiceMessage.withData("tool_result", result)
    │
    ├─→ [Node 3: HumanNode]
    │   │ state = WAITING ⏸️
    │   ├─→ Save checkpoint
    │   └─→ Return to user for input
    │
    │   [User provides response]
    │   │
    │   └─→ GraphRunner.resume(checkpointId, userResponse)
    │       │ state = RUNNING
    │       └─→ Continue execution...
    │
    └─→ [Final: OutputNode]
        │ state = COMPLETED ✅
        └─→ Extract final result
            └─→ Return to application
```

### State Transition Diagram

```
                    ┌──────────┐
                    │  READY   │ (Initial state)
                    └────┬─────┘
                         │
                         │ GraphRunner.execute()
                         ▼
                    ┌──────────┐
              ┌─────┤ RUNNING  │────┐
              │     └──────────┘    │
              │                     │
    Success   │                     │ Error / Exception
              │                     │
              ▼                     ▼
        ┌──────────┐          ┌──────────┐
        │COMPLETED │          │  FAILED  │ (Terminal states)
        └──────────┘          └──────────┘

    HumanNode
        │
        │
        ▼
    ┌──────────┐
    │ WAITING  │ (Checkpoint saved)
    └────┬─────┘
         │
         │ User provides input
         │ GraphRunner.resume()
         ▼
    ┌──────────┐
    │ RUNNING  │ (Continue execution)
    └──────────┘
```

---

## SpiceMessage: The Foundation

### Message Structure

```kotlin
@Serializable
data class SpiceMessage(
    // ===== Identification =====
    val id: String,                          // msg_{uuid}
    val correlationId: String,               // Groups related messages
    val causationId: String?,                // Parent message ID

    // ===== Content =====
    val content: String,                     // Human-readable text
    val data: Map<String, Any>,              // Structured data
    val toolCalls: List<OAIToolCall>,        // Tool specifications

    // ===== Execution State =====
    val state: ExecutionState,               // State machine
    val stateHistory: List<StateTransition>, // Audit trail
    val metadata: Map<String, Any>,          // Context metadata

    // ===== Graph Context =====
    val graphId: String?,                    // Current graph
    val nodeId: String?,                     // Current node
    val runId: String?,                      // Execution run

    // ===== Actors =====
    val from: String,                        // Sender
    val to: String?,                         // Recipient

    // ===== Timing =====
    val timestamp: Instant,                  // Created at
    val expiresAt: Instant?                  // Expires at (TTL)
)
```

### Message Lifecycle

```
1. Creation
   SpiceMessage.create("content", "user")
   │
   ├─→ Generates unique ID (msg_{uuid})
   ├─→ Sets correlationId (for grouping)
   ├─→ Sets state = READY
   └─→ Records timestamp

2. Processing
   message.transitionTo(ExecutionState.RUNNING, "Processing")
   │
   ├─→ Updates state
   ├─→ Appends to stateHistory
   └─→ Returns new message (immutable)

3. Data Flow
   message.withData(mapOf("key" to "value"))
   │
   ├─→ Merges with existing data
   └─→ Returns new message

4. Tool Calls
   message.withToolCall(OAIToolCall.selection(...))
   │
   ├─→ Appends to toolCalls list
   └─→ Returns new message

5. Completion
   message.transitionTo(ExecutionState.COMPLETED, "Done")
   │
   ├─→ Updates state to terminal state
   └─→ Returns final message
```

### Key Helper Methods

```kotlin
// Factory methods
SpiceMessage.create(content: String, from: String): SpiceMessage
SpiceMessage.fromUserInput(input: String, userId: String?): SpiceMessage

// Transformation methods
message.reply(content: String, from: String): SpiceMessage
message.withData(data: Map<String, Any>): SpiceMessage
message.withMetadata(metadata: Map<String, Any>): SpiceMessage
message.withToolCall(toolCall: OAIToolCall): SpiceMessage
message.withGraphContext(graphId: String, nodeId: String, runId: String): SpiceMessage
message.transitionTo(state: ExecutionState, reason: String): SpiceMessage

// Access methods
message.getData<T>(key: String): T?
message.getMetadata<T>(key: String): T?
message.findToolCall(functionName: String): OAIToolCall?
message.hasToolCalls(): Boolean
message.getToolCallNames(): List<String>
```

---

## Component Architecture

### Agent Interface

```kotlin
interface Agent {
    val id: String
    val name: String
    val description: String
    val capabilities: List<String>

    /**
     * Process a message and return a response
     *
     * Input:  SpiceMessage (typically in RUNNING state)
     * Output: SpiceMessage (may update content, data, toolCalls)
     */
    suspend fun processMessage(message: SpiceMessage): SpiceResult<SpiceMessage>

    /**
     * Process with runtime context (advanced)
     */
    suspend fun processMessage(
        message: SpiceMessage,
        runtime: AgentRuntime
    ): SpiceResult<SpiceMessage> = processMessage(message)

    fun getTools(): List<Tool> = emptyList()
    fun canHandle(message: SpiceMessage): Boolean = true
    fun isReady(): Boolean = true
}
```

**Agent Responsibilities:**
- Process SpiceMessage → SpiceMessage
- Optionally add tool calls
- Update data/metadata
- Handle errors gracefully

### Tool Interface

```kotlin
interface Tool {
    val name: String
    val description: String
    val schema: ToolSchema?

    /**
     * Execute tool with parameters
     */
    suspend fun execute(
        params: Map<String, Any>,
        context: ToolContext
    ): SpiceResult<ToolResult>
}

data class ToolResult(
    val success: Boolean,
    val result: Any?,
    val metadata: Map<String, Any> = emptyMap(),
    val error: String? = null
)

data class ToolContext(
    val userId: String?,
    val tenantId: String?,
    val agentId: String?,
    val additionalContext: Map<String, Any> = emptyMap()
)
```

**Tool Responsibilities:**
- Execute specific functionality
- Return ToolResult (success/failure)
- Maintain idempotency
- Handle errors internally

### Node Interface

```kotlin
interface Node {
    val id: String

    /**
     * Execute node logic
     *
     * Input:  SpiceMessage (typically in RUNNING state)
     * Output: SpiceMessage (may change state, add tool calls, update data)
     */
    suspend fun run(message: SpiceMessage): SpiceResult<SpiceMessage>
}
```

**Built-in Node Types:**

1. **AgentNode** - Wraps Agent, calls `agent.processMessage()`
2. **ToolNode** - Wraps Tool, calls `tool.execute()`, stores result in `message.data["tool_result"]`
3. **HumanNode** - Pauses execution (state = WAITING), waits for user input
4. **DynamicHumanNode** - Runtime-generated prompts
5. **OutputNode** - Extracts final result from message
6. **CustomNode** - User-defined processing logic

---

## Data Flow Patterns

### Pattern 1: Agent → Agent

```kotlin
val graph = graph("agent-chain") {
    agent("step1", agent1)
    agent("step2", agent2)
    edge("step1", "step2")
}

// Flow:
// User → SpiceMessage
//     ↓
// Agent1.processMessage() → SpiceMessage (updated content, data)
//     ↓
// Agent2.processMessage() → SpiceMessage (final result)
```

**Data Access in Agent2:**
```kotlin
class Agent2 : Agent {
    override suspend fun processMessage(message: SpiceMessage) {
        // Access Agent1's output
        val agent1Response = message.content
        val agent1Data = message.getData<String>("agent1_key")

        // Process and return
        return SpiceResult.success(
            message.reply("Processed: $agent1Response", id)
        )
    }
}
```

### Pattern 2: Agent → Tool → Agent

```kotlin
val graph = graph("agent-tool-agent") {
    agent("analyzer", analyzerAgent)
    tool("process", processingTool) { message ->
        // Extract params from analyzer output
        mapOf("data" to message.getData<String>("analysis_result"))
    }
    agent("summarizer", summarizerAgent)

    edge("analyzer", "process")
    edge("process", "summarizer")
}

// Flow:
// Analyzer → SpiceMessage (with data)
//     ↓
// Tool.execute() → ToolResult
//     ↓ (ToolNode stores result in message.data["tool_result"])
// Summarizer → SpiceMessage (accesses tool_result)
```

**Data Access in Summarizer:**
```kotlin
class SummarizerAgent : Agent {
    override suspend fun processMessage(message: SpiceMessage) {
        // Access tool result
        val toolResult = message.getData<Any>("tool_result")

        // Summarize
        return SpiceResult.success(
            message.reply("Summary: ...", id)
        )
    }
}
```

### Pattern 3: Conditional Routing

```kotlin
val graph = graph("conditional") {
    agent("classifier", classifierAgent)
    agent("handler-a", handlerA)
    agent("handler-b", handlerB)
    output("result")

    // Conditional edges
    edge("classifier", "handler-a") { message ->
        message.getData<String>("category") == "A"
    }
    edge("classifier", "handler-b") { message ->
        message.getData<String>("category") == "B"
    }
    edge("handler-a", "result")
    edge("handler-b", "result")
}
```

### Pattern 4: Human-in-the-Loop (HITL)

```kotlin
val graph = graph("hitl-workflow") {
    agent("processor", processorAgent)
    human("confirm", "Do you want to proceed?")
    agent("executor", executorAgent)
    output("result")

    edge("processor", "confirm")
    edge("confirm", "executor")
    edge("executor", "result")
}

// Execution Flow:
// 1. Execute graph → pauses at HumanNode (state = WAITING)
// 2. Save checkpoint (runId, graphId, nodeId)
// 3. Return to user with prompt
// 4. User provides response
// 5. Resume execution with user response
// 6. Continue from checkpoint
```

---

## State Machine

### State Definitions

```kotlin
enum class ExecutionState {
    READY,      // Initial state, ready to execute
    RUNNING,    // Currently being processed
    WAITING,    // Paused, awaiting user input (HITL)
    COMPLETED,  // Successfully finished
    FAILED      // Error occurred
}
```

### State Transition Rules

```
READY → RUNNING
    ✅ Allowed: When GraphRunner starts execution
    ❌ Invalid: Direct to WAITING/COMPLETED/FAILED

RUNNING → WAITING
    ✅ Allowed: When HumanNode is encountered
    ❌ Invalid: Only HumanNode can trigger this

RUNNING → COMPLETED
    ✅ Allowed: When graph execution succeeds
    ❌ Invalid: If errors occurred (should be FAILED)

RUNNING → FAILED
    ✅ Allowed: When error/exception occurs
    ❌ Invalid: If processing successful

WAITING → RUNNING
    ✅ Allowed: When GraphRunner.resume() is called
    ❌ Invalid: Without valid checkpoint and user response

COMPLETED → [any]
    ❌ Invalid: Terminal state, no transitions

FAILED → [any]
    ❌ Invalid: Terminal state, no transitions
```

### State Transition API

```kotlin
// Automatic transition (by GraphRunner)
val message = SpiceMessage.create("input", "user")  // state = READY
val running = runner.execute(graph, message)        // state → RUNNING

// Manual transition (in custom nodes)
val waiting = message.transitionTo(
    ExecutionState.WAITING,
    "User confirmation required"
)

// Access state history
waiting.stateHistory.forEach { transition ->
    println("${transition.from} → ${transition.to}: ${transition.reason}")
}
```

---

## Graph Execution

### Graph Structure

```kotlin
data class Graph(
    val id: String,
    val nodes: Map<String, Node>,        // nodeId → Node
    val edges: List<Edge>,               // Directed edges
    val startNodeId: String,             // Entry point
    val outputNodeId: String             // Exit point
)

data class Edge(
    val from: String,                    // Source node ID
    val to: String,                      // Target node ID
    val condition: (SpiceMessage) -> Boolean = { true },
    val transformer: (SpiceMessage) -> SpiceMessage = { it }
)
```

### Execution Algorithm

```kotlin
// Simplified GraphRunner logic
class DefaultGraphRunner : GraphRunner {
    override suspend fun execute(
        graph: Graph,
        message: SpiceMessage
    ): SpiceResult<SpiceMessage> {
        var currentMessage = message.withGraphContext(
            graphId = graph.id,
            nodeId = graph.startNodeId,
            runId = UUID.randomUUID().toString()
        )

        var currentNodeId = graph.startNodeId

        while (currentNodeId != graph.outputNodeId) {
            // Get current node
            val node = graph.nodes[currentNodeId] ?: return failure("Node not found")

            // Execute node
            currentMessage = currentMessage.transitionTo(ExecutionState.RUNNING, "Processing $currentNodeId")
            val result = node.run(currentMessage)

            when (result) {
                is SpiceResult.Success -> {
                    currentMessage = result.value

                    // Check for HITL pause
                    if (currentMessage.state == ExecutionState.WAITING) {
                        return SpiceResult.success(currentMessage)  // Pause and return
                    }
                }
                is SpiceResult.Failure -> {
                    return result
                }
            }

            // Find next node
            val nextEdge = graph.edges.find { edge ->
                edge.from == currentNodeId && edge.condition(currentMessage)
            } ?: return failure("No valid edge found")

            // Apply transformer
            currentMessage = nextEdge.transformer(currentMessage)

            // Move to next node
            currentNodeId = nextEdge.to
        }

        // Execute output node
        val outputNode = graph.nodes[graph.outputNodeId]!!
        val finalResult = outputNode.run(currentMessage)

        return finalResult.map { it.transitionTo(ExecutionState.COMPLETED, "Graph execution completed") }
    }
}
```

### Checkpoint/Resume

```kotlin
// Checkpoint structure
data class Checkpoint(
    val runId: String,
    val graphId: String,
    val currentNodeId: String,
    val message: SpiceMessage,
    val metadata: Map<String, Any>,
    val timestamp: Instant
)

// Resume algorithm
override suspend fun resumeFromCheckpoint(
    graph: Graph,
    checkpointId: String,
    userResponse: SpiceMessage,
    checkpointStore: CheckpointStore
): SpiceResult<SpiceMessage> {
    // Load checkpoint
    val checkpoint = checkpointStore.load(checkpointId).getOrElse {
        return SpiceResult.failure(SpiceError.executionError("Checkpoint not found"))
    }

    // Merge user response with checkpoint context
    val resumeMessage = userResponse.copy(
        graphId = checkpoint.graphId,
        nodeId = checkpoint.currentNodeId,
        runId = checkpoint.runId,
        correlationId = checkpoint.message.correlationId,
        metadata = userResponse.metadata + checkpoint.metadata
    )

    // Find next node after HumanNode
    val nextNodeId = graph.edges.find { it.from == checkpoint.currentNodeId }?.to
        ?: return SpiceResult.failure(SpiceError.executionError("No next node"))

    // Continue execution from next node
    return executeFromNode(graph, nextNodeId, resumeMessage)
}
```

---

## Design Patterns

### 1. Immutable Data Pattern

All message transformations return new instances:

```kotlin
// ❌ Mutable (not allowed)
message.content = "new content"

// ✅ Immutable (functional style)
val newMessage = message.copy(content = "new content")
val withData = message.withData(mapOf("key" to "value"))
```

### 2. Builder Pattern (Graph DSL)

```kotlin
// Fluent API for graph construction
val graph = graph("my-graph") {
    agent("step1", agent1)
    tool("step2", tool1)
    human("step3", "Confirm?")
    output("result") { it.content }

    edge("step1", "step2")
    edge("step2", "step3")
    edge("step3", "result")
}
```

### 3. Wrapper Pattern (Nodes)

Nodes wrap agents and tools with consistent interface:

```kotlin
// AgentNode wraps Agent
class AgentNode(
    override val id: String,
    private val agent: Agent
) : Node {
    override suspend fun run(message: SpiceMessage): SpiceResult<SpiceMessage> {
        return agent.processMessage(message)
    }
}

// ToolNode wraps Tool
class ToolNode(
    override val id: String,
    private val tool: Tool,
    private val paramMapper: (SpiceMessage) -> Map<String, Any>
) : Node {
    override suspend fun run(message: SpiceMessage): SpiceResult<SpiceMessage> {
        val params = paramMapper(message)
        val context = ToolContext(...)
        val result = tool.execute(params, context)

        return result.map { toolResult ->
            message.withData(mapOf("tool_result" to toolResult.result))
        }
    }
}
```

### 4. Result Pattern (Error Handling)

```kotlin
sealed class SpiceResult<out T> {
    data class Success<T>(val value: T) : SpiceResult<T>()
    data class Failure(val error: SpiceError) : SpiceResult<Nothing>()
}

// Usage
val result: SpiceResult<SpiceMessage> = agent.processMessage(message)

when (result) {
    is SpiceResult.Success -> println(result.value.content)
    is SpiceResult.Failure -> println(result.error.message)
}

// Functional operations
result.map { message -> message.withData(...) }
result.flatMap { message -> nextAgent.processMessage(message) }
result.getOrElse { defaultMessage }
```

---

## Comparison with 0.x

### Message Types

| 0.x | 1.0.0 | Notes |
|-----|-------|-------|
| `Comm` | `SpiceMessage` | Single unified type |
| `NodeContext` | ❌ Removed | Data now in `SpiceMessage.data` |
| `NodeResult` | ❌ Removed | Return `SpiceMessage` directly |
| `ExecutionContext` | ❌ Removed | Context in `SpiceMessage.metadata` |

### Agent Implementation

```kotlin
// ❌ 0.x
class MyAgent : Agent {
    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        return SpiceResult.success(comm.reply("Response", id))
    }
}

// ✅ 1.0.0
class MyAgent : Agent {
    override val id = "my-agent"
    override val name = "My Agent"
    override val description = "..."
    override val capabilities = listOf("chat")

    override suspend fun processMessage(message: SpiceMessage): SpiceResult<SpiceMessage> {
        return SpiceResult.success(message.reply("Response", id))
    }
}
```

### Node Implementation

```kotlin
// ❌ 0.x
class MyNode : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        val data = ctx.state["key"]
        return SpiceResult.success(NodeResult.fromContext(ctx, data = "result"))
    }
}

// ✅ 1.0.0
class MyNode : Node {
    override val id = "my-node"

    override suspend fun run(message: SpiceMessage): SpiceResult<SpiceMessage> {
        val data = message.getData<String>("key")
        return SpiceResult.success(message.copy(content = "result"))
    }
}
```

### Data Access

```kotlin
// ❌ 0.x
val previousComm = ctx.state["_previousComm"] as? Comm
val data = ctx.context.get("key")
val userId = ctx.context.userId

// ✅ 1.0.0
val data = message.getData<String>("key")
val userId = message.getMetadata<String>("userId")
val previousContent = message.content  // Direct access
```

---

## Performance Characteristics

### Memory Profile

- **SpiceMessage**: ~1-5 KB per message (depends on data size)
- **Checkpoint**: ~5-10 KB per checkpoint (includes full message)
- **Graph**: ~0.1-1 KB per node/edge

### Execution Performance

- **Message Creation**: < 1 ms
- **State Transition**: < 0.1 ms
- **Node Execution**: Depends on agent/tool logic
- **Graph Traversal**: < 1 ms per node
- **Checkpoint Save/Load**: 1-5 ms (Redis), 0.1 ms (in-memory)

### Scalability

- **Concurrent Executions**: Thousands (limited by agent/tool resources)
- **Message Throughput**: 10,000+ messages/sec (core framework)
- **Graph Size**: Tested with 100+ nodes
- **Checkpoint Retention**: Millions (with proper TTL)

---

## Next Steps

- **[Quick Start Guide](QUICK_START_1.0.md)** - Build your first application
- **[Migration Guide](MIGRATION_0.x_TO_1.0.md)** - Upgrade from 0.x
- **[API Reference](wiki/api-reference.md)** - Detailed API documentation
- **[Best Practices](wiki/best-practices.md)** - Production patterns

---

**Master the architecture, build amazing AI systems! 🌶️**
