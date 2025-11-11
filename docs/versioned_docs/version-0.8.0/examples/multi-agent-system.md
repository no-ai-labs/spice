# Multi-Agent System

Build collaborative agent systems.

```kotlin
val researcher = buildAgent { /* ... */ }
val analyzer = buildAgent { /* ... */ }
val writer = buildAgent { /* ... */ }

val swarm = buildSwarmAgent {
    addAgent(researcher)
    addAgent(analyzer)
    addAgent(writer)
}
```
