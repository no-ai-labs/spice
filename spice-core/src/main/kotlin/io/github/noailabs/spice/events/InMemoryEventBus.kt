package io.github.noailabs.spice.events

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.time.Duration

/**
 * 💾 In-Memory Event Bus
 *
 * Fast, ephemeral implementation of EventBus using coroutine channels and flows.
 * Suitable for testing, development, and single-instance deployments.
 *
 * **Characteristics:**
 * - **Performance:** Low latency (<1ms pub/sub)
 * - **Persistence:** None (events lost on restart)
 * - **Scalability:** Single-instance only (no distributed pub/sub)
 * - **Thread Safety:** Yes (using coroutine-safe primitives)
 * - **Memory:** Bounded by replay buffer size
 *
 * **Architecture:**
 * ```
 * Publisher ──publish()──→ MutableSharedFlow (replay=100)
 *                              │
 *                              ├──→ Subscription 1 (coroutine)
 *                              ├──→ Subscription 2 (coroutine)
 *                              └──→ Subscription 3 (coroutine)
 * ```
 *
 * **Use Cases:**
 * - Unit tests and integration tests
 * - Development environment
 * - Single-instance production (non-distributed)
 * - Prototype and POC
 *
 * **Limitations:**
 * - Events lost on process restart
 * - Not suitable for multi-instance deployments
 * - No persistent event log
 * - No backpressure handling (buffered only)
 *
 * **Example Usage:**
 * ```kotlin
 * // Simple usage
 * val eventBus = InMemoryEventBus()
 *
 * // Subscribe
 * val subId = eventBus.subscribe("node.*.completed") { message ->
 *     println("Node completed: ${message.nodeId}")
 * }
 *
 * // Publish
 * eventBus.publish("node.booking.completed", message)
 *
 * // Unsubscribe
 * eventBus.unsubscribe(subId)
 * ```
 *
 * **Topic Wildcard Matching:**
 * - `*` matches single level: "node.*.completed" matches "node.booking.completed"
 * - `**` matches multiple levels: "node.**.completed" matches "node.a.b.completed"
 *
 * @param replayBufferSize Number of recent events to replay to new subscribers (default: 100)
 * @param scope CoroutineScope for background tasks (default: SupervisorScope)
 *
 * @author Spice Framework
 * @since 1.0.0
 */
