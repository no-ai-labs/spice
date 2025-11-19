package io.github.noailabs.spice.event

import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.eventbus.EventEnvelope
import io.github.noailabs.spice.eventbus.EventMetadata
import io.github.noailabs.spice.eventbus.dlq.DeadLetterQueue
import io.github.noailabs.spice.eventbus.dlq.InMemoryDeadLetterQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.WakeupException
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.time.Duration as JavaDuration
import java.util.Properties
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 🚌 Kafka Tool Call Event Bus
 *
 * High-throughput, distributed event bus for tool call lifecycle events using Kafka.
 * Enables multi-instance, multi-agent orchestration with strong durability guarantees.
 *
 * **Architecture:**
 * ```
 * Agent A (Instance 1) → publish → Kafka Topic
 *                                       ↓
 *                              Agent B (Instance 2) ← subscribe
 * ```
 *
 * **Features:**
 * - High-throughput distributed pub/sub
 * - Strong durability (replicated across brokers)
 * - Stable consumer groups for load balancing and offset persistence
 * - Configurable starting position (latest vs earliest)
 * - Idempotent producer (prevents duplicates)
 *
 * **Requirements:**
 * - Kafka 2.0+ cluster
 * - Proper network connectivity to bootstrap servers
 * - Optional: SASL/SSL configuration for secure clusters
 *
 * **Use Cases:**
 * - High-scale production deployments
 * - Multi-region agent orchestration
 * - Event sourcing and replay
 * - Long-term audit trail
 *
 * **Consumer Group:**
 * - Use stable group ID (defaults to clientId) so offsets persist across restarts
 * - Multiple instances with same group ID share load
 * - Different group IDs receive all events independently
 *
 * **Auto Offset Reset:**
 * - "latest": Start from newest events (default for production)
 * - "earliest": Replay all events from beginning (useful for new consumers)
 *
 * @property bootstrapServers Kafka bootstrap servers (e.g., "localhost:9092")
 * @property topic Kafka topic name (default: "spice.toolcall.events")
 * @property clientId Kafka client ID
 * @property consumerGroup Consumer group ID (defaults to clientId for offset persistence)
 * @property autoOffsetReset "latest" or "earliest" (default: "latest")
 * @property config Event bus configuration
 * @property pollTimeout Consumer poll timeout
 * @property acks Producer acks setting (default: "all")
 * @property securityProtocol Optional security protocol (SASL_SSL, etc.)
 * @property saslMechanism Optional SASL mechanism (PLAIN, SCRAM, etc.)
 * @property saslJaasConfig Optional SASL JAAS configuration
 * @property deadLetterQueue Dead letter queue for failed events (default: InMemoryDeadLetterQueue)
 * @property onDLQWrite Optional callback when event is written to DLQ
 *
 * @since 1.0.0
 */
