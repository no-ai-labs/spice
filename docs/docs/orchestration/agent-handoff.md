---
sidebar_position: 11
---

# Agent Handoff Pattern

Agent Handoff is a pattern where an AI Agent determines that **human intervention is needed** during processing and **asynchronously** hands off the task to a human agent.

## HITL vs Agent Handoff

| Aspect | HITL (Human-in-the-Loop) | Agent Handoff |
|--------|-------------------------|---------------|
| **Graph State** | Paused (WAITING) | Continues/Completes |
| **Wait Mode** | Synchronous wait | Asynchronous transfer |
| **Decision Maker** | Graph designer | Agent itself |
| **Resume Method** | Resume API call | New Comm transmission |
| **Use Case** | Approval workflows | Chatbot→Agent escalation |

```kotlin
// HITL: Graph pauses and waits
graph("approval") {
    agent("draft", draftAgent)
    humanNode("approve", "Approve?")  // 🛑 Graph pauses here
    agent("publish", publishAgent)
}

// Handoff: Agent decides and transfers on its own, Graph continues
class SmartAgent : Agent {
    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        if (needsHuman(comm)) {
            return handoff(comm)  // 🔄 Transfer to human, Graph continues
        }
        return processNormally(comm)
    }
}
```

## Core Components

### 1. HandoffRequest
Request containing information needed when transferring to a human:

```kotlin
@Serializable
data class HandoffRequest(
    val reason: String,                    // Reason for handoff
    val tasks: List<HandoffTask>,         // List of tasks for human
    val priority: HandoffPriority,        // Priority level
    val conversationHistory: List<String>, // Conversation history
    val metadata: Map<String, String>,    // Additional metadata
    val fromAgentId: String,              // Original Agent ID
    val toAgentId: String                 // Destination (e.g., human-agent-pool)
)

@Serializable
data class HandoffTask(
    val id: String,
    val description: String,               // Task description
    val type: HandoffTaskType,            // Task type
    val context: Map<String, String>,     // Task-specific context
    val required: Boolean                 // Whether required
)
```

### 2. HandoffResponse
Response returned by human after completing work:

```kotlin
@Serializable
data class HandoffResponse(
    val handoffId: String,                // Original request ID
    val humanAgentId: String,             // Human agent ID who handled this
    val result: String,                   // Result
    val completedTasks: List<CompletedTask>, // Completed tasks
    val returnToBot: Boolean,             // Whether to return to bot
    val notes: String?                    // Additional notes
)
```

## Usage Examples

### 1. Basic Handoff (AICC Agent Escalation)

```kotlin
class CustomerServiceAgent(override val id: String = "cs-bot") : Agent {
    override val name = "Customer Service Bot"
    override val description = "24/7 customer support"
    override val capabilities = listOf("faq", "account-info")

    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        val intent = analyzeIntent(comm.content)

        // Handoff complex inquiries to human
        if (intent.confidence < 0.7 || intent.requiresHuman) {
            return SpiceResult.success(
                comm.handoff(fromAgentId = id) {
                    reason = "Complex customer inquiry requires human agent"
                    priority = HandoffPriority.HIGH
                    toAgentId = "human-agent-pool"

                    // Specify tasks for human
                    task(
                        description = "Investigate customer account issue",
                        type = HandoffTaskType.INVESTIGATE,
                        required = true,
                        context = mapOf(
                            "customer_id" to (comm.context?.userId ?: "unknown"),
                            "issue_type" to intent.category
                        )
                    )

                    task(
                        description = "Provide solution to customer",
                        type = HandoffTaskType.RESPOND,
                        required = true
                    )

                    // Transfer conversation history
                    addHistory("Customer: ${comm.content}")
                    addHistory("Bot confidence: ${intent.confidence}")

                    // Metadata
                    addMetadata("session_id", comm.conversationId ?: "unknown")
                    addMetadata("language", "en")
                }
            )
        }

        // Bot can handle
        return SpiceResult.success(comm.reply(handleFAQ(comm.content), id))
    }

    override fun canHandle(comm: Comm) = true
    override fun getTools() = emptyList<Tool>()
    override fun isReady() = true
}
```

### 2. Human Agent Processing and Return

```kotlin
class HumanAgent(override val id: String = "human-agent-john") : Agent {
    override val name = "John (Human Agent)"
    override val description = "Human customer service agent"
    override val capabilities = listOf("complex-support")

    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        // Check if this is a handoff request
        if (comm.isHandoff()) {
            val request = comm.getHandoffRequest()
            if (request != null) {
                println("📨 Handoff received: ${request.reason}")
                println("📋 Tasks:")
                request.tasks.forEach { task ->
                    println("  - [${task.type}] ${task.description}")
                }

                // Human performs actual work (via UI in reality)
                val result = performHumanWork(request)

                // Return to original agent after completion
                return SpiceResult.success(
                    comm.returnFromHandoff(
                        humanAgentId = id,
                        result = result,
                        completedTasks = listOf(
                            CompletedTask(
                                taskId = request.tasks[0].id,
                                result = "Account issue resolved",
                                success = true
                            )
                        ),
                        notes = "Customer issue has been resolved"
                    )
                )
            }
        }

        return SpiceResult.success(comm.reply("Processing...", id))
    }

    private fun performHumanWork(request: HandoffRequest): String {
        // In reality, human works through UI
        // This is a simulation
        return "We've identified and resolved your account issue. " +
               "Please let us know if you need further assistance!"
    }

    override fun canHandle(comm: Comm) = comm.isHandoff()
    override fun getTools() = emptyList<Tool>()
    override fun isReady() = true
}
```

