package io.github.noailabs.spice.eventsourcing

import io.github.noailabs.spice.KafkaCommHub
import javax.sql.DataSource

/**
 * Factory for creating EventStore instances
 * 
 * Provides convenient methods to create different EventStore implementations
 * with various backing stores and configurations
 */
object EventStoreFactory {
    
    /**
     * Create a Kafka-based EventStore with PostgreSQL persistence
     * 
     * @param kafkaCommHub The Kafka communication hub
     * @param dataSource The PostgreSQL data source
     * @param config Optional configuration
     * @return Configured EventStore instance
     */
    fun kafkaWithPostgres(
        kafkaCommHub: KafkaCommHub,
        dataSource: DataSource,
        config: EventStoreConfig = EventStoreConfig()
    ): EventStore {
        val eventPublisher = EventPublisherFactory.kafka(kafkaCommHub)
        return KafkaEventStore(
            dataSource = dataSource,
            eventPublisher = eventPublisher,
            config = config
        )
    }
    
    /**
     * Create an in-memory EventStore for testing
     * 
     * @param dataSource The data source for persistence
     * @return In-memory EventStore instance
     */
    fun inMemory(dataSource: DataSource): EventStore {
        val eventPublisher = EventPublisherFactory.inMemory()
        return KafkaEventStore(
            dataSource = dataSource,
            eventPublisher = eventPublisher
        )
    }
    
    /**
     * Create an EventStore with custom event publisher
     * 
     * @param dataSource The data source for persistence
     * @param eventPublisher Custom event publisher
     * @param config Optional configuration
     * @return Configured EventStore instance
     */
    fun withCustomPublisher(
        dataSource: DataSource,
        eventPublisher: EventPublisher,
        config: EventStoreConfig = EventStoreConfig()
    ): EventStore {
        return KafkaEventStore(
            dataSource = dataSource,
            eventPublisher = eventPublisher,
            config = config
        )
    }
    
    /**
     * Create an EventStore with multiple publishers (e.g., Kafka + RabbitMQ)
     * 
     * @param dataSource The data source for persistence
     * @param publishers List of event publishers
     * @param config Optional configuration
     * @return EventStore with composite publisher
     */
    fun withMultiplePublishers(
        dataSource: DataSource,
        publishers: List<EventPublisher>,
        config: EventStoreConfig = EventStoreConfig()
    ): EventStore {
        val compositePublisher = CompositeEventPublisher(publishers)
        return KafkaEventStore(
            dataSource = dataSource,
            eventPublisher = compositePublisher,
            config = config
        )
    }
}