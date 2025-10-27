# Graph System

**Added in:** 0.5.0

The **Graph System** is a powerful orchestration framework inspired by Microsoft's Agent Framework, enabling you to build complex, multi-step AI workflows with fine-grained control over execution flow, error handling, and state management.

## Overview

The Graph System provides three core abstractions:

- **Node**: A unit of work (Agent, Tool, or custom logic)
- **Graph**: A directed acyclic graph (DAG) connecting nodes
- **Runner**: Executes the graph with middleware support

```kotlin
// Simple example
val graph = graph("my-workflow") {
    agent("analyzer", analysisAgent)
    tool("processor", processorTool) { mapOf("input" to it.state["analyzer"]) }
    output("result") { it.state["processor"] }
}

val report = DefaultGraphRunner().run(graph, mapOf("input" to "data")).getOrThrow()
```

## Key Features

### 🔗 Flexible Node Types
- **AgentNode**: Execute any Spice Agent
- **ToolNode**: Execute any Spice Tool
- **OutputNode**: Transform and output results
- **Custom Nodes**: Implement `Node` interface

### 🎯 Smart Execution Flow
- **Sequential execution** with automatic state management
- **Conditional edges** for dynamic routing
- **Multiple paths** from a single node

### 🛡️ Robust Error Handling
- **ErrorAction** system (RETRY, SKIP, CONTINUE, PROPAGATE)
- **Automatic retry** with configurable limits
- **Graceful degradation** with SKIP/CONTINUE

### 💾 Checkpoint & Resume
- **Save execution state** at any point
- **Resume from failure** without re-executing completed nodes
- **Configurable checkpointing** (every N nodes, on error, time-based)

### 🔍 Graph Validation
- **Pre-execution validation** catches errors early
- **Cycle detection** ensures DAG structure
- **Unreachable node** detection
- **Invalid reference** checking

### 🎨 Middleware System
- **Intercept execution** at graph and node level
- **Metrics collection** with `onNode` hooks
- **Custom error handling** with `onError` hooks
- **Lifecycle hooks**: `onStart`, `onFinish`

### 🌐 Context Propagation
- **AgentContext** flows through all nodes automatically
- **Multi-tenant support** built-in
- **Correlation IDs** for distributed tracing

## Architecture

```
┌─────────────────────────────────────────┐
│         GraphRunner                      │
│  ┌────────────────────────────────────┐ │
│  │  Middleware Chain                  │ │
│  │  • onStart                         │ │
│  │  • onNode (for each node)          │ │
│  │  • onError (on failures)           │ │
│  │  • onFinish                        │ │
│  └────────────────────────────────────┘ │
│                                          │
│  ┌────────────────────────────────────┐ │
│  │  Graph Execution                   │ │
│  │  • Validate graph structure        │ │
│  │  • Execute nodes sequentially      │ │
│  │  • Handle errors with ErrorAction  │ │
│  │  • Save checkpoints (optional)     │ │
│  └────────────────────────────────────┘ │
└─────────────────────────────────────────┘
               ↓
    ┌─────────────────────┐
    │   Node Execution    │
    │  • AgentNode        │
    │  • ToolNode         │
    │  • OutputNode       │
    │  • Custom Node      │
    └─────────────────────┘
               ↓
    ┌─────────────────────┐
    │  AgentContext       │
    │  (auto-propagated)  │
    └─────────────────────┘
```

## Quick Start

### 1. Define a Simple Graph

```kotlin
val graph = graph("greeting-workflow") {
    // Agent node: processes input
    agent("greeter", greetingAgent)

    // Output node: transforms result
    output("result") { ctx ->
        ctx.state["greeter"]
    }
}
```

### 2. Execute the Graph

```kotlin
val runner = DefaultGraphRunner()
val result = runner.run(
    graph = graph,
    input = mapOf("name" to "Alice")
)

when (result) {
    is SpiceResult.Success -> println("Result: ${result.value.result}")
    is SpiceResult.Failure -> println("Error: ${result.error.message}")
}
```

