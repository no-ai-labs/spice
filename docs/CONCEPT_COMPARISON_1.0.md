# 📊 Spice Framework 1.0.0 - Concept Comparison

Complete reference comparing Spice 0.x and 1.0.0 concepts, APIs, and patterns.

---

## Quick Reference Tables

### Core Types

| Concept | 0.x | 1.0.0 | Status |
|---------|-----|-------|--------|
| **Message Type** | `Comm` | `SpiceMessage` | ✅ Unified |
| **Node Input** | `NodeContext` | `SpiceMessage` | ✅ Simplified |
| **Node Output** | `NodeResult` | `SpiceMessage` | ✅ Unified |
| **Execution Context** | `ExecutionContext` | `SpiceMessage.metadata` | ✅ Integrated |
| **Message Hub** | `CommHub` | ❌ Removed (use Graph) | ⚠️ Breaking |
| **Message Type Enum** | `CommType` | ❌ Removed | ⚠️ Breaking |
| **Message Role Enum** | `CommRole` | ❌ Removed | ⚠️ Breaking |

### Interfaces

| Interface | 0.x Method | 1.0.0 Method | Change |
|-----------|------------|--------------|--------|
| **Agent** | `processComm(Comm)` | `processMessage(SpiceMessage)` | ⚠️ Signature changed |
| **Agent** | - | `val name: String` | ✅ New property |
| **Agent** | - | `val description: String` | ✅ New property |
| **Agent** | - | `val capabilities: List<String>` | ✅ New property |
| **Node** | `run(NodeContext)` | `run(SpiceMessage)` | ⚠️ Signature changed |
| **Tool** | `execute(Map<String, Any>)` | `execute(Map<String, Any>, ToolContext)` | ✅ Context added |
| **Graph** | `Flow` interface | `Graph` data class | ⚠️ Complete redesign |

### State Management

| Feature | 0.x | 1.0.0 | Notes |
|---------|-----|-------|-------|
| **Execution State** | Manual (in NodeContext.state) | `ExecutionState` enum | ✅ Built-in |
| **State Transitions** | Manual tracking | `SpiceMessage.transitionTo()` | ✅ Type-safe |
| **State History** | Not built-in | `SpiceMessage.stateHistory` | ✅ Audit trail |
| **Valid States** | Custom | READY/RUNNING/WAITING/COMPLETED/FAILED | ✅ Standardized |

### Data Flow

| Pattern | 0.x | 1.0.0 | Improvement |
|---------|-----|-------|-------------|
| **Message Data** | `Comm.data` | `SpiceMessage.data` | ✅ Same |
| **Metadata** | Manual in `Comm.data` | `SpiceMessage.metadata` | ✅ Separated |
| **Previous Message** | `ctx.state["_previousComm"]` | Direct access to `message.content` | ✅ Simpler |
| **Tool Results** | Manual storage | `message.data["tool_result"]` | ✅ Standardized |
| **Tool Calls** | Custom format | `OAIToolCall` (OpenAI compatible) | ✅ Standard |

---

## Detailed Comparisons

### 1. Message Creation

#### Simple Message

<table>
<tr><th>0.x</th><th>1.0.0</th></tr>
<tr>
<td>

```kotlin
val comm = Comm(
    content = "Hello",
    from = "user"
)
```

</td>
<td>

```kotlin
val message = SpiceMessage.create(
    content = "Hello",
    from = "user"
)
```

</td>
</tr>
</table>

**Changes:**
- ✅ Factory method `create()` provides better defaults
- ✅ Auto-generates ID, correlationId, timestamp

#### Message with Data

<table>
<tr><th>0.x</th><th>1.0.0</th></tr>
<tr>
<td>

```kotlin
val comm = Comm(
    content = "Process",
    from = "user",
    data = mapOf(
        "key" to "value",
        "userId" to "123"
    )
)
```

</td>
<td>

```kotlin
val message = SpiceMessage.create("Process", "user")
    .withData(mapOf("key" to "value"))
    .withMetadata(mapOf("userId" to "123"))
```

