package io.github.noailabs.spice.eventsourcing

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import java.time.Instant
import java.util.Base64

/**
 * Event Serialization System for Spice EventSourcing
 * 
 * Provides pluggable serialization strategies for events
 */

/**
 * Core interface for event serialization
 */
interface EventSerializer {
    /**
     * Serialize an event to bytes
     */
    fun serialize(event: Event): SerializedEvent
    
    /**
     * Deserialize bytes to an event
     */
    fun deserialize(serialized: SerializedEvent): Event
    
    /**
     * Get the content type this serializer produces
     */
    val contentType: String
}

/**
 * Container for serialized event data
 */
data class SerializedEvent(
    val eventType: String,
    val data: ByteArray,
    val contentType: String,
    val schemaVersion: Int = 1
) {
    fun toBase64(): String = Base64.getEncoder().encodeToString(data)
    
    companion object {
        fun fromBase64(eventType: String, base64: String, contentType: String): SerializedEvent {
            return SerializedEvent(
                eventType = eventType,
                data = Base64.getDecoder().decode(base64),
                contentType = contentType
            )
        }
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SerializedEvent) return false
        return eventType == other.eventType && 
               data.contentEquals(other.data) && 
               contentType == other.contentType
    }
    
    override fun hashCode(): Int {
        var result = eventType.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + contentType.hashCode()
        return result
    }
}

/**
 * JSON-based event serializer using Kotlinx Serialization
 */
class JsonEventSerializer(
    private val json: Json = Json {
        serializersModule = eventSerializersModule
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }
) : EventSerializer {
    
    override val contentType = "application/json"
    
    override fun serialize(event: Event): SerializedEvent {
        val eventWrapper = EventWrapper(
            eventType = event.eventType,
            eventId = event.eventId,
            streamId = event.streamId,
            version = event.version,
            timestamp = event.timestamp.toEpochMilli(),
            metadata = event.metadata,
            data = when (event) {
                is DomainEvent -> serializeDomainEvent(event)
                else -> JsonObject(emptyMap()) // Generic event
            }
        )
        
        val jsonString = json.encodeToString(eventWrapper)
        
        return SerializedEvent(
            eventType = event.eventType,
            data = jsonString.toByteArray(),
            contentType = contentType
        )
    }
    
    override fun deserialize(serialized: SerializedEvent): Event {
        val jsonString = String(serialized.data)
        val wrapper = json.decodeFromString<EventWrapper>(jsonString)
        
        return when {
            EventTypeRegistry.getClass(wrapper.eventType) != null -> {
                // Use registered deserializer
                deserializeRegisteredEvent(wrapper)
            }
            wrapper.data.containsKey("aggregateId") -> {
                // Assume it's a domain event
                deserializeDomainEvent(wrapper)
            }
            else -> {
                // Generic event
                GenericEvent(
                    eventId = wrapper.eventId,
                    eventType = wrapper.eventType,
                    streamId = wrapper.streamId,
                    version = wrapper.version,
                    timestamp = Instant.ofEpochMilli(wrapper.timestamp),
                    metadata = wrapper.metadata,
                    data = wrapper.data.toString().toByteArray()
                )
            }
        }
    }
    
    private fun serializeDomainEvent(event: DomainEvent): JsonObject {
        return buildJsonObject {
            put("aggregateId", event.aggregateId)
            put("aggregateType", event.aggregateType)
            
            // Add event-specific fields
            when (event) {
                is EntityCreatedEvent -> {
                    put("createdBy", event.createdBy)
                }
                is EntityUpdatedEvent -> {
                    put("updatedBy", event.updatedBy)
                    put("changes", JsonObject(event.changes.mapValues { 
                        JsonPrimitive(it.value?.toString())
                    }))
                }
                is EntityDeletedEvent -> {
                    put("deletedBy", event.deletedBy)
                    event.reason?.let { put("reason", it) }
                }
                is StateTransitionEvent -> {
                    put("fromState", event.fromState)
                    put("toState", event.toState)
                    put("triggeredBy", event.triggeredBy)
                }
                else -> {
                    // Custom domain events - use reflection or registration
                    // For now, just include basic fields
                }
            }
        }
    }
    
    private fun deserializeDomainEvent(wrapper: EventWrapper): Event {
        val data = wrapper.data
        val aggregateId = data["aggregateId"]?.jsonPrimitive?.content ?: ""
        val aggregateType = data["aggregateType"]?.jsonPrimitive?.content ?: ""
        
        return when {
            wrapper.eventType.endsWith("Created") -> {
                EntityCreatedEvent(
                    aggregateId = aggregateId,
                    aggregateType = aggregateType,
                    createdBy = data["createdBy"]?.jsonPrimitive?.content ?: "system",
                    timestamp = Instant.ofEpochMilli(wrapper.timestamp)
                )
            }
            wrapper.eventType.endsWith("Updated") -> {
                EntityUpdatedEvent(
                    aggregateId = aggregateId,
                    aggregateType = aggregateType,
                    updatedBy = data["updatedBy"]?.jsonPrimitive?.content ?: "system",
                    changes = data["changes"]?.jsonObject?.mapValues { 
                        it.value.jsonPrimitive.content 
                    } ?: emptyMap(),
                    timestamp = Instant.ofEpochMilli(wrapper.timestamp)
                )
            }
            wrapper.eventType.endsWith("StateChanged") -> {
                StateTransitionEvent(
                    aggregateId = aggregateId,
                    aggregateType = aggregateType,
                    fromState = data["fromState"]?.jsonPrimitive?.content ?: "",
                    toState = data["toState"]?.jsonPrimitive?.content ?: "",
                    triggeredBy = data["triggeredBy"]?.jsonPrimitive?.content ?: "system",
                    timestamp = Instant.ofEpochMilli(wrapper.timestamp)
                )
            }
            else -> {
                // Generic domain event
                object : BaseDomainEvent(
                    aggregateId = aggregateId,
                    aggregateType = aggregateType,
                    eventType = wrapper.eventType,
                    timestamp = Instant.ofEpochMilli(wrapper.timestamp)
                ) {
                    override fun toProto(): ByteArray = data.toString().toByteArray()
                }
            }
        }
    }
    
    private fun deserializeRegisteredEvent(wrapper: EventWrapper): Event {
        // This would use registered deserializers
        // For now, fall back to domain event deserialization
        return deserializeDomainEvent(wrapper)
    }
}

