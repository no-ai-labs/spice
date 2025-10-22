# Comm API

The unified communication system for Spice Framework - modern, powerful, and future-proof.

## Overview

`Comm` (Communication) is the foundation of all messaging in Spice Framework. It replaces legacy Message/EnhancedMessage systems with a clean, extensible design that supports:

- **Flexible messaging** - Text, system, tools, media, and more
- **Threading & conversations** - Organized message flows
- **Rich metadata** - Extensible key-value data storage
- **Media attachments** - Images, documents, audio, video
- **Social features** - Mentions, reactions, priorities
- **Security** - Encryption and TTL support

## Core Structure

```kotlin
data class Comm(
    // Core identification
    val id: String = "comm-${UUID.randomUUID()}",
    val content: String,
    val from: String,
    val to: String? = null,

    // Type and role
    val type: CommType = CommType.TEXT,
    val role: CommRole = CommRole.USER,

    // Timing and threading
    val timestamp: Long = Instant.now().toEpochMilli(),
    val conversationId: String? = null,
    val thread: String? = null,
    val parentId: String? = null,

    // Flexible metadata
    val data: Map<String, String> = emptyMap(),

    // Rich content features
    val media: List<MediaItem> = emptyList(),
    val mentions: List<String> = emptyList(),
    val reactions: List<Reaction> = emptyList(),

    // Control features
    val priority: Priority = Priority.NORMAL,
    val encrypted: Boolean = false,
    val ttl: Long? = null,
    val expiresAt: Long? = null
)
```

## CommType

Different types of communication content:

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

    // Media types
    IMAGE,          // Image content
    DOCUMENT,       // Document attachment
    AUDIO,          // Audio content
    VIDEO           // Video content
}
```

**Common Usage:**
- `TEXT` - Normal user/agent messages
- `SYSTEM` - System notifications and instructions
- `TOOL_CALL` / `TOOL_RESULT` - Tool execution flow
- `ERROR` - Error messages
- `IMAGE` / `DOCUMENT` / `AUDIO` / `VIDEO` - Media content

## CommRole

Role in the conversation:

```kotlin
enum class CommRole {
    USER,           // User/Human input
    ASSISTANT,      // AI Assistant response
    SYSTEM,         // System message
    TOOL,           // Tool-related message
    AGENT           // Agent-to-agent communication
}
```

## Priority

Message priority levels:

```kotlin
enum class Priority {
    LOW,            // Low priority
    NORMAL,         // Normal priority (default)
    HIGH,           // High priority
    URGENT,         // Urgent message
    CRITICAL        // Critical message
}
```

## Creating Comm

### Simple Creation

```kotlin
// Basic message
val comm = Comm(
    content = "Hello, world!",
    from = "user-123"
)

// With recipient
val comm = Comm(
    content = "Hello, agent!",
    from = "user-123",
    to = "agent-456"
)

// With type and role
val comm = Comm(
    content = "System notification",
    from = "system",
    type = CommType.SYSTEM,
    role = CommRole.SYSTEM
)
```

### Quick Functions

```kotlin
// Quick comm creation
val comm = quickComm(
    content = "Hello!",
    from = "user",
    to = "agent",
    type = CommType.TEXT,
    role = CommRole.USER
)

// System message
val systemMsg = systemComm(
    content = "Agent initialized",
    to = "user"
)

// Error message
val errorMsg = errorComm(
    error = "Processing failed",
    to = "user"
)
```

### DSL Builder

```kotlin
val comm = comm("Hello, world!") {
    from("user-123")
    to("agent-456")
    type(CommType.TEXT)
    role(CommRole.USER)

    // Add metadata
    data("key", "value")
    data("foo" to "bar", "baz" to "qux")

    // Add features
    mention("@user1", "@user2")
    urgent()
    encrypt()
    expires(60000) // 60 seconds TTL

    // Add media
    image("photo.jpg", "https://example.com/photo.jpg", caption = "My photo")
    document("report.pdf", "https://example.com/report.pdf")
}
```

## Helper Methods

### reply()

Create a reply to the current message:

```kotlin
// Simple reply
val original = Comm(content = "Hello", from = "user")
val reply = original.reply(
    content = "Hi there!",
    from = "agent-1"
)
// reply.parentId == original.id
// reply.to == "user"
// reply.thread == original.id

