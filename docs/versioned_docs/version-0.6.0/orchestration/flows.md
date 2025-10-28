# Flow Orchestration

Coordinate multiple agents with flows. Spice provides `MultiAgentFlow` for orchestrating agent execution with various strategies and conditions.

## Overview

`MultiAgentFlow` supports:
- **4 execution strategies**: Sequential, Parallel, Competition, Pipeline
- **Conditional execution**: Execute agents based on runtime conditions
- **Registry-based or direct**: Reference agents by ID or pass instances
- **Dynamic strategy selection**: Choose strategy at runtime based on message content

## Creating Flows

### Using buildFlow DSL

```kotlin
val flow = buildFlow {
    id = "my-flow"
    name = "My Flow"
    strategy = FlowStrategy.SEQUENTIAL

    step("step1", "agent1")
    step("step2", "agent2")
    step("step3", "agent3")
}

// Execute
runBlocking {
    val result = flow.process(comm)
}
```

### Using MultiAgentFlow Directly

```kotlin
val flow = MultiAgentFlow(
    flowId = "direct-flow",
    defaultStrategy = FlowStrategy.PARALLEL
)
    .addAgent(agent1)
    .addAgent(agent2)
    .addStep(agent3) { comm -> comm.data["priority"] == "high" }

runBlocking {
    val result = flow.process(comm)
}
```

## Execution Strategies

### Sequential Flow

Agents execute one by one: A → B → C

```kotlin
val flow = buildFlow {
    id = "sequential"
    name = "Sequential Processing"
    strategy = FlowStrategy.SEQUENTIAL

    step("step1", "agent1")
    step("step2", "agent2")
    step("step3", "agent3")
}
```

Output of each agent becomes input to the next.

### Parallel Flow

All agents execute simultaneously:

```kotlin
val flow = buildFlow {
    id = "parallel"
    name = "Parallel Processing"
    strategy = FlowStrategy.PARALLEL

    step("task1", "agent1")
    step("task2", "agent2")
    step("task3", "agent3")
}
```

All agents process the same input concurrently. Results are aggregated.

### Competition Flow

Agents compete for fastest response:

```kotlin
val flow = buildFlow {
    id = "competition"
    name = "Fastest Response"
    strategy = FlowStrategy.COMPETITION

    step("model1", "gpt-4")
    step("model2", "claude")
    step("model3", "gemini")
}
```

First successful response wins. Other tasks are cancelled.

### Pipeline Flow

Output flows through agents like a pipeline:

```kotlin
val flow = buildFlow {
    id = "pipeline"
    name = "Data Pipeline"
    strategy = FlowStrategy.PIPELINE

    step("extract", "extractor")
    step("transform", "transformer")
    step("load", "loader")
}
```

Output of A → Input of B → Output of B → Input of C

## Conditional Execution

Add conditions to control which agents execute:

```kotlin
val flow = buildFlow {
    id = "conditional"
    name = "Conditional Flow"
    strategy = FlowStrategy.SEQUENTIAL

    step("validate", "validator") { comm ->
        comm.content.isNotEmpty()  // Only if content exists
    }

    step("process", "processor") { comm ->
        comm.data["valid"] == "true"  // Only if validated
    }

    step("respond", "responder")  // Always executes
}
```

Conditions are evaluated at runtime before each agent executes.

## Dynamic Strategy Selection

Choose strategy based on runtime conditions:

```kotlin
val flow = buildFlow {
    id = "adaptive"
    name = "Adaptive Flow"

    step("agent1", "agent1")
    step("agent2", "agent2")
    step("agent3", "agent3")
}

flow.setStrategyResolver { comm, agents ->
    when {
        comm.data["urgent"] == "true" -> FlowStrategy.COMPETITION
        comm.data["mode"] == "parallel" -> FlowStrategy.PARALLEL
        agents.size > 5 -> FlowStrategy.PIPELINE
        else -> FlowStrategy.SEQUENTIAL
    }
}

// Strategy is selected at runtime
runBlocking {
    val result = flow.process(
        Comm(
            content = "Process this",
            from = "user",
            data = mapOf("urgent" to "true")  // Will use COMPETITION
        )
    )
}
```

## Flow Metadata

Flows include execution metadata in the result:

```kotlin
runBlocking {
    val result = flow.process(comm)

    println("Strategy used: ${result.data["flow_strategy"]}")
    println("Execution time: ${result.data["execution_time_ms"]}ms")
    println("Agent count: ${result.data["agent_count"]}")
    println("Completed steps: ${result.data["completed_steps"]}")
}
```

## Convenience Functions

Create flows using convenience functions:

```kotlin
import io.github.noailabs.spice.sequentialFlow
import io.github.noailabs.spice.parallelFlow
import io.github.noailabs.spice.competitionFlow
import io.github.noailabs.spice.pipelineFlow

// Sequential
val seq = sequentialFlow(agent1, agent2, agent3)

// Parallel
val par = parallelFlow(agent1, agent2, agent3)

// Competition
val comp = competitionFlow(agent1, agent2, agent3)

// Pipeline
val pipe = pipelineFlow(agent1, agent2, agent3)
```

## Flow Operators

Use operators for fluent composition:

```kotlin
// Sequential chaining: A then B
val flow1 = agent1 + agent2

// Parallel execution: A and B
val flow2 = agent1 parallelWith agent2

// Competition: A vs B
val flow3 = agent1 competesWith agent2
```

## Complete Example

```kotlin
import io.github.noailabs.spice.*
import io.github.noailabs.spice.dsl.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Create agents
    val validator = buildAgent {
        id = "validator"
        name = "Input Validator"
        handle { comm ->
            val isValid = comm.content.length >= 10
            SpiceResult.success(
                comm.reply(
                    content = if (isValid) "Valid input" else "Invalid input",
                    from = id,
                    data = mapOf("valid" to isValid.toString())
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
                    content = "Processed: ${comm.content.uppercase()}",
                    from = id,
                    data = mapOf("processed" to "true")
                )
            )
        }
    }

    val responder = buildAgent {
        id = "responder"
        name = "Response Formatter"
        handle { comm ->
            SpiceResult.success(
                comm.reply(
                    content = "✓ ${comm.content}",
                    from = id
                )
            )
        }
    }

    // Register agents
    AgentRegistry.register(validator)
    AgentRegistry.register(processor)
    AgentRegistry.register(responder)

    // Create conditional flow
    val flow = buildFlow {
        id = "validation-pipeline"
        name = "Validation Pipeline"
        strategy = FlowStrategy.SEQUENTIAL

        step("validate", "validator")

        step("process", "processor") { comm ->
            comm.data["valid"] == "true"
        }

        step("respond", "responder") { comm ->
            comm.data["processed"] == "true"
        }
    }

    // Test with valid input
    println("=== Valid Input ===")
    val result1 = flow.process(
        Comm(content = "This is a long enough message", from = "user")
    )
    println(result1.content)

    // Test with invalid input
    println("\n=== Invalid Input ===")
    val result2 = flow.process(
        Comm(content = "Short", from = "user")
    )
    println(result2.content)
}
```
