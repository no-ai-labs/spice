package io.github.noailabs.spice.eventsourcing

import kotlinx.coroutines.flow.Flow

/**
 * Event Publisher interface for publishing events to various backends
 * 
 * This abstraction allows different implementations for event publishing
 * such as Kafka, RabbitMQ, AWS SNS/SQS, or in-memory for testing
 */
interface EventPublisher {
    
    /**
     * Publish a single event
     * 
     * @param event The event to publish
     */
    suspend fun publish(event: Event)
    
    /**
     * Publish multiple events in batch
     * 
     * @param events List of events to publish
     */
    suspend fun publishBatch(events: List<Event>)
    
    /**
     * Subscribe to events from a specific stream
     * 
     * @param streamId The stream to subscribe to
     * @param fromVersion Start subscription from this version (inclusive)
     * @return Flow of events from the stream
     */
    suspend fun subscribe(streamId: String, fromVersion: Long = 0): Flow<Event>
    
    /**
     * Subscribe to events by type across all streams
     * 
     * @param eventTypes List of event types to subscribe to
     * @return Flow of events matching the types
     */
    suspend fun subscribeToTypes(vararg eventTypes: String): Flow<Event>
    
    /**
     * Subscribe to all events
     * 
     * @return Flow of all events
     */
    suspend fun subscribeToAll(): Flow<Event>
    
    /**
     * Close the publisher and release resources
     */
    suspend fun close()
}

/**
 * Event Publisher that supports multiple backends
 */
class CompositeEventPublisher(
    private val publishers: List<EventPublisher>
) : EventPublisher {
    
    override suspend fun publish(event: Event) {
        publishers.forEach { it.publish(event) }
    }
    
    override suspend fun publishBatch(events: List<Event>) {
        publishers.forEach { it.publishBatch(events) }
    }
    
    override suspend fun subscribe(streamId: String, fromVersion: Long): Flow<Event> {
        // Return from the first publisher (primary)
        return publishers.firstOrNull()?.subscribe(streamId, fromVersion) 
            ?: kotlinx.coroutines.flow.emptyFlow()
    }
    
    override suspend fun subscribeToTypes(vararg eventTypes: String): Flow<Event> {
        // Return from the first publisher (primary)
        return publishers.firstOrNull()?.subscribeToTypes(*eventTypes)
            ?: kotlinx.coroutines.flow.emptyFlow()
    }
    
    override suspend fun subscribeToAll(): Flow<Event> {
        // Return from the first publisher (primary)
        return publishers.firstOrNull()?.subscribeToAll()
            ?: kotlinx.coroutines.flow.emptyFlow()
    }
    
    override suspend fun close() {
        publishers.forEach { it.close() }
    }
}

/**
 * Configuration for event publishers
 */
data class EventPublisherConfig(
    val batchSize: Int = 100,
    val batchTimeoutMillis: Long = 1000,
    val retryPolicy: RetryPolicy = ExponentialBackoffRetryPolicy(),
    val enableMetrics: Boolean = true
)

/**
 * Factory for creating event publishers
 */
object EventPublisherFactory {
    
    /**
     * Create a Kafka event publisher
     */
    fun kafka(
        kafkaCommHub: io.github.noailabs.spice.KafkaCommHub,
        config: EventPublisherConfig = EventPublisherConfig()
    ): EventPublisher {
        return KafkaEventPublisher(kafkaCommHub, config)
    }
    
    /**
     * Create an in-memory event publisher for testing
     */
    fun inMemory(): EventPublisher {
        return InMemoryEventPublisher()
    }
    
    /**
     * Create a composite publisher that publishes to multiple backends
     */
    fun composite(vararg publishers: EventPublisher): EventPublisher {
        return CompositeEventPublisher(publishers.toList())
    }
}