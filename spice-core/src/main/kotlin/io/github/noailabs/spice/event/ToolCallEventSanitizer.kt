package io.github.noailabs.spice.event

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Configuration for metadata filtering in ToolCallEvents.
 *
 * Used to control which metadata keys are included/excluded when publishing events.
 * Useful for removing sensitive data or reducing payload size.
 *
 * **Filtering Logic:**
 * 1. If `includeMetadataKeys` is not null and not empty, only those keys are kept
 * 2. Keys in `excludeMetadataKeys` are then removed
 *
 * **Usage:**
 * ```kotlin
 * val config = MetadataFilterConfig(
 *     includeMetadataKeys = setOf("action_type", "tool_name", "status"),
 *     excludeMetadataKeys = setOf("password", "apiKey", "token")
 * )
 * ```
 *
 * @property includeMetadataKeys Whitelist of keys to include (null = all keys)
 * @property excludeMetadataKeys Blacklist of keys to exclude (null = none)
 * @since 1.0.0
 */
data class MetadataFilterConfig(
    val includeMetadataKeys: Set<String>? = null,
    val excludeMetadataKeys: Set<String>? = null
) {
    /**
     * Check if any filtering is configured
     */
    fun hasFilters(): Boolean {
        return (includeMetadataKeys != null && includeMetadataKeys.isNotEmpty()) ||
            (excludeMetadataKeys != null && excludeMetadataKeys.isNotEmpty())
    }

    companion object {
        /**
         * No filtering - all metadata passes through
         */
        val NONE = MetadataFilterConfig()

        /**
         * Common sensitive keys to exclude
         */
        val SECURITY_EXCLUDE = MetadataFilterConfig(
            excludeMetadataKeys = setOf(
                "password", "apiKey", "token", "secret",
                "sessionToken", "accessToken", "refreshToken",
                "authorization", "credential", "privateKey"
            )
        )
    }
}

/**
 * Utility for sanitizing ToolCallEvent metadata.
 *
 * Applies include/exclude filtering to event metadata before publishing.
 * Ensures sensitive data is not leaked and payload sizes are controlled.
 *
 * @since 1.0.0
 */
object ToolCallEventSanitizer {

    /**
     * Filter metadata map based on configuration.
     *
     * @param metadata Original metadata map
     * @param config Filter configuration
     * @return Filtered metadata map
     */
    fun filterMetadata(
        metadata: Map<String, Any>,
        config: MetadataFilterConfig
    ): Map<String, Any> {
        if (!config.hasFilters()) {
            return metadata
        }

        var result = metadata

        // Apply whitelist (include only specified keys)
        val includeKeys = config.includeMetadataKeys
        if (includeKeys != null && includeKeys.isNotEmpty()) {
            val excluded = result.keys - includeKeys
            if (excluded.isNotEmpty()) {
                logger.debug { "Metadata keys dropped by whitelist: $excluded" }
            }
            result = result.filterKeys { it in includeKeys }
        }

        // Apply blacklist (remove specified keys)
        val excludeKeys = config.excludeMetadataKeys
        if (excludeKeys != null && excludeKeys.isNotEmpty()) {
            val removed = result.keys.intersect(excludeKeys)
            if (removed.isNotEmpty()) {
                logger.debug { "Metadata keys dropped by blacklist: $removed" }
            }
            result = result.filterKeys { it !in excludeKeys }
        }

        return result
    }

    /**
     * Create a sanitized copy of a ToolCallEvent with filtered metadata.
     *
     * Returns a new event instance with filtered metadata. The original event
     * is not modified.
     *
     * @param event Original event
     * @param config Filter configuration
     * @return New event with filtered metadata
     */
    fun sanitize(event: ToolCallEvent, config: MetadataFilterConfig): ToolCallEvent {
        if (!config.hasFilters()) {
            return event
        }

        val filteredMetadata = filterMetadata(event.metadata, config)

        return when (event) {
            is ToolCallEvent.Emitted -> event.copy(metadata = filteredMetadata)
            is ToolCallEvent.Received -> event.copy(metadata = filteredMetadata)
            is ToolCallEvent.Completed -> event.copy(metadata = filteredMetadata)
            is ToolCallEvent.Failed -> event.copy(metadata = filteredMetadata)
            is ToolCallEvent.Retrying -> event.copy(metadata = filteredMetadata)
            is ToolCallEvent.Cancelled -> event.copy(metadata = filteredMetadata)
        }
    }
}