### 3. Access Execution Report

```kotlin
val report = result.getOrThrow()
println("Graph: ${report.graphId}")
println("Status: ${report.status}")
println("Duration: ${report.duration}")
println("Nodes executed: ${report.nodeReports.size}")

report.nodeReports.forEach { nodeReport ->
    println("  - ${nodeReport.nodeId}: ${nodeReport.status} (${nodeReport.duration})")
}
```

## Multi-Step Workflow Example

```kotlin
val workflow = graph("data-processing") {
    // Step 1: Validate input
    tool("validator", validationTool) { ctx ->
        mapOf("data" to ctx.state["input"])
    }

    // Step 2: Process with AI
    agent("processor", processingAgent)

    // Step 3: Store results
    tool("storage", storageTool) { ctx ->
        mapOf(
            "validation" to ctx.state["validator"],
            "processed" to ctx.state["processor"]
        )
    }

    // Output combined result
    output("summary") { ctx ->
        mapOf(
            "validation" to ctx.state["validator"],
            "processing" to ctx.state["processor"],
            "storage" to ctx.state["storage"]
        )
    }
}
```

## Conditional Routing

```kotlin
val graph = graph("conditional-workflow") {
    // Decision node
    agent("classifier", classificationAgent)

    // Route A
    agent("route-a", routeAAgent)

    // Route B
    agent("route-b", routeBAgent)

    output("result") { it.state["_previous"] }

    // Custom edges with conditions
    edges {
        edge("classifier", "route-a") { result ->
            result.data == "type-a"
        }
        edge("classifier", "route-b") { result ->
            result.data == "type-b"
        }
        edge("route-a", "result")
        edge("route-b", "result")
    }
}
```

## Error Handling with Middleware

```kotlin
val retryMiddleware = object : Middleware {
    override suspend fun onError(
        err: Throwable,
        ctx: RunContext
    ): ErrorAction {
        return when {
            err.message?.contains("retry") == true -> ErrorAction.RETRY
            err.message?.contains("skip") == true -> ErrorAction.SKIP
            else -> ErrorAction.PROPAGATE
        }
    }
}

val graph = Graph(
    id = "resilient-workflow",
    nodes = nodes,
    edges = edges,
    entryPoint = "start",
    middleware = listOf(retryMiddleware)
)
```

## Checkpoint & Resume

```kotlin
val store = InMemoryCheckpointStore()
val config = CheckpointConfig(
    saveEveryNNodes = 5,
    saveOnError = true
)

// Run with checkpointing
val result = runner.runWithCheckpoint(graph, input, store, config)

// Later: resume from failure
if (result.isFailure) {
    val checkpoints = store.listByGraph(graph.id).getOrThrow()
    val latestCheckpoint = checkpoints.first()

    val resumeResult = runner.resume(graph, latestCheckpoint.id, store)
}
```

## Best Practices

### ✅ Do's

1. **Validate graphs** before deployment
2. **Use meaningful node IDs** for debugging
3. **Leverage middleware** for cross-cutting concerns
4. **Enable checkpointing** for long-running workflows
5. **Use conditional edges** for dynamic routing
6. **Propagate context** for multi-tenant scenarios

### ❌ Don'ts

1. **Don't create cycles** - graphs must be DAGs
2. **Don't ignore validation errors** - they catch issues early
3. **Don't skip error handling** - use ErrorAction appropriately
4. **Don't forget to clean up checkpoints** - they're cleaned automatically on success

## Next Steps

- Learn about [Graph Nodes](./graph-nodes.md)
- Explore [Middleware System](./graph-middleware.md)
- Master [Checkpoint & Resume](./graph-checkpoint.md)
- Understand [Graph Validation](./graph-validation.md)

## Related

- [Multi-Agent Orchestration](./multi-agent.md)
- [Tool Pipelines](./tool-pipeline.md)
- [Error Handling](../error-handling/overview.md)
- [Advanced Context Propagation](../advanced/context-propagation.md)
