# Spice 0.10.0 Migration Guide

## 🚀 OpenAI Tool Spec Integration (Breaking Changes)

Spice 0.10.0 introduces OpenAI Function Calling standard to the framework, providing industry-standard tool calling semantics compatible with OpenAI, Anthropic, LangChain, and LlamaIndex.

### Version Strategy
- **0.10.0**: Current version with breaking changes
- **0.9.x**: LTS branch maintained for existing projects

---

## What Changed

### 1. New Tool Call Data Classes

**Before (0.9.x):**
```kotlin
val comm = comm.reply("Select option", agentId)
    .copy(data = mapOf(
        "menu_text" to "Option 1\nOption 2",
        "workflow_message" to "Please confirm"
    ))
```

**After (0.10.0):**
```kotlin
import io.github.noailabs.spice.toolspec.OAIToolCall
import io.github.noailabs.spice.toolspec.withToolCall

val comm = comm.reply("Select option", agentId)
    .withToolCall(
        OAIToolCall.selection(
            items = listOf(
                mapOf("id" to "1", "name" to "Option 1"),
                mapOf("id" to "2", "name" to "Option 2")
            ),
            promptMessage = "Please select an option"
        )
    )
```

### 2. Tool Interface Enhancement

**New Method:**
```kotlin
interface Tool {
    // ... existing methods ...

    // NEW in 0.10.0
    fun toToolSpec(strict: Boolean = false): Map<String, Any>
}
```

**Usage:**
```kotlin
val tool: Tool = WebSearchTool()
val openAISpec = tool.toToolSpec()
// Use with OpenAI API, Anthropic, etc.
```

### 3. AgentNode Automatic Propagation

AgentNode now automatically propagates `tool_calls` to next nodes:

**Accessing in Custom Nodes:**
```kotlin
class MyCustomNode : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        // Method 1: Via state
        @Suppress("UNCHECKED_CAST")
        val toolCalls = ctx.state["tool_calls"] as? List<OAIToolCall>

        // Method 2: Via _previousComm
        val previousComm = ctx.state["_previousComm"] as? Comm
        val toolCallsFromComm = previousComm?.getToolCalls()

        // Process tool calls...
        return SpiceResult.success(NodeResult.fromContext(ctx, "Done"))
    }
}
```

---

## Migration Steps

### Step 1: Update Dependencies

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.noailabs:spice-core:0.10.0")
}
```

### Step 2: Update Agent Code

**Option A: Use Tool Call Factories**
```kotlin
override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
    val toolCall = OAIToolCall.selection(
        items = listOf(
            mapOf("id" to "res1", "name" to "Reservation 1", "description" to "Details...")
        ),
        promptMessage = "Select a reservation"
    )

    return SpiceResult.success(
        comm.reply("Please select", id).withToolCall(toolCall)
    )
}
```

**Option B: Use Tool Call Builder (DSL)**
```kotlin
import io.github.noailabs.spice.toolspec.toolCall

override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
    val toolCall = toolCall {
        functionName("request_user_selection")
        argument("items", myItems)
        argument("prompt_message", "Select an option")
    }

    return SpiceResult.success(comm.reply("Select", id).withToolCall(toolCall))
}
```

### Step 3: Update Custom Nodes

**Before (accessing raw data):**
```kotlin
class ResponseNode : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        val message = ctx.state["workflow_message"]?.toString() ?: ""
        return SpiceResult.success(NodeResult.fromContext(ctx, message))
    }
}
```

**After (accessing tool calls):**
```kotlin
import io.github.noailabs.spice.toolspec.getToolCalls
import io.github.noailabs.spice.toolspec.findToolCall

class ResponseNode : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        // Get previous Comm with tool calls
        val previousComm = ctx.state["_previousComm"] as? Comm

        // Find completion tool call
        val completionCall = previousComm?.findToolCall("workflow_completed")
        val message = completionCall?.function?.getArgumentString("message") ?: ""

        return SpiceResult.success(NodeResult.fromContext(ctx, message))
    }
}
```

---

## Backward Compatibility

### Automatic Legacy Field Migration

Spice 0.10.0 includes automatic migration for legacy fields:

```kotlin
// Old code with legacy fields still works!
val comm = Comm(content = "Hello", from = "agent")
    .copy(data = mapOf("menu_text" to "Option 1\nOption 2"))

// getToolCalls() automatically migrates to tool_calls format
val toolCalls = comm.getToolCalls()
// Returns: List<OAIToolCall> with REQUEST_USER_SELECTION
```

**Legacy Fields Supported:**
- `menu_text` → `request_user_selection`
- `workflow_message` + `needs_user_confirmation` → `request_user_confirmation`
- `workflow_message` (alone) → `workflow_completed`
- `structured_data` → `tool_message`
- `needs_user_selection` → `request_user_selection`

### Cleanup Legacy Fields

After migration, clean up legacy fields:

```kotlin
import io.github.noailabs.spice.toolspec.cleanupLegacyFields

val cleanComm = comm
    .withToolCall(OAIToolCall.selection(...))
    .cleanupLegacyFields()  // Removes menu_text, workflow_message, etc.
