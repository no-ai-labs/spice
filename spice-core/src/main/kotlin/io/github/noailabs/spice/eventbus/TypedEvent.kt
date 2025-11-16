package io.github.noailabs.spice.eventbus

import kotlinx.datetime.Instant

/**
 * 🎁 Typed Event Wrapper
 *
 * Wrapper for typed events received from a channel.
 * Contains both the typed event and its envelope with metadata.
 *
 * **Usage:**
 * ```kotlin
 * eventBus.subscribe(StandardChannels.TOOL_CALLS)
 *     .collect { typedEvent ->
 *         val event = typedEvent.event  // Type: ToolCallEvent
 *         val envelope = typedEvent.envelope  // EventEnvelope
 *         val metadata = envelope.metadata
 *
 *         when (event) {
 *             is ToolCallEvent.Emitted -> handle(event)
 *             is ToolCallEvent.Completed -> handle(event)
 *         }
 *     }
 * ```
 *
 * @property id Event ID (from envelope)
 * @property event Typed event
 * @property envelope Event envelope with metadata and versioning
 * @property receivedAt Timestamp when event was received by subscriber
 *
 * @since 1.0.0-alpha-5
 * @author Spice Framework
 */
data class TypedEvent<T>(
    val id: String,
    val event: T,
    val envelope: EventEnvelope,
    val receivedAt: Instant
) {
    /**
     * Event metadata (shortcut to envelope.metadata)
     */
    val metadata: EventMetadata
        get() = envelope.metadata

    /**
     * Event timestamp (shortcut to envelope.timestamp)
     */
    val timestamp: Instant
        get() = envelope.timestamp

    /**
     * Correlation ID (shortcut to envelope.correlationId)
     */
    val correlationId: String?
        get() = envelope.correlationId

    /**
     * Causation ID (shortcut to envelope.causationId)
     */
    val causationId: String?
        get() = envelope.causationId

    /**
     * Schema version (shortcut to envelope.schemaVersion)
     */
    val schemaVersion: String
        get() = envelope.schemaVersion

    /**
     * Check if event matches a predicate
     */
    fun matches(predicate: (T) -> Boolean): Boolean = predicate(event)

    override fun toString(): String =
        "TypedEvent(id='$id', event=${event!!.javaClass.simpleName}, timestamp=$timestamp)"
}
