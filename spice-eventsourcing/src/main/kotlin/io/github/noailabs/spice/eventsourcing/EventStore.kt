package io.github.noailabs.spice.eventsourcing

import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Event Store interface for event sourcing support in Spice Framework
 * 
 * This provides event sourcing capabilities that can be integrated
 * with Spice's communication infrastructure
 */
interface EventStore {
    
    /**
     * Append events to a stream
     * @param streamId The aggregate/stream identifier
     * @param events List of events to append
     * @param expectedVersion Expected current version for optimistic concurrency control (-1 for any)
     * @return The new stream version after appending
     */
    suspend fun append(
        streamId: String,
        events: List<Event>,
        expectedVersion: Long = -1
    ): Long
    
    /**
     * Read events from a stream
     * @param streamId The stream to read from
     * @param fromVersion Start reading from this version (inclusive)
     * @param toVersion Read up to this version (inclusive, null for latest)
     * @return List of events in order
     */
    suspend fun readStream(
        streamId: String,
        fromVersion: Long = 0,
        toVersion: Long? = null
    ): List<Event>
    
    /**
     * Subscribe to a stream for real-time updates
     * @param streamId The stream to subscribe to
     * @param fromVersion Start subscription from this version
     * @return Flow of events
     */
    suspend fun subscribe(
        streamId: String,
        fromVersion: Long = 0
    ): Flow<Event>
    
    /**
     * Subscribe to events by type across all streams
     * @param eventTypes List of event types to subscribe to
     * @return Flow of events matching the types
     */
    suspend fun subscribeToTypes(
        vararg eventTypes: String
    ): Flow<Event>
    
    /**
     * Get current version of a stream
     * @param streamId The stream identifier
     * @return Current version or -1 if stream doesn't exist
     */
    suspend fun getStreamVersion(streamId: String): Long
    
    /**
     * Create a snapshot of current state
     * @param streamId The stream identifier
     * @param snapshot The snapshot data
     * @param version The version this snapshot represents
     */
    suspend fun saveSnapshot(
        streamId: String,
        snapshot: Snapshot,
        version: Long
    )
    
    /**
     * Get the latest snapshot for a stream
     * @param streamId The stream identifier
     * @return Latest snapshot or null if none exists
     */
    suspend fun getLatestSnapshot(streamId: String): Snapshot?
}

/**
 * Base interface for all events
 */
interface Event {
    val eventId: String
    val eventType: String
    val streamId: String
    val version: Long
    val timestamp: Instant
    val metadata: EventMetadata
    
    /**
     * Convert event to protobuf bytes for storage/transmission
     */
    fun toProto(): ByteArray
}

/**
 * Event metadata for tracking and correlation
 */
@kotlinx.serialization.Serializable
data class EventMetadata(
    val userId: String,
    val correlationId: String,
    val causationId: String? = null,
    val tenantId: String? = null,
    val sourceSystem: String = "spice"
)

/**
 * Snapshot of aggregate state at a specific version
 */
data class Snapshot(
    val streamId: String,
    val version: Long,
    val timestamp: Instant,
    val data: ByteArray,
    val metadata: Map<String, Any?> = emptyMap()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Snapshot) return false
        return streamId == other.streamId && 
               version == other.version &&
               data.contentEquals(other.data)
    }
    
    override fun hashCode(): Int {
        var result = streamId.hashCode()
        result = 31 * result + version.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

/**
 * Event handler interface for processing events
 */
interface EventHandler<T : Event> {
    suspend fun handle(event: T)
}

/**
 * Aggregate root interface for domain objects
 */
abstract class AggregateRoot {
    val pendingEvents = mutableListOf<Event>()
    var version: Long = -1
        protected set
    
    /**
     * Apply an event to update state and add to pending events
     */
    protected fun applyEvent(event: Event) {
        applyChange(event)
        pendingEvents.add(event)
        version = event.version
    }
    
    /**
     * Apply event to update state (used for rebuilding from history)
     */
    abstract fun applyChange(event: Event)
    
    /**
     * Mark pending events as committed
     */
    fun markEventsAsCommitted() {
        pendingEvents.clear()
    }
    
    /**
     * Load aggregate from event history
     */
    fun loadFromHistory(events: List<Event>) {
        events.forEach { event ->
            applyChange(event)
            version = event.version
        }
    }
}