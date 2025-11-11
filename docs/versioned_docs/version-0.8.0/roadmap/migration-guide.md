# Migration Guide: 0.4.x → 0.5.0

:::info Good News
**Your existing Agents still work!** The Agent interface hasn't changed. Only orchestration patterns (Swarm/Flow) need migration.
:::

## TL;DR

### ✅ No Changes Needed
- **Agent implementations** - Use as-is
- **Tool implementations** - Use as-is
- **Comm usage** - Compatible
- **AgentContext** - Compatible
- **SpiceResult** - Compatible

### 🔄 Changes Required
- **Swarm orchestration** → Graph
- **Flow orchestration** → Graph
- **Context access in orchestration** → `ctx.state["key"]`

## Step-by-Step Migration

### Step 1: Update Dependencies

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.noailabs:spice-core:0.5.0")
}
```

### Step 2: Check Your Usage Pattern

#### Pattern A: Using Agents Only (No Migration Needed!)

```kotlin
// ✅ This still works in 0.5.0!
val myAgent = agent {
    name = "My Agent"
    tools = listOf(myTool)
}

val result = myAgent.processComm(comm).getOrThrow()
```

**Action**: Nothing! Your code works as-is.

#### Pattern B: Using Swarm/Flow (Migration Required)

If you're using `swarm {}` or `flow {}`, continue to Step 3.

### Step 3: Migrate Swarm → Graph

**Before (0.4.x):**
```kotlin
val mySwarm = swarm("customer-support") {
    agent("classifier", classifierAgent)
    agent("technical", technicalAgent)
    agent("billing", billingAgent)
}

// Run swarm
val result = mySwarm.run(initialComm)
```

**After (0.5.0):**
```kotlin
val myGraph = graph("customer-support") {
    agent("classifier", classifierAgent)
    agent("technical", technicalAgent)
    agent("billing", billingAgent)

    // Define edges (how agents connect)
    edge("classifier", "technical")
    edge("classifier", "billing")
}

// Run graph
val runner = DefaultGraphRunner()
val result = runner.run(
    graph = myGraph,
    input = mapOf("comm" to initialComm)
).getOrThrow()
```

### Step 4: Migrate Context Access

**Before (0.4.x):**
```kotlin
swarm("workflow") {
    agent("step1", agent1)
    agent("step2", agent2) { ctx ->
        val previousResult = ctx["step1"]  // Old context access
    }
}
```

**After (0.5.0):**
```kotlin
graph("workflow") {
    agent("step1", agent1)
    agent("step2", agent2)

    // Access results via NodeContext in custom nodes
    // Or use edges to control flow
    edge("step1", "step2") { result ->
        // result.data contains step1's output
        result.data != null
    }
}
```

## Breaking Changes Summary

| Area | 0.4.x | 0.5.0 | Impact |
|------|-------|-------|--------|
| **Agent API** | `Agent` interface | **Same** | ✅ No change |
| **Tool API** | `Tool` interface | **Same** | ✅ No change |
| **Comm** | `Comm` class | **Same** | ✅ No change |
| **Swarm** | `swarm {}` | `graph {}` | 🔄 Migrate |
| **Flow** | `flow {}` | `graph {}` | 🔄 Migrate |
| **Context** | Implicit context | `NodeContext.state` | 🔄 Update access |

## Real-World Migration Examples

### Example 1: Simple Agent (No Changes!)

**0.4.x and 0.5.0 - Both work the same:**
```kotlin
// Your agent implementation
class CustomerServiceAgent : Agent {
    override val id = "cs-agent"
    override val name = "Customer Service"
    override val capabilities = listOf("support", "faq")

    override suspend fun processComm(comm: Comm): SpiceResult<Comm> {
        // Process comm and return result
        return SpiceResult.success(comm.reply("Response", id))
    }
}

// Using the agent
val agent = CustomerServiceAgent()
val result = agent.processComm(myComm).getOrThrow()
```

**Migration effort**: ✅ Zero! Copy-paste your code.

### Example 2: Multi-Agent Workflow

**Before (0.4.x) - Using Swarm:**
```kotlin
val supportSwarm = swarm("customer-support") {
    member("classifier") { classifierAgent }
    member("technical") { technicalAgent }
    member("billing") { billingAgent }

    strategy = SwarmStrategy.SEQUENTIAL
}

