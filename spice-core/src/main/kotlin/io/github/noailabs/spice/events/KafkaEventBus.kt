package io.github.noailabs.spice.events

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
import java.util.Properties
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Kafka backed EventBus implementation.
 *
 * **Status:** Beta - Production ready with caveats
 *
 * Publishes all events into a single Kafka topic (default: `spice.events`) with
 * the logical Spice topic encoded as the record key. Subscribers filter the
 * multiplexed stream client-side using the same wildcard semantics that the
 * in-memory bus supports.
 *
 * **Features:**
 * - Idempotent producer enabled by default (prevents duplicate messages)
 * - Consumer group support for load balancing
 * - Auto-offset-reset set to "earliest" (processes all messages from beginning)
 * - Acks set to "all" by default (ensures message durability)
 *
 * **Requirements:**
 * - Kafka 2.0+ cluster
 * - Proper network connectivity to bootstrap servers
 * - Optional: SASL/SSL configuration for secure clusters
 *
 * **Known Limitations:**
 * - Wildcard topic matching happens client-side (all events read, then filtered)
 * - No support for exactly-once semantics (at-least-once delivery only)
 * - Automatic consumer offset commit (manual commit not supported)
 *
 * @since 1.0.0
 */
class KafkaEventBus(
    private val bootstrapServers: String,
    private val topic: String = "spice.events",
    private val clientId: String = "spice-eventbus",
    private val pollTimeout: Duration = 1.seconds,
    private val acks: String = "all",
    private val securityProtocol: String? = null,
    private val saslMechanism: String? = null,
    private val saslJaasConfig: String? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) : EventBus {

    private data class Subscription(
        val id: String,
        val topicPattern: String,
        val consumerGroup: String,
        val job: Job
    )

    private val producer = KafkaProducer<String, String>(producerProperties())
    private val subscriptions = mutableMapOf<String, Subscription>()
    private val mutex = Mutex()

    private val published = AtomicLong(0)
    private val consumed = AtomicLong(0)
    private val errors = AtomicLong(0)

    override suspend fun publish(topic: String, message: SpiceMessage): SpiceResult<String> =
        SpiceResult.catching {
            val payload = json.encodeToString(message)
            val metadata = producer.send(ProducerRecord(this.topic, topic, payload)).get()
            published.incrementAndGet()
            // Return Kafka record metadata as message ID: "topic-partition-offset"
            "${metadata.topic()}-${metadata.partition()}-${metadata.offset()}"
        }

    override suspend fun subscribe(
        topic: String,
        handler: suspend (SpiceMessage) -> Unit
    ): String = createSubscription(
        topicPattern = topic,
        consumerGroup = "spice-${UUID.randomUUID().toString().replace("-", "").take(12)}",
        handler = handler
    )

    override suspend fun subscribeWithGroup(
        topic: String,
        groupId: String,
        handler: suspend (SpiceMessage) -> Unit
    ): String = createSubscription(topic, groupId, handler)

    override suspend fun unsubscribe(subscriptionId: String): SpiceResult<Unit> = mutex.withLock {
        val subscription = subscriptions.remove(subscriptionId) ?: return@withLock SpiceResult.success(Unit)
        SpiceResult.catching {
            subscription.job.cancelAndJoin()
            Unit
        }
    }

    override suspend fun acknowledge(messageId: String, subscriptionId: String): SpiceResult<Unit> =
        SpiceResult.success(Unit)

    override suspend fun getPending(subscriptionId: String, limit: Int): List<SpiceMessage> = emptyList()

    override suspend fun close() {
        val currentSubscriptions = mutex.withLock {
            val copy = subscriptions.values.toList()
            subscriptions.clear()
            copy
        }
        currentSubscriptions.forEach { it.job.cancel() }
        currentSubscriptions.forEach { runCatching { it.job.join() } }

        // Flush pending messages and close producer safely
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            runCatching {
                producer.flush()
            }.onFailure { error ->
                System.err.println("Failed to flush Kafka producer: ${error.message}")
            }
            runCatching {
                producer.close(java.time.Duration.ofSeconds(5))
            }.onFailure { error ->
                System.err.println("Failed to close Kafka producer: ${error.message}")
            }
        }

        scope.cancel()  // Properly cancel the entire scope to stop all background consumers
    }

    override suspend fun getStats(): EventBusStats = EventBusStats(
        published = published.get(),
        consumed = consumed.get(),
        pending = 0L,
        errors = errors.get(),
        activeSubscriptions = mutex.withLock { subscriptions.size }
    )

    private suspend fun createSubscription(
        topicPattern: String,
        consumerGroup: String,
        handler: suspend (SpiceMessage) -> Unit
    ): String = mutex.withLock {
        val subscriptionId = "sub-${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val job = scope.launch {
            runConsumer(subscriptionId, topicPattern, consumerGroup, handler)
        }
        subscriptions[subscriptionId] = Subscription(
            id = subscriptionId,
            topicPattern = topicPattern,
            consumerGroup = consumerGroup,
            job = job
        )
        subscriptionId
    }

    private suspend fun runConsumer(
        subscriptionId: String,
        topicPattern: String,
        consumerGroup: String,
        handler: suspend (SpiceMessage) -> Unit
    ) {
        val properties = consumerProperties(consumerGroup)
        val consumer = KafkaConsumer<String, String>(properties)
        consumer.subscribe(listOf(topic))

        try {
            while (coroutineContext.isActive) {
                val records = consumer.poll(java.time.Duration.ofMillis(pollTimeout.inWholeMilliseconds))
                records.forEach { record ->
                    val eventTopic = record.key() ?: return@forEach
                    if (!TopicPatternMatcher.matches(eventTopic, topicPattern)) {
                        return@forEach
                    }
                    val message = runCatching {
                        json.decodeFromString(SpiceMessage.serializer(), record.value())
                    }.onFailure { errors.incrementAndGet() }.getOrNull()

                    if (message != null) {
                        handler(message)
                        consumed.incrementAndGet()
                    }
                }
            }
        } catch (ex: WakeupException) {
            // Expected on shutdown
        } finally {
            runCatching { consumer.close() }
        }
    }

    private fun producerProperties(): Properties = Properties().apply {
        put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
        put(ProducerConfig.ACKS_CONFIG, acks)
        put(ProducerConfig.CLIENT_ID_CONFIG, clientId)
        put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")  // Enable idempotent producer
        securityProtocol?.let { put("security.protocol", it) }
        saslMechanism?.let { put("sasl.mechanism", it) }
        saslJaasConfig?.let { put("sasl.jaas.config", it) }
    }

    private fun consumerProperties(groupId: String): Properties = Properties().apply {
        put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
        put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
        put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
        put(ConsumerConfig.CLIENT_ID_CONFIG, "$clientId-$groupId")
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")  // Process all messages from beginning
        put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
        securityProtocol?.let { put("security.protocol", it) }
        saslMechanism?.let { put("sasl.mechanism", it) }
        saslJaasConfig?.let { put("sasl.jaas.config", it) }
    }
}
