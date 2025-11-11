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
- **Serialization** - JSON-ready with Kotlinx Serialization

## Core Structure

```kotlin
@Serializable
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

    // Flexible metadata (v0.9.0: supports native types and null)
    @Serializable(with = AnyValueMapSerializer::class)
    val data: Map<String, Any?> = emptyMap(),

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

## Type System

### CommType

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

### CommRole

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

**When to use each:**
- `USER` - Human input, user queries
- `ASSISTANT` - AI responses, agent replies
- `SYSTEM` - Framework messages, notifications
- `TOOL` - Tool calls and results
- `AGENT` - Inter-agent coordination

### Priority

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

**Priority guidelines:**
- `LOW` - Logging, analytics, non-critical updates
- `NORMAL` - Standard messages (default)
- `HIGH` - Important but not time-sensitive
- `URGENT` - Time-sensitive, needs quick response
- `CRITICAL` - System-critical, immediate action required

## Supporting Data Classes

### MediaItem

Represents media attachments:

```kotlin
@Serializable
data class MediaItem(
    val filename: String,      // Original filename
    val url: String,           // Media URL (local or remote)
    val type: String,          // Media type: image, document, audio, video
    val size: Long = 0,        // File size in bytes
    val caption: String? = null, // Optional caption
    @Serializable(with = AnyValueMapSerializer::class)
    val metadata: Map<String, Any?> = emptyMap() // Additional metadata (v0.9.0: native types and null)
)
```

**Example:**
```kotlin
val image = MediaItem(
    filename = "screenshot.png",
    url = "https://storage.example.com/images/abc123.png",
    type = "image",
    size = 524288,
    caption = "Bug screenshot",
    metadata = mapOf(
        "resolution" to "1920x1080",     // String
        "format" to "png",                // String
        "captured_at" to "2024-01-15T10:30:00Z",  // String
        "width" to 1920,                  // v0.9.0: Int natively supported
        "height" to 1080,                 // v0.9.0: Int natively supported
        "has_alpha" to true               // v0.9.0: Boolean natively supported
    )
)
```

### Reaction

User reactions to messages:

```kotlin
@Serializable
data class Reaction(
    val user: String,          // User who reacted
    val emoji: String,         // Emoji reaction
    val timestamp: Long        // When reaction was added
)
```

**Example:**
```kotlin
val reaction = Reaction(
    user = "user-123",
    emoji = "👍",
    timestamp = Instant.now().toEpochMilli()
)
```

### CommResult

Result of communication operations:

```kotlin
data class CommResult(
    val success: Boolean,
    val commId: String? = null,
    val deliveredTo: List<String> = emptyList(),
    val error: String? = null,
    val timestamp: Long = Instant.now().toEpochMilli()
) {
    companion object {
        fun success(commId: String, deliveredTo: List<String> = emptyList()): CommResult
        fun failure(error: String): CommResult
    }
}
```

**Usage:**
```kotlin
suspend fun sendComm(comm: Comm): CommResult {
    return try {
        val delivered = deliveryService.send(comm)
        CommResult.success(comm.id, delivered)
    } catch (e: Exception) {
        CommResult.failure("Delivery failed: ${e.message}")
    }
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

// With conversation context
val comm = Comm(
    content = "Continue discussion",
    from = "user-123",
    conversationId = "conv-789",
    thread = "thread-456"
)
```

### Quick Functions

Convenience functions for common patterns:

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

Fluent API for complex messages:

```kotlin
val comm = comm("Hello, world!") {
    from("user-123")
    to("agent-456")
    type(CommType.TEXT)
    role(CommRole.USER)

    // Conversation context
    conversation("conv-789")
    thread("thread-456")
    replyTo("parent-123")

    // Add metadata
    data("key", "value")
    data("foo" to "bar", "baz" to "qux")

    // Add features
    mention("@user1", "@user2")
    urgent()
    encrypt()
    expires(60000) // 60 seconds TTL

    // Add media
    image("photo.jpg", "https://example.com/photo.jpg",
          size = 1024000, caption = "My photo")
    document("report.pdf", "https://example.com/report.pdf", size = 512000)
    audio("recording.mp3", "https://example.com/audio.mp3", size = 2048000)
    video("demo.mp4", "https://example.com/video.mp4", size = 10485760)
}
```

**CommBuilder API:**

```kotlin
class CommBuilder(content: String) {
    fun from(sender: String)
    fun to(recipient: String)
    fun type(type: CommType)
    fun role(role: CommRole)
    fun conversation(id: String)
    fun thread(id: String)
    fun replyTo(parentId: String)

    fun data(key: String, value: String)
    fun data(vararg pairs: Pair<String, String>)

    fun mention(vararg users: String)
    fun urgent()
    fun critical()
    fun encrypt()
    fun expires(ttlMs: Long)

    fun image(filename: String, url: String, size: Long = 0, caption: String? = null)
    fun document(filename: String, url: String, size: Long = 0)
    fun audio(filename: String, url: String, size: Long = 0)
    fun video(filename: String, url: String, size: Long = 0)

    fun build(): Comm
}
```

## Core Methods

### reply()

Create a reply to the current message:

**Signature:**
```kotlin
fun reply(
    content: String,
    from: String,
    role: CommRole = CommRole.ASSISTANT,
    type: CommType = CommType.TEXT
): Comm

fun reply(
    content: String,
    from: String,
    type: CommType = CommType.TEXT,
    role: CommRole = CommRole.ASSISTANT,
    data: Map<String, Any?> = emptyMap()  // v0.9.0: native types and null
): Comm
```

**Behavior:**
- Automatically sets `to` to original sender
- Sets `parentId` to original message ID
- Sets `thread` to original thread or original ID
- Preserves `conversationId`
- Generates new ID and timestamp

**Examples:**
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

// Reply with metadata (v0.9.0: native types)
val reply = original.reply(
    content = "Processed!",
    from = "agent-1",
    data = mapOf(
        "status" to "complete",
        "duration_ms" to 150,      // v0.9.0: Int natively supported
        "timestamp" to System.currentTimeMillis()  // v0.9.0: Long natively supported
    )
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

**Signature:**
```kotlin
fun forward(to: String): Comm
```

**Behavior:**
- Preserves content and most fields
- Changes `to` recipient
- Generates new ID and timestamp
- Does NOT change `thread` or `conversationId`

**Example:**
```kotlin
val comm = Comm(content = "Important message", from = "user-1")
val forwarded = comm.forward(to = "agent-2")
// Same content, different recipient, new ID
```

### error()

Create an error response:

**Signature:**
```kotlin
fun error(message: String, from: String = "system"): Comm
```

**Behavior:**
- Creates reply with `CommType.ERROR`
- Sets role to `CommRole.SYSTEM`
- Default sender is "system"

**Example:**
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

**Signatures:**
```kotlin
fun toolCall(
    toolName: String,
    params: Map<String, Any?>,  // v0.9.0: supports null values
    from: String
): Comm

fun toolResult(
    result: String,
    from: String = "system"
): Comm
```

**Examples:**
```kotlin
val original = Comm(content = "Calculate 2 + 2", from = "user")

// Tool call (v0.9.0: native Int types)
val toolCall = original.toolCall(
    toolName = "calculator",
    params = mapOf(
        "operation" to "add",
        "a" to 2,       // v0.9.0: Int natively supported
        "b" to 2        // v0.9.0: Int natively supported
    ),
    from = "agent-1"
)
// toolCall.type == CommType.TOOL_CALL
// toolCall.data["tool_name"] == "calculator"

// Tool result
val toolResult = toolCall.toolResult(
    result = "4",
    from = "calculator"
)
// toolResult.type == CommType.TOOL_RESULT
```

## Metadata & Data

### withData()

Add metadata to messages:

**Signatures:**
```kotlin
// v0.9.0: Updated to support Any? values
fun withData(key: String, value: Any?): Comm
fun withData(vararg pairs: Pair<String, Any?>): Comm
```

**Examples:**
```kotlin
val comm = Comm(content = "Hello", from = "user")

// Add single entry
val updated = comm.withData("key", "value")

// v0.9.0: Native type support - no more string conversion needed!
val updated = comm.withData(
    "status" to "processing",
    "priority" to 5,                         // Int natively supported
    "timestamp" to System.currentTimeMillis(), // Long natively supported
    "enabled" to true,                        // Boolean natively supported
    "optional" to null                        // Null supported
)

// Chain calls with native types
val updated = comm
    .withData("model", "gpt-4")
    .withData("tokens", 150)         // v0.9.0: Int (not "150")
    .withData("cost", 0.003)         // v0.9.0: Double (not "0.003")
    .withData("temperature", 0.7)    // v0.9.0: Double

// Access data - safe casting required
val status = updated.data["status"] as? String              // "processing"
val tokens = updated.data["tokens"] as? Int                 // 150
val cost = updated.data["cost"] as? Double                  // 0.003
val enabled = updated.data["enabled"] as? Boolean           // true
```

### Common Metadata Keys

**Recommended conventions (v0.9.0: Use native types):**

| Key | Description | Type | Example |
|-----|-------------|------|---------|
| `status` | Processing status | String | `"processing"`, `"complete"`, `"failed"` |
| `duration_ms` | Processing duration | Int/Long | `1250` |
| `model` | LLM model used | String | `"gpt-4"`, `"claude-3-sonnet"` |
| `tokens` | Token count | Int | `150` |
| `cost` | Estimated cost | Double | `0.003` |
| `error_code` | Error code | String | `"RATE_LIMIT"`, `"INVALID_INPUT"` |
| `retry_count` | Retry attempts | Int | `2` |
| `enabled` | Feature flag | Boolean | `true`, `false` |
| `tool_name` | Tool name (auto-set) | String | `"calculator"` |
| `tool_params` | Tool params (auto-set) | Map | `mapOf("a" to 2, "b" to 2)` |
| `user_id` | User identifier | String | `"user-123"` |
| `session_id` | Session identifier | String | `"sess-456"` |
| `trace_id` | Distributed trace ID | String | `"abc123xyz"` |
| `optional_field` | Optional value | Any? | `null` |

**v0.9.0 Best Practice:**
```kotlin
// ✅ Good - Use native types
val comm = comm.withData(
    "count" to 42,                    // Int
    "score" to 0.95,                  // Double
    "enabled" to true,                // Boolean
    "tags" to listOf("ai", "ml"),     // List
    "optional" to null                // Null
)

// ❌ Old - String conversion (pre-0.9.0)
val comm = comm.withData(
    "count" to "42",                  // String (requires parsing later)
    "score" to "0.95",                // String (requires parsing later)
    "enabled" to "true"               // String (requires parsing later)
)
```

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

**Key concepts:**
- `conversationId` - Groups all related messages
- `thread` - Root message of a reply chain
- `parentId` - Immediate parent message
- `timestamp` - Message creation time

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

### Conversation Management

```kotlin
// Group by conversation
fun groupByConversation(comms: List<Comm>): Map<String?, List<Comm>> {
    return comms.groupBy { it.conversationId }
}

// Get conversation thread
fun getThread(comms: List<Comm>, threadId: String): List<Comm> {
    return comms
        .filter { it.thread == threadId || it.id == threadId }
        .sortedBy { it.timestamp }
}

// Find root messages
fun getRootMessages(comms: List<Comm>): List<Comm> {
    return comms.filter { it.parentId == null }
}

// Build conversation tree
data class CommNode(
    val comm: Comm,
    val children: List<CommNode>
)

fun buildTree(comms: List<Comm>, parentId: String? = null): List<CommNode> {
    val children = comms.filter { it.parentId == parentId }
    return children.map { comm ->
        CommNode(comm, buildTree(comms, comm.id))
    }
}
```

## Rich Content Features

### Media Attachments

**Adding Media:**

```kotlin
// Single media
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
        MediaItem("doc2.pdf", "https://...", "document", 204800),
        MediaItem("image.jpg", "https://...", "image", 1024000)
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

**Processing Media:**

```kotlin
// Check for media
if (comm.media.isNotEmpty()) {
    comm.media.forEach { mediaItem ->
        when (mediaItem.type) {
            "image" -> processImage(mediaItem)
            "document" -> processDocument(mediaItem)
            "audio" -> processAudio(mediaItem)
            "video" -> processVideo(mediaItem)
        }
    }
}

// Filter by type
val images = comm.media.filter { it.type == "image" }
val documents = comm.media.filter { it.type == "document" }

// Calculate total size
val totalSize = comm.media.sumOf { it.size }
```

### Mentions

**Adding Mentions:**

```kotlin
// In constructor
val comm = Comm(
    content = "Hey everyone!",
    from = "user",
    mentions = listOf("@alice", "@bob", "@charlie")
)

// Using method
val comm = Comm(content = "Hey!", from = "user")
    .mention("@alice", "@bob", "@charlie")

// Using DSL
val comm = comm("Team meeting at 3pm") {
    from("manager")
    mention("@alice", "@bob", "@charlie")
}
```

**Processing Mentions:**

```kotlin
// Check mentions
if ("@alice" in comm.mentions) {
    notifyUser("alice", comm)
}

// Notify all mentioned users
comm.mentions.forEach { mention ->
    val username = mention.removePrefix("@")
    notifyUser(username, comm)
}

// Extract mentioned users
val usernames = comm.mentions.map { it.removePrefix("@") }
```

### Reactions

**Adding Reactions:**

```kotlin
val original = Comm(content = "Great work!", from = "user")

// Add reaction
val updated = original.addReaction(
    user = "bob",
    emoji = "👍"
)

// Add multiple reactions
val updated = original
    .addReaction("bob", "👍")
    .addReaction("alice", "❤️")
    .addReaction("charlie", "🎉")
```

**Processing Reactions:**

```kotlin
// List all reactions
updated.reactions.forEach { reaction ->
    println("${reaction.user} reacted with ${reaction.emoji} at ${reaction.timestamp}")
}

// Count reactions by emoji
val reactionCounts = comm.reactions
    .groupBy { it.emoji }
    .mapValues { it.value.size }
// {"👍": 5, "❤️": 3, "🎉": 2}

// Check if user reacted
val userReacted = comm.reactions.any { it.user == "bob" }

// Get user's reactions
val userReactions = comm.reactions.filter { it.user == "bob" }
```

## Control Features

### Priority

**Setting Priority:**

```kotlin
// On creation
val comm = Comm(
    content = "Urgent!",
    from = "system",
    priority = Priority.URGENT
)

// Using methods
val comm = Comm(content = "Message", from = "user")
    .urgent()      // Priority.URGENT
    .critical()    // Priority.CRITICAL
    .lowPriority() // Priority.LOW

// Using copy
val normal = comm.copy(priority = Priority.NORMAL)
```

**Processing by Priority:**

```kotlin
// Sort by priority
val sorted = comms.sortedByDescending {
    when (it.priority) {
        Priority.CRITICAL -> 5
        Priority.URGENT -> 4
        Priority.HIGH -> 3
        Priority.NORMAL -> 2
        Priority.LOW -> 1
    }
}

// Filter urgent messages
val urgent = comms.filter {
    it.priority == Priority.URGENT || it.priority == Priority.CRITICAL
}

// Priority-based routing
suspend fun routeByPriority(comm: Comm) {
    when (comm.priority) {
        Priority.CRITICAL -> handleImmediately(comm)
        Priority.URGENT -> scheduleHighPriority(comm)
        Priority.NORMAL -> queueNormal(comm)
        Priority.LOW -> queueLowPriority(comm)
        else -> queueNormal(comm)
    }
}
```

### Encryption

**Encrypting Messages:**

```kotlin
// Mark as encrypted
val comm = Comm(content = "Sensitive data", from = "user")
    .encrypt()

// Using DSL
val comm = comm("Secret message") {
    from("user")
    encrypt()
}

// Check encryption
if (comm.encrypted) {
    // Handle with encryption layer
    val decrypted = encryptionService.decrypt(comm.content)
}
```

**Encryption Pattern:**

```kotlin
class EncryptionService(private val key: String) {

    fun encrypt(comm: Comm): Comm {
        val encryptedContent = encryptData(comm.content, key)
        return comm.copy(
            content = encryptedContent,
            encrypted = true
        )
    }

    fun decrypt(comm: Comm): Comm {
        if (!comm.encrypted) return comm

        val decryptedContent = decryptData(comm.content, key)
        return comm.copy(
            content = decryptedContent,
            encrypted = false
        )
    }

    private fun encryptData(data: String, key: String): String {
        // Implementation using AES, etc.
        return ""
    }

    private fun decryptData(data: String, key: String): String {
        // Implementation
        return ""
    }
}
```

### Time-To-Live (TTL)

**Setting TTL:**

```kotlin
// Message expires in 60 seconds
val comm = Comm(content = "Temporary message", from = "user")
    .expires(60000)

// Using DSL
val comm = comm("Expires soon") {
    from("user")
    expires(30000) // 30 seconds
}

// Check TTL
val ttl = comm.ttl // 60000L
val expiresAt = comm.expiresAt // timestamp
```

**Expiration Handling:**

```kotlin
// Check if expired
if (comm.isExpired()) {
    logger.warn("Message ${comm.id} expired")
    return
}

// Filter expired messages
val validMessages = comms.filter { !it.isExpired() }

// Cleanup expired messages
suspend fun cleanupExpired(comms: List<Comm>): List<Comm> {
    val (expired, valid) = comms.partition { it.isExpired() }

    expired.forEach { comm ->
        logger.info("Removing expired message: ${comm.id}")
        notifyExpiration(comm)
    }

    return valid
}

// Auto-cleanup with coroutines
fun startExpirationCleanup(scope: CoroutineScope, comms: MutableList<Comm>) {
    scope.launch {
        while (isActive) {
            delay(10000) // Check every 10 seconds
            comms.removeAll { it.isExpired() }
        }
    }
}
```

## Type & Role Helpers

**Changing Type/Role:**

```kotlin
// Change type
val comm = Comm(content = "Hello", from = "user")
    .withType(CommType.SYSTEM)

// Change role
val comm = Comm(content = "Hello", from = "user")
    .withRole(CommRole.AGENT)

// Combined
val comm = Comm(content = "Message", from = "user")
    .withType(CommType.ERROR)
    .withRole(CommRole.SYSTEM)
```

## Extension Functions

**Built-in Extensions:**

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

if (comm.isError()) {
    handleError(comm)
}

if (comm.isSystem()) {
    logSystemMessage(comm)
}
```

**Custom Extensions:**

```kotlin
// Check if message is from agent
fun Comm.isFromAgent(): Boolean =
    role == CommRole.AGENT || role == CommRole.ASSISTANT

// Check if has media
fun Comm.hasMedia(): Boolean =
    media.isNotEmpty()

// Get conversation age
fun Comm.ageMillis(): Long =
    Instant.now().toEpochMilli() - timestamp

// Format for display
fun Comm.formatDisplay(): String {
    return "[${from}] (${timestamp}): ${content}"
}

// Extract user mentions
fun Comm.getMentionedUsers(): List<String> =
    mentions.map { it.removePrefix("@") }

// Check if urgent
fun Comm.isUrgent(): Boolean =
    priority == Priority.URGENT || priority == Priority.CRITICAL

// Get media by type
fun Comm.getMediaByType(type: String): List<MediaItem> =
    media.filter { it.type == type }
```

## Serialization

### JSON Serialization

Comm uses Kotlinx Serialization for JSON support:

```kotlin
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

// Serialize to JSON
val comm = Comm(content = "Hello", from = "user")
val json = Json.encodeToString(comm)

// Deserialize from JSON
val comm = Json.decodeFromString<Comm>(json)

// Pretty print
val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}.encodeToString(comm)
```

**JSON Example:**

```json
{
  "id": "comm-abc123",
  "content": "Hello, world!",
  "from": "user-123",
  "to": "agent-456",
  "type": "TEXT",
  "role": "USER",
  "timestamp": 1705334400000,
  "conversationId": "conv-789",
  "thread": null,
  "parentId": null,
  "data": {
    "model": "gpt-4",
    "tokens": "150"
  },
  "media": [],
  "mentions": ["@alice"],
  "reactions": [],
  "priority": "NORMAL",
  "encrypted": false,
  "ttl": null,
  "expiresAt": null
}
```

### Persistence Patterns

**Database Storage:**

```kotlin
// Using JSONB in PostgreSQL
@Entity
@Table(name = "comms")
class CommEntity(
    @Id
    val id: String,

    @Column(columnDefinition = "jsonb")
    val commJson: String,

    val timestamp: Long,
    val conversationId: String?,

    @Column(name = "from_user")
    val fromUser: String,

    val type: String
) {
    companion object {
        fun from(comm: Comm): CommEntity {
            return CommEntity(
                id = comm.id,
                commJson = Json.encodeToString(comm),
                timestamp = comm.timestamp,
                conversationId = comm.conversationId,
                fromUser = comm.from,
                type = comm.type.name
            )
        }
    }

    fun toComm(): Comm {
        return Json.decodeFromString(commJson)
    }
}