</td>
</tr>
</table>

**Changes:**
- ✅ Separates business data from metadata
- ✅ Fluent API with chaining
- ✅ Type-safe accessors (`getData<T>()`, `getMetadata<T>()`)

#### Message Reply

<table>
<tr><th>0.x</th><th>1.0.0</th></tr>
<tr>
<td>

```kotlin
val response = comm.reply(
    "Response",
    agentId
)
```

</td>
<td>

```kotlin
val response = message.reply(
    "Response",
    agentId
)
```

</td>
</tr>
</table>

**Changes:**
- ✅ Same API (no changes needed)

---

### 2. Agent Implementation

#### Minimal Agent

<table>
<tr><th>0.x</th><th>1.0.0</th></tr>
<tr>
<td>

```kotlin
class EchoAgent : Agent {
    override val id = "echo"

    override suspend fun processComm(
        comm: Comm
    ): SpiceResult<Comm> {
        return SpiceResult.success(
            comm.reply(
                "Echo: ${comm.content}",
                id
            )
        )
    }
}
```

</td>
<td>

```kotlin
class EchoAgent : Agent {
    override val id = "echo"
    override val name = "Echo Agent"
    override val description = "Echoes input"
    override val capabilities = listOf("chat")

    override suspend fun processMessage(
        message: SpiceMessage
    ): SpiceResult<SpiceMessage> {
        return SpiceResult.success(
            message.reply(
                "Echo: ${message.content}",
                id
            )
        )
    }
}
```

</td>
</tr>
</table>

**Changes:**
- ⚠️ Method renamed: `processComm()` → `processMessage()`
- ⚠️ Parameter type: `Comm` → `SpiceMessage`
- ✅ New required properties: `name`, `description`, `capabilities`

#### Agent with Data Processing

<table>
<tr><th>0.x</th><th>1.0.0</th></tr>
<tr>
<td>

```kotlin
class DataAgent : Agent {
    override val id = "data"

    override suspend fun processComm(
        comm: Comm
    ): SpiceResult<Comm> {
        val input = comm.data["input"] as? String

        val result = process(input)

        return SpiceResult.success(
            comm.reply(result, id)
                .copy(data = mapOf(
                    "processed" to true
                ))
        )
    }
}
```

</td>
<td>

```kotlin
class DataAgent : Agent {
    override val id = "data"
    override val name = "Data Agent"
    override val description = "Processes data"
    override val capabilities = listOf("processing")

    override suspend fun processMessage(
        message: SpiceMessage
    ): SpiceResult<SpiceMessage> {
        val input = message.getData<String>("input")

        val result = process(input)

        return SpiceResult.success(
            message.reply(result, id)
                .withData(mapOf(
                    "processed" to true
                ))
        )
    }
}
```

</td>
</tr>
</table>

**Changes:**
- ✅ Type-safe data access: `getData<T>("key")`
- ✅ Fluent API: `withData()` instead of `copy(data = ...)`

#### Agent with Runtime Context

<table>
<tr><th>0.x</th><th>1.0.0</th></tr>
<tr>
<td>

```kotlin
class ContextAgent : Agent {
    override suspend fun processComm(
        comm: Comm,
        runtime: AgentRuntime
    ): SpiceResult<Comm> {
        val userId = runtime.context.userId

        // Process...
    }
}
```

</td>
<td>

```kotlin
class ContextAgent : Agent {
    override val id = "context"
    override val name = "Context Agent"
    override val description = "Uses context"
    override val capabilities = listOf("contextual")

    override suspend fun processMessage(
        message: SpiceMessage
    ): SpiceResult<SpiceMessage> {
        val userId = message.getMetadata<String>("userId")

        // Process...
    }

    // Optional: Advanced usage
    override suspend fun processMessage(
        message: SpiceMessage,
        runtime: AgentRuntime
    ): SpiceResult<SpiceMessage> {
        // Access runtime context
    }
}
```

</td>
</tr>
</table>

