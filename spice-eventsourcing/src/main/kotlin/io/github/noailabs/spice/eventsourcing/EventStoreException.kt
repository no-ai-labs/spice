package io.github.noailabs.spice.eventsourcing

/**
 * Exception hierarchy for Event Store operations
 * 
 * Provides specific exceptions for different failure scenarios
 * in event sourcing operations
 */

/**
 * Base exception for all event store related errors
 */
sealed class EventStoreException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    
    /**
     * The stream ID involved in the operation, if applicable
     */
    open val streamId: String? = null
    
    /**
     * The event ID involved in the operation, if applicable
     */
    open val eventId: String? = null
}

/**
 * Thrown when there's a version conflict during append operations
 */
class ConcurrencyException(
    override val streamId: String,
    val expectedVersion: Long,
    val actualVersion: Long,
    cause: Throwable? = null
) : EventStoreException(
    "Concurrency conflict on stream $streamId: expected version $expectedVersion but was $actualVersion",
    cause
) {
    /**
     * Check if this is a create conflict (stream already exists)
     */
    val isCreateConflict: Boolean = expectedVersion == -1L && actualVersion >= 0
}

/**
 * Thrown when a requested stream is not found
 */
class StreamNotFoundException(
    override val streamId: String,
    cause: Throwable? = null
) : EventStoreException(
    "Stream not found: $streamId",
    cause
)

/**
 * Thrown when a requested event is not found
 */
class EventNotFoundException(
    override val eventId: String,
    override val streamId: String? = null,
    cause: Throwable? = null
) : EventStoreException(
    "Event not found: $eventId" + (streamId?.let { " in stream $it" } ?: ""),
    cause
)

/**
 * Thrown when event data is corrupted or cannot be deserialized
 */
class CorruptedEventException(
    override val eventId: String,
    override val streamId: String?,
    val eventType: String?,
    cause: Throwable? = null
) : EventStoreException(
    "Corrupted event $eventId of type $eventType in stream $streamId",
    cause
) {
    constructor(
        serializedEvent: SerializedEvent,
        streamId: String? = null,
        cause: Throwable? = null
    ) : this(
        eventId = "unknown",
        streamId = streamId,
        eventType = serializedEvent.eventType,
        cause = cause
    )
}

/**
 * Thrown when a snapshot operation fails
 */
class SnapshotException(
    override val streamId: String,
    val version: Long,
    val operation: SnapshotOperation,
    cause: Throwable? = null
) : EventStoreException(
    "Snapshot operation ${operation.name} failed for stream $streamId at version $version",
    cause
) {
    enum class SnapshotOperation {
        SAVE,
        LOAD,
        DELETE
    }
}

/**
 * Thrown when the event store itself is unavailable
 */
class EventStoreUnavailableException(
    val storeName: String = "default",
    cause: Throwable? = null
) : EventStoreException(
    "Event store '$storeName' is unavailable",
    cause
)

/**
 * Thrown when an event exceeds size limits
 */
class EventTooLargeException(
    override val eventId: String,
    val eventSize: Long,
    val maxSize: Long,
    cause: Throwable? = null
) : EventStoreException(
    "Event $eventId size ($eventSize bytes) exceeds maximum allowed size ($maxSize bytes)",
    cause
)

/**
 * Thrown when subscription operations fail
 */
class SubscriptionException(
    val subscriptionId: String,
    override val streamId: String? = null,
    cause: Throwable? = null
) : EventStoreException(
    "Subscription $subscriptionId failed" + (streamId?.let { " for stream $it" } ?: ""),
    cause
)

/**
 * Thrown when there's a timeout in event store operations
 */
class EventStoreTimeoutException(
    val operation: String,
    val timeoutMillis: Long,
    override val streamId: String? = null,
    cause: Throwable? = null
) : EventStoreException(
    "Operation '$operation' timed out after ${timeoutMillis}ms" + 
    (streamId?.let { " on stream $it" } ?: ""),
    cause
)

/**
 * Thrown when transaction operations fail
 */
class TransactionException(
    val transactionId: String,
    val operation: TransactionOperation,
    cause: Throwable? = null
) : EventStoreException(
    "Transaction $transactionId failed during ${operation.name}",
    cause
) {
    enum class TransactionOperation {
        BEGIN,
        COMMIT,
        ROLLBACK
    }
}

/**
 * Retry policy for handling transient failures
 */
interface RetryPolicy {
    /**
     * Determine if an exception should trigger a retry
     */
    fun shouldRetry(exception: Exception, attemptNumber: Int): Boolean
    
