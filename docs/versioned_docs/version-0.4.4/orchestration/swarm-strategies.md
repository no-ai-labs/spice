# Swarm Strategies Deep Dive

Complete guide to the 5 execution strategies in Spice Framework's Swarm Intelligence system.

## Overview

Swarm strategies define **how multiple agents coordinate** to solve tasks. Each strategy has unique execution patterns, performance characteristics, and ideal use cases.

| Strategy | Execution Pattern | Best For | Complexity |
|----------|------------------|----------|------------|
| **PARALLEL** | All agents simultaneously | Independent analyses | Low |
| **SEQUENTIAL** | Agents in order, passing results | Data pipelines | Low |
| **CONSENSUS** | Multi-round discussion | Decision-making | Medium |
| **COMPETITION** | Best result wins | Creative tasks | Low |
| **HIERARCHICAL** | Levels of delegation | Complex workflows | High |

---

## 1. PARALLEL Strategy

### How It Works

```kotlin
private suspend fun executeParallel(strategy: SwarmStrategy, comm: Comm): SwarmResult {
    // 1. Launch all agents concurrently using async
    val jobs = strategy.selectedAgents.map { agentId ->
        coroutineScope {
            async {
                val agent = memberAgents[agentId] ?: return@async AgentResult(...)
                agent.processComm(comm) // Each agent processes independently
            }
        }
    }

    // 2. Wait for all to complete
    val agentResults = jobs.map { it.await() }

    // 3. Aggregate all results
    return coordinator.aggregateResults(agentResults, strategy)
}
```

**Execution Flow:**

```
User Input
    │
    ├─► Agent 1 ───┐
    ├─► Agent 2 ───┤
    ├─► Agent 3 ───┼─► Aggregate Results ─► Final Output
    ├─► Agent 4 ───┤
    └─► Agent 5 ───┘

    Time: max(agent_times)
```

### Performance Characteristics

- **Latency:** `max(slowest_agent_time)` - As fast as the slowest agent
- **Throughput:** High - All agents work simultaneously
- **Memory:** `O(n)` where n = number of agents
- **Failure Handling:** Continues even if some agents fail

### When to Use

✅ **Perfect for:**
- Multi-perspective analysis
- Independent evaluations
- Gathering diverse opinions
- Data from multiple sources
- Tasks with no dependencies

❌ **Avoid when:**
- Agents need previous results
- Sequential logic required
- Single authoritative answer needed

### Real-World Example: Market Research

```kotlin
// Analyze market from multiple angles simultaneously
val marketSwarm = buildSwarmAgent {
    name = "Market Research Swarm"
    defaultStrategy(SwarmStrategyType.PARALLEL)

    quickSwarm {
        specialist("competitor-analyst", "Competitor Analyst", "analyze competitors")
        specialist("customer-analyst", "Customer Analyst", "analyze customers")
        specialist("trend-analyst", "Trend Analyst", "analyze trends")
        specialist("price-analyst", "Price Analyst", "analyze pricing")
        specialist("risk-analyst", "Risk Analyst", "analyze risks")
    }
}

val result = marketSwarm.processComm(Comm(
    content = "Analyze the market for launching a new AI productivity tool",
    from = "product-manager"
))

// Output: 5 independent analyses aggregated
// ✅ All perspectives gathered in ~same time as longest analysis
```

### Aggregation Output Format

```
🤖 Swarm Analysis (5/5 agents):

Agent competitor-analyst:
- 3 major competitors identified
- Average pricing: $29/month
- Gap in market: Small business segment

Agent customer-analyst:
- Target: Tech professionals 25-45
- Pain point: Context switching
- Willingness to pay: $20-40/month

Agent trend-analyst:
- AI adoption up 150% YoY
- Remote work trend continues
- Integration with existing tools critical

Agent price-analyst:
- Recommended price: $25/month
- Freemium model suggested
- Annual discount: 20%

Agent risk-analyst:
- Competition risk: Medium
- Technology risk: Low
- Market timing: Excellent
```

---

## 2. SEQUENTIAL Strategy

### How It Works