**Changes:**
- ✅ Metadata replaces runtime context for most use cases
- ✅ Optional runtime parameter for advanced scenarios

---

### 3. Custom Node Implementation

#### Basic Node

<table>
<tr><th>0.x</th><th>1.0.0</th></tr>
<tr>
<td>

```kotlin
class TransformNode : Node {
    override val id = "transform"

    override suspend fun run(
        ctx: NodeContext
    ): SpiceResult<NodeResult> {
        val previousComm = ctx.state["_previousComm"] as? Comm
        val input = previousComm?.content ?: ""

        val result = transform(input)

        return SpiceResult.success(
            NodeResult.fromContext(
                ctx,
                data = result
            )
        )
    }
}
```

</td>
<td>

```kotlin
class TransformNode : Node {
    override val id = "transform"

    override suspend fun run(
        message: SpiceMessage
    ): SpiceResult<SpiceMessage> {
        val input = message.content

        val result = transform(input)

        return SpiceResult.success(
            message.copy(content = result)
        )
    }
}
```

</td>
</tr>
</table>

**Changes:**
- ⚠️ No more `NodeContext` - use `SpiceMessage` directly
- ⚠️ No more `NodeResult` - return `SpiceMessage`
- ✅ Direct access to message content
- ✅ Simpler, cleaner code

#### Node with State Access

<table>
<tr><th>0.x</th><th>1.0.0</th></tr>
<tr>
<td>

```kotlin
class StateNode : Node {
    override suspend fun run(
        ctx: NodeContext
    ): SpiceResult<NodeResult> {
        val previousData = ctx.state["key"] as? String
        val contextUser = ctx.context.userId

        return NodeResult.fromContext(
            ctx,
            data = "Processed",
            additionalState = mapOf(
                "newKey" to "newValue"
            )
        )
    }
}
```

</td>
<td>

```kotlin
class StateNode : Node {
    override val id = "state"

    override suspend fun run(
        message: SpiceMessage
    ): SpiceResult<SpiceMessage> {
        val previousData = message.getData<String>("key")
        val userId = message.getMetadata<String>("userId")

        return SpiceResult.success(
            message.copy(
                content = "Processed",
                data = message.data + mapOf(
                    "newKey" to "newValue"
                )
            )
        )
    }
}
```

</td>
</tr>
</table>

**Changes:**
- ✅ Data access: `message.getData<T>("key")`
- ✅ Metadata access: `message.getMetadata<T>("key")`
- ✅ No conversion between types

---

### 4. Tool Implementation

#### Basic Tool

<table>
<tr><th>0.x</th><th>1.0.0</th></tr>
<tr>
<td>

```kotlin
class CalculatorTool : Tool {
    override val name = "calculator"
    override val description = "Math operations"

    override suspend fun execute(
        params: Map<String, Any>
    ): SpiceResult<ToolResult> {
        val a = params["a"] as? Int ?: 0
        val b = params["b"] as? Int ?: 0

        return SpiceResult.success(
            ToolResult(
                success = true,
                result = a + b
            )
        )
    }
}
```

</td>
<td>

```kotlin
class CalculatorTool : Tool {
    override val name = "calculator"
    override val description = "Math operations"
    override val schema = ToolSchema(
        name = name,
        description = description,
        parameters = mapOf(
            "a" to ParameterSchema("number", "First", true),
            "b" to ParameterSchema("number", "Second", true)
        )
    )

    override suspend fun execute(
        params: Map<String, Any>,
        context: ToolContext
    ): SpiceResult<ToolResult> {
        val a = (params["a"] as? Number)?.toInt() ?: 0
        val b = (params["b"] as? Number)?.toInt() ?: 0

        return SpiceResult.success(
            ToolResult(
                success = true,
                result = a + b,
                metadata = mapOf(
                    "userId" to (context.userId ?: "unknown")
                )
            )
        )
    }
}
```

</td>
</tr>
</table>