// Repository
interface CommRepository : JpaRepository<CommEntity, String> {
    fun findByConversationIdOrderByTimestamp(conversationId: String): List<CommEntity>
    fun findByFromUserAndTimestampAfter(fromUser: String, timestamp: Long): List<CommEntity>
}
```

## Advanced Patterns

### Comm Filtering

```kotlin
// Filter by type
fun filterByType(comms: List<Comm>, type: CommType): List<Comm> =
    comms.filter { it.type == type }

// Filter by role
fun filterByRole(comms: List<Comm>, role: CommRole): List<Comm> =
    comms.filter { it.role == role }

// Filter by date range
fun filterByDateRange(
    comms: List<Comm>,
    startTime: Long,
    endTime: Long
): List<Comm> =
    comms.filter { it.timestamp in startTime..endTime }

// Complex filtering
fun filterComms(
    comms: List<Comm>,
    predicate: (Comm) -> Boolean
): List<Comm> = comms.filter(predicate)

// Usage
val errors = filterComms(comms) { it.isError() }
val urgentFromUser = filterComms(comms) {
    it.from == "user-123" && it.isUrgent()
}
```

### Comm Transformation

```kotlin
// Transform pipeline
class CommPipeline {
    private val transformers = mutableListOf<(Comm) -> Comm>()