```kotlin
private suspend fun executeSequential(strategy: SwarmStrategy, comm: Comm): SwarmResult {
    var currentComm = comm  // Start with original input
    val agentResults = mutableListOf<AgentResult>()

    // Process agents one by one
    for (agentId in strategy.selectedAgents) {
        val agent = memberAgents[agentId] ?: break

        val result = agent.processComm(currentComm)
        result.fold(
            onSuccess = { response ->
                agentResults.add(AgentResult(agentId, true, response.content))

                // IMPORTANT: Next agent receives previous output!
                currentComm = Comm(
                    content = response.content,  // Previous agent's output
                    from = agentId,
                    data = response.data  // Context accumulated
                )
            },
            onFailure = { error ->
                agentResults.add(AgentResult(agentId, false, error.message))
                break  // Stop pipeline on failure
            }
        )
    }

    return coordinator.aggregateResults(agentResults, strategy)
}
```

**Execution Flow:**

```
User Input
    │
    ▼
┌─────────┐
│ Agent 1 │  Extracts data
└─────────┘
    │ Output becomes input
    ▼
┌─────────┐
│ Agent 2 │  Cleans data
└─────────┘
    │ Output becomes input
    ▼
┌─────────┐
│ Agent 3 │  Analyzes data
└─────────┘
    │
    ▼
Final Result

Time: sum(agent_times)
```

### Performance Characteristics

- **Latency:** `sum(agent_times)` - Adds up all agent times
- **Throughput:** Low - One agent at a time
- **Memory:** `O(1)` - Only current comm in memory
- **Failure Handling:** Stops on first failure (fail-fast)

### When to Use

✅ **Perfect for:**
- Data transformation pipelines
- Multi-stage processing
- Incremental refinement
- Validation chains
- Step-by-step workflows

❌ **Avoid when:**
- Need parallel insights
- Agents are independent
- One failure shouldn't stop all

### Real-World Example: Content Pipeline

```kotlin
// Transform raw content through processing stages
val contentPipeline = buildSwarmAgent {
    name = "Content Processing Pipeline"
    defaultStrategy(SwarmStrategyType.SEQUENTIAL)

    quickSwarm {
        specialist("extractor", "Content Extractor", "Extract key information")
        specialist("summarizer", "Summarizer", "Create concise summary")
        specialist("enhancer", "Content Enhancer", "Improve readability")
        specialist("seo-optimizer", "SEO Optimizer", "Optimize for search")
        specialist("formatter", "Formatter", "Format for publication")
    }
}

val result = contentPipeline.processComm(Comm(
    content = """
        Raw blog post text here...
        [5000 words of unstructured content]
    """,
    from = "content-team"
))

// Output: Fully processed article
// Stage 1: Key points extracted
// Stage 2: Summarized to 500 words
// Stage 3: Enhanced readability score 85 → 92
// Stage 4: SEO keywords added, meta description
// Stage 5: Markdown formatting, images optimized
```

### Data Flow Example

```kotlin
// Input
Comm(content = "Analyze sales data: Q1=100k, Q2=150k, Q3=120k, Q4=180k")

// After Agent 1 (Extractor)
Comm(content = "Extracted: Q1:100k, Q2:150k, Q3:120k, Q4:180k, Total:550k")

// After Agent 2 (Calculator)
Comm(content = "Growth: Q2 +50%, Q3 -20%, Q4 +50%, Avg: +26.7%")

// After Agent 3 (Analyzer)
Comm(content = "Trend: Strong recovery in Q4. Q3 dip likely seasonal.")

// After Agent 4 (Reporter)
Comm(content = "Report: Sales grew 26.7% YoY. Q4 momentum excellent.
                Recommend maintaining Q4 strategies into next year.")

// Final Output: Fully analyzed report
```

---

## 3. CONSENSUS Strategy

### How It Works

