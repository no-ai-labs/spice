---
sidebar_position: 1
---

# Migration Guide: 0.4.4 → 0.5.0

Complete guide for migrating from Spice 0.4.4 (Swarm-based) to 0.5.0 (Graph-based).

## Overview

Spice 0.5.0 introduces the **Graph System**, a complete replacement for the 0.4.4 Swarm orchestration. The Graph System is:

- More flexible and composable
- Easier to reason about
- Better error handling
- Built-in checkpoint/resume support
- Inspired by Microsoft Agent Framework

## Breaking Changes Summary

| 0.4.4 Concept | 0.5.0 Replacement | Status |
|--------------|-------------------|---------|
| `Swarm` | `Graph` | ✅ Direct replacement |
| `SwarmOrchestrator` | `GraphRunner` | ✅ Direct replacement |
| `SwarmStrategy` | Conditional `Edge` | ✅ Pattern change |
| `SpiceFlowConfig` | `GraphConfig` | ✅ Direct replacement |
| `decision()` DSL | `edge()` with condition | ⚠️ Pattern change |
| `human()` DSL | `humanNode()` | ⚠️ Name change |

## Migration Checklist

- [ ] Update dependency to 0.5.0
- [ ] Replace Swarm imports with Graph imports
- [ ] Convert SpiceFlowConfig to GraphConfig
- [ ] Convert SwarmOrchestrator to GraphOrchestrator
- [ ] Update DSL: `human()` → `humanNode()`
- [ ] Convert decision nodes to conditional edges
- [ ] Update tests
- [ ] Test in staging environment

## Step-by-Step Migration

### Step 1: Update Dependencies

**build.gradle.kts:**

```kotlin
dependencies {
    // ❌ Old
    implementation("io.github.noailabs:spice-core:0.4.4")

    // ✅ New
    implementation("io.github.noailabs:spice-core:0.5.0")
}
```

### Step 2: Update Imports

```kotlin
// ❌ Old imports (0.4.4)
import io.github.noailabs.spice.swarm.*
import io.github.noailabs.spice.orchestration.SwarmOrchestrator

// ✅ New imports (0.5.0)
import io.github.noailabs.spice.graph.*
import io.github.noailabs.spice.graph.dsl.*
import io.github.noailabs.spice.graph.runner.*
import io.github.noailabs.spice.graph.middleware.*
```

### Step 3: Replace Configuration

#### Before (0.4.4):

```kotlin
@Configuration
class SpiceFlowConfig {

    @Bean
    fun kaiSwarm(
        intentFillingAgent: Agent,
        handoffCoordinator: Agent
    ): Swarm {
        return swarm("kai-workflow") {
            agent("intent-filling", intentFillingAgent)

            decision("confidence-check") { result ->
                if (needsHandoff(result)) "handoff" else "complete"
            }

            human("human-handoff") {
                prompt = "Please handle this manually"
                options = listOf("approve", "reject")
            }
        }
    }

    @Bean
    fun swarmOrchestrator(
        swarm: Swarm
    ): SwarmOrchestrator {
        return SwarmOrchestrator(swarm)
    }
}
```

#### After (0.5.0):

```kotlin
import io.github.noailabs.spice.graph.dsl.graph
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import io.github.noailabs.spice.graph.nodes.HumanOption

@Configuration
class GraphConfig {

    @Bean
    fun kaiWorkflow(
        intentFillingAgent: Agent,
        handoffCoordinator: Agent
    ): Graph {
        return graph("kai-workflow") {
            // Agents
            agent("intent-filling", intentFillingAgent)
            agent("handoff-coordinator", handoffCoordinator)

            // Human node (not decision!)
            humanNode(
                id = "human-handoff",
                prompt = "Please handle this manually",
                options = listOf(
                    HumanOption("approve", "Approve"),
                    HumanOption("reject", "Reject")
                ),
                timeout = Duration.ofHours(24)
            )

            // Conditional routing via edges
            edge("intent-filling", "handoff-coordinator") { result ->
                needsHandoff(result.data)
            }

            edge("intent-filling", "human-handoff") { result ->
                !needsHandoff(result.data)
            }

            output("final-result") { ctx ->
                ctx.state["_previous"]
            }

            // Add middleware
            middleware(LoggingMiddleware())
        }
    }

    @Bean
    fun graphRunner(): DefaultGraphRunner {
        return DefaultGraphRunner()
    }

    @Bean
    fun checkpointStore(): CheckpointStore {
        return InMemoryCheckpointStore()
    }

    private fun needsHandoff(data: Any?): Boolean {
        // Your business logic
        return false
    }
}
```

