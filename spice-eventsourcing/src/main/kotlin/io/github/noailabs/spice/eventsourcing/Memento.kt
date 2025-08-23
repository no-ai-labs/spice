package io.github.noailabs.spice.eventsourcing

import java.time.Instant

/**
 * Memento Pattern implementation for Event Sourcing
 * 
 * Provides a structured way to save and restore aggregate state
 * without exposing internal implementation details
 */

/**
 * Base interface for aggregate mementos
 * 
 * A memento captures the state of an aggregate at a specific version
 * and provides a way to restore that state later
 */
interface AggregateMemento {
    /**
     * The unique identifier of the aggregate
     */
    val aggregateId: String
    
    /**
     * The type of the aggregate (e.g., "Order", "Policy")
     */
    val aggregateType: String
    
    /**
     * The version of the aggregate when this memento was created
     */
    val version: Long
    
    /**
     * When this memento was created
     */
    val timestamp: Instant
    
    /**
     * Restore the state to the given aggregate
     * 
     * @param aggregate The aggregate to restore state to
     * @throws IllegalArgumentException if the aggregate type doesn't match
     */
    fun restoreState(aggregate: Aggregate)
}

/**
 * Interface for aggregates that support memento pattern
 */
interface MementoCapable {
    /**
     * Create a memento capturing the current state
     */
    fun createMemento(): AggregateMemento
    
    /**
     * Restore state from a memento
     */
    fun restoreFromMemento(memento: AggregateMemento)
}

/**
 * Enhanced Aggregate base class with memento support
 */
abstract class MementoAggregate : Aggregate(), MementoCapable {
    
    /**
     * Template method for creating type-safe mementos
     */
    protected abstract fun doCreateMemento(): AggregateMemento
    
    /**
     * Template method for restoring from type-safe mementos
     */
    protected abstract fun doRestoreFromMemento(memento: AggregateMemento)
    
    override fun createMemento(): AggregateMemento {
        return doCreateMemento()
    }
    
    override fun restoreFromMemento(memento: AggregateMemento) {
        require(memento.aggregateType == aggregateType) {
            "Cannot restore memento of type ${memento.aggregateType} to aggregate of type $aggregateType"
        }
        require(memento.aggregateId == aggregateId) {
            "Cannot restore memento with ID ${memento.aggregateId} to aggregate with ID $aggregateId"
        }
        
        doRestoreFromMemento(memento)
        this.version = memento.version
    }
}

/**
 * Snapshot store that uses memento pattern
 */
interface MementoSnapshotStore {
    /**
     * Save a memento as a snapshot
     */
    suspend fun saveMemento(memento: AggregateMemento)
    
    /**
     * Load the latest memento for an aggregate
     */
    suspend fun loadLatestMemento(aggregateType: String, aggregateId: String): AggregateMemento?
    
    /**
     * Load a memento at a specific version
     */
    suspend fun loadMementoAtVersion(
        aggregateType: String, 
        aggregateId: String, 
        version: Long
    ): AggregateMemento?
    
    /**
     * Delete all mementos for an aggregate
     */
    suspend fun deleteMementos(aggregateType: String, aggregateId: String)
}

/**
 * Default implementation that converts between Memento and Snapshot
 */
class DefaultMementoSnapshotStore(
    private val eventStore: EventStore,
    private val serializer: MementoSerializer = JsonMementoSerializer()
) : MementoSnapshotStore {
    
    override suspend fun saveMemento(memento: AggregateMemento) {
        val snapshot = Snapshot(
            streamId = "${memento.aggregateType}-${memento.aggregateId}",
            version = memento.version,
            timestamp = memento.timestamp,
            data = serializer.serialize(memento),
            metadata = mapOf(
                "aggregateType" to memento.aggregateType,
                "aggregateId" to memento.aggregateId,
                "mementoClass" to memento::class.java.name
            )
        )
        
        eventStore.saveSnapshot(
            streamId = snapshot.streamId,
            snapshot = snapshot,
            version = memento.version
        )
    }
    
    override suspend fun loadLatestMemento(
        aggregateType: String, 
        aggregateId: String
    ): AggregateMemento? {
        val streamId = "$aggregateType-$aggregateId"
        val snapshot = eventStore.getLatestSnapshot(streamId) ?: return null
        
        return serializer.deserialize(
            data = snapshot.data,
            metadata = snapshot.metadata
        )
    }
    
    override suspend fun loadMementoAtVersion(
        aggregateType: String,
        aggregateId: String,
        version: Long
    ): AggregateMemento? {
        // This would need an enhanced EventStore interface
        // For now, just return the latest if version matches
        val latest = loadLatestMemento(aggregateType, aggregateId)
        return if (latest?.version == version) latest else null
    }
    
    override suspend fun deleteMementos(aggregateType: String, aggregateId: String) {
        // This would need an enhanced EventStore interface
        // For now, this is a no-op
    }
}

