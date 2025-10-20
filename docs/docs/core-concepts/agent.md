# Agent

Agents are the core building blocks of Spice Framework.

## What is an Agent?

An Agent is an autonomous unit that:
- Processes incoming communications
- Executes tools and actions
- Maintains internal state
- Interacts with other agents

## Agent Interface

```kotlin
interface Agent : Identifiable {
    val id: String
    val name: String
    val description: String
    val capabilities: List<String>

    suspend fun processComm(comm: Comm): Comm
    fun canHandle(comm: Comm): Boolean
    fun getTools(): List<Tool>
    fun isReady(): Boolean
}
```

## Creating Agents

### Using DSL

```kotlin
val agent = buildAgent {
    id = "my-agent"
    name = "My Agent"
    description = "Does amazing things"

    handle { comm ->
        // Process communication
        comm.reply("Processed!", id)
    }
}
```

### With Tools

```kotlin
val agent = buildAgent {
    id = "tool-agent"
    name = "Tool Agent"

    // Add inline tool
    tool("analyze") {
        description = "Analyze data"
        parameter("data", "string", "Data to analyze")
        execute { params ->
            ToolResult.success("Analysis: ${params["data"]}")
        }
    }

    handle { comm ->
        val result = run("analyze", mapOf("data" to comm.content))
        comm.reply(result.result, id)
    }
}
```

## BaseAgent

Extend `BaseAgent` for custom agents:

```kotlin
class CustomAgent(
    id: String,
    name: String
) : BaseAgent(id, name, "Custom agent") {

    override suspend fun processComm(comm: Comm): Comm {
        // Custom processing logic
        return comm.reply("Custom response", id)
    }
}
```

## Agent Lifecycle

1. **Initialization**: `initialize(runtime: AgentRuntime)`
2. **Processing**: `processComm(comm: Comm)`
3. **Cleanup**: `cleanup()`

## Agent Metrics

Track agent performance:

```kotlin
val metrics = agent.getMetrics()
println("Total requests: ${metrics.totalRequests}")
println("Success rate: ${metrics.getSuccessRate()}")
println("Avg response time: ${metrics.getAverageResponseTimeMs()}ms")
```

## Next Steps

- [Communication System](./comm)
- [Adding Tools](./tool)
- [Agent Registration](./registry)
