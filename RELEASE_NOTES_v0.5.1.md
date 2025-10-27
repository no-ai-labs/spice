# Spice Framework v0.5.1 Release Notes

**Release Date**: October 28, 2025
**Type**: Patch Release
**Theme**: AgentNode Metadata Propagation Fix

---

## 🎯 Executive Summary

Spice Framework v0.5.1 is a **patch release** that fixes a critical bug in AgentNode metadata propagation. This release ensures that `Comm.data` metadata automatically propagates across all agents in a graph, enabling seamless metadata sharing between agents without manual intervention.

### Quick Highlights

- 🐛 **Bug Fix**: AgentNode now properly propagates `Comm.data` metadata across graph nodes
- 📝 **Documentation**: Comprehensive documentation updates for metadata propagation
- ✅ **Tests**: Added 2 new integration tests for metadata propagation
- 🔄 **No Breaking Changes**: Fully backward compatible with 0.5.0

---

## 🐛 Bug Fixes

### AgentNode Metadata Propagation

**Issue**: In v0.5.0, when agents added metadata to their response Comm via the `data` field, this metadata was lost when passing to the next agent in the graph. Only the `content` string was propagated.

**Root Cause**: AgentNode was creating new Comm instances without copying the previous Comm's `data` map, causing metadata to be lost between nodes.

**Fix**: AgentNode now:
1. Extracts the previous Comm from `ctx.state["_previousComm"]`
2. Copies the `data` map to the new Comm
3. Stores the full response Comm back to `_previousComm` for the next node

**Example - Before (0.5.0):**

```kotlin
// Agent 1 adds metadata
val agent1 = object : Agent {
    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        return SpiceResult.success(
            comm.reply(
                content = "Step 1",
                from = id,
                data = mapOf("sessionId" to "session-123")  // Added metadata
            )
        )
    }
}

// Agent 2 tries to access metadata
val agent2 = object : Agent {
    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        val sessionId = comm.data["sessionId"]  // ❌ Was null in 0.5.0!
        return SpiceResult.success(comm.reply("Step 2", id))
    }
}

val graph = graph("chain") {
    agent("step1", agent1)
    agent("step2", agent2)  // Metadata was lost here
}
```

**Example - After (0.5.1):**

```kotlin
// Same agents as above

val graph = graph("chain") {
    agent("step1", agent1)
    agent("step2", agent2)  // ✅ Metadata automatically propagated!
}

// agent2 now receives: comm.data["sessionId"] == "session-123"
```

**Technical Details**:

```kotlin
// AgentNode.kt implementation (simplified)
override suspend fun run(ctx: NodeContext): SpiceResult<NodeResult> {
    val inputContent = ctx.state["_previous"]?.toString() ?: ""

    // 🆕 Extract previous metadata
    val previousComm = ctx.state["_previousComm"] as? Comm
    val previousData = previousComm?.data ?: emptyMap()

    // 🆕 Create Comm with propagated metadata
    val comm = Comm(
        content = inputContent,
        from = "graph-${ctx.graphId}",
        context = ctx.agentContext,
        data = previousData  // ✅ Metadata propagation!
    )

    return agent.processComm(comm)
        .map { response ->
            // 🆕 Store full Comm for next node
            ctx.state["_previousComm"] = response

            NodeResult(data = response.content, metadata = mapOf(...))
        }
}
```

**Impact**:
- ✅ **High**: Fixes critical bug that prevented agent-to-agent metadata sharing
- ✅ **Automatic**: No code changes needed - metadata propagation "just works"
- ✅ **Backward Compatible**: Existing graphs continue to work

**Why This Matters**:
- 🔗 **Session tracking**: Pass session IDs through multi-agent workflows
- 📊 **Request tracing**: Track request IDs across agent chains
- 🎯 **Context sharing**: Share arbitrary metadata between agents
- 🔄 **Accumulated state**: Build up metadata as it flows through the graph

---

## ✅ New Tests

### Metadata Propagation Tests

Added 2 comprehensive integration tests in `GraphIntegrationTest.kt`:

**1. `test metadata propagation across agent nodes`**
- Tests 3-agent chain where each agent adds and verifies metadata
- Agent 1 adds `"agent1": "metadata1", "step": "1"`
- Agent 2 verifies Agent 1's metadata, adds `"agent2": "metadata2", "step": "2"`
- Agent 3 verifies all previous metadata is accessible
- ✅ **Result**: All metadata propagates correctly through the chain

**2. `test metadata propagation with initial data`**
- Tests initializing a graph with pre-existing metadata
- Creates initial Comm with `"initialKey": "initialValue"`
- Agent accesses this initial metadata in first node
- ✅ **Result**: Initial metadata is accessible to first agent

