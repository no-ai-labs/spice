package io.github.noailabs.spice.error

import io.github.noailabs.spice.Comm
import io.github.noailabs.spice.CommType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

/**
 * 🎯 Spice Result - Type-Safe Error Handling
 *
 * A sealed class representing either success or failure in Spice operations.
 * Provides Railway-Oriented Programming pattern for error handling.
 *
 * Example:
 * ```kotlin
 * val result: SpiceResult<Comm> = agent.processComm(comm)
 *     .map { it.content.uppercase() }
 *     .recover { SpiceError.AgentError("Fallback", it) }
 *
 * when (result) {
 *     is Success -> println("Result: ${result.value}")
 *     is Failure -> println("Error: ${result.error.message}")
 * }
 * ```
 */
sealed class SpiceResult<T> {

    /**
     * Successful result containing a value
     */
    data class Success<T>(val value: T) : SpiceResult<T>()

    /**
     * Failed result containing an error
     */
    data class Failure(val error: SpiceError) : SpiceResult<Nothing>()

    /**
     * Check if result is success
     */
    val isSuccess: Boolean
        get() = this is Success

    /**
     * Check if result is failure
     */
    val isFailure: Boolean
        get() = this is Failure

    /**
     * Get value or null
     */
    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }

    /**
     * Get value or throw exception
     */
    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw error.toException()
    }

    /**
     * Get value or default
     */
    fun getOrElse(default: T): T = when (this) {
        is Success -> value
        is Failure -> default
    }

    /**
     * Get value or compute default
     */
    inline fun getOrElse(crossinline default: (SpiceError) -> T): T = when (this) {
        is Success -> value
        is Failure -> default(error)
    }

    /**
     * Transform success value
     */
    @Suppress("UNCHECKED_CAST")
    inline fun <R> map(transform: (T) -> R): SpiceResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this as SpiceResult<R>
    }

    /**
     * Transform success value to another Result
     */
    @Suppress("UNCHECKED_CAST")
    inline fun <R> flatMap(transform: (T) -> SpiceResult<R>): SpiceResult<R> = when (this) {
        is Success -> transform(value)
        is Failure -> this as SpiceResult<R>
    }

    /**
     * Transform error
     */
    @Suppress("UNCHECKED_CAST")
    inline fun mapError(transform: (SpiceError) -> SpiceError): SpiceResult<T> = when (this) {
        is Success -> this
        is Failure -> Failure(transform(error)) as SpiceResult<T>
    }

    /**
     * Recover from error
     */
    inline fun recover(recovery: (SpiceError) -> T): SpiceResult<T> = when (this) {
        is Success -> this
        is Failure -> Success(recovery(error))
    }

    /**
     * Recover from error with another Result
     */
    inline fun recoverWith(recovery: (SpiceError) -> SpiceResult<T>): SpiceResult<T> = when (this) {
        is Success -> this
        is Failure -> recovery(error)
    }

    /**
     * Execute side effect if success
     */
    inline fun onSuccess(action: (T) -> Unit): SpiceResult<T> {
        if (this is Success) action(value)
        return this
    }

    /**
     * Execute side effect if failure
     */
    inline fun onFailure(action: (SpiceError) -> Unit): SpiceResult<T> {
        if (this is Failure) action(error)
        return this
    }

    /**
     * Fold result into a single value
     */
    inline fun <R> fold(
        onSuccess: (T) -> R,
        onFailure: (SpiceError) -> R
    ): R = when (this) {
        is Success -> onSuccess(value)
        is Failure -> onFailure(error)
    }

    companion object {
        /**
         * Create success result
         */
        fun <T> success(value: T): SpiceResult<T> = Success(value)

        /**
         * Create failure result
         */
        @Suppress("UNCHECKED_CAST")
        fun <T> failure(error: SpiceError): SpiceResult<T> = Failure(error) as SpiceResult<T>

        /**
         * Catch exceptions and convert to Result
         */
        @Suppress("UNCHECKED_CAST")
        inline fun <T> catching(block: () -> T): SpiceResult<T> = try {
            Success(block())
        } catch (e: Exception) {
            Failure(SpiceError.fromException(e)) as SpiceResult<T>
        }

        /**
         * Catch suspend exceptions and convert to Result
         */
        @Suppress("UNCHECKED_CAST")
        suspend inline fun <T> catchingSuspend(crossinline block: suspend () -> T): SpiceResult<T> = try {
            Success(block())
        } catch (e: Exception) {
            Failure(SpiceError.fromException(e)) as SpiceResult<T>
        }
    }
}

/**
 * Extension: Convert Comm to Result based on error type
 */
fun Comm.toResult(): SpiceResult<Comm> {
    return if (this.type == CommType.ERROR) {
        SpiceResult.failure(SpiceError.CommError(this.content, this))
    } else {
        SpiceResult.success(this)
    }
}

/**
 * Extension: Apply Result to Flow
 */
fun <T> Flow<T>.asResult(): Flow<SpiceResult<T>> = this
    .map { SpiceResult.success(it) }
    .catch { emit(SpiceResult.failure(SpiceError.fromException(it))) }

/**
 * Extension: Unwrap successful results from Flow
 */
fun <T> Flow<SpiceResult<T>>.unwrapSuccesses(): Flow<T> = this
    .map { result ->
        when (result) {
            is SpiceResult.Success -> result.value
            is SpiceResult.Failure -> throw result.error.toException()
        }
    }

/**
 * Extension: Filter only successful results
 */
fun <T : Any> Flow<SpiceResult<T>>.filterSuccesses(): Flow<T> = this
    .map { it.getOrNull() }
    .mapNotNull { it }
