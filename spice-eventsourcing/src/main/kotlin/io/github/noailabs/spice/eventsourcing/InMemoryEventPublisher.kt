package io.github.noailabs.spice.eventsourcing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory event publisher for testing and development
 * 
 * This implementation keeps all events in memory and provides
 * instant event delivery without external dependencies
 */
class InMemoryEventPublisher : EventPublisher {
    
    // All events flow
    private val allEventsFlow = MutableSharedFlow<Event>(
        replay = 0,
        extraBufferCapacity = 1000
    )
    
    // Stream-specific flows
    private val streamFlows = ConcurrentHashMap<String, MutableSharedFlow<Event>>()
    
    // Event type flows
    private val typeFlows = ConcurrentHashMap<String, MutableSharedFlow<Event>>()
    
    // Store all events for replay
    private val eventStore = mutableListOf<Event>()
    private val mutex = Mutex()
    
    override suspend fun publish(event: Event) {
        mutex.withLock {
            eventStore.add(event)
        }
        
        // Emit to all events flow
        allEventsFlow.emit(event)
        
        // Emit to stream-specific flow
        val streamFlow = streamFlows.getOrPut(event.streamId) {
            MutableSharedFlow(replay = 0, extraBufferCapacity = 1000)
        }
        streamFlow.emit(event)
        
        // Emit to type-specific flow
        val typeFlow = typeFlows.getOrPut(event.eventType) {
            MutableSharedFlow(replay = 0, extraBufferCapacity = 1000)
        }
        typeFlow.emit(event)
    }
    
    override suspend fun publishBatch(events: List<Event>) {
        events.forEach { publish(it) }
    }
    
    override suspend fun subscribe(streamId: String, fromVersion: Long): Flow<Event> {
        // Create stream flow if it doesn't exist
        val streamFlow = streamFlows.getOrPut(streamId) {
            MutableSharedFlow(replay = 0, extraBufferCapacity = 1000)
        }
        
        // Return flow that filters by version
        return streamFlow.filter { it.version >= fromVersion }
    }
    
    override suspend fun subscribeToTypes(vararg eventTypes: String): Flow<Event> {
        // For simplicity, filter from all events flow
        return allEventsFlow.filter { it.eventType in eventTypes }
    }
    
    override suspend fun subscribeToAll(): Flow<Event> {
        return allEventsFlow
    }
    
    override suspend fun close() {
        // Clear all flows
        streamFlows.clear()
        typeFlows.clear()
        mutex.withLock {
            eventStore.clear()
        }
    }
    
    /**
     * Get all stored events (useful for testing)
     */
    suspend fun getAllEvents(): List<Event> = mutex.withLock {
        eventStore.toList()
    }
    
    /**
     * Get events for a specific stream (useful for testing)
     */
    suspend fun getStreamEvents(streamId: String): List<Event> = mutex.withLock {
        eventStore.filter { it.streamId == streamId }
    }
    
    /**
     * Clear all events (useful for testing)
     */
    suspend fun clear() = mutex.withLock {
        eventStore.clear()
    }
}