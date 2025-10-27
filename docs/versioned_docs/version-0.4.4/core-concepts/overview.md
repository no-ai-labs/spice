# Core Concepts

Understanding the fundamental building blocks of Spice Framework.

## Architecture

Spice Framework is built on four core concepts:

```
Agent → Comm → Tool → Registry
```

### 1. Agent

An autonomous unit that processes messages and performs actions. Agents can:
- Handle incoming communications
- Execute tools
- Maintain state
- Interact with other agents

[Learn more about Agents →](./agent)

### 2. Comm (Communication)

The unified message format for all agent interactions. Replaces legacy Message systems with:
- Type-safe enums for message types
- Rich metadata support
- Media attachments
- Priority and TTL

[Learn more about Comm →](./comm)

### 3. Tool

Reusable functions that agents can execute. Tools provide:
- Parameter validation
- Type checking
- Async execution
- Error handling

[Learn more about Tools →](./tool)

### 4. Registry

Thread-safe, type-safe management of components:
- AgentRegistry - Manage agents
- ToolRegistry - Organize tools
- FlowRegistry - Coordinate workflows

[Learn more about Registry →](./registry)

## How They Work Together

```kotlin
// 1. Create an agent
val agent = buildAgent {
    id = "processor"

    // 2. Add tools
    tool("process") { /* ... */ }

    // 3. Handle communications
    handle { comm ->
        // 4. Execute tools
        val result = run("process", params)
        comm.reply(result.result, id)
    }
}

// 5. Register in registry
AgentRegistry.register(agent)
```

## Next Steps

Dive deeper into each concept:

- [Agent Architecture](./agent)
- [Communication System](./comm)
- [Tool Development](./tool)
- [Registry System](./registry)