**Changes:**
- ✅ Added `ToolContext` parameter for metadata access
- ✅ Added `schema` property for OpenAI compatibility
- ✅ Better metadata support in `ToolResult`

---

### 5. Graph/Flow Construction

#### Simple Linear Flow

<table>
<tr><th>0.x</th><th>1.0.0</th></tr>
<tr>
<td>

```kotlin
val flow = buildFlow {
    id = "my-flow"
    name = "My Flow"

    step("step1", "agent1-id")
    step("step2", "agent2-id")
    step("step3", "agent3-id")
}

AgentRegistry.register(agent1)
AgentRegistry.register(agent2)
AgentRegistry.register(agent3)

val result = flow.execute(initialComm)
```

</td>
<td>

```kotlin
val graph = graph("my-workflow") {
    agent("step1", agent1)
    agent("step2", agent2)
    agent("step3", agent3)
    output("result") { message ->
        message.content
    }

    edge("step1", "step2")
    edge("step2", "step3")
    edge("step3", "result")
}

val runner = DefaultGraphRunner()
val result = runner.execute(graph, initialMessage)
```

</td>
</tr>
</table>

**Changes:**
- ⚠️ Flow → Graph (complete redesign)
- ✅ Direct agent references (no registry lookup)
- ✅ Explicit edge definitions
- ✅ Explicit output node

#### Conditional Flow

<table>
<tr><th>0.x</th><th>1.0.0</th></tr>
<tr>
<td>

```kotlin
val flow = buildFlow {
    step("classifier") { comm ->
        classifierAgent.processComm(comm)
    }

    step("handler-a") { comm ->
        if (comm.data["category"] == "A") {
            handlerA.processComm(comm)
        } else {
            SpiceResult.success(comm)
        }
    }
}
```

</td>
<td>

```kotlin
val graph = graph("conditional") {
    agent("classifier", classifierAgent)
    agent("handler-a", handlerA)
    agent("handler-b", handlerB)
    output("result")

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

</td>
</tr>
</table>

**Changes:**
- ✅ Explicit conditional edges
- ✅ Cleaner separation of concerns
- ✅ Easier to visualize flow

---

### 6. HITL (Human-in-the-Loop)

#### Checkpoint/Resume

<table>
<tr><th>0.x</th><th>1.0.0</th></tr>
<tr>
<td>

```kotlin
// Manual checkpoint management
suspend fun execute(comm: Comm): HITLResult {
    val step1 = agent1.processComm(comm)

    // Need user input
    val checkpoint = Checkpoint(
        id = UUID.randomUUID().toString(),
        comm = step1.getOrThrow(),
        state = mapOf("step" to 1)
    )

    checkpointStore.save(checkpoint)

    return HITLResult.Paused(
        checkpointId = checkpoint.id,
        prompt = "Confirm?"
    )
}

suspend fun resume(
    checkpointId: String,
    userResponse: String
): Comm {
    val checkpoint = checkpointStore.load(checkpointId)

    val comm = checkpoint.comm
        .copy(content = userResponse)

    return agent2.processComm(comm).getOrThrow()
}
```

</td>
<td>

```kotlin
// Automatic checkpoint management
val graph = graph("hitl") {
    agent("step1", agent1)
    human("confirm", "Confirm?")
    agent("step2", agent2)
    output("result")

    edge("step1", "confirm")
    edge("confirm", "step2")
    edge("step2", "result")
}

val runner = DefaultGraphRunner()
val checkpointStore = RedisCheckpointStore(...)

// Execute
val result = runner.executeWithCheckpoint(
    graph, initialMessage, checkpointStore
)

