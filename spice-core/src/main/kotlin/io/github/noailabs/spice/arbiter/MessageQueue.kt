package io.github.noailabs.spice.arbiter

import io.github.noailabs.spice.SpiceMessage
import io.github.noailabs.spice.error.SpiceResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import redis.clients.jedis.JedisPool
import java.util.UUID

/**
 * Envelope representing a queued message awaiting processing.
 */
data class QueueEnvelope(
    val id: String,
    val topic: String,
    val message: SpiceMessage
)

/**
 * Simple abstraction for message queues used by the Arbiter.
 */
interface MessageQueue {
    suspend fun publish(topic: String, message: SpiceMessage): SpiceResult<Unit>
    suspend fun consume(topic: String, handler: suspend (QueueEnvelope) -> Unit)
    suspend fun ack(topic: String, id: String)
    suspend fun fail(topic: String, id: String, reason: String? = null)
    suspend fun shutdown()
}

class InMemoryMessageQueue : MessageQueue {
    private val channels = mutableMapOf<String, Channel<QueueEnvelope>>()
    private val lock = Mutex()
    @Volatile private var running = true

    private suspend fun channel(topic: String): Channel<QueueEnvelope> = lock.withLock {
        channels.getOrPut(topic) { Channel(Channel.BUFFERED) }
    }

    override suspend fun publish(topic: String, message: SpiceMessage): SpiceResult<Unit> =
        SpiceResult.catching {
            channel(topic).send(
                QueueEnvelope(
                    id = UUID.randomUUID().toString(),
                    topic = topic,
                    message = message
                )
            )
        }

    override suspend fun consume(topic: String, handler: suspend (QueueEnvelope) -> Unit) {
        val channel = channel(topic)
        while (running) {
            val result = channel.receiveCatching()
            val envelope = result.getOrNull() ?: break
            handler(envelope)
        }
    }

    override suspend fun ack(topic: String, id: String) = Unit

    override suspend fun fail(topic: String, id: String, reason: String?) {
        // Could push to in-memory DLQ; no-op for now
    }

    override suspend fun shutdown() {
        running = false
        lock.withLock {
            channels.values.forEach { it.close() }
            channels.clear()
        }
    }
}

class RedisMessageQueue(
    private val jedisPool: JedisPool,
    private val namespace: String = "spice:mq",
    private val json: Json = Json { ignoreUnknownKeys = true }
) : MessageQueue {

    @Serializable
    private data class QueuePayload(
        val id: String,
        val message: SpiceMessage
    )

    private fun key(topic: String) = "$namespace:$topic"
    private fun deadKey(topic: String) = "$namespace:$topic:dead"
    @Volatile private var running = true

    override suspend fun publish(topic: String, message: SpiceMessage): SpiceResult<Unit> =
        SpiceResult.catching {
            val payload = QueuePayload(id = UUID.randomUUID().toString(), message = message)
            jedisPool.resource.use { jedis ->
                jedis.lpush(key(topic), json.encodeToString(payload))
            }
        }

    override suspend fun consume(topic: String, handler: suspend (QueueEnvelope) -> Unit) {
        while (running) {
            val payload = jedisPool.resource.use { jedis ->
                val response = jedis.brpop(5, key(topic))
                response?.getOrNull(1)
            } ?: continue
            val decoded = runCatching {
                json.decodeFromString(QueuePayload.serializer(), payload)
            }.getOrElse {
                jedisPool.resource.use { jedis -> jedis.lpush(deadKey(topic), payload) }
                continue
            }
            handler(
                QueueEnvelope(
                    id = decoded.id,
                    topic = topic,
                    message = decoded.message
                )
            )
        }
    }

    override suspend fun ack(topic: String, id: String) {
        // Messages are removed upon BRPOP; nothing to do
    }

    override suspend fun fail(topic: String, id: String, reason: String?) {
        jedisPool.resource.use { jedis ->
            jedis.lpush(deadKey(topic), "$id:${reason ?: "failed"}")
        }
    }

    override suspend fun shutdown() {
        running = false
    }
}
