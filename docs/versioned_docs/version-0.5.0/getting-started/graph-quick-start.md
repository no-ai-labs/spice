---
sidebar_position: 4
---

# Graph Quick Start

Build your first Graph workflow in 5 minutes!

## What You'll Build

A simple customer support workflow that:
1. Analyzes customer intent
2. Routes to appropriate handler
3. Returns a response

## Prerequisites

```gradle
dependencies {
    implementation("io.github.noailabs:spice-core:0.5.0")
}
```

## Step 1: Create Your Agents

```kotlin
// Intent analysis agent
val intentAgent = object : Agent {
    override val id = "intent-analyzer"
    override val name = "Intent Analyzer"
    override val description = "Analyzes customer intent"
    override val capabilities = listOf("intent-analysis")

    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        val intent = when {
            comm.content.contains("refund", ignoreCase = true) -> "refund"
            comm.content.contains("technical", ignoreCase = true) -> "technical"
            else -> "general"
        }

        return SpiceResult.success(
            comm.reply(intent, id)
        )
    }

    override fun canHandle(comm: Comm) = true
    override fun getTools() = emptyList<Tool>()
    override fun isReady() = true
}

// Response agent
val responseAgent = object : Agent {
    override val id = "responder"
    override val name = "Response Agent"
    override val description = "Generates customer response"
    override val capabilities = listOf("response-generation")

    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        val intent = comm.content
        val response = when (intent) {
            "refund" -> "We'll process your refund within 3-5 business days."
            "technical" -> "Our technical team will contact you within 24 hours."
            else -> "Thank you for contacting us. How can we help?"
        }

        return SpiceResult.success(
            comm.reply(response, id)
        )
    }

    override fun canHandle(comm: Comm) = true
    override fun getTools() = emptyList<Tool>()
    override fun isReady() = true
}
```

## Step 2: Build the Graph

```kotlin
import io.github.noailabs.spice.graph.dsl.graph
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner

val supportWorkflow = graph("customer-support") {
    // Step 1: Analyze intent
    agent("analyzer", intentAgent)

    // Step 2: Generate response
    agent("responder", responseAgent)

    // Step 3: Output result (OPTIONAL - see note below)
    output("final-response") { ctx ->
        ctx.state["responder"]
    }
}
```

:::tip Understanding output()

The `output()` node is **OPTIONAL**. Here's how it works:

**Without output():**
```kotlin
val graph = graph("simple") {
    agent("step1", agent1)
    agent("step2", agent2)
    // No output() - returns step2's result automatically
}
```

**With output():**
```kotlin
val graph = graph("custom") {
    agent("step1", agent1)
    agent("step2", agent2)

    // Use output() to:
    // 1. Select specific node results
    output("result") { ctx -> ctx.state["step1"] }  // Return step1, not step2

    // 2. Transform results
    output("result") { ctx ->
        mapOf(
            "step1" to ctx.state["step1"],
            "step2" to ctx.state["step2"]
        )
    }

    // 3. Use previous node
    output("result") { ctx -> ctx.state["_previous"] }  // Last executed node
}
```

**Key points:**
- `report.result` = last executed node's `NodeResult.data` (without output)
- `report.result` = output selector's return value (with output)
- Use `ctx.state["_previous"]` to access the most recent node's result

:::

## Step 3: Execute the Graph

```kotlin
suspend fun main() {
    val runner = DefaultGraphRunner()

    // Run the workflow
    val result = runner.run(
        graph = supportWorkflow,
        input = mapOf(
            "input" to "I need a refund for my order"
        )
    )

    // Handle the result
    when (result) {
        is SpiceResult.Success -> {
            val report = result.value
            println("Status: ${report.status}")
            println("Result: ${report.result}")
            println("Duration: ${report.duration}")

            // Print node execution details
            report.nodeReports.forEach { nodeReport ->
                println("  - ${nodeReport.nodeId}: ${nodeReport.duration}")
            }
        }
        is SpiceResult.Failure -> {
            println("Error: ${result.error.message}")
        }
    }
}
```

## Step 4: See the Output

```
Status: SUCCESS
Result: We'll process your refund within 3-5 business days.
Duration: PT0.125S

  - analyzer: PT0.045S
  - responder: PT0.062S
  - final-response: PT0.018S
```

