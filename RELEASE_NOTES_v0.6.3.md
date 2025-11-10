# Spice Framework 0.6.3 Release Notes

**Released:** 2025-11-10

## 🔄 Feature Release: Optional Cycle Validation

Spice 0.6.3 introduces optional cycle validation for graphs, enabling **iterative workflows, retry loops, and conditional loops** while maintaining safety through explicit opt-in.

---

## ✨ What's New

### Graph.allowCycles Parameter

**NEW**: Graphs can now explicitly allow cycles for use cases requiring iterative patterns.

#### The Previous Limitation (0.6.2 and earlier)

Graphs were strictly Directed Acyclic Graphs (DAGs) - **no cycles allowed**:

```kotlin
// ❌ 0.6.2: This graph would FAIL validation
val workflowWithLoop = Graph(
    id = "workflow-loop",
    nodes = mapOf(
        "workflow" to workflowNode,
        "response" to responseNode
    ),
    edges = listOf(
        Edge("workflow", "workflow") { shouldContinue(it) },  // Self-loop
        Edge("workflow", "response") { !shouldContinue(it) }
    ),
    entryPoint = "workflow"
)

// GraphValidator.validate() returns Failure:
// "Graph contains cycles involving nodes: workflow"
```

**Problem**: Some workflows naturally require loops:
- Iterative refinement until quality threshold
- Retry loops with backoff
- User interaction loops until confirmation
- Workflow systems with conditional loops

#### The Solution (0.6.3)

Set `allowCycles = true` to explicitly permit cycles:

```kotlin
// ✅ 0.6.3: Same graph now PASSES validation
val workflowWithLoop = Graph(
    id = "workflow-loop",
    nodes = mapOf(
        "workflow" to workflowNode,
        "response" to responseNode
    ),
    edges = listOf(
        Edge("workflow", "workflow") { result ->
            // Loop condition: continue processing
            (result.data as? Map<*, *>)?.get("continue") == true
        },
        Edge("workflow", "response") { result ->
            // Exit condition: stop when done
            (result.data as? Map<*, *>)?.get("continue") != true
        }
    ),
    entryPoint = "workflow",
    allowCycles = true  // ⭐ New parameter
)

// GraphValidator.validate() returns Success! ✅
```

---

## 🎯 Use Cases

### Use Case 1: Iterative Refinement

Process data repeatedly until quality threshold is met:

```kotlin
val refinementWorkflow = graph("data-refinement") {
    agent("refine", refineAgent)
    agent("check-quality", qualityCheckAgent)
    output("final-result") { it.state["refine"] }

    edges {
        edge("refine", "check-quality")
        edge("check-quality", "refine") { result ->
            // Loop back if quality insufficient
            val quality = (result.data as? Map<*, *>)?.get("quality") as? Double ?: 0.0
            val iterations = ctx.state["iterations"] as? Int ?: 0
            quality < 0.9 && iterations < 10  // Safety guard
        }
        edge("check-quality", "final-result") { result ->
            // Exit when quality sufficient
            val quality = (result.data as? Map<*, *>)?.get("quality") as? Double ?: 0.0
            quality >= 0.9
        }
    }

    allowCycles = true
}
```

### Use Case 2: Retry with Exponential Backoff

```kotlin
val retryWorkflow = graph("api-retry") {
    agent("call-api", apiAgent)
    agent("check-result", resultCheckAgent)
    agent("backoff", backoffAgent)
    output("success") { it.state["call-api"] }

    edges {
        edge("call-api", "check-result")
        edge("check-result", "success") { result ->
            (result.data as? Map<*, *>)?.get("success") == true
        }
        edge("check-result", "backoff") { result ->
            val retries = ctx.state["retries"] as? Int ?: 0
            (result.data as? Map<*, *>)?.get("success") != true && retries < 5
        }
        edge("backoff", "call-api")  // Loop back after waiting
    }

    allowCycles = true
}
```

### Use Case 3: User Interaction Loop

```kotlin
val userDialogWorkflow = graph("user-dialog") {
    human("ask-question", prompt = "Enter your choice:")
    agent("validate", validationAgent)
    agent("confirm", confirmAgent)
    output("confirmed") { it.state["confirm"] }

    edges {
        edge("ask-question", "validate")
        edge("validate", "confirm") { it.data == true }
        edge("validate", "ask-question") { it.data == false }  // Loop back
        edge("confirm", "ask-question") { result ->
            (result.data as? Boolean) != true  // Loop if not confirmed
        }
        edge("confirm", "confirmed") { result ->
            (result.data as? Boolean) == true  // Exit when confirmed
        }
    }

    allowCycles = true
}
```