```kotlin
private suspend fun executeConsensus(strategy: SwarmStrategy, comm: Comm): SwarmResult {
    // PHASE 1: Initial parallel responses
    val initialResults = executeParallel(strategy, comm)

    // PHASE 2: Cross-evaluation and refinement
    val refinedResults = mutableListOf<AgentResult>()

    for (agentId in strategy.selectedAgents) {
        val agent = memberAgents[agentId] ?: continue

        // Each agent reviews others' perspectives and refines their view
        val consensusComm = Comm(
            content = """
                Based on these perspectives:
                ${initialResults.agentResults.joinToString { it.content }}

                What is your refined view on: ${comm.content}
            """,
            from = "swarm-coordinator"
        )

        val refinedResult = agent.processComm(consensusComm)
        // Collect refined perspectives...
    }

    return coordinator.buildConsensus(refinedResults, strategy)
}
```

**Execution Flow:**

```
Phase 1: Initial Responses (Parallel)
─────────────────────────────────────
User Input
    │
    ├─► Agent 1 → "View A"
    ├─► Agent 2 → "View B"
    └─► Agent 3 → "View C"

Phase 2: Cross-Evaluation (Parallel)
─────────────────────────────────────
"View A, B, C" + Original Question
    │
    ├─► Agent 1 → "Refined View A' (considering B, C)"
    ├─► Agent 2 → "Refined View B' (considering A, C)"
    └─► Agent 3 → "Refined View C' (considering A, B)"

    ▼
Build Consensus
    │
    ▼
Final Decision

Time: 2 × max(agent_times)
```

### Performance Characteristics

- **Latency:** `2 × max(agent_times)` - Two rounds of parallel execution
- **Throughput:** Medium - Two phases required
- **Memory:** `O(2n)` - Stores both rounds of results
- **Failure Handling:** Continues with available agents

### When to Use

✅ **Perfect for:**
- Group decision-making
- Resolving disagreements
- Finding common ground
- Strategic planning
- Balanced perspectives

❌ **Avoid when:**
- Need quick answer
- Single expert sufficient
- Clear right/wrong answer

### Real-World Example: Product Strategy

```kotlin
// Strategic decision with multiple stakeholders
val strategySwarm = buildSwarmAgent {
    name = "Product Strategy Swarm"
    defaultStrategy(SwarmStrategyType.CONSENSUS)

    quickSwarm {
        specialist("product-lead", "Product Lead", "product vision")
        specialist("engineering-lead", "Engineering Lead", "technical feasibility")
        specialist("design-lead", "Design Lead", "user experience")
        specialist("business-lead", "Business Lead", "business impact")
    }

    config {
        timeout(60000)  // Consensus takes time
    }
}

val result = strategySwarm.processComm(Comm(
    content = "Should we build native mobile apps or focus on progressive web app?",
    from = "cto"
))
```

### Consensus Output Format

```
🤝 Swarm Consensus:

Based on 4 agent perspectives, the consensus is:

Key agreements:
- Progressive web app provides faster time-to-market
- Native features needed for offline functionality
- Hybrid approach recommended

Detailed perspectives:

1. Product Lead (refined):
   Initially preferred native apps for best UX. After considering
   engineering timeline and business constraints, agrees PWA first
   with native wrapper for critical features is optimal path.

2. Engineering Lead (refined):
   Technical feasibility favors PWA. However, acknowledging design
   concerns, recommends Capacitor for native capabilities while
   maintaining web codebase.

3. Design Lead (refined):
   UX concerns about PWA limitations remain, but team can achieve
   90% of desired experience with modern PWA APIs. Native wrapper
   addresses remaining gaps.

4. Business Lead (refined):
   Cost-benefit analysis strongly favors PWA. 3-month faster launch,
   50% lower development cost. Native features via Capacitor addresses
   premium user needs.

Final Recommendation:
Build PWA first, use Capacitor for native capabilities, evaluate
fully native apps based on usage metrics after 6 months.

Confidence: 85%
```

---

## 4. COMPETITION Strategy

### How It Works

