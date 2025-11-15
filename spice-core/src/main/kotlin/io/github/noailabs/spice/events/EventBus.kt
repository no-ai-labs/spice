package io.github.noailabs.spice.events

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult
import kotlin.time.Duration

/**
 * 📡 Event Bus for Spice Framework 1.0.0
 *
 * Provides pub/sub messaging for event-driven workflows and async orchestration.
 * Enables decoupled communication between graph nodes, agents, and external systems.
 *
 * **Architecture Pattern:**
 * ```
 * Node A ──publish("topic.a")──→ EventBus
 *                                    │
 *                                    ├──→ Node B (subscriber)
 *                                    ├──→ Node C (subscriber)
 *                                    └──→ External System (subscriber)
 * ```
 *
 * **Use Cases:**
 * 1. **Async Workflows:** Trigger next step without blocking current execution
 * 2. **Fan-out:** Broadcast message to multiple consumers
 * 3. **Event Sourcing:** Record all state changes as events
 * 4. **HITL Integration:** Notify external UI when human input required
 * 5. **Monitoring:** Stream execution events to observability tools
 *
 * **Topic Naming Convention:**
 * - Node events: `node.{graphId}.{nodeId}.{event}` (e.g., "node.booking.selection.completed")
 * - Graph events: `graph.{graphId}.{event}` (e.g., "graph.booking.started")
 * - System events: `system.{component}.{event}` (e.g., "system.arbiter.ready")
 * - Custom events: `custom.{domain}.{event}` (e.g., "custom.reservation.cancelled")
 *
 * **Message Semantics:**
 * - **At-most-once:** Fire and forget (fast, no guarantees)
 * - **At-least-once:** Retry until acknowledged (reliable, possible duplicates)
 * - **Exactly-once:** Idempotent delivery (reliable, no duplicates)
 *
 * **Implementations:**
 * - `InMemoryEventBus`: Fast, ephemeral (testing, dev) - ✅ Stable
 * - `RedisStreamsEventBus`: Distributed, persistent (Beta - production ready with caveats)
 * - `KafkaEventBus`: High-throughput, persistent (Beta - production ready with caveats)
 *
 * **⚠️ Production Notes (Beta Backends):**
 * - **RedisStreamsEventBus**: Requires Redis 5.0+, consumer group acknowledgment may have edge cases
 * - **KafkaEventBus**: Requires proper Kafka cluster setup, idempotent producer enabled by default
 * - Both backends are tested and functional but may have edge cases in high-throughput scenarios
 * - Consider thorough testing in staging before production deployment
 *
 * **Example Usage:**
 * ```kotlin
 * // Publisher (in AgentNode)
 * override suspend fun run(message: SpiceMessage): SpiceResult<SpiceMessage> {
 *     val result = agent.processMessage(message)
 *
 *     // Publish node completion event
 *     eventBus?.publish("node.${graphId}.${nodeId}.completed", result)
 *
 *     return SpiceResult.success(result)
 * }
 *
 * // Subscriber (external system)
 * eventBus.subscribe("node.booking.*.completed") { message ->
 *     logger.info { "Node completed: ${message.nodeId}" }
 *     sendMetrics(message)
 * }
 * ```
 *
 * **Thread Safety:**
 * All implementations must be thread-safe for concurrent pub/sub operations.
 *
 * @author Spice Framework
 * @since 1.0.0
 */
interface EventBus {
    /**
     * Publish message to topic
     * Non-blocking operation (fire-and-forget by default)
     *
     * @param topic Topic name (supports wildcards in subscription)
     * @param message Message to publish
     * @return SpiceResult with message ID for acknowledgment (required for at-least-once semantics)
     */
    suspend fun publish(topic: String, message: SpiceMessage): SpiceResult<String>

    /**
     * Subscribe to topic with message handler
     * Supports wildcard patterns (*, **)
     *
     * Examples:
     * - "node.booking.selection.completed" (exact match)
     * - "node.booking.*.completed" (single-level wildcard)
     * - "node.**.completed" (multi-level wildcard)
     *
     * @param topic Topic pattern
     * @param handler Coroutine handler for received messages
     * @return Subscription ID for later unsubscribe
     */
    suspend fun subscribe(topic: String, handler: suspend (SpiceMessage) -> Unit): String

