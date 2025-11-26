package io.github.noailabs.spice.infra.hitl

import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import io.github.noailabs.spice.hitl.HITLMetadata
import io.github.noailabs.spice.hitl.HITLResponseEvent
import io.github.noailabs.spice.hitl.HITLEventStatus
import io.github.noailabs.spice.hitl.HitlEventEmitter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * EventBus-based HITL Event Emitter
 *
 * Implementation of HitlEventEmitter that uses internal event channels
 * for communication between the graph executor and external systems.
 *
 * **Architecture:**
 * ```
 * Graph Executor                    External System
 *      |                                  |
 *      | emitHitlRequest()                |
 *      |---> requestChannel ------->      |
 *      |                            (process request)
 *      | awaitHitlResponse()              |
 *      |<--- responseChannel <------      |
 *      |                            submitResponse()
 * ```
 *
 * **Usage:**
 * ```kotlin
 * val emitter = EventBusHitlEventEmitter()
 *
 * // In graph builder
 * graph("my-workflow") {
 *     hitlEventEmitter(emitter)
 *     hitlInput("get-name", "What is your name?")
 * }
 *
 * // External system submits response
 * emitter.submitResponse(HITLResponse.textInput(toolCallId, "Alice"))
 * ```
 *
 * @property bufferSize Size of the internal channels
 * @since 1.0.6
 */
class EventBusHitlEventEmitter(
    private val bufferSize: Int = 100
) : HitlEventEmitter {

    // Request flow - external systems can collect from this
    private val _requestFlow = MutableSharedFlow<HITLMetadata>(
        replay = 0,
        extraBufferCapacity = bufferSize
    )

    // Response channel keyed by toolCallId
    private val responseChannels = mutableMapOf<String, Channel<HITLResponseEvent>>()
    private val mutex = Mutex()

    /**
     * Flow of HITL requests for external systems to consume
     */
    val requestFlow = _requestFlow

    override suspend fun emitHitlRequest(metadata: HITLMetadata): SpiceResult<Unit> {
        return try {
            // Create response channel for this request
            mutex.withLock {
                responseChannels[metadata.toolCallId] = Channel(Channel.BUFFERED)
            }

            // Emit request
            _requestFlow.emit(metadata)
            SpiceResult.success(Unit)
        } catch (e: Exception) {
            SpiceResult.failure(
                SpiceError.executionError(
                    message = "Failed to emit HITL request: ${e.message}",
                    cause = e
                )
            )
        }
    }

    override suspend fun checkHitlResponse(toolCallId: String): HITLResponseEvent? {
        val channel = mutex.withLock { responseChannels[toolCallId] } ?: return null
        return channel.tryReceive().getOrNull()
    }

    override suspend fun awaitHitlResponse(
        toolCallId: String,
        timeoutMs: Long?
    ): SpiceResult<HITLResponseEvent> {
        val channel = mutex.withLock { responseChannels[toolCallId] }
            ?: return SpiceResult.failure(
                SpiceError.validationError("No pending HITL request for toolCallId: $toolCallId")
            )

        return try {
            val response = if (timeoutMs != null) {
                withTimeoutOrNull(timeoutMs) {
                    channel.receive()
                } ?: HITLResponseEvent.timeout(toolCallId, "HITL request timed out after ${timeoutMs}ms")
            } else {
                channel.receive()
            }

            // Cleanup channel after receiving response
            mutex.withLock {
                responseChannels.remove(toolCallId)?.close()
            }

            SpiceResult.success(response)
        } catch (e: Exception) {
            SpiceResult.failure(
                SpiceError.executionError(
                    message = "Failed to await HITL response: ${e.message}",
                    cause = e
                )
            )
        }
    }

    override suspend fun cancelHitlRequest(
        toolCallId: String,
        reason: String
    ): SpiceResult<Unit> {
        return try {
            val channel = mutex.withLock { responseChannels.remove(toolCallId) }
            if (channel != null) {
                // Send cancellation event before closing
                channel.trySend(HITLResponseEvent.cancelled(toolCallId, reason))
                channel.close()
            }
            SpiceResult.success(Unit)
        } catch (e: Exception) {
            SpiceResult.failure(
                SpiceError.executionError(
                    message = "Failed to cancel HITL request: ${e.message}",
                    cause = e
                )
            )
        }
    }

    // =========================================
    // External System API
    // =========================================

    /**
     * Submit a HITL response from external system
     *
     * Called by the external system (UI, webhook handler, etc.) when
     * user provides their response.
     *
     * @param event The response event to submit
     * @return SpiceResult indicating success/failure
     */
    suspend fun submitResponse(event: HITLResponseEvent): SpiceResult<Unit> {
        val channel = mutex.withLock { responseChannels[event.toolCallId] }
            ?: return SpiceResult.failure(
                SpiceError.validationError(
                    "No pending HITL request for toolCallId: ${event.toolCallId}. " +
                    "The request may have timed out or been cancelled."
                )
            )

        return try {
            channel.send(event)
            SpiceResult.success(Unit)
        } catch (e: Exception) {
            SpiceResult.failure(
                SpiceError.executionError(
                    message = "Failed to submit HITL response: ${e.message}",
                    cause = e
                )
            )
        }
    }

    /**
     * Check if there's a pending HITL request for the given toolCallId
     *
     * @param toolCallId Tool call identifier
     * @return true if there's a pending request
     */
    suspend fun hasPendingRequest(toolCallId: String): Boolean {
        return mutex.withLock { responseChannels.containsKey(toolCallId) }
    }

    /**
     * Get list of all pending HITL request tool call IDs
     *
     * @return List of pending tool call IDs
     */
    suspend fun getPendingRequestIds(): List<String> {
        return mutex.withLock { responseChannels.keys.toList() }
    }

    /**
     * Close all channels and cleanup resources
     */
    suspend fun close() {
        mutex.withLock {
            responseChannels.values.forEach { it.close() }
            responseChannels.clear()
        }
    }
}