    fun addTransformer(transformer: (Comm) -> Comm): CommPipeline {
        transformers.add(transformer)
        return this
    }

    fun process(comm: Comm): Comm {
        return transformers.fold(comm) { current, transformer ->
            transformer(current)
        }
    }
}

// Usage
val pipeline = CommPipeline()
    .addTransformer { it.withData("processed", "true") }
    .addTransformer { it.withData("timestamp", Instant.now().toString()) }
    .addTransformer { comm ->
        if (comm.content.contains("urgent")) comm.urgent() else comm
    }

val processed = pipeline.process(originalComm)
```

### Comm Routing

```kotlin
class CommRouter {
    private val routes = mutableMapOf<(Comm) -> Boolean, suspend (Comm) -> Unit>()

    fun route(predicate: (Comm) -> Boolean, handler: suspend (Comm) -> Unit) {
        routes[predicate] = handler
    }

    suspend fun handle(comm: Comm) {
        val route = routes.entries.find { it.key(comm) }
        if (route != null) {
            route.value(comm)
        } else {
            handleDefault(comm)
        }
    }

    private suspend fun handleDefault(comm: Comm) {
        println("No route found for comm: ${comm.id}")
    }
}

// Usage
val router = CommRouter()

router.route({ it.isError() }) { comm ->
    errorHandler.handle(comm)
}