    /**
     * Unsubscribe from topic
     *
     * @param subscriptionId Subscription ID returned by subscribe()
     * @return SpiceResult indicating success or failure
     */
    suspend fun unsubscribe(subscriptionId: String): SpiceResult<Unit>

    /**
     * Subscribe to topic with consumer group
     * Messages are load-balanced across group members (at-most-once delivery per group)
     *
     * @param topic Topic pattern
     * @param groupId Consumer group identifier
     * @param handler Coroutine handler for received messages
     * @return Subscription ID for later unsubscribe
     */
    suspend fun subscribeWithGroup(
        topic: String,
        groupId: String,
        handler: suspend (SpiceMessage) -> Unit
    ): String

    /**
     * Acknowledge message delivery
     * Required for at-least-once semantics with consumer groups
     *
     * @param messageId Message ID to acknowledge
     * @param subscriptionId Subscription ID
     * @return SpiceResult indicating success or failure
     */
    suspend fun acknowledge(messageId: String, subscriptionId: String): SpiceResult<Unit>

    /**
     * Get pending messages for subscription
     * Useful for manual polling mode
     *
     * @param subscriptionId Subscription ID
     * @param limit Maximum number of messages to retrieve
     * @return List of pending messages
     */
    suspend fun getPending(subscriptionId: String, limit: Int = 100): List<SpiceMessage>

    /**
     * Close event bus and release resources
     * Should be called during application shutdown
     */
    suspend fun close()

    /**
     * Get event bus statistics
     *
     * @return EventBusStats with publish/consume metrics
     */
    suspend fun getStats(): EventBusStats
}

/**
 * 📊 Event Bus Statistics
 *
 * Metrics for monitoring event bus performance and health.
 *
 * **Key Metrics:**
 * - **Publish Rate:** Messages published per second
 * - **Consume Rate:** Messages consumed per second
 * - **Lag:** Messages waiting to be consumed
 * - **Error Rate:** Failed publish/consume operations
 *
 * **Example:**
 * ```kotlin
 * val stats = eventBus.getStats()
 * println("Publish rate: ${stats.publishRate()}/sec")
 * println("Pending messages: ${stats.pending}")
 * println("Error rate: ${stats.errorRate()}%")
 * ```
 *
 * @property published Total messages published
 * @property consumed Total messages consumed
 * @property pending Messages waiting to be consumed
 * @property errors Total errors (publish + consume failures)
 * @property activeSubscriptions Number of active subscriptions
 *
 * @author Spice Framework
 * @since 1.0.0
 */
data class EventBusStats(
    val published: Long,
    val consumed: Long,
    val pending: Long,
    val errors: Long,
    val activeSubscriptions: Int
) {
    /**
     * Calculate throughput (messages processed per second)
     * Requires time window for rate calculation
     */
    fun throughput(): Long {
        return consumed
    }

    /**
     * Calculate error rate percentage
     */
    fun errorRate(): Double {
        val total = published + consumed
        return if (total > 0) (errors.toDouble() / total) * 100 else 0.0
    }

    /**
     * Check if event bus is healthy
     * Criteria: Error rate < 5%, Pending < 1000
     */
    fun isHealthy(): Boolean {
        return errorRate() < 5.0 && pending < 1000
    }
}

/**
 * 🎯 Event Topics (Standard Conventions)
 *
 * Predefined topic patterns for common Spice Framework events.
 * Using these conventions enables better tooling and monitoring.
 *
 * **Usage:**
 * ```kotlin
 * // Publish
 * eventBus.publish(EventTopics.nodeStarted(graphId, nodeId), message)
 *
 * // Subscribe
 * eventBus.subscribe(EventTopics.allNodeEvents(graphId)) { message ->
 *     logger.info { "Node event: ${message.nodeId}" }
 * }
 * ```
 *
 * @author Spice Framework
 * @since 1.0.0
 */