**Key Changes:**
- `swarm()` → `graph()`
- `SwarmOrchestrator` → `DefaultGraphRunner`
- `decision()` → removed, use `edge()` with conditions
- `human()` → `humanNode()`
- Add explicit `output()` node
- Add middleware as needed

### Step 4: Replace Orchestrator

#### Before (0.4.4):

```kotlin
@Component
class KAISwarmOrchestrator(
    private val swarm: Swarm,
    private val sessionManager: SessionStateManager
) : Agent {

    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        val result = swarm.execute(comm).getOrThrow()
        return SpiceResult.success(result)
    }
}
```

#### After (0.5.0):

```kotlin
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import io.github.noailabs.spice.graph.runner.RunStatus

@Component
class KAIGraphOrchestrator(
    @Qualifier("kaiWorkflow") private val graph: Graph,
    private val graphRunner: DefaultGraphRunner,
    private val checkpointStore: CheckpointStore,
    private val sessionManager: SessionStateManager
) : Agent {

    override val id = "kai-orchestrator"
    override val name = "KAI Orchestrator"
    override val description = "Orchestrates KAI workflow using Graph API"
    override val capabilities = listOf("orchestration")

    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        val input = mapOf(
            "input" to comm,
            "sessionId" to (comm.context?.sessionId ?: "unknown")
        )

        val result = graphRunner.runWithCheckpoint(
            graph = graph,
            input = input,
            store = checkpointStore,
            config = CheckpointConfig(
                saveEveryNNodes = 3,
                saveOnError = true
            )
        ).getOrThrow()

        return when (result.status) {
            RunStatus.SUCCESS -> {
                SpiceResult.success(result.result as Comm)
            }
            RunStatus.PAUSED -> {
                // Human interaction needed
                val interaction = result.result as HumanInteraction
                sessionManager.saveCheckpointId(
                    comm.context?.sessionId ?: "",
                    result.checkpointId!!
                )
                SpiceResult.success(
                    comm.reply("Awaiting human input: ${interaction.prompt}", id)
                )
            }
            RunStatus.FAILED -> {
                SpiceResult.failure(result.error ?: Exception("Unknown error"))
            }
            else -> {
                SpiceResult.failure(Exception("Unexpected status: ${result.status}"))
            }
        }
    }

    // Resume from human input
    suspend fun resumeWithHumanResponse(
        checkpointId: String,
        response: HumanResponse
    ): SpiceResult<Comm> {
        val result = graphRunner.resumeWithHumanResponse(
            graph = graph,
            checkpointId = checkpointId,
            response = response,
            store = checkpointStore
        ).getOrThrow()

        return SpiceResult.success(result.result as Comm)
    }

    override fun canHandle(comm: Comm) = true
    override fun getTools() = emptyList<Tool>()
    override fun isReady() = true
}
```

**Key Changes:**
- Inject `Graph` and `DefaultGraphRunner` instead of `Swarm`
- Use `runWithCheckpoint()` for resumable workflows
- Handle `RunStatus.PAUSED` for HITL
- Add `resumeWithHumanResponse()` method
- Add checkpoint management

### Step 5: Convert Decision Nodes

#### Before (0.4.4):

```kotlin
swarm("workflow") {
    agent("classifier", classifierAgent)

    decision("route-decision") { result ->
        when (result.classification) {
            "type-a" -> "handle-a"
            "type-b" -> "handle-b"
            else -> "handle-default"
        }
    }

    agent("handle-a", handlerA)
    agent("handle-b", handlerB)
    agent("handle-default", defaultHandler)
}
```

#### After (0.5.0):

```kotlin
graph("workflow") {
    agent("classifier", classifierAgent)

    agent("handle-a", handlerA)
    agent("handle-b", handlerB)
    agent("handle-default", defaultHandler)

    output("result") { it.state["_previous"] }

    // Conditional edges replace decision node
    edge("classifier", "handle-a") { result ->
        (result.data as? Classification)?.type == "type-a"
    }

    edge("classifier", "handle-b") { result ->
        (result.data as? Classification)?.type == "type-b"
    }

    edge("classifier", "handle-default") { result ->
        val classification = result.data as? Classification
        classification?.type != "type-a" && classification?.type != "type-b"
    }

    // All handlers converge to output
    edge("handle-a", "result")
    edge("handle-b", "result")
    edge("handle-default", "result")
}
```

**Key Pattern:**
- Remove `decision()` node
- Add explicit agent nodes for each branch
- Use `edge()` with conditions to route
- Add convergence edges to output

### Step 6: Update Human-in-the-Loop