router.route({ it.isTool() }) { comm ->
    toolExecutor.execute(comm)
}

router.route({ it.priority == Priority.CRITICAL }) { comm ->
    emergencyHandler.handle(comm)
}

router.handle(incomingComm)
```

### Batching & Aggregation

```kotlin
// Batch processing
class CommBatcher(
    private val batchSize: Int,
    private val processor: suspend (List<Comm>) -> Unit
) {
    private val buffer = mutableListOf<Comm>()

    suspend fun add(comm: Comm) {
        buffer.add(comm)
        if (buffer.size >= batchSize) {
            flush()
        }
    }

    suspend fun flush() {
        if (buffer.isNotEmpty()) {
            processor(buffer.toList())
            buffer.clear()
        }
    }
}

// Aggregation
fun aggregateByConversation(comms: List<Comm>): Map<String, ConversationSummary> {
    return comms
        .groupBy { it.conversationId ?: "no-conversation" }
        .mapValues { (_, convComms) ->
            ConversationSummary(
                messageCount = convComms.size,
                participants = convComms.map { it.from }.distinct(),
                startTime = convComms.minOf { it.timestamp },
                endTime = convComms.maxOf { it.timestamp },
                errorCount = convComms.count { it.isError() },
                toolCalls = convComms.count { it.type == CommType.TOOL_CALL }
            )
        }
}

