---
sidebar_position: 10
---

# HITL (Human-in-the-Loop)

HITL (Human-in-the-Loop) is a pattern where a **Graph pauses execution synchronously** at designated points to wait for human input, then resumes based on the human's response.

## ⚠️ HITL vs Agent Handoff

HITL and Agent Handoff are fundamentally different patterns:

| Aspect | HITL | Agent Handoff |
|--------|------|---------------|
| **Graph State** | Paused (WAITING) | Continues/Completes |
| **Wait Mode** | Synchronous wait | Asynchronous transfer |
| **Decision Maker** | Graph designer | Agent itself |
| **Resume Method** | Resume API | New Comm |
| **Use Case** | Approval workflows | Chatbot→Agent escalation |

```kotlin
// HITL: Graph pauses and waits
graph("approval") {
    agent("draft", draftAgent)
    humanNode("approve", "Approve?")  // 🛑 Graph pauses here
    agent("publish", publishAgent)
}

// Handoff: Agent decides and transfers, Graph continues
class SmartAgent : Agent {
    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        if (needsHuman(comm)) {
            return handoff(comm)  // 🔄 Transfer to human, Graph continues
        }
        return processNormally(comm)
    }
}
```

**Agent Handoff** is already implemented in Spice 0.5.0. [View Documentation](/docs/orchestration/agent-handoff)

## Core Components

### 1. HumanNode

Special Node type that pauses graph execution:

```kotlin
humanNode(
    id = "review",
    prompt = "Please review the draft",
    options = listOf(
        HumanOption("approve", "Approve", "Approve and continue"),
        HumanOption("reject", "Reject", "Reject and rewrite")
    ),
    timeout = Duration.ofMinutes(30),  // Optional
    validator = { response ->           // Optional
        response.selectedOption != null
    }
)
```

### 2. DynamicHumanNode (Added in 0.8.0)

**Breaking free from static prompts!** `DynamicHumanNode` reads the prompt text from `NodeContext` at runtime, allowing agents to generate prompts dynamically based on their processing results.

```kotlin
dynamicHumanNode(
    id = "select-reservation",
    promptKey = "menu_text",  // Reads from ctx.state["menu_text"] or ctx.context["menu_text"]
    fallbackPrompt = "Please make a selection",
    options = emptyList(),    // Optional predefined options
    timeout = Duration.ofMinutes(10)
)
```

#### Key Differences from HumanNode

| Feature | HumanNode | DynamicHumanNode |
|---------|-----------|------------------|
| **Prompt Source** | Static (compile-time) | Dynamic (runtime from NodeContext) |
| **Use Case** | Fixed approval prompts | Agent-generated menus/messages |
| **Flexibility** | Limited to predefined text | Adapts to execution results |

#### Example: Agent-Generated Reservation Menu

```kotlin
val workflowGraph = graph("reservation-workflow") {
    // Agent lists reservations and stores menu in state
    agent("list-reservations", listAgent)

    // DynamicHumanNode reads menu from state["menu_text"]
    dynamicHumanNode(
        id = "select-reservation",
        promptKey = "menu_text",
        fallbackPrompt = "Please make a selection"
    )

    // Agent processes user selection
    agent("cancel-reservation", cancelAgent)
}

// In the listAgent:
class ListReservationsAgent : Agent {
    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        val reservations = fetchReservations(comm)

        // Generate dynamic menu
        val menuText = buildString {
            appendLine("어떤 예약을 선택하시겠어요?")
            appendLine()
            reservations.forEachIndexed { index, res ->
                appendLine("${index + 1}. ${res.name} | ${res.checkIn} | ${res.checkOut}")
            }
        }

        // Store menu and data in comm for next node
        return SpiceResult.success(
            comm.reply(
                content = "Found ${reservations.size} reservations",
                from = id,
                data = mapOf(
                    "menu_text" to menuText,
                    "reservations_json" to reservations.toJson(),
                    "reservations_count" to reservations.size.toString()
                )
            )
        )
    }
}
```

#### Prompt Resolution Order

`DynamicHumanNode` checks the following sources in priority order:

1. **`ctx.state[promptKey]`** - Direct state updates from previous nodes
2. **`ctx.context.get(promptKey)`** - Metadata from AgentNode (via `comm.data`)
3. **`fallbackPrompt`** - Default if key not found

This ensures maximum flexibility across different checkpoint resume scenarios.

#### Checkpoint Resume Support

`DynamicHumanNode` works seamlessly with checkpointing:

```kotlin
// Turn 1: Agent generates menu, graph pauses
val pausedReport = runner.runWithCheckpoint(
    graph = workflowGraph,
    input = mapOf("userId" to "user123"),
    store = checkpointStore
).getOrThrow()

// Checkpoint saves:
// - state["menu_text"] = "1. Hotel A\n2. Hotel B\n..."
// - context["menu_text"] = "1. Hotel A\n2. Hotel B\n..."
// - context["reservations_json"] = "[{...}, {...}]"

// Turn 2: Resume with user selection
val finalReport = runner.resumeWithHumanResponse(
    graph = workflowGraph,
    checkpointId = pausedReport.checkpointId!!,
    response = HumanResponse.text("select-reservation", "1"),
    store = checkpointStore
).getOrThrow()

// DynamicHumanNode restores menu_text from checkpoint
// Agent accesses reservations_json from restored context
```