// Reply with metadata
val reply = original.reply(
    content = "Processed!",
    from = "agent-1",
    data = mapOf("status" to "complete", "duration_ms" to "150")
)

// Reply with custom type/role
val reply = original.reply(
    content = "Error occurred",
    from = "agent-1",
    type = CommType.ERROR,
    role = CommRole.SYSTEM
)
```

### forward()

Forward message to another recipient:

```kotlin
val comm = Comm(content = "Important message", from = "user-1")
val forwarded = comm.forward(to = "agent-2")
// Same content, different recipient, new ID
```

### error()

Create an error response:

```kotlin
val original = Comm(content = "Process this", from = "user")
val errorResponse = original.error(
    message = "Processing failed: Invalid input",
    from = "agent-1"
)
// type == CommType.ERROR
// role == CommRole.SYSTEM
```

### toolCall() / toolResult()

Create tool-related messages:

```kotlin
val original = Comm(content = "Calculate 2 + 2", from = "user")

// Tool call
val toolCall = original.toolCall(
    toolName = "calculator",
    params = mapOf("operation" to "add", "a" to 2, "b" to 2),
    from = "agent-1"
)

// Tool result
val toolResult = toolCall.toolResult(
    result = "4",
    from = "calculator"
)
```

## Metadata & Data

### Adding Metadata

```kotlin
val comm = Comm(content = "Hello", from = "user")

// Add single entry
val updated = comm.withData("key", "value")

// Add multiple entries
val updated = comm.withData(
    "status" to "processing",
    "priority" to "high",
    "timestamp" to "2024-01-01"
)

// Access data
val status = updated.data["status"] // "processing"
```

**Common metadata keys:**
- `status` - Processing status
- `duration_ms` - Processing duration
- `model` - LLM model used
- `tokens` - Token count
- `cost` - Estimated cost
- `error_code` - Error code
- `retry_count` - Retry attempts

## Threading & Conversations

### Understanding the Hierarchy

```
conversationId: "conv-123"         # Entire conversation
  ├─ thread: null                  # Root message
  │  ├─ message A (id: msg-1)
  │  └─ reply to A (parentId: msg-1, thread: msg-1)
  │     └─ reply to reply (parentId: msg-2, thread: msg-1)
  │
  └─ thread: null                  # Another root message
     └─ message B (id: msg-4)
        └─ reply to B (parentId: msg-4, thread: msg-4)
```

### Threading Example

```kotlin
// Start conversation
val userMsg = Comm(
    content = "What's the weather?",
    from = "user",
    conversationId = "conv-123"
)

// Agent replies (creates thread)
val agentReply = userMsg.reply(
    content = "Let me check...",
    from = "weather-agent"
)
// agentReply.thread == userMsg.id
// agentReply.parentId == userMsg.id
// agentReply.conversationId == "conv-123"

// Follow-up in same thread
val followUp = agentReply.reply(
    content = "It's sunny, 72°F",
    from = "weather-agent"
)
// followUp.thread == userMsg.id (same thread!)
// followUp.parentId == agentReply.id
// followUp.conversationId == "conv-123"
```

## Rich Content Features

### Media Attachments

```kotlin
// Add image
val comm = Comm(content = "Check this out!", from = "user")
    .withMedia(
        MediaItem(
            filename = "screenshot.png",
            url = "https://example.com/screenshot.png",
            type = "image",
            size = 524288,
            caption = "Bug screenshot",
            metadata = mapOf("resolution" to "1920x1080")
        )
    )

// Multiple media
val comm = Comm(content = "Attachments", from = "user")
    .withMedia(
        MediaItem("doc1.pdf", "https://...", "document", 102400),
        MediaItem("doc2.pdf", "https://...", "document", 204800)
    )

