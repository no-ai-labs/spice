package io.github.noailabs.spice.eventsourcing

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of MementoSnapshotStore for testing
 * 
 * This provides a simple way to test memento functionality
 * without external dependencies
 */
class InMemoryMementoSnapshotStore : MementoSnapshotStore {
    
    // Key: streamId, Value: List of mementos sorted by version
    private val mementos = ConcurrentHashMap<String, MutableList<AggregateMemento>>()
    private val mutex = Mutex()
    
    override suspend fun saveMemento(memento: AggregateMemento) = mutex.withLock {
        val streamId = "${memento.aggregateType}-${memento.aggregateId}"
        val mementoList = mementos.getOrPut(streamId) { mutableListOf() }
        
        // Remove any existing memento at the same version
        mementoList.removeIf { it.version == memento.version }
        
        // Add new memento and sort by version
        mementoList.add(memento)
        mementoList.sortBy { it.version }
    }
    
    override suspend fun loadLatestMemento(
        aggregateType: String,
        aggregateId: String
    ): AggregateMemento? = mutex.withLock {
        val streamId = "$aggregateType-$aggregateId"
        return mementos[streamId]?.lastOrNull()
    }
    
    override suspend fun loadMementoAtVersion(
        aggregateType: String,
        aggregateId: String,
        version: Long
    ): AggregateMemento? = mutex.withLock {
        val streamId = "$aggregateType-$aggregateId"
        return mementos[streamId]?.find { it.version == version }
    }
    
    override suspend fun deleteMementos(
        aggregateType: String,
        aggregateId: String
    ) {
        mutex.withLock {
            val streamId = "$aggregateType-$aggregateId"
            mementos.remove(streamId)
        }
    }
    
    /**
     * Get all mementos for testing purposes
     */
    suspend fun getAllMementos(): Map<String, List<AggregateMemento>> = mutex.withLock {
        mementos.mapValues { it.value.toList() }
    }
    
    /**
     * Clear all mementos
     */
    suspend fun clear() = mutex.withLock {
        mementos.clear()
    }
    
    /**
     * Get memento count for an aggregate
     */
    suspend fun getMementoCount(
        aggregateType: String,
        aggregateId: String
    ): Int = mutex.withLock {
        val streamId = "$aggregateType-$aggregateId"
        mementos[streamId]?.size ?: 0
    }
}

/**
 * Factory methods for creating test-friendly memento stores
 */
object MementoSnapshotStoreFactory {
    
    /**
     * Create an in-memory store for testing
     */
    fun inMemory(): MementoSnapshotStore {
        return InMemoryMementoSnapshotStore()
    }
    
    /**
     * Create a store backed by an EventStore
     */
    fun withEventStore(
        eventStore: EventStore,
        serializer: MementoSerializer = JsonMementoSerializer()
    ): MementoSnapshotStore {
        return DefaultMementoSnapshotStore(eventStore, serializer)
    }
    
    /**
     * Create a composite store that writes to multiple backends
     */
    fun composite(stores: List<MementoSnapshotStore>): MementoSnapshotStore {
        return CompositeMementoSnapshotStore(stores)
    }
}

/**
 * Composite memento store that writes to multiple backends
 */
class CompositeMementoSnapshotStore(
    private val stores: List<MementoSnapshotStore>
) : MementoSnapshotStore {
    
    override suspend fun saveMemento(memento: AggregateMemento) {
        stores.forEach { it.saveMemento(memento) }
    }
    
    override suspend fun loadLatestMemento(
        aggregateType: String,
        aggregateId: String
    ): AggregateMemento? {
        // Return from the first store that has it
        stores.forEach { store ->
            store.loadLatestMemento(aggregateType, aggregateId)?.let { return it }
        }
        return null
    }
    
    override suspend fun loadMementoAtVersion(
        aggregateType: String,
        aggregateId: String,
        version: Long
    ): AggregateMemento? {
        // Return from the first store that has it
        stores.forEach { store ->
            store.loadMementoAtVersion(aggregateType, aggregateId, version)?.let { return it }
        }
        return null
    }
    
    override suspend fun deleteMementos(aggregateType: String, aggregateId: String) {
        stores.forEach { it.deleteMementos(aggregateType, aggregateId) }
    }
}