package io.github.noailabs.spice.eventbus.schema

import io.github.noailabs.spice.error.SpiceResult
import kotlinx.serialization.KSerializer
import kotlin.reflect.KClass

/**
 * 📚 Schema Registry
 *
 * Manages event schema versions and handles schema evolution.
 * Enables backward and forward compatibility for event versioning.
 *
 * **Versioning Strategy:**
 * - Semantic versioning: MAJOR.MINOR.PATCH
 * - MAJOR: Breaking changes (incompatible schema)
 * - MINOR: Backward-compatible additions (new optional fields)
 * - PATCH: Bug fixes (no schema changes)
 *
 * **Compatibility Rules:**
 * - Same MAJOR version: Compatible (can deserialize)
 * - Different MAJOR version: Incompatible (migration required)
 *
 * **Usage:**
 * ```kotlin
 * val registry = DefaultSchemaRegistry()
 *
 * // Register schema versions
 * registry.register(ToolCallEvent::class, "1.0.0", ToolCallEvent.serializer())
 * registry.register(ToolCallEventV2::class, "2.0.0", ToolCallEventV2.serializer())
 *
 * // Get serializer for version
 * val serializer = registry.getSerializer(ToolCallEvent::class, "1.0.0")
 *
 * // Check compatibility
 * val compatible = registry.isCompatible("ToolCallEvent", "1.0.0", "1.1.0")  // true
 * val incompatible = registry.isCompatible("ToolCallEvent", "1.0.0", "2.0.0")  // false
 * ```
 *
 * @since 1.0.0-alpha-5
 * @author Spice Framework
 */
interface SchemaRegistry {
    /**
     * Register event schema with version
     *
     * @param type Event type class
     * @param version Schema version (semantic versioning)
     * @param serializer Kotlinx serializer for this version
     */
    fun <T : Any> register(
        type: KClass<T>,
        version: String,
        serializer: KSerializer<T>
    )

    /**
     * Get serializer for type and version
     *
     * @param type Event type class
     * @param version Schema version
     * @return Serializer or null if not found
     */
    fun <T : Any> getSerializer(
        type: KClass<T>,
        version: String
    ): KSerializer<T>?

    /**
     * Get latest version for event type
     *
     * @param type Event type class
     * @return Latest version or null if not registered
     */
    fun <T : Any> getLatestVersion(type: KClass<T>): String?

    /**
     * Check if version is compatible with another version
     *
     * Same major version = compatible
     * Different major version = incompatible
     *
     * @param eventType Event type name (fully qualified)
     * @param fromVersion Source version
     * @param toVersion Target version
     * @return true if compatible
     */
    fun isCompatible(
        eventType: String,
        fromVersion: String,
        toVersion: String
    ): Boolean

    /**
     * Migrate event from old version to new version
     *
     * @param event Original event (any version)
     * @param fromVersion Source version
     * @param toVersion Target version
     * @param targetType Target event type
     * @return Migrated event or null if migration not possible
     */
    suspend fun <T : Any> migrate(
        event: Any,
        fromVersion: String,
        toVersion: String,
        targetType: KClass<T>
    ): SpiceResult<T>

    /**
     * Get all registered versions for event type
     *
     * @param type Event type class
     * @return List of registered versions (sorted ascending)
     */
    fun <T : Any> getVersions(type: KClass<T>): List<String>

    /**
     * Check if schema is registered
     *
     * @param type Event type class
     * @param version Schema version
     * @return true if registered
     */
    fun <T : Any> isRegistered(type: KClass<T>, version: String): Boolean
}

/**
 * Schema version info
 */
data class SchemaInfo<T : Any>(
    val type: KClass<T>,
    val version: String,
    val serializer: KSerializer<T>,
    val major: Int,
    val minor: Int,
    val patch: Int
) {
    companion object {
        private val SEMVER_REGEX = Regex("""^(\d+)\.(\d+)\.(\d+)$""")

        fun <T : Any> parse(
            type: KClass<T>,
            version: String,
            serializer: KSerializer<T>
        ): SchemaInfo<T> {
            val match = SEMVER_REGEX.matchEntire(version)
                ?: throw IllegalArgumentException("Invalid version format: $version (expected: MAJOR.MINOR.PATCH)")

            val (major, minor, patch) = match.destructured
            return SchemaInfo(
                type = type,
                version = version,
                serializer = serializer,
                major = major.toInt(),
                minor = minor.toInt(),
                patch = patch.toInt()
            )
        }
    }
}