// Using DSL
val comm = comm("See attachments") {
    from("user")
    image("photo.jpg", "https://...", size = 1024000, caption = "Vacation photo")
    document("report.pdf", "https://...", size = 512000)
    audio("recording.mp3", "https://...", size = 2048000)
    video("demo.mp4", "https://...", size = 10485760)
}
```

### Mentions

```kotlin
val comm = Comm(content = "Hey everyone!", from = "user")
    .mention("@alice", "@bob", "@charlie")

// Check mentions
if (comm.mentions.contains("@alice")) {
    // Notify alice
}
```

### Reactions

```kotlin
val original = Comm(content = "Great work!", from = "user")

// Add reaction
val updated = original.addReaction(
    user = "bob",
    emoji = "👍"
)

// Check reactions
updated.reactions.forEach { reaction ->
    println("${reaction.user} reacted with ${reaction.emoji}")
}
```

## Control Features

### Priority

```kotlin
// Set priority on creation
val comm = Comm(
    content = "Urgent!",
    from = "system",
    priority = Priority.URGENT
)

// Change priority
val normal = comm.copy(priority = Priority.NORMAL)
val urgent = comm.urgent()
val critical = comm.critical()
val low = comm.lowPriority()

// Check priority
if (comm.priority == Priority.CRITICAL) {
    // Handle immediately
}
```

### Encryption

```kotlin
val comm = Comm(content = "Sensitive data", from = "user")
    .encrypt()

if (comm.encrypted) {
    // Handle encrypted content
}
```

### Time-To-Live (TTL)

```kotlin
// Message expires in 60 seconds
val comm = Comm(content = "Temporary message", from = "user")
    .expires(60000)

// Check expiration
if (comm.isExpired()) {
    println("Message expired")
} else {
    println("Message still valid")
}

// Access TTL
val ttl = comm.ttl // 60000L
val expiresAt = comm.expiresAt // timestamp
```

## Type & Role Helpers

```kotlin
// Change type
val comm = Comm(content = "Hello", from = "user")
    .withType(CommType.SYSTEM)

// Change role
val comm = Comm(content = "Hello", from = "user")
    .withRole(CommRole.AGENT)

// Check type/role
val comm = Comm(content = "Error!", from = "system", type = CommType.ERROR)

comm.isSystem()  // Extension function
comm.isError()   // Extension function
comm.isTool()    // Extension function
```

## Extension Functions

```kotlin
// Check if from system
fun Comm.isSystem(): Boolean =
    role == CommRole.SYSTEM || from == "system"

// Check if error
fun Comm.isError(): Boolean =
    type == CommType.ERROR

// Check if tool-related
fun Comm.isTool(): Boolean =
    type == CommType.TOOL_CALL || type == CommType.TOOL_RESULT

// Get tool name
fun Comm.getToolName(): String? =
    data["tool_name"]

// Get tool params
fun Comm.getToolParams(): String? =
    data["tool_params"]
```

**Usage:**

```kotlin
val comm = toolCall(...)

if (comm.isTool()) {
    val toolName = comm.getToolName()
    val params = comm.getToolParams()
    // Execute tool
}
```

## Real-World Examples

### Agent Communication Flow

```kotlin
// User sends message
val userMsg = Comm(
    content = "Analyze this data",
    from = "user-123",
    conversationId = "conv-456"
)

// Agent processes and replies
val agentReply = userMsg.reply(
    content = "Starting analysis...",
    from = "data-agent",
    data = mapOf("status" to "processing")
)

// Agent calls tool
val toolCall = agentReply.toolCall(
    toolName = "analyze_data",
    params = mapOf("dataset" to "users.csv"),
    from = "data-agent"
)

// Tool returns result
val toolResult = toolCall.toolResult(
    result = "Analysis complete: 1000 records processed",
    from = "analyze_data"
)

// Agent sends final result
val finalResult = toolResult.reply(
    content = "Analysis complete! Found 3 anomalies.",
    from = "data-agent",
    type = CommType.RESULT,
    data = mapOf(
        "status" to "complete",
        "duration_ms" to "1250",
        "anomalies" to "3"
    )
)
```

### Multi-Agent Coordination

```kotlin
// Coordinator broadcasts task
val task = Comm(
    content = "Research topic: AI safety",
    from = "coordinator",
    conversationId = "research-task-1"
)

