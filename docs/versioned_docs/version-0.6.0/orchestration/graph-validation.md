# Graph Validation

**Added in:** 0.5.0

Graph Validation ensures your workflows are structurally sound before execution, catching errors early and providing clear feedback.

## Overview

The validation system checks for:
- **Empty graphs** - At least one node required
- **Invalid entry points** - Entry point must exist
- **Invalid edge references** - All edges must reference existing nodes
- **Cycles** - Graphs must be DAGs (Directed Acyclic Graphs)
- **Unreachable nodes** - All nodes must be reachable from entry point

## Automatic Validation

Validation happens automatically before execution:

```kotlin
val graph = graph("my-graph") {
    // ... define nodes and edges
}

// Validation runs before execution
val result = runner.run(graph, input)

// If validation fails, returns SpiceResult.Failure
when (result) {
    is SpiceResult.Success -> println("Success!")
    is SpiceResult.Failure -> {
        // ValidationError with details
        println("Error: ${result.error.message}")
    }
}
```

## Manual Validation

You can validate graphs explicitly:

```kotlin
val validation = GraphValidator.validate(graph)

when (validation) {
    is SpiceResult.Success -> println("Graph is valid!")
    is SpiceResult.Failure -> {
        val error = validation.error as SpiceError.ValidationError
        println("Validation failed: ${error.message}")

        // Access detailed errors
        val errors = error.context["errors"] as List<String>
        errors.forEach { println("  - $it") }
    }
}
```

## Validation Rules

### Rule 1: Non-Empty Graph

Graphs must have at least one node.

```kotlin
// ❌ Invalid: Empty graph
val invalid = Graph(
    id = "empty",
    nodes = emptyMap(),  // No nodes!
    edges = emptyList(),
    entryPoint = "start"
)

// Error: "Graph must have at least one node"
```

### Rule 2: Valid Entry Point

Entry point must reference an existing node.

```kotlin
// ❌ Invalid: Non-existent entry point
val invalid = Graph(
    id = "bad-entry",
    nodes = mapOf("node1" to someNode),
    edges = emptyList(),
    entryPoint = "nonexistent"  // Doesn't exist!
)

// Error: "Entry point 'nonexistent' does not exist in graph"
```

### Rule 3: Valid Edge References

All edges must reference existing nodes.

```kotlin
// ❌ Invalid: Edge to non-existent node
val invalid = Graph(
    id = "bad-edge",
    nodes = mapOf("node1" to someNode),
    edges = listOf(
        Edge("node1", "node2")  // node2 doesn't exist!
    ),
    entryPoint = "node1"
)

// Error: "Edge references non-existent 'to' node: node2"
```

### Rule 4: No Cycles (DAG)

Graphs must be Directed Acyclic Graphs - no cycles allowed.

```kotlin
// ❌ Invalid: Cycle in graph
val invalid = Graph(
    id = "cyclic",
    nodes = mapOf(
        "node1" to someNode,
        "node2" to someNode,
        "node3" to someNode
    ),
    edges = listOf(
        Edge("node1", "node2"),
        Edge("node2", "node3"),
        Edge("node3", "node1")  // Cycle!
    ),
    entryPoint = "node1"
)

// Error: "Graph contains cycles involving nodes: node1, node2, node3"
```

**Why DAG?**
- Predictable execution order
- No infinite loops
- Clear data flow
- Easier to reason about

### Rule 5: No Unreachable Nodes

All nodes must be reachable from the entry point.

```kotlin
// ❌ Invalid: Orphan node
val invalid = Graph(
    id = "orphan",
    nodes = mapOf(
        "node1" to someNode,
        "node2" to someNode,
        "orphan" to someNode  // Not connected!
    ),
    edges = listOf(
        Edge("node1", "node2")
    ),
    entryPoint = "node1"
)

// Error: "Graph contains unreachable nodes: orphan"
```

## GraphValidator API

### validate()

Validates entire graph structure:

```kotlin
fun validate(graph: Graph): SpiceResult<Unit>
```

**Returns:**
- `SpiceResult.Success` if valid
- `SpiceResult.Failure` with `ValidationError` if invalid

### isDAG()

Checks if graph is a valid DAG:

