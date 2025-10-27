# Metrics

> 📝 **Coming Soon**: Guide to collecting and exposing metrics.

## Preview

Track agent performance:

```kotlin
metrics.counter("agent.comm.processed").increment()
metrics.histogram("agent.latency").record(duration)
```

## See Also

- [Observability Overview](./overview.md)
- [Tool Caching](../performance/tool-caching.md) - Cache metrics
