---
sidebar_position: 1
---

# Observability Overview

Production-grade observability for the Spice Framework using OpenTelemetry.

## What is Observability?

Observability gives you deep insights into your AI agent systems:

- **Traces**: See the complete journey of requests through your swarm
- **Metrics**: Track performance, costs, and usage patterns
- **Monitoring**: Real-time dashboards and alerting

## Why Observability?

### 1. Performance Optimization ⚡

Find bottlenecks instantly:
- Which agents are slow?
- Which LLM calls take longest?
- Where should you add caching?

### 2. Cost Management 💰

Track LLM spending:
- Monitor token usage per model
- Estimate costs in real-time
- Identify expensive operations

### 3. Error Tracking 🐛

Debug distributed systems:
- Trace errors across multiple agents
- See exact failure points
- Understand error patterns

### 4. Capacity Planning 📊

Plan for growth:
- Monitor agent load
- Track memory usage
- Predict scaling needs

## Quick Start

### 1. Add Dependencies

OpenTelemetry is included in `spice-core`:

```kotlin
dependencies {
    implementation("io.github.no-ai-labs:spice-core:0.2.1")
}
```

### 2. Initialize Observability

```kotlin
import io.github.noailabs.spice.observability.*

// At application startup
ObservabilityConfig.initialize(
    ObservabilityConfig.Config(
        serviceName = "my-ai-app",
        serviceVersion = "1.0.0",
        otlpEndpoint = "http://localhost:4317",
        enableTracing = true,
        enableMetrics = true
    )
)
```

### 3. Add Tracing to Agents

```kotlin
val agent = buildAgent {
    name = "Research Agent"
    handle { comm ->
        // Your agent logic
        SpiceResult.success(comm.reply("Result", id))
    }
}.traced()  // Add this line!
```

### 4. Run Jaeger

```bash
docker run -d --name jaeger \
  -e COLLECTOR_OTLP_ENABLED=true \
  -p 16686:16686 \
  -p 4317:4317 \
  jaegertracing/all-in-one:latest
```

### 5. View Traces

Open http://localhost:16686 and see your traces!

## What Gets Tracked?

### Agent Operations
- Request duration
- Success/failure rates
- Agent-to-agent communication
- Tool executions

### LLM Usage
- API calls per provider
- Token consumption
- Estimated costs
- Response times

### Swarm Coordination
- Strategy execution
- Agent participation
- Consensus building
- Result aggregation

### System Health
- Memory usage
- Active agents
- Error rates
- Throughput

## Example: Traced Swarm

```kotlin
val swarm = buildSwarmAgent {
    name = "Research Swarm"

    quickSwarm {
        // Each agent automatically traced
        val researcher = buildAgent { ... }.traced()
        val analyst = buildAgent { ... }.traced()
        val expert = buildAgent { ... }.traced()

        addAgent("researcher", researcher)
        addAgent("analyst", analyst)
        addAgent("expert", expert)
    }

    config {
        debug(true)
    }
}

// Execute and view complete trace
val result = swarm.processComm(comm)
```

## Next Steps

- [Setup Guide](./setup): Detailed configuration
- [Tracing](./tracing): Distributed tracing guide
- [Metrics](./metrics): Metrics collection
- [Visualization](./visualization): Dashboards and alerting
