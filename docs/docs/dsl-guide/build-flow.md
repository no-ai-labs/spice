# Building Flows

Create multi-agent workflows with the buildFlow DSL.

## Basic Flow

```kotlin
val flow = buildFlow {
    id = "simple-flow"
    name = "Simple Flow"
    description = "Process data through multiple agents"

    step("step1", "agent-1")
    step("step2", "agent-2")
    step("step3", "agent-3")
}
```

## Conditional Flow

```kotlin
val flow = buildFlow {
    id = "conditional-flow"
    name = "Conditional Flow"

    step("analyze", "analyzer") { comm ->
        comm.content.isNotEmpty()
    }

    step("process", "processor") { comm ->
        comm.data["analyzed"] == "true"
    }

    step("respond", "responder")
}
```

## Flow Execution

```kotlin
// Register agents
AgentRegistry.register(agent1)
AgentRegistry.register(agent2)

// Register flow
FlowRegistry.register(flow)

// Execute flow
val result = flow.execute(
    Comm(content = "Process this", from = "user")
)
```