object EventTopics {
    // Node lifecycle events
    fun nodeStarted(graphId: String, nodeId: String) = "node.$graphId.$nodeId.started"
    fun nodeCompleted(graphId: String, nodeId: String) = "node.$graphId.$nodeId.completed"
    fun nodeFailed(graphId: String, nodeId: String) = "node.$graphId.$nodeId.failed"
    fun nodeWaiting(graphId: String, nodeId: String) = "node.$graphId.$nodeId.waiting"

    // Node event patterns (for subscription)
    fun allNodeEvents(graphId: String) = "node.$graphId.*.*"
    fun nodeEvents(graphId: String, nodeId: String) = "node.$graphId.$nodeId.*"

    // Graph lifecycle events
    fun graphStarted(graphId: String) = "graph.$graphId.started"
    fun graphCompleted(graphId: String) = "graph.$graphId.completed"
    fun graphFailed(graphId: String) = "graph.$graphId.failed"
    fun graphPaused(graphId: String) = "graph.$graphId.paused"

    // Graph event patterns
    fun allGraphEvents(graphId: String) = "graph.$graphId.*"
    fun allGraphs() = "graph.*.*"

    // HITL events
    fun hitlRequested(graphId: String, nodeId: String) = "hitl.$graphId.$nodeId.requested"
    fun hitlResolved(graphId: String, nodeId: String) = "hitl.$graphId.$nodeId.resolved"
    fun allHitlEvents() = "hitl.*.*.*"

    // System events
    fun systemReady(component: String) = "system.$component.ready"
    fun systemError(component: String) = "system.$component.error"
    fun systemShutdown(component: String) = "system.$component.shutdown"

    // Tool events
    fun toolInvoked(toolName: String) = "tool.$toolName.invoked"
    fun toolCompleted(toolName: String) = "tool.$toolName.completed"
    fun toolFailed(toolName: String) = "tool.$toolName.failed"
    fun allToolEvents() = "tool.*.*"

    // Custom domain events
    fun custom(domain: String, event: String) = "custom.$domain.$event"
}

/**
 * 🔌 Event Bus Builder DSL
 *
 * Fluent API for configuring event bus with topic subscriptions.
 *
 * **Example:**
 * ```kotlin
 * val eventBus = eventBus {
 *     implementation = RedisStreamEventBus(redis)
 *
 *     subscribe(EventTopics.allNodeEvents("booking")) { message ->
 *         logger.info { "Booking node event: ${message.nodeId}" }
 *     }
 *
 *     subscribeWithGroup(EventTopics.allHitlEvents(), "hitl-handlers") { message ->
 *         handleHitlRequest(message)
 *     }
 * }
 * ```
 *
 * @author Spice Framework
 * @since 1.0.0
 */
class EventBusBuilder {
    private var implementation: EventBus? = null
    private val subscriptions = mutableListOf<Pair<String, suspend (SpiceMessage) -> Unit>>()
    private val groupSubscriptions = mutableListOf<Triple<String, String, suspend (SpiceMessage) -> Unit>>()

    fun implementation(bus: EventBus) {
        implementation = bus
    }

    fun subscribe(topic: String, handler: suspend (SpiceMessage) -> Unit) {
        subscriptions.add(topic to handler)
    }

    fun subscribeWithGroup(topic: String, groupId: String, handler: suspend (SpiceMessage) -> Unit) {
        groupSubscriptions.add(Triple(topic, groupId, handler))
    }

    suspend fun build(): EventBus {
        val bus = implementation ?: throw IllegalStateException("Event bus implementation not set")

        // Register subscriptions
        subscriptions.forEach { (topic, handler) ->
            bus.subscribe(topic, handler)
        }

        groupSubscriptions.forEach { (topic, groupId, handler) ->
            bus.subscribeWithGroup(topic, groupId, handler)
        }

        return bus
    }
}

/**
 * DSL function for building event bus
 */
suspend fun eventBus(block: EventBusBuilder.() -> Unit): EventBus {
    return EventBusBuilder().apply(block).build()
}
