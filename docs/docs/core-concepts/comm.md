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

```kotlin
val comm = Comm(content = "Hello", from = "user")
    .withData("priority", "high")
    .withData("category", "important")
```

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