```

---

## Standard Tool Call Types

### 1. Selection

```kotlin
OAIToolCall.selection(
    items = listOf(
        mapOf("id" to "1", "name" to "Option 1", "description" to "First option"),
        mapOf("id" to "2", "name" to "Option 2", "description" to "Second option")
    ),
    promptMessage = "Please select an option",
    selectionType = "single",  // or "multiple"
    metadata = mapOf("priority" to 100)
)
```

### 2. Confirmation

```kotlin
OAIToolCall.confirmation(
    message = "Do you want to proceed?",
    options = listOf("Yes", "No"),
    confirmationType = "action_confirmation",
    metadata = mapOf("action" to "cancel_booking")
)
```

### 3. Human-in-the-Loop (HITL)

```kotlin
OAIToolCall.hitl(
    type = "text_input",
    question = "What is your preferred date?",
    context = mapOf("current_date" to "2025-01-15")
)
```

### 4. Workflow Completion

```kotlin
OAIToolCall.completion(
    message = "Booking completed successfully",
    workflowId = "booking-12345",
    reasoning = "All validation passed",
    metadata = mapOf("booking_id" to "B12345")
)
```

### 5. Tool Message

```kotlin
OAIToolCall.toolMessage(
    message = "Processing reservation...",
    toolName = "reservation_processor",
    isIntermediate = true,
    metadata = mapOf("step" to 2)
)
```

### 6. Error

```kotlin
OAIToolCall.error(
    message = "Validation failed",
    errorType = "validation_error",
    isRecoverable = true
)
```

---

## Extension Functions

### Comm Extensions

```kotlin
import io.github.noailabs.spice.toolspec.*

// Add tool call
val comm = comm.withToolCall(toolCall)

// Add multiple tool calls
val comm = comm.withToolCalls(listOf(toolCall1, toolCall2))

// Retrieve tool calls
val toolCalls = comm.getToolCalls()

// Query tool calls
val hasToolCalls = comm.hasToolCalls()
val count = comm.countToolCalls()
val names = comm.getToolCallNames()

// Find specific tool call
val selectionCall = comm.findToolCall("request_user_selection")
val allSelections = comm.findToolCalls("request_user_selection")

// Check for specific tool call
if (comm.hasToolCall("workflow_completed")) {
    // Handle completion
}

// Statistics
val stats = comm.getToolCallStats()
println("Total: ${stats.totalToolCalls}")
println("Functions: ${stats.functionCounts}")
println("Has legacy: ${stats.hasLegacyFields}")

// Cleanup
val cleanComm = comm.cleanupLegacyFields()
```

---

## Benefits

1. **Industry Standard**: Compatible with OpenAI, Anthropic, LangChain, LlamaIndex
2. **Type Safety**: No more string key lookups - use typed tool calls
3. **Auto-Migration**: Legacy fields automatically migrate to new format
4. **Production Proven**: Battle-tested in KAI-Core (47 files, production stable)
5. **Ecosystem Integration**: Export tools as OpenAI Function Calling spec

---

## Troubleshooting

### Issue: "Unresolved reference 'tool_calls'"

**Solution**: Import extension functions:
```kotlin
import io.github.noailabs.spice.toolspec.getToolCalls
import io.github.noailabs.spice.toolspec.withToolCall
```

### Issue: Legacy field not migrating

**Solution**: Check field name spelling. Only these are supported:
- `menu_text`
- `workflow_message`
- `needs_user_confirmation`
- `structured_data`
- `needs_user_selection`

### Issue: Tool calls not propagating to next node

**Solution**: Ensure AgentNode is used (not custom wrapper):
```kotlin
graph("my-graph") {
    agent("selection", myAgent)  // ✅ Uses AgentNode (auto-propagates)
    output("processor")
    edge("selection", "processor")
}
```

---

## Examples

### Full Workflow Example

```kotlin
// Agent produces tool call
class SelectionAgent : Agent {
    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        val toolCall = OAIToolCall.selection(
            items = getAvailableItems(),
            promptMessage = "Select an item"
        )
        return SpiceResult.success(comm.reply("Selection required", id).withToolCall(toolCall))
    }
}

// Custom node processes tool call
class ProcessorNode : Node {
    override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
        val previousComm = ctx.state["_previousComm"] as? Comm
        val toolCalls = previousComm?.getToolCalls() ?: emptyList()

        toolCalls.forEach { toolCall ->
            when (toolCall.getFunctionName()) {
                "request_user_selection" -> {
                    val items = toolCall.function.getArgumentList("items")
                    // Process items...
                }
                "workflow_completed" -> {
                    val message = toolCall.function.getArgumentString("message")
                    // Handle completion...
                }
            }
        }

        return SpiceResult.success(NodeResult.fromContext(ctx, "Processed"))
    }
}

// Graph definition
val graph = graph("workflow") {
    agent("selection", SelectionAgent())
    output("processor")
    edge("selection", "processor")
}
```

---

## Support

- **Documentation**: https://github.com/noailabs/spice/docs
- **Issues**: https://github.com/noailabs/spice/issues
- **0.9.x LTS**: Maintained until 2026-01

---

## Next Steps

1. Upgrade to 0.10.0
2. Update Agent implementations with tool calls
3. Test backward compatibility with legacy fields
4. Clean up legacy fields after migration verified
5. Export tools as OpenAI Function Calling specs
