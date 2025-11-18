package io.github.noailabs.spice.eventbus

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.UUID

/**
 * 📦 Event Envelope
 *
 * Wrapper for all events with versioning, metadata, and correlation info.
 * Enables schema evolution and backward/forward compatibility.
 *
 * **Versioning Strategy:**
 * - `schemaVersion`: Event type version (semantic versioning: MAJOR.MINOR.PATCH)
 * - Consumers can handle multiple versions
 * - Unknown versions are routed to dead letter queue
 *
 * **Evolution Patterns:**
 * 1. **Backward compatible**: Add optional fields (old consumers ignore new fields)
 * 2. **Forward compatible**: Old events missing new fields (defaults used by new consumers)
 * 3. **Breaking changes**: Increment major version, provide migration path
 *
 * **Correlation & Causation:**
 * - `correlationId`: Groups related events in distributed workflow (e.g., all events in one graph execution)
 * - `causationId`: Direct cause-effect relationship (e.g., NodeCompleted caused by NodeStarted)
 *
 * **Example:**
 * ```kotlin
 * val envelope = EventEnvelope(
 *     channelName = "spice.graph.lifecycle",
 *     eventType = "io.github.noailabs.spice.eventbus.events.GraphLifecycleEvent.Started",
 *     schemaVersion = "1.0.0",
 *     payload = json.encodeToString(event),
 *     metadata = EventMetadata(
 *         source = "graph-runner",
 *         userId = "user123",
 *         traceId = "trace-abc"
 *     ),
 *     correlationId = "graph-run-xyz"
 * )
 * ```
 *
 * @property id Unique event ID (UUID)
 * @property channelName Channel this event was published to
 * @property eventType Fully qualified event type name
 * @property schemaVersion Schema version (semantic versioning)
 * @property payload JSON serialized event
 * @property metadata Event metadata (userId, traceId, etc.)
 * @property timestamp Event creation timestamp
 * @property correlationId Correlation ID for distributed workflows
 * @property causationId Causation ID for cause-effect relationships
 *
 * @since 1.0.0-alpha-5
 * @author Spice Framework
 */
@Serializable
data class EventEnvelope(
    val id: String = generateEventId(),
    val channelName: String,
    val eventType: String,
    val schemaVersion: String = "1.0.0",
    val payload: String,
    val metadata: EventMetadata,
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant = Clock.System.now(),
    val correlationId: String? = null,
    val causationId: String? = null
) {
    init {
        require(id.isNotBlank()) { "Event ID must not be blank" }
        require(channelName.isNotBlank()) { "Channel name must not be blank" }
        require(eventType.isNotBlank()) { "Event type must not be blank" }
        require(schemaVersion.matches(SEMVER_REGEX)) {
            "Schema version must follow semantic versioning (e.g., 1.0.0)"
        }
        require(payload.isNotBlank()) { "Payload must not be blank" }
    }

    companion object {
        private val SEMVER_REGEX = Regex("""^\d+\.\d+\.\d+$""")

        /**
         * Generate unique event ID
         */
        fun generateEventId(): String = UUID.randomUUID().toString()
    }
}

/**
 * 🏷️ Event Metadata
 *
 * Contextual information for events (who, where, when, why).
 *
 * **Metadata Fields:**
 * - `source`: Component that published the event (e.g., "graph-runner", "agent-1")
 * - `userId`: User ID for user-initiated events
 * - `tenantId`: Tenant ID for multi-tenant systems
 * - `traceId`: Distributed tracing ID (OpenTelemetry compatible)
 * - `spanId`: Span ID within trace
 * - `priority`: Event priority (0 = normal, higher = more important)
 * - `ttl`: Expiry timestamp (Unix milliseconds)
 * - `custom`: Custom key-value metadata
 *
 * **Example:**
 * ```kotlin
 * val metadata = EventMetadata(
 *     source = "agent-booking",
 *     userId = "user123",
 *     tenantId = "tenant-acme",
 *     traceId = "trace-abc-123",
 *     priority = 10,
 *     custom = mapOf(
 *         "region" to "us-west-1",
 *         "version" to "1.0.0"
 *     )
 * )
 * ```
 *
 * @property source Event source (component/service name)
 * @property userId User ID
 * @property tenantId Tenant ID
 * @property traceId Distributed tracing ID
 * @property spanId Span ID within trace
 * @property priority Event priority (default: 0)
 * @property ttl Expiry timestamp (Unix milliseconds)
 * @property custom Custom metadata
 *
 * @since 1.0.0-alpha-5
 * @author Spice Framework
 */
@Serializable
data class EventMetadata(
    val source: String? = null,
    val userId: String? = null,
    val tenantId: String? = null,
    val traceId: String? = null,
    val spanId: String? = null,
    val priority: Int = 0,
    val ttl: Long? = null,
    val custom: Map<String, String> = emptyMap()
) {
    companion object {
        /**
         * Empty metadata (no context)
         */
        val EMPTY = EventMetadata()
    }
}

/**
 * Instant serializer for kotlinx.datetime
 */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Instant {
        return Instant.parse(decoder.decodeString())
    }
}