### 3. HumanResponse

Human's input after interaction:

```kotlin
// Multiple choice response
val response = HumanResponse.choice(
    nodeId = "review",
    optionId = "approve"
)

// Free text response
val response = HumanResponse.text(
    nodeId = "feedback",
    text = "Please add more details to section 3"
)
```

### 4. Graph Execution States

```kotlin
enum class GraphExecutionState {
    RUNNING,           // Normal execution
    WAITING_FOR_HUMAN, // Paused for human input
    COMPLETED,         // Finished successfully
    FAILED,            // Failed with error
    CANCELLED          // Cancelled
}
```

## Usage Examples

### 1. Basic Approval Workflow

```kotlin
val approvalGraph = graph("approval-workflow") {
    agent("draft", draftAgent)  // Create draft

    // Human reviews and approves/rejects
    humanNode(
        id = "review",
        prompt = "Please review the draft",
        options = listOf(
            HumanOption("approve", "Approve", "Approve draft and continue"),
            HumanOption("reject", "Reject", "Reject draft and rewrite")
        )
    )

    // Conditional branching based on human response
    edge("review", "publish") { result ->
        (result.data as? HumanResponse)?.selectedOption == "approve"
    }
    edge("review", "draft") { result ->
        (result.data as? HumanResponse)?.selectedOption == "reject"
    }

    agent("publish", publishAgent)
}

val runner = DefaultGraphRunner()
val checkpointStore = InMemoryCheckpointStore()

// Step 1: Start graph execution (pauses at HumanNode)
val initialResult = runner.runWithCheckpoint(
    graph = approvalGraph,
    input = mapOf("content" to "Initial draft"),
    store = checkpointStore
).getOrThrow()

// Verify graph paused
println("Status: ${initialResult.status}") // PAUSED
val interaction = initialResult.result as HumanInteraction
println("Prompt: ${interaction.prompt}") // "Please review the draft"

// Step 2: Get pending interactions
val pending = runner.getPendingInteractions(
    checkpointId = initialResult.checkpointId!!,
    store = checkpointStore
).getOrThrow()

println("Waiting for: ${pending.first().prompt}")

// Step 3: Human provides response
val humanResponse = HumanResponse.choice(
    nodeId = "review",
    optionId = "approve"
)

// Step 4: Resume execution
val finalResult = runner.resumeWithHumanResponse(
    graph = approvalGraph,
    checkpointId = initialResult.checkpointId!!,
    response = humanResponse,
    store = checkpointStore
).getOrThrow()

println("Final status: ${finalResult.status}") // SUCCESS
```

### 2. Free Text Input

```kotlin
val feedbackGraph = graph("collect-feedback") {
    agent("explain", explainerAgent)

    // Get free text input from human
    humanNode(
        id = "get-feedback",
        prompt = "Please provide your detailed feedback"
        // No options = free text input mode
    )

    agent("process", processorAgent)
}

// ... execute and pause ...

// Human provides free text
val response = HumanResponse.text(
    nodeId = "get-feedback",
    text = "The explanation is clear, but please add examples for edge cases."
)

// Resume with text input
val result = runner.resumeWithHumanResponse(
    graph = feedbackGraph,
    checkpointId = checkpointId,
    response = response,
    store = checkpointStore
).getOrThrow()
```

### 3. Timeout Handling

```kotlin
val urgentApprovalGraph = graph("urgent-approval") {
    agent("create-request", requestAgent)

    // Human must respond within 30 minutes
    humanNode(
        id = "urgent-review",
        prompt = "URGENT: Approve within 30 minutes",
        timeout = Duration.ofMinutes(30),
        options = listOf(
            HumanOption("approve", "Approve"),
            HumanOption("reject", "Reject")
        )
    )

    // Handle timeout
    edge("urgent-review", "auto-reject") { result ->
        // Timeout results in null response
        result.data == null
    }
    edge("urgent-review", "approved") { result ->
        (result.data as? HumanResponse)?.selectedOption == "approve"
    }

    agent("auto-reject", autoRejectAgent)
    agent("approved", approvedAgent)
}
```

### 4. Multiple Sequential Approvals

```kotlin
val multiApprovalGraph = graph("multi-stage-approval") {
    agent("draft", draftAgent)

    humanNode(
        id = "technical-review",
        prompt = "Technical review",
        options = listOf(HumanOption("ok", "Approve"))
    )

    humanNode(
        id = "legal-review",
        prompt = "Legal review",
        options = listOf(HumanOption("ok", "Approve"))
    )

    humanNode(
        id = "executive-review",
        prompt = "Executive approval",
        options = listOf(HumanOption("ok", "Approve"))
    )

    agent("publish", publishAgent)
}

// First pause - technical review
val techPause = runner.runWithCheckpoint(graph, input, store).getOrThrow()
val techResume = runner.resumeWithHumanResponse(
    graph, techPause.checkpointId!!,
    HumanResponse.choice("technical-review", "ok"),
    store
).getOrThrow()

// Second pause - legal review
val legalResume = runner.resumeWithHumanResponse(
    graph, techResume.checkpointId!!,
    HumanResponse.choice("legal-review", "ok"),
    store
).getOrThrow()

// Third pause - executive review
val finalResult = runner.resumeWithHumanResponse(
    graph, legalResume.checkpointId!!,
    HumanResponse.choice("executive-review", "ok"),
    store
).getOrThrow()
```