```kotlin
fun isDAG(graph: Graph): Boolean
```

```kotlin
if (!GraphValidator.isDAG(graph)) {
    println("Graph contains cycles!")
}
```

### findTerminalNodes()

Find nodes with no outgoing edges:

```kotlin
fun findTerminalNodes(graph: Graph): List<String>
```

```kotlin
val terminals = GraphValidator.findTerminalNodes(graph)
println("Terminal nodes: $terminals")
// Useful for finding end points
```

## Validation Examples

### Example 1: Validate Before Deployment

```kotlin
fun deployGraph(graph: Graph): Result<Unit> {
    // Validate before deploying to production
    val validation = GraphValidator.validate(graph)

    return when (validation) {
        is SpiceResult.Success -> {
            // Graph is valid, proceed with deployment
            deployToProduction(graph)
            Result.success(Unit)
        }
        is SpiceResult.Failure -> {
            // Log validation errors
            logger.error("Graph validation failed: ${validation.error.message}")
            Result.failure(Exception(validation.error.message))
        }
    }
}
```

### Example 2: CI/CD Validation

```kotlin
@Test
fun `test all production graphs are valid`() {
    val graphs = listOf(
        createUserWorkflow(),
        createDataProcessingWorkflow(),
        createAnalyticsWorkflow()
    )

    graphs.forEach { graph ->
        val result = GraphValidator.validate(graph)
        assertTrue(result.isSuccess, "Graph ${graph.id} should be valid")
    }
}
```

### Example 3: Development-Time Checks

```kotlin
fun createWorkflow(): Graph {
    val graph = graph("my-workflow") {
        agent("step1", agent1)
        agent("step2", agent2)
        agent("step3", agent3)
        output("result") { it.state["step3"] }
    }

    // Validate immediately during development
    require(GraphValidator.validate(graph).isSuccess) {
        "Graph validation failed"
    }

    return graph
}
```

### Example 4: Interactive Validation

```kotlin
fun validateAndReport(graph: Graph) {
    println("🔍 Validating graph: ${graph.id}")

    val result = GraphValidator.validate(graph)

    when (result) {
        is SpiceResult.Success -> {
            println("✅ Graph is valid!")
            println("   Nodes: ${graph.nodes.size}")
            println("   Edges: ${graph.edges.size}")
            println("   Entry: ${graph.entryPoint}")

            val terminals = GraphValidator.findTerminalNodes(graph)
            println("   Terminals: $terminals")

            val isDAG = GraphValidator.isDAG(graph)
            println("   Is DAG: $isDAG")
        }
        is SpiceResult.Failure -> {
            println("❌ Graph is invalid!")
            val error = result.error as SpiceError.ValidationError
            println("   Message: ${error.message}")

            val errors = error.context["errors"] as? List<String>
            errors?.forEach { err ->
                println("   - $err")
            }
        }
    }
}
```

## Error Messages

Validation errors are detailed and actionable:

```
Graph validation failed: Graph must have at least one node

Graph validation failed: Entry point 'start' does not exist in graph

Graph validation failed: Edge references non-existent 'from' node: node1

Graph validation failed: Edge references non-existent 'to' node: node2

Graph validation failed: Graph contains cycles involving nodes: node1, node2, node3

Graph validation failed: Graph contains unreachable nodes: orphan1, orphan2
```

Multiple errors are combined:

```
Graph validation failed: Entry point 'start' does not exist in graph;
Edge references non-existent 'to' node: node2;
Graph contains unreachable nodes: orphan
```

## Cycle Detection Algorithm

The validator uses **Depth-First Search (DFS)** with a recursion stack:

```
1. Mark node as visiting (recursion stack)
2. For each neighbor:
   - If neighbor is in recursion stack → Cycle found!
   - If neighbor not visited → Recursively visit
3. Mark node as visited
4. Remove from recursion stack
```

**Time Complexity:** O(V + E) where V = nodes, E = edges

## Self-Loop Detection

Self-loops (node pointing to itself) are automatically detected as cycles:

```kotlin
// ❌ Invalid: Self-loop
val invalid = Graph(
    id = "self-loop",
    nodes = mapOf("node1" to someNode),
    edges = listOf(
        Edge("node1", "node1")  // Self-loop!
    ),
    entryPoint = "node1"
)

// Error: "Graph contains cycles involving nodes: node1"
```

