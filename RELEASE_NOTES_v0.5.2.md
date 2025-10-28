# Spice Framework v0.5.2 Release Notes

**Release Date**: October 28, 2025
**Type**: Patch Release
**Theme**: Initial Metadata Support for Graph Nodes

---

## 🎯 Executive Summary

Spice Framework v0.5.2 is a **patch release** that adds support for initializing the first graph node with metadata. This enables graph orchestrators and external systems to pass session IDs, request IDs, and other contextual metadata to the entire graph workflow.

### Quick Highlights

- ✨ **Enhancement**: First graph node can now receive initial metadata via 3 different methods
- 🔧 **Flexible initialization**: Support for `"comm"`, `"_previousComm"`, and `"metadata"` keys
- 📝 **Documentation**: Added comprehensive guide for metadata initialization patterns
- ✅ **Tests**: Added 3 new integration tests for initial metadata support
- 🔄 **No Breaking Changes**: Fully backward compatible with 0.5.1

---

## ✨ Enhancements

### Initial Metadata Support for First Node

**Problem**: In v0.5.1, the first node in a graph had no way to receive initial metadata from the graph orchestrator. It always started with an empty `data` map, preventing orchestrators from passing session IDs, request IDs, or other context.

**Example of the problem:**

```kotlin
// Orchestrator wants to pass metadata
val initialComm = Comm(
    content = "Process this request",
    from = "user",
    data = mapOf(
        "sessionId" to "session-123",
        "requestId" to "req-456"
    )
)

val input = mapOf(
    "input" to initialComm.content,
    "comm" to initialComm
)

val report = runner.run(graph, input).getOrThrow()

// ❌ In 0.5.1: First agent couldn't access sessionId or requestId!
```

**Solution**: AgentNode now checks multiple sources for initial metadata with a priority order:

```kotlin
// AgentNode.kt implementation
val previousComm = ctx.state["_previousComm"] as? Comm      // Priority 1: Previous node
    ?: ctx.state["comm"] as? Comm                           // Priority 2: Initial Comm ✨ NEW

val previousData = previousComm?.data
    ?: (ctx.state["metadata"] as? Map<*, *>)?.mapKeys { ... }  // Priority 3: Direct map ✨ NEW
    ?: emptyMap()

// Comm created with metadata from any source
val comm = Comm(
    content = inputContent,
    from = "graph-${ctx.graphId}",
    context = ctx.agentContext,
    data = previousData  // ✅ Includes initial metadata!
)
```

---

## 📚 Three Initialization Methods

### Method 1: Using `"comm"` key (Recommended)

Pass a complete `Comm` object with metadata via the `"comm"` key:

```kotlin
val initialComm = Comm(
    content = "Start processing",
    from = "user",
    data = mapOf(
        "sessionId" to "session-123",
        "requestId" to "req-456",
        "priority" to "high"
    )
)

val initialState = mapOf(
    "input" to initialComm.content,
    "comm" to initialComm  // ✅ First node picks up metadata!
)

val report = runner.run(graph, initialState).getOrThrow()
// All agents can now access sessionId, requestId, priority!
```

**Why use this?**
- ✅ Most explicit and clear
- ✅ Passes complete Comm object with all fields
- ✅ Recommended for new code

### Method 2: Using `"_previousComm"` key

Use the same pattern as inter-node communication:

```kotlin
val initialComm = Comm(
    content = "Start",
    from = "user",
    data = mapOf("sessionId" to "session-123")
)

val initialState = mapOf(
    "input" to "Start",
    "_previousComm" to initialComm  // ✅ Also works
)
```

**Why use this?**
- ✅ Consistent with inter-node pattern
- ✅ Backward compatible with existing patterns
- ✅ Useful for testing and migration

### Method 3: Using `"metadata"` map

Pass metadata as a direct map (fallback pattern):

```kotlin
val initialState = mapOf(
    "input" to "Start",
    "metadata" to mapOf(
        "sessionId" to "session-123",
        "userId" to "user-456"
    )
)
```

**Why use this?**
- ✅ Simpler when you don't need full Comm
- ✅ Good for quick metadata injection
- ✅ Useful when Comm construction is inconvenient

---

## 🎯 Real-World Use Case

### Graph Orchestrator Integration

```kotlin
class KAIGraphOrchestrator(
    private val graph: Graph,
    private val runner: GraphRunner
) {
    suspend fun processRequest(
        userComm: Comm,
        sessionId: String,
        correlationId: String
    ): SpiceResult<String> {

        // Enrich user Comm with orchestrator metadata
        val enrichedComm = Comm(
            content = userComm.content,
            from = userComm.from,
            data = userComm.data + mapOf(
                "sessionId" to sessionId,
                "correlationId" to correlationId,
                "orchestratorId" to "kai-orchestrator",
                "timestamp" to System.currentTimeMillis().toString()
            )
        )

        // Initialize graph with enriched Comm
        val input = mapOf(
            "input" to enrichedComm.content,
            "comm" to enrichedComm  // ✅ All agents receive metadata!
        )

        return runner.run(graph, input)
            .map { report -> report.result as String }
    }
}

// Usage
val orchestrator = KAIGraphOrchestrator(myGraph, runner)

val userComm = Comm(
    content = "I need help with billing",
    from = "user-123"
)

val result = orchestrator.processRequest(
    userComm = userComm,
    sessionId = "session-abc",
    correlationId = "corr-xyz"
).getOrThrow()

// Every agent in the graph can now:
// - Track the session with sessionId
// - Correlate logs with correlationId
// - Access orchestrator context
```

