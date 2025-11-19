package io.github.noailabs.spice.event

import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.reflect.KClass

/**
 * 🏗️ Abstract Tool Call Event Bus
 *
 * Base class providing common bookkeeping for all event bus implementations.
 * Centralizes history management, metrics tracking, sanitization, and subscription
 * filtering to eliminate code duplication across InMemory, Redis, and Kafka backends.
 *
 * **Shared Functionality:**
 * - Event sanitization (metadata filtering)
 * - History storage and trimming
 * - Metrics tracking (publish count, subscriber count)
 * - Subscription filtering by event type, tool call ID, or run ID
 *
 * **Subclass Responsibilities:**
 * - Implement `doPublish()` for backend-specific publishing
 * - Call `processPublishedEvent()` after successful backend publish
 * - Use `emitToSubscribers()` when events are received from backend
 *
 * @property config Event bus configuration
 *
 * @since 1.0.0
 */
abstract class AbstractToolCallEventBus(
    protected val config: EventBusConfig = EventBusConfig.DEFAULT
) : ToolCallEventBus {

    // Reactive event stream using SharedFlow
    protected val eventFlow = MutableSharedFlow<ToolCallEvent>(
        replay = 0,
        extraBufferCapacity = 100
    )

    // Event history (if enabled)
    private val history = mutableListOf<ToolCallEvent>()
    protected val mutex = Mutex()

    // Metrics
    private var publishCount = 0L
    protected var subscriberCount = 0

    /**
     * Publish event to the bus.
     *
     * This method handles sanitization and delegates to subclass for backend-specific publishing.
     */
    override suspend fun publish(event: ToolCallEvent): SpiceResult<Unit> {
        return SpiceResult.catching {
            // Sanitize event metadata if filters are configured
            val sanitizedEvent = ToolCallEventSanitizer.sanitize(event, config.metadataFilter)

            // Delegate to subclass for backend-specific publishing
            doPublish(sanitizedEvent)

            // Process common bookkeeping after successful publish
            processPublishedEvent(sanitizedEvent)

            Unit
        }
    }

    /**
     * Backend-specific publish implementation.
     *
     * Subclasses must implement this to publish the event to their specific backend
     * (in-memory flow, Redis stream, Kafka topic, etc.).
     *
     * @param event The sanitized event to publish
     */
    protected abstract suspend fun doPublish(event: ToolCallEvent)

    /**
     * Process published event for history and metrics.
     *
     * Call this after successfully publishing to the backend.
     * Handles history storage and metrics tracking.
     *
     * @param event The published event
     */
    protected suspend fun processPublishedEvent(event: ToolCallEvent) {
        // Store in history if enabled
        if (config.enableHistory) {
            mutex.withLock {
                history.add(event)
                if (history.size > config.historySize) {
                    history.removeAt(0)
                }
            }
        }

        // Update metrics
        if (config.enableMetrics) {
            mutex.withLock {
                publishCount++
            }
        }
    }

    /**
     * Emit event to local subscribers.
     *
     * Subclasses should call this when events are received from the backend
     * (e.g., from Redis polling or Kafka consumer).
     *
     * @param event The event to emit
     */
    protected suspend fun emitToSubscribers(event: ToolCallEvent) {
        eventFlow.emit(event)
    }

    override fun subscribe(): Flow<ToolCallEvent> {
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
                else -> event.message.runId == runId
            }
        }
    }

    override suspend fun getHistory(limit: Int): SpiceResult<List<ToolCallEvent>> {
        return mutex.withLock {
            if (!config.enableHistory) {
                return@withLock SpiceResult.success(emptyList())
            }
            val events = history.takeLast(limit).reversed()
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
     * Get publish count (metrics).
     */
    suspend fun getPublishCount(): Long {
        return mutex.withLock {
            publishCount
        }
    }

    /**
     * Get event count in history.
     */
    suspend fun getHistorySize(): Int {
        return mutex.withLock {
            history.size
        }
    }

    /**
     * Reset metrics.
     */
    suspend fun resetMetrics() {
        mutex.withLock {
            publishCount = 0
            subscriberCount = 0
        }
    }
}