when (result) {
    is SpiceResult.Success -> {
        val msg = result.value

        if (msg.state == ExecutionState.WAITING) {
            // Automatically paused
            val checkpointId = msg.runId!!

            // ... prompt user ...

            // Resume
            val userResponse = SpiceMessage.create(
                "confirmed",
                "user"
            )

            val result2 = runner.resumeFromCheckpoint(
                graph,
                checkpointId,
                userResponse,
                checkpointStore
            )
        }
    }
}
```

</td>
</tr>
</table>

**Changes:**
- ✅ Built-in checkpoint/resume
- ✅ Automatic state management (ExecutionState.WAITING)
- ✅ HumanNode for HITL points
- ✅ Less boilerplate

---

### 7. Tool Calls (Selection/Confirmation)

#### User Selection

<table>
<tr><th>0.x</th><th>1.0.0</th></tr>
<tr>
<td>

```kotlin
class SelectionAgent : Agent {
    override suspend fun processComm(
        comm: Comm
    ): SpiceResult<Comm> {
        val options = listOf("A", "B", "C")

        return SpiceResult.success(
            comm.reply("Select:", id)
                .copy(data = mapOf(
                    "menu_text" to options.joinToString("\n"),
                    "workflow_message" to "Choose"
                ))
        )
    }
}
```

</td>
<td>

```kotlin
class SelectionAgent : Agent {
    override val id = "selection"
    override val name = "Selection Agent"
    override val description = "User selection"
    override val capabilities = listOf("selection")

    override suspend fun processMessage(
        message: SpiceMessage
    ): SpiceResult<SpiceMessage> {
        return SpiceResult.success(
            message.reply("Select:", id)
                .withToolCall(
                    OAIToolCall.selection(
                        items = listOf(
                            mapOf("id" to "a", "name" to "Option A"),
                            mapOf("id" to "b", "name" to "Option B"),
                            mapOf("id" to "c", "name" to "Option C")
                        ),
                        promptMessage = "Choose an option",
                        selectionType = "single"
                    )
                )
        )
    }
}
```

</td>
</tr>
</table>

**Changes:**
- ✅ Standardized OAIToolCall (OpenAI compatible)
- ✅ Structured item format
- ✅ Better UI integration

---

### 8. Error Handling

#### Agent Error Handling

<table>
<tr><th>0.x</th><th>1.0.0</th></tr>
<tr>
<td>

```kotlin
class SafeAgent : Agent {
    override suspend fun processComm(
        comm: Comm
    ): SpiceResult<Comm> {
        return try {
            val result = riskyOperation(comm)
            SpiceResult.success(
                comm.reply(result, id)
            )
        } catch (e: Exception) {
            SpiceResult.failure(
                SpiceError.executionError(e.message)
            )
        }
    }
}
```

</td>
<td>

```kotlin
class SafeAgent : Agent {
    override val id = "safe"
    override val name = "Safe Agent"
    override val description = "Error handling"
    override val capabilities = listOf("resilient")

    override suspend fun processMessage(
        message: SpiceMessage
    ): SpiceResult<SpiceMessage> {
        return try {
            val result = riskyOperation(message)
            SpiceResult.success(
                message.reply(result, id)
            )
        } catch (e: Exception) {
            // Return as failed message
            SpiceResult.success(
                message.copy(
                    content = "Error: ${e.message}",
                    state = ExecutionState.FAILED,
                    metadata = message.metadata + mapOf(
                        "error" to e.message,
                        "error_type" to e::class.simpleName
                    )
                )
            )
        }
    }
}
```

</td>
</tr>
</table>

**Changes:**
- ✅ Use ExecutionState.FAILED for errors
- ✅ Store error details in metadata
- ✅ Return success with failed message (for flow continuation)

---

### 9. Spring Boot Integration

#### Configuration

<table>
<tr><th>0.x</th><th>1.0.0</th></tr>
<tr>
<td>

```yaml
spice:
  enabled: true
  agents:
    auto-register: true
```

</td>
<td>

```yaml
spice:
  enabled: true

  graph:
    registry:
      enabled: true
    execution:
      timeout-ms: 60000

  statemachine:
    enabled: true
    persistence:
      type: REDIS
      redis:
        host: localhost
        port: 6379

  spring-ai:
    enabled: true
    default-provider: openai