// Execute
val result = supportSwarm.execute(
    input = initialComm,
    context = myContext
)
```

**After (0.5.0) - Using Graph:**
```kotlin
val supportGraph = graph("customer-support") {
    agent("classifier", classifierAgent)
    agent("technical", technicalAgent)
    agent("billing", billingAgent)
}

// Execute
val runner = DefaultGraphRunner()
val result = runner.run(
    graph = supportGraph,
    input = mapOf("comm" to initialComm)
).getOrThrow()
```

**Migration effort**: 🟡 Low - Replace `swarm {}` with `graph {}`, update execution API.

### Example 3: Conditional Routing

**Before (0.4.x):**
```kotlin
swarm("support") {
    agent("classifier", classifierAgent)

    handoff("classifier" to "technical") { ctx ->
        ctx["category"] == "technical"
    }
    handoff("classifier" to "billing") { ctx ->
        ctx["category"] == "billing"
    }

    agent("technical", technicalAgent)
    agent("billing", billingAgent)
}
```

**After (0.5.0):**
```kotlin
graph("support") {
    agent("classifier", classifierAgent)
    agent("technical", technicalAgent)
    agent("billing", billingAgent)

    // Conditional edges
    edge("classifier", "technical") { result ->
        val comm = result.data as? Comm
        comm?.data?.get("category") == "technical"
    }
    edge("classifier", "billing") { result ->
        val comm = result.data as? Comm
        comm?.data?.get("category") == "billing"
    }
}
```

**Migration effort**: 🟡 Medium - Update routing logic to use edges.

### Example 4: Using New 0.5.0 Features

**Add Middleware (Optional):**
```kotlin
graph("monitored-workflow") {
    agent("step1", agent1)
    agent("step2", agent2)

    // Add logging
    middleware(LoggingMiddleware())

    // Add metrics
    middleware(MetricsMiddleware())
}
```

**Add Checkpointing (Optional):**
```kotlin
val checkpointStore = InMemoryCheckpointStore()

val result = runner.runWithCheckpoint(
    graph = myGraph,
    input = mapOf("data" to myData),
    store = checkpointStore,
    config = CheckpointConfig(saveEveryNNodes = 5)
).getOrThrow()

// Resume later if needed
val resumed = runner.resume(
    graph = myGraph,
    checkpointId = result.checkpointId,
    store = checkpointStore
).getOrThrow()
```

**Add HITL - Human Approval (Optional):**
```kotlin
graph("approval-workflow") {
    agent("draft", draftAgent)

    // Pause for human review
    humanNode(
        id = "review",
        prompt = "Approve or reject?",
        options = listOf(
            HumanOption("approve", "Approve"),
            HumanOption("reject", "Reject")
        )
    )

    agent("publish", publishAgent)

    edge("review", "publish") { result ->
        (result.data as? HumanResponse)?.selectedOption == "approve"
    }
}
```

## Common Migration Patterns

### Pattern: Tool Chain

**0.4.x:**
```kotlin
flow("data-processing") {
    step("fetch") { fetchData() }
    step("transform") { transformData(it) }
    step("save") { saveData(it) }
}
```

**0.5.0:**
```kotlin
graph("data-processing") {
    tool("fetch", fetchTool)
    tool("transform", transformTool)
    tool("save", saveTool)
}
```

### Pattern: Multi-Tenant Context

**Both versions - Same AgentContext API:**
```kotlin
withAgentContext(
    userId = "user-123",
    tenantId = "tenant-abc"
) {
    // 0.4.x
    val swarmResult = mySwarm.execute(comm)

    // 0.5.0
    val graphResult = runner.run(myGraph, input).getOrThrow()

    // Both automatically have access to AgentContext!
}
```

## Migration Checklist

- [ ] Update `build.gradle.kts` to 0.5.0
- [ ] Identify if you use Swarm or Flow
- [ ] If using Swarm → Convert to Graph
- [ ] If using Flow → Convert to Graph
- [ ] Update execution API calls
- [ ] Test thoroughly
- [ ] (Optional) Add Middleware for logging/metrics
- [ ] (Optional) Add Checkpointing for long workflows
- [ ] (Optional) Add HITL for approval workflows

## Migration Examples

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