## Next: Add Conditional Routing

```kotlin
val advancedWorkflow = graph("advanced-support") {
    agent("analyzer", intentAgent)

    // Route A: Technical support
    agent("tech-support", technicalAgent)

    // Route B: Refund processing
    agent("refund-processor", refundAgent)

    // Route C: General inquiries
    agent("general-support", generalAgent)

    output("result") { ctx -> ctx.state["_previous"] }

    // Conditional edges
    edge("analyzer", "tech-support") { result ->
        result.data == "technical"
    }

    edge("analyzer", "refund-processor") { result ->
        result.data == "refund"
    }

    edge("analyzer", "general-support") { result ->
        result.data == "general"
    }

    // All routes converge to output
    edge("tech-support", "result")
    edge("refund-processor", "result")
    edge("general-support", "result")
}
```

## Add Logging Middleware

```kotlin
import io.github.noailabs.spice.graph.middleware.LoggingMiddleware

val workflowWithLogging = graph("logged-workflow") {
    // Add middleware
    middleware(LoggingMiddleware())

    agent("analyzer", intentAgent)
    agent("responder", responseAgent)
    output("result") { ctx -> ctx.state["responder"] }
}
```

## Complete Spring Boot Example

```kotlin
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GraphConfig {

    @Bean
    fun intentAgent(): Agent = object : Agent {
        override val id = "intent-analyzer"
        // ... implementation
    }

    @Bean
    fun responseAgent(): Agent = object : Agent {
        override val id = "responder"
        // ... implementation
    }

    @Bean
    fun supportWorkflow(
        intentAgent: Agent,
        responseAgent: Agent
    ): Graph {
        return graph("customer-support") {
            agent("analyzer", intentAgent)
            agent("responder", responseAgent)
            output("result") { it.state["responder"] }

            middleware(LoggingMiddleware())
        }
    }

    @Bean
    fun graphRunner(): DefaultGraphRunner {
        return DefaultGraphRunner()
    }
}

@RestController
@RequestMapping("/api/support")
class SupportController(
    private val supportWorkflow: Graph,
    private val graphRunner: DefaultGraphRunner
) {

    @PostMapping("/ticket")
    suspend fun handleTicket(@RequestBody request: SupportRequest): SupportResponse {
        val result = graphRunner.run(
            graph = supportWorkflow,
            input = mapOf("input" to request.message)
        ).getOrThrow()

        return SupportResponse(
            message = result.result.toString(),
            processingTime = result.duration.toMillis()
        )
    }
}

data class SupportRequest(val message: String)
data class SupportResponse(val message: String, val processingTime: Long)
```

## What's Next?

Now that you've built your first graph, explore:

- **[Add Checkpointing](../orchestration/graph-checkpoint.md)** - Save and resume long-running workflows
- **[Human-in-the-Loop](../orchestration/graph-hitl.md)** - Pause for human input
- **[Custom Middleware](../orchestration/graph-middleware.md)** - Add metrics, tracing, and error handling
- **[Spring Boot Integration](../guides/graph-spring-boot.md)** - Production-ready setup
- **[Design Patterns](../guides/graph-patterns.md)** - Best practices and common patterns

## Common Use Cases

### Use Case 1: Multi-Step Processing Pipeline

```kotlin
val pipeline = graph("data-pipeline") {
    tool("validate", validationTool) { ctx ->
        mapOf("data" to ctx.state["input"])
    }

    agent("process", processingAgent)

    tool("store", storageTool) { ctx ->
        mapOf("result" to ctx.state["process"])
    }

    output("summary") { ctx ->
        mapOf(
            "validated" to ctx.state["validate"],
            "processed" to ctx.state["process"],
            "stored" to ctx.state["store"]
        )
    }
}
```

### Use Case 2: Approval Workflow

```kotlin
val approvalFlow = graph("approval") {
    agent("draft", draftAgent)

    humanNode(
        id = "review",
        prompt = "Please review and approve",
        options = listOf(
            HumanOption("approve", "Approve"),
            HumanOption("reject", "Reject"),
            HumanOption("revise", "Request Revision")
        ),
        timeout = Duration.ofHours(24)
    )

    edge("review", "publish") { result ->
        (result.data as? HumanResponse)?.selectedOption == "approve"
    }

    edge("review", "revise") { result ->
        (result.data as? HumanResponse)?.selectedOption == "revise"
    }

    agent("publish", publishAgent)
    agent("revise", revisionAgent)

    edge("revise", "draft")  // Loop back for revision
}
```

