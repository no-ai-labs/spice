package io.github.noailabs.spice.eventbus

import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * 📻 Typed Event Channel
 *
 * Strongly-typed event stream with configuration.
 * Channels are identified by name and restricted to a specific event type and schema version.
 *
 * **Schema Enforcement:**
 * Each channel is tied to a specific event type and schema version.
 * The schema MUST be registered in SchemaRegistry before the channel can be used.
 * This prevents schema mismatches and incompatible payloads at the code level.
 *
 * **Usage:**
 * ```kotlin
 * // 1. Register schema first
 * registry.register(MyEvent::class, "1.0.0", MyEvent.serializer())
 *
 * // 2. Create channel (version required!)
 * val channel = EventChannel(
 *     name = "my.events",
 *     type = MyEvent::class,
 *     version = "1.0.0",
 *     config = ChannelConfig(enableHistory = true)
 * )
 * ```
 *
 * @property name Channel name (unique identifier)
 * @property type Event type class (for type safety and serialization)
 * @property version Schema version (must be registered in SchemaRegistry)
 * @property config Channel configuration
 *
 * @since 1.0.0-alpha-5
 * @author Spice Framework
 */
data class EventChannel<T : Any>(
    val name: String,
    val type: KClass<T>,
    val version: String,
    val config: ChannelConfig = ChannelConfig.DEFAULT
) {
    init {
        require(name.isNotBlank()) { "Channel name must not be blank" }
        require(version.isNotBlank()) { "Version must not be blank" }
    }

    /**
     * Get schema key for this channel (TypeName:Version)
     */
    val schemaKey: String
        get() = "${type.qualifiedName}:$version"

    override fun toString(): String = "EventChannel(name='$name', type=${type.simpleName}, version='$version')"
}

/**
 * 🔧 Channel Configuration
 *
 * Configuration for event channels controlling history, dead letter queue, and TTL.
 *
 * **Configuration Options:**
 * - `enableHistory`: Store recent events for replay (useful for new subscribers)
 * - `historySize`: Maximum number of events to retain in history
 * - `enableDeadLetter`: Send malformed/failed events to dead letter queue
 * - `deadLetterTopic`: Custom DLQ topic (null = default DLQ)
 * - `retryPolicy`: Retry policy for failed event processing
 * - `ttl`: Time-to-live for events (after this duration, events expire)
 *
 * **Example:**
 * ```kotlin
 * val config = ChannelConfig(
 *     enableHistory = true,
 *     historySize = 10000,
 *     enableDeadLetter = true,
 *     ttl = 7.days
 * )
 * ```
 *
 * @property enableHistory Enable event history storage
 * @property historySize Maximum number of events to retain
 * @property enableDeadLetter Enable dead letter queue
 * @property deadLetterTopic Custom DLQ topic (null = default)
 * @property retryPolicy Retry policy for failed events
 * @property ttl Time-to-live for events
 *
 * @since 1.0.0-alpha-5
 * @author Spice Framework
 */
data class ChannelConfig(
    val enableHistory: Boolean = false,
    val historySize: Int = 1000,
    val enableDeadLetter: Boolean = true,
    val deadLetterTopic: String? = null,
    val retryPolicy: RetryPolicy = RetryPolicy.NONE,
    val ttl: Duration? = null
) {
    init {
        require(historySize > 0) { "History size must be positive" }
    }

    companion object {
        /**
         * Default configuration (no history, DLQ enabled, no retry)
         */
        val DEFAULT = ChannelConfig()

        /**
         * Configuration for high-volume transient events (no history, no DLQ)
         */
        val TRANSIENT = ChannelConfig(
            enableHistory = false,
            enableDeadLetter = false
        )

        /**
         * Configuration for important events with history
         */
        val PERSISTENT = ChannelConfig(
            enableHistory = true,
            historySize = 10000,
            enableDeadLetter = true,
            ttl = 30.days
        )
    }
}

/**
 * 🔄 Retry Policy
 *
 * Defines retry behavior for failed event processing.
 *
 * @since 1.0.0-alpha-5
 * @author Spice Framework
 */
sealed class RetryPolicy {
    /**
     * No retry (default)
     */
    data object NONE : RetryPolicy()

    /**
     * Fixed retry interval
     *
     * @property maxAttempts Maximum retry attempts
     * @property interval Fixed interval between retries
     */
    data class Fixed(
        val maxAttempts: Int,
        val interval: Duration
    ) : RetryPolicy() {
        init {
            require(maxAttempts > 0) { "Max attempts must be positive" }
        }
    }

    /**
     * Exponential backoff
     *
     * @property maxAttempts Maximum retry attempts
     * @property initialInterval Initial retry interval
     * @property multiplier Backoff multiplier (default: 2.0)
     * @property maxInterval Maximum retry interval
     */
    data class Exponential(
        val maxAttempts: Int,
        val initialInterval: Duration,
        val multiplier: Double = 2.0,
        val maxInterval: Duration
    ) : RetryPolicy() {
        init {
            require(maxAttempts > 0) { "Max attempts must be positive" }
            require(multiplier > 1.0) { "Multiplier must be greater than 1.0" }
        }
    }
}