class KafkaToolCallEventBus(
    private val bootstrapServers: String,
    private val topic: String = "spice.toolcall.events",
    private val clientId: String = "spice-toolcall-eventbus",
    private val consumerGroup: String = clientId,
    private val autoOffsetReset: String = "latest",
    config: EventBusConfig = EventBusConfig.DEFAULT,
    private val pollTimeout: Duration = 1.seconds,
    private val acks: String = "all",
    private val securityProtocol: String? = null,
    private val saslMechanism: String? = null,
    private val saslJaasConfig: String? = null,
    private val deadLetterQueue: DeadLetterQueue = InMemoryDeadLetterQueue(),
    private val onDLQWrite: ((String, String) -> Unit)? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "_type"
    }
) : AbstractToolCallEventBus(config) {

    private val producer = KafkaProducer<String, String>(producerProperties())

    // Background consumer job
    private var consumerJob: Job? = null

    // Additional metrics
    private var errors = 0L
    private var dlqCount = 0L

    init {
        // Start background consumer
        startConsumer()
    }

    /**
     * Publish event to Kafka topic.
     */
    override suspend fun doPublish(event: ToolCallEvent) {
        val payload = json.encodeToString<ToolCallEvent>(event)
        val key = event.toolCall.id  // Use toolCallId as key for partitioning

        val record = ProducerRecord(topic, key, payload)

        suspendCancellableCoroutine<org.apache.kafka.clients.producer.RecordMetadata> { cont ->
            producer.send(record) { meta, exception ->
                if (exception != null) {
                    cont.resumeWithException(exception)
                } else {
                    cont.resume(meta)
                }
            }
        }
    }

    /**
     * Start background Kafka consumer.
     */
    private fun startConsumer() {
        consumerJob = scope.launch {
            val consumer = KafkaConsumer<String, String>(consumerProperties())
            consumer.subscribe(listOf(topic))

            try {
                while (isActive) {
                    val records = consumer.poll(JavaDuration.ofMillis(pollTimeout.inWholeMilliseconds))

                    for (record in records) {
                        when (val decoded = SpiceResult.catching {
                            json.decodeFromString<ToolCallEvent>(record.value())
                        }) {
                            is SpiceResult.Success -> {
                                emitToSubscribers(decoded.value)
                            }
                            is SpiceResult.Failure -> {
                                handleDecodingError(record, decoded.error)
                            }
                        }
                    }
                }
            } catch (e: WakeupException) {
                // Ignore wakeup exception for graceful shutdown
            } finally {
                consumer.close()
            }
        }
    }

    /**
     * Handle decoding errors by sending to DLQ.
     */
    private suspend fun handleDecodingError(
        record: ConsumerRecord<String, String>,
        error: io.github.noailabs.spice.error.SpiceError
    ) {
        // Record error metrics
        if (config.enableMetrics) {
            mutex.withLock {
                errors++
                dlqCount++
            }
        }

        // Create EventEnvelope for DLQ from raw Kafka record
        val envelope = EventEnvelope(
            channelName = "kafka.toolcall.events",
            eventType = "ToolCallEvent",
            schemaVersion = "1.0.0",
            payload = record.value(),
            metadata = EventMetadata(
                custom = mapOf(
                    "kafka.key" to (record.key() ?: ""),
                    "kafka.topic" to record.topic(),
                    "kafka.partition" to record.partition().toString(),
                    "kafka.offset" to record.offset().toString(),
                    "kafka.timestamp" to record.timestamp().toString()
                )
            ),
            timestamp = Clock.System.now(),
            correlationId = record.key()
        )

        // Send to DLQ
        val reason = "Deserialization failed: ${error.message} (type: ${error.code})"
        scope.launch {
            deadLetterQueue.send(envelope, reason, error.cause)
            onDLQWrite?.invoke(record.key() ?: "unknown", reason)
        }

        // Log error with record metadata
        System.err.println(
            "[KafkaToolCallEventBus] Failed to decode event at " +
            "topic=${record.topic()} partition=${record.partition()} offset=${record.offset()}: " +
            "$reason -> Sent to DLQ"
        )
    }

    /**
     * Close event bus and stop consumer.
     */
    suspend fun close() {
        consumerJob?.cancelAndJoin()
        producer.close()
        scope.cancel()
    }

    /**
     * Producer configuration.
     */
    private fun producerProperties(): Properties {
        return Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(ProducerConfig.CLIENT_ID_CONFIG, "$clientId-producer")
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.ACKS_CONFIG, acks)
            put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")
            put(ProducerConfig.RETRIES_CONFIG, "3")

            // Security config
            securityProtocol?.let { put("security.protocol", it) }
            saslMechanism?.let { put("sasl.mechanism", it) }
            saslJaasConfig?.let { put("sasl.jaas.config", it) }
        }
    }

    /**
     * Consumer configuration.
     */
    private fun consumerProperties(): Properties {
        return Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(ConsumerConfig.CLIENT_ID_CONFIG, "$clientId-consumer")
            put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroup)
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset)
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")

            // Security config
            securityProtocol?.let { put("security.protocol", it) }
            saslMechanism?.let { put("sasl.mechanism", it) }
            saslJaasConfig?.let { put("sasl.jaas.config", it) }
        }
    }
}
