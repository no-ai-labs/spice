package io.github.noailabs.spice.event

/**
 * 💾 In-Memory Tool Call Event Bus
 *
 * Fast, ephemeral event bus for development and testing.
 * Uses Kotlin Flow for reactive event streaming.
 *
 * **Characteristics:**
 * - Performance: O(1) publish, O(n) subscribe
 * - Persistence: None (events lost on restart if history disabled)
 * - Scalability: Single-instance only
 * - Thread Safety: Yes (Mutex + SharedFlow)
 *
 * **Use Cases:**
 * - Development environment
 * - Unit tests and integration tests
 * - Demo and prototype
 * - Single-instance deployments
 *
 * **NOT for production distributed systems** - Use Kafka/Redis Pub/Sub instead
 *
 * @property config Event bus configuration
 *
 * @since 2.0.0
 */
class InMemoryToolCallEventBus(
    config: EventBusConfig = EventBusConfig.DEFAULT
) : AbstractToolCallEventBus(config) {

    /**
     * Emit event directly to local subscribers.
     *
     * In-memory implementation simply emits to the shared flow.
     */
    override suspend fun doPublish(event: ToolCallEvent) {
        emitToSubscribers(event)
    }
}