## Complex Cycle Example

```kotlin
// ❌ Invalid: Complex cycle
val invalid = graph("complex-cycle") {
    agent("a", agent1)
    agent("b", agent2)
    agent("c", agent3)
    agent("d", agent4)

    edges {
        edge("a", "b")
        edge("b", "c")
        edge("c", "d")
        edge("d", "b")  // Creates cycle: b → c → d → b
    }
}

// Error: "Graph contains cycles involving nodes: b, c, d"
```

## Best Practices

### ✅ Do's

1. **Validate early** - In development, not just production
2. **Add validation tests** - Test graphs in CI/CD
3. **Use meaningful IDs** - Easier to debug validation errors
4. **Check terminal nodes** - Ensure workflows have clear end points
5. **Document graph structure** - Especially for complex workflows

### ❌ Don'ts

1. **Don't skip validation** - Runtime errors are harder to debug
2. **Don't ignore warnings** - They indicate potential issues
3. **Don't create complex graphs without testing** - Start small
4. **Don't modify graphs after validation** - Re-validate if changed
5. **Don't suppress validation errors** - Fix the root cause

## Validation in Production

### Strategy 1: Validate on Load

```kotlin
class GraphRepository {
    fun loadGraph(id: String): Graph {
        val graph = loadFromDatabase(id)

        // Validate before returning
        val validation = GraphValidator.validate(graph)
        if (validation.isFailure) {
            throw IllegalStateException("Loaded invalid graph: $id")
        }

        return graph
    }
}
```

### Strategy 2: Validate on Create

```kotlin
class GraphBuilder {
    fun build(): Graph {
        val graph = Graph(
            id = id,
            nodes = nodes,
            edges = edges,
            entryPoint = entryPoint
        )

        // Validate immediately
        val validation = GraphValidator.validate(graph)
        require(validation.isSuccess) {
            "Failed to build graph: ${validation.exceptionOrNull()?.message}"
        }

        return graph
    }
}
```

### Strategy 3: Pre-Deployment Gate

```kotlin
fun deployWorkflow(graph: Graph) {
    // Gate 1: Validation
    val validation = GraphValidator.validate(graph)
    if (validation.isFailure) {
        throw DeploymentException("Validation failed")
    }

    // Gate 2: Additional checks
    if (graph.nodes.size > 100) {
        throw DeploymentException("Graph too large")
    }

    // Deploy
    deploy(graph)
}
```

## Troubleshooting

### Issue: "Entry point does not exist"

**Problem:** Entry point ID doesn't match any node ID

**Solution:**
```kotlin
// Check node IDs match entry point
println("Entry point: ${graph.entryPoint}")
println("Node IDs: ${graph.nodes.keys}")
```

### Issue: "Graph contains cycles"

**Problem:** Circular dependencies in graph

**Solution:**
```kotlin
// Use isDAG to confirm
if (!GraphValidator.isDAG(graph)) {
    // Manually check edges for cycles
    graph.edges.forEach { edge ->
        println("${edge.from} → ${edge.to}")
    }
}
```

### Issue: "Unreachable nodes"

**Problem:** Nodes not connected to entry point

**Solution:**
```kotlin
// Check connectivity
fun printReachability(graph: Graph) {
    val reachable = mutableSetOf<String>()

    fun dfs(nodeId: String) {
        if (nodeId in reachable) return
        reachable.add(nodeId)
        graph.edges.filter { it.from == nodeId }
            .forEach { dfs(it.to) }
    }

    dfs(graph.entryPoint)

    val unreachable = graph.nodes.keys - reachable
    println("Unreachable: $unreachable")
}
```

## Next Steps

- Learn [Error Handling Strategies](../error-handling/overview.md)
- Review [Graph Middleware](./graph-middleware.md)
- Explore [Performance Optimization](../performance/overview.md)

## Related

- [Graph System Overview](./graph-system.md)
- [Graph Nodes](./graph-nodes.md)
- [Graph Checkpoint](./graph-checkpoint.md)
- [Testing Best Practices](../core-concepts/testing.md)
