package io.github.noailabs.spice.types

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * ExecutionContext - Execution Context Metadata
 *
 * Core functionality:
 * - Multi-tenant isolation (tenantId)
 * - User identification (userId)
 * - Session isolation (sessionId)
 * - Extensible metadata
 *
 * Usage example:
 * ```kotlin
 * val context = ExecutionContext(
 *     tenantId = "STAYFOLIO:SELF:KR",
 *     userId = "user123",
 *     sessionId = "session-456",
 *     metadata = mapOf(
 *         "channel" to "web",
 *         "locale" to "ko-KR",
 *         "deviceId" to "device-789"
 *     )
 * )
 * ```
 *
 * Security:
 * - Data isolation via tenantId
 * - Permission check via userId
 * - Context isolation via sessionId
 *
 * Observability:
 * - Additional info in metadata (IP, User-Agent, etc.)
 * - Context included in logs and metrics
 *
 * @property tenantId Tenant ID (COMPANY:PLATFORM:LANGUAGE)
 * @property userId User ID
 * @property sessionId Session ID (conversation unit)
 * @property metadata Extended metadata (channel, locale, deviceId, etc.)
 *
 * @since Spice 1.6.0
 */
@Serializable
data class ExecutionContext(
    val tenantId: String,
    val userId: String,
    val sessionId: String,
    val metadata: Map<String, @Contextual Any> = emptyMap()
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            coerceInputValues = true
        }

        /**
         * Deserialize from JSON string
         *
         * Note: metadata is converted from JsonObject to primitive types.
         * Complex types are kept as JsonElement.
         */
        fun fromJson(jsonString: String): ExecutionContext {
            val jsonObj = json.parseToJsonElement(jsonString) as JsonObject
            val tenantId = (jsonObj["tenantId"] as? JsonPrimitive)?.content ?: ""
            val userId = (jsonObj["userId"] as? JsonPrimitive)?.content ?: ""
            val sessionId = (jsonObj["sessionId"] as? JsonPrimitive)?.content ?: ""

            val metadataObj = jsonObj["metadata"] as? JsonObject ?: JsonObject(emptyMap())
            val metadata = metadataObj.mapValues { (_, v) ->
                when (v) {
                    is JsonPrimitive -> v.content
                    else -> v
                }
            }

            return ExecutionContext(
                tenantId = tenantId,
                userId = userId,
                sessionId = sessionId,
                metadata = metadata
            )
        }

        /**
         * Create basic ExecutionContext
         */
        fun create(
            tenantId: String,
            userId: String,
            sessionId: String
        ): ExecutionContext {
            return ExecutionContext(
                tenantId = tenantId,
                userId = userId,
                sessionId = sessionId
            )
        }

        /**
         * Create ExecutionContext with metadata
         */
        fun withMetadata(
            tenantId: String,
            userId: String,
            sessionId: String,
            metadata: Map<String, Any>
        ): ExecutionContext {
            return ExecutionContext(
                tenantId = tenantId,
                userId = userId,
                sessionId = sessionId,
                metadata = metadata
            )
        }

        /**
         * Restore from Map (for SpiceMessage.metadata interoperability)
         */
        fun fromMap(map: Map<String, Any>): ExecutionContext {
            return ExecutionContext(
                tenantId = map["tenantId"] as String,
                userId = map["userId"] as String,
                sessionId = map["sessionId"] as String,
                metadata = map.filterKeys { it !in setOf("tenantId", "userId", "sessionId") }
            )
        }
    }

    /**
     * Extract value from metadata (type-safe)
     */
    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> getMetadata(key: String): T? {
        return metadata[key] as? T
    }

    /**
     * Extract required value from metadata
     */
    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> requireMetadata(key: String): T {
        return metadata[key] as? T
            ?: throw IllegalArgumentException("Required metadata '$key' not found")
    }

    /**
     * Add metadata entry
     */
    fun withMetadata(key: String, value: Any): ExecutionContext {
        return copy(metadata = metadata + (key to value))
    }

    /**
     * Add multiple metadata entries
     */
    fun withMetadata(additional: Map<String, Any>): ExecutionContext {
        return copy(metadata = metadata + additional)
    }

    /**
     * Serialize to JSON string
     *
     * Note: metadata values are converted to strings.
     * JsonElement values are kept as-is.
     */
    fun toJson(): String {
        val metadataJson = buildJsonObject {
            metadata.forEach { (k, v) ->
                when (v) {
                    is JsonElement -> put(k, v)
                    is Number -> put(k, JsonPrimitive(v))
                    is Boolean -> put(k, JsonPrimitive(v))
                    else -> put(k, JsonPrimitive(v.toString()))
                }
            }
        }

        val jsonObj = buildJsonObject {
            put("tenantId", JsonPrimitive(tenantId))
            put("userId", JsonPrimitive(userId))
            put("sessionId", JsonPrimitive(sessionId))
            put("metadata", metadataJson)
        }

        return json.encodeToString(jsonObj)
    }

    /**
     * Convert to Map (for SpiceMessage.metadata interoperability)
     */
    fun toMap(): Map<String, Any> = buildMap {
        put("tenantId", tenantId)
        put("userId", userId)
        put("sessionId", sessionId)
        putAll(metadata)
    }
}
