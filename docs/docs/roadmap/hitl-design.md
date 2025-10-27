---
sidebar_position: 4
---

# HITL (Human-in-the-Loop) Design

HITL (Human-in-the-Loop) is a feature that defines points during Graph execution where human intervention is required, and proceeds based on approval/rejection/modification.

Designed for Spice based on Microsoft Agent Framework's Human-in-the-Loop pattern.

## ⚠️ Difference from Agent Handoff

**HITL** and **Agent Handoff** are completely different patterns:

| Aspect | HITL | Agent Handoff |
|--------|------|---------------|
| **Graph State** | Paused (WAITING) | Continues/Completes |
| **Wait Mode** | Synchronous wait | Asynchronous transfer |
| **Decision Maker** | Graph designer | Agent itself |
| **Resume Method** | Resume API | New Comm |
| **Use Case** | Approval workflows | Chatbot→Agent escalation |
| **Implementation Status** | 🔜 Planned | ✅ Already implemented |

**Agent Handoff** is already included in Spice 0.5.0. [View Documentation](/docs/orchestration/agent-handoff)

## Core Concepts

### 1. HumanNode
Special Node type that waits for human input:

```kotlin
class HumanNode(
    override val id: String,
    val prompt: String,  // Message to show user
    val options: List<HumanOption> = emptyList(),  // Choices (optional)
    val timeout: Duration? = null,  // Response wait time (optional)
    val validator: ((HumanResponse) -> Boolean)? = null  // Input validation (optional)
) : Node

data class HumanOption(
    val id: String,
    val label: String,
    val description: String? = null
)

data class HumanResponse(
    val nodeId: String,
    val selectedOption: String? = null,  // Selected option ID
    val text: String? = null,  // Free text input
    val metadata: Map<String, Any> = emptyMap(),
    val timestamp: Instant = Instant.now()
)
```

### 2. Execution State Management

```kotlin
enum class GraphExecutionState {
    RUNNING,           // Normal execution
    WAITING_FOR_HUMAN, // Waiting for human input
    COMPLETED,         // Completed
    FAILED,            // Failed
    CANCELLED          // Cancelled
}

data class HumanInteraction(
    val nodeId: String,
    val prompt: String,
    val options: List<HumanOption>,
    val pausedAt: Instant,
    val expiresAt: Instant? = null
)
```

### 3. GraphRunner Extension

```kotlin
interface GraphRunner {
    // Existing methods...

    /**
     * Resume execution after receiving human input.
     */
    suspend fun resumeWithHumanResponse(
        graph: Graph,
        checkpointId: String,
        response: HumanResponse,
        store: CheckpointStore
    ): SpiceResult<RunReport>

    /**
     * Get current human interactions waiting for response.
     */
    suspend fun getPendingInteractions(
        checkpointId: String,
        store: CheckpointStore
    ): SpiceResult<List<HumanInteraction>>
}
```

## Usage Examples

### 1. Basic Approval/Rejection Pattern

```kotlin
val approvalGraph = graph("approval-workflow") {
    agent("draft", draftAgent)  // Create draft

    // Human reviews and approves/rejects
    humanNode(
        id = "review",
        prompt = "Please review the draft",
        options = listOf(
            HumanOption("approve", "Approve", "Approve draft and continue"),
            HumanOption("reject", "Reject", "Reject draft and rewrite"),
            HumanOption("edit", "Edit", "Edit manually")
        )
    )

    // Conditional branching
    edge("review", "publish") { result ->
        (result.data as? HumanResponse)?.selectedOption == "approve"
    }
    edge("review", "draft") { result ->
        (result.data as? HumanResponse)?.selectedOption == "reject"
    }
    edge("review", "manual-edit") { result ->
        (result.data as? HumanResponse)?.selectedOption == "edit"
    }

    agent("publish", publishAgent)
    agent("manual-edit", editorAgent)
}

// Execution
val runner = DefaultGraphRunner()
val checkpointStore = InMemoryCheckpointStore()

// Step 1: Start graph execution (pauses at HumanNode)
val initialResult = runner.runWithCheckpoint(
    graph = approvalGraph,
    input = mapOf("content" to "Initial draft"),
    store = checkpointStore
).getOrThrow()

// Step 2: Check pending interactions
val pending = runner.getPendingInteractions(
    checkpointId = initialResult.checkpointId,
    store = checkpointStore
).getOrThrow()

println("Waiting: ${pending.first().prompt}")

// Step 3: Provide human response
val humanResponse = HumanResponse(
    nodeId = "review",
    selectedOption = "approve"
)

// Step 4: Resume
val finalResult = runner.resumeWithHumanResponse(
    graph = approvalGraph,
    checkpointId = initialResult.checkpointId,
    response = humanResponse,
    store = checkpointStore
).getOrThrow()
```