    /**
     * Calculate delay before next retry attempt
     */
    fun getRetryDelay(attemptNumber: Int): Long
    
    /**
     * Maximum number of retry attempts
     */
    val maxAttempts: Int
}

/**
 * Default retry policy with exponential backoff
 */
class ExponentialBackoffRetryPolicy(
    override val maxAttempts: Int = 3,
    private val baseDelayMillis: Long = 100,
    private val maxDelayMillis: Long = 5000,
    private val multiplier: Double = 2.0
) : RetryPolicy {
    
    override fun shouldRetry(exception: Exception, attemptNumber: Int): Boolean {
        if (attemptNumber >= maxAttempts) return false
        
        return when (exception) {
            is EventStoreUnavailableException -> true
            is EventStoreTimeoutException -> true
            is ConcurrencyException -> true
            is TransactionException -> exception.operation != TransactionException.TransactionOperation.COMMIT
            else -> false
        }
    }
    
    override fun getRetryDelay(attemptNumber: Int): Long {
        val delay = (baseDelayMillis * Math.pow(multiplier, (attemptNumber - 1).toDouble())).toLong()
        return minOf(delay, maxDelayMillis)
    }
}

/**
 * Retry executor that applies retry policies
 */
class RetryExecutor(
    private val retryPolicy: RetryPolicy = ExponentialBackoffRetryPolicy()
) {
    
    suspend fun <T> execute(
        operation: String,
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        
        for (attempt in 1..retryPolicy.maxAttempts) {
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                
                if (!retryPolicy.shouldRetry(e, attempt)) {
                    throw e
                }
                
                if (attempt < retryPolicy.maxAttempts) {
                    val delay = retryPolicy.getRetryDelay(attempt)
                    kotlinx.coroutines.delay(delay)
                }
            }
        }
        
        throw lastException ?: IllegalStateException("Retry failed with no exception")
    }
}

/**
 * Extension functions for common error handling patterns
 */

/**
 * Execute with retry policy
 */
suspend fun <T> EventStore.withRetry(
    retryPolicy: RetryPolicy = ExponentialBackoffRetryPolicy(),
    block: suspend EventStore.() -> T
): T {
    val executor = RetryExecutor(retryPolicy)
    return executor.execute("EventStore operation") {
        block()
    }
}

/**
 * Handle specific exceptions with custom logic
 */
inline fun <T> handleEventStoreExceptions(
    onConcurrencyConflict: (ConcurrencyException) -> T? = { null },
    onStreamNotFound: (StreamNotFoundException) -> T? = { null },
    onCorruptedEvent: (CorruptedEventException) -> T? = { null },
    block: () -> T
): T {
    return try {
        block()
    } catch (e: ConcurrencyException) {
        onConcurrencyConflict(e) ?: throw e
    } catch (e: StreamNotFoundException) {
        onStreamNotFound(e) ?: throw e
    } catch (e: CorruptedEventException) {
        onCorruptedEvent(e) ?: throw e
    }
}

/**
 * Create an exception for a failed aggregate operation
 */
fun aggregateOperationFailed(
    aggregateType: String,
    aggregateId: String,
    operation: String,
    cause: Throwable
): EventStoreException {
    val streamId = "$aggregateType-$aggregateId"
    return when (cause) {
        is EventStoreException -> cause
        else -> EventStoreUnavailableException(
            storeName = "Failed to $operation aggregate $aggregateType with id $aggregateId",
            cause = cause
        )
    }
}

/**
 * Result type for event store operations
 */
sealed class EventStoreResult<out T> {
    data class Success<T>(val value: T) : EventStoreResult<T>()
    data class Failure(val exception: EventStoreException) : EventStoreResult<Nothing>()
    
    inline fun <R> map(transform: (T) -> R): EventStoreResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }
    
    inline fun <R> flatMap(transform: (T) -> EventStoreResult<R>): EventStoreResult<R> = when (this) {
        is Success -> transform(value)
        is Failure -> this
    }
    
    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw exception
    }
    
    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }
}

/**
 * Convert exceptions to EventStoreResult
 */
inline fun <T> runEventStoreOperation(block: () -> T): EventStoreResult<T> {
    return try {
        EventStoreResult.Success(block())
    } catch (e: EventStoreException) {
        EventStoreResult.Failure(e)
    } catch (e: Exception) {
        EventStoreResult.Failure(
            EventStoreUnavailableException(
                storeName = "unknown",
                cause = e
            )
        )
    }
}