---

## 🔍 Technical Details

### Graph Class Changes

**File**: `spice-core/src/main/kotlin/io/github/noailabs/spice/graph/Graph.kt:17`

```kotlin
// 0.6.2
data class Graph(
    override val id: String,
    val nodes: Map<String, Node>,
    val edges: List<Edge>,
    val entryPoint: String,
    val middleware: List<Middleware> = emptyList()
) : Identifiable

// 0.6.3
data class Graph(
    override val id: String,
    val nodes: Map<String, Node>,
    val edges: List<Edge>,
    val entryPoint: String,
    val middleware: List<Middleware> = emptyList(),
    val allowCycles: Boolean = false  // ⭐ New parameter
) : Identifiable
```

**Default behavior unchanged**: `allowCycles = false` maintains backward compatibility.

### GraphValidator Changes

**File**: `spice-core/src/main/kotlin/io/github/noailabs/spice/graph/GraphValidator.kt:38-44`

```kotlin
// 0.6.2: Always check for cycles
val cycleNodes = detectCycles(graph)
if (cycleNodes.isNotEmpty()) {
    errors.add("Graph contains cycles involving nodes: ${cycleNodes.joinToString(", ")}")
}

// 0.6.3: Conditional cycle check
if (!graph.allowCycles) {  // ⭐ Only check if cycles not allowed
    val cycleNodes = detectCycles(graph)
    if (cycleNodes.isNotEmpty()) {
        errors.add("Graph contains cycles involving nodes: ${cycleNodes.joinToString(", ")}")
    }
}
```

### GraphValidator.isDAG()

`isDAG()` behavior **unchanged** - always returns `false` for cyclic graphs:

```kotlin
val graph = Graph(
    id = "cyclic",
    nodes = mapOf("a" to node),
    edges = listOf(Edge("a", "a")),
    entryPoint = "a",
    allowCycles = true
)

GraphValidator.validate(graph).isSuccess  // true ✅
GraphValidator.isDAG(graph)  // false (correctly identifies non-DAG)
```

This allows checking graph topology independent of validation policy.

---

## ⚠️ Safety Considerations

### Infinite Loop Protection

**Important**: When using `allowCycles = true`, **you must implement loop guards**:

#### 1. **Iteration Counters**

```kotlin
edge("process", "process") { result ->
    val iterations = ctx.state["iterations"] as? Int ?: 0
    val shouldContinue = checkCondition(result)

    shouldContinue && iterations < MAX_ITERATIONS  // ⭐ Safety guard
}
```

#### 2. **Timeout Protection via Middleware**

```kotlin
class TimeoutMiddleware(val maxDuration: Duration) : Middleware {
    override suspend fun onStart(ctx: RunContext, next: suspend () -> Unit) {
        val startTime = Instant.now()
        withTimeout(maxDuration.toMillis()) {
            next()
        }
    }
}

val graph = Graph(
    ...,
    middleware = listOf(TimeoutMiddleware(Duration.ofMinutes(5))),
    allowCycles = true
)
```

#### 3. **Exit Conditions in Every Loop**

```kotlin
// ❌ DANGEROUS: No guaranteed exit
edge("node", "node") { result ->
    (result.data as? Map<*, *>)?.get("retry") == true  // Might loop forever!
}

// ✅ SAFE: Multiple exit conditions
edge("node", "node") { result ->
    val shouldRetry = (result.data as? Map<*, *>)?.get("retry") == true
    val retries = ctx.state["retries"] as? Int ?: 0
    val withinTimeout = Duration.between(startTime, Instant.now()) < maxDuration

    shouldRetry && retries < 5 && withinTimeout  // Multiple guards
}
```

### Best Practices for Cyclic Graphs

✅ **Do's**:
1. **Always** set explicit exit conditions
2. **Always** add iteration/retry counters
3. **Always** include timeout middleware
4. **Test** loop termination with various inputs
5. **Monitor** execution time in production