#### Before (0.4.4):

```kotlin
swarm("approval") {
    agent("draft", draftAgent)

    human("review") {
        prompt = "Approve draft?"
        options = listOf("approve", "reject")
    }

    agent("publish", publishAgent)
}
```

#### After (0.5.0):

```kotlin
import io.github.noailabs.spice.graph.nodes.HumanOption
import java.time.Duration

graph("approval") {
    agent("draft", draftAgent)

    humanNode(
        id = "review",
        prompt = "Approve draft?",
        options = listOf(
            HumanOption("approve", "Approve", "Approve and publish"),
            HumanOption("reject", "Reject", "Reject and discard")
        ),
        timeout = Duration.ofHours(24),
        validator = { response ->
            response.selectedOption in listOf("approve", "reject")
        }
    )

    agent("publish", publishAgent)
    agent("discard", discardAgent)

    // Conditional routing based on human response
    edge("review", "publish") { result ->
        (result.data as? HumanResponse)?.selectedOption == "approve"
    }

    edge("review", "discard") { result ->
        (result.data as? HumanResponse)?.selectedOption == "reject"
    }

    output("result") { it.state["_previous"] }
}
```

**Key Changes:**
- `human()` → `humanNode()`
- Use `HumanOption` data class
- Add optional `timeout` and `validator`
- Use conditional edges for routing after human input
- Handle response with `HumanResponse` type

### Step 7: Update Tests

#### Before (0.4.4):

```kotlin
@Test
fun `test swarm execution`() = runTest {
    val swarm = swarm("test") {
        agent("step1", testAgent)
    }

    val result = swarm.execute(testComm).getOrThrow()
    assertEquals("expected", result.content)
}
```

#### After (0.5.0):

```kotlin
import io.github.noailabs.spice.graph.runner.RunStatus

@Test
fun `test graph execution`() = runTest {
    val graph = graph("test") {
        agent("step1", testAgent)
        output("result") { it.state["step1"] }
    }

    val runner = DefaultGraphRunner()
    val report = runner.run(
        graph = graph,
        input = mapOf("input" to testData)
    ).getOrThrow()

    assertEquals(RunStatus.SUCCESS, report.status)
    assertEquals("expected", (report.result as Comm).content)

    // Verify node execution
    assertEquals(2, report.nodeReports.size)
    val step1Report = report.nodeReports.find { it.nodeId == "step1" }
    assertNotNull(step1Report)
    assertEquals(NodeStatus.SUCCESS, step1Report.status)
}
```

**Key Changes:**
- Test against `RunReport` instead of direct result
- Verify `RunStatus`
- Check individual node reports
- Validate execution flow

## Migration Examples

### Example 1: Simple Pipeline

#### Before (0.4.4):

```kotlin
val pipeline = swarm("data-pipeline") {
    agent("validate", validator)
    agent("process", processor)
    agent("store", storage)
}
```

#### After (0.5.0):

```kotlin
val pipeline = graph("data-pipeline") {
    agent("validate", validator)
    agent("process", processor)
    agent("store", storage)
    output("result") { it.state["store"] }
}
```

### Example 2: Conditional Workflow

#### Before (0.4.4):

```kotlin
val workflow = swarm("conditional") {
    agent("analyzer", analyzer)

    decision("routing") { result ->
        if (result.confidence > 0.8) "auto" else "manual"
    }

    agent("auto-handler", autoHandler)
    agent("manual-handler", manualHandler)
}
```

#### After (0.5.0):

```kotlin
val workflow = graph("conditional") {
    agent("analyzer", analyzer)
    agent("auto-handler", autoHandler)
    agent("manual-handler", manualHandler)
    output("result") { it.state["_previous"] }

    edge("analyzer", "auto-handler") { result ->
        (result.data as? AnalysisResult)?.confidence ?: 0.0 > 0.8
    }

    edge("analyzer", "manual-handler") { result ->
        (result.data as? AnalysisResult)?.confidence ?: 0.0 <= 0.8
    }

    edge("auto-handler", "result")
    edge("manual-handler", "result")
}
```

### Example 3: Error Handling

#### Before (0.4.4):

```kotlin
val workflow = swarm("resilient") {
    agent("api-caller", apiAgent)

    // Limited error handling
}
```

#### After (0.5.0):

