# Agent Framework Architecture Specification

## Overview

This document details the **Microsoft Agent Framework-inspired architecture** for Spice 0.5.0.

## Core Abstractions

### Node Interface

Every execution unit is a `Node`:

```kotlin
sealed interface Node {
  val id: String
  suspend fun run(ctx: NodeContext): NodeResult
}

data class NodeResult(
  val data: Any?,
  val metadata: Map<String, Any> = emptyMap()
)
```

### Node Types

- **AgentNode**: LLM agent execution
- **ToolNode**: Tool invocation
- **DecisionNode**: Conditional branching
- **ParallelNode**: Concurrent execution
- **HumanNode**: Human approval/input
- **OutputNode**: Final output

## Graph Model

```kotlin
class Graph(
  val id: String,
  val nodes: Map<String, Node>,
  val edges: List<Edge>,
  val middleware: List<Middleware> = emptyList()
)
```

## Middleware System

```kotlin
interface Middleware {
  suspend fun onStart(ctx: RunContext, next: suspend () -> Unit)
  suspend fun onNode(req: NodeRequest, next: suspend (NodeRequest) -> NodeResult): NodeResult
  suspend fun onError(err: Throwable, ctx: RunContext): ErrorAction
  suspend fun onFinish(report: RunReport)
}
```

### Built-in Middleware

- OpenTelemetryMiddleware
- CostMeterMiddleware  
- OutputValidationMiddleware
- SecurityMiddleware

## Checkpoint System

```kotlin
interface CheckpointStore {
  suspend fun save(runId: String, nodeId: String, state: Map<String, Any>): String
  suspend fun load(checkpointId: String): Checkpoint
  suspend fun resume(graph: Graph, checkpointId: String): RunReport
}
```

## Next Steps

Read the [Migration Guide](./migration-guide.md) for detailed migration instructions.
