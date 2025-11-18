package io.github.noailabs.spice.eventbus.dlq

import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.eventbus.EventEnvelope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import java.util.UUID

/**
 * 💀 In-Memory Dead Letter Queue
 *
 * Simple in-memory implementation of DeadLetterQueue for testing and development.
 * Not suitable for production (data lost on restart).
 *
 * **Features:**
 * - Thread-safe using Mutex
 * - **Per-channel partitioning**: Prevents one busy channel from evicting others
 * - **FIFO eviction with metrics**: Track eviction count and expose hooks
 * - Fast lookups by message ID
 *
 * **Back-pressure Controls:**
 * - Per-channel max size (prevents cross-channel eviction)
 * - Global max size (total limit across all channels)
 * - Eviction hooks (onEvict callback for monitoring)
 * - Eviction metrics (total evicted count)
 *
 * **Limitations:**
 * - Data lost on restart
 * - Not distributed (single instance only)
 * - Limited by heap memory
 *
 * **Usage:**
 * ```kotlin
 * val dlq = InMemoryDeadLetterQueue(
 *     maxSize = 10000,
 *     maxSizePerChannel = 1000,
 *     onEvict = { msg -> logger.warn("DLQ evicted: ${msg.id}") }
 * )
 * dlq.send(envelope, "Deserialization failed")
 * ```
 *
 * @property maxSize Maximum total messages across all channels
 * @property maxSizePerChannel Maximum messages per channel (prevents single channel from dominating)
 * @property onEvict Callback invoked when message is evicted (for monitoring/alerting)
 *
 * @since 1.0.0-alpha-5
 * @author Spice Framework
 */
class InMemoryDeadLetterQueue(
    private val maxSize: Int = 10000,
    private val maxSizePerChannel: Int = 1000,
    private val onEvict: ((DeadLetterMessage) -> Unit)? = null
) : DeadLetterQueue {

    // Per-channel storage (prevents cross-channel eviction)
    private val messagesByChannel = mutableMapOf<String, MutableList<DeadLetterMessage>>()
    private val messageIndex = mutableMapOf<String, DeadLetterMessage>()
    private val mutex = Mutex()

    // Metrics
    private var totalEvicted: Long = 0

    init {
        require(maxSize > 0) { "Max size must be positive" }
        require(maxSizePerChannel > 0) { "Max size per channel must be positive" }
        require(maxSizePerChannel <= maxSize) { "Max size per channel must not exceed total max size" }
    }

    override suspend fun send(
        originalEnvelope: EventEnvelope,
        reason: String,
        error: Throwable?
    ): SpiceResult<String> = mutex.withLock {
        val messageId = UUID.randomUUID().toString()
        val channelName = originalEnvelope.channelName

        val dlqMessage = DeadLetterMessage(
            id = messageId,
            originalEnvelope = originalEnvelope,
            reason = reason,
            error = error?.message,
            stackTrace = error?.stackTraceToString(),
            receivedAt = Clock.System.now(),
            retryCount = 0
        )

        // Get or create channel list
        val channelMessages = messagesByChannel.getOrPut(channelName) { mutableListOf() }

        // Add message
        channelMessages.add(dlqMessage)
        messageIndex[messageId] = dlqMessage

        // Per-channel FIFO eviction
        while (channelMessages.size > maxSizePerChannel) {
            val oldest = channelMessages.removeAt(0)
            messageIndex.remove(oldest.id)
            totalEvicted++
            onEvict?.invoke(oldest)
        }

        // Global FIFO eviction (across all channels)
        val totalSize = messagesByChannel.values.sumOf { it.size }
        while (totalSize > maxSize) {
            // Find oldest message across all channels
            val oldestEntry = messagesByChannel
                .flatMap { (_, messages) -> messages }
                .minByOrNull { it.receivedAt }

            if (oldestEntry != null) {
                val channelList = messagesByChannel[oldestEntry.channelName]
                channelList?.remove(oldestEntry)
                messageIndex.remove(oldestEntry.id)
                totalEvicted++
                onEvict?.invoke(oldestEntry)
            } else {
                break
            }
        }

        SpiceResult.success(messageId)
    }

    override suspend fun getMessages(
        limit: Int,
        offset: Int
    ): SpiceResult<List<DeadLetterMessage>> = mutex.withLock {
        // Flatten all channel messages and sort by receivedAt descending (newest first)
        val allMessages = messagesByChannel.values.flatten()
        val sorted = allMessages.sortedByDescending { it.receivedAt }

        val result = sorted
            .drop(offset)
            .take(limit)

        SpiceResult.success(result)
    }

    override suspend fun getMessage(
        messageId: String
    ): SpiceResult<DeadLetterMessage?> = mutex.withLock {
        SpiceResult.success(messageIndex[messageId])
    }

    override suspend fun retry(messageId: String): SpiceResult<Unit> = mutex.withLock {
        val message = messageIndex[messageId]
            ?: return@withLock SpiceResult.failure(
                SpiceError.validationError("DLQ message not found: $messageId")
            )

        // Update retry count
        val updated = message.copy(
            retryCount = message.retryCount + 1,
            lastRetryAt = Clock.System.now()
        )

        // Replace in channel list
        val channelMessages = messagesByChannel[message.channelName]
        if (channelMessages != null) {
            val index = channelMessages.indexOfFirst { it.id == messageId }
            if (index >= 0) {
                channelMessages[index] = updated
                messageIndex[messageId] = updated
            }
        }

        // Note: Actual retry (republish to event bus) should be done by caller
        SpiceResult.success(Unit)
    }

    override suspend fun delete(messageId: String): SpiceResult<Unit> = mutex.withLock {
        val message = messageIndex.remove(messageId)
            ?: return@withLock SpiceResult.failure(
                SpiceError.validationError("DLQ message not found: $messageId")
            )

        // Remove from channel list
        val channelMessages = messagesByChannel[message.channelName]
        channelMessages?.removeIf { it.id == messageId }

        SpiceResult.success(Unit)
    }

    override suspend fun getStats(): DeadLetterStats = mutex.withLock {
        val allMessages = messagesByChannel.values.flatten()

        val byChannel = allMessages.groupingBy { it.channelName }.eachCount().mapValues { it.value.toLong() }
        val byReason = allMessages.groupingBy { it.reason }.eachCount().mapValues { it.value.toLong() }
        val oldest = allMessages.minByOrNull { it.receivedAt }?.receivedAt
        val newest = allMessages.maxByOrNull { it.receivedAt }?.receivedAt

        DeadLetterStats(
            totalMessages = allMessages.size.toLong(),
            byChannel = byChannel,
            byReason = byReason,
            oldestMessage = oldest,
            newestMessage = newest,
            totalEvicted = totalEvicted
        )
    }

    override suspend fun clear(): SpiceResult<Int> = mutex.withLock {
        val count = messagesByChannel.values.sumOf { it.size }
        messagesByChannel.clear()
        messageIndex.clear()
        SpiceResult.success(count)
    }

    /**
     * Get current size (for testing)
     */
    suspend fun size(): Int = mutex.withLock {
        messagesByChannel.values.sumOf { it.size }
    }

    /**
     * Get total eviction count (for monitoring)
     */
    suspend fun getEvictionCount(): Long = mutex.withLock {
        totalEvicted
    }

    /**
     * Get messages by channel (for debugging)
     */
    suspend fun getMessagesByChannel(channelName: String): List<DeadLetterMessage> = mutex.withLock {
        messagesByChannel[channelName]?.toList() ?: emptyList()
    }
}
