package io.github.noailabs.spice.commhub

import io.github.noailabs.spice.Comm
import io.github.noailabs.spice.CommResult
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory implementation of CommBackend
 * Suitable for single-instance deployments and testing
 */
class InMemoryCommBackend(
    private val config: InMemoryBackendConfig = InMemoryBackendConfig()
) : CommBackend {
    
    private val channels = ConcurrentHashMap<String, Channel<Comm>>()
    private val history = ConcurrentLinkedDeque<Comm>()
    private val messageFlow = MutableSharedFlow<Comm>(
        replay = 0,
        extraBufferCapacity = config.flowBufferCapacity
    )
    private val pendingCounts = ConcurrentHashMap<String, AtomicInteger>()
    
    override suspend fun send(comm: Comm): CommResult {
        return try {
            // Store in history
            storeHistory(comm)
            
            // Route to recipient
            comm.to?.let { recipient ->
                val channel = channels.computeIfAbsent(recipient) {
                    Channel(capacity = config.channelCapacity)
                }
                
                // Track pending count
                pendingCounts.computeIfAbsent(recipient) { AtomicInteger(0) }
                    .incrementAndGet()
                
                // Send with timeout to prevent blocking
                withTimeoutOrNull(config.sendTimeoutMs) {
                    channel.send(comm)
                } ?: return CommResult.failure("Send timeout to $recipient")
                
                // Emit to flow for subscribers
                messageFlow.emit(comm)
            }
            
            CommResult.success(comm.id, listOfNotNull(comm.to))
        } catch (e: Exception) {
            CommResult.failure("Failed to send: ${e.message}")
        }
    }
    
    override suspend fun sendBatch(comms: List<Comm>): List<CommResult> {
        return coroutineScope {
            comms.map { comm ->
                async { send(comm) }
            }.awaitAll()
        }
    }
    
    override suspend fun receive(agentId: String): Comm? {
        val channel = channels[agentId] ?: return null
        
        return channel.tryReceive().getOrNull()?.also {
            pendingCounts[agentId]?.decrementAndGet()
        }
    }
    
    override suspend fun receiveTimeout(agentId: String, timeoutMs: Long): Comm? {
        val channel = channels[agentId] ?: return null
        
        return withTimeoutOrNull(timeoutMs) {
            channel.receive().also {
                pendingCounts[agentId]?.decrementAndGet()
            }
        }
    }
    
    override fun subscribe(agentId: String): Flow<Comm> {
        // Ensure channel exists
        channels.computeIfAbsent(agentId) {
            Channel(capacity = config.channelCapacity)
        }
        
        return messageFlow.filter { comm ->
            comm.to == agentId
        }
    }
    
    override fun subscribePattern(pattern: String): Flow<Comm> {
        val regex = pattern.toRegex()
        return messageFlow.filter { comm ->
            comm.to?.matches(regex) == true
        }
    }
    
    override suspend fun getPendingCount(agentId: String): Int {
        return pendingCounts[agentId]?.get() ?: 0
    }
    
    override suspend fun clear(agentId: String) {
        channels[agentId]?.let { channel ->
            // Drain the channel
            while (!channel.isEmpty) {
                channel.tryReceive()
            }
        }
        pendingCounts[agentId]?.set(0)
    }
    
    override suspend fun storeHistory(comm: Comm) {
        history.addLast(comm)
        
        // Trim history if needed
        while (history.size > config.maxHistorySize) {
            history.removeFirst()
        }
    }
    
    override suspend fun getHistory(
        agentId: String?,
        limit: Int,
        offset: Int
    ): List<Comm> {
        val filtered = if (agentId != null) {
            history.filter { it.from == agentId || it.to == agentId }
        } else {
            history.toList()
        }
        
        return filtered
            .drop(offset)
            .take(limit)
    }
    
    override suspend fun health(): BackendHealth {
        val totalPending = pendingCounts.values.sumOf { it.get() }
        
        return BackendHealth(
            healthy = true,
            latencyMs = 0, // In-memory is essentially instant
            pendingMessages = totalPending,
            details = mapOf(
                "activeChannels" to channels.size,
                "historySize" to history.size
            )
        )
    }
    
    override suspend fun close() {
        // Close all channels
        channels.values.forEach { it.close() }
        channels.clear()
        
        // Clear history
        history.clear()
        
        // Clear pending counts
        pendingCounts.clear()
    }
}

/**
 * Configuration for in-memory backend
 */
data class InMemoryBackendConfig(
    override val name: String = "in-memory",
    val channelCapacity: Int = 1000,
    val flowBufferCapacity: Int = 100,
    val maxHistorySize: Int = 10_000,
    val sendTimeoutMs: Long = 5_000
) : BackendConfig {
    override val properties: Map<String, Any> = mapOf(
        "channelCapacity" to channelCapacity,
        "flowBufferCapacity" to flowBufferCapacity,
        "maxHistorySize" to maxHistorySize,
        "sendTimeoutMs" to sendTimeoutMs
    )
}

/**
 * Factory for in-memory backend
 */
class InMemoryBackendFactory : CommBackendFactory {
    override fun create(config: BackendConfig): CommBackend {
        val inMemoryConfig = InMemoryBackendConfig(
            name = config.name,
            channelCapacity = config.properties["channelCapacity"] as? Int ?: 1000,
            flowBufferCapacity = config.properties["flowBufferCapacity"] as? Int ?: 100,
            maxHistorySize = config.properties["maxHistorySize"] as? Int ?: 10_000,
            sendTimeoutMs = config.properties["sendTimeoutMs"] as? Long ?: 5_000
        )
        return InMemoryCommBackend(inMemoryConfig)
    }
    
    override fun supports(type: String): Boolean {
        return type == "in-memory" || type == "memory"
    }
}