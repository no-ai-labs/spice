# Swarm Intelligence

Multi-agent coordination with 5 execution strategies and AI-powered coordination.

## Overview

Swarm Intelligence enables multiple specialized agents to work together as a collective, making decisions through coordination and emergent behaviors.

## Swarm Strategies

1. **PARALLEL** - Execute all agents simultaneously (best for independent analyses)
2. **SEQUENTIAL** - Execute agents in sequence, passing results forward (best for pipelines)
3. **CONSENSUS** - Build consensus through multi-round discussion (best for decision-making)
4. **COMPETITION** - Select best result from competing agents (best for creative tasks)
5. **HIERARCHICAL** - Hierarchical delegation with levels (best for complex tasks)

## Creating a Swarm

### Basic Swarm

```kotlin
val swarm = buildSwarmAgent {
    name = "Research Swarm"
    description = "Multi-agent research and analysis"

    // Add member agents
    quickSwarm {
        researchAgent("researcher", "Lead Researcher")
        analysisAgent("analyst", "Data Analyst")
        specialist("expert", "Domain Expert", "Expert analysis")
    }

    // Configure behavior
    config {
        debug(true)
        timeout(45000)
        maxOperations(5)
    }

    // Set execution strategy
    defaultStrategy(SwarmStrategyType.PARALLEL)
}

// Execute
val result = swarm.processComm(Comm(
    content = "Analyze the impact of AI on healthcare",
    from = "user",
    type = CommType.TEXT
))
```

### AI-Powered Coordinator

Use an LLM to make intelligent coordination decisions:

```kotlin
// Create LLM coordinator agent
val llmCoordinator = buildAgent {
    name = "GPT-4 Coordinator"
    description = "Meta-coordination agent"
    // Configure your GPT-4 or Claude agent here
}

// Create AI-powered swarm
val aiSwarm = buildSwarmAgent {
    name = "AI Research Swarm"

    // Use AI for intelligent coordination
    aiCoordinator(llmCoordinator)

    quickSwarm {
        researchAgent("researcher")
        analysisAgent("analyst")
        specialist("expert", "Expert", "analysis")
    }

    config {
        debug(true)
        timeout(60000)
    }
}
```

The AI coordinator will:
- Analyze tasks and select optimal strategies
- Intelligently aggregate agent results
- Build sophisticated consensus
- Select best results with reasoning

## Quick Swarm Creation

Pre-configured swarm templates:

```kotlin
// Research swarm
val research = researchSwarm(
    name = "Research Team",
    debugEnabled = true
)

// Creative swarm
val creative = creativeSwarm(
    name = "Creative Team"
)

// Decision-making swarm
val decision = decisionSwarm(
    name = "Decision Team"
)

// AI powerhouse (with real API keys)
val powerhouse = aiPowerhouseSwarm(
    name = "AI Powerhouse",
    claudeApiKey = System.getenv("CLAUDE_API_KEY"),
    gptApiKey = System.getenv("OPENAI_API_KEY")
)
```

## Strategy Examples

### Parallel Execution

```kotlin
val parallelSwarm = buildSwarmAgent {
    name = "Parallel Analysis Swarm"
    defaultStrategy(SwarmStrategyType.PARALLEL)

    quickSwarm {
        analysisAgent("agent1")
        analysisAgent("agent2")
        analysisAgent("agent3")
    }
}

// All agents execute simultaneously
val result = parallelSwarm.processComm(comm)
```

### Sequential Pipeline

```kotlin
val pipelineSwarm = buildSwarmAgent {
    name = "Processing Pipeline"
    defaultStrategy(SwarmStrategyType.SEQUENTIAL)

    quickSwarm {
        specialist("extractor", "Extractor", "Extract data")
        specialist("cleaner", "Cleaner", "Clean data")
        specialist("analyzer", "Analyzer", "Analyze data")
    }
}

// Results pass through each agent
val result = pipelineSwarm.processComm(comm)
```

### Consensus Building

```kotlin
val consensusSwarm = buildSwarmAgent {
    name = "Consensus Swarm"
    defaultStrategy(SwarmStrategyType.CONSENSUS)

    quickSwarm {
        researchAgent("researcher1")
        researchAgent("researcher2")
        specialist("expert", "Expert", "expert view")
    }

    config {
        timeout(60000)  // Consensus takes time
    }
}

// Multi-round discussion to reach agreement
val result = consensusSwarm.processComm(comm)
```

## Coordinator Types

### Smart Coordinator (Default)

Intelligent coordination with task classification:

```kotlin
buildSwarmAgent {
    coordinator(CoordinatorType.SMART)  // Default
}
```

### Simple Coordinator

Lightweight coordination:

```kotlin
buildSwarmAgent {
    coordinator(CoordinatorType.SIMPLE)
}
```

### AI-Powered Coordinator

LLM-enhanced coordination:

```kotlin
buildSwarmAgent {
    coordinator(CoordinatorType.AI_POWERED)
    llmCoordinator(myLLMAgent)
}
```

## Observability

Track swarm performance:

```kotlin
val swarm = buildSwarmAgent {
    name = "Observable Swarm"

    quickSwarm {
        val agent1 = buildAgent { ... }.traced()
        val agent2 = buildAgent { ... }.traced()

        addAgent("agent1", agent1)
        addAgent("agent2", agent2)
    }

    config {
        debug(true)
    }
}

// View traces in Jaeger
// Track metrics in Grafana
```

## Best Practices

1. **Choose the right strategy** for your task
2. **Use AI coordinator** for complex decision-making
3. **Enable tracing** for production debugging
4. **Set appropriate timeouts** for your use case
5. **Monitor costs** when using multiple LLM agents
6. **Start with PARALLEL** for independent tasks
7. **Use SEQUENTIAL** for pipelines
8. **Use CONSENSUS** for decisions
9. **Use COMPETITION** for creativity

## Next Steps

- [Multi-Agent Patterns](./multi-agent)
- [Flow Orchestration](./flows)
- [Observability](../observability/overview)