```

</td>
</tr>
</table>

**Changes:**
- ✅ More granular configuration
- ✅ State machine configuration
- ✅ Spring AI integration

---

## API Reference Quick Lookup

### SpiceMessage API

| Method | Description | Example |
|--------|-------------|---------|
| `create(content, from)` | Factory method | `SpiceMessage.create("Hi", "user")` |
| `reply(content, from)` | Create reply | `message.reply("Response", "agent")` |
| `withData(data)` | Add data | `message.withData(mapOf("key" to "value"))` |
| `withMetadata(meta)` | Add metadata | `message.withMetadata(mapOf("userId" to "123"))` |
| `withToolCall(call)` | Add tool call | `message.withToolCall(OAIToolCall.selection(...))` |
| `getData<T>(key)` | Get typed data | `message.getData<String>("key")` |
| `getMetadata<T>(key)` | Get typed metadata | `message.getMetadata<String>("userId")` |
| `transitionTo(state, reason)` | Change state | `message.transitionTo(ExecutionState.RUNNING, "...")` |
| `hasToolCalls()` | Check tool calls | `message.hasToolCalls()` |
| `findToolCall(name)` | Find specific call | `message.findToolCall("request_user_selection")` |

### Graph DSL API

| Method | Description | Example |
|--------|-------------|---------|
| `graph(id) { }` | Create graph | `graph("my-graph") { ... }` |
| `agent(id, agent)` | Add agent node | `agent("step1", myAgent)` |
| `tool(id, tool, mapper)` | Add tool node | `tool("calc", calcTool) { msg -> ... }` |
| `human(id, prompt)` | Add HITL node | `human("confirm", "Confirm?")` |
| `output(id, selector)` | Add output node | `output("result") { it.content }` |
| `edge(from, to)` | Add edge | `edge("step1", "step2")` |
| `edge(from, to, condition)` | Conditional edge | `edge("a", "b") { msg -> ... }` |

### GraphRunner API

| Method | Description | Example |
|--------|-------------|---------|
| `execute(graph, message)` | Execute graph | `runner.execute(graph, msg)` |
| `executeWithCheckpoint(...)` | Execute with checkpoints | `runner.executeWithCheckpoint(graph, msg, store)` |
| `resumeFromCheckpoint(...)` | Resume from checkpoint | `runner.resumeFromCheckpoint(graph, id, msg, store)` |

---

## Summary

### Key Improvements in 1.0.0

| Area | Improvement | Impact |
|------|-------------|--------|
| **Architecture** | Single message type | ⭐⭐⭐ High - Simpler, cleaner |
| **State Management** | Built-in state machine | ⭐⭐⭐ High - Standardized |
| **HITL Support** | Integrated checkpoint/resume | ⭐⭐⭐ High - Less boilerplate |
| **Tool Calls** | OpenAI compatible | ⭐⭐ Medium - Better integration |
| **Type Safety** | Generic type accessors | ⭐⭐ Medium - Fewer cast errors |
| **Metadata** | Separated from data | ⭐⭐ Medium - Cleaner separation |
| **Graph DSL** | Explicit nodes/edges | ⭐⭐⭐ High - More powerful |

### Breaking Changes Impact

| Change | Migration Effort | Workaround |
|--------|------------------|------------|
| `Comm` → `SpiceMessage` | Medium | Rename + update properties |
| `NodeContext` removed | Medium-High | Use SpiceMessage directly |
| `NodeResult` removed | Medium | Return SpiceMessage |
| `CommHub` removed | High | Use Graph + GraphRunner |
| Agent properties | Low | Add name, description, capabilities |
| Tool context | Low | Add ToolContext parameter |

---

## Next Steps

1. **[Quick Start Guide](QUICK_START_1.0.md)** - Try 1.0.0 hands-on
2. **[Migration Guide](MIGRATION_0.x_TO_1.0.md)** - Step-by-step migration
3. **[Architecture Overview](ARCHITECTURE_1.0.md)** - Deep dive into design
4. **[Installation Guide](INSTALLATION_1.0.md)** - Setup instructions

---

**Ready to upgrade? 🌶️ Spice 1.0.0 is worth it!**