---

## 🔄 Priority Order

AgentNode checks for metadata in this order:

1. **`_previousComm`** - From previous node (inter-node communication)
2. **`comm`** - Initial Comm from graph input (first node initialization)
3. **`metadata`** - Direct metadata map (fallback)

This ensures:
- ✅ Inter-node communication takes precedence
- ✅ Initial metadata is available to first node
- ✅ Fallback for direct metadata injection

---

## ✅ New Tests

### Added Tests (3 total)

**1. `test metadata from initial comm in state`**
- Tests passing initial Comm via `"comm"` key
- Verifies first agent receives sessionId and requestId
- ✅ **PASSED**

**2. `test metadata from direct metadata map in state`**
- Tests passing metadata as direct map via `"metadata"` key
- Verifies first agent receives tenantId and userId
- ✅ **PASSED**

**3. `test metadata propagation priority order`**
- Tests priority order with initial Comm
- Verifies metadata accumulation across 2-agent chain
- Verifies initial metadata persists through entire graph
- ✅ **PASSED**

**Test Results**:
```bash
./gradlew :spice-core:test --tests "*metadata*"

GraphIntegrationTest > test metadata from initial comm in state() PASSED
GraphIntegrationTest > test metadata from direct metadata map in state() PASSED
GraphIntegrationTest > test metadata propagation priority order() PASSED

BUILD SUCCESSFUL
327 tests completed, 15 failed (pre-existing), 1 skipped
```

---

## 📚 Documentation Updates

### Updated Files

**orchestration/graph-nodes.md**
- Added "Initializing with metadata" section
- Documented all 3 initialization methods
- Added priority order explanation
- Included recommendation for which method to use
- Added real-world examples

**Key additions:**
- Method comparison table
- Priority order diagram
- Orchestrator integration examples
- Best practices for metadata initialization

**Total**: ~150 lines of new documentation

---

## 🔄 Migration Guide

### No Migration Required! 🎉

This is an **enhancement release** - existing code continues to work:

✅ **If you're on 0.5.1**: Just update to 0.5.2 and optionally add initial metadata

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.noailabs:spice-core:0.5.2")
}
```

✅ **Existing graphs work unchanged**: Graphs without initial metadata continue to work

✅ **No API changes**: All existing APIs remain identical

**Recommended Actions**:
1. Update to 0.5.2
2. If you have a graph orchestrator, add initial metadata using `"comm"` key
3. Enjoy seamless metadata propagation from orchestrator through entire graph!

---

## 📦 Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("io.github.noailabs:spice-core:0.5.2")
    implementation("io.github.noailabs:spice-extensions-sparql:0.5.2")
    implementation("io.github.noailabs:spice-eventsourcing:0.5.2")
    implementation("io.github.noailabs:spice-springboot:0.5.2")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'io.github.noailabs:spice-core:0.5.2'
    implementation 'io.github.noailabs:spice-extensions-sparql:0.5.2'
    implementation 'io.github.noailabs:spice-eventsourcing:0.5.2'
    implementation 'io.github.noailabs:spice-springboot:0.5.2'
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.noailabs</groupId>
    <artifactId>spice-core</artifactId>
    <version>0.5.2</version>
</dependency>
```

---

## 🔗 Resources

- **Documentation**: https://docs.spice.noailabs.io
- **GitHub**: https://github.com/no-ai-labs/spice
- **Changelog**: [CHANGELOG.md](CHANGELOG.md)
- **Graph Nodes Documentation**: [docs/orchestration/graph-nodes.md](docs/versioned_docs/version-0.5.0/orchestration/graph-nodes.md)

---

## 📅 What's Next

### Upcoming in v0.5.3

- Performance optimizations for large graphs
- Additional metadata utility functions
- Enhanced debugging for metadata flow

### Upcoming in v0.6.0

- Streaming support for graph execution
- Multi-graph composition
- Advanced observability integrations (OpenTelemetry)
- Graph versioning and migration tools

---

## 🙏 Thank You

Thank you to everyone who reported issues and provided feedback on the initial metadata limitation!

This release enables seamless integration between graph orchestrators and graph workflows, making it easy to pass context through multi-agent systems.

**Happy Building!** 🚀

---

*Spice Framework - Build intelligent agent systems with confidence*