data class ConversationSummary(
    val messageCount: Int,
    val participants: List<String>,
    val startTime: Long,
    val endTime: Long,
    val errorCount: Int,
    val toolCalls: Int
)
```

## Real-World Examples

### Example 1: Customer Support System

```kotlin
class CustomerSupportSystem {
    private val agents = mapOf(
        "billing" to billingAgent,
        "technical" to technicalAgent,
        "general" to generalAgent
    )

    suspend fun handleCustomerMessage(
        message: String,
        customerId: String,
        conversationId: String
    ): Comm {
        // Create customer message
        val customerComm = comm(message) {
            from(customerId)
            conversation(conversationId)
            data("customer_tier" to "premium")
            data("timestamp" to Instant.now().toString())
        }

        // Classify message
        val category = classifyMessage(message)
        val agent = agents[category] ?: agents["general"]!!

        // Create routing message
        val routingComm = customerComm.copy(to = agent.id)
            .withData("category", category)
            .withData("routed_at", Instant.now().toString())

        // Agent processes
        val agentResponse = agent.processComm(routingComm).getOrThrow()

        // Reply to customer
        return routingComm.reply(
            content = agentResponse.content,
            from = agent.id,
            data = mapOf(
                "agent_type" to category,
                "processing_time_ms" to "450",
                "confidence" to "0.95"
            )
        )
    }