```kotlin
private suspend fun executeCompetition(strategy: SwarmStrategy, comm: Comm): SwarmResult {
    // 1. Execute all agents in parallel (same as PARALLEL)
    val results = executeParallel(strategy, comm)

    // 2. Select the BEST result based on criteria
    return coordinator.selectBestResult(results, strategy)
}

override suspend fun selectBestResult(results: SwarmResult, strategy: SwarmStrategy): SwarmResult {
    // Default: Select longest/most detailed response
    val best = results.agentResults
        .filter { it.success }
        .maxByOrNull { it.content.length }

    val content = best?.let {
        "🏆 Competition Winner (Agent ${it.agentId}):\n\n${it.content}"
    } ?: "🤖 No winner in competition"

    return SwarmResult(content, results.successRate, results.agentResults)
}
```

**Execution Flow:**

```
User Input
    │
    ├─► Agent 1 ───┐
    ├─► Agent 2 ───┤
    ├─► Agent 3 ───┼─► Evaluate & Select Best ─► Winner Output
    ├─► Agent 4 ───┤
    └─► Agent 5 ───┘

    Time: max(agent_times) + evaluation_time
```

### Performance Characteristics

- **Latency:** `max(agent_times) + selection_time` - Parallel + evaluation
- **Throughput:** High - All agents work simultaneously
- **Memory:** `O(n)` - Stores all results for comparison
- **Failure Handling:** Selects from successful agents only

### When to Use

✅ **Perfect for:**
- Creative tasks (writing, design)
- Multiple solution approaches
- Quality-based selection
- Exploring alternatives
- Best-of-N sampling

❌ **Avoid when:**
- All perspectives needed
- Objective correctness required
- Can't waste agent work

### Real-World Example: Creative Writing

```kotlin
// Generate multiple versions, pick the best
val creativeSwarm = buildSwarmAgent {
    name = "Creative Writing Swarm"
    defaultStrategy(SwarmStrategyType.COMPETITION)

    quickSwarm {
        specialist("formal-writer", "Formal Writer", "professional tone")
        specialist("casual-writer", "Casual Writer", "friendly tone")
        specialist("technical-writer", "Technical Writer", "precise language")
        specialist("storyteller", "Storyteller", "narrative style")
    }
}

val result = creativeSwarm.processComm(Comm(
    content = "Write a product announcement for our new AI code assistant",
    from = "marketing"
))
```

### Competition Output Format

```
🏆 Competition Winner (Agent storyteller):

Introducing CodeMate: Your New Development Partner

Remember the last time you spent hours debugging a tricky issue,
only to discover it was a simple typo? Or when you rewrote the
same boilerplate code for the hundredth time?

CodeMate changes everything.

It's not just another code completion tool. It's an AI pair
programmer that understands your project's context, your coding
style, and your goals. It suggests solutions before you ask,
catches bugs before they happen, and writes tests while you focus
on features.

Built by developers, for developers. Powered by GPT-4.
Available today.

[Other 3 versions not shown but were evaluated]

Why this won:
- Engaging narrative hook (relatable pain point)
- Clear value proposition
- Emotional connection
- Strong call-to-action
- Length: 847 characters (most detailed)
```

### Custom Selection Criteria

You can customize how the "best" result is selected:

```kotlin
class CustomSwarmCoordinator : SwarmCoordinator {
    override suspend fun selectBestResult(
        results: SwarmResult,
        strategy: SwarmStrategy
    ): SwarmResult {
        // Custom criteria: keyword density, sentiment, or AI evaluation
        val best = results.agentResults
            .filter { it.success }
            .maxByOrNull { result ->
                // Your scoring logic
                scoreResult(result.content)
            }

        return SwarmResult(best?.content ?: "No winner", ...)
    }

    private fun scoreResult(content: String): Double {
        // Custom scoring: readability, keywords, length, etc.
        val readabilityScore = calculateReadability(content)
        val keywordScore = countRelevantKeywords(content)
        val lengthScore = content.length / 1000.0

        return readabilityScore * 0.5 + keywordScore * 0.3 + lengthScore * 0.2
    }
}
```

---

## 5. HIERARCHICAL Strategy

### How It Works

