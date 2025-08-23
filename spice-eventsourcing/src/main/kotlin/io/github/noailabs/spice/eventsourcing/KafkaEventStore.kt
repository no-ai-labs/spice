package io.github.noailabs.spice.eventsourcing

import io.github.noailabs.spice.KafkaCommHub
import io.github.noailabs.spice.Comm
import io.github.noailabs.spice.CommType
import io.github.noailabs.spice.CommRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource
import org.slf4j.LoggerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * Kafka-based EventStore implementation
 * 
 * Uses Kafka for event streaming and PostgreSQL for persistent storage
 * Integrates with Spice's KafkaCommHub for messaging
 */
class KafkaEventStore(
    private val kafkaCommHub: KafkaCommHub,
    private val dataSource: DataSource,
    private val config: EventStoreConfig = EventStoreConfig(),
    private val serializer: EventSerializer = JsonEventSerializer()
) : EventStore {
    
    private val logger = LoggerFactory.getLogger(KafkaEventStore::class.java)
    private val streamVersionCache = ConcurrentHashMap<String, Long>()
    private val eventSubscribers = ConcurrentHashMap<String, Channel<Event>>()
    
    init {
        // Initialize database schema
        initializeSchema()
        
        // Subscribe to event topics
        setupEventSubscriptions()
    }
    
    override suspend fun append(
        streamId: String,
        events: List<Event>,
        expectedVersion: Long
    ): Long = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                // Check expected version
                val currentVersion = getStreamVersionFromDb(conn, streamId)
                if (expectedVersion != -1L && currentVersion != expectedVersion) {
                    throw ConcurrencyException(streamId, expectedVersion, currentVersion)
                }
                
                var version = currentVersion
                val publishedEvents = mutableListOf<Event>()
                
                // Insert events into database
                conn.prepareStatement("""
                    INSERT INTO events (
                        event_id, event_type, stream_id, version, 
                        timestamp, data, metadata
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """).use { stmt ->
                    for (event in events) {
                        version++
                        val versionedEvent = event.withVersion(version)
                        
                        val serialized = serializer.serialize(versionedEvent)
                        
                        stmt.setString(1, versionedEvent.eventId)
                        stmt.setString(2, versionedEvent.eventType)
                        stmt.setString(3, streamId)
                        stmt.setLong(4, version)
                        stmt.setTimestamp(5, java.sql.Timestamp.from(versionedEvent.timestamp))
                        stmt.setBytes(6, serialized.data)
                        stmt.setString(7, Json.encodeToString(versionedEvent.metadata))
                        stmt.addBatch()
                        
                        publishedEvents.add(versionedEvent)
                    }
                    stmt.executeBatch()
                }
                
                // Update stream version
                updateStreamVersion(conn, streamId, version)
                
                conn.commit()
                streamVersionCache[streamId] = version
                
                // Publish events to Kafka
                publishedEvents.forEach { event ->
                    publishEventToKafka(event)
                }
                
                version
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }
    }
    
    override suspend fun readStream(
        streamId: String,
        fromVersion: Long,
        toVersion: Long?
    ): List<Event> = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val sql = buildString {
                append("SELECT event_id, event_type, version, timestamp, data, metadata ")
                append("FROM events ")
                append("WHERE stream_id = ? AND version >= ? ")
                if (toVersion != null) {
                    append("AND version <= ? ")
                }
                append("ORDER BY version")
            }
            
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, streamId)
                stmt.setLong(2, fromVersion)
                if (toVersion != null) {
                    stmt.setLong(3, toVersion)
                }
                
                stmt.executeQuery().use { rs ->
                    val events = mutableListOf<Event>()
                    while (rs.next()) {
                        val serialized = SerializedEvent(
                            eventType = rs.getString("event_type"),
                            data = rs.getBytes("data"),
                            contentType = config.contentType
                        )
                        val event = serializer.deserialize(serialized)
                        events.add(event)
                    }
                    events
                }
            }
        }
    }
    
    override suspend fun subscribe(
        streamId: String,
        fromVersion: Long
    ): Flow<Event> = flow {
        // Create a channel for this subscription
        val channel = Channel<Event>(Channel.UNLIMITED)
        val subscriptionId = "${streamId}-${System.currentTimeMillis()}"
        eventSubscribers[subscriptionId] = channel
        
        try {
            // First, emit all existing events
            val existingEvents = readStream(streamId, fromVersion)
            existingEvents.forEach { emit(it) }
            
            // Then listen for new events
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
        // Create a channel for this subscription
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
    
    override suspend fun getStreamVersion(streamId: String): Long {
        return streamVersionCache[streamId] ?: withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                getStreamVersionFromDb(conn, streamId)
            }
        }
    }
    
    override suspend fun saveSnapshot(
        streamId: String,
        snapshot: Snapshot,
        version: Long
    ) {
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement("""
                    INSERT INTO snapshots (
                        stream_id, version, timestamp, data, metadata
                    ) VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (stream_id, version) DO UPDATE SET
                        timestamp = EXCLUDED.timestamp,
                        data = EXCLUDED.data,
                        metadata = EXCLUDED.metadata
                """).use { stmt ->
                    stmt.setString(1, streamId)
                    stmt.setLong(2, version)
                    stmt.setTimestamp(3, java.sql.Timestamp.from(snapshot.timestamp))
                    stmt.setBytes(4, snapshot.data)
                    stmt.setString(5, Json.encodeToString(snapshot.metadata))
                    stmt.executeUpdate()
                }
            }
        }
    }
    
    override suspend fun getLatestSnapshot(streamId: String): Snapshot? = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                SELECT version, timestamp, data, metadata
                FROM snapshots
                WHERE stream_id = ?
                ORDER BY version DESC
                LIMIT 1
            """).use { stmt ->
                stmt.setString(1, streamId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        Snapshot(
                            streamId = streamId,
                            version = rs.getLong("version"),
                            timestamp = rs.getTimestamp("timestamp").toInstant(),
                            data = rs.getBytes("data"),
                            metadata = Json.decodeFromString(rs.getString("metadata"))
                        )
                    } else null
                }
            }
        }
    }
    
    // Private helper methods
    
    private fun initializeSchema() {
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                // Events table
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS events (
                        event_id VARCHAR(255) PRIMARY KEY,
                        event_type VARCHAR(255) NOT NULL,
                        stream_id VARCHAR(255) NOT NULL,
                        version BIGINT NOT NULL,
                        timestamp TIMESTAMP NOT NULL,
                        data BYTEA NOT NULL,
                        metadata JSONB,
                        UNIQUE(stream_id, version)
                    )
                """)
                
                // Create indexes
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_stream_version ON events (stream_id, version)")
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_event_type ON events (event_type)")
                
                // Snapshots table
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS snapshots (
                        stream_id VARCHAR(255) NOT NULL,
                        version BIGINT NOT NULL,
                        timestamp TIMESTAMP NOT NULL,
                        data BYTEA NOT NULL,
                        metadata JSONB,
                        PRIMARY KEY (stream_id, version)
                    )
                """)
                
                // Stream versions table
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS stream_versions (
                        stream_id VARCHAR(255) PRIMARY KEY,
                        version BIGINT NOT NULL
                    )
                """)
            }
        }
    }
    
    private fun getStreamVersionFromDb(conn: java.sql.Connection, streamId: String): Long {
        conn.prepareStatement("""
            SELECT version FROM stream_versions WHERE stream_id = ?
        """).use { stmt ->
            stmt.setString(1, streamId)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) rs.getLong("version") else -1L
            }
        }
    }
    
    private fun updateStreamVersion(conn: java.sql.Connection, streamId: String, version: Long) {
        conn.prepareStatement("""
            INSERT INTO stream_versions (stream_id, version) 
            VALUES (?, ?)
            ON CONFLICT (stream_id) DO UPDATE SET version = EXCLUDED.version
        """).use { stmt ->
            stmt.setString(1, streamId)
            stmt.setLong(2, version)
            stmt.executeUpdate()
        }
    }
    
    private fun setupEventSubscriptions() {
        // Subscribe to a special agent that listens for event messages
        kafkaCommHub.subscribeAgent("event-store") { comm ->
            if (comm.type == CommType.DATA && comm.data["is_event"] == "true") {
                GlobalScope.launch {
                    try {
                        val event = deserializeEventFromComm(comm)
                        // Distribute to all subscribers
                        eventSubscribers.values.forEach { channel ->
                            channel.send(event)
                        }
                    } catch (e: Exception) {
                        logger.error("Failed to process event from Kafka", e)
                    }
                }
            }
        }
    }
    
    private suspend fun publishEventToKafka(event: Event) {
        val comm = Comm(
            content = "Event: ${event.eventType} for stream ${event.streamId}",
            from = "event-store",
            type = CommType.DATA,
            role = CommRole.SYSTEM,
            data = mapOf(
                "is_event" to "true",
                "event_id" to event.eventId,
                "event_type" to event.eventType,
                "stream_id" to event.streamId,
                "version" to event.version.toString(),
                "timestamp" to event.timestamp.toString(),
                "data" to event.toProto().encodeToString()
            )
        )
        
        kafkaCommHub.send(comm)
        
        // Also broadcast to a stream-specific topic
        val streamComm = comm.copy(
            to = "stream-${event.streamId}"
        )
        kafkaCommHub.send(streamComm)
    }
    
    private fun deserializeEvent(
        eventId: String,
        eventType: String,
        streamId: String,
        version: Long,
        timestamp: Instant,
        data: ByteArray,
        metadata: EventMetadata
    ): Event {
        // For now, return a generic event implementation
        // In real implementation, you would deserialize based on eventType
        return GenericEvent(
            eventId = eventId,
            eventType = eventType,
            streamId = streamId,
            version = version,
            timestamp = timestamp,
            metadata = metadata,
            data = data
        )
    }
    
    private fun deserializeEventFromComm(comm: Comm): Event {
        return GenericEvent(
            eventId = comm.data["event_id"] as String,
            eventType = comm.data["event_type"] as String,
            streamId = comm.data["stream_id"] as String,
            version = (comm.data["version"] as String).toLong(),
            timestamp = Instant.parse(comm.data["timestamp"] as String),
            metadata = EventMetadata(
                userId = comm.from,
                correlationId = comm.id
            ),
            data = (comm.data["data"] as String).toByteArray()
        )
    }
    
    fun close() {
        kafkaCommHub.unsubscribeAgent("event-store")
        eventSubscribers.values.forEach { it.close() }
        eventSubscribers.clear()
    }
}

