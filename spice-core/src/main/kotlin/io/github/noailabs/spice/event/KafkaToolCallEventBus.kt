package io.github.noailabs.spice.event

import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.WakeupException
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.time.Duration as JavaDuration
import java.util.Properties
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.reflect.KClass
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
 *
 * @since 1.0.0
 */
class KafkaToolCallEventBus(
    private val bootstrapServers: String,
    private val topic: String = "spice.toolcall.events",
    private val clientId: String = "spice-toolcall-eventbus",
    private val consumerGroup: String = clientId,
    private val autoOffsetReset: String = "latest",
    private val config: EventBusConfig = EventBusConfig.DEFAULT,
    private val pollTimeout: Duration = 1.seconds,
    private val acks: String = "all",
    private val securityProtocol: String? = null,
    private val saslMechanism: String? = null,
    private val saslJaasConfig: String? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "_type"
    }
) : ToolCallEventBus {

    private val producer = KafkaProducer<String, String>(producerProperties())

    // Local event flow for subscribers on this instance
    private val eventFlow = MutableSharedFlow<ToolCallEvent>(
        replay = 0,
        extraBufferCapacity = 100
    )

    // Event history (if enabled)
    private val history = mutableListOf<ToolCallEvent>()
    private val mutex = Mutex()

    // Background consumer job
    private var consumerJob: Job? = null

    // Metrics
    private var publishCount = 0L
    private var subscriberCount = 0
    private var errors = 0L

    init {
        // Start background consumer
        startConsumer()
    }

    override suspend fun publish(event: ToolCallEvent): SpiceResult<Unit> {
        return SpiceResult.catching {
            val payload = json.encodeToString<ToolCallEvent>(event)
            val key = event.toolCall.id  // Use toolCallId as key for partitioning

            val record = ProducerRecord(topic, key, payload)

            val metadata = suspendCancellableCoroutine<org.apache.kafka.clients.producer.RecordMetadata> { cont ->
                producer.send(record) { meta, exception ->
                    if (exception != null) {
                        cont.resumeWithException(exception)
                    } else {
                        cont.resume(meta)
                    }
                }
            }

            // Store in history if enabled
            if (config.enableHistory) {
                mutex.withLock {
                    history.add(event)
                    if (history.size > config.historySize) {
                        history.removeAt(0)
                    }
                }
            }

            // Update metrics
            if (config.enableMetrics) {
                mutex.withLock {
                    publishCount++
                }
            }

            Unit
        }
    }

    override fun subscribe(): Flow<ToolCallEvent> {
        subscriberCount++
        return eventFlow
    }

    override fun subscribe(vararg eventTypes: KClass<out ToolCallEvent>): Flow<ToolCallEvent> {
        subscriberCount++
        return eventFlow.filter { event ->
            eventTypes.any { it.isInstance(event) }
        }
    }

    override fun subscribeToToolCall(toolCallId: String): Flow<ToolCallEvent> {
        subscriberCount++
        return eventFlow.filter { event ->
            event.toolCall.id == toolCallId
        }
    }

    override fun subscribeToRun(runId: String): Flow<ToolCallEvent> {
        subscriberCount++
        return eventFlow.filter { event ->
            when (event) {
                is ToolCallEvent.Emitted -> event.runId == runId
                else -> event.message.runId == runId
            }
        }
    }

    override suspend fun getHistory(limit: Int): SpiceResult<List<ToolCallEvent>> {
        return mutex.withLock {
            if (!config.enableHistory) {
                return@withLock SpiceResult.success(emptyList())
            }
            val events = history.takeLast(limit).reversed()
            SpiceResult.success(events)
        }
    }

    override suspend fun getToolCallHistory(toolCallId: String): SpiceResult<List<ToolCallEvent>> {
        return mutex.withLock {
            if (!config.enableHistory) {
                return@withLock SpiceResult.success(emptyList())
            }
            val events = history.filter { it.toolCall.id == toolCallId }
            SpiceResult.success(events)
        }
    }

    override suspend fun clearHistory(): SpiceResult<Unit> {
        return mutex.withLock {
            history.clear()
            SpiceResult.success(Unit)
        }
    }

    override suspend fun getSubscriberCount(): Int {
        return subscriberCount
    }

    /**
     * Start background Kafka consumer
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
                                eventFlow.emit(decoded.value)
                            }
                            is SpiceResult.Failure -> {
                                // Record error metrics
                                if (config.enableMetrics) {
                                    mutex.withLock { errors++ }
                                }

                                // Log error with record metadata
                                System.err.println(
                                    "[KafkaToolCallEventBus] Failed to decode event at " +
                                    "topic=${record.topic()} partition=${record.partition()} offset=${record.offset()}: " +
                                    "${decoded.error.message} (type: ${decoded.error.code})"
                                )

                                // Offset auto-commits, so malformed event is skipped
                                // TODO: Add dead letter handler support
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
     * Close event bus and stop consumer
     */
    suspend fun close() {
        consumerJob?.cancelAndJoin()
        producer.close()
        scope.cancel()
    }

    /**
     * Producer configuration
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
     * Consumer configuration
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