    private fun classifyMessage(message: String): String {
        return when {
            message.contains("payment") || message.contains("invoice") -> "billing"
            message.contains("error") || message.contains("not working") -> "technical"
            else -> "general"
        }
    }
}
```

### Example 2: Multi-Agent Research System

```kotlin
class ResearchCoordinator {
    private val researchAgent = buildAgent { /* ... */ }
    private val analysisAgent = buildAgent { /* ... */ }
    private val summaryAgent = buildAgent { /* ... */ }

    suspend fun conductResearch(
        topic: String,
        userId: String
    ): Comm {
        val conversationId = "research-${UUID.randomUUID()}"

        // Initial request
        val initialComm = comm(topic) {
            from(userId)
            conversation(conversationId)
            data("task_type" to "research")
            urgent()
        }

        // Research phase
        val researchComm = initialComm.copy(to = researchAgent.id)
        val researchResult = researchAgent.processComm(researchComm).getOrThrow()

        // Analysis phase - forward research results
        val analysisComm = researchResult.forward(to = analysisAgent.id)
            .withData("phase", "analysis")
        val analysisResult = analysisAgent.processComm(analysisComm).getOrThrow()

        // Summary phase - create consolidated message
        val consolidatedContent = """
            Research Results:
            ${researchResult.content}

            Analysis:
            ${analysisResult.content}
        """.trimIndent()

        val summaryComm = comm(consolidatedContent) {
            from("coordinator")
            to(summaryAgent.id)
            conversation(conversationId)
            thread(initialComm.id)
            data("phase" to "summary")
            data("research_papers" to researchResult.data["papers_found"].orEmpty())
            data("analysis_insights" to analysisResult.data["insights_count"].orEmpty())
        }

        val summaryResult = summaryAgent.processComm(summaryComm).getOrThrow()

        // Final response to user
        return initialComm.reply(
            content = summaryResult.content,
            from = "coordinator",
            type = CommType.RESULT,
            data = mapOf(
                "research_agent" to researchResult.from,
                "analysis_agent" to analysisResult.from,
                "summary_agent" to summaryResult.from,
                "total_processing_time_ms" to "3500",
                "confidence" to "0.92"
            )
        )
    }
}
```

### Example 3: Event-Driven Workflow

```kotlin
class WorkflowEngine {
    private val eventBus = mutableListOf<(Comm) -> Unit>()

