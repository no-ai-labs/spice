package io.github.noailabs.spice.eventsourcing

import java.time.Instant
import java.util.UUID

/**
 * Enhanced Event Type System for Spice EventSourcing
 * 
 * Provides type-safe event hierarchies with better domain modeling
 */

/**
 * Base interface for all domain events
 * 
 * Domain events represent facts that happened in the past
 */
sealed interface DomainEvent : Event {
    /**
     * The aggregate that this event belongs to
     */
    val aggregateId: String
    
    /**
     * Type of aggregate (e.g., "Order", "Policy", "Customer")
     */
    val aggregateType: String
    
    /**
     * Business timestamp when the event occurred
     */
    val occurredAt: Instant
        get() = timestamp
    
    /**
     * Get the stream ID for this event
     */
    override val streamId: String
        get() = "$aggregateType-$aggregateId"
}

/**
 * Base class for implementing domain events
 */
abstract class BaseDomainEvent(
    override val aggregateId: String,
    override val aggregateType: String,
    override val eventType: String,
    override val timestamp: Instant = Instant.now(),
    override val eventId: String = UUID.randomUUID().toString(),
    override var version: Long = -1,
    override val metadata: EventMetadata = EventMetadata(
        userId = "system",
        correlationId = UUID.randomUUID().toString()
    )
) : DomainEvent

/**
 * Event categories for different bounded contexts
 */

/**
 * System events - infrastructure and technical events
 */
sealed interface SystemEvent : Event {
    val systemComponent: String
}

/**
 * Integration events - for cross-boundary communication
 */
sealed interface IntegrationEvent : Event {
    val sourceSystem: String
    val targetSystem: String?
}

/**
 * Command events - events that trigger actions
 */
sealed interface CommandEvent : Event {
    val commandId: String
    val commandType: String
}

/**
 * Notification events - for informing external systems
 */
sealed interface NotificationEvent : Event {
    val notificationType: String
    val recipients: List<String>
}

/**
 * Common domain event types
 */

/**
 * Base for entity lifecycle events
 */
abstract class EntityLifecycleEvent(
    aggregateId: String,
    aggregateType: String,
    eventType: String,
    timestamp: Instant = Instant.now()
) : BaseDomainEvent(aggregateId, aggregateType, eventType, timestamp)

/**
 * Entity created event
 */
open class EntityCreatedEvent(
    aggregateId: String,
    aggregateType: String,
    val createdBy: String,
    timestamp: Instant = Instant.now()
) : EntityLifecycleEvent(
    aggregateId = aggregateId,
    aggregateType = aggregateType,
    eventType = "${aggregateType}Created",
    timestamp = timestamp
) {
    override fun toProto(): ByteArray {
        // TODO: Implement proper protobuf serialization
        return "$eventType:$aggregateId".toByteArray()
    }
}

/**
 * Entity updated event
 */
open class EntityUpdatedEvent(
    aggregateId: String,
    aggregateType: String,
    val updatedBy: String,
    val changes: Map<String, Any?> = emptyMap(),
    timestamp: Instant = Instant.now()
) : EntityLifecycleEvent(
    aggregateId = aggregateId,
    aggregateType = aggregateType,
    eventType = "${aggregateType}Updated",
    timestamp = timestamp
) {
    override fun toProto(): ByteArray {
        // TODO: Implement proper protobuf serialization
        return "$eventType:$aggregateId".toByteArray()
    }
}

/**
 * Entity deleted event
 */
open class EntityDeletedEvent(
    aggregateId: String,
    aggregateType: String,
    val deletedBy: String,
    val reason: String? = null,
    timestamp: Instant = Instant.now()
) : EntityLifecycleEvent(
    aggregateId = aggregateId,
    aggregateType = aggregateType,
    eventType = "${aggregateType}Deleted",
    timestamp = timestamp
) {
    override fun toProto(): ByteArray {
        // TODO: Implement proper protobuf serialization
        return "$eventType:$aggregateId".toByteArray()
    }
}

/**
 * State transition event
 */
open class StateTransitionEvent(
    aggregateId: String,
    aggregateType: String,
    val fromState: String,
    val toState: String,
    val triggeredBy: String,
    timestamp: Instant = Instant.now()
) : BaseDomainEvent(
    aggregateId = aggregateId,
    aggregateType = aggregateType,
    eventType = "${aggregateType}StateChanged",
    timestamp = timestamp
) {
    override fun toProto(): ByteArray {
        // TODO: Implement proper protobuf serialization
        return "$eventType:$aggregateId:$fromState->$toState".toByteArray()
    }
}

/**
 * Value changed event for tracking specific field changes
 */
open class ValueChangedEvent<T>(
    aggregateId: String,
    aggregateType: String,
    val fieldName: String,
    val oldValue: T?,
    val newValue: T,
    val changedBy: String,
    timestamp: Instant = Instant.now()
) : BaseDomainEvent(
    aggregateId = aggregateId,
    aggregateType = aggregateType,
    eventType = "${aggregateType}${fieldName}Changed",
    timestamp = timestamp
) {
    override fun toProto(): ByteArray {
        // TODO: Implement proper protobuf serialization
        return "$eventType:$aggregateId:$fieldName".toByteArray()
    }
}

/**
 * Event metadata extensions
 */
fun EventMetadata.withTenant(tenantId: String): EventMetadata =
    copy(tenantId = tenantId)

fun EventMetadata.withCausation(causationId: String): EventMetadata =
    copy(causationId = causationId)

fun EventMetadata.withUser(userId: String): EventMetadata =
    copy(userId = userId)

/**
 * DSL for creating events
 */
inline fun <reified T : DomainEvent> domainEvent(
    block: DomainEventBuilder.() -> Unit
): T {
    val builder = DomainEventBuilder()
    builder.block()
    return builder.build()
}

class DomainEventBuilder {
    lateinit var aggregateId: String
    lateinit var aggregateType: String
    lateinit var eventType: String
    var timestamp: Instant = Instant.now()
    var metadata: EventMetadata = EventMetadata(
        userId = "system",
        correlationId = UUID.randomUUID().toString()
    )
    
    fun metadata(block: EventMetadataBuilder.() -> Unit) {
        val metadataBuilder = EventMetadataBuilder(metadata)
        metadataBuilder.block()
        metadata = metadataBuilder.build()
    }
    
    inline fun <reified T : DomainEvent> build(): T {
        // This is simplified - in real implementation would use reflection or code generation
        throw NotImplementedError("Use specific event constructors instead")
    }
}

class EventMetadataBuilder(private var metadata: EventMetadata) {
    fun userId(id: String) {
        metadata = metadata.copy(userId = id)
    }
    
    fun tenantId(id: String) {
        metadata = metadata.copy(tenantId = id)
    }
    
    fun correlationId(id: String) {
        metadata = metadata.copy(correlationId = id)
    }
    
    fun causationId(id: String) {
        metadata = metadata.copy(causationId = id)
    }
    
    fun build(): EventMetadata = metadata
}

/**
 * Event type registry for runtime type resolution
 */
object EventTypeRegistry {
    private val typeMap = mutableMapOf<String, Class<out Event>>()
    
    fun register(eventType: String, clazz: Class<out Event>) {
        typeMap[eventType] = clazz
    }
    
    fun getClass(eventType: String): Class<out Event>? = typeMap[eventType]
    
    inline fun <reified T : Event> register(eventType: String) {
        register(eventType, T::class.java)
    }
}