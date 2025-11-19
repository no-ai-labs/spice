package io.github.noailabs.spice.tool

import io.github.noailabs.spice.ToolResult
import io.github.noailabs.spice.error.SpiceError
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Registry for managing multiple ToolLifecycleListeners.
 *
 * Aggregates multiple listeners and dispatches events to all of them.
 * Exceptions from individual listeners are caught and logged to prevent
 * breaking tool execution.
 *
 * **Usage:**
 * ```kotlin
 * val listeners = ToolLifecycleListeners()
 *     .add(MetricsListener())
 *     .add(SlackAlertListener())
 *     .add(KafkaExporter())
 *
 * // Or using builder
 * val listeners = ToolLifecycleListeners.builder()
 *     .add(MetricsListener())
 *     .add(SlackAlertListener())
 *     .build()
 *
 * // In graph DSL
 * graph("workflow") {
 *     toolLifecycleListeners(listeners)
 *     // ...
 * }
 * ```
 *
 * @since 1.0.0
 */
class ToolLifecycleListeners private constructor(
    private val listeners: List<ToolLifecycleListener>
) : ToolLifecycleListener {

    /**
     * Check if any listeners are registered
     */
    fun isEmpty(): Boolean = listeners.isEmpty()

    /**
     * Get the number of registered listeners
     */
    fun size(): Int = listeners.size

    override suspend fun onInvoke(context: ToolInvocationContext) {
        for (listener in listeners) {
            try {
                listener.onInvoke(context)
            } catch (e: Exception) {
                logger.warn(e) {
                    "Listener ${listener::class.simpleName} threw exception in onInvoke for tool ${context.toolName}"
                }
            }
        }
    }

    override suspend fun onSuccess(context: ToolInvocationContext, result: ToolResult, durationMs: Long) {
        for (listener in listeners) {
            try {
                listener.onSuccess(context, result, durationMs)
            } catch (e: Exception) {
                logger.warn(e) {
                    "Listener ${listener::class.simpleName} threw exception in onSuccess for tool ${context.toolName}"
                }
            }
        }
    }

    override suspend fun onFailure(context: ToolInvocationContext, error: SpiceError, durationMs: Long) {
        for (listener in listeners) {
            try {
                listener.onFailure(context, error, durationMs)
            } catch (e: Exception) {
                logger.warn(e) {
                    "Listener ${listener::class.simpleName} threw exception in onFailure for tool ${context.toolName}"
                }
            }
        }
    }

    override suspend fun onComplete(context: ToolInvocationContext) {
        for (listener in listeners) {
            try {
                listener.onComplete(context)
            } catch (e: Exception) {
                logger.warn(e) {
                    "Listener ${listener::class.simpleName} threw exception in onComplete for tool ${context.toolName}"
                }
            }
        }
    }

    companion object {
        /**
         * Empty registry with no listeners
         */
        val EMPTY = ToolLifecycleListeners(emptyList())

        /**
         * Create a registry with a single listener
         */
        fun of(listener: ToolLifecycleListener): ToolLifecycleListeners {
            return ToolLifecycleListeners(listOf(listener))
        }

        /**
         * Create a registry with multiple listeners
         */
        fun of(vararg listeners: ToolLifecycleListener): ToolLifecycleListeners {
            return ToolLifecycleListeners(listeners.toList())
        }

        /**
         * Create a registry from a list of listeners
         */
        fun of(listeners: List<ToolLifecycleListener>): ToolLifecycleListeners {
            return ToolLifecycleListeners(listeners)
        }

        /**
         * Create a builder for fluent configuration
         */
        fun builder(): Builder = Builder()
    }

    /**
     * Builder for fluent ToolLifecycleListeners construction
     */
    class Builder {
        private val listeners = mutableListOf<ToolLifecycleListener>()

        /**
         * Add a listener
         */
        fun add(listener: ToolLifecycleListener): Builder {
            listeners.add(listener)
            return this
        }

        /**
         * Add multiple listeners
         */
        fun addAll(listeners: Collection<ToolLifecycleListener>): Builder {
            this.listeners.addAll(listeners)
            return this
        }

        /**
         * Build the registry
         */
        fun build(): ToolLifecycleListeners {
            return ToolLifecycleListeners(listeners.toList())
        }
    }
}

/**
 * Extension to create ToolLifecycleListeners from a list
 */
fun List<ToolLifecycleListener>.toToolLifecycleListeners(): ToolLifecycleListeners {
    return ToolLifecycleListeners.of(this)
}