```kotlin
val workflow = graph("resilient") {
    agent("api-caller", apiAgent)
    output("result") { it.state["api-caller"] }

    // Robust error handling with middleware
    middleware(object : Middleware {
        override suspend fun onError(
            err: Throwable,
            ctx: RunContext
        ): ErrorAction {
            return when (err) {
                is SocketTimeoutException -> {
                    println("Network timeout, retrying...")
                    ErrorAction.RETRY
                }
                is HttpException -> {
                    if (err.code in 500..599) {
                        ErrorAction.RETRY
                    } else {
                        ErrorAction.PROPAGATE
                    }
                }
                else -> ErrorAction.PROPAGATE
            }
        }
    })
}
```

## Common Pitfalls

### Pitfall 1: Forgetting Output Node

```kotlin
// ❌ Wrong - No output node
graph("incomplete") {
    agent("step1", agent1)
}

// ✅ Correct - Add output node
graph("complete") {
    agent("step1", agent1)
    output("result") { it.state["step1"] }
}
```

### Pitfall 2: Missing Edge Convergence

```kotlin
// ❌ Wrong - Branches don't converge
graph("divergent") {
    agent("classifier", classifier)
    agent("handler-a", handlerA)
    agent("handler-b", handlerB)
    output("result") { it.state["???"] }  // Which handler's result?

    edge("classifier", "handler-a") { /* condition */ }
    edge("classifier", "handler-b") { /* condition */ }
    // Missing: edges to output!
}

// ✅ Correct - Explicit convergence
graph("convergent") {
    agent("classifier", classifier)
    agent("handler-a", handlerA)
    agent("handler-b", handlerB)
    output("result") { it.state["_previous"] }

    edge("classifier", "handler-a") { /* condition */ }
    edge("classifier", "handler-b") { /* condition */ }
    edge("handler-a", "result")  // ✅ Converge
    edge("handler-b", "result")  // ✅ Converge
}
```

### Pitfall 3: Using human() Instead of humanNode()

```kotlin
// ❌ Wrong - human() doesn't exist in 0.5.0
graph("old-style") {
    human("review", "Approve?")
}

// ✅ Correct - Use humanNode()
graph("new-style") {
    humanNode(
        id = "review",
        prompt = "Approve?",
        options = listOf(HumanOption("yes", "Yes"))
    )
}
```

### Pitfall 4: Not Handling PAUSED Status

```kotlin
// ❌ Wrong - Only handle SUCCESS
val report = runner.run(graph, input).getOrThrow()
return report.result as Comm  // Crash if PAUSED!

// ✅ Correct - Handle all statuses
val report = runner.run(graph, input).getOrThrow()
when (report.status) {
    RunStatus.SUCCESS -> report.result as Comm
    RunStatus.PAUSED -> {
        // Save checkpoint, notify user
        saveCheckpoint(report.checkpointId!!)
        createPendingResponse()
    }
    RunStatus.FAILED -> throw report.error!!
    else -> throw IllegalStateException("Unexpected status")
}
```

## Benefits of Migration

After migration to 0.5.0, you'll gain:

### 1. Better Error Handling

```kotlin
middleware(object : Middleware {
    override suspend fun onError(err: Throwable, ctx: RunContext): ErrorAction {
        return when (err) {
            is RetryableException -> ErrorAction.RETRY
            is SkippableException -> ErrorAction.SKIP
            else -> ErrorAction.PROPAGATE
        }
    }
})
```

### 2. Checkpoint & Resume

```kotlin
// Save state automatically
val report = runner.runWithCheckpoint(
    graph = graph,
    input = input,
    store = checkpointStore,
    config = CheckpointConfig(saveEveryNNodes = 5)
)

// Resume from any point
if (report.status == RunStatus.FAILED) {
    runner.resume(graph, lastCheckpointId, checkpointStore)
}
```

### 3. Better Observability

```kotlin
report.nodeReports.forEach { node ->
    println("${node.nodeId}: ${node.duration} (${node.status})")
}

// Detailed metrics
middleware(MetricsMiddleware())
```

### 4. Flexible Routing

```kotlin
// Multiple conditions, easy to reason about
edge("classifier", "priority-queue") { result ->
    result.data.isPriority && result.data.confidence > 0.9
}

edge("classifier", "standard-queue") { result ->
    !result.data.isPriority || result.data.confidence <= 0.9
}
```

## Need Help?

- **[Graph Quick Start](../getting-started/graph-quick-start.md)** - Learn Graph basics
- **[Spring Boot Integration](./graph-spring-boot.md)** - Production setup
- **[Design Patterns](./graph-patterns.md)** - Best practices
- **[API Reference](../api/graph.md)** - Complete API documentation
- **[Troubleshooting](./troubleshooting.md)** - Common issues

## Feedback

Found an issue with this migration guide? Please open an issue on [GitHub](https://github.com/no-ai-labs/spice/issues).
