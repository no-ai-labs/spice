package io.github.noailabs.spice.hitl.validation

import mu.KLogger

/**
 * Log level enum for HitlResultParser logging.
 *
 * Independent of SLF4J to maintain spice-core's minimal dependencies.
 *
 * @since Spice 1.3.5
 */
enum class HitlLogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    /** Disable logging completely */
    OFF
}

/**
 * Configuration options for HitlResultParser behavior.
 *
 * **Thread Safety**: Immutable data class, safe for concurrent access.
 *
 * @property successLogLevel Log level for successful parsing (default: DEBUG)
 * @property unknownFieldLogLevel Log level for unknown field warnings (default: DEBUG)
 * @property emptyCanonicalLogLevel Log level when empty canonical is detected (default: WARN)
 * @property parseFailureLogLevel Log level for parse failures (default: WARN)
 *
 * @since Spice 1.3.5
 */
data class HitlResultParserOptions(
    val successLogLevel: HitlLogLevel = HitlLogLevel.DEBUG,
    val unknownFieldLogLevel: HitlLogLevel = HitlLogLevel.DEBUG,
    val emptyCanonicalLogLevel: HitlLogLevel = HitlLogLevel.WARN,
    val parseFailureLogLevel: HitlLogLevel = HitlLogLevel.WARN
) {
    companion object {
        /**
         * Default options with standard log levels.
         */
        val DEFAULT = HitlResultParserOptions()

        /**
         * Quiet mode: minimal logging.
         *
         * Only logs parse failures at ERROR level.
         */
        fun quiet() = HitlResultParserOptions(
            successLogLevel = HitlLogLevel.OFF,
            unknownFieldLogLevel = HitlLogLevel.OFF,
            emptyCanonicalLogLevel = HitlLogLevel.WARN,
            parseFailureLogLevel = HitlLogLevel.ERROR
        )

        /**
         * Verbose mode: detailed logging for debugging.
         */
        fun verbose() = HitlResultParserOptions(
            successLogLevel = HitlLogLevel.DEBUG,
            unknownFieldLogLevel = HitlLogLevel.INFO,
            emptyCanonicalLogLevel = HitlLogLevel.WARN,
            parseFailureLogLevel = HitlLogLevel.WARN
        )

        /**
         * Production mode: balanced logging.
         */
        fun production() = HitlResultParserOptions(
            successLogLevel = HitlLogLevel.TRACE,
            unknownFieldLogLevel = HitlLogLevel.DEBUG,
            emptyCanonicalLogLevel = HitlLogLevel.WARN,
            parseFailureLogLevel = HitlLogLevel.ERROR
        )

        /**
         * Silent mode: no logging at all.
         *
         * Useful for testing where log output is not desired.
         */
        fun silent() = HitlResultParserOptions(
            successLogLevel = HitlLogLevel.OFF,
            unknownFieldLogLevel = HitlLogLevel.OFF,
            emptyCanonicalLogLevel = HitlLogLevel.OFF,
            parseFailureLogLevel = HitlLogLevel.OFF
        )
    }
}

/**
 * Global configuration holder for HitlResultParser.
 *
 * **Thread Safety**: Uses @Volatile for read visibility and synchronized
 * block with update function for atomic updates.
 *
 * **Usage:**
 * ```kotlin
 * // Get current options
 * val options = HitlResultParserConfig.options
 *
 * // Set new options
 * HitlResultParserConfig.setOptions(HitlResultParserOptions.quiet())
 *
 * // Update specific fields
 * HitlResultParserConfig.update { it.copy(successLogLevel = HitlLogLevel.INFO) }
 *
 * // Reset to defaults
 * HitlResultParserConfig.resetToDefaults()
 * ```
 *
 * @since Spice 1.3.5
 */
object HitlResultParserConfig {

    @Volatile
    private var _options: HitlResultParserOptions = HitlResultParserOptions.DEFAULT

    private val lock = Any()

    /**
     * Current parser options.
     *
     * Thread-safe read via @Volatile.
     */
    val options: HitlResultParserOptions
        get() = _options

    /**
     * Set new parser options.
     *
     * Thread-safe update via synchronized block.
     *
     * @param options The options to use
     */
    fun setOptions(options: HitlResultParserOptions) {
        synchronized(lock) {
            _options = options
        }
    }

    /**
     * Update options atomically using a transform function.
     *
     * Thread-safe atomic update via synchronized block.
     *
     * **Usage:**
     * ```kotlin
     * HitlResultParserConfig.update { current ->
     *     current.copy(successLogLevel = HitlLogLevel.INFO)
     * }
     * ```
     *
     * @param transform Function to transform current options to new options
     */
    fun update(transform: (HitlResultParserOptions) -> HitlResultParserOptions) {
        synchronized(lock) {
            _options = transform(_options)
        }
    }

    /**
     * Reset options to defaults.
     *
     * Useful for test cleanup.
     */
    fun resetToDefaults() {
        synchronized(lock) {
            _options = HitlResultParserOptions.DEFAULT
        }
    }
}

/**
 * Utility for logging with configurable log levels.
 *
 * Maps [HitlLogLevel] to KotlinLogging calls, avoiding code duplication.
 *
 * @since Spice 1.3.5
 */
object HitlLogLevelMapper {

    /**
     * Log a message at the specified level.
     *
     * If level is [HitlLogLevel.OFF], the message supplier is not invoked.
     *
     * @param logger The KotlinLogging logger
     * @param level The log level
     * @param message Lazy message supplier
     */
    inline fun log(logger: KLogger, level: HitlLogLevel, crossinline message: () -> String) {
        when (level) {
            HitlLogLevel.TRACE -> logger.trace { message() }
            HitlLogLevel.DEBUG -> logger.debug { message() }
            HitlLogLevel.INFO -> logger.info { message() }
            HitlLogLevel.WARN -> logger.warn { message() }
            HitlLogLevel.ERROR -> logger.error { message() }
            HitlLogLevel.OFF -> { /* no-op: skip logging entirely */ }
        }
    }

    /**
     * Log a message with exception at the specified level.
     *
     * If level is [HitlLogLevel.OFF], the message supplier is not invoked.
     *
     * @param logger The KotlinLogging logger
     * @param level The log level
     * @param exception The exception to log
     * @param message Lazy message supplier
     */
    inline fun log(logger: KLogger, level: HitlLogLevel, exception: Throwable, crossinline message: () -> String) {
        when (level) {
            HitlLogLevel.TRACE -> logger.trace(exception) { message() }
            HitlLogLevel.DEBUG -> logger.debug(exception) { message() }
            HitlLogLevel.INFO -> logger.info(exception) { message() }
            HitlLogLevel.WARN -> logger.warn(exception) { message() }
            HitlLogLevel.ERROR -> logger.error(exception) { message() }
            HitlLogLevel.OFF -> { /* no-op: skip logging entirely */ }
        }
    }
}
