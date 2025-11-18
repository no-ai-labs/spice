package io.github.noailabs.spice.eventbus.dlq

import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.eventbus.EventEnvelope
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * 💀 Dead Letter Queue (DLQ)
 *
 * Stores events that failed to process due to:
 * - Malformed payload (deserialization errors)
 * - Schema version mismatches
 * - Handler exceptions
 * - Validation failures
 *
 * **Purpose:**
 * - Prevent event loss
 * - Enable debugging and investigation
 * - Support manual retry/replay
 * - Track failure patterns
 *
 * **Operations:**
 * - Send failed events to DLQ
 * - List DLQ messages for inspection
 * - Retry individual messages
 * - Delete processed messages
 *
 * **Usage:**
 * ```kotlin
 * val dlq = InMemoryDeadLetterQueue()
 *
 * // Send to DLQ
 * dlq.send(
 *     originalEnvelope = envelope,
 *     reason = "Deserialization failed: Unknown field 'newField'",
 *     error = exception
 * )
 *
 * // Inspect DLQ
 * val messages = dlq.getMessages(limit = 100)
 * messages.forEach { msg ->
 *     println("Failed: ${msg.reason}")
 * }
 *
 * // Retry message
 * dlq.retry(messageId)
 *
 * // Delete message
 * dlq.delete(messageId)
 * ```
 *
 * @since 1.0.0-alpha-5
 * @author Spice Framework
 */
interface DeadLetterQueue {
    /**
     * Send event to dead letter queue
     *
     * @param originalEnvelope Original event envelope
     * @param reason Failure reason (human-readable)
     * @param error Optional exception that caused failure
     * @return SpiceResult with DLQ message ID
     */
    suspend fun send(
        originalEnvelope: EventEnvelope,
        reason: String,
        error: Throwable? = null
    ): SpiceResult<String>

    /**
     * Get dead letter messages
     *
     * @param limit Maximum number of messages to retrieve
     * @param offset Offset for pagination
     * @return List of dead letter messages (sorted by receivedAt desc)
     */
    suspend fun getMessages(
        limit: Int = 100,
        offset: Int = 0
    ): SpiceResult<List<DeadLetterMessage>>

    /**
     * Get dead letter message by ID
     *
     * @param messageId DLQ message ID
     * @return Dead letter message or null if not found
     */
    suspend fun getMessage(messageId: String): SpiceResult<DeadLetterMessage?>

    /**
     * Retry dead letter message
     *
     * Republishes the original event to the event bus.
     * Increments retry count.
     *
     * @param messageId DLQ message ID
     * @return SpiceResult indicating success or failure
     */
    suspend fun retry(messageId: String): SpiceResult<Unit>

    /**
     * Delete dead letter message
     *
     * Permanently removes message from DLQ.
     *
     * @param messageId DLQ message ID
     * @return SpiceResult indicating success or failure
     */
    suspend fun delete(messageId: String): SpiceResult<Unit>

    /**
     * Get DLQ statistics
     *
     * @return DLQ stats (total messages, by channel, by reason)
     */
    suspend fun getStats(): DeadLetterStats

    /**
     * Clear all DLQ messages
     *
     * ⚠️ WARNING: This permanently deletes all DLQ messages!
     *
     * @return Number of messages deleted
     */
    suspend fun clear(): SpiceResult<Int>
}

/**
 * 💀 Dead Letter Message
 *
 * Wrapper for events in dead letter queue.
 *
 * @property id Unique DLQ message ID
 * @property originalEnvelope Original event envelope
 * @property reason Failure reason
 * @property error Error message (from exception)
 * @property stackTrace Stack trace (if exception available)
 * @property receivedAt Timestamp when added to DLQ
 * @property retryCount Number of retry attempts
 * @property lastRetryAt Timestamp of last retry
 *
 * @since 1.0.0-alpha-5
 * @author Spice Framework
 */
@Serializable
data class DeadLetterMessage(
    val id: String,
    val originalEnvelope: EventEnvelope,
    val reason: String,
    val error: String? = null,
    val stackTrace: String? = null,
    @Serializable(with = io.github.noailabs.spice.eventbus.InstantSerializer::class)
    val receivedAt: Instant = Clock.System.now(),
    val retryCount: Int = 0,
    @Serializable(with = io.github.noailabs.spice.eventbus.InstantSerializer::class)
    val lastRetryAt: Instant? = null
) {
    /**
     * Channel name from original envelope
     */
    val channelName: String
        get() = originalEnvelope.channelName

    /**
     * Event type from original envelope
     */
    val eventType: String
        get() = originalEnvelope.eventType

    /**
     * Check if message has been retried
     */
    val hasBeenRetried: Boolean
        get() = retryCount > 0

    override fun toString(): String =
        "DeadLetterMessage(id='$id', channel='$channelName', reason='$reason', retryCount=$retryCount)"
}

/**
 * 📊 Dead Letter Queue Statistics
 *
 * Aggregated statistics for DLQ monitoring.
 *
 * @property totalMessages Total messages in DLQ
 * @property byChannel Messages grouped by channel
 * @property byReason Messages grouped by failure reason
 * @property oldestMessage Timestamp of oldest message
 * @property newestMessage Timestamp of newest message
 *
 * @since 1.0.0-alpha-5
 * @author Spice Framework
 */
data class DeadLetterStats(
    val totalMessages: Long,
    val byChannel: Map<String, Long>,
    val byReason: Map<String, Long>,
    val oldestMessage: Instant? = null,
    val newestMessage: Instant? = null,
    val totalEvicted: Long = 0
) {
    /**
     * Check if DLQ is healthy (not too many messages)
     */
    fun isHealthy(maxMessages: Long = 100): Boolean {
        return totalMessages < maxMessages
    }

    override fun toString(): String = buildString {
        appendLine("Dead Letter Queue Statistics:")
        appendLine("  Total Messages: $totalMessages")
        appendLine("  By Channel:")
        byChannel.forEach { (channel, count) ->
            appendLine("    - $channel: $count")
        }
        appendLine("  By Reason:")
        byReason.forEach { (reason, count) ->
            appendLine("    - $reason: $count")
        }
        if (oldestMessage != null) {
            appendLine("  Oldest Message: $oldestMessage")
        }
        if (newestMessage != null) {
            appendLine("  Newest Message: $newestMessage")
        }
    }

    companion object {
        val EMPTY = DeadLetterStats(
            totalMessages = 0,
            byChannel = emptyMap(),
            byReason = emptyMap(),
            totalEvicted = 0
        )
    }
}