### 3. Bot Processing Returned Response

```kotlin
class SmartBotAgent(override val id: String = "smart-bot") : Agent {
    override val name = "Smart Bot"
    override val description = "AI bot with human escalation"
    override val capabilities = listOf("auto-response", "handoff")

    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        // Check if returned from human
        if (comm.isReturnFromHandoff()) {
            val response = comm.getHandoffResponse()
            if (response != null) {
                println("✅ Returned from human: ${response.result}")
                println("📝 Human notes: ${response.notes}")

                // Continue processing using human's response
                return SpiceResult.success(
                    comm.reply(
                        content = "Thank you! Agent result: ${response.result}",
                        to = id
                    )
                )
            }
        }

        // Regular processing
        if (isComplexQuery(comm.content)) {
            // Handoff
            return SpiceResult.success(
                comm.handoff(fromAgentId = id) {
                    reason = "Complex inquiry"
                    task("Resolve inquiry", HandoffTaskType.RESPOND, true)
                }
            )
        }

        return SpiceResult.success(comm.reply("Auto response: ${comm.content}", id))
    }

    private fun isComplexQuery(content: String): Boolean {
        // In reality, use ML model to determine
        return content.contains("refund") || content.contains("account issue")
    }

    override fun canHandle(comm: Comm) = true
    override fun getTools() = emptyList<Tool>()
    override fun isReady() = true
}
```

### 4. Using Handoff in Graph

```kotlin
val customerSupportGraph = graph("customer-support") {
    agent("bot", CustomerServiceAgent())
    agent("human", HumanAgent())
    agent("smart-bot", SmartBotAgent())

    // Define edges (detect handoff)
    edge("bot", "human") { result ->
        // Check handoff in Comm
        val comm = result.data as? Comm
        comm?.isHandoff() == true
    }

    edge("human", "smart-bot") { result ->
        // Check if returned from human
        val comm = result.data as? Comm
        comm?.isReturnFromHandoff() == true
    }

    output("final") { ctx -> ctx.state["smart-bot"] }
}

// Execute
val runner = DefaultGraphRunner()
val result = runner.run(
    graph = customerSupportGraph,
    input = mapOf(
        "input" to Comm(
            content = "I can't log into my account. I want a refund.",
            from = "customer-123"
        )
    )
).getOrThrow()
```

## Real-world AICC Workflow

```kotlin
// 1. Bot initial response
val initialComm = Comm(content = "I want to refund this product", from = "customer")

// 2. Bot determines it's complex → Handoff
val handoffComm = csBot.processComm(initialComm).getOrThrow()
// handoffComm.isHandoff() == true
// handoffComm.getHandoffRequest()?.tasks == [verify refund policy, respond to customer]

// 3. CommHub routes to human-agent-pool
commHub.send(handoffComm)

// 4. Human agent receives and processes
val humanResponse = humanAgent.processComm(handoffComm).getOrThrow()
// humanResponse.isReturnFromHandoff() == true

// 5. Bot receives return response and concludes
val finalResponse = csBot.processComm(humanResponse).getOrThrow()
println(finalResponse.content)  // "Refund has been processed..."
```

## Priority Management

```kotlin
comm.handoff(fromAgentId = id) {
    reason = "Urgent refund request"
    priority = HandoffPriority.URGENT  // LOW, NORMAL, HIGH, URGENT

    task("Requires immediate processing", HandoffTaskType.RESPOND, required = true)
}
```

## Task Types (HandoffTaskType)

- `RESPOND`: Respond to customer
- `APPROVE`: Approve/reject
- `REVIEW`: Review content
- `INVESTIGATE`: Investigation needed
- `ESCALATE`: Further escalation
- `CUSTOM`: Custom task

## Integration with AgentContext

Handoff automatically propagates `AgentContext`:

```kotlin
withAgentContext(
    userId = "customer-123",
    tenantId = "company-abc",
    sessionId = "session-xyz"
) {
    val handoffComm = csBot.processComm(comm).getOrThrow()

    // AgentContext is automatically propagated
    val request = handoffComm.getHandoffRequest()
    // Human agent maintains same context during processing
}
```

## Checklist

✅ **Determine handoff timing** - Define situations bot cannot handle
✅ **Specify tasks** - Clearly communicate what human needs to do
✅ **Conversation history** - Provide sufficient context
✅ **Process returns** - Continue with human's response
✅ **Prioritization** - Route based on urgency
✅ **Context propagation** - Safely handle multi-tenant environments

## Next Steps

- [HITL (Human-in-the-Loop)](../roadmap/release-1-0-0#hitl-queue--arbiter-wiring) - Graph-level synchronous approval
- [Multi-Agent Orchestration](/docs/orchestration/multi-agent) - Coordinating multiple agents
- [Context Propagation](/docs/guides/context-propagation) - Utilizing AgentContext
