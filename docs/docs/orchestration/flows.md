# Flow Orchestration

Coordinate multiple agents with flows.

## Sequential Flow

```kotlin
val flow = buildFlow {
    id = "sequential"
    step("step1", "agent1")
    step("step2", "agent2")
    step("step3", "agent3")
}
```

## Conditional Flow

```kotlin
val flow = buildFlow {
    id = "conditional"

    step("validate", "validator") { comm ->
        comm.content.isNotEmpty()
    }

    step("process", "processor") { comm ->
        comm.data["valid"] == "true"
    }
}
```
