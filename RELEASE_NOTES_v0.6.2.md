# Spice Framework 0.6.2 Release Notes

**Released:** 2025-11-05

## 🐛 Patch Release: GraphBuilder Conditional Edge Fix

Spice 0.6.2 fixes a critical bug in the Graph DSL where conditional edges were incorrectly overridden by automatic sequential edges.

---

## 🔧 What's Fixed

### GraphBuilder Conditional Edge Priority

**FIXED**: Conditional edges defined with `edge()` now correctly take precedence over automatic sequential edges.

#### The Problem (0.6.0 - 0.6.1)

When using the Graph DSL with conditional edges, automatic edges were added **before** explicit edges, causing conditions to be ignored:

```kotlin
graph("my-workflow") {
    agent("classifier", classifierAgent)  // Auto-edge created: classifier -> processor
    agent("processor", processorAgent)

    // ❌ These conditional edges were IGNORED!
    edge("classifier", "processor") { result ->
        result.metadata["_previousComm"]?.data["should_process"] == "true"
    }
    edge("classifier", "output") { result ->
        result.metadata["_previousComm"]?.data["should_process"] == "false"
    }
}

// Result: Always went to "processor" regardless of condition!
```

**Symptom:** Edge condition lambdas never executed, graphs always followed sequential flow.

#### The Fix (0.6.2)

Explicit edges now override automatic edges:

```kotlin
graph("my-workflow") {
    agent("classifier", classifierAgent)
    agent("processor", processorAgent)
    agent("output", outputAgent)

    // ✅ These conditional edges NOW WORK!
    edge("classifier", "processor") { result ->
        val comm = result.metadata["_previousComm"] as? Comm
        comm?.data?.get("should_process") == "true"
    }
    edge("classifier", "output") { result ->
        val comm = result.metadata["_previousComm"] as? Comm
        comm?.data?.get("should_process") == "false"
    }

    edge("processor", "output")  // Sequential edge still works
}

// ✅ Now correctly evaluates conditions and routes accordingly!
```

---

## 🔍 Technical Details

### GraphBuilder Implementation

**Changes in `GraphBuilder.kt`:**

1. **Split edge tracking**: Separate `autoEdges` and `explicitEdges` lists
2. **Priority-based merging**: Explicit edges take precedence over auto-edges
3. **Conflict resolution**: Auto-edges from nodes with explicit edges are filtered out

```kotlin
// 0.6.1 (Bug)
class GraphBuilder(val id: String) {
    private val edges = mutableListOf<Edge>()  // Mixed auto + explicit

    fun build(): Graph {
        return Graph(edges = edges)  // ❌ Auto-edges come first!
    }
}

// 0.6.2 (Fixed)
class GraphBuilder(val id: String) {
    private val autoEdges = mutableListOf<Edge>()        // Sequential flow
    private val explicitEdges = mutableListOf<Edge>()    // User-defined conditions

    fun build(): Graph {
        val explicitFromNodes = explicitEdges.map { it.from }.toSet()
        val finalEdges = explicitEdges +
            autoEdges.filterNot { it.from in explicitFromNodes }

        return Graph(edges = finalEdges)  // ✅ Explicit edges first!
    }
}
```

### Edge Evaluation in GraphRunner

The GraphRunner already had correct logic:

```kotlin
// GraphRunner.kt:256-259 (unchanged)
val nextId = graph.edges
    .firstOrNull { edge -> edge.from == nodeId && edge.condition(result) }
    ?.to
```

The issue was in the **edge ordering** provided by GraphBuilder, not the evaluation logic.

---

## 📊 Impact Assessment

### Who Should Upgrade?

**Critical if you:**
- Use `graph { }` DSL with `edge()` conditional routing
- Experience unexpected sequential execution despite conditions
- See edge lambda logs not printing

**Non-critical if you:**
- Build graphs manually with `Graph()` constructor (already worked)
- Use purely sequential workflows (no conditional edges)
- On versions < 0.6.0 (different API)

### Backward Compatibility

✅ **100% backward compatible**

- Sequential graphs without conditional edges work identically
- Manual `Graph()` construction unchanged
- All existing tests pass
- No API changes

---

## 🧪 Testing

### New Test Coverage

Added comprehensive DSL conditional edge test in `ConditionalEdgeTest`:

