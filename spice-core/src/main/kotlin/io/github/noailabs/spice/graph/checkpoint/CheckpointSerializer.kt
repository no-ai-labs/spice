package io.github.noailabs.spice.graph.checkpoint

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * 🔧 Checkpoint Serializer
 *
 * Type-safe JSON serialization for checkpoints.
 * Preserves nested Map/List structures for checkpoint persistence.
 *
 * **Problem Solved:**
 * Default serialization can lose type information for nested structures.
 * This serializer ensures checkpoints can be reliably saved to and restored from
 * external storage (Redis, DB, file system).
 *
 * **Features:**
 * - Serialize Checkpoint to JSON string
 * - Deserialize back to Checkpoint with preserved types
 * - Nested structures remain as Map<*, *> and List<*>
 * - Pretty-print support for debugging
 *
 * **Usage:**
 * ```kotlin
 * // Serialize for storage
 * val json = CheckpointSerializer.serialize(checkpoint)
 * redis.set(checkpointId, json)
 *
 * // Deserialize for restore
 * val json = redis.get(checkpointId)
 * val checkpoint = CheckpointSerializer.deserialize(json)
 * ```
 *
 * @since 1.0.0
 */
object CheckpointSerializer {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val jsonPretty = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    /**
     * Serialize checkpoint to JSON
     *
     * @param checkpoint Checkpoint to serialize
     * @param prettyPrint Whether to format JSON for readability (default: false)
     * @return JSON string
     */
    fun serialize(checkpoint: Checkpoint, prettyPrint: Boolean = false): String {
        return if (prettyPrint) {
            jsonPretty.encodeToString(checkpoint)
        } else {
            json.encodeToString(checkpoint)
        }
    }

    /**
     * Deserialize checkpoint from JSON
     *
     * @param json JSON string
     * @return Checkpoint with preserved types
     */
    fun deserialize(json: String): Checkpoint {
        return this.json.decodeFromString(json)
    }
}