    fun subscribe(handler: (Comm) -> Unit) {
        eventBus.add(handler)
    }

    fun publish(comm: Comm) {
        eventBus.forEach { it(comm) }
    }

    suspend fun executeWorkflow(
        workflowId: String,
        input: String
    ): Comm {
        val conversationId = "workflow-$workflowId"

        // Start workflow
        val startComm = comm(input) {
            from("workflow-engine")
            conversation(conversationId)
            type(CommType.WORKFLOW_START)
            data("workflow_id" to workflowId)
            data("started_at" to Instant.now().toString())
        }
        publish(startComm)

        try {
            // Step 1: Validation
            val validationComm = startComm.reply(
                content = "Validating input...",
                from = "validator",
                data = mapOf("step" to "1", "status" to "processing")
            )
            publish(validationComm)

            val validated = validateInput(input)
            val validatedComm = validationComm.reply(
                content = "Validation complete",
                from = "validator",
                data = mapOf("step" to "1", "status" to "complete", "valid" to "true")
            )
            publish(validatedComm)

            // Step 2: Processing
            val processingComm = validatedComm.reply(
                content = "Processing data...",
                from = "processor",
                data = mapOf("step" to "2", "status" to "processing")
            )
            publish(processingComm)

            val result = processData(validated)
            val processedComm = processingComm.reply(
                content = "Processing complete: $result",
                from = "processor",
                data = mapOf("step" to "2", "status" to "complete", "result_size" to "1024")
            )
            publish(processedComm)

            // Step 3: Finalization
            val finalComm = processedComm.reply(
                content = "Workflow complete: $result",
                from = "workflow-engine",
                type = CommType.WORKFLOW_END,
                data = mapOf(
                    "workflow_id" to workflowId,
                    "completed_at" to Instant.now().toString(),
                    "steps_completed" to "3",
                    "duration_ms" to "2500"
                )
            )
            publish(finalComm)

            return finalComm

        } catch (e: Exception) {
            // Error handling
            val errorComm = startComm.error(
                message = "Workflow failed: ${e.message}",
                from = "workflow-engine"
            ).withData(
                "workflow_id" to workflowId,
                "error_type" to e::class.simpleName.orEmpty(),
                "failed_at" to Instant.now().toString()
            )
            publish(errorComm)

            return errorComm
        }
    }

    private suspend fun validateInput(input: String): String = input
    private suspend fun processData(input: String): String = "processed-$input"
}
```

## Testing Strategies

### Unit Testing

```kotlin
class CommTest {

    @Test
    fun `test reply creates proper thread`() {
        val original = Comm(content = "Hello", from = "user")
        val reply = original.reply("Hi", "agent")

        assertEquals(original.id, reply.parentId)
        assertEquals(original.id, reply.thread)
        assertEquals("user", reply.to)
        assertEquals("agent", reply.from)
    }

    @Test
    fun `test expiration works correctly`() {
        val comm = Comm(content = "Test", from = "user")
            .expires(100) // 100ms

        assertFalse(comm.isExpired())

        Thread.sleep(150)

        assertTrue(comm.isExpired())
    }

    @Test
    fun `test metadata accumulation`() {
        val comm = Comm(content = "Test", from = "user")
            .withData("key1", "value1")
            .withData("key2", "value2")

        assertEquals("value1", comm.data["key1"])
        assertEquals("value2", comm.data["key2"])
    }

    @Test
    fun `test tool call creation`() {
        val original = Comm(content = "Calculate", from = "user")
        val toolCall = original.toolCall(
            toolName = "calculator",
            params = mapOf("a" to 1, "b" to 2),
            from = "agent"
        )

        assertEquals(CommType.TOOL_CALL, toolCall.type)
        assertEquals("calculator", toolCall.getToolName())
        assertTrue(toolCall.isTool())
    }
}
```

### Integration Testing

```kotlin
class CommIntegrationTest {

    @Test
    fun `test full conversation flow`() = runBlocking {
        val conversationId = "test-conv-${UUID.randomUUID()}"
        val comms = mutableListOf<Comm>()

        // User message
        val userMsg = Comm(
            content = "Hello",
            from = "user",
            conversationId = conversationId
        )
        comms.add(userMsg)

        // Agent reply
        val agentReply = userMsg.reply("Hi there!", "agent")
        comms.add(agentReply)

        // User follow-up
        val followUp = agentReply.reply("How are you?", "user")
        comms.add(followUp)

        // Verify conversation structure
        assertEquals(3, comms.size)
        assertTrue(comms.all { it.conversationId == conversationId })
        assertEquals(userMsg.id, agentReply.thread)
        assertEquals(userMsg.id, followUp.thread)
    }
}
```

## Performance Considerations

### Memory Management

```kotlin
// Avoid holding large conversation histories in memory
// Use pagination and streaming

