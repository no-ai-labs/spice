# Comm (Communication)

The unified communication system in Spice Framework.

## Overview

`Comm` is the modern, unified message type that replaces legacy Message systems. It provides:

- Type-safe message types
- Rich metadata support
- Media attachments
- Priority and expiration
- Built-in reply mechanisms

## Basic Usage

```kotlin
// Create a communication
val comm = Comm(
    content = "Hello, Agent!",
    from = "user",
    to = "agent-1",
    type = CommType.TEXT
)

// Reply to a communication
val reply = comm.reply(
    content = "Hello, User!",
    from = "agent-1"
)
```

## Comm Types

```kotlin
enum class CommType {
    TEXT,           // General text message
    SYSTEM,         // System message
    TOOL_CALL,      // Tool invocation
    TOOL_RESULT,    // Tool execution result
    ERROR,          // Error message
    DATA,           // Data transfer
    PROMPT,         // Prompt message
    RESULT,         // Final result
    WORKFLOW_START, // Workflow start
    WORKFLOW_END,   // Workflow end
    INTERRUPT,      // Interrupt message
    RESUME,         // Resume message
    IMAGE,          // Image content
    DOCUMENT,       // Document attachment
    AUDIO,          // Audio content
    VIDEO           // Video content
}
```

## Comm Roles

```kotlin
enum class CommRole {
    USER,           // User/Human input
    ASSISTANT,      // AI Assistant response
    SYSTEM,         // System message
    TOOL,           // Tool-related message
    AGENT           // Agent-to-agent communication
}
```

## Advanced Features

### Adding Metadata

The `data` field is a flexible `Map<String, String>` for carrying metadata across your application.

```kotlin
// Add metadata when creating
val comm = Comm(
    content = "Hello",
    from = "user",
    data = mapOf("priority" to "high", "category" to "important")
)

// Add metadata fluently
val enriched = comm
    .withData("priority", "high")
    .withData("category", "important")

// Add multiple at once
val multi = comm.withData(
    "priority" to "high",
    "category" to "important",
    "timestamp" to System.currentTimeMillis().toString()
)
```

#### Metadata in Replies

When you reply to a Comm, **metadata is automatically merged**:

```kotlin
val original = Comm(
    content = "Request",
    from = "user",
    data = mapOf("sessionId" to "session-123", "requestId" to "req-456")
)

// Reply with additional metadata
val response = original.reply(
    content = "Response",
    from = "agent",
    data = mapOf("responseTime" to "50ms")
)

// response.data contains all metadata:
// {
//   "sessionId": "session-123",      // From original
//   "requestId": "req-456",          // From original
//   "responseTime": "50ms"           // Added in reply
// }
```

#### Metadata Propagation in Graphs

When using AgentNode in graphs, **metadata automatically propagates** across all agents:

```kotlin
val graph = graph("metadata-flow") {
    agent("enricher", enricherAgent)  // Adds metadata
    agent("processor", processorAgent) // Receives + adds metadata
    agent("finalizer", finalizerAgent) // Receives all metadata
}

// Each agent automatically receives metadata from previous agents!
```

**Example:**

```kotlin
// Agent 1: Enriches with sessionId
val agent1 = object : Agent {
    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        return SpiceResult.success(
            comm.reply(
                content = "Step 1 complete",
                from = id,
                data = mapOf("sessionId" to "session-123")
            )
        )
    }
    // ... other methods
}

// Agent 2: Automatically receives sessionId, adds userId
val agent2 = object : Agent {
    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        val sessionId = comm.data["sessionId"]  // ✅ Available!
        return SpiceResult.success(
            comm.reply(
                content = "Step 2 complete",
                from = id,
                data = mapOf("userId" to "user-456")
            )
        )
    }
    // ... other methods
}

// Agent 3: Receives both sessionId AND userId
val agent3 = object : Agent {
    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        val sessionId = comm.data["sessionId"]  // ✅ "session-123"
        val userId = comm.data["userId"]        // ✅ "user-456"
        return SpiceResult.success(
            comm.reply("All done for $sessionId, $userId", id)
        )
    }
    // ... other methods
}
```

:::tip
See [Graph Nodes](../orchestration/graph-nodes.md) for detailed information about metadata propagation in graphs.
:::

### Media Attachments

```kotlin
val comm = Comm(content = "Check this image", from = "user")
    .withMedia(
        MediaItem(
            filename = "screenshot.png",
            url = "https://example.com/image.png",
            type = "image",
            size = 1024000
        )
    )
```

### Priority and Expiration

```kotlin
val urgentComm = Comm(content = "Emergency!", from = "user")
    .urgent()
    .expires(ttlMs = 5000) // 5 seconds TTL

if (urgentComm.isExpired()) {
    // Handle expired message
}
```

### Reactions

```kotlin
val comm = Comm(content = "Great work!", from = "user")
    .addReaction("user1", "👍")
    .addReaction("user2", "❤️")
```

## DSL Functions

### Quick Creation

```kotlin
// Simple text message
val comm = quickComm(
    content = "Hello",
    from = "user",
    to = "agent-1"
)

// System message
val sysComm = systemComm("System starting", to = "all")

// Error message
val errComm = errorComm("Something went wrong", to = "admin")
```

### Builder Pattern

```kotlin
val comm = comm("Message content") {
    from("user")
    to("agent-1")
    type(CommType.TEXT)
    role(CommRole.USER)
    urgent()
    mention("agent-2", "agent-3")
    data("key", "value")
}
```

## Tool Communication

### Tool Call

```kotlin
val toolCall = comm.toolCall(
    toolName = "calculate",
    params = mapOf("a" to 5, "b" to 3),
    from = "agent-1"
)
```

### Tool Result

```kotlin
val toolResult = comm.toolResult(
    result = "Result: 8",
    from = "tool-executor"
)
```

## Extension Functions

```kotlin
// Check message properties
if (comm.isSystem()) { /* ... */ }
if (comm.isError()) { /* ... */ }
if (comm.isTool()) { /* ... */ }

// Extract tool information
val toolName = comm.getToolName()
val toolParams = comm.getToolParams()
```

## Next Steps

- [Using Tools](./tool)
- [Agent Communication](./agent)
- [Flow Orchestration](../orchestration/flows)
