# Swarm Intelligence

Multi-agent coordination with 5 strategies.

## Swarm Strategies

1. **PARALLEL** - Execute agents simultaneously
2. **SEQUENTIAL** - Chain agents in sequence
3. **CONSENSUS** - Build consensus through discussion
4. **COMPETITION** - Select best result
5. **HIERARCHICAL** - Hierarchical delegation

## Creating a Swarm

```kotlin
val swarm = buildSwarmAgent {
    id = "swarm-1"
    name = "Analysis Swarm"

    // Add member agents
    addAgent(researcher)
    addAgent(analyzer)
    addAgent(synthesizer)

    // Smart coordinator
    coordinator = SmartSwarmCoordinator(memberAgents)
}
```
