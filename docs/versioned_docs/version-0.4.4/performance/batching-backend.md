# Batching Backend

> 📝 **Coming Soon**: Guide on batching LLM requests for optimal throughput.

## Preview

Batching combines multiple requests to reduce overhead:

```kotlin
// Coming in v0.5.0
val batchedBackend = OpenAIBackend().batched(
    maxBatchSize = 10,
    maxWaitTime = 100.milliseconds
)
```

## See Also

- [Performance Overview](./overview.md)
- [Tool Caching](./tool-caching.md)