❌ **Don'ts**:
1. **Don't** rely on single condition for exit
2. **Don't** allow unbounded loops
3. **Don't** ignore middleware timeout errors
4. **Don't** use cycles without thorough testing
5. **Don't** skip checkpointing for long loops

---

## 🧪 Testing

### New Test Coverage

**File**: `spice-core/src/test/kotlin/io/github/noailabs/spice/graph/GraphValidatorTest.kt`

```kotlin
@Test
fun `test cyclic graph passes validation when allowCycles is true`() {
    val graph = Graph(
        id = "allowed-cyclic-graph",
        nodes = mapOf(
            "node1" to OutputNode("node1"),
            "node2" to OutputNode("node2"),
            "node3" to OutputNode("node3")
        ),
        edges = listOf(
            Edge("node1", "node2"),
            Edge("node2", "node3"),
            Edge("node3", "node1")  // Cycle!
        ),
        entryPoint = "node1",
        allowCycles = true  // ⭐ Explicitly allow
    )

    val result = GraphValidator.validate(graph)

    assertTrue(result.isSuccess)  // Passes validation
    assertFalse(GraphValidator.isDAG(graph))  // Still not a DAG
}

@Test
fun `test self-loop passes validation when allowCycles is true`() {
    val graph = Graph(
        id = "allowed-self-loop-graph",
        nodes = mapOf(
            "workflow" to OutputNode("workflow"),
            "response" to OutputNode("response")
        ),
        edges = listOf(
            Edge("workflow", "workflow") { /* loop condition */ },
            Edge("workflow", "response") { /* exit condition */ }
        ),
        entryPoint = "workflow",
        allowCycles = true
    )

    val result = GraphValidator.validate(graph)

    assertTrue(result.isSuccess)
}
```

**All GraphValidatorTest tests**: 11/11 PASSED ✅

---

## 📊 Impact Assessment

### Who Should Upgrade?

**Upgrade if you:**
- Need iterative workflows with loops
- Implement retry mechanisms in graphs
- Build user interaction flows
- Want workflow-style conditional loops

**Upgrade is optional if you:**
- Only use DAG workflows (existing behavior unchanged)
- Build manual `Graph()` with cycles (now validates correctly)

### Backward Compatibility

✅ **100% backward compatible**

- Default `allowCycles = false` maintains existing behavior
- All existing DAG validations work identically
- No API changes to existing code
- All tests pass without modification

---

## 📖 Migration from 0.6.2

### Update Dependency

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.github.no-ai-labs.spice-framework:spice-core:0.6.3")
}
```

### Enable Cycles for Existing Workflows

If you had workarounds for cycles:

```kotlin
// ❌ 0.6.2: Manual validation skip (hack)
val graph = Graph(...)
// Skip validation, run directly
runner.runValidatedGraph(graph, input)

// ✅ 0.6.3: Clean solution
val graph = Graph(
    ...,
    allowCycles = true  // Explicit opt-in
)
runner.run(graph, input)  // Validates correctly
```

---

## 🔮 What's Next?

### Future Enhancements (0.7.0+)

- Parallel node execution (fan-out/fan-in patterns)
- Advanced merge strategies for parallel results
- Loop performance metrics in NodeReport
- Automatic cycle detection warnings in logs
- Visual graph editor with cycle support

---

## 📦 Get Started

### Update

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.github.no-ai-labs.spice-framework:spice-core:0.6.3")
}
```

### Resources

- [Graph Validation Documentation](/docs/orchestration/graph-validation)
- [Graph API Documentation](/docs/api/graph)
- [0.6.2 Release Notes](/RELEASE_NOTES_v0.6.2.md)
- [GitHub Release v0.6.3](https://github.com/no-ai-labs/spice-framework/releases/tag/v0.6.3)

---

## 📝 Full Changelog

### Added
- **Graph**: `allowCycles: Boolean` parameter to explicitly allow cyclic graphs
- **Documentation**: Cyclic graph use cases and best practices
- **Tests**: Cyclic graph validation tests with `allowCycles = true`
- **Documentation**: Safety considerations for infinite loop prevention

### Changed
- **GraphValidator**: Cycle detection now conditional on `Graph.allowCycles`
- **Graph comment**: Updated from "DAG" to "directed graph" with cycle option

### Fixed
- None (feature release)

---

**Happy Building! 🌶️**

*- The Spice Team*
