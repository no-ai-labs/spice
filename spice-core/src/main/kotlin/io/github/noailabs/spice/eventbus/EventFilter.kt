package io.github.noailabs.spice.eventbus

/**
 * 🔍 Event Filter
 *
 * Filters events in typed channel subscriptions.
 * Allows filtering by event type, metadata, or custom predicates.
 *
 * **Usage:**
 * ```kotlin
 * // Filter by predicate
 * val filter = EventFilter.by<ToolCallEvent> {
 *     it is ToolCallEvent.Completed
 * }
 *
 * // Filter by metadata field
 * val userFilter = EventFilter.byMetadata<GraphLifecycleEvent>(
 *     "userId" to "user123"
 * )
 *
 * // Combine filters
 * val combined = filter1.and(filter2)
 *
 * // Subscribe with filter
 * eventBus.subscribe(StandardChannels.TOOL_CALLS, filter)
 *     .collect { event -> ... }
 * ```
 *
 * @since 1.0.0-alpha-5
 * @author Spice Framework
 */
sealed class EventFilter<T> {
    /**
     * Apply filter to typed event
     */
    abstract fun matches(typedEvent: TypedEvent<T>): Boolean

    /**
     * No filter (accept all events)
     */
    class All<T> : EventFilter<T>() {
        override fun matches(typedEvent: TypedEvent<T>): Boolean = true
        override fun toString(): String = "EventFilter.All"
    }

    /**
     * Filter by predicate on event
     */
    data class Predicate<T>(
        val predicate: (T) -> Boolean
    ) : EventFilter<T>() {
        override fun matches(typedEvent: TypedEvent<T>): Boolean =
            predicate(typedEvent.event)

        override fun toString(): String = "EventFilter.Predicate"
    }

    /**
     * Filter by metadata field
     */
    data class Metadata<T>(
        val key: String,
        val value: String
    ) : EventFilter<T>() {
        override fun matches(typedEvent: TypedEvent<T>): Boolean =
            typedEvent.metadata.custom[key] == value

        override fun toString(): String = "EventFilter.Metadata($key=$value)"
    }

    /**
     * Filter by user ID
     */
    data class UserId<T>(
        val userId: String
    ) : EventFilter<T>() {
        override fun matches(typedEvent: TypedEvent<T>): Boolean =
            typedEvent.metadata.userId == userId

        override fun toString(): String = "EventFilter.UserId($userId)"
    }

    /**
     * Filter by tenant ID
     */
    data class TenantId<T>(
        val tenantId: String
    ) : EventFilter<T>() {
        override fun matches(typedEvent: TypedEvent<T>): Boolean =
            typedEvent.metadata.tenantId == tenantId

        override fun toString(): String = "EventFilter.TenantId($tenantId)"
    }

    /**
     * Filter by correlation ID
     */
    data class CorrelationId<T>(
        val correlationId: String
    ) : EventFilter<T>() {
        override fun matches(typedEvent: TypedEvent<T>): Boolean =
            typedEvent.correlationId == correlationId

        override fun toString(): String = "EventFilter.CorrelationId($correlationId)"
    }

    /**
     * AND combinator (both filters must match)
     */
    data class And<T>(
        val left: EventFilter<T>,
        val right: EventFilter<T>
    ) : EventFilter<T>() {
        override fun matches(typedEvent: TypedEvent<T>): Boolean =
            left.matches(typedEvent) && right.matches(typedEvent)

        override fun toString(): String = "EventFilter.And($left, $right)"
    }

    /**
     * OR combinator (either filter must match)
     */
    data class Or<T>(
        val left: EventFilter<T>,
        val right: EventFilter<T>
    ) : EventFilter<T>() {
        override fun matches(typedEvent: TypedEvent<T>): Boolean =
            left.matches(typedEvent) || right.matches(typedEvent)

        override fun toString(): String = "EventFilter.Or($left, $right)"
    }

    /**
     * NOT combinator (filter must not match)
     */
    data class Not<T>(
        val filter: EventFilter<T>
    ) : EventFilter<T>() {
        override fun matches(typedEvent: TypedEvent<T>): Boolean =
            !filter.matches(typedEvent)

        override fun toString(): String = "EventFilter.Not($filter)"
    }

    /**
     * Combine this filter with another using AND
     */
    fun and(other: EventFilter<T>): EventFilter<T> = And(this, other)

    /**
     * Combine this filter with another using OR
     */
    fun or(other: EventFilter<T>): EventFilter<T> = Or(this, other)

    /**
     * Negate this filter
     */
    operator fun not(): EventFilter<T> = Not(this)

    companion object {
        /**
         * Accept all events
         */
        fun <T> all(): EventFilter<T> = All()

        /**
         * Filter by predicate
         */
        fun <T> by(predicate: (T) -> Boolean): EventFilter<T> = Predicate(predicate)

        /**
         * Filter by metadata field
         */
        fun <T> byMetadata(key: String, value: String): EventFilter<T> = Metadata(key, value)

        /**
         * Filter by user ID
         */
        fun <T> byUserId(userId: String): EventFilter<T> = UserId(userId)

        /**
         * Filter by tenant ID
         */
        fun <T> byTenantId(tenantId: String): EventFilter<T> = TenantId(tenantId)

        /**
         * Filter by correlation ID
         */
        fun <T> byCorrelationId(correlationId: String): EventFilter<T> = CorrelationId(correlationId)
    }
}
