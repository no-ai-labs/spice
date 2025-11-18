package io.github.noailabs.spice.event

import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.reflect.KClass

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
    private val config: EventBusConfig = EventBusConfig.DEFAULT
) : ToolCallEventBus {

    // Reactive event stream using SharedFlow
    private val eventFlow = MutableSharedFlow<ToolCallEvent>(
        replay = 0,  // Don't replay old events to new subscribers
        extraBufferCapacity = 100  // Buffer up to 100 events
    )

    // Event history (if enabled)
    private val history = mutableListOf<ToolCallEvent>()
    private val mutex = Mutex()

    // Metrics
    private var publishCount = 0L
    private var subscriberCount = 0

    override suspend fun publish(event: ToolCallEvent): SpiceResult<Unit> {
        return SpiceResult.catching {
            // Emit event to all subscribers
            eventFlow.emit(event)

            // Store in history if enabled
            if (config.enableHistory) {
                mutex.withLock {
                    history.add(event)

                    // Trim history if exceeds max size
                    if (history.size > config.historySize) {
                        history.removeAt(0)  // Remove oldest
                    }
                }
            }

            // Update metrics
            if (config.enableMetrics) {
                mutex.withLock {
                    publishCount++
                }
            }

            Unit
        }
    }

    override fun subscribe(): Flow<ToolCallEvent> {
        // Increment subscriber count
        subscriberCount++

        return eventFlow
    }

    override fun subscribe(vararg eventTypes: KClass<out ToolCallEvent>): Flow<ToolCallEvent> {
        subscriberCount++

        return eventFlow.filter { event ->
            eventTypes.any { it.isInstance(event) }
        }
    }

    override fun subscribeToToolCall(toolCallId: String): Flow<ToolCallEvent> {
        subscriberCount++

        return eventFlow.filter { event ->
            event.toolCall.id == toolCallId
        }
    }

    override fun subscribeToRun(runId: String): Flow<ToolCallEvent> {
        subscriberCount++

        return eventFlow.filter { event ->
            when (event) {
                is ToolCallEvent.Emitted -> event.runId == runId
                else -> {
                    // For other events, check if the message has the runId
                    event.message.runId == runId
                }
            }
        }
    }

    override suspend fun getHistory(limit: Int): SpiceResult<List<ToolCallEvent>> {
        return mutex.withLock {
            if (!config.enableHistory) {
                return@withLock SpiceResult.success(emptyList())
            }

            val events = history.takeLast(limit).reversed()  // Most recent first
            SpiceResult.success(events)
        }
    }

    override suspend fun getToolCallHistory(toolCallId: String): SpiceResult<List<ToolCallEvent>> {
        return mutex.withLock {
            if (!config.enableHistory) {
                return@withLock SpiceResult.success(emptyList())
            }

            val events = history.filter { it.toolCall.id == toolCallId }
            SpiceResult.success(events)
        }
    }

    override suspend fun clearHistory(): SpiceResult<Unit> {
        return mutex.withLock {
            history.clear()
            SpiceResult.success(Unit)
        }
    }

    override suspend fun getSubscriberCount(): Int {
        return subscriberCount
    }

    /**
     * Get publish count (metrics)
     */
    suspend fun getPublishCount(): Long {
        return mutex.withLock {
            publishCount
        }
    }

    /**
     * Get event count in history
     */
    suspend fun getHistorySize(): Int {
        return mutex.withLock {
            history.size
        }
    }

    /**
     * Reset metrics
     */
    suspend fun resetMetrics() {
        mutex.withLock {
            publishCount = 0
            subscriberCount = 0
        }
    }
}
