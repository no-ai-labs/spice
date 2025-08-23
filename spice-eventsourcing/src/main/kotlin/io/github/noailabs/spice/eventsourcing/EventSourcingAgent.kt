package io.github.noailabs.spice.eventsourcing

import io.github.noailabs.spice.SmartAgent
import io.github.noailabs.spice.Comm
import io.github.noailabs.spice.CommType
import io.github.noailabs.spice.CommRole
import java.time.Instant

/**
 * Base class for event-sourced agents in Spice Framework
 * 
 * Combines the SmartAgent capabilities with event sourcing patterns
 */
abstract class EventSourcingAgent(
    val id: String,
    name: String,
    role: String = "",
    private val eventStore: EventStore
) {
    
    val agent = SmartAgent(
        id = id,
        name = name,
        role = role
    )
    
    protected val aggregateId: String = id
    protected abstract val aggregateType: String
    
    /**
     * Initialize agent by loading events from store
     */
    open suspend fun initialize() {
        val streamId = getStreamId()
        val events = eventStore.readStream(streamId, 0)
        
        // Rebuild state from events
        events.forEach { event ->
            applyEvent(event)
        }
    }
    
    /**
     * Process a comm and potentially generate events
     */
    open suspend fun processComm(comm: Comm): Comm {
        return try {
            val events = handleComm(comm)
            
            if (events.isNotEmpty()) {
                // Append events to store
                val streamId = getStreamId()
                val currentVersion = eventStore.getStreamVersion(streamId)
                eventStore.append(streamId, events, currentVersion)
                
                // Apply events to update state
                events.forEach { applyEvent(it) }
            }
            
            // Return success response
            Comm(
                content = "Processed successfully",
                from = agent.id,
                to = comm.from,
                type = CommType.RESULT,
                role = CommRole.AGENT
            )
        } catch (e: Exception) {
            // Return error response
            Comm(
                content = "Error: ${e.message}",
                from = agent.id,
                to = comm.from,
                type = CommType.ERROR,
                role = CommRole.AGENT
            )
        }
    }
    
    /**
     * Handle comm and return events to be applied
     */
    protected abstract suspend fun handleComm(comm: Comm): List<Event>
    
    /**
     * Apply an event to update agent state
     */
    protected abstract fun applyEvent(event: Event)
    
    /**
     * Get the stream ID for this agent
     */
    protected fun getStreamId(): String = "$aggregateType-$aggregateId"
}

/**
 * Example: Counter agent using event sourcing
 */
class CounterAgent(
    eventStore: EventStore,
    id: String = "counter-agent"
) : EventSourcingAgent(
    id = id,
    name = "Counter Agent",
    role = "Counter",
    eventStore = eventStore
) {
    
    override val aggregateType = "Counter"
    
    private var count: Int = 0
    
    override suspend fun handleComm(comm: Comm): List<Event> {
        return when (comm.content.lowercase()) {
            "increment" -> listOf(CounterIncrementedEvent(aggregateId))
            "decrement" -> listOf(CounterDecrementedEvent(aggregateId))
            "reset" -> listOf(CounterResetEvent(aggregateId))
            else -> emptyList()
        }
    }
    
    override fun applyEvent(event: Event) {
        when (event) {
            is CounterIncrementedEvent -> count++
            is CounterDecrementedEvent -> count--
            is CounterResetEvent -> count = 0
        }
    }
    
    override suspend fun processComm(comm: Comm): Comm {
        val response = super.processComm(comm)
        
        // Override to include current count in response
        return if (response.type != CommType.ERROR) {
            response.copy(
                content = "Count is now: $count"
            )
        } else {
            response
        }
    }
}

// Counter events
data class CounterIncrementedEvent(
    val counterId: String,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val eventType: String = "CounterIncremented",
    override val streamId: String = "Counter-$counterId",
    override val version: Long = -1,
    override val timestamp: Instant = Instant.now(),
    override val metadata: EventMetadata = EventMetadata(
        userId = "system",
        correlationId = java.util.UUID.randomUUID().toString()
    )
) : Event {
    override fun toProto(): ByteArray = eventType.toByteArray()
}

data class CounterDecrementedEvent(
    val counterId: String,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val eventType: String = "CounterDecremented",
    override val streamId: String = "Counter-$counterId",
    override val version: Long = -1,
    override val timestamp: Instant = Instant.now(),
    override val metadata: EventMetadata = EventMetadata(
        userId = "system",
        correlationId = java.util.UUID.randomUUID().toString()
    )
) : Event {
    override fun toProto(): ByteArray = eventType.toByteArray()
}

data class CounterResetEvent(
    val counterId: String,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val eventType: String = "CounterReset",
    override val streamId: String = "Counter-$counterId",
    override val version: Long = -1,
    override val timestamp: Instant = Instant.now(),
    override val metadata: EventMetadata = EventMetadata(
        userId = "system",
        correlationId = java.util.UUID.randomUUID().toString()
    )
) : Event {
    override fun toProto(): ByteArray = eventType.toByteArray()
}