/**
 * Protobuf-based event serializer
 * 
 * Note: This is a placeholder. Real implementation would use protobuf compiler
 */
class ProtobufEventSerializer : EventSerializer {
    
    override val contentType = "application/x-protobuf"
    
    override fun serialize(event: Event): SerializedEvent {
        // In real implementation, would use generated protobuf classes
        // For now, use the event's toProto() method
        return SerializedEvent(
            eventType = event.eventType,
            data = event.toProto(),
            contentType = contentType
        )
    }
    
    override fun deserialize(serialized: SerializedEvent): Event {
        // In real implementation, would use protobuf parser
        // For now, create a generic event
        return GenericEvent(
            eventId = java.util.UUID.randomUUID().toString(),
            eventType = serialized.eventType,
            streamId = "unknown",
            version = -1,
            timestamp = Instant.now(),
            metadata = EventMetadata(
                userId = "system",
                correlationId = java.util.UUID.randomUUID().toString()
            ),
            data = serialized.data
        )
    }
}

/**
 * Composite serializer that can handle multiple formats
 */
class CompositeEventSerializer(
    private val serializers: Map<String, EventSerializer> = mapOf(
        "application/json" to JsonEventSerializer(),
        "application/x-protobuf" to ProtobufEventSerializer()
    ),
    private val defaultContentType: String = "application/json"
) : EventSerializer {
    
    override val contentType = defaultContentType
    
    override fun serialize(event: Event): SerializedEvent {
        val serializer = serializers[contentType] 
            ?: throw IllegalStateException("No serializer for content type: $contentType")
        return serializer.serialize(event)
    }
    
    override fun deserialize(serialized: SerializedEvent): Event {
        val serializer = serializers[serialized.contentType]
            ?: throw IllegalStateException("No serializer for content type: ${serialized.contentType}")
        return serializer.deserialize(serialized)
    }
    
    fun withContentType(contentType: String): CompositeEventSerializer {
        return CompositeEventSerializer(serializers, contentType)
    }
}

/**
 * Internal wrapper for JSON serialization
 */
@Serializable
private data class EventWrapper(
    val eventType: String,
    val eventId: String,
    val streamId: String,
    val version: Long,
    val timestamp: Long,
    val metadata: EventMetadata,
    val data: JsonObject
)

/**
 * Serializers module for Kotlinx Serialization
 */
private val eventSerializersModule = SerializersModule {
    // Register polymorphic serializers here if needed
}

/**
 * Factory for creating event serializers
 */
object EventSerializerFactory {
    
    fun json(config: JsonBuilder.() -> Unit = {}): EventSerializer {
        val json = Json {
            serializersModule = eventSerializersModule
            ignoreUnknownKeys = true
            prettyPrint = false
            encodeDefaults = true
            config()
        }
        return JsonEventSerializer(json)
    }
    
    fun protobuf(): EventSerializer {
        return ProtobufEventSerializer()
    }
    
    fun composite(vararg pairs: Pair<String, EventSerializer>): EventSerializer {
        return CompositeEventSerializer(pairs.toMap())
    }
}