### 2. Free Text Input Pattern

```kotlin
val inputGraph = graph("data-collection") {
    agent("explain", explainerAgent)  // Provide explanation

    // Get free text input
    humanNode(
        id = "get-input",
        prompt = "Please provide additional information",
        validator = { response ->
            response.text?.length?.let { it >= 10 } ?: false
        }
    )

    agent("process", processorAgent)  // Process input
}
```

### 3. Timeout Pattern

```kotlin
val timeoutGraph = graph("urgent-approval") {
    agent("create-request", requestAgent)

    // Requires response within 30 minutes
    humanNode(
        id = "urgent-review",
        prompt = "Urgent request review (30 minute limit)",
        timeout = Duration.ofMinutes(30),
        options = listOf(
            HumanOption("approve", "Approve"),
            HumanOption("reject", "Reject")
        )
    )

    // Auto-reject on timeout
    edge("urgent-review", "auto-reject") { result ->
        (result.data as? HumanResponse) == null  // timeout = null response
    }
    edge("urgent-review", "approved") { result ->
        (result.data as? HumanResponse)?.selectedOption == "approve"
    }
}
```

## Implementation Plan

### Phase 1: Basic HITL Support
- [ ] Implement `HumanNode`
- [ ] `HumanResponse` data model
- [ ] Checkpoint integration (save HITL waiting state)
- [ ] Implement `resumeWithHumanResponse()`
- [ ] Basic tests

### Phase 2: Advanced Features
- [ ] Timeout support
- [ ] Validator support
- [ ] Multiple choice vs Free text distinction
- [ ] Implement `getPendingInteractions()`
- [ ] Integration tests

### Phase 3: UI/UX Integration
- [ ] REST API for HITL interactions
- [ ] WebSocket real-time notifications
- [ ] Dashboard example
- [ ] Documentation and guides

## Technical Considerations

### 1. Checkpoint Structure
```kotlin
data class Checkpoint(
    val id: String,
    val runId: String,
    val graphId: String,
    val currentNodeId: String,
    val state: Map<String, Any?>,
    val agentContext: AgentContext?,
    val timestamp: Instant,

    // HITL support
    val executionState: GraphExecutionState = GraphExecutionState.RUNNING,
    val pendingInteraction: HumanInteraction? = null
)
```

### 2. Thread Safety
- HumanNode execution is suspend function
- Checkpoint save is atomic
- Concurrency considerations (multiple people responding simultaneously)

### 3. Error Handling
- Timeout processing
- Invalid response handling
- Checkpoint restore failure handling

### 4. AgentContext Integration
```kotlin
// Record human response in context
val enrichedContext = ctx.agentContext?.copy(
    metadata = ctx.agentContext.metadata + mapOf(
        "human_response_${nodeId}" to response,
        "reviewed_by" to response.metadata["user_id"],
        "reviewed_at" to response.timestamp
    )
)
```

## Expected Use Cases

1. **Document Approval Workflow**: Draft → Review → Approve/Reject → Publish
2. **Data Validation**: AI analysis → Human verification → Final decision
3. **Risky Operation Approval**: Request generation → Manager approval → Execution
4. **Collaborative Workflow**: AI suggestion → Human modification → AI reprocessing
5. **Emergency Response**: Auto-detection → Human judgment → Action execution

## Microsoft AF vs Spice HITL

| Feature | Microsoft AF | Spice |
|---------|-------------|-------|
| Human Node | ✅ Built-in | ✅ Planned |
| Checkpoint | ✅ | ✅ Already implemented |
| Resume | ✅ | ✅ Extension needed |
| Timeout | ✅ | 🔜 Phase 2 |
| Validation | ✅ | 🔜 Phase 2 |
| UI Integration | ✅ Dashboard | 🔜 Phase 3 |
| Multi-tenant | ❌ | ✅ AgentContext integration |

## Next Steps

HITL feature will be implemented in 3 phases:

1. **Phase 1** (Core): HumanNode, Resume implementation → MVP
2. **Phase 2** (Advanced): Timeout, Validation → Production-ready
3. **Phase 3** (Integration): REST API, UI examples → User-friendly

Each phase is independently testable, with features added incrementally.
