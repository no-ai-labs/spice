# Building Flows

Create multi-agent workflows with the buildFlow DSL. The `buildFlow` DSL returns a `MultiAgentFlow` instance that can be executed with various strategies.

## Basic Flow

```kotlin
val flow = buildFlow {
    id = "simple-flow"
    name = "Simple Flow"
    description = "Process data through multiple agents"
    strategy = FlowStrategy.SEQUENTIAL  // Optional, defaults to SEQUENTIAL

    step("step1", "agent-1")
    step("step2", "agent-2")
    step("step3", "agent-3")
}

// Execute the flow
runBlocking {
    val result = flow.process(
        Comm(content = "Process this", from = "user")
    )
    println(result.content)
}
```

## Flow Strategies

The `strategy` property determines how agents execute:

```kotlin
val flow = buildFlow {
    id = "parallel-flow"
    name = "Parallel Processing"
    strategy = FlowStrategy.PARALLEL  // All agents run simultaneously

    step("step1", "agent-1")
    step("step2", "agent-2")
    step("step3", "agent-3")
}
```

Available strategies:
- `FlowStrategy.SEQUENTIAL` - Agents execute one by one: A → B → C
- `FlowStrategy.PARALLEL` - All agents execute simultaneously
- `FlowStrategy.COMPETITION` - First successful response wins
- `FlowStrategy.PIPELINE` - Output of A flows to B, then to C

## Conditional Flow

Add conditions to control which agents execute:

```kotlin
val flow = buildFlow {
    id = "conditional-flow"
    name = "Conditional Flow"
    strategy = FlowStrategy.SEQUENTIAL

    step("analyze", "analyzer") { comm ->
        comm.content.isNotEmpty()  // Only execute if content is not empty
    }

    step("process", "processor") { comm ->
        comm.data["analyzed"] == "true"  // Only if previous step marked as analyzed
    }

    step("respond", "responder")  // Always executes (no condition)
}

// Conditions are evaluated at runtime
runBlocking {
    val result = flow.process(
        Comm(content = "Analyze this data", from = "user")
    )
}
```

## Registry-Based vs Direct Reference

### Using Agent IDs (Registry lookup)

```kotlin
// Register agents first
AgentRegistry.register(analyzerAgent)
AgentRegistry.register(processorAgent)

val flow = buildFlow {
    id = "registry-flow"
    name = "Registry-Based Flow"

    // Reference agents by ID
    step("step1", "analyzer")
    step("step2", "processor")
}
```

### Using Agent Instances (Direct reference)

```kotlin
val analyzer = buildAgent { /* ... */ }
val processor = buildAgent { /* ... */ }

val flow = buildFlow {
    id = "direct-flow"
    name = "Direct Reference Flow"

    // Pass agent instances directly
    step("step1", analyzer)
    step("step2", processor)
}
```

## Flow Execution

```kotlin
// Register flow (optional, for reuse)
FlowRegistry.register(flow)

// Execute immediately
runBlocking {
    val result = flow.process(
        Comm(content = "Process this", from = "user")
    )

    // Check flow metadata
    println("Strategy: ${result.data["flow_strategy"]}")
    println("Execution time: ${result.data["execution_time_ms"]}ms")
    println("Agent count: ${result.data["agent_count"]}")
}
```

## Advanced: Dynamic Strategy Selection

```kotlin
val flow = buildFlow {
    id = "adaptive-flow"
    name = "Adaptive Strategy Flow"

    step("step1", "agent-1")
    step("step2", "agent-2")
    step("step3", "agent-3")
}

// Set dynamic strategy resolver
flow.setStrategyResolver { comm, agents ->
    when {
        comm.data["priority"] == "high" -> FlowStrategy.PARALLEL
        agents.size > 5 -> FlowStrategy.COMPETITION
        else -> FlowStrategy.SEQUENTIAL
    }
}

// Strategy is selected at runtime based on the comm
runBlocking {
    val result = flow.process(
        Comm(
            content = "Urgent request",
            from = "user",
            data = mapOf("priority" to "high")
        )
    )
}
```

## Complete Example

```kotlin
import io.github.noailabs.spice.*
import io.github.noailabs.spice.dsl.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Create agents
    val analyzer = buildAgent {
        id = "analyzer"
        name = "Data Analyzer"
        handle { comm ->
            SpiceResult.success(
                comm.reply(
                    content = "Analysis: ${comm.content}",
                    from = id,
                    data = mapOf("analyzed" to "true")
                )
            )
        }
    }

    val processor = buildAgent {
        id = "processor"
        name = "Data Processor"
        handle { comm ->
            SpiceResult.success(
                comm.reply(
                    content = "Processed: ${comm.content}",
                    from = id,
                    data = mapOf("processed" to "true")
                )
            )
        }
    }

    // Register agents
    AgentRegistry.register(analyzer)
    AgentRegistry.register(processor)

    // Create flow
    val flow = buildFlow {
        id = "data-pipeline"
        name = "Data Processing Pipeline"
        strategy = FlowStrategy.SEQUENTIAL

        step("analyze", "analyzer") { comm ->
            comm.content.isNotEmpty()
        }

        step("process", "processor") { comm ->
            comm.data["analyzed"] == "true"
        }
    }

    // Execute flow
    val result = flow.process(
        Comm(content = "Raw data", from = "user")
    )

    println(result.content)
    println("Completed in ${result.data["execution_time_ms"]}ms")
}
```