```kotlin
@Test
fun `test DSL conditional edges override automatic edges`() = runTest {
    val testGraph = graph("dsl-conditional-test") {
        agent("check", checkAgent)
        agent("path-a", pathAAgent)
        agent("path-b", pathBAgent)
        output("result")

        // Conditional routing
        edge("check", "path-a") { result ->
            val comm = result.metadata["_previousComm"] as? Comm
            comm?.data?.get("route") == "path-a"
        }
        edge("check", "path-b") { result ->
            val comm = result.metadata["_previousComm"] as? Comm
            comm?.data?.get("route") == "path-b"
        }
        edge("path-a", "result")
        edge("path-b", "result")
    }

    // Verify path-a for high values
    val highResult = runner.run(testGraph, mapOf("value" to 15))
    assertTrue(pathATaken && !pathBTaken)

    // Verify path-b for low values
    val lowResult = runner.run(testGraph, mapOf("value" to 5))
    assertTrue(!pathATaken && pathBTaken)
}
```

**All ConditionalEdgeTest tests:** 4/4 PASSED ✅

---

## 💡 Best Practices

### Accessing Previous Comm in Edge Conditions

Since `NodeResult.data` contains the node's output (often a String), use `metadata["_previousComm"]` to access the full Comm:

```kotlin
edge("my-node", "next-node") { result ->
    // ✅ CORRECT: Access Comm from metadata
    val comm = result.metadata["_previousComm"] as? Comm
    val shouldContinue = comm?.data?.get("continue") == "true"
    shouldContinue
}

edge("my-node", "error-handler") { result ->
    // ❌ WRONG: result.data is String, not Comm
    val comm = result.data as? Comm  // null!
    comm?.data?.get("error") == "true"
}
```

### Logging Edge Decisions

Add debug logging to understand routing:

```kotlin
edge("classifier", "processor") { result ->
    val comm = result.metadata["_previousComm"] as? Comm
    val shouldProcess = comm?.data?.get("should_process") == "true"

    logger.info { "🔀 [Edge] classifier -> processor: $shouldProcess" }
    logger.debug { "  Comm data: ${comm?.data}" }

    shouldProcess
}
```

### Combining Sequential and Conditional Edges

Mix sequential flow with conditional branches:

```kotlin
graph("hybrid-flow") {
    agent("start", startAgent)      // Sequential
    agent("decision", decisionAgent)

    // Conditional fork
    edge("decision", "path-a") { isPathA(it) }
    edge("decision", "path-b") { isPathB(it) }

    agent("path-a", pathAAgent)
    agent("path-b", pathBAgent)

    // Sequential merge
    edge("path-a", "end")
    edge("path-b", "end")

    agent("end", endAgent)
}
```

---

## 📖 Migration from 0.6.1

### No Action Required! 🎉

Simply update your dependency:

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.github.no-ai-labs.spice-framework:spice-core:0.6.2")
}
```

Your conditional edges will now work correctly without code changes.

### Verification

If you had workarounds, you can now remove them:

```kotlin
// ❌ 0.6.1 Workaround (manual Graph construction)
fun createGraph(): Graph {
    return Graph(
        id = "my-graph",
        nodes = mapOf(...),
        edges = listOf(
            Edge("a", "b") { condition }
        ),
        entryPoint = "a"
    )
}

// ✅ 0.6.2 Clean DSL (now works!)
val graph = graph("my-graph") {
    agent("a", agentA)
    agent("b", agentB)
    edge("a", "b") { condition }
}
```

---

## 🔮 What's Next?

### Future Enhancements (0.7.0+)

- Named edge conditions for debugging
- Edge visualization tools
- Conditional edge validation at build time
- Multi-condition edge syntax

---

## 📦 Get Started

### Update

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.github.no-ai-labs.spice-framework:spice-core:0.6.2")
}
```

### Resources

- [Graph API Documentation](/docs/api/graph)
- [Conditional Edge Patterns](/docs/guides/conditional-routing)
- [0.6.1 Release Notes](/RELEASE_NOTES_v0.6.1.md)
- [GitHub Issue #22](https://github.com/no-ai-labs/spice-framework/issues/22) (if created)

---

## 📝 Full Changelog

### Fixed
- **Graph DSL**: Conditional edges now correctly override automatic sequential edges
- **GraphBuilder**: Explicit `edge()` calls take priority over auto-generated edges from sequential node declarations

### Added
- **Test**: `ConditionalEdgeTest.test DSL conditional edges override automatic edges()`
- **Documentation**: Best practices for edge conditions in release notes

### Changed
- **GraphBuilder**: Internal edge tracking split into `autoEdges` and `explicitEdges`
- **GraphBuilder.build()**: Priority-based edge merging algorithm

---

**Happy Building! 🌶️**

*- The Spice Team*