### 5. Conditional Branching

```kotlin
val reviewGraph = graph("conditional-review") {
    agent("analyze", analyzeAgent)

    humanNode(
        id = "decision",
        prompt = "Choose next action",
        options = listOf(
            HumanOption("approve", "Approve as-is"),
            HumanOption("revise", "Request revision"),
            HumanOption("reject", "Reject completely")
        )
    )

    // Three different paths based on human choice
    edge("decision", "publish") { result ->
        (result.data as? HumanResponse)?.selectedOption == "approve"
    }
    edge("decision", "revise") { result ->
        (result.data as? HumanResponse)?.selectedOption == "revise"
    }
    edge("decision", "archive") { result ->
        (result.data as? HumanResponse)?.selectedOption == "reject"
    }

    agent("publish", publishAgent)
    agent("revise", reviseAgent)
    agent("archive", archiveAgent)
}
```

## Integration with AgentContext

HITL automatically preserves AgentContext across pause/resume:

```kotlin
withAgentContext(
    userId = "user-123",
    tenantId = "company-abc",
    sessionId = "session-xyz"
) {
    // Start graph - context is saved in checkpoint
    val pausedResult = runner.runWithCheckpoint(
        graph = approvalGraph,
        input = mapOf("document" to "Draft v1"),
        store = checkpointStore
    ).getOrThrow()

    // ... later, when resuming ...
    // Context is automatically restored
    val finalResult = runner.resumeWithHumanResponse(
        graph = approvalGraph,
        checkpointId = pausedResult.checkpointId!!,
        response = HumanResponse.choice("review", "approve"),
        store = checkpointStore
    ).getOrThrow()

    // All nodes after resume still have the same AgentContext
}
```

## API Reference

### GraphRunner Methods

```kotlin
interface GraphRunner {
    /**
     * Resume execution after receiving human response.
     */
    suspend fun resumeWithHumanResponse(
        graph: Graph,
        checkpointId: String,
        response: HumanResponse,
        store: CheckpointStore
    ): SpiceResult<RunReport>

    /**
     * Get pending human interactions from a checkpoint.
     */
    suspend fun getPendingInteractions(
        checkpointId: String,
        store: CheckpointStore
    ): SpiceResult<List<HumanInteraction>>
}
```

### RunReport

When a graph pauses for human input:

```kotlin
data class RunReport(
    val graphId: String,
    val status: RunStatus,        // PAUSED when waiting for human
    val result: Any?,             // HumanInteraction when paused
    val duration: Duration,
    val nodeReports: List<NodeReport>,
    val error: Throwable? = null,
    val checkpointId: String? = null  // Set when status is PAUSED
)
```

### HumanInteraction

Information about pending human input:

```kotlin
data class HumanInteraction(
    val nodeId: String,
    val prompt: String,
    val options: List<HumanOption>,
    val pausedAt: String,          // ISO-8601 timestamp
    val expiresAt: String? = null, // ISO-8601 timestamp (if timeout set)
    val allowFreeText: Boolean = false
)
```

## Use Cases

1. **Document Approval Workflow**: Draft → Review → Approve/Reject → Publish
2. **Data Validation**: AI analysis → Human verification → Final decision
3. **Risky Operation Approval**: Request generation → Manager approval → Execution
4. **Collaborative Workflow**: AI suggestion → Human modification → AI reprocessing
5. **Quality Control**: Automated check → Human inspection → Release
6. **Multi-stage Approval**: Technical → Legal → Executive approvals

## Best Practices

### ✅ Do

- Use HITL when **graph designer** determines where human input is needed
- Save `checkpointId` from paused report for resuming later
- Check `status == RunStatus.PAUSED` to detect HITL pause
- Use `getPendingInteractions()` to get interaction details
- Validate human responses before resuming (optional)
- Set reasonable timeouts for time-sensitive approvals
- Use descriptive prompts and option labels

### ❌ Don't

- Don't use HITL when agent should decide to escalate (use Agent Handoff instead)
- Don't lose the `checkpointId` - you need it to resume
- Don't assume graph will complete without checking status
- Don't ignore timeout requirements for urgent workflows

## Next Steps

- [Agent Handoff](/docs/orchestration/agent-handoff) - Asynchronous human escalation pattern
- [Graph Checkpointing](/docs/orchestration/graph-checkpoint) - Save and restore graph state
- [Graph Middleware](/docs/orchestration/graph-middleware) - Intercept graph execution
- [Multi-Agent Orchestration](/docs/orchestration/multi-agent) - Coordinate multiple agents
