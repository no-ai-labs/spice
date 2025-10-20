# Multi-Agent Systems

Build complex multi-agent applications.

## Agent Communication

```kotlin
// Agent 1
val agent1 = buildAgent {
    id = "agent-1"
    handle { comm ->
        // Send to agent-2
        callAgent("agent-2", comm.forward("agent-2"))
        comm.reply("Done", id)
    }
}

// Agent 2
val agent2 = buildAgent {
    id = "agent-2"
    handle { comm ->
        comm.reply("Received from ${comm.from}", id)
    }
}
```
