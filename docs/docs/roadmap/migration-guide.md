# Migration Guide: 0.4.x → 0.5.0

:::danger Breaking Changes
Spice 0.5.0 introduces **breaking API changes**. This guide helps you migrate from 0.4.x to the new graph-based runtime.
:::

## Quick Start

### Automated Migration

```bash
# Run migration tool
./gradlew spiceMigrate --from 0.4 --to 0.5 --dry-run

# Review and apply
./gradlew spiceMigrate --from 0.4 --to 0.5 --apply
```

## Breaking Changes Summary

| Area | 0.4.x | 0.5.0 | Action |
|------|-------|-------|--------|
| **Orchestration** | `swarm {}`, `flow {}` | `graph {}` | Replace with graph nodes |
| **Context** | `context.get("key")` | `ctx.state["key"]` | Update access pattern |
| **Execution** | `swarm().run()` | `graph().run()` | Update API call |

## Migration Examples

### Swarm → Graph

**Before (0.4.x):**
```kotlin
swarm("support") {
  agent("classifier", classifierAgent)
  agent("technical", technicalAgent)
  handoff("classifier" to "technical") { ctx ->
    ctx["category"] == "technical"
  }
}
```

**After (0.5.0):**
```kotlin
graph("support") {
  agent("classifier", classifierAgent)
  decision("routeByCategory") { ctx ->
    ctx["category"] == "technical"
  }
  branch("routeByCategory") {
    onTrue { agent("technical", technicalAgent) }
    onFalse { agent("general", generalAgent) }
  }
}
```

### Flow → Graph

**Before (0.4.x):**
```kotlin
flow("processing") {
  step("fetch") { fetchData() }
  step("transform") { transformData(it) }
}
```

**After (0.5.0):**
```kotlin
graph("processing") {
  tool("fetch", fetchTool)
  tool("transform", transformTool)
  output("result")
}
```

## Getting Help

- **GitHub Issues**: [Report bugs](https://github.com/no-ai-labs/spice/issues)
- **Discord**: Join our migration channel
- **Docs**: [Architecture Spec](./af-architecture.md)

## Rollback Plan

If migration fails, revert to 0.4.x:

```kotlin
// build.gradle.kts
dependencies {
  implementation("io.github.noailabs:spice-core:0.4.4")
}
```

**0.4.x LTS Support**: 6 months