class ConversationManager {
    private val maxInMemory = 100

    fun trimOldMessages(comms: MutableList<Comm>) {
        if (comms.size > maxInMemory) {
            // Archive old messages
            val toArchive = comms.take(comms.size - maxInMemory)
            archiveMessages(toArchive)

            // Keep recent messages
            comms.retainAll { it.timestamp > getRecentThreshold() }
        }
    }

    private fun getRecentThreshold(): Long {
        return Instant.now().toEpochMilli() - 3600000 // 1 hour
    }
}
```

### Efficient Serialization

```kotlin
// Use lazy JSON serialization
class CommStorage {
    private val cache = ConcurrentHashMap<String, String>()

    fun store(comm: Comm) {
        val json = cache.computeIfAbsent(comm.id) {
            Json.encodeToString(comm)
        }
        persistToDatabase(comm.id, json)
    }
}
```

### Batch Operations

```kotlin
// Process comms in batches
suspend fun processBatch(comms: List<Comm>) {
    comms.chunked(50).forEach { batch ->
        // Process 50 at a time
        batch.map { comm ->
            async { processComm(comm) }
        }.awaitAll()
    }
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
// ✅ Good - Rich metadata with native types (v0.9.0)
val comm = agent.processComm(input).getOrThrow()
    .withData(
        "model" to "gpt-4",
        "tokens" to 150,              // v0.9.0: Int
        "duration_ms" to 1200,        // v0.9.0: Int
        "cost_usd" to 0.003,          // v0.9.0: Double
        "success" to true             // v0.9.0: Boolean
    )

// ❌ Bad - No context
val comm = agent.processComm(input).getOrThrow()
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

### 6. Validate Content

```kotlin
// ✅ Good - Validate before processing
fun validateComm(comm: Comm): Boolean {
    return comm.content.isNotBlank() &&
           comm.from.isNotBlank() &&
           !comm.isExpired()
}

if (validateComm(comm)) {
    processMessage(comm)
}

// ❌ Bad - Process without validation
processMessage(comm) // Might fail with invalid data
```

### 7. Use Immutability

```kotlin
// ✅ Good - Create new instances
val updated = comm
    .withData("key", "value")
    .urgent()
    .encrypt()

// ❌ Bad - Don't try to modify (Comm is immutable)
// comm.data["key"] = "value" // Won't compile!
```

## Troubleshooting

### Common Issues

**Issue: Thread not maintained in replies**
```kotlin
// Problem: Manual threading
val reply = Comm(...)

// Solution: Use reply() method
val reply = original.reply(content = "...", from = "agent")
```

**Issue: Lost conversation context**
```kotlin
// Problem: Not preserving conversationId
val newComm = Comm(content = "...", from = "agent")

// Solution: Copy from original or use reply
val newComm = original.reply(content = "...", from = "agent")
// Automatically preserves conversationId
```

**Issue: Serialization failures**
```kotlin
// Problem: Complex objects in data map
val comm = Comm(...).withData("object", complexObject.toString())

// Solution: Serialize properly
val json = Json.encodeToString(complexObject)
val comm = Comm(...).withData("object", json)
```

## Migration from Legacy Message

### Message → Comm Mapping

```kotlin
// Old Message API
val message = Message(
    content = "Hello",
    role = MessageRole.USER
)

// New Comm API
val comm = Comm(
    content = "Hello",
    from = "user",
    role = CommRole.USER
)
```

### Migration Helper

```kotlin
fun Message.toComm(from: String = "user"): Comm {
    return Comm(
        content = this.content,
        from = from,
        role = when (this.role) {
            MessageRole.USER -> CommRole.USER
            MessageRole.ASSISTANT -> CommRole.ASSISTANT
            MessageRole.SYSTEM -> CommRole.SYSTEM
        }
    )
}

fun Comm.toMessage(): Message {
    return Message(
        content = this.content,
        role = when (this.role) {
            CommRole.USER -> MessageRole.USER
            CommRole.ASSISTANT -> MessageRole.ASSISTANT
            CommRole.SYSTEM -> MessageRole.SYSTEM
            CommRole.AGENT -> MessageRole.ASSISTANT
            CommRole.TOOL -> MessageRole.SYSTEM
        }
    )
}
```

## Next Steps

- [Agent API](./agent) - Learn about agents
- [Tool API](./tool) - Learn about tools
- [DSL Guide](../dsl-guide/overview) - Build agents with DSL
- [Error Handling](../error-handling/overview) - Handle comm errors
- [Context Propagation](../advanced/context-propagation) - Trace comm flow across agents