### Use Case 3: Error Handling with Retry

```kotlin
val resilientWorkflow = graph("resilient") {
    agent("api-caller", apiAgent)

    middleware(object : Middleware {
        override suspend fun onError(
            err: Throwable,
            ctx: RunContext
        ): ErrorAction {
            return when (err) {
                is SocketTimeoutException -> ErrorAction.RETRY
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

    output("result") { it.state["api-caller"] }
}
```

## Tips

### Tip 1: Debug with State Inspection

```kotlin
val report = runner.run(graph, input).getOrThrow()

// Inspect what each node produced
report.nodeReports.forEach { nodeReport ->
    println("${nodeReport.nodeId} output: ${nodeReport.output}")
}
```

### Tip 2: Use Context Propagation

```kotlin
val agentContext = AgentContext.of(
    "userId" to "user-123",
    "tenantId" to "tenant-456"
)

withContext(agentContext) {
    runner.run(graph, input)
}

// Context automatically flows to all nodes
```

### Tip 3: Validate Before Deployment

```kotlin
import io.github.noailabs.spice.graph.GraphValidator

// Check for issues before running
val validation = GraphValidator.validate(graph)
if (validation.isFailure) {
    println("Graph validation failed: ${validation.error.message}")
}

// Check for cycles
val cycles = GraphValidator.findCycles(graph)
if (cycles.isNotEmpty()) {
    println("Found cycles: $cycles")
}
```

## Troubleshooting

### Issue: "Node not found"

Make sure all edges reference valid node IDs:

```kotlin
// ❌ Wrong
edge("analyzer", "typo-node")

// ✅ Correct
edge("analyzer", "responder")
```

### Issue: "Graph must have at least one node"

Ensure you add nodes before building:

```kotlin
// ❌ Wrong
val graph = graph("empty") {
    // No nodes added
}

// ✅ Correct
val graph = graph("valid") {
    agent("my-agent", myAgent)
    output("result") { it.state["my-agent"] }
}
```

### Issue: Result is null

Check the output selector:

```kotlin
// ❌ Wrong - key doesn't exist
output("result") { it.state["nonexistent-key"] }

// ✅ Correct - use actual node ID
output("result") { it.state["responder"] }

// ✅ Or use _previous for last node
output("result") { it.state["_previous"] }
```

## Complete Working Example

Here's a complete, copy-pasteable example:

```kotlin
import io.github.noailabs.spice.*
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.graph.dsl.graph
import io.github.noailabs.spice.graph.runner.DefaultGraphRunner
import io.github.noailabs.spice.graph.middleware.LoggingMiddleware
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Define agent
    val echoAgent = object : Agent {
        override val id = "echo"
        override val name = "Echo Agent"
        override val description = "Echoes input"
        override val capabilities = listOf("echo")

        override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
            return SpiceResult.success(
                comm.reply("Echo: ${comm.content}", id)
            )
        }

        override fun canHandle(comm: Comm) = true
        override fun getTools() = emptyList<Tool>()
        override fun isReady() = true
    }

    // Build graph
    val graph = graph("echo-workflow") {
        middleware(LoggingMiddleware())
        agent("echo", echoAgent)
        output("result") { it.state["echo"] }
    }

    // Execute
    val runner = DefaultGraphRunner()
    val result = runner.run(
        graph = graph,
        input = mapOf("input" to "Hello, Spice!")
    )

    // Print result
    when (result) {
        is SpiceResult.Success -> {
            println("Result: ${result.value.result}")
            println("Duration: ${result.value.duration}")
        }
        is SpiceResult.Failure -> {
            println("Error: ${result.error.message}")
        }
    }
}
```

Output:
```
[INFO] Graph echo-workflow started (run: ...)
[DEBUG] Executing node: echo
[DEBUG] Node echo succeeded
[INFO] Graph echo-workflow finished with status SUCCESS
Result: Echo: Hello, Spice!
Duration: PT0.085S
```

Congratulations! You've built your first Spice Graph workflow! 🎉
