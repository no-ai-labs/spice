package io.github.noailabs.spice.eventsourcing

import io.github.noailabs.spice.KafkaCommHub
import io.github.noailabs.spice.Comm
import io.github.noailabs.spice.CommType
import io.github.noailabs.spice.CommRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.Base64

/**
 * Kafka-based event publisher implementation
 * 
 * Uses Spice's KafkaCommHub for event distribution
 */
class KafkaEventPublisher(
    private val kafkaCommHub: KafkaCommHub,
    private val config: EventPublisherConfig = EventPublisherConfig()
) : EventPublisher {
    
    private val logger = LoggerFactory.getLogger(KafkaEventPublisher::class.java)
    private val eventSubscribers = ConcurrentHashMap<String, Channel<Event>>()
    private var isInitialized = false
    
    init {
        setupEventSubscriptions()
    }
    
    override suspend fun publish(event: Event) {
        val comm = eventToComm(event)
        kafkaCommHub.send(comm)
        
        // Also broadcast to stream-specific topic
        val streamComm = comm.copy(
            to = "stream-${event.streamId}"
        )
        kafkaCommHub.send(streamComm)
        
        // Distribute to local subscribers
        distributeToSubscribers(event)
    }
    
    override suspend fun publishBatch(events: List<Event>) {
        events.forEach { publish(it) }
    }
    
    override suspend fun subscribe(streamId: String, fromVersion: Long): Flow<Event> = flow {
        val channel = Channel<Event>(Channel.UNLIMITED)
        val subscriptionId = "stream-${streamId}-${System.currentTimeMillis()}"
        eventSubscribers[subscriptionId] = channel
        
        try {
            for (event in channel) {
                if (event.streamId == streamId && event.version >= fromVersion) {
                    emit(event)
                }
            }
        } finally {
            eventSubscribers.remove(subscriptionId)
            channel.close()
        }
    }
    
    override suspend fun subscribeToTypes(vararg eventTypes: String): Flow<Event> = flow {
        val channel = Channel<Event>(Channel.UNLIMITED)
        val subscriptionId = "types-${System.currentTimeMillis()}"
        eventSubscribers[subscriptionId] = channel
        
        try {
            for (event in channel) {
                if (event.eventType in eventTypes) {
                    emit(event)
                }
            }
        } finally {
            eventSubscribers.remove(subscriptionId)
            channel.close()
        }
    }
    
    override suspend fun subscribeToAll(): Flow<Event> = flow {
        val channel = Channel<Event>(Channel.UNLIMITED)
        val subscriptionId = "all-${System.currentTimeMillis()}"
        eventSubscribers[subscriptionId] = channel
        
        try {
            for (event in channel) {
                emit(event)
            }
        } finally {
            eventSubscribers.remove(subscriptionId)
            channel.close()
        }
    }
    
    override suspend fun close() {
        kafkaCommHub.unsubscribeAgent("event-publisher")
        eventSubscribers.values.forEach { it.close() }
        eventSubscribers.clear()
    }
    
    private fun setupEventSubscriptions() {
        if (isInitialized) return
        
        kafkaCommHub.subscribeAgent("event-publisher") { comm ->
            if (comm.type == CommType.DATA && comm.data["is_event"] == "true") {
                GlobalScope.launch {
                    try {
                        val event = commToEvent(comm)
                        distributeToSubscribers(event)
                    } catch (e: Exception) {
                        logger.error("Failed to process event from Kafka", e)
                    }
                }
            }
        }
        
        isInitialized = true
    }
    
    private suspend fun distributeToSubscribers(event: Event) {
        eventSubscribers.values.forEach { channel ->
            try {
                channel.send(event)
            } catch (e: Exception) {
                logger.warn("Failed to send event to subscriber", e)
            }
        }
    }
    
    private fun eventToComm(event: Event): Comm {
        return Comm(
            content = "Event: ${event.eventType} for stream ${event.streamId}",
            from = "event-publisher",
            to = "event-stream",
            type = CommType.DATA,
            role = CommRole.SYSTEM,
            data = mapOf(
                "is_event" to "true",
                "event_id" to event.eventId,
                "event_type" to event.eventType,
                "stream_id" to event.streamId,
                "version" to event.version.toString(),
                "timestamp" to event.timestamp.toString(),
                "metadata" to Json.encodeToString(event.metadata),
                "data" to Base64.getEncoder().encodeToString(event.toProto())
            )
        )
    }
    
    private fun commToEvent(comm: Comm): Event {
        return GenericEvent(
            eventId = comm.data["event_id"] as String,
            eventType = comm.data["event_type"] as String,
            streamId = comm.data["stream_id"] as String,
            version = (comm.data["version"] as String).toLong(),
            timestamp = java.time.Instant.parse(comm.data["timestamp"] as String),
            metadata = Json.decodeFromString(comm.data["metadata"] as String),
            data = Base64.getDecoder().decode(comm.data["data"] as String)
        )
    }
}