```kotlin
private suspend fun executeHierarchical(strategy: SwarmStrategy, comm: Comm): SwarmResult {
    val hierarchy = strategy.agentHierarchy ?: return executeParallel(strategy, comm)

    var currentResults = listOf(AgentResult("coordinator", true, comm.content))

    // Process each level sequentially
    for (level in hierarchy) {
        val levelResults = mutableListOf<AgentResult>()

        // Within each level, agents run in parallel
        for (agentId in level) {
            val agent = memberAgents[agentId] ?: continue

            // Pass context from previous level
            val contextComm = Comm(
                content = """
                    Context from previous level:
                    ${currentResults.joinToString { it.content }}

                    Task: ${comm.content}
                """,
                from = "swarm-hierarchy"
            )

            val result = agent.processComm(contextComm)
            // Collect results for this level...
        }

        currentResults = levelResults  // Feed to next level
    }

    return coordinator.aggregateResults(currentResults, strategy)
}
```

**Execution Flow:**

```
User Input
    │
    ▼
┌──────────────────┐
│  Level 1: Lead   │  High-level strategy
│   Agent 1        │
└──────────────────┘
    │ Output becomes context
    ▼
┌──────────────────────────────────────┐
│  Level 2: Specialists (Parallel)    │
│   Agent 2  │  Agent 3  │  Agent 4   │  Detailed execution
└──────────────────────────────────────┘
    │ All outputs become context
    ▼
┌──────────────────────────────────────┐
│  Level 3: Experts (Parallel)        │
│   Agent 5  │  Agent 6               │  Deep expertise
└──────────────────────────────────────┘
    │
    ▼
Final Result

Time: sum(level_max_times)
```

### Performance Characteristics

- **Latency:** `sum(max(level_times))` - Each level's slowest agent
- **Throughput:** Medium - Parallel within levels, sequential across levels
- **Memory:** `O(levels × agents_per_level)` - Stores level contexts
- **Failure Handling:** Level can continue with remaining agents

### When to Use

✅ **Perfect for:**
- Complex multi-stage workflows
- Tasks requiring different expertise levels
- Top-down delegation
- Refinement at each stage
- Coordinated specialization

❌ **Avoid when:**
- Simple tasks
- Flat organization works
- No clear hierarchy exists

### Real-World Example: Enterprise Architecture

```kotlin
// Complex system design with expertise levels
val architectureSwarm = buildSwarmAgent {
    name = "System Architecture Swarm"
    defaultStrategy(SwarmStrategyType.HIERARCHICAL)

    quickSwarm {
        // Level 1: Chief Architect (Strategy)
        specialist("chief-architect", "Chief Architect", "system architecture")

        // Level 2: Domain Architects (Specialists)
        specialist("backend-architect", "Backend Architect", "backend design")
        specialist("frontend-architect", "Frontend Architect", "frontend design")
        specialist("data-architect", "Data Architect", "data architecture")

        // Level 3: Technical Experts (Deep expertise)
        specialist("database-expert", "Database Expert", "database optimization")
        specialist("security-expert", "Security Expert", "security hardening")
        specialist("performance-expert", "Performance Expert", "scalability")
    }

    config {
        timeout(90000)  // Complex hierarchical tasks take time
    }
}

val result = architectureSwarm.processComm(Comm(
    content = "Design architecture for real-time analytics platform (1M events/sec)",
    from = "vp-engineering"
))
```

### Hierarchical Output Format