/**
 * Configuration for the event store
 */
data class EventStoreConfig(
    val topicPrefix: String = "events",
    val snapshotFrequency: Int = 100,
    val contentType: String = "application/json"
)

// Helper implementation of Event with version
private fun Event.withVersion(version: Long): Event {
    return when (this) {
        is GenericEvent -> copy(version = version)
        else -> GenericEvent(
            eventId = eventId,
            eventType = eventType,
            streamId = streamId,
            version = version,
            timestamp = timestamp,
            metadata = metadata,
            data = toProto()
        )
    }
}

/**
 * Generic event implementation for deserialization
 */
data class GenericEvent(
    override val eventId: String,
    override val eventType: String,
    override val streamId: String,
    override val version: Long,
    override val timestamp: Instant,
    override val metadata: EventMetadata,
    val data: ByteArray
) : Event {
    override fun toProto(): ByteArray = data
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GenericEvent) return false
        return eventId == other.eventId
    }
    
    override fun hashCode(): Int = eventId.hashCode()
}

// Extension function to encode ByteArray to String
private fun ByteArray.encodeToString(): String = 
    java.util.Base64.getEncoder().encodeToString(this)

// Extension function to decode String to ByteArray
private fun String.toByteArray(): ByteArray = 
    java.util.Base64.getDecoder().decode(this)