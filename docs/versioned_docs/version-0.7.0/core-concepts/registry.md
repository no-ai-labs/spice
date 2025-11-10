# Registry

Thread-safe, type-safe component management in Spice Framework.

## Overview

Spice provides generic registry system for managing:
- **Agents** - AgentRegistry
- **Tools** - ToolRegistry
- **Flows** - FlowRegistry
- **Tool Chains** - ToolChainRegistry

## Generic Registry

```kotlin
open class Registry<T : Identifiable>(val name: String) {
    fun register(item: T): T
    fun get(id: String): T?
    fun getAll(): List<T>
    fun has(id: String): Boolean
    fun unregister(id: String): Boolean
    fun clear()
    fun size(): Int
}
```

## Agent Registry

```kotlin
// Register agent
AgentRegistry.register(myAgent)

// Get agent
val agent = AgentRegistry.get("agent-id")

// Find by capability
val agents = AgentRegistry.findByCapability("text-processing")

// Find by tag
val tagged = AgentRegistry.findByTag("production")

// Find by provider
val openaiAgents = AgentRegistry.findByProvider("openai")
```

## Tool Registry

### Basic Operations

```kotlin
// Register tool
ToolRegistry.register(tool, namespace = "global")

// Get tool
val tool = ToolRegistry.getTool("tool-name", namespace = "global")

// Check existence
if (ToolRegistry.hasTool("tool-name")) {
    // Tool exists
}
```

### Namespace Support

```kotlin
// Register in namespace
ToolRegistry.register(mathTool, namespace = "math")
ToolRegistry.register(textTool, namespace = "text")

// Get from namespace
val mathTools = ToolRegistry.getByNamespace("math")
```

### Search Capabilities

```kotlin
// By tag
val tools = ToolRegistry.getByTag("calculator")

// By source
val agentTools = ToolRegistry.getBySource("agent-tool")

// Get all AgentTool metadata
val agentToolsWithMeta = ToolRegistry.getAgentTools()
```

## Flow Registry

```kotlin
// Register flow
FlowRegistry.register(myFlow)

// Get flow
val flow = FlowRegistry.get("flow-id")

// Get all flows
val allFlows = FlowRegistry.getAll()
```

## Searchable Registry

Extend for custom search:

```kotlin
abstract class SearchableRegistry<T : Identifiable>(name: String) : Registry<T>(name) {
    fun findBy(predicate: (T) -> Boolean): List<T>
    fun findFirstBy(predicate: (T) -> Boolean): T?
}
```

### Custom Search

```kotlin
// Find agents by custom criteria
val agents = AgentRegistry.findBy { agent ->
    agent.capabilities.contains("analysis") &&
    agent.name.startsWith("AI")
}

// Find first matching
val agent = AgentRegistry.findFirstBy { it.name == "Special Agent" }
```

## Thread Safety

All registries are thread-safe using ConcurrentHashMap:

```kotlin
// Safe concurrent access
coroutineScope {
    launch { AgentRegistry.register(agent1) }
    launch { AgentRegistry.register(agent2) }
    launch { AgentRegistry.register(agent3) }
}
```

## Best Practices

### 1. Use Namespaces

```kotlin
// Organize tools by namespace
ToolRegistry.register(tool, namespace = "api")
ToolRegistry.register(tool, namespace = "internal")
```

### 2. Clean Up

```kotlin
// Unregister when done
AgentRegistry.unregister("temporary-agent")

// Clear all (testing)
AgentRegistry.clear()
```

### 3. Check Before Use

```kotlin
// Always check existence
if (AgentRegistry.has("agent-id")) {
    val agent = AgentRegistry.get("agent-id")!!
    // Use agent
}
```

## Next Steps

- [Agent Management](./agent)
- [Tool Development](./tool)
- [Flow Orchestration](../orchestration/flows)