```
🎯 Hierarchical Swarm Result:

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Level 1: Strategic Direction
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Chief Architect:
- Event-driven architecture required
- Microservices for scalability
- Stream processing (Kafka/Flink)
- CQRS pattern for reads/writes
- Cloud-native deployment

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Level 2: Domain Designs (in context of Level 1)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Backend Architect:
- Kafka for event ingestion (1M+ events/sec)
- Flink for stream processing
- API Gateway: Kong
- Services: Event Processor, Analytics Engine, Query Service

Frontend Architect:
- Real-time dashboard: WebSocket updates
- React + Redux for state
- Chart.js for visualizations
- Progressive loading for large datasets

Data Architect:
- Hot storage: TimescaleDB (last 7 days)
- Warm storage: S3 + Athena (last 90 days)
- Cold storage: Glacier (archive)
- Data retention policies

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Level 3: Technical Specifications (in context of Levels 1-2)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Database Expert:
- TimescaleDB configuration:
  * Chunk time interval: 1 hour
  * Compression after 24 hours
  * Hypertable partitioning by timestamp
  * Indexes: (tenant_id, timestamp), (event_type)
- Expected storage: 500GB/month

Security Expert:
- mTLS for service-to-service
- JWT for API authentication
- Data encryption at rest (AES-256)
- VPC isolation per tenant
- DDoS protection via Cloudflare

Performance Expert:
- Auto-scaling: 5-50 pods per service
- Cache layer: Redis (90% hit rate target)
- CDN for dashboard assets
- Connection pooling: max 1000/pod
- Estimated latency: P50=50ms, P99=200ms

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Final Recommendation
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Complete architecture specified across 3 levels of expertise.
Estimated capacity: 1.2M events/sec with 99.9% uptime.
Implementation timeline: 16 weeks.
```

### Custom Hierarchy Definition

```kotlin
// Define custom hierarchy levels
val customHierarchy = listOf(
    listOf("strategic-planner"),                    // Level 1: Single leader
    listOf("architect-1", "architect-2"),           // Level 2: 2 architects
    listOf("dev-1", "dev-2", "dev-3", "dev-4")     // Level 3: 4 developers
)

val swarm = buildSwarmAgent {
    name = "Custom Hierarchy"
    defaultStrategy(SwarmStrategyType.HIERARCHICAL)
    // Custom hierarchy will be created by coordinator
}
```

---

## Decision Matrix

Choose the right strategy based on your needs:

| Requirement | PARALLEL | SEQUENTIAL | CONSENSUS | COMPETITION | HIERARCHICAL |
|-------------|----------|------------|-----------|-------------|--------------|
| **Multiple perspectives** | ✅ | ❌ | ✅ | ✅ | ✅ |
| **Fast execution** | ✅ | ❌ | ❌ | ✅ | ❌ |
| **Data pipeline** | ❌ | ✅ | ❌ | ❌ | ⚠️ |
| **Group decision** | ❌ | ❌ | ✅ | ❌ | ❌ |
| **Best quality** | ❌ | ❌ | ❌ | ✅ | ⚠️ |
| **Complex workflow** | ❌ | ⚠️ | ❌ | ❌ | ✅ |
| **Low latency** | ✅ | ❌ | ❌ | ⚠️ | ❌ |
| **Sequential deps** | ❌ | ✅ | ❌ | ❌ | ✅ |

Legend: ✅ Perfect | ⚠️ Possible | ❌ Not suitable

## Performance Comparison

### Latency

```
3 agents, each takes 1 second:

PARALLEL:     ███ (1s)
SEQUENTIAL:   █████████ (3s)
CONSENSUS:    ██████ (2s - two parallel rounds)
COMPETITION:  ███ (1s + selection)
HIERARCHICAL: ████ (1s per level)
```

### Cost Efficiency

```
Task with 5 agents:

PARALLEL:     💰💰💰💰💰 (all 5 agents used)
SEQUENTIAL:   💰💰💰💰💰 (all 5 agents used)
CONSENSUS:    💰💰💰💰💰💰💰💰💰💰 (all agents × 2 rounds)
COMPETITION:  💰💰💰💰💰 (all 5 agents, only 1 result used)
HIERARCHICAL: 💰💰💰💰💰 (all 5 agents used)
```

### Resource Utilization

```
Peak concurrent agents:

PARALLEL:     █████ (all 5 at once)
SEQUENTIAL:   █ (1 at a time)
CONSENSUS:    █████ + █████ (5 in each round)
COMPETITION:  █████ (all 5 at once)
HIERARCHICAL: ███ (max per level)
```

## Advanced Patterns

### Dynamic Strategy Selection

Let the coordinator choose the best strategy:

