---
id: merge-strategies
title: Merge Strategies API
sidebar_label: Merge Strategies
description: Built-in helpers for combining parallel node results in Spice 1.0.0 graphs.
---

# Merge Strategies

When you fan out work with `parallel { ... }`, each branch yields a `SpiceMessage`. Merge strategies describe how those results roll up into a single message that continues through the graph. Spice 1.0.0 ships a small set of composable helpers so you can pick the semantics that fit your workload.

```kotlin
import io.github.noailabs.spice.graph.merge.MergeStrategy
import io.github.noailabs.spice.graph.merge.MergeStrategies

val strategy: MergeStrategy = MergeStrategies.combine {
    // 1) keep metadata from the primary branch
    selectPrimary("llm")

    // 2) merge tool outputs under a single key
    mergeData("analysis") { results ->
        results.map { it.data["analysis"] }
    }

    // 3) aggregate errors for observability
    collectErrors()
}

graph {
    parallel(strategy = strategy) {
        branch("llm") { callLlmNode() }
        branch("retrieval") { runRetrievalNode() }
    }
}
```

## Built-in Strategies

| Strategy | Purpose |
| --- | --- |
| `MergeStrategies.first()` | Pick the first completed branch and drop the rest (fastest-wins). |
| `MergeStrategies.last()` | Always forward the most recent branch (useful for override flows). |
| `MergeStrategies.combine { ... }` | Declarative builder that lets you mix `selectPrimary`, `mergeData`, `collectErrors`, and custom reducers. |
| `MergeStrategies.custom { a, b -> ... }` | Provide your own reducer for full control. |

All strategies implement the same `MergeStrategy` interface, so you can swap them via configuration or share them in your own module.

## When to Customize

- **Fan-out Tooling** – Combine multiple tool outputs into a single map (e.g., search + reasoning + enrichment).
- **Voting Systems** – Evaluate several agents and keep the highest-scoring result.
- **Observability** – Attach branch-specific metadata/errors to the message before it continues.

Need a refresher on parallel nodes? Head back to [Parallel Execution](../orchestration/parallel-execution.md).
