package io.github.noailabs.spice.eventbus

/**
 * 📊 Event Bus Statistics
 *
 * Metrics for monitoring event bus performance and health.
 * Provides aggregated metrics across all channels.
 *
 * **Key Metrics:**
 * - **Publish Rate**: Messages published per second
 * - **Consume Rate**: Messages consumed per second
 * - **Lag**: Messages waiting to be consumed (in history)
 * - **Error Rate**: Failed publish/consume operations
 * - **Active Channels**: Number of active channels
 * - **Active Subscribers**: Number of active subscribers
 *
 * **Example:**
 * ```kotlin
 * val stats = eventBus.getStats()
 * println("Channels: ${stats.activeChannels}")
 * println("Published: ${stats.published}")
 * println("Consumed: ${stats.consumed}")
 * println("Errors: ${stats.errors}")
 * println("Error rate: ${stats.errorRate()}%")
 * println("Healthy: ${stats.isHealthy()}")
 * ```
 *
 * @property published Total messages published across all channels
 * @property consumed Total messages consumed across all channels
 * @property pending Messages waiting in history (not yet consumed)
 * @property errors Total errors (publish + consume failures)
 * @property activeChannels Number of active channels
 * @property activeSubscribers Number of active subscribers
 * @property deadLetterMessages Messages in dead letter queue
 *
 * @since 1.0.0-alpha-5
 * @author Spice Framework
 */
data class EventBusStats(
    val published: Long,
    val consumed: Long,
    val pending: Long,
    val errors: Long,
    val activeChannels: Int,
    val activeSubscribers: Int,
    val deadLetterMessages: Long = 0
) {
    /**
     * Calculate throughput (messages processed per second)
     * Note: This is cumulative, not per-second rate
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
     * Criteria:
     * - Error rate < 5%
     * - Pending < 1000 (per channel average)
     * - Dead letter queue < 100
     */
    fun isHealthy(): Boolean {
        val errorRateOk = errorRate() < 5.0
        val pendingOk = pending < (activeChannels * 1000)
        val dlqOk = deadLetterMessages < 100

        return errorRateOk && pendingOk && dlqOk
    }

    /**
     * Get average pending messages per channel
     */
    fun avgPendingPerChannel(): Double {
        return if (activeChannels > 0) pending.toDouble() / activeChannels else 0.0
    }

    /**
     * Get average subscribers per channel
     */
    fun avgSubscribersPerChannel(): Double {
        return if (activeChannels > 0) activeSubscribers.toDouble() / activeChannels else 0.0
    }

    /**
     * Pretty print statistics
     */
    override fun toString(): String = buildString {
        appendLine("EventBus Statistics:")
        appendLine("  Published: $published")
        appendLine("  Consumed: $consumed")
        appendLine("  Pending: $pending")
        appendLine("  Errors: $errors (${String.format("%.2f", errorRate())}%)")
        appendLine("  Active Channels: $activeChannels")
        appendLine("  Active Subscribers: $activeSubscribers")
        appendLine("  Dead Letter Messages: $deadLetterMessages")
        appendLine("  Healthy: ${isHealthy()}")
    }

    companion object {
        /**
         * Empty statistics (initial state)
         */
        val EMPTY = EventBusStats(
            published = 0,
            consumed = 0,
            pending = 0,
            errors = 0,
            activeChannels = 0,
            activeSubscribers = 0,
            deadLetterMessages = 0
        )
    }
}
