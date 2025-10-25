# Cached Agent

> 📝 **Coming Soon**: Detailed guide on agent-level caching strategies.

For now, see [Tool Caching](./tool-caching.md) for tool-level caching.

## Quick Preview

Agent-level caching caches entire agent processing results:

```kotlin
val agent = buildAgent {
    id = "my-agent"
    // ... agent configuration
}

val cachedAgent = agent.cached(ttl = 300)
```

## See Also

- [Tool Caching](./tool-caching.md) - Tool-level caching (available now)
- [Performance Overview](./overview.md) - Performance optimization strategies