// Agent 1 responds
val response1 = task.reply(
    content = "Found 15 papers on AI safety",
    from = "research-agent-1",
    data = mapOf("papers_found" to "15", "relevance_score" to "0.89")
)

// Agent 2 responds
val response2 = task.reply(
    content = "Analyzed ethical implications",
    from = "ethics-agent",
    data = mapOf("topics_covered" to "5", "concerns" to "3")
)

// Coordinator aggregates
val summary = task.reply(
    content = "Research complete: 15 papers, 5 topics, 3 concerns",
    from = "coordinator",
    type = CommType.RESULT,
    data = mapOf(
        "agent_1_contribution" to response1.data.toString(),
        "agent_2_contribution" to response2.data.toString()
    )
)
```

### Error Handling

```kotlin
suspend fun processMessage(comm: Comm): Comm {
    return try {
        // Process message
        val result = processData(comm.content)
        comm.reply(
            content = result,
            from = "processor",
            data = mapOf("status" to "success")
        )
    } catch (e: Exception) {
        // Return error
        comm.error(
            message = "Processing failed: ${e.message}",
            from = "processor"
        ).withData(
            "error_type" to e::class.simpleName.orEmpty(),
            "timestamp" to System.currentTimeMillis().toString()
        )
    }
}
```

### Media-Rich Communication

```kotlin
val report = comm("Monthly Report - January 2024") {
    from("reporting-agent")
    to("manager")
    type(CommType.DOCUMENT)

    // Add report documents
    document(
        "sales-report.pdf",
        "https://storage.example.com/reports/2024-01.pdf",
        size = 1024000
    )

    // Add visualization
    image(
        "sales-chart.png",
        "https://storage.example.com/charts/2024-01.png",
        size = 512000,
        caption = "Sales trends Q1 2024"
    )

    // Add metadata
    data(
        "period" to "2024-01",
        "generated_at" to Instant.now().toString(),
        "pages" to "24",
        "summary" to "Revenue up 15%"
    )

    // Mark as important
    urgent()
}
```

## Best Practices

### 1. Use Appropriate Types

```kotlin
// ✅ Good - Clear type
val comm = Comm(
    content = "System restart required",
    from = "system",
    type = CommType.SYSTEM,
    role = CommRole.SYSTEM
)

// ❌ Bad - Ambiguous
val comm = Comm(
    content = "System restart required",
    from = "system"
    // Defaults to TEXT/USER - confusing!
)
```

### 2. Maintain Threading

```kotlin
// ✅ Good - Proper threading
val reply = original.reply(content = "...", from = "agent")
// Automatically sets thread, parentId, conversationId

// ❌ Bad - Manual threading (error-prone)
val reply = Comm(
    content = "...",
    from = "agent",
    thread = original.id, // Easy to forget!
    parentId = original.id,
    conversationId = original.conversationId
)
```

### 3. Include Metadata

```kotlin
// ✅ Good - Rich metadata
val comm = agent.processComm(input)
    .withData(
        "model" to "gpt-4",
        "tokens" to "150",
        "duration_ms" to "1200",
        "cost_usd" to "0.003"
    )

// ❌ Bad - No context
val comm = agent.processComm(input)
// Missing important operational data
```

### 4. Handle Expiration

```kotlin
// ✅ Good - Check expiration
if (!comm.isExpired()) {
    processMessage(comm)
} else {
    logger.warn("Message expired: ${comm.id}")
}

// ❌ Bad - No expiration check
processMessage(comm) // Might process expired message
```

### 5. Use Priority Appropriately

```kotlin
// ✅ Good - Reserve critical for emergencies
val alertComm = errorComm("Database connection lost!")
    .critical()

val normalComm = Comm(content = "Hello", from = "user")
// priority = NORMAL (default)

// ❌ Bad - Everything is urgent
val comm = Comm(content = "Hello", from = "user")
    .urgent() // Don't abuse priority!
```

## Next Steps

- [Agent API](./agent) - Learn about agents
- [Tool API](./tool) - Learn about tools
- [Error Handling](../error-handling/overview) - Handle comm errors
- [Observability](../observability/overview) - Track comm flow