```kotlin
val adaptiveSwarm = buildSwarmAgent {
    name = "Adaptive Swarm"
    coordinator(CoordinatorType.SMART)  // Analyzes task and selects strategy

    quickSwarm {
        researchAgent("researcher")
        analysisAgent("analyst")
        specialist("expert", "Expert", "expert view")
    }
}

// Coordinator will analyze task and choose:
// - "compare" → CONSENSUS
// - "best" → COMPETITION
// - "step by step" → SEQUENTIAL
// - default → PARALLEL
```

### Hybrid Strategies

Combine strategies for complex workflows:

```kotlin
// Phase 1: PARALLEL for research
// Phase 2: SEQUENTIAL for processing
// Phase 3: CONSENSUS for decision

suspend fun complexWorkflow(input: Comm): Comm {
    // Research phase (parallel)
    val researchSwarm = buildSwarmAgent {
        defaultStrategy(SwarmStrategyType.PARALLEL)
        // ... researchers
    }
    val research = researchSwarm.processComm(input).getOrThrow()

    // Processing phase (sequential)
    val pipelineSwarm = buildSwarmAgent {
        defaultStrategy(SwarmStrategyType.SEQUENTIAL)
        // ... processors
    }
    val processed = pipelineSwarm.processComm(research).getOrThrow()

    // Decision phase (consensus)
    val decisionSwarm = buildSwarmAgent {
        defaultStrategy(SwarmStrategyType.CONSENSUS)
        // ... decision makers
    }
    return decisionSwarm.processComm(processed).getOrThrow()
}
```

### Conditional Strategies

Switch strategies based on results:

```kotlin
suspend fun adaptiveExecution(task: Comm): Comm {
    // Start with parallel
    val parallelResult = parallelSwarm.processComm(task).getOrThrow()

    // If no agreement, switch to consensus
    if (hasLowAgreement(parallelResult)) {
        return consensusSwarm.processComm(task).getOrThrow()
    }

    return parallelResult
}
```

## Best Practices

### 1. Match Strategy to Task

```kotlin
// ✅ Good - Strategy matches task type
val analysisSwarm = buildSwarmAgent {
    name = "Analysis Swarm"
    defaultStrategy(SwarmStrategyType.PARALLEL)  // Multiple perspectives
}

// ❌ Bad - Wrong strategy
val pipelineSwarm = buildSwarmAgent {
    name = "Pipeline Swarm"
    defaultStrategy(SwarmStrategyType.PARALLEL)  // Should be SEQUENTIAL!
}
```

### 2. Set Appropriate Timeouts

```kotlin
// ✅ Good - Timeout matches strategy
val consensusSwarm = buildSwarmAgent {
    defaultStrategy(SwarmStrategyType.CONSENSUS)
    config {
        timeout(60000)  // 60s - Two rounds need more time
    }
}

val parallelSwarm = buildSwarmAgent {
    defaultStrategy(SwarmStrategyType.PARALLEL)
    config {
        timeout(30000)  // 30s - Single round sufficient
    }
}
```

### 3. Handle Failures Gracefully

```kotlin
// ✅ Good - Failure handling
val robustSwarm = buildSwarmAgent {
    defaultStrategy(SwarmStrategyType.PARALLEL)
    config {
        retryAttempts(3)
        timeout(30000)
    }
}

// Even if 2 out of 5 agents fail, continue with 3
// Result will show 3/5 success rate
```

### 4. Monitor Performance

```kotlin
// Track swarm performance
val status = swarm.getSwarmStatus()
println("""
    Total agents: ${status.totalAgents}
    Ready agents: ${status.readyAgents}
    Active operations: ${status.activeOperations}
    Avg success rate: ${status.averageSuccessRate}
""")

val history = swarm.getOperationHistory()
history.forEach { result ->
    println("Operation: ${result.successRate} success, ${result.agentResults.size} agents")
}
```

## Next Steps

- [Swarm Intelligence Overview](./swarm) - Getting started with swarms
- [Multi-Agent Patterns](./multi-agent) - Agent coordination patterns
- [Observability](../observability/overview) - Monitor swarm performance
- [Error Handling](../error-handling/overview) - Handle swarm failures
