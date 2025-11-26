package io.github.noailabs.spice.routing.constants

/**
 * Standard metric names and labels for routing decisions.
 *
 * Use these constants for consistent observability across implementations.
 * Implementations should use these labels when reporting to Micrometer, Prometheus, etc.
 *
 * ## Metric Naming Convention
 * - Prefix: `spice_routing_`
 * - Suffix indicates type: `_total` (counter), `_seconds` (histogram/timer)
 *
 * ## Example Usage
 * ```kotlin
 * // Micrometer
 * meterRegistry.counter(
 *     RoutingMetrics.ROUTING_TOTAL,
 *     RoutingMetrics.LABEL_ENGINE_ID, engine.id,
 *     RoutingMetrics.LABEL_RESULT, result.resultId,
 *     RoutingMetrics.LABEL_CONFIDENCE, RoutingMetrics.confidenceBucket(confidence)
 * ).increment()
 *
 * // Prometheus direct
 * routing_total{engine_id="intent-classifier",result="YES",confidence_bucket="high"} 42
 * ```
 *
 * @since 1.0.7
 */
object RoutingMetrics {

    // ===========================
    // METRIC NAMES
    // ===========================

    /**
     * Counter: Total routing decisions attempted.
     * Labels: engine_id, graph_id, node_id, tenant_id
     */
    const val ROUTING_ATTEMPTS_TOTAL = "spice_routing_attempts_total"

    /**
     * Counter: Successful routing decisions.
     * Labels: engine_id, result, confidence_bucket, graph_id, tenant_id
     */
    const val ROUTING_TOTAL = "spice_routing_total"

    /**
     * Counter: Routing errors.
     * Labels: engine_id, error_code, graph_id, tenant_id
     */
    const val ROUTING_ERRORS_TOTAL = "spice_routing_errors_total"

    /**
     * Counter: Fallback routing (when no mapping matched).
     * Labels: engine_id, graph_id, node_id
     */
    const val ROUTING_FALLBACKS_TOTAL = "spice_routing_fallbacks_total"

    /**
     * Histogram: Routing decision latency.
     * Labels: engine_id, graph_id
     */
    const val ROUTING_DURATION_SECONDS = "spice_routing_duration_seconds"

    /**
     * Gauge: Current embedding match score (for embedding-based routing).
     * Labels: engine_id, model
     */
    const val EMBEDDING_MATCH_SCORE = "spice_embedding_match_score"

    /**
     * Counter: Decision engine cache hits.
     * Labels: engine_id, cache_type
     */
    const val ROUTING_CACHE_HITS_TOTAL = "spice_routing_cache_hits_total"

    /**
     * Counter: Decision engine cache misses.
     * Labels: engine_id, cache_type
     */
    const val ROUTING_CACHE_MISSES_TOTAL = "spice_routing_cache_misses_total"

    // ===========================
    // LABEL KEYS
    // ===========================

    /** Decision engine identifier */
    const val LABEL_ENGINE_ID = "engine_id"

    /** Decision result (resultId) */
    const val LABEL_RESULT = "result"

    /** Confidence bucket (high/medium/low) */
    const val LABEL_CONFIDENCE = "confidence_bucket"

    /** Graph identifier */
    const val LABEL_GRAPH_ID = "graph_id"

    /** Node identifier */
    const val LABEL_NODE_ID = "node_id"

    /** User identifier */
    const val LABEL_USER_ID = "user_id"

    /** Tenant identifier */
    const val LABEL_TENANT_ID = "tenant_id"

    /** Error code */
    const val LABEL_ERROR_CODE = "error_code"

    /** Model name (for ML-based routing) */
    const val LABEL_MODEL = "model"

    /** Decision path taken */
    const val LABEL_DECISION_PATH = "decision_path"

    /** Cache type (memory/redis) */
    const val LABEL_CACHE_TYPE = "cache_type"

    // ===========================
    // CONFIDENCE BUCKETS
    // ===========================

    /** High confidence (>= 0.8) */
    const val CONFIDENCE_HIGH = "high"

    /** Medium confidence (>= 0.5, < 0.8) */
    const val CONFIDENCE_MEDIUM = "medium"

    /** Low confidence (< 0.5) */
    const val CONFIDENCE_LOW = "low"

    /**
     * Get confidence bucket for a score.
     *
     * @param score Confidence score (0.0-1.0)
     * @return Bucket name: "high", "medium", or "low"
     */
    fun confidenceBucket(score: Double): String = when {
        score >= 0.8 -> CONFIDENCE_HIGH
        score >= 0.5 -> CONFIDENCE_MEDIUM
        else -> CONFIDENCE_LOW
    }

    // ===========================
    // LOG FIELD NAMES
    // ===========================

    /**
     * Standard log field names for structured logging.
     */
    object LogFields {
        const val ENGINE_ID = "routing.engine_id"
        const val RESULT_ID = "routing.result_id"
        const val CONFIDENCE = "routing.confidence"
        const val LATENCY_MS = "routing.latency_ms"
        const val REASONING = "routing.reasoning"
        const val FALLBACK_USED = "routing.fallback_used"
        const val ERROR_CODE = "routing.error_code"
        const val ERROR_MESSAGE = "routing.error_message"
    }
}