/**
 * Interface for serializing mementos
 */
interface MementoSerializer {
    fun serialize(memento: AggregateMemento): ByteArray
    fun deserialize(data: ByteArray, metadata: Map<String, String>): AggregateMemento
}

/**
 * JSON-based memento serializer using kotlinx.serialization
 */
class JsonMementoSerializer : MementoSerializer {
    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }
    
    override fun serialize(memento: AggregateMemento): ByteArray {
        // For now, use Java serialization
        // In production, use proper JSON serialization with type registry
        val baos = java.io.ByteArrayOutputStream()
        java.io.ObjectOutputStream(baos).use { oos ->
            oos.writeObject(memento)
        }
        return baos.toByteArray()
    }
    
    override fun deserialize(data: ByteArray, metadata: Map<String, String>): AggregateMemento {
        // For now, use Java deserialization
        // In production, use proper JSON deserialization with type registry
        val bais = java.io.ByteArrayInputStream(data)
        java.io.ObjectInputStream(bais).use { ois ->
            return ois.readObject() as AggregateMemento
        }
    }
}

/**
 * Enhanced AggregateRepository with memento support
 */
class MementoAggregateRepository(
    private val eventStore: EventStore,
    private val snapshotStore: MementoSnapshotStore = DefaultMementoSnapshotStore(eventStore),
    private val snapshotFrequency: Int = 100
) {
    
    /**
     * Load an aggregate, trying snapshot first then events
     */
    suspend fun <T : MementoAggregate> load(
        aggregateId: String,
        aggregateFactory: () -> T
    ): T {
        val aggregate = aggregateFactory()
        val streamId = aggregate.getStreamId()
        
        // Try to load from snapshot first
        val memento = snapshotStore.loadLatestMemento(
            aggregate.aggregateType, 
            aggregateId
        )
        
        val fromVersion = if (memento != null) {
            aggregate.restoreFromMemento(memento)
            memento.version + 1
        } else {
            0L
        }
        
        // Load remaining events
        val events = eventStore.readStream(streamId, fromVersion)
            .filterIsInstance<DomainEvent>()
        
        // Apply events since snapshot
        aggregate.loadFromHistory(events)
        
        return aggregate
    }
    
    /**
     * Save an aggregate, creating snapshot if needed
     */
    suspend fun save(aggregate: MementoAggregate) {
        val events = aggregate.getUncommittedEvents()
        if (events.isEmpty()) return
        
        val streamId = aggregate.getStreamId()
        val newVersion = eventStore.append(streamId, events, aggregate.version)
        
        aggregate.markEventsAsCommitted(newVersion)
        
        // Create snapshot if we've accumulated enough events
        if (newVersion % snapshotFrequency == 0L) {
            val memento = aggregate.createMemento()
            snapshotStore.saveMemento(memento)
        }
    }
    
    /**
     * Load aggregate at a specific version using snapshots
     */
    suspend fun <T : MementoAggregate> loadAtVersion(
        aggregateId: String,
        version: Long,
        aggregateFactory: () -> T
    ): T {
        val aggregate = aggregateFactory()
        
        // Try to find a snapshot before the target version
        val memento = snapshotStore.loadLatestMemento(
            aggregate.aggregateType,
            aggregateId
        )
        
        val fromVersion = if (memento != null && memento.version <= version) {
            aggregate.restoreFromMemento(memento)
            memento.version + 1
        } else {
            0L
        }
        
        // Load events up to target version
        val streamId = aggregate.getStreamId()
        val events = eventStore.readStream(streamId, fromVersion, version)
            .filterIsInstance<DomainEvent>()
        
        aggregate.loadFromHistory(events)
        
        return aggregate
    }
}