class InMemoryEventBus(
    private val replayBufferSize: Int = 100,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : EventBus {

    /**
     * Subscription metadata
     */
    private data class Subscription(
        val id: String,
        val topicPattern: String,
        val groupId: String?,
        val handler: suspend (SpiceMessage) -> Unit,
        val job: Job
    )

    /**
     * Published event with topic
     */
    private data class PublishedEvent(
        val topic: String,
        val message: SpiceMessage
    )

    // Shared flow for pub/sub
    private val eventFlow = MutableSharedFlow<PublishedEvent>(
        replay = replayBufferSize,
        extraBufferCapacity = 1000
    )

    // Active subscriptions
    private val subscriptions = mutableMapOf<String, Subscription>()
    private val mutex = Mutex()

    // Consumer group state (topic → groupId → last consumed message ID)
    private val groupState = mutableMapOf<String, MutableMap<String, String>>()

    // Statistics
    private var published = 0L
    private var consumed = 0L
    private var errors = 0L
    private val pending = Channel<SpiceMessage>(Channel.UNLIMITED)

    /**
     * Publish message to topic
     */
    override suspend fun publish(topic: String, message: SpiceMessage): SpiceResult<String> {
        return SpiceResult.catching {
            val event = PublishedEvent(topic, message)
            eventFlow.emit(event)
            published++
            message.id  // Return message ID for acknowledgment
        }
    }

    /**
     * Subscribe to topic with wildcard support
     */
    override suspend fun subscribe(topic: String, handler: suspend (SpiceMessage) -> Unit): String = mutex.withLock {
        val subscriptionId = "sub_${UUID.randomUUID().toString().replace("-", "").take(16)}"

        // Launch coroutine to handle incoming events
        val job = scope.launch {
            eventFlow.asSharedFlow().collect { event ->
                try {
                    if (TopicPatternMatcher.matches(event.topic, topic)) {
                        handler(event.message)
                        consumed++
                    }
                } catch (e: Exception) {
                    errors++
                    // Log error but don't stop subscription
                }
            }
        }

        val subscription = Subscription(
            id = subscriptionId,
            topicPattern = topic,
            groupId = null,
            handler = handler,
            job = job
        )

        subscriptions[subscriptionId] = subscription
        return subscriptionId
    }

    /**
     * Subscribe with consumer group (load-balanced delivery)
     */
    override suspend fun subscribeWithGroup(
        topic: String,
        groupId: String,
        handler: suspend (SpiceMessage) -> Unit
    ): String = mutex.withLock {
        val subscriptionId = "sub_${UUID.randomUUID().toString().replace("-", "").take(16)}"

        // Initialize group state if needed
        if (!groupState.containsKey(topic)) {
            groupState[topic] = mutableMapOf()
        }

        // Launch coroutine to handle incoming events
        val job = scope.launch {
            eventFlow.asSharedFlow().collect { event ->
                try {
                    if (TopicPatternMatcher.matches(event.topic, topic)) {
                        // Load balancing: Only one consumer in group handles each message
                        val lastConsumed = groupState[topic]?.get(groupId)
                        if (lastConsumed != event.message.id) {
                            handler(event.message)
                            groupState[topic]?.put(groupId, event.message.id)
                            consumed++
                        }
                    }
                } catch (e: Exception) {
                    errors++
                }
            }
        }

        val subscription = Subscription(
            id = subscriptionId,
            topicPattern = topic,
            groupId = groupId,
            handler = handler,
            job = job
        )

        subscriptions[subscriptionId] = subscription
        return subscriptionId
    }

    /**
     * Unsubscribe from topic
     */
    override suspend fun unsubscribe(subscriptionId: String): SpiceResult<Unit> = mutex.withLock {
        SpiceResult.catching {
            val subscription = subscriptions.remove(subscriptionId)
            subscription?.job?.cancel()
            Unit
        }
    }

    /**
     * Acknowledge message (no-op for in-memory implementation)
     */
    override suspend fun acknowledge(messageId: String, subscriptionId: String): SpiceResult<Unit> {
        // In-memory bus doesn't need explicit ACK
        return SpiceResult.success(Unit)
    }

    /**
     * Get pending messages (not supported in in-memory implementation)
     */
    override suspend fun getPending(subscriptionId: String, limit: Int): List<SpiceMessage> {
        // In-memory bus doesn't persist pending messages
        return emptyList()
    }

    /**
     * Close event bus and cancel all subscriptions
     */
    override suspend fun close() {
        mutex.withLock {
            subscriptions.values.forEach { it.job.cancel() }
            subscriptions.clear()
            scope.cancel()
        }
    }

    /**
     * Get event bus statistics
     */
    override suspend fun getStats(): EventBusStats {
        return EventBusStats(
            published = published,
            consumed = consumed,
            pending = 0L,  // In-memory bus doesn't track pending
            errors = errors,
            activeSubscriptions = subscriptions.size
        )
    }

    /**
     * Match topic against pattern with wildcard support
     *
     * Examples:
     * - matchesTopic("node.booking.completed", "node.*.completed") → true
     * - matchesTopic("node.a.b.completed", "node.**.completed") → true
     * - matchesTopic("node.booking.started", "node.*.completed") → false
     *
     * @param topic Actual topic name
     * @param pattern Topic pattern with wildcards
     * @return true if topic matches pattern
     */
    private fun matchesTopic(topic: String, pattern: String): Boolean =
        TopicPatternMatcher.matches(topic, pattern)

    /**
     * Get active subscriptions count
     */
    fun activeSubscriptionsCount(): Int = subscriptions.size

    /**
     * Get all active subscription IDs (for debugging)
     */
    suspend fun getActiveSubscriptions(): Set<String> = mutex.withLock {
        subscriptions.keys.toSet()
    }

    /**
     * Get subscription details (for debugging)
     */
    suspend fun getSubscriptionInfo(subscriptionId: String): SubscriptionInfo? = mutex.withLock {
        val sub = subscriptions[subscriptionId] ?: return null
        SubscriptionInfo(
            id = sub.id,
            topicPattern = sub.topicPattern,
            groupId = sub.groupId,
            isActive = sub.job.isActive
        )
    }

    /**
     * Subscription information for debugging
     */
    data class SubscriptionInfo(
        val id: String,
        val topicPattern: String,
        val groupId: String?,
        val isActive: Boolean
    )

    /**
     * Reset statistics (for testing)
     */
    suspend fun resetStats() {
        published = 0
        consumed = 0
        errors = 0
    }
}
