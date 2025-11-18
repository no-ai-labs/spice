package io.github.noailabs.spice.eventbus.schema

import io.github.noailabs.spice.error.SpiceError
import io.github.noailabs.spice.error.SpiceResult
import kotlinx.serialization.KSerializer
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * 📚 Default Schema Registry Implementation
 *
 * Thread-safe implementation of SchemaRegistry using ConcurrentHashMap.
 * Supports semantic versioning and compatibility checking.
 *
 * **Thread Safety:**
 * All operations are thread-safe using ConcurrentHashMap.
 *
 * **Migration:**
 * Currently only supports same-major-version compatibility.
 * Breaking changes (major version bump) require manual migration.
 *
 * @since 1.0.0-alpha-5
 * @author Spice Framework
 */
class DefaultSchemaRegistry : SchemaRegistry {
    // Key: "TypeName:Version" (e.g., "ToolCallEvent:1.0.0")
    private val schemas = ConcurrentHashMap<String, SchemaInfo<*>>()

    // Key: "TypeName" → Latest version
    private val latestVersions = ConcurrentHashMap<String, String>()

    override fun <T : Any> register(
        type: KClass<T>,
        version: String,
        serializer: KSerializer<T>
    ) {
        val typeName = type.qualifiedName
            ?: throw IllegalArgumentException("Type must have qualified name: $type")

        val schemaInfo = SchemaInfo.parse(type, version, serializer)
        val key = schemaKey(typeName, version)

        schemas[key] = schemaInfo

        // Update latest version if this is newer
        latestVersions.compute(typeName) { _, current ->
            if (current == null) {
                version
            } else {
                val currentInfo = schemas[schemaKey(typeName, current)] as SchemaInfo<*>
                if (schemaInfo.major > currentInfo.major ||
                    (schemaInfo.major == currentInfo.major && schemaInfo.minor > currentInfo.minor) ||
                    (schemaInfo.major == currentInfo.major && schemaInfo.minor == currentInfo.minor && schemaInfo.patch > currentInfo.patch)
                ) {
                    version
                } else {
                    current
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getSerializer(
        type: KClass<T>,
        version: String
    ): KSerializer<T>? {
        val typeName = type.qualifiedName ?: return null
        val key = schemaKey(typeName, version)
        val schemaInfo = schemas[key] as? SchemaInfo<T>
        return schemaInfo?.serializer
    }

    override fun <T : Any> getLatestVersion(type: KClass<T>): String? {
        val typeName = type.qualifiedName ?: return null
        return latestVersions[typeName]
    }

    override fun isCompatible(
        eventType: String,
        fromVersion: String,
        toVersion: String
    ): Boolean {
        if (fromVersion == toVersion) return true

        val fromKey = schemaKey(eventType, fromVersion)
        val toKey = schemaKey(eventType, toVersion)

        val fromInfo = schemas[fromKey] ?: return false
        val toInfo = schemas[toKey] ?: return false

        // Compatible if same major version
        return fromInfo.major == toInfo.major
    }

    override suspend fun <T : Any> migrate(
        event: Any,
        fromVersion: String,
        toVersion: String,
        targetType: KClass<T>
    ): SpiceResult<T> {
        // Same version - just cast
        if (fromVersion == toVersion) {
            @Suppress("UNCHECKED_CAST")
            return SpiceResult.success(event as T)
        }

        val typeName = targetType.qualifiedName
            ?: return SpiceResult.failure(
                SpiceError.validationError("Target type must have qualified name")
            )

        // Check compatibility
        if (!isCompatible(typeName, fromVersion, toVersion)) {
            return SpiceResult.failure(
                SpiceError.validationError(
                    "Incompatible versions: $fromVersion → $toVersion (different major versions)"
                )
            )
        }

        // For now, we only support same-major-version compatibility
        // Within same major version, forward/backward compatibility is automatic
        // (new optional fields are ignored by old deserializers, old missing fields get defaults)
        @Suppress("UNCHECKED_CAST")
        return try {
            SpiceResult.success(event as T)
        } catch (e: ClassCastException) {
            SpiceResult.failure(
                SpiceError.validationError(
                    "Failed to migrate event: ${e.message}"
                )
            )
        }
    }

    override fun <T : Any> getVersions(type: KClass<T>): List<String> {
        val typeName = type.qualifiedName ?: return emptyList()

        return schemas.keys
            .filter { it.startsWith("$typeName:") }
            .map { it.substringAfter(":") }
            .sorted()
    }

    override fun <T : Any> isRegistered(type: KClass<T>, version: String): Boolean {
        val typeName = type.qualifiedName ?: return false
        val key = schemaKey(typeName, version)
        return schemas.containsKey(key)
    }

    /**
     * Get schema information for debugging
     */
    fun getSchemaInfo(typeName: String, version: String): SchemaInfo<*>? {
        return schemas[schemaKey(typeName, version)]
    }

    /**
     * Get all registered schemas
     */
    fun getAllSchemas(): Map<String, SchemaInfo<*>> {
        return schemas.toMap()
    }

    /**
     * Clear all registered schemas (for testing)
     */
    fun clear() {
        schemas.clear()
        latestVersions.clear()
    }

    private fun schemaKey(typeName: String, version: String): String {
        return "$typeName:$version"
    }
}
