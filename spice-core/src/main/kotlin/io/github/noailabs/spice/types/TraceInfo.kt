package io.github.noailabs.spice.types

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * TraceInfo - Event DAG Tracing
 *
 * Core functionality:
 * - Track parent-child relationships between events
 * - Workflow execution tracing
 * - Step execution tracing
 * - Session isolation
 *
 * Event DAG structure:
 * ```
 * user_input (evt_001)
 *   | parentEventId=evt_001
 * workflow_update (evt_002, state=RUNNING)
 *   | parentEventId=evt_002
 * tool_call (evt_003, toolName=list_reservations)
 *   | parentEventId=evt_003
 * agent_message (evt_004, "Found 2 results")
 *   | parentEventId=evt_004
 * workflow_update (evt_005, state=WAITING)
 *   | parentEventId=evt_005
 * user_input (evt_006, "1")  <- HITL Resume
 *   | parentEventId=evt_006
 * ...
 * ```
 *
 * Replay:
 * - Query all events by sessionId
 * - Reconstruct DAG using parentEventId
 * - Sequential re-execution possible
 *
 * Observability:
 * - Performance measurement via workflowId + stepId
 * - Distributed tracing via traceId (Jaeger, Zipkin compatible)
 *
 * @property sessionId Session ID (conversation unit)
 * @property workflowId Workflow ID (optional, during workflow execution)
 * @property stepId Step ID (optional, during specific step execution)
 * @property parentEventId Parent event ID (DAG connection)
 * @property traceId Distributed tracing ID (optional, external system integration)
 *
 * @since Spice 1.6.0
 */
@Serializable
data class TraceInfo(
    val sessionId: String,
    val workflowId: String? = null,
    val stepId: String? = null,
    val parentEventId: String? = null,
    val traceId: String? = null
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            coerceInputValues = true
        }

        /**
         * Deserialize from JSON string
         */
        fun fromJson(jsonString: String): TraceInfo = json.decodeFromString(jsonString)

        /**
         * Create TraceInfo for session start
         */
        fun forSession(sessionId: String, traceId: String? = null): TraceInfo {
            return TraceInfo(
                sessionId = sessionId,
                traceId = traceId
            )
        }

        /**
         * Create TraceInfo for workflow start
         */
        fun forWorkflow(
            sessionId: String,
            workflowId: String,
            parentEventId: String? = null,
            traceId: String? = null
        ): TraceInfo {
            return TraceInfo(
                sessionId = sessionId,
                workflowId = workflowId,
                parentEventId = parentEventId,
                traceId = traceId
            )
        }

        /**
         * Create TraceInfo for step execution
         */
        fun forStep(
            sessionId: String,
            workflowId: String,
            stepId: String,
            parentEventId: String? = null,
            traceId: String? = null
        ): TraceInfo {
            return TraceInfo(
                sessionId = sessionId,
                workflowId = workflowId,
                stepId = stepId,
                parentEventId = parentEventId,
                traceId = traceId
            )
        }

        /**
         * Restore from Map (for SpiceMessage.data interoperability)
         */
        fun fromMap(map: Map<String, Any?>?): TraceInfo {
            if (map == null) return TraceInfo(sessionId = "")
            return TraceInfo(
                sessionId = map["sessionId"] as? String ?: "",
                workflowId = map["workflowId"] as? String,
                stepId = map["stepId"] as? String,
                parentEventId = map["parentEventId"] as? String,
                traceId = map["traceId"] as? String
            )
        }
    }

    /**
     * Create TraceInfo for next event (update parentEventId)
     */
    fun withParent(parentEventId: String): TraceInfo {
        return copy(parentEventId = parentEventId)
    }

    /**
     * Update TraceInfo when entering workflow
     */
    fun enterWorkflow(workflowId: String): TraceInfo {
        return copy(workflowId = workflowId)
    }

    /**
     * Update TraceInfo when entering step
     */
    fun enterStep(stepId: String): TraceInfo {
        return copy(stepId = stepId)
    }

    /**
     * Serialize to JSON string
     */
    fun toJson(): String = json.encodeToString(this)

    /**
     * Convert to Map (for SpiceMessage.data interoperability)
     */
    fun toMap(): Map<String, Any?> = buildMap {
        put("sessionId", sessionId)
        workflowId?.let { put("workflowId", it) }
        stepId?.let { put("stepId", it) }
        parentEventId?.let { put("parentEventId", it) }
        traceId?.let { put("traceId", it) }
    }
}