**Test Results**:
```bash
./gradlew :spice-core:test --tests "*metadata propagation*"

GraphIntegrationTest > test metadata propagation with initial data() PASSED
GraphIntegrationTest > test metadata propagation across agent nodes() PASSED

BUILD SUCCESSFUL
```

**Coverage**:
- ✅ **3-node chain** with metadata accumulation
- ✅ **Initial metadata** injection
- ✅ **Metadata merging** in replies
- ✅ **Full graph execution** with metadata

---

## 📚 Documentation Updates

### Updated Documentation Files

**1. `orchestration/graph-nodes.md`**

Added comprehensive "Internal Behavior: State & Metadata Propagation" section:
- Explains how `_previousComm` convention works
- Provides step-by-step flow diagrams
- Shows 3-agent chain example with metadata accumulation
- Documents `enricher → consumer` pattern
- Explains initial metadata injection

**Key Additions**:
```markdown
:::info Internal Behavior: State & Metadata Propagation

**Critical:** AgentNode stores **only `Comm.content`** in state for downstream nodes,
but automatically propagates `Comm.data` metadata across the graph!

// What's stored in state:
// state["processor"] = "result text"    // ✅ String (content only)
// state["_previousComm"] = Comm(...)    // ✅ Full Comm (for metadata)
:::
```

**2. `core-concepts/comm.md`**

Expanded "Adding Metadata" section with:
- Detailed explanation of `data` field usage
- "Metadata in Replies" subsection showing automatic merging
- "Metadata Propagation in Graphs" subsection with examples
- 3-agent example showing sessionId and userId propagation
- Cross-reference to graph-nodes.md

**Key Additions**:
```markdown
#### Metadata Propagation in Graphs

When using AgentNode in graphs, **metadata automatically propagates** across all agents:

```kotlin
val graph = graph("metadata-flow") {
    agent("enricher", enricherAgent)   // Adds metadata
    agent("processor", processorAgent) // Receives + adds metadata
    agent("finalizer", finalizerAgent) // Receives all metadata
}

// Each agent automatically receives metadata from previous agents!
```
```

**Files Updated**:
- `docs/docs/orchestration/graph-nodes.md`
- `docs/docs/core-concepts/comm.md`
- `docs/versioned_docs/version-0.5.0/orchestration/graph-nodes.md`
- `docs/versioned_docs/version-0.5.0/core-concepts/comm.md`

**Total Documentation**: ~400 new lines of comprehensive metadata propagation documentation

---

## 🔄 Migration Guide

### No Migration Required! 🎉

This is a **bug fix release** - no code changes needed:

✅ **If you're on 0.5.0**: Just update to 0.5.1 and metadata propagation will work automatically

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.noailabs:spice-core:0.5.1")  // Update version
}
```

✅ **Existing code benefits immediately**: Any agents that were adding metadata via `comm.reply(data = ...)` will now have that metadata propagate correctly

✅ **No API changes**: All APIs remain identical to 0.5.0

**Recommended Actions**:
1. Update to 0.5.1
2. Review your agents to see if they can benefit from metadata sharing
3. Consider adding session IDs, request IDs, or tracing metadata

---

## 📦 Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("io.github.noailabs:spice-core:0.5.1")
    implementation("io.github.noailabs:spice-extensions-sparql:0.5.1")
    implementation("io.github.noailabs:spice-eventsourcing:0.5.1")
    implementation("io.github.noailabs:spice-springboot:0.5.1")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'io.github.noailabs:spice-core:0.5.1'
    implementation 'io.github.noailabs:spice-extensions-sparql:0.5.1'
    implementation 'io.github.noailabs:spice-eventsourcing:0.5.1'
    implementation 'io.github.noailabs:spice-springboot:0.5.1'
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.noailabs</groupId>
    <artifactId>spice-core</artifactId>
    <version>0.5.1</version>
</dependency>
```

---

## 🔗 Resources

- **Documentation**: https://docs.spice.noailabs.io
- **GitHub**: https://github.com/no-ai-labs/spice
- **Changelog**: [CHANGELOG.md](CHANGELOG.md)
- **Graph Nodes Documentation**: [docs/orchestration/graph-nodes.md](docs/versioned_docs/version-0.5.0/orchestration/graph-nodes.md)
- **Comm Documentation**: [docs/core-concepts/comm.md](docs/versioned_docs/version-0.5.0/core-concepts/comm.md)

---

## 📅 What's Next

### Upcoming in v0.5.2

- Performance optimizations for large graphs
- Additional middleware hooks
- Enhanced error reporting

### Upcoming in v0.6.0

- Streaming support for graph execution
- Multi-graph composition
- Advanced observability integrations (OpenTelemetry)
- Graph versioning and migration tools

---

## 🙏 Thank You

Thank you to everyone who reported issues and provided feedback on the metadata propagation behavior!

**Happy Building!** 🚀

---

*Spice Framework - Build intelligent agent systems with